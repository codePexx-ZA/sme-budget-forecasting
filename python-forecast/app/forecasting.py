# Builds forecasts from approved income statement history and saves them to forecast_run and forecast_value.

import uuid
from collections import defaultdict
from collections.abc import Callable
from dataclasses import dataclass, field
from datetime import date
from typing import Literal

import psycopg
from psycopg.types.json import Jsonb
from pydantic import BaseModel, Field

from app import algorithms

MONTHS_PER_PERIOD = {"monthly": 1, "quarterly": 3, "annual": 12}

RUN_COLUMNS = "run_id, unit_id, forecasting_method, history_start, history_end, parameters, created_at"

# Below these counts the forecast still runs, but the user is warned to treat it with caution.
RECOMMENDED_PERIODS = {"monthly": 24, "quarterly": 8, "annual": 3}


class ForecastError(Exception):
    pass


class ForecastRequest(BaseModel):
    unit_id: str = Field(min_length=1, max_length=32)
    frequency: Literal["annual", "quarterly", "monthly"] = "annual"
    horizon: int = Field(default=1, ge=1, le=3, description="Number of future periods (1-3)")
    window_size: int = Field(default=3, ge=2, le=12, description="Moving Average window (annual: 2-3)")
    alpha: float = Field(default=0.5, gt=0, le=1, description="Exponential Smoothing weight on recent periods")


@dataclass
class History:
    series: dict[str, list[tuple[date, float]]]
    warnings: list[str] = field(default_factory=list)


def add_months(day: date, months: int) -> date:
    month_index = day.month - 1 + months
    return date(day.year + month_index // 12, month_index % 12 + 1, 1)


def financial_year_start(day: date, year_end_month: int) -> date:
    # A February year-end means the financial year starts on 1 March.
    start_month = year_end_month % 12 + 1
    year = day.year if day.month >= start_month else day.year - 1
    return date(year, start_month, 1)


def load_history(conn: psycopg.Connection, request: ForecastRequest) -> History:
    unit = conn.execute(
        """
        SELECT b.year_end_month
        FROM business_unit u
        JOIN business b ON b.business_id = u.business_id
        WHERE u.unit_id = %s
        """,
        (request.unit_id,),
    ).fetchone()
    if unit is None:
        raise ForecastError(f"Business unit {request.unit_id} not found")
    year_end_month = unit["year_end_month"]

    # Only approved submissions are used. DISTINCT ON keeps the most recently submitted
    # figure when two approved uploads cover the same category and period.
    rows = conn.execute(
        """
        SELECT DISTINCT ON (fli.category, fli.period_start, fli.frequency)
               fli.category, fli.period_start, fli.frequency, fli.amount
        FROM financial_line_item fli
        JOIN budget_submission bs ON bs.submission_id = fli.submission_id
        WHERE bs.unit_id = %s AND bs.status = 'approved'
        ORDER BY fli.category, fli.period_start, fli.frequency, bs.submitted_at DESC
        """,
        (request.unit_id,),
    ).fetchall()
    return build_series(rows, request.frequency, year_end_month)


def build_series(rows: list[dict], frequency: str, year_end_month: int) -> History:
    points: dict[str, dict[date, float]] = defaultdict(dict)
    monthly: dict[tuple[str, date], dict[date, float]] = defaultdict(dict)

    for row in rows:
        amount = float(row["amount"])
        if row["frequency"] == frequency:
            points[row["category"]][row["period_start"]] = amount
        elif frequency == "annual" and row["frequency"] == "monthly":
            fy_start = financial_year_start(row["period_start"], year_end_month)
            monthly[(row["category"], fy_start)][row["period_start"]] = amount

    # For annual runs, a financial year of monthly data is rolled up only when all 12 months
    # are present and no annual figure exists. Annual figures are never split into months.
    warnings: list[str] = []
    rolled_up = 0
    for (category, fy_start), months in monthly.items():
        if fy_start in points[category]:
            continue
        if len(months) == 12:
            points[category][fy_start] = sum(months.values())
            rolled_up += 1
    if rolled_up:
        warnings.append(f"{rolled_up} annual figures were calculated from complete years of monthly data.")

    series = {
        category: sorted(periods.items())
        for category, periods in points.items()
        if periods
    }
    return History(series=series, warnings=warnings)


def select_usable_series(
    history: History, request: ForecastRequest, warnings: list[str]
) -> dict[str, list[tuple[date, float]]]:
    # Categories shorter than the Moving Average window are skipped; gaps are allowed but reported.
    step = MONTHS_PER_PERIOD[request.frequency]
    usable: dict[str, list[tuple[date, float]]] = {}
    for category, points in sorted(history.series.items()):
        if len(points) < request.window_size:
            warnings.append(
                f"{category}: skipped, only {len(points)} {request.frequency} periods "
                f"(Moving Average window is {request.window_size})."
            )
            continue
        expected = add_months(points[0][0], step * (len(points) - 1))
        if expected != points[-1][0]:
            warnings.append(f"{category}: history has missing periods; forecast uses the periods available.")
        usable[category] = points

    if not usable:
        raise ForecastError(
            f"No category has at least {request.window_size} approved {request.frequency} periods to forecast."
        )
    return usable


def history_warnings(
    usable: dict[str, list[tuple[date, float]]], frequency: str, history_end: date
) -> list[str]:
    warnings: list[str] = []
    shortest = min(len(points) for points in usable.values())
    if shortest < RECOMMENDED_PERIODS[frequency]:
        warnings.append(
            f"Short history: {shortest} {frequency} periods "
            f"(recommended at least {RECOMMENDED_PERIODS[frequency]}). Treat forecasts with caution."
        )
    for category, points in usable.items():
        if points[-1][0] != history_end:
            warnings.append(f"{category}: no figure for the latest period ({history_end}).")
    return warnings


def forecast_methods(request: ForecastRequest) -> dict[str, tuple[dict, Callable[[list[float]], list[float]]]]:
    # Both methods run on the same history and are saved as separate runs,
    # so the user can compare them and choose which becomes the budget.
    return {
        "moving_average": (
            {"window_size": request.window_size},
            lambda values: algorithms.moving_average_forecast(values, request.window_size, request.horizon),
        ),
        "exponential_smoothing": (
            {"alpha": request.alpha},
            lambda values: algorithms.exponential_smoothing_forecast(values, request.alpha, request.horizon),
        ),
    }


def run_forecast(conn: psycopg.Connection, request: ForecastRequest) -> list[dict]:
    history = load_history(conn, request)
    warnings = list(history.warnings)
    usable = select_usable_series(history, request, warnings)

    history_start = min(points[0][0] for points in usable.values())
    history_end = max(points[-1][0] for points in usable.values())
    warnings.extend(history_warnings(usable, request.frequency, history_end))

    step = MONTHS_PER_PERIOD[request.frequency]
    future_periods = [add_months(history_end, step * k) for k in range(1, request.horizon + 1)]

    # One transaction: if any insert fails, neither run is saved.
    run_ids = []
    with conn.transaction():
        for method, (method_params, forecast) in forecast_methods(request).items():
            run_id = uuid.uuid4().hex
            parameters = {
                **method_params,
                "frequency": request.frequency,
                "horizon": request.horizon,
                "warnings": warnings,
            }
            conn.execute(
                """
                INSERT INTO forecast_run (run_id, unit_id, forecasting_method, history_start, history_end, parameters)
                VALUES (%s, %s, %s, %s, %s, %s)
                """,
                (run_id, request.unit_id, method, history_start, history_end, Jsonb(parameters)),
            )
            value_rows = [
                (uuid.uuid4().hex, run_id, category, period, amount)
                for category, points in usable.items()
                for period, amount in zip(future_periods, forecast([amount for _, amount in points]))
            ]
            with conn.cursor() as cur:
                cur.executemany(
                    """
                    INSERT INTO forecast_value (value_id, run_id, category, forecast_period, baseline_amount)
                    VALUES (%s, %s, %s, %s, %s)
                    """,
                    value_rows,
                )
            run_ids.append(run_id)

    return [get_run(conn, run_id) for run_id in run_ids]


def get_run(conn: psycopg.Connection, run_id: str) -> dict | None:
    run = conn.execute(
        f"""
        SELECT {RUN_COLUMNS}
        FROM forecast_run
        WHERE run_id = %s
        """,
        (run_id,),
    ).fetchone()
    if run is None:
        return None

    values = conn.execute(
        """
        SELECT fv.category, lc.display_name, fv.forecast_period, fv.baseline_amount, fv.adjusted_amount
        FROM forecast_value fv
        JOIN line_category lc ON lc.category_code = fv.category
        WHERE fv.run_id = %s
        ORDER BY lc.sort_order, fv.forecast_period
        """,
        (run_id,),
    ).fetchall()
    return {**run, "values": values}


def list_runs(conn: psycopg.Connection, unit_id: str | None, take: int) -> list[dict]:
    return conn.execute(
        f"""
        SELECT {RUN_COLUMNS}
        FROM forecast_run
        WHERE %(unit_id)s::varchar IS NULL OR unit_id = %(unit_id)s
        ORDER BY created_at DESC
        LIMIT %(take)s
        """,
        {"unit_id": unit_id, "take": take},
    ).fetchall()


def delete_run(conn: psycopg.Connection, run_id: str) -> bool:
    with conn.transaction():
        deleted = conn.execute("DELETE FROM forecast_run WHERE run_id = %s", (run_id,))
    return deleted.rowcount > 0

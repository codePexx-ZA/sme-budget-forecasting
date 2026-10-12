# Checks the annual roll-up rule: monthly figures become an annual figure only for a complete financial year.

from datetime import date

from app.forecasting import add_months, build_series

FY_START = date(2024, 3, 1)  # February year-end


def monthly_rows(count: int) -> list[dict]:
    return [
        {"category": "revenue", "period_start": add_months(FY_START, i), "frequency": "monthly", "amount": 100}
        for i in range(count)
    ]


def test_eleven_months_give_no_annual_figure():
    assert build_series(monthly_rows(11), "annual", 2).series == {}


def test_twelve_months_are_summed():
    assert build_series(monthly_rows(12), "annual", 2).series == {"revenue": [(FY_START, 1200.0)]}


def test_annual_figure_wins_over_monthly():
    annual = {"category": "revenue", "period_start": FY_START, "frequency": "annual", "amount": 999}
    assert build_series([annual, *monthly_rows(12)], "annual", 2).series == {"revenue": [(FY_START, 999.0)]}

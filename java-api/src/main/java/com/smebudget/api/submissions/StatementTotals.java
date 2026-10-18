package com.smebudget.api.submissions;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Turns stored category amounts into gross profit, operating profit and net profit.
 * See {@link PeriodTotals} for which categories go into which figure.
 */
final class StatementTotals {

    private static final Set<String> INCOME = Set.of("revenue", "other_income");
    private static final Set<String> GROSS_DEDUCT = Set.of("cost_of_sales");
    private static final Set<String> OPERATING_DEDUCT = Set.of(
            "employee_costs", "occupancy", "electricity", "other_utilities",
            "transport_fuel", "marketing", "other_operating", "depreciation");
    private static final Set<String> NET_DEDUCT = Set.of("finance_costs", "income_tax");

    private StatementTotals() {
    }

    static List<PeriodTotals> calculate(List<FinancialLineItem> lines) {
        Map<LocalDate, BigDecimal[]> byPeriod = new LinkedHashMap<>();
        for (FinancialLineItem line : lines) {
            BigDecimal[] parts = byPeriod.computeIfAbsent(line.getPeriodStart(), key -> new BigDecimal[] {
                    BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO
            });
            BigDecimal amount = line.getAmount() == null ? BigDecimal.ZERO : line.getAmount();
            String category = line.getCategory();
            if (INCOME.contains(category)) {
                parts[0] = parts[0].add(amount);
            } else if (GROSS_DEDUCT.contains(category)) {
                parts[1] = parts[1].add(amount);
            } else if (OPERATING_DEDUCT.contains(category)) {
                parts[2] = parts[2].add(amount);
            } else if (NET_DEDUCT.contains(category)) {
                parts[3] = parts[3].add(amount);
            }
        }

        List<LocalDate> periods = new ArrayList<>(byPeriod.keySet());
        periods.sort(Comparator.naturalOrder());

        List<PeriodTotals> totals = new ArrayList<>();
        for (LocalDate period : periods) {
            BigDecimal[] parts = byPeriod.get(period);
            BigDecimal gross = parts[0].subtract(parts[1]);
            BigDecimal operating = gross.subtract(parts[2]);
            BigDecimal net = operating.subtract(parts[3]);
            totals.add(new PeriodTotals(period, gross, operating, net));
        }
        return totals;
    }
}

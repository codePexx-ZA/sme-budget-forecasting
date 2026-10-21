package com.smebudget.api.targets;

import com.smebudget.api.auth.AppUser;
import com.smebudget.api.auth.AppUserRepository;
import com.smebudget.api.auth.AuthenticatedUser;
import com.smebudget.api.common.ApiException;
import com.smebudget.api.submissions.ApprovalAction;
import com.smebudget.api.submissions.ApprovalActionRepository;
import com.smebudget.api.submissions.BudgetSubmission;
import com.smebudget.api.submissions.BudgetSubmissionRepository;
import com.smebudget.api.submissions.FinancialLineItem;
import com.smebudget.api.submissions.FinancialLineItemRepository;
import com.smebudget.api.submissions.LineCategory;
import com.smebudget.api.submissions.LineCategoryRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Meeting report (Boitu). Reads data that the other parts have already saved.
 * It does not forecast, and it does not send email. The phone shares {@code shareText}
 * with whatever mail app the user already has.
 *
 * <p>Only approved annual figures are included. A draft or a submission still waiting
 * is counted in the note and left out of the numbers. If two approved uploads cover
 * the same category and year, the one submitted later wins, matching the forecasting service.
 */
@Service
public class ReportService {

    static final ZoneId JOHANNESBURG = ZoneId.of("Africa/Johannesburg");
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", Locale.ENGLISH);
    private static final String MOVING_AVERAGE = "moving_average";
    private static final String EXPONENTIAL = "exponential_smoothing";
    private static final String NOT_AVAILABLE = "n/a";

    private static final Set<String> INCOME = Set.of("revenue", "other_income");
    private static final Set<String> GROSS_DEDUCT = Set.of("cost_of_sales");
    private static final Set<String> OPERATING_DEDUCT = Set.of(
            "employee_costs", "occupancy", "electricity", "other_utilities",
            "transport_fuel", "marketing", "other_operating", "depreciation");
    private static final Set<String> NET_DEDUCT = Set.of("finance_costs", "income_tax");

    private final AppUserRepository users;
    private final ReportUnitRepository units;
    private final ReportBusinessRepository businesses;
    private final BudgetSubmissionRepository submissions;
    private final FinancialLineItemRepository lineItems;
    private final ApprovalActionRepository approvals;
    private final LineCategoryRepository categories;
    private final KpiTargetRepository targets;
    private final SavedForecastRunRepository forecastRuns;
    private final SavedForecastValueRepository forecastValues;
    private final SavedAlertRepository alerts;

    public ReportService(AppUserRepository users, ReportUnitRepository units, ReportBusinessRepository businesses,
                         BudgetSubmissionRepository submissions, FinancialLineItemRepository lineItems,
                         ApprovalActionRepository approvals, LineCategoryRepository categories,
                         KpiTargetRepository targets, SavedForecastRunRepository forecastRuns,
                         SavedForecastValueRepository forecastValues, SavedAlertRepository alerts) {
        this.users = users;
        this.units = units;
        this.businesses = businesses;
        this.submissions = submissions;
        this.lineItems = lineItems;
        this.approvals = approvals;
        this.categories = categories;
        this.targets = targets;
        this.forecastRuns = forecastRuns;
        this.forecastValues = forecastValues;
        this.alerts = alerts;
    }

    @Transactional(readOnly = true)
    public MeetingReport meetingReport(AuthenticatedUser principal) {
        AppUser user = requireUser(principal);
        ReportUnit unit = units.findById(user.getUnitId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Business unit not found."));
        String businessName = businesses.findById(unit.getBusinessId())
                .map(ReportBusiness::getLegalName)
                .orElse(unit.getUnitName());

        List<BudgetSubmission> unitSubmissions = submissions.findByUnitId(user.getUnitId());
        List<BudgetSubmission> approved = new ArrayList<>();
        int waiting = 0;
        int drafts = 0;
        for (BudgetSubmission submission : unitSubmissions) {
            if (BudgetSubmission.STATUS_APPROVED.equals(submission.getStatus())) {
                approved.add(submission);
            } else if (BudgetSubmission.STATUS_SUBMITTED.equals(submission.getStatus())) {
                waiting++;
            } else if (BudgetSubmission.STATUS_DRAFT.equals(submission.getStatus())) {
                drafts++;
            }
        }

        Map<String, List<FinancialLineItem>> lines = new HashMap<>();
        for (BudgetSubmission submission : approved) {
            lines.put(submission.getSubmissionId(), lineItems.findBySubmissionId(submission.getSubmissionId()));
        }
        LatestYear history = selectLatestYear(approved, lines);

        Map<String, BigDecimal> movingAverage = amountsFor(latestRun(user.getUnitId(), MOVING_AVERAGE));
        Map<String, BigDecimal> smoothing = amountsFor(latestRun(user.getUnitId(), EXPONENTIAL));
        LocalDate forecastPeriod = forecastPeriod(latestRun(user.getUnitId(), MOVING_AVERAGE),
                latestRun(user.getUnitId(), EXPONENTIAL));

        List<LineCategory> categoryRows = categories.findAllByOrderBySortOrderAsc();
        List<MeetingReport.ComparisonRow> rows = new ArrayList<>();
        for (LineCategory category : categoryRows) {
            BigDecimal last = history.amounts().get(category.getCategoryCode());
            BigDecimal average = movingAverage.get(category.getCategoryCode());
            BigDecimal smooth = smoothing.get(category.getCategoryCode());
            if (last == null && average == null && smooth == null) {
                continue;
            }
            rows.add(new MeetingReport.ComparisonRow(
                    category.getDisplayName(),
                    money(last),
                    money(average),
                    money(smooth)));
        }

        List<KpiTarget> unitTargets = targets.findByUnitIdOrderByTargetPeriodDescMetricNameAsc(user.getUnitId());
        List<MeetingReport.Fact> resultLines = results(history.amounts(), unitTargets);
        List<MeetingReport.TargetLine> targetLines = targetLines(
                unitTargets, categoryRows, history.amounts(), movingAverage, smoothing);
        List<MeetingReport.ApprovalNote> approvalLines = approvalLines(approved);
        List<MeetingReport.WarningLine> warningLines = warningLines();

        String historyNote = history.period() == null
                ? "No approved annual history yet. Forecasts and the figures below need an approved annual submission."
                : "Latest approved year starts " + history.period() + ".";
        String forecastNote = forecastPeriod == null
                ? "No forecast has been run yet. Open Forecasts and alerts, run both methods, then open this report again."
                : "Forecasts are for the year starting " + forecastPeriod + ". Moving average and exponential smoothing are both shown.";
        String excludedNote = excludedNote(waiting, drafts);
        String preparedAt = WHEN.format(Instant.now().atZone(JOHANNESBURG));

        MeetingReport report = new MeetingReport(
                businessName,
                unit.getUnitName(),
                user.getFullName(),
                preparedAt,
                historyNote,
                forecastNote,
                excludedNote,
                approvalLines,
                rows,
                resultLines,
                targetLines,
                warningLines,
                "");
        return new MeetingReport(
                report.businessName(), report.unitName(), report.preparedFor(), report.preparedAt(),
                report.historyNote(), report.forecastNote(), report.excludedNote(),
                report.approvals(), report.rows(), report.results(), report.targets(), report.warnings(),
                shareText(report));
    }

    /**
     * One amount per category for the newest annual period. A later submission replaces an
     * earlier approved figure for the same category and period.
     */
    static LatestYear selectLatestYear(List<BudgetSubmission> approved, Map<String, List<FinancialLineItem>> lines) {
        Map<String, Chosen> chosen = new HashMap<>();
        for (BudgetSubmission submission : approved) {
            if (!BudgetSubmission.STATUS_APPROVED.equals(submission.getStatus())) {
                continue;
            }
            Instant submittedAt = submission.getSubmittedAt() == null ? Instant.EPOCH : submission.getSubmittedAt();
            List<FinancialLineItem> submissionLines = lines.getOrDefault(submission.getSubmissionId(), List.of());
            for (FinancialLineItem line : submissionLines) {
                if (!FinancialLineItem.ANNUAL.equals(line.getFrequency()) || line.getPeriodStart() == null) {
                    continue;
                }
                String key = line.getCategory() + "|" + line.getPeriodStart();
                Chosen current = chosen.get(key);
                if (current == null || submittedAt.isAfter(current.submittedAt())) {
                    chosen.put(key, new Chosen(line.getCategory(), line.getPeriodStart(), line.getAmount(), submittedAt));
                }
            }
        }
        LocalDate latest = null;
        for (Chosen row : chosen.values()) {
            if (latest == null || row.period().isAfter(latest)) {
                latest = row.period();
            }
        }
        Map<String, BigDecimal> amounts = new LinkedHashMap<>();
        if (latest != null) {
            for (Chosen row : chosen.values()) {
                if (latest.equals(row.period())) {
                    amounts.put(row.category(), row.amount());
                }
            }
        }
        return new LatestYear(latest, amounts);
    }

    private SavedForecastRun latestRun(String unitId, String method) {
        for (SavedForecastRun run : forecastRuns.findByUnitIdOrderByCreatedAtDesc(unitId)) {
            if (method.equals(run.getForecastingMethod())) {
                return run;
            }
        }
        return null;
    }

    /** Uses the earliest forecast period on that run, which is the next period when the horizon is 1. */
    private Map<String, BigDecimal> amountsFor(SavedForecastRun run) {
        Map<String, BigDecimal> amounts = new HashMap<>();
        if (run == null) {
            return amounts;
        }
        List<SavedForecastValue> values = forecastValues.findByRunId(run.getRunId());
        LocalDate period = null;
        for (SavedForecastValue value : values) {
            if (period == null || value.getForecastPeriod().isBefore(period)) {
                period = value.getForecastPeriod();
            }
        }
        if (period == null) {
            return amounts;
        }
        for (SavedForecastValue value : values) {
            if (period.equals(value.getForecastPeriod())) {
                amounts.put(value.getCategory(), value.getBaselineAmount());
            }
        }
        return amounts;
    }

    private LocalDate forecastPeriod(SavedForecastRun movingAverage, SavedForecastRun smoothing) {
        LocalDate period = null;
        for (SavedForecastRun run : new SavedForecastRun[] { movingAverage, smoothing }) {
            if (run == null) {
                continue;
            }
            for (SavedForecastValue value : forecastValues.findByRunId(run.getRunId())) {
                if (period == null || value.getForecastPeriod().isBefore(period)) {
                    period = value.getForecastPeriod();
                }
            }
        }
        return period;
    }

    private List<MeetingReport.ApprovalNote> approvalLines(List<BudgetSubmission> approved) {
        List<MeetingReport.ApprovalNote> lines = new ArrayList<>();
        approved.stream()
                .sorted(Comparator.comparing(BudgetSubmission::getPeriodStart))
                .forEach(submission -> {
                    String period = submission.getPeriodStart() + " to " + submission.getPeriodEnd();
                    List<ApprovalAction> actions = approvals.findBySubmissionId(submission.getSubmissionId());
                    boolean noted = false;
                    for (ApprovalAction action : actions) {
                        if (!ApprovalAction.DECISION_APPROVED.equals(action.getDecision())) {
                            continue;
                        }
                        String name = users.findById(action.getActedBy()).map(AppUser::getFullName).orElse("Unknown approver");
                        String when = action.getActedAt() == null ? "" : DAY.format(action.getActedAt().atZone(JOHANNESBURG));
                        String comment = action.getComments() == null || action.getComments().isBlank()
                                ? ""
                                : action.getComments().trim();
                        lines.add(new MeetingReport.ApprovalNote(name, when, period, comment));
                        noted = true;
                    }
                    if (!noted) {
                        lines.add(new MeetingReport.ApprovalNote("No approver recorded", "", period, ""));
                    }
                });
        return lines;
    }

    private List<MeetingReport.Fact> results(Map<String, BigDecimal> amounts, List<KpiTarget> unitTargets) {
        if (amounts.isEmpty()) {
            return List.of(new MeetingReport.Fact("Results", "n/a", "These appear once an annual submission is approved."));
        }
        BigDecimal income = sum(amounts, INCOME);
        BigDecimal gross = income.subtract(sum(amounts, GROSS_DEDUCT));
        BigDecimal operating = gross.subtract(sum(amounts, OPERATING_DEDUCT));
        BigDecimal net = operating.subtract(sum(amounts, NET_DEDUCT));
        List<MeetingReport.Fact> lines = new ArrayList<>();
        lines.add(new MeetingReport.Fact("Gross profit", money(gross), null));
        String margin = "n/a";
        String marginNote = null;
        if (income.signum() > 0) {
            BigDecimal percent = gross.multiply(new BigDecimal("100")).divide(income, 1, RoundingMode.HALF_UP);
            margin = percent.toPlainString() + "%";
            for (KpiTarget target : unitTargets) {
                if ("gross profit margin".equalsIgnoreCase(target.getMetricName().trim())) {
                    marginNote = "Target " + target.getTargetValue().stripTrailingZeros().toPlainString() + "%";
                    break;
                }
            }
        }
        lines.add(new MeetingReport.Fact("Gross profit margin", margin, marginNote));
        lines.add(new MeetingReport.Fact("Operating profit", money(operating), null));
        lines.add(new MeetingReport.Fact("Net profit", money(net), null));
        return lines;
    }

    @Transactional(readOnly = true)
    public FinancialYearResponse financialYear(AuthenticatedUser principal) {
        AppUser user = requireUser(principal);
        int month = FinancialYear.DEFAULT_YEAR_END_MONTH;
        ReportUnit unit = units.findById(user.getUnitId()).orElse(null);
        if (unit != null) {
            month = businesses.findById(unit.getBusinessId())
                    .map(business -> FinancialYear.month(business.getYearEndMonth()))
                    .orElse(FinancialYear.DEFAULT_YEAR_END_MONTH);
        }
        LocalDate today = LocalDate.now(JOHANNESBURG);
        FinancialYear.Span current = FinancialYear.containing(today, month);
        FinancialYear.Span next = FinancialYear.following(current);
        return new FinancialYearResponse(
                month, current.start(), current.end(), next.start(), next.end());
    }

    private List<MeetingReport.TargetLine> targetLines(List<KpiTarget> unitTargets,
                                                      List<LineCategory> categoryRows,
                                                      Map<String, BigDecimal> lastYear,
                                                      Map<String, BigDecimal> movingAverage,
                                                      Map<String, BigDecimal> smoothing) {
        List<MeetingReport.TargetLine> lines = new ArrayList<>();
        for (KpiTarget target : unitTargets) {
            String value = "ZAR".equalsIgnoreCase(target.getMeasurementUnit())
                    ? money(target.getTargetValue())
                    : target.getTargetValue().stripTrailingZeros().toPlainString() + " " + target.getMeasurementUnit();
            String category = categoryForTarget(target.getMetricName(), categoryRows);
            boolean compare = category != null && "ZAR".equalsIgnoreCase(target.getMeasurementUnit());
            lines.add(new MeetingReport.TargetLine(
                    target.getMetricName(),
                    value,
                    String.valueOf(target.getTargetPeriod()),
                    compare ? money(lastYear.get(category)) : null,
                    compare ? money(movingAverage.get(category)) : null,
                    compare ? money(smoothing.get(category)) : null));
        }
        return lines;
    }

    private List<MeetingReport.WarningLine> warningLines() {
        List<MeetingReport.WarningLine> lines = new ArrayList<>();
        for (SavedAlert alert : alerts.findByResolutionStatusOrderByCreatedAtDesc("open")) {
            if (lines.size() >= 20) {
                break;
            }
            String severity = alert.getSeverity() == null ? "warning" : alert.getSeverity();
            lines.add(new MeetingReport.WarningLine(severity, alert.getMessage()));
        }
        return lines;
    }

    private static String excludedNote(int waiting, int drafts) {
        if (waiting == 0 && drafts == 0) {
            return null;
        }
        List<String> parts = new ArrayList<>();
        if (waiting > 0) {
            parts.add(waiting + (waiting == 1 ? " submission is waiting for approval" : " submissions are waiting for approval"));
        }
        if (drafts > 0) {
            parts.add(drafts + (drafts == 1 ? " draft is still being edited" : " drafts are still being edited"));
        }
        return "Not in these figures: " + String.join(", and ", parts) + ".";
    }

    static String shareText(MeetingReport report) {
        StringBuilder text = new StringBuilder();
        text.append("BUDGET REPORT\n");
        text.append(report.businessName()).append("\n");
        text.append(report.unitName()).append("\n");
        text.append("Prepared for ").append(report.preparedFor()).append("\n");
        text.append("Prepared ").append(report.preparedAt()).append(" (South Africa)\n\n");
        text.append("APPROVED HISTORY\n");
        text.append(report.historyNote()).append("\n");
        if (report.approvals().isEmpty()) {
            text.append("Nothing has been approved yet.\n");
        }
        for (MeetingReport.ApprovalNote approval : report.approvals()) {
            text.append("Approved by ").append(approval.approver());
            if (approval.when() != null && !approval.when().isBlank()) {
                text.append(" on ").append(approval.when());
            }
            if (approval.period() != null && !approval.period().isBlank()) {
                text.append(" for ").append(approval.period());
            }
            text.append(".");
            if (approval.comment() != null && !approval.comment().isBlank()) {
                text.append(" ").append(approval.comment());
            }
            text.append("\n");
        }
        if (report.excludedNote() != null) {
            text.append(report.excludedNote()).append("\n");
        }
        text.append("\nLATEST YEAR AND FORECASTS\n");
        text.append(report.forecastNote()).append("\n");
        for (MeetingReport.ComparisonRow row : report.rows()) {
            text.append("\n").append(row.category()).append("\n");
            text.append("  Last approved year: ").append(row.lastYear()).append("\n");
            text.append("  Moving average: ").append(row.movingAverage()).append("\n");
            text.append("  Exponential smoothing: ").append(row.exponentialSmoothing()).append("\n");
        }
        text.append("\nRESULTS FOR THAT YEAR\n");
        for (MeetingReport.Fact result : report.results()) {
            text.append(result.label()).append(": ").append(result.value());
            if (result.note() != null && !result.note().isBlank()) {
                text.append(" (").append(result.note()).append(")");
            }
            text.append("\n");
        }
        text.append("\nKPI TARGETS\n");
        if (report.targets().isEmpty()) {
            text.append("No KPI targets have been set.\n");
        }
        for (MeetingReport.TargetLine target : report.targets()) {
            text.append(target.name()).append(": ").append(target.value());
            text.append(" (period ").append(target.period()).append(")");
            if (target.lastYear() != null) {
                text.append(". Last approved year ").append(target.lastYear());
                text.append("; moving average ").append(target.movingAverage());
                text.append("; exponential smoothing ").append(target.exponentialSmoothing());
                text.append(".");
            }
            text.append("\n");
        }
        text.append("\nECONOMIC WARNINGS\n");
        if (report.warnings().isEmpty()) {
            text.append("No open economic warnings.\n");
        }
        for (MeetingReport.WarningLine warning : report.warnings()) {
            text.append(warning.severity()).append(": ").append(warning.message()).append("\n");
        }
        text.append("\nRevenue and employee costs are set by the business. The warnings apply to the other categories they name.\n");
        return text.toString().trim();
    }

    /**
     * Category code a ZAR target can be compared with, or null when the name is not a line.
     * Display names such as "Employee costs" match {@code line_category}. "revenue" and
     * "electricity" still match, including the saved name "Electricity cost".
     * "Gross profit margin" stays null so the margin note on the results section keeps it.
     */
    static String categoryForTarget(String metricName, List<LineCategory> categories) {
        if (metricName == null) {
            return null;
        }
        String trimmed = metricName.trim();
        if (trimmed.isEmpty() || "gross profit margin".equalsIgnoreCase(trimmed)) {
            return null;
        }
        if (categories != null) {
            for (LineCategory category : categories) {
                if (category.getCategoryCode().equalsIgnoreCase(trimmed)
                        || category.getDisplayName().equalsIgnoreCase(trimmed)) {
                    return category.getCategoryCode();
                }
            }
        }
        String key = trimmed.toLowerCase(Locale.ROOT).replace(" cost", "").trim();
        if ("revenue".equals(key) || "electricity".equals(key)) {
            return key;
        }
        return null;
    }

    private static BigDecimal sum(Map<String, BigDecimal> amounts, Set<String> categories) {
        BigDecimal total = BigDecimal.ZERO;
        for (String category : categories) {
            BigDecimal amount = amounts.get(category);
            if (amount != null) {
                total = total.add(amount);
            }
        }
        return total;
    }

    private static String money(BigDecimal value) {
        if (value == null) {
            return NOT_AVAILABLE;
        }
        DecimalFormat format = new DecimalFormat("#,##0.00");
        return "R " + format.format(value);
    }

    private AppUser requireUser(AuthenticatedUser principal) {
        if (principal == null || principal.userId() == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Please log in to continue.");
        }
        return users.findById(principal.userId())
                .filter(AppUser::isActive)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Please log in to continue."));
    }

    record LatestYear(LocalDate period, Map<String, BigDecimal> amounts) {
    }

    private record Chosen(String category, LocalDate period, BigDecimal amount, Instant submittedAt) {
    }
}

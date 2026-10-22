package com.smebudget.api.targets;

import com.smebudget.api.auth.AuthenticatedUser;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Meeting report (Boitu). One read of everything already saved for this business unit.
 *
 * <pre>
 * GET /api/reports/meeting          decision maker, own business unit
 * GET /api/reports/financial-year   any logged-in user, this business's year-end (submission form)
 * </pre>
 */
@RestController
@RequestMapping("/api/reports")
public class ReportController {

    private final ReportService reports;

    public ReportController(ReportService reports) {
        this.reports = reports;
    }

    @GetMapping("/meeting")
    @PreAuthorize("hasRole('DECISION_MAKER')")
    public MeetingReport meeting(@AuthenticationPrincipal AuthenticatedUser user) {
        return reports.meetingReport(user);
    }

    /** One read of the business row the meeting report already uses, for the submission form. */
    @GetMapping("/financial-year")
    public FinancialYearResponse financialYear(@AuthenticationPrincipal AuthenticatedUser user) {
        return reports.financialYear(user);
    }
}

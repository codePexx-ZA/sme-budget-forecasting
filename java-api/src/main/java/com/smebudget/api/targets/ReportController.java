package com.smebudget.api.targets;

import com.smebudget.api.auth.AuthenticatedUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Meeting report (Boitu). One read of everything already saved for this business unit.
 *
 * <pre>
 * GET /api/reports/meeting    any logged-in user, own business unit
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
    public MeetingReport meeting(@AuthenticationPrincipal AuthenticatedUser user) {
        return reports.meetingReport(user);
    }
}

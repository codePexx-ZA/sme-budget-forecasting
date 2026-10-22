package com.smebudget.api.targets;

import com.smebudget.api.auth.AuthenticatedUser;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Forecasts and economic warnings for the decision maker (Boitu).
 * The Android app calls these. This API then calls the Python and C# services.
 *
 * <pre>
 * GET  /api/insights/forecasts   decision maker: latest saved runs for this business unit
 * POST /api/insights/forecasts   decision maker: run Moving Average and Exponential Smoothing
 * GET  /api/insights/alerts      decision maker: open economic warnings
 * </pre>
 */
@RestController
@RequestMapping("/api/insights")
public class InsightsController {

    private final InsightsService insights;

    public InsightsController(InsightsService insights) {
        this.insights = insights;
    }

    @GetMapping("/forecasts")
    @PreAuthorize("hasRole('DECISION_MAKER')")
    public List<ForecastView> forecasts(@AuthenticationPrincipal AuthenticatedUser user) {
        return insights.listForecasts(user);
    }

    @PostMapping("/forecasts")
    @PreAuthorize("hasRole('DECISION_MAKER')")
    public List<ForecastView> runForecast(@AuthenticationPrincipal AuthenticatedUser user) {
        return insights.runForecast(user);
    }

    @GetMapping("/alerts")
    @PreAuthorize("hasRole('DECISION_MAKER')")
    public List<AlertView> alerts(@AuthenticationPrincipal AuthenticatedUser user) {
        return insights.listAlerts(user);
    }
}

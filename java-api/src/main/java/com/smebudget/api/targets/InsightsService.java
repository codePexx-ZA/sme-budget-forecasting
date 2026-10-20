package com.smebudget.api.targets;

import com.fasterxml.jackson.databind.JsonNode;
import com.smebudget.api.auth.AppUser;
import com.smebudget.api.auth.AppUserRepository;
import com.smebudget.api.auth.AuthenticatedUser;
import com.smebudget.api.common.ApiException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * Reads forecasts from the Python service and warning alerts from the C# service (Boitu).
 * Forecasting itself stays in Python: only Moving Average and Exponential Smoothing.
 * This class does not calculate a forecast and does not write forecast tables.
 */
@Service
public class InsightsService {

    private static final String LOGIN_AGAIN = "Please log in to continue.";
    private static final String FORECAST_DOWN = "The forecasting service is not running. Start it with Docker and try again.";
    private static final String ALERTS_DOWN = "The alerts service is not running. Start it with Docker and try again.";

    private final AppUserRepository users;
    private final RestClient forecast;
    private final RestClient alerts;

    public InsightsService(AppUserRepository users,
                           @Value("${app.forecast.base-url}") String forecastUrl,
                           @Value("${app.alerts.base-url}") String alertsUrl) {
        this.users = users;
        this.forecast = client(forecastUrl, Duration.ofSeconds(60));
        this.alerts = client(alertsUrl, Duration.ofSeconds(15));
    }

    /** The two newest saved runs for this unit, including their category values. */
    public List<ForecastView> listForecasts(AuthenticatedUser principal) {
        String unitId = requireUser(principal).getUnitId();
        JsonNode runs = get(forecast, "/api/forecasts?unit_id=" + unitId + "&take=4", FORECAST_DOWN);
        List<ForecastView> views = new ArrayList<>();
        if (runs == null || !runs.isArray()) {
            return views;
        }
        int shown = 0;
        for (JsonNode run : runs) {
            if (shown >= 2) {
                break;
            }
            String runId = text(run, "run_id");
            if (runId == null || runId.isBlank()) {
                continue;
            }
            JsonNode detail = get(forecast, "/api/forecasts/" + runId, FORECAST_DOWN);
            views.add(toForecast(detail));
            shown++;
        }
        return views;
    }

    /**
     * Asks Python to run both methods on the unit's approved history.
     * Horizon is 1 year so the decision maker can compare the two methods for the next period.
     */
    public List<ForecastView> runForecast(AuthenticatedUser principal) {
        String unitId = requireUser(principal).getUnitId();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("unit_id", unitId);
        body.put("frequency", "annual");
        body.put("horizon", 1);
        body.put("window_size", 3);
        body.put("alpha", 0.5);
        JsonNode created = post(forecast, "/api/forecasts", body, FORECAST_DOWN);
        List<ForecastView> views = new ArrayList<>();
        if (created != null && created.isArray()) {
            for (JsonNode run : created) {
                views.add(toForecast(run));
            }
        }
        return views;
    }

    public List<AlertView> listAlerts(AuthenticatedUser principal) {
        requireUser(principal);
        JsonNode rows = get(alerts, "/api/indicators/alerts?take=20", ALERTS_DOWN);
        List<AlertView> views = new ArrayList<>();
        if (rows == null || !rows.isArray()) {
            return views;
        }
        for (JsonNode row : rows) {
            views.add(toAlert(row));
        }
        return views;
    }

    static ForecastView toForecast(JsonNode run) {
        List<String> warnings = new ArrayList<>();
        JsonNode warningNodes = run.path("parameters").path("warnings");
        if (warningNodes.isArray()) {
            for (JsonNode warning : warningNodes) {
                if (warning.isTextual()) {
                    warnings.add(warning.asText());
                }
            }
        }
        List<ForecastLineView> lines = new ArrayList<>();
        JsonNode values = run.path("values");
        if (values.isArray()) {
            for (JsonNode value : values) {
                String category = text(value, "category");
                String display = text(value, "display_name");
                if (display == null || display.isBlank()) {
                    display = category;
                }
                lines.add(new ForecastLineView(
                        category,
                        display,
                        text(value, "forecast_period"),
                        text(value, "baseline_amount")));
            }
        }
        return new ForecastView(
                text(run, "run_id"),
                text(run, "forecasting_method"),
                text(run, "history_start"),
                text(run, "history_end"),
                warnings,
                lines);
    }

    static AlertView toAlert(JsonNode row) {
        return new AlertView(
                text(row, "severity"),
                text(row, "message"),
                text(row, "resolutionStatus"));
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        return value.asText();
    }

    private JsonNode get(RestClient client, String path, String downMessage) {
        try {
            return client.get().uri(path).retrieve().body(JsonNode.class);
        } catch (RestClientResponseException ex) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, remoteMessage(ex));
        } catch (ResourceAccessException ex) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, downMessage);
        }
    }

    private JsonNode post(RestClient client, String path, Object body, String downMessage) {
        try {
            return client.post().uri(path).body(body).retrieve().body(JsonNode.class);
        } catch (RestClientResponseException ex) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, remoteMessage(ex));
        } catch (ResourceAccessException ex) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, downMessage);
        }
    }

    /** Python sends {"detail": "..."}. Anything else becomes a short fallback. */
    private static String remoteMessage(RestClientResponseException ex) {
        String body = ex.getResponseBodyAsString();
        int detail = body.indexOf("\"detail\"");
        if (detail >= 0) {
            int quote = body.indexOf('"', body.indexOf(':', detail) + 1);
            int end = quote < 0 ? -1 : body.indexOf('"', quote + 1);
            if (quote >= 0 && end > quote) {
                return body.substring(quote + 1, end);
            }
        }
        return "The forecasting or alerts service rejected the request.";
    }

    private AppUser requireUser(AuthenticatedUser principal) {
        if (principal == null || principal.userId() == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, LOGIN_AGAIN);
        }
        return users.findById(principal.userId())
                .filter(AppUser::isActive)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, LOGIN_AGAIN));
    }

    private static RestClient client(String baseUrl, Duration readTimeout) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(readTimeout);
        String root = baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
        return RestClient.builder().baseUrl(root).requestFactory(factory).build();
    }
}

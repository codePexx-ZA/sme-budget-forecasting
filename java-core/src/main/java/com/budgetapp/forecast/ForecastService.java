package com.budgetapp.forecast;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import okhttp3.*;
import java.io.IOException;

public class ForecastService {

    // Base URL of the Python forecasting service running in Docker
    private static final String BASE_URL = "http://localhost:5090";
    private OkHttpClient client;
    private Gson gson;

    public ForecastService() {
        client = new OkHttpClient();
        gson = new Gson();
    }

    /**
     * Triggers a new forecast run for a business unit.
     * Calls POST /api/forecasts on the Python service.
     * Both Moving Average and Exponential Smoothing are run.
     *
     * @param unitId the business unit ID to forecast for
     * @param frequency annual, quarterly or monthly
     * @param horizon number of periods to forecast (1 to 3)
     * @param windowSize window size for Moving Average (default 3)
     * @param alpha smoothing factor for Exponential Smoothing (default 0.5)
     * @return the response from the Python service as a String
     */
    public String runForecast(String unitId, String frequency, int horizon, int windowSize, double alpha) {
        // Build the request body as JSON
        JsonObject body = new JsonObject();
        body.addProperty("unit_id", unitId);
        body.addProperty("frequency", frequency);
        body.addProperty("horizon", horizon);
        body.addProperty("window_size", windowSize);
        body.addProperty("alpha", alpha);

        RequestBody requestBody = RequestBody.create(
            body.toString(),
            MediaType.parse("application/json")
        );

        Request request = new Request.Builder()
            .url(BASE_URL + "/api/forecasts")
            .post(requestBody)
            .build();

        try (Response response = client.newCall(request).execute()) {
            if (response.isSuccessful() && response.body() != null) {
                String responseBody = response.body().string();
                System.out.println("Forecast run successful.");
                return responseBody;
            } else {
                System.out.println("Forecast request failed: " + response.code());
                return null;
            }
        } catch (IOException e) {
            System.out.println("Error calling forecast service: " + e.getMessage());
            return null;
        }
    }

    /**
     * Retrieves all saved forecast runs for a business unit.
     * Calls GET /api/forecasts?unit_id=... on the Python service.
     *
     * @param unitId the business unit ID
     * @return list of forecast runs as a String
     */
    public String getForecastsForUnit(String unitId) {
        Request request = new Request.Builder()
            .url(BASE_URL + "/api/forecasts?unit_id=" + unitId)
            .get()
            .build();

        try (Response response = client.newCall(request).execute()) {
            if (response.isSuccessful() && response.body() != null) {
                return response.body().string();
            } else {
                System.out.println("Failed to get forecasts: " + response.code());
                return null;
            }
        } catch (IOException e) {
            System.out.println("Error retrieving forecasts: " + e.getMessage());
            return null;
        }
    }

    /**
     * Retrieves a single forecast run with all its values.
     * Calls GET /api/forecasts/{run_id} on the Python service.
     *
     * @param runId the forecast run ID
     * @return forecast run details as a String
     */
    public String getForecastById(String runId) {
        Request request = new Request.Builder()
            .url(BASE_URL + "/api/forecasts/" + runId)
            .get()
            .build();

        try (Response response = client.newCall(request).execute()) {
            if (response.isSuccessful() && response.body() != null) {
                return response.body().string();
            } else {
                System.out.println("Failed to get forecast: " + response.code());
                return null;
            }
        } catch (IOException e) {
            System.out.println("Error retrieving forecast: " + e.getMessage());
            return null;
        }
    }

    /**
     * Deletes a forecast run and all its values.
     * Calls DELETE /api/forecasts/{run_id} on the Python service.
     *
     * @param runId the forecast run ID to delete
     * @return true if deleted successfully, false otherwise
     */
    public boolean deleteForecast(String runId) {
        Request request = new Request.Builder()
            .url(BASE_URL + "/api/forecasts/" + runId)
            .delete()
            .build();

        try (Response response = client.newCall(request).execute()) {
            if (response.isSuccessful()) {
                System.out.println("Forecast deleted: " + runId);
                return true;
            } else {
                System.out.println("Failed to delete forecast: " + response.code());
                return false;
            }
        } catch (IOException e) {
            System.out.println("Error deleting forecast: " + e.getMessage());
            return false;
        }
    }

    /**
     * Checks if the Python forecasting service is running.
     * Calls GET /health on the Python service.
     *
     * @return true if service is up, false otherwise
     */
    public boolean checkHealth() {
        Request request = new Request.Builder()
            .url(BASE_URL + "/health")
            .get()
            .build();

        try (Response response = client.newCall(request).execute()) {
            return response.isSuccessful();
        } catch (IOException e) {
            System.out.println("Forecast service is not reachable: " + e.getMessage());
            return false;
        }
    }
}
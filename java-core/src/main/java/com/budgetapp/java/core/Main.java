package com.budgetapp.java.core;

import com.budgetapp.service.BudgetService;
import com.budgetapp.entities.BudgetSubmission;
import com.budgetapp.entities.BusinessUnit;
import com.budgetapp.forecast.ForecastService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;
import java.util.List;

public class Main {

    // Seed data unit ID from the database
    private static final String TEST_UNIT_ID = "20000000000000000000000000000001";

    public static void main(String[] args) {

        System.out.println("=== Budget Prediction App - Full System Test ===");

        EntityManagerFactory emf = null;
        EntityManager em = null;

        try {
            // Test 1: Connect to database
            System.out.println("\n[1] Connecting to database...");
            emf = Persistence.createEntityManagerFactory("budgetPU");
            em = emf.createEntityManager();
            System.out.println("Connection successful.");

            // Test 2: Read a business unit from seed data
            System.out.println("\n[2] Reading business unit from database...");
            BusinessUnit unit = em.find(BusinessUnit.class, TEST_UNIT_ID);
            if (unit != null) {
                System.out.println("Found unit: " + unit.getUnitName());
            } else {
                System.out.println("No unit found - check seed data.");
            }

            // Test 3: List all submissions for that unit
            System.out.println("\n[3] Listing submissions for unit...");
            BudgetService service = new BudgetService();
            List<BudgetSubmission> submissions = service.getSubmissionsForUnit(TEST_UNIT_ID);
            if (submissions.isEmpty()) {
                System.out.println("No submissions found.");
            } else {
                for (BudgetSubmission s : submissions) {
                    System.out.println("Submission: " + s.getSubmissionId() + " | Status: " + s.getStatus());
                }
            }

            // Test 4: Check forecast service health
            System.out.println("\n[4] Checking forecast service...");
            ForecastService forecastService = new ForecastService();
            boolean isUp = forecastService.checkHealth();
            if (isUp) {
                System.out.println("Forecast service is running.");
            } else {
                System.out.println("Forecast service is not reachable.");
            }

            // Test 5: Get existing forecasts for the unit
            System.out.println("\n[5] Retrieving existing forecasts for unit...");
            String forecasts = forecastService.getForecastsForUnit(TEST_UNIT_ID);
            if (forecasts != null) {
                System.out.println("Forecasts retrieved successfully.");
                System.out.println(forecasts);
            } else {
                System.out.println("No forecasts found or service unavailable.");
            }

            // Test 6: Run a new forecast
            System.out.println("\n[6] Running a new forecast...");
            String result = forecastService.runForecast(TEST_UNIT_ID, "annual", 3, 3, 0.5);
            if (result != null) {
                System.out.println("Forecast result:");
                System.out.println(result);
            } else {
                System.out.println("Forecast run failed.");
            }

            System.out.println("\n=== All tests completed ===");

        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
            e.printStackTrace();
        } finally {
            if (em != null) em.close();
            if (emf != null) emf.close();
        }
    }
}
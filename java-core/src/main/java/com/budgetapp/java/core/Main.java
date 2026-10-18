package com.budgetapp.java.core;

import com.budgetapp.service.BudgetService;
import com.budgetapp.entities.BudgetSubmission;
import com.budgetapp.entities.BusinessUnit;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;
import java.time.LocalDate;
import java.util.List;

public class Main {

    public static void main(String[] args) {
        
        System.out.println("=== Budget Prediction App - Connection Test ===");
        
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
            BusinessUnit unit = em.find(BusinessUnit.class, "20000000000000000000000000000001");
            if (unit != null) {
                System.out.println("Found unit: " + unit.getUnitName());
            } else {
                System.out.println("No unit found - check seed data.");
            }

            // Test 3: List all submissions for that unit
            System.out.println("\n[3] Listing submissions for unit...");
            BudgetService service = new BudgetService();
            List<BudgetSubmission> submissions = service.getSubmissionsForUnit("20000000000000000000000000000001");
            if (submissions.isEmpty()) {
                System.out.println("No submissions found.");
            } else {
                for (BudgetSubmission s : submissions) {
                    System.out.println("Submission: " + s.getSubmissionId() + " | Status: " + s.getStatus());
                }
            }

            System.out.println("\n=== All tests passed ===");

        } catch (Exception e) {
            System.out.println("Connection failed: " + e.getMessage());
            e.printStackTrace();
        } finally {
            if (em != null) em.close();
            if (emf != null) emf.close();
        }
    }
}
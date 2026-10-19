package com.budgetapp.dao;

import com.budgetapp.entities.BudgetSubmission;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;
import java.util.List;

// This class represents a single financial line item and maps to the 'financial_line_item' table
// Each line item stores one amount for one category for one period within a submission

public class BudgetSubmissionDAO {

    private EntityManagerFactory emf;

    public BudgetSubmissionDAO() {
        emf = Persistence.createEntityManagerFactory("budgetPU");
    }

    // CREATE
    public void save(BudgetSubmission submission) {
        EntityManager em = emf.createEntityManager();
        try {
            em.getTransaction().begin();
            // Generate ID if not set
            if (submission.getSubmissionId() == null || submission.getSubmissionId().isEmpty()) {
                submission.setSubmissionId(java.util.UUID.randomUUID().toString().replace("-", ""));
            }
            em.persist(submission);
            em.getTransaction().commit();
            System.out.println("Submission saved: " + submission.getSubmissionId());
        } catch (Exception e) {
            em.getTransaction().rollback();
            System.out.println("Error saving submission: " + e.getMessage());
        } finally {
            em.close();
        }
    }

    // READ - get one by ID
    public BudgetSubmission findById(String submissionId) {
        EntityManager em = emf.createEntityManager();
        try {
            return em.find(BudgetSubmission.class, submissionId);
        } finally {
            em.close();
        }
    }

    // READ - get all submissions for a business unit
    public List<BudgetSubmission> findByUnitId(String unitId) {
        EntityManager em = emf.createEntityManager();
        try {
            return em.createQuery(
                "SELECT s FROM BudgetSubmission s WHERE s.unit.unitId = :unitId ORDER BY s.periodStart DESC",
                BudgetSubmission.class)
                .setParameter("unitId", unitId)
                .getResultList();
        } finally {
            em.close();
        }
    }

    // UPDATE
    public void update(BudgetSubmission submission) {
        EntityManager em = emf.createEntityManager();
        try {
            em.getTransaction().begin();
            em.merge(submission);
            em.getTransaction().commit();
            System.out.println("Submission updated: " + submission.getSubmissionId());
        } catch (Exception e) {
            em.getTransaction().rollback();
            System.out.println("Error updating submission: " + e.getMessage());
        } finally {
            em.close();
        }
    }

    // DELETE
    public void delete(String submissionId) {
        EntityManager em = emf.createEntityManager();
        try {
            em.getTransaction().begin();
            BudgetSubmission submission = em.find(BudgetSubmission.class, submissionId);
            if (submission != null) {
                em.remove(submission);
                System.out.println("Submission deleted: " + submissionId);
            } else {
                System.out.println("Submission not found: " + submissionId);
            }
            em.getTransaction().commit();
        } catch (Exception e) {
            em.getTransaction().rollback();
            System.out.println("Error deleting submission: " + e.getMessage());
        } finally {
            em.close();
        }
    }

    public void close() {
        if (emf != null) emf.close();
    }
}
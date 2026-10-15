package com.budgetapp.dao;

import com.budgetapp.entities.FinancialLineItem;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;
import java.util.List;

public class FinancialLineItemDAO {

    private EntityManagerFactory emf;

    public FinancialLineItemDAO() {
        emf = Persistence.createEntityManagerFactory("budgetPU");
    }

    // CREATE
    public void save(FinancialLineItem item) {
        EntityManager em = emf.createEntityManager();
        try {
            em.getTransaction().begin();
            if (item.getLineItemId() == null || item.getLineItemId().isEmpty()) {
                item.setLineItemId(java.util.UUID.randomUUID().toString().replace("-", ""));
            }
            em.persist(item);
            em.getTransaction().commit();
            System.out.println("Line item saved: " + item.getLineItemId());
        } catch (Exception e) {
            em.getTransaction().rollback();
            System.out.println("Error saving line item: " + e.getMessage());
        } finally {
            em.close();
        }
    }

    // READ - get one by ID
    public FinancialLineItem findById(String lineItemId) {
        EntityManager em = emf.createEntityManager();
        try {
            return em.find(FinancialLineItem.class, lineItemId);
        } finally {
            em.close();
        }
    }

    // READ - get all line items for a submission
    public List<FinancialLineItem> findBySubmissionId(String submissionId) {
        EntityManager em = emf.createEntityManager();
        try {
            return em.createQuery(
                "SELECT f FROM FinancialLineItem f WHERE f.submission.submissionId = :submissionId ORDER BY f.periodStart ASC",
                FinancialLineItem.class)
                .setParameter("submissionId", submissionId)
                .getResultList();
        } finally {
            em.close();
        }
    }

    // READ - get all line items for a specific category
    public List<FinancialLineItem> findByCategory(String submissionId, String category) {
        EntityManager em = emf.createEntityManager();
        try {
            return em.createQuery(
                "SELECT f FROM FinancialLineItem f WHERE f.submission.submissionId = :submissionId AND f.category = :category ORDER BY f.periodStart ASC",
                FinancialLineItem.class)
                .setParameter("submissionId", submissionId)
                .setParameter("category", category)
                .getResultList();
        } finally {
            em.close();
        }
    }

    // UPDATE
    public void update(FinancialLineItem item) {
        EntityManager em = emf.createEntityManager();
        try {
            em.getTransaction().begin();
            em.merge(item);
            em.getTransaction().commit();
            System.out.println("Line item updated: " + item.getLineItemId());
        } catch (Exception e) {
            em.getTransaction().rollback();
            System.out.println("Error updating line item: " + e.getMessage());
        } finally {
            em.close();
        }
    }

    // DELETE
    public void delete(String lineItemId) {
        EntityManager em = emf.createEntityManager();
        try {
            em.getTransaction().begin();
            FinancialLineItem item = em.find(FinancialLineItem.class, lineItemId);
            if (item != null) {
                em.remove(item);
                System.out.println("Line item deleted: " + lineItemId);
            } else {
                System.out.println("Line item not found: " + lineItemId);
            }
            em.getTransaction().commit();
        } catch (Exception e) {
            em.getTransaction().rollback();
            System.out.println("Error deleting line item: " + e.getMessage());
        } finally {
            em.close();
        }
    }

    public void close() {
        if (emf != null) emf.close();
    }
}
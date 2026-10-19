package com.budgetapp.service;

import com.budgetapp.dao.BudgetSubmissionDAO;
import com.budgetapp.dao.FinancialLineItemDAO;
import com.budgetapp.entities.BudgetSubmission;
import com.budgetapp.entities.FinancialLineItem;
import java.time.OffsetDateTime;
import java.util.List;

// This class contains the business logic for managing budget submissions and line items.
// It validates data before passing it to the DAO layer and enforces workflow rules
// such as only allowing draft submissions to be deleted or submitted for approval

public class BudgetService {

    private BudgetSubmissionDAO submissionDAO;
    private FinancialLineItemDAO lineItemDAO;

    public BudgetService() {
        submissionDAO = new BudgetSubmissionDAO();
        lineItemDAO = new FinancialLineItemDAO();
    }

    // Create a new draft submission
    public boolean createSubmission(BudgetSubmission submission) {
        if (submission.getUnit() == null) {
            System.out.println("Validation failed: business unit is required.");
            return false;
        }
        if (submission.getPeriodStart() == null || submission.getPeriodEnd() == null) {
            System.out.println("Validation failed: period start and end are required.");
            return false;
        }
        if (submission.getPeriodEnd().isBefore(submission.getPeriodStart())) {
            System.out.println("Validation failed: period end cannot be before period start.");
            return false;
        }
        submission.setStatus("draft");
        submissionDAO.save(submission);
        return true;
    }

    // Submit a draft for approval
    public boolean submitForApproval(String submissionId) {
        BudgetSubmission submission = submissionDAO.findById(submissionId);
        if (submission == null) {
            System.out.println("Submission not found: " + submissionId);
            return false;
        }
        if (!submission.getStatus().equals("draft")) {
            System.out.println("Only draft submissions can be submitted for approval.");
            return false;
        }
        submission.setStatus("submitted");
        submission.setSubmittedAt(OffsetDateTime.now());
        submissionDAO.update(submission);
        return true;
    }

    // Get all submissions for a unit
    public List<BudgetSubmission> getSubmissionsForUnit(String unitId) {
        return submissionDAO.findByUnitId(unitId);
    }

    // Get one submission by ID
    public BudgetSubmission getSubmission(String submissionId) {
        return submissionDAO.findById(submissionId);
    }

    // Delete a draft submission
    public boolean deleteSubmission(String submissionId) {
        BudgetSubmission submission = submissionDAO.findById(submissionId);
        if (submission == null) {
            System.out.println("Submission not found: " + submissionId);
            return false;
        }
        if (!submission.getStatus().equals("draft")) {
            System.out.println("Only draft submissions can be deleted.");
            return false;
        }
        submissionDAO.delete(submissionId);
        return true;
    }

    // Add a line item to a submission
    public boolean addLineItem(FinancialLineItem item) {
        if (item.getSubmission() == null) {
            System.out.println("Validation failed: submission is required.");
            return false;
        }
        if (item.getCategory() == null || item.getCategory().isEmpty()) {
            System.out.println("Validation failed: category is required.");
            return false;
        }
        if (item.getAmount() == null) {
            System.out.println("Validation failed: amount is required.");
            return false;
        }
        if (item.getPeriodStart() == null) {
            System.out.println("Validation failed: period start is required.");
            return false;
        }
        lineItemDAO.save(item);
        return true;
    }

    // Get all line items for a submission
    public List<FinancialLineItem> getLineItems(String submissionId) {
        return lineItemDAO.findBySubmissionId(submissionId);
    }

    // Update a line item
    public void updateLineItem(FinancialLineItem item) {
        lineItemDAO.update(item);
    }

    // Delete a line item
    public void deleteLineItem(String lineItemId) {
        lineItemDAO.delete(lineItemId);
    }

    public void close() {
        submissionDAO.close();
        lineItemDAO.close();
    }
}
package com.smebudget.api.submissions;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** Loads and saves budget line items. */
public interface FinancialLineItemRepository extends JpaRepository<FinancialLineItem, String> {

    List<FinancialLineItem> findBySubmissionId(String submissionId);
}

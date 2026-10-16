package com.smebudget.api.submissions;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FinancialLineItemRepository extends JpaRepository<FinancialLineItem, String> {

    List<FinancialLineItem> findBySubmissionId(String submissionId);
}

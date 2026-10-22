package com.smebudget.api.submissions;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** Loads and saves approval decisions. */
public interface ApprovalActionRepository extends JpaRepository<ApprovalAction, String> {

    List<ApprovalAction> findBySubmissionId(String submissionId);
}

package com.smebudget.api.submissions;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** Reads and writes budget_submission. The table itself lives in database/init. */
public interface BudgetSubmissionRepository extends JpaRepository<BudgetSubmission, String> {

    List<BudgetSubmission> findByUnitId(String unitId);

    /** True when some other submission points at this one via replaces_submission_id. */
    boolean existsByReplacesSubmissionId(String submissionId);
}

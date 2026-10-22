package com.smebudget.api.submissions;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** Loads and saves budget submissions. */
public interface BudgetSubmissionRepository extends JpaRepository<BudgetSubmission, String> {

    List<BudgetSubmission> findByUnitId(String unitId);

    /** True when some other submission points at this one via replaces_submission_id. */
    boolean existsByReplacesSubmissionId(String submissionId);
}

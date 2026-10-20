package com.smebudget.api.targets;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SavedAlertRepository extends JpaRepository<SavedAlert, String> {

    List<SavedAlert> findByResolutionStatusOrderByCreatedAtDesc(String resolutionStatus);
}

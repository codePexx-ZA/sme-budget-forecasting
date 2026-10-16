package com.smebudget.api.submissions;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LineCategoryRepository extends JpaRepository<LineCategory, String> {

    List<LineCategory> findAllByOrderBySortOrderAsc();
}

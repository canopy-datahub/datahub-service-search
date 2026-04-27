package org.canopyplatform.canopy.searchservice.repositories;

import org.canopyplatform.canopy.searchservice.models.SearchLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SearchLogRepository extends JpaRepository<SearchLog, Long> {}

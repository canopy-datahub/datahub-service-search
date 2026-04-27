package org.canopyplatform.canopy.searchservice.service;

import org.canopyplatform.canopy.searchservice.mappers.SearchQueryMapper;
import org.canopyplatform.canopy.searchservice.models.SearchQuery;
import org.canopyplatform.canopy.searchservice.repositories.SearchLogRepository;
import org.canopyplatform.canopy.searchservice.models.SearchLog;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SearchQueryLogger {

    private final SearchQueryMapper searchQueryMapper;
    private final SearchLogRepository searchLogRepository;

    public void logSearchQuery(SearchQuery searchQuery){
        SearchLog logEntry = searchQueryMapper.mapQueryToLog(searchQuery);
        searchLogRepository.save(logEntry);
    }

}

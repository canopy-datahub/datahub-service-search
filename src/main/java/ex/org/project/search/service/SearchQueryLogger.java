package ex.org.project.search.service;

import ex.org.project.search.mappers.SearchQueryMapper;
import ex.org.project.search.models.SearchLog;
import ex.org.project.search.models.SearchQuery;
import ex.org.project.search.repositories.SearchLogRepository;
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

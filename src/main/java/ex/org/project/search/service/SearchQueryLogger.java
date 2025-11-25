package ex.org.project.search.service;

import ex.org.project.search.models.SearchLog;
import ex.org.project.search.models.SearchQuery;
import ex.org.project.search.repositories.SearchLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SearchQueryLogger {

    private final SearchLogRepository searchLogRepository;

    public void logSearchQuery(SearchQuery searchQuery){
        SearchLog logEntry = new SearchLog();
        logEntry.setQuery(searchQuery.getQ());
        searchLogRepository.save(logEntry);
    }

}

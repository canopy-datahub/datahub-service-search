package ex.org.project.search.mappers;

import ex.org.project.search.models.SearchQuery;
import ex.org.project.search.models.SearchLog;

public interface SearchQueryMapper {

    SearchLog mapQueryToLog(SearchQuery searchQuery);

}

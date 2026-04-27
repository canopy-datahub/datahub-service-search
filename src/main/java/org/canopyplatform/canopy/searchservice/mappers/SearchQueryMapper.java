package org.canopyplatform.canopy.searchservice.mappers;

import org.canopyplatform.canopy.searchservice.models.SearchQuery;
import org.canopyplatform.canopy.searchservice.models.SearchLog;

public interface SearchQueryMapper {

    SearchLog mapQueryToLog(SearchQuery searchQuery);

}

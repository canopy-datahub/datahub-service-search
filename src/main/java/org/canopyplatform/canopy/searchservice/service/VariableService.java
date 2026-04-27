package org.canopyplatform.canopy.searchservice.service;

import org.canopyplatform.canopy.searchservice.models.SearchQuery;
import jakarta.servlet.http.HttpServletResponse;

public interface VariableService {

    String searchVariables(SearchQuery searchQuery);

    void convertSearchStringToCSV(HttpServletResponse response, String s);

}


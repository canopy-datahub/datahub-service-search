package ex.org.project.search.service;

import ex.org.project.search.models.SearchQuery;
import jakarta.servlet.http.HttpServletResponse;

public interface VariableService {

    String searchVariables(SearchQuery searchQuery);

    void convertSearchStringToCSV(HttpServletResponse response, String s);

}


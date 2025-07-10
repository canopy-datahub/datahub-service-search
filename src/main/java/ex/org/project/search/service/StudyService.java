package ex.org.project.search.service;

import ex.org.project.search.models.SearchQuery;
import jakarta.servlet.http.HttpServletResponse;

public interface StudyService {

    String searchStudies(SearchQuery searchQuery);

    void convertSearchStringToCSV(HttpServletResponse response, String s);

    String searchAutocomplete(String query);

}

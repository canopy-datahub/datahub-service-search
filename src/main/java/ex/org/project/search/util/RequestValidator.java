package ex.org.project.search.util;

import ex.org.project.search.exceptions.MalformedRequestException;
import ex.org.project.search.models.SearchQuery;

import java.util.List;

public class RequestValidator {

    private static final String[] invalidStrings = {"</", "/>"};

    public static void validateSearchQuery(SearchQuery searchQuery) {

        List<String> requestParams = List.of(searchQuery.getQ(), searchQuery.getAdv(), searchQuery.getProp(),
                                             searchQuery.getSort());
        requestParams.stream()
                .filter(RequestValidator::containsInvalidSubstrings)
                .findAny()
                .ifPresent(invalidParam -> {
                    throw new MalformedRequestException("Invalid characters in request parameter");
                });
    }

    public static void validateStringRequestParams(List<String> requestParams) {
        requestParams.stream()
                .filter(RequestValidator::containsInvalidSubstrings)
                .findAny()
                .ifPresent(invalidParam -> {
                    throw new MalformedRequestException("Invalid characters in request parameter");
                });
    }

    private static boolean containsInvalidSubstrings(String requestParam) {
        if (requestParam == null || requestParam.isBlank()) {
            return false;
        }
        for(String s : invalidStrings) {
            if(requestParam.contains(s)){
                return true;
            }
        }
        return false;
    }
}

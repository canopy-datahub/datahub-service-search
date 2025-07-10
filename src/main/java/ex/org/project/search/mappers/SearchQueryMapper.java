package ex.org.project.search.mappers;

import ex.org.project.search.models.SearchQuery;
import ex.org.project.search.models.SearchLog;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel="spring")
public interface SearchQueryMapper {

    @Mapping(source = "searchQuery.q", target="query")
    SearchLog mapQueryToLog(SearchQuery searchQuery);

}

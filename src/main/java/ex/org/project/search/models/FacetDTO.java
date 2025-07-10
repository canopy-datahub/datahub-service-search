package ex.org.project.search.models;

import java.util.List;

public record FacetDTO (String name, List<String> facets){}

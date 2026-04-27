package org.canopyplatform.canopy.searchservice.models;

import java.util.List;

public record FacetDTO (String name, List<String> facets){}

package org.canopyplatform.canopy.searchservice.models;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import org.canopyplatform.canopy.searchservice.exceptions.MalformedRequestException;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import com.fasterxml.jackson.databind.ObjectMapper;

@Data
public class SearchQuery {

    private static final TypeReference<List<FacetDTO>> typeReference = new TypeReference<List<FacetDTO>>() {};
    private static final ObjectMapper mapper = new ObjectMapper();

    private String q = new String();
    private String adv = new String();
    private List<FacetDTO> facets = new ArrayList<>();
    private Integer page = 1;
    private Integer size = 10;
    private String prop = "title";
    private String sort = "asc";

    public void setFacets(String facets) {
        try {
            this.facets = mapper.readValue(facets, typeReference);
        }
        catch(JsonProcessingException e){
            throw new MalformedRequestException("Malformed query facets");
        }
    }

}

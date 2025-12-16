package ex.org.project.search.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import ex.org.project.search.exceptions.AdvancedSearchException;
import ex.org.project.search.models.FacetDTO;
import ex.org.project.search.models.SearchQuery;
import ex.org.project.search.exceptions.OpenSearchException;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.client.RequestOptions;
import org.opensearch.client.RestHighLevelClient;
import org.opensearch.common.unit.Fuzziness;
import org.opensearch.index.query.BoolQueryBuilder;
import org.opensearch.index.query.QueryBuilder;
import org.opensearch.index.query.QueryBuilders;
import org.opensearch.search.aggregations.AggregationBuilders;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.opensearch.search.fetch.subphase.highlight.HighlightBuilder;
import org.opensearch.search.sort.SortBuilders;
import org.opensearch.search.sort.SortOrder;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import com.opencsv.CSVWriter;

import java.io.IOException;
import java.util.*;
import java.io.StringWriter;

@Slf4j
public abstract class BaseSearchService {

    @Autowired
    protected RestHighLevelClient client;
    
    protected final SearchQueryLogger searchQueryLogger;
    protected final ObjectMapper objectMapper = new ObjectMapper();
    
    private static final Map<String, SortOrder> SORT_DIRECTION = Map.of(
            "asc", SortOrder.ASC,
            "desc", SortOrder.DESC
    );

    protected BaseSearchService(SearchQueryLogger searchQueryLogger) {
        this.searchQueryLogger = searchQueryLogger;
    }

    // Abstract methods to be implemented by subclasses
    protected abstract String getIndex();
    protected abstract Map<String, Float> getExactFields();
    protected abstract Map<String, Float> getFuzzyFields();
    protected abstract Map<String, Float> getPartialFields();
    protected abstract List<String> getAggregationFields();
    protected abstract Map<String, String> getSortingFields();
    protected abstract Float getMinScoreRatio();
    protected abstract List<String> getCSVHeaders();
    protected abstract List<String> getCSVKeys();
    protected abstract String getCSVFilename();

    public String search(SearchQuery searchQuery) {
        if (!searchQuery.getAdv().isBlank()) {
            return advancedSearch(searchQuery);
        }
        return normalSearch(searchQuery);
    }

    protected String normalSearch(SearchQuery searchQuery) {
        // set index and initialize search and query builders
        final SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        SearchRequest request = new SearchRequest(getIndex());
        BoolQueryBuilder queryBuilder = QueryBuilders.boolQuery();

        // add text string search query to the bool query
        if (!searchQuery.getQ().isBlank()) {
            applyQueryToBuilder(searchQuery.getQ(), queryBuilder);
        }
        searchSourceBuilder.query(queryBuilder);
        applyInitialSearchParameters(searchSourceBuilder, searchQuery);
        request.source(searchSourceBuilder);
        Float minScore = getScoreToFilterBy(request);

        //apply pagination, highlighting, and sorting
        applyAdditionalParameters(searchSourceBuilder, searchQuery);
        searchSourceBuilder.minScore(minScore);

        //call opensearch and return data / throw error
        try {
            SearchResponse searchResponse = client.search(request, RequestOptions.DEFAULT);
            postProcessSearchResponse(searchResponse);
            searchQueryLogger.logSearchQuery(searchQuery);
            return searchResponse.toString();
        } catch (IOException e) {
            log.error("Error connecting to OpenSearch client", e);
            throw new OpenSearchException("Error connecting to OpenSearch client");
        }
    }

    protected void applyQueryToBuilder(String query, BoolQueryBuilder queryBuilder) {
        queryBuilder.should(QueryBuilders.queryStringQuery(query)
                .fields(getExactFields())
                .boost(1.5F));
        for (Map.Entry<String, Float> fieldToWeight : getFuzzyFields().entrySet()) {
            queryBuilder.should(QueryBuilders.matchQuery(fieldToWeight.getKey(), query)
                    .fuzziness(Fuzziness.AUTO)
                    .boost(.5F * fieldToWeight.getValue()));
        }
        queryBuilder.should(QueryBuilders.queryStringQuery(String.format("*%s*", query))
                .fields(getPartialFields())
                .boost(.8F));
    }

    protected String advancedSearch(SearchQuery searchQuery) {
        final SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        SearchRequest request = new SearchRequest(getIndex());
        BoolQueryBuilder queryBuilder = QueryBuilders.boolQuery();

        //parse the json object to create queries based on the provided rules
        try {
            JsonNode advancedQuery = objectMapper.readTree(searchQuery.getAdv());
            parseRules(queryBuilder, advancedQuery);
        } catch (JsonProcessingException e) {
            log.error("Json error", e);
            throw new AdvancedSearchException("Problem processing advanced search query");
        }
        log.debug(queryBuilder.toString());
        searchSourceBuilder.query(queryBuilder);

        //apply filters, pagination, highlighting, and sorting
        applyAdditionalParameters(searchSourceBuilder, searchQuery);

        //build final response
        request.source(searchSourceBuilder);

        //call opensearch and return response to user
        try {
            SearchResponse searchResponse = client.search(request, RequestOptions.DEFAULT);
            postProcessSearchResponse(searchResponse);
            return searchResponse.toString();
        } catch (IOException e) {
            log.error("Error connecting to OpenSearch client", e);
            throw new OpenSearchException("Error connecting to OpenSearch client");
        }
    }

    //parseRules reads data of a single hierarchical level of an advanced search query
    // each time a json object containing rules is found, it will be called on that object
    protected void parseRules(BoolQueryBuilder queryBuilder, JsonNode ruleNode) {
        //extracts rules about how the query will be  created (and/or/nand/nor)
        String combinator = ruleNode.get("combinator").asText();
        boolean negation = false;
        if (ruleNode.has("not")) {
            negation = ruleNode.get("not").asBoolean();
        }
        //checks each json object in a rules array
        // search criteria cause a query to be made
        // a rules array calls parseRules again and creates a subquery
        for (Iterator<JsonNode> it = ruleNode.get("rules").elements(); it.hasNext(); ) {
            JsonNode node = it.next();
            if (node.has("rules")) {
                BoolQueryBuilder subQuery = QueryBuilders.boolQuery();
                parseRules(subQuery, node);
                addSearchCriteria(queryBuilder, subQuery, combinator, negation);
            } else {
                QueryBuilder searchCriteria = createSearchCriteria(node);
                addSearchCriteria(queryBuilder, searchCriteria, combinator, negation);
            }
        }
    }

    //builds opensearch query based on the operator in a search criteria node
    protected QueryBuilder createSearchCriteria(JsonNode criteriaNode) {
        return switch (criteriaNode.get("operator").asText()) {
            case "contains" -> QueryBuilders.queryStringQuery(String.format("*%s*", criteriaNode.get("value").asText()))
                    .field(criteriaNode.get("field").asText());
            case "beginsWith" -> QueryBuilders.prefixQuery(criteriaNode.get("field").asText() + ".keyword",
                    criteriaNode.get("value").asText());
            case "equals" -> QueryBuilders.termQuery(criteriaNode.get("field").asText() + ".keyword",
                    criteriaNode.get("value").asText());
            default -> throw new AdvancedSearchException("Problem creating search criteria");
        };
    }

    //adds search query to bool query based on the combinator (and/or) and if it is negated
    protected void addSearchCriteria(BoolQueryBuilder queryBuilder, QueryBuilder searchCriteria, String combinator, Boolean negation) {
        switch (combinator) {
            case "and":
                if (negation) {
                    queryBuilder.mustNot(searchCriteria);
                } else {
                    queryBuilder.must(searchCriteria);
                }
                break;
            case "or":
                if (negation) {
                    BoolQueryBuilder norQuery = QueryBuilders.boolQuery();
                    queryBuilder.should(norQuery.mustNot(searchCriteria));
                } else {
                    queryBuilder.should(searchCriteria);
                }
                break;
            default:
                throw new AdvancedSearchException("Problem adding search criteria");
        }
    }

    protected void applyInitialSearchParameters(SearchSourceBuilder searchSourceBuilder, SearchQuery searchQuery) {
        BoolQueryBuilder filters = QueryBuilders.boolQuery();
        // Iterate over the selected facets and add them to the bool query as overall filters
        for (FacetDTO facet : searchQuery.getFacets()) {
            filters.filter(QueryBuilders.termsQuery(facet.name() + ".keyword", facet.facets()));
        }
        searchSourceBuilder.postFilter(filters);
        searchSourceBuilder.size(1);
    }

    protected Float getScoreToFilterBy(SearchRequest request) {
        try {
            SearchResponse maxScoreResponse = client.search(request, RequestOptions.DEFAULT);
            float maxScore = maxScoreResponse.getHits().getMaxScore();
            return maxScore * getMinScoreRatio();
        } catch (IOException e) {
            log.error("Error connecting to OpenSearch client", e);
            throw new OpenSearchException("Error connecting to OpenSearch client");
        }
    }

    //apply filters, pagination, highlighting, and sorting to the top level search source builder
    protected void applyAdditionalParameters(SearchSourceBuilder searchSourceBuilder, SearchQuery searchQuery) {
        //add filters to the aggregation fields and then subaggregate on the field
        //this should return how many new results would be added if a facet is selected
        for (String term : getAggregationFields()) {
            BoolQueryBuilder aggFilters = QueryBuilders.boolQuery();
            for (FacetDTO facet : searchQuery.getFacets()) {
                if (!facet.name().equals(term)) {
                    aggFilters.filter(QueryBuilders.termsQuery(facet.name() + ".keyword", facet.facets()));
                }
            }
            searchSourceBuilder.aggregation(AggregationBuilders.filters(term, aggFilters)
                    .subAggregation(AggregationBuilders.terms(term).field(term + ".keyword").size(300))
            );
        }
        searchSourceBuilder.highlighter(getHighlightBuilder());
        //define field to sort on and order
        if (getSortingFields().containsKey(searchQuery.getProp())) {
            if (SORT_DIRECTION.containsKey(searchQuery.getSort())) {
                searchSourceBuilder.sort(SortBuilders.fieldSort(getSortingFields().get(searchQuery.getProp())).order(SORT_DIRECTION.get(searchQuery.getSort())));
            } else {
                //exceptions for bad params to be added later so default to ascending for now
                searchSourceBuilder.sort(SortBuilders.fieldSort(getSortingFields().get(searchQuery.getProp())).order(SortOrder.ASC));
            }
        }

        //calculate starting index for page and apply to source builder
        Integer start = (searchQuery.getPage() * searchQuery.getSize()) - searchQuery.getSize();
        if (start < 0) {
            start = 0;
        }

        searchSourceBuilder.from(start);
        searchSourceBuilder.size(searchQuery.getSize());
    }

    //initializes highlighting for all searches
    protected HighlightBuilder getHighlightBuilder() {
        HighlightBuilder highlightBuilder = new HighlightBuilder();
        highlightBuilder.numOfFragments(0);
        highlightBuilder.preTags("<mark>");
        highlightBuilder.postTags("</mark>");
        highlightBuilder.highlighterType("unified");
        //highlighting in query fields
        for (Map.Entry<String, Float> field : getExactFields().entrySet()) {
            HighlightBuilder.Field highlightTitle = new HighlightBuilder.Field(field.getKey());
            highlightBuilder.field(highlightTitle);
            highlightBuilder.numOfFragments(0);
        }
        return highlightBuilder;
    }

    /**
     * Converts the results of an elastic search string to a CSV file and inserts that CSV into a HttpServletResponse.
     */
    public void convertSearchStringToCSV(HttpServletResponse response, String s) {
        //Convert the elasticsearch json String into a JSONObject and get the hits array object
        JSONObject json = new JSONObject(s);
        JSONObject hits = json.getJSONObject("hits");
        JSONArray hitsArray = hits.getJSONArray("hits");

        //Create the CSV writer and write the column headers for the file
        StringWriter stringWriter = new StringWriter();
        try (CSVWriter csvWriter = new CSVWriter(stringWriter)) {
            csvWriter.writeNext(getCSVHeaders().toArray(new String[0]));

            //Extract the data from each array element and write it to the CSV file
            for (int i = 0; i < hitsArray.length(); i++) {
                String[] rowValues = new String[getCSVKeys().size()];
                JSONObject hitsElement = hitsArray.getJSONObject(i);
                JSONObject source = hitsElement.getJSONObject("_source");
                for (int j = 0; j < getCSVKeys().size(); j++) {
                    Object o = source.get(getCSVKeys().get(j));
                    String value = String.valueOf(o);
                    rowValues[j] = value.equals("null") ? "" : value;
                }
                csvWriter.writeNext(rowValues);
            }
        } catch (IOException e) {
            log.error("Error creating CSV file", e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            return;
        }

        //Build the final response object
        String csvData = stringWriter.toString();
        response.setContentType("text/csv");
        response.setHeader("Content-Disposition", "attachment; filename=" + getCSVFilename());
        try {
            response.getWriter().write(csvData);
        } catch (IOException e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }

    // Hook method for subclasses to perform post-processing on search response
    protected void postProcessSearchResponse(SearchResponse searchResponse) {
        // Default implementation does nothing
        // Subclasses can override to add specific post-processing
    }
}

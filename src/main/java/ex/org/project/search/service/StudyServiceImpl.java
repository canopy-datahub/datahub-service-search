package ex.org.project.search.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import ex.org.project.search.exceptions.AdvancedSearchException;
import ex.org.project.search.config.QueryConfiguration;
import ex.org.project.search.models.FacetDTO;

import ex.org.project.search.models.OpensearchIndices;
import ex.org.project.search.models.SearchQuery;
import ex.org.project.search.exceptions.OpenSearchException;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.unit.Fuzziness;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.bucket.filter.ParsedFilters;
import org.elasticsearch.search.aggregations.bucket.terms.ParsedStringTerms;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.fetch.subphase.highlight.HighlightBuilder;
import org.elasticsearch.search.sort.SortBuilders;
import org.elasticsearch.search.sort.SortOrder;
import org.elasticsearch.search.suggest.SuggestBuilder;
import org.elasticsearch.search.suggest.SuggestBuilders;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import com.opencsv.CSVWriter;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.util.*;
import java.io.StringWriter;

@Slf4j
@Service
@RequiredArgsConstructor
public class StudyServiceImpl implements StudyService {

    @Autowired
    RestTemplate restTemplate;
    private RestHighLevelClient client;
    private String index;
    private String autocompleteIndex;
    @Value("${apis.host}")
    private String entityServiceHost;
    @Value("${apis.entity-service.getProps}")
    private String getPropsEndpoint;
    private final QueryConfiguration queryConfig;
    private static final Map<String, SortOrder> SORT_DIRECTION = Map.of(
            "asc", SortOrder.ASC,
            "desc", SortOrder.DESC
    );
    private static final String ESTIMATED_PARTICIPANT_RANGE = "estimated_participant_range";
    private final HighlightBuilder highlightBuilder;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final SearchQueryLogger searchQueryLogger;

    @Autowired
    StudyServiceImpl(RestHighLevelClient client, RestTemplate restTemplate, QueryConfiguration queryConfiguration,
                     OpensearchIndices indices, SearchQueryLogger searchQueryLogger){
        this.client = client;
        this.restTemplate = restTemplate;
        this.queryConfig = queryConfiguration;
        this.highlightBuilder = getHighlightBuilder();
        this.index = indices.studies();
        this.autocompleteIndex = indices.autocomplete();
        this.searchQueryLogger = searchQueryLogger;
    }

    public String searchAutocomplete(String query){
        SearchRequest request = new SearchRequest(autocompleteIndex);
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();
        SuggestBuilder suggestBuilder = new SuggestBuilder();
        suggestBuilder.addSuggestion("phrase.completion",
                                     SuggestBuilders.completionSuggestion("phrase.completion") // field for completions
                                             .prefix(query, Fuzziness.ONE) // prefix is user-supplied query
                                             .size(5)); // size is number of suggestions returned

        sourceBuilder.fetchSource("phrase", null).suggest(suggestBuilder); // only return the phrase field
        request.source(sourceBuilder);
        try {
            SearchResponse searchResponse = client.search(request, RequestOptions.DEFAULT);
            return searchResponse.getSuggest().toString();
        } catch(IOException e) {
            log.error("Error connecting to OpenSearch client", e);
            throw new OpenSearchException("Error connecting to OpenSearch client");
        }
    }

    public String searchStudies(SearchQuery searchQuery) {
        if (!searchQuery.getAdv().isBlank()) {
            return advancedSearch(searchQuery);
        }
        return normalSearch(searchQuery);
    }

    private String normalSearch(SearchQuery searchQuery){

        // set index and initialize search and query builders
        final SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        SearchRequest request = new SearchRequest(index);
        BoolQueryBuilder queryBuilder = QueryBuilders.boolQuery();

        // add text string search query to the bool query
        if(!searchQuery.getQ().isBlank()){
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
            sortEstimatedParticipantsFacets(searchResponse);
            searchQueryLogger.logSearchQuery(searchQuery);
            return searchResponse.toString();
        } catch(IOException e) {
            log.error("Error connecting to OpenSearch client", e);
            throw new OpenSearchException("Error connecting to OpenSearch client");
        }
    }

    private void applyQueryToBuilder(String query, BoolQueryBuilder queryBuilder){
        queryBuilder.should(QueryBuilders.queryStringQuery(query)
                                    .fields(queryConfig.getExactFields())
                                    .boost(1.5F));
        for(Map.Entry<String, Float> fieldToWeight : queryConfig.getFuzzyFields().entrySet()){
            queryBuilder.should(QueryBuilders.matchQuery(fieldToWeight.getKey(), query)
                                        .fuzziness(Fuzziness.AUTO)
                                        .boost(.5F * fieldToWeight.getValue()));
        }
        queryBuilder.should(QueryBuilders.queryStringQuery(String.format("*%s*", query))
                                    .fields(queryConfig.getPartialFields())
                                    .boost(.8F));
    }

    private String advancedSearch(SearchQuery searchQuery){
        final SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        SearchRequest request = new SearchRequest(index);
        BoolQueryBuilder queryBuilder = QueryBuilders.boolQuery();

        //parse the json object to create queries based on the provided rules
        try {
            JsonNode advancedQuery = objectMapper.readTree(searchQuery.getAdv());
            parseRules(queryBuilder, advancedQuery);
        } catch (JsonProcessingException e){
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
            sortEstimatedParticipantsFacets(searchResponse);
            return searchResponse.toString();
        } catch(IOException e) {
            log.error("Error connecting to OpenSearch client", e);
            throw new OpenSearchException("Error connecting to OpenSearch client");
        }
    }

    //parseRules reads data of a single hierarchical level of an advanced search query
    // each time a json object containing rules is found, it will be called on that object
    private void parseRules(BoolQueryBuilder queryBuilder, JsonNode ruleNode){
        //extracts rules about how the query will be  created (and/or/nand/nor)
        String combinator = ruleNode.get("combinator").asText();
        boolean negation = false;
        if(ruleNode.has("not")){
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
            } else{
                QueryBuilder searchCriteria = createSearchCriteria(node);
                addSearchCriteria(queryBuilder, searchCriteria, combinator, negation);
            }
        }
    }

    //builds opensearch query based on the operator in a search criteria node
    private QueryBuilder createSearchCriteria(JsonNode criteriaNode){
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
    private void addSearchCriteria(BoolQueryBuilder queryBuilder, QueryBuilder searchCriteria, String combinator, Boolean negation){
        switch (combinator){
            case "and":
                if(negation){
                    queryBuilder.mustNot(searchCriteria);
                } else {
                    queryBuilder.must(searchCriteria);
                }
                break;
            case "or":
                if(negation){
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

    private void applyInitialSearchParameters(SearchSourceBuilder searchSourceBuilder, SearchQuery searchQuery) {
        BoolQueryBuilder filters = QueryBuilders.boolQuery();
        // Iterate over the selected facets and add them to the bool query as overall filters
        for (FacetDTO facet : searchQuery.getFacets()) {
            filters.filter(QueryBuilders.termsQuery(facet.name() + ".keyword", facet.facets()));
        }
        searchSourceBuilder.postFilter(filters);
        searchSourceBuilder.size(1);
    }

    private Float getScoreToFilterBy(SearchRequest  request){
        try {
            SearchResponse maxScoreResponse = client.search(request, RequestOptions.DEFAULT);
            float maxScore = maxScoreResponse.getHits().getMaxScore();
            return maxScore * queryConfig.getMinScoreRatio();
        } catch(IOException e) {
            log.error("Error connecting to OpenSearch client", e);
            throw new OpenSearchException("Error connecting to OpenSearch client");
        }
    }

    //apply filters, pagination, highlighting, and sorting to the top level search source builder
    private void applyAdditionalParameters(SearchSourceBuilder searchSourceBuilder, SearchQuery searchQuery){
        //add filters to the aggregation fields and then subaggregate on the field
        //this should return how many new results would be added if a facet is selected
        for(String term : queryConfig.getAggregationFields()) {
            BoolQueryBuilder aggFilters = QueryBuilders.boolQuery();
            for (FacetDTO facet : searchQuery.getFacets()) {
                if(!facet.name().equals(term)) {
                    aggFilters.filter(QueryBuilders.termsQuery(facet.name() + ".keyword", facet.facets()));
                }
            }
            searchSourceBuilder.aggregation(AggregationBuilders.filters(term, aggFilters)
                    .subAggregation(AggregationBuilders.terms(term).field(term + ".keyword").size(300))
            );
        }
        searchSourceBuilder.highlighter(highlightBuilder);
        //define field to sort on and order
        if(queryConfig.getSortingFields().containsKey(searchQuery.getProp())){
            if(SORT_DIRECTION.containsKey(searchQuery.getSort())){
                searchSourceBuilder.sort(SortBuilders.fieldSort(queryConfig.getSortingFields().get(searchQuery.getProp())).order(SORT_DIRECTION.get(searchQuery.getSort())));
            } else {
                //exceptions for bad params to be added later so default to ascending for now
                searchSourceBuilder.sort(SortBuilders.fieldSort(queryConfig.getSortingFields().get(searchQuery.getProp())).order(SortOrder.ASC));
            }
        }

        //calculate starting index for page and apply to source builder
        Integer start = (searchQuery.getPage() * searchQuery.getSize()) - searchQuery.getSize();
        if(start  < 0){
            start = 0;
        }

        searchSourceBuilder.from(start);
        searchSourceBuilder.size(searchQuery.getSize());
    }

    //initializes highlighting for all searches
    private HighlightBuilder getHighlightBuilder() {
        HighlightBuilder highlightBuilder = new HighlightBuilder();
        highlightBuilder.numOfFragments(0);
        highlightBuilder.preTags("<mark>");
        highlightBuilder.postTags("</mark>");
        highlightBuilder.highlighterType("unified");
        //highlighting in query fields
        for(Map.Entry<String, Float>  field : queryConfig.getExactFields().entrySet()){
            HighlightBuilder.Field highlightTitle = new HighlightBuilder.Field(field.getKey());
            highlightBuilder.field(highlightTitle);
            highlightBuilder.numOfFragments(0);
        }
        return highlightBuilder;
    }


    /**
     * Converts the results of an elastic search string to a CSV file and inserts that CSV into a HttpServletResponse.
     * Used the EntityService getProps API endpoint to select which fields will be extracted from the search results
     * and inserted into the CSV.
     */
    public void convertSearchStringToCSV(HttpServletResponse response, String s){

        Map<String, Object> restResponse = restTemplate.getForObject(entityServiceHost + getPropsEndpoint, Map.class);

        //Extract the data from the response of the getProps call
        List<String> headerList = new ArrayList<>();
        List<String> keyList = new ArrayList<>();
        processRestResponse(restResponse, headerList, keyList);

        //Convert the elasticsearch json String into a JSONObject and get the hits array object
        JSONObject json = new JSONObject(s);
        JSONObject hits = json.getJSONObject("hits");
        JSONArray hitsArray = hits.getJSONArray("hits");

        //Create the CSV writer and write the column headers for the file
        StringWriter stringWriter = new StringWriter();
        CSVWriter csvWriter = new CSVWriter(stringWriter);
        csvWriter.writeNext(headerList.toArray(new String[0]));

        //Extract the data from each array element and write it to the CSV file
        for (int i = 0; i < hitsArray.length(); i++){
            String[] rowValues = new String[keyList.size()];
            JSONObject hitsElement = hitsArray.getJSONObject(i);
            JSONObject source = hitsElement.getJSONObject("_source");
            for (int j = 0; j < keyList.size(); j++){
                Object o = source.get(keyList.get(j));
                String value = String.valueOf(o);
                rowValues[j] = value.equals("null") ? "" : value;
            }
            csvWriter.writeNext(rowValues);
        }

        //Build the final response object
        String csvData = stringWriter.toString();
        response.setContentType("text/csv");
        response.setHeader("Content-Disposition", "attachment; filename=StudyExplorerResults.csv");
        try {
            response.getWriter().write(csvData);
        } catch (IOException e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * This method extracts the data from the EntityService getProps API call into two separate lists
     * headers and keys used for building a CSV file.
     */
    private void processRestResponse(Map<String, Object> restResponse, List<String> headers, List<String> keys){
        ArrayList<LinkedHashMap> titleNode = (ArrayList<LinkedHashMap>) restResponse.get("Title");
        ArrayList<LinkedHashMap> repNode = (ArrayList<LinkedHashMap>) restResponse.get("Representative");
        ArrayList<LinkedHashMap> detailNode = (ArrayList<LinkedHashMap>) restResponse.get("Detail");

        for (LinkedHashMap m : titleNode){
            headers.add((String) m.get("displayLabel"));
            keys.add((String) m.get("entityPropertyName"));
        }
        for (LinkedHashMap m : repNode){
            headers.add((String) m.get("displayLabel"));
            keys.add((String) m.get("entityPropertyName"));
        }
    }

    /**
     * Method which sorts the filter facets so they are in a logical order when displayed on the front-end.
     * @param searchResponse response from the elastic search call
     */
    private void sortEstimatedParticipantsFacets(SearchResponse searchResponse){

        //Retrieve the ParsedStringTerms, which is the list of facets we want to sort.
        ParsedFilters parsedFilters = searchResponse.getAggregations().get(ESTIMATED_PARTICIPANT_RANGE);
        ParsedFilters.ParsedBucket buckets = parsedFilters.getBucketByKey("0");
        Aggregations agg = buckets.getAggregations();
        ParsedStringTerms terms = agg.get(ESTIMATED_PARTICIPANT_RANGE);

        //Run the sort using a custom comparator
        terms.getBuckets().sort((Comparator<Terms.Bucket>) (o1, o2) -> {

            //We can sort using the first number of the name string, so retrieve that
            String s1 = o1.getKeyAsString().split(" ")[0];
            String s2 = o2.getKeyAsString().split(" ")[0];

            int value1;
            int value2;

            //One of the names starts with the word "Greater", so here we identify that and sort it last
            try {
                value1 = Integer.parseInt(s1);
            } catch (NumberFormatException e){
                return 1;
            }
            try {
                value2 = Integer.parseInt(s2);
            } catch (NumberFormatException e){
                return -1;
            }

            //Do the actual comparison for sorting
            if (value1 < value2){
                return -1;
            } else if (value1 > value2){
                return 1;
            }
            //This should never be reached, since all the facets returned by elastic search are different
            return 0;
        });
    }

}

package org.canopyplatform.canopy.searchservice.service;

import org.canopyplatform.canopy.searchservice.config.QueryConfiguration;
import org.canopyplatform.canopy.searchservice.models.OpensearchIndices;
import org.canopyplatform.canopy.searchservice.models.SearchQuery;
import org.canopyplatform.canopy.searchservice.exceptions.OpenSearchException;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.client.RequestOptions;
import org.opensearch.client.RestHighLevelClient;
import org.opensearch.common.unit.Fuzziness;
import org.opensearch.search.aggregations.Aggregations;
import org.opensearch.search.aggregations.bucket.filter.ParsedFilters;
import org.opensearch.search.aggregations.bucket.terms.ParsedStringTerms;
import org.opensearch.search.aggregations.bucket.terms.Terms;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.opensearch.search.suggest.SuggestBuilder;
import org.opensearch.search.suggest.SuggestBuilders;
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
public class StudyServiceImpl extends BaseSearchService implements StudyService {

    @Autowired
    RestTemplate restTemplate;
    private String index;
    private String autocompleteIndex;
    @Value("${apis.host}")
    private String entityServiceHost;
    @Value("${apis.entity-service.getProps}")
    private String getPropsEndpoint;
    private final QueryConfiguration queryConfig;
    private static final String ESTIMATED_PARTICIPANT_RANGE = "estimated_participant_range";

    @Autowired
    StudyServiceImpl(RestHighLevelClient client, RestTemplate restTemplate, QueryConfiguration queryConfiguration,
                     OpensearchIndices indices, SearchQueryLogger searchQueryLogger){
        super(searchQueryLogger);
        this.restTemplate = restTemplate;
        this.queryConfig = queryConfiguration;
        this.index = indices.studies();
        this.autocompleteIndex = indices.autocomplete();
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
        return search(searchQuery);
    }

    // Override postProcessSearchResponse to add study-specific processing
    @Override
    protected void postProcessSearchResponse(SearchResponse searchResponse) {
        sortEstimatedParticipantsFacets(searchResponse);
    }

    // SearchConfiguration implementation
    @Override
    protected String getIndex() {
        return index;
    }

    @Override
    public Map<String, Float> getExactFields() {
        return queryConfig.getExactFields();
    }

    @Override
    public Map<String, Float> getFuzzyFields() {
        return queryConfig.getFuzzyFields();
    }

    @Override
    public Map<String, Float> getPartialFields() {
        return queryConfig.getPartialFields();
    }

    @Override
    public List<String> getAggregationFields() {
        return queryConfig.getAggregationFields();
    }

    @Override
    public Map<String, String> getSortingFields() {
        return queryConfig.getSortingFields();
    }

    @Override
    public Float getMinScoreRatio() {
        return queryConfig.getMinScoreRatio();
    }

    @Override
    public List<String> getCSVHeaders() {
        // This will be populated from the EntityService API call
        return new ArrayList<>();
    }

    @Override
    public List<String> getCSVKeys() {
        // This will be populated from the EntityService API call
        return new ArrayList<>();
    }

    @Override
    public String getCSVFilename() {
        return "StudyExplorerResults.csv";
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

        // Use the base class method with dynamic headers
        convertSearchStringToCSVWithCustomHeaders(response, s, headerList, keyList);
    }

    private void convertSearchStringToCSVWithCustomHeaders(HttpServletResponse response, String s, List<String> headers, List<String> keys) {
        //Convert the elasticsearch json String into a JSONObject and get the hits array object
        JSONObject json = new JSONObject(s);
        JSONObject hits = json.getJSONObject("hits");
        JSONArray hitsArray = hits.getJSONArray("hits");

        //Create the CSV writer and write the column headers for the file
        StringWriter stringWriter = new StringWriter();
        try (CSVWriter csvWriter = new CSVWriter(stringWriter)) {
            csvWriter.writeNext(headers.toArray(new String[0]));

            //Extract the data from each array element and write it to the CSV file
            for (int i = 0; i < hitsArray.length(); i++) {
                String[] rowValues = new String[keys.size()];
                JSONObject hitsElement = hitsArray.getJSONObject(i);
                JSONObject source = hitsElement.getJSONObject("_source");
                for (int j = 0; j < keys.size(); j++) {
                    Object o = source.get(keys.get(j));
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

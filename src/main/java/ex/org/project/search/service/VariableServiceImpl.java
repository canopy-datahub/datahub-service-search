package ex.org.project.search.service;

import ex.org.project.search.config.VariableQueryConfiguration;
import ex.org.project.search.models.OpensearchIndices;
import ex.org.project.search.models.SearchQuery;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.client.RestHighLevelClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class VariableServiceImpl extends BaseSearchService implements VariableService {

    private final VariableQueryConfiguration queryConfig;
    private String index;

    @Autowired
    VariableServiceImpl(RestHighLevelClient client, VariableQueryConfiguration queryConfiguration,
                     OpensearchIndices indices, SearchQueryLogger searchQueryLogger){
        super(searchQueryLogger);
        this.queryConfig = queryConfiguration;
        this.index = indices.variables();
    }

    public String searchVariables(SearchQuery searchQuery) {
        return search(searchQuery);
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
        return Arrays.asList("Variable", "Variable Label", "Section", "Study Name", "Datatype", "Variable ID");
    }

    @Override
    public List<String> getCSVKeys() {
        return Arrays.asList("variable", "variable_label", "section", "study_name", "datatype", "variable_id");
    }

    @Override
    public String getCSVFilename() {
        return "VariableSearchResults.csv";
    }

}


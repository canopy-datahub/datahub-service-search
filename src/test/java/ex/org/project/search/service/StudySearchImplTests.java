package ex.org.project.search.service;

import ex.org.project.search.config.QueryConfiguration;
import ex.org.project.search.models.OpensearchIndices;
import org.opensearch.client.RestHighLevelClient;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.io.UnsupportedEncodingException;
import java.util.*;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StudySearchImplTests {

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private RestHighLevelClient restHighLevelClient;

    @Mock
    private SearchQueryLogger searchQueryLogger;

    private QueryConfiguration queryConfiguration;
    private OpensearchIndices opensearchIndices;

    private StudyServiceImpl studyService;

    @BeforeEach
    public void setup(){
        MockitoAnnotations.initMocks(this);
        queryConfiguration = getQueryConfiguration();
        opensearchIndices = getOpensearchIndices();
        studyService = new StudyServiceImpl(restHighLevelClient, restTemplate, queryConfiguration, opensearchIndices, searchQueryLogger);
    }

    private QueryConfiguration getQueryConfiguration(){
        var config = new QueryConfiguration();
        config.setExactFields(Map.of("title", 1.5f));
        config.setFuzzyFields(Map.of("title", 1.2f));
        config.setPartialFields(Map.of("title", 0.75f));
        config.setSortingFields(Map.of("title", "title.keyword"));
        config.setAggregationFields(List.of("title"));
        return config;
    }

    private OpensearchIndices getOpensearchIndices(){
        return new OpensearchIndices("study_index", "auto_index", "variable_search");
    }


    @Test
    void testSearchStringToCSV() throws UnsupportedEncodingException {
        when(restTemplate.getForObject(anyString(), eq(Map.class))).thenReturn(getPropsMockResponse());
        ReflectionTestUtils.setField(studyService, "entityServiceHost", "testHost");
        ReflectionTestUtils.setField(studyService, "getPropsEndpoint", "/testEndpoint");
        MockHttpServletResponse response = new MockHttpServletResponse();
        studyService.convertSearchStringToCSV(response, getInputStringForTest());
        Assertions.assertEquals("text/csv", response.getContentType());
        Assertions.assertEquals("attachment; filename=StudyExplorerResults.csv", response.getHeaderValue("Content-Disposition"));
        String comparison = "\"Study Name\",\"PHS (dbGaP) ID\"\n" +
                "\"TestTitle\",\"phs00004\"\n";
        Assertions.assertEquals(comparison, response.getContentAsString());
    }

    private Map<String, Object> getPropsMockResponse(){
        Map<String, Object> response = new HashMap<>();

        ArrayList<LinkedHashMap<String, String>> titleList = new ArrayList<>();
        LinkedHashMap<String, String> titleMap = new LinkedHashMap<>();
        titleMap.put("displayLabel", "Study Name");
        titleMap.put("entityPropertyName", "title");
        titleList.add(titleMap);
        response.put("Title", titleList);

        ArrayList<LinkedHashMap<String, String>> repList = new ArrayList<>();
        LinkedHashMap<String, String> repMap = new LinkedHashMap<>();
        repMap.put("displayLabel", "PHS (dbGaP) ID");
        repMap.put("entityPropertyName", "phs");
        repList.add(repMap);
        response.put("Representative", repList);

        ArrayList<LinkedHashMap<String, String>> detailList = new ArrayList<>();
        LinkedHashMap<String, String> detailMap = new LinkedHashMap<>();
        detailMap.put("displayLabel", "Study Description");
        detailMap.put("entityPropertyName", "description");
        detailList.add(detailMap);
        response.put("Detail", detailList);

        return response;
    }


    private String getInputStringForTest(){
        return "{\n" +
                "    \"hits\": {\n" +
                "        \"hits\": [\n" +
                "            {\n" +
                "                \"_index\": \"testIndex1\",\n" +
                "                \"_id\": \"1\",\n" +
                "                \"_score\": null,\n" +
                "                \"_source\": {\n" +
                "                    \"studyenddate\": \"11/06/2020\",\n" +
                "                    \"grant_number\": \"2\",\n" +
                "                    \"types\": \"Methods\",\n" +
                "                    \"acknowledgement_statement\": \"testAcknowledgementStatement\",\n" +
                "                    \"pi_name\": \"David Test\",\n" +
                "                    \"estimated_participants\": \"3\",\n" +
                "                    \"updated_at\": null,\n" +
                "                    \"estimated_participant_range\": \"0 - 5\",\n" +
                "                    \"foa_url\": null,\n" +
                "                    \"general_research_group\": \"TestResearchGroup\",\n" +
                "                    \"pi_institution\": \"University of Unit Test\",\n" +
                "                    \"data_general_types\": \"Test; UnitTest\",\n" +
                "                    \"subject_array\": [\n" +
                "                        \"Testing\",\n" +
                "                        \"Unit Testing\"\n" +
                "                    ],\n" +
                "                    \"phs\": \"phs00004\",\n" +
                "                    \"multi_center_sites\": \"TestingSite\",\n" +
                "                    \"institutes_supporting_study_array\": [\n" +
                "                        \"TESTN\",\n" +
                "                        \"UNITO\"\n" +
                "                    ],\n" +
                "                    \"ct_url\": null,\n" +
                "                    \"created_at\": \"2021-11-15T20:14:40.000Z\",\n" +
                "                    \"institutes_supporting_study\": \"TESTN; UNITO\",\n" +
                "                    \"description\": \"TestDescription\",\n" +
                "                    \"disease_specific_related_conditions\": null,\n" +
                "                    \"study_citation\": null,\n" +
                "                    \"studystartdate\": \"10/16/2020\",\n" +
                "                    \"actual_study_size\": \"7\",\n" +
                "                    \"topics\": null,\n" +
                "                    \"has_data_files\": \"Yes\",\n" +
                "                    \"status\": \"Approved\",\n" +
                "                    \"data_general_types_array\": [\n" +
                "                        \"Test\",\n" +
                "                        \"UnitTest\"\n" +
                "                    ],\n" +
                "                    \"source\": \"SourceTest; testSource\",\n" +
                "                    \"subject\": \"POC Testing; Coordinator Assisted\",\n" +
                "                    \"types_array\": [\n" +
                "                        \"Methods\"\n" +
                "                    ],\n" +
                "                    \"title\": \"TestTitle\",\n" +
                "                    \"study_doi\": null,\n" +
                "                    \"modified_at\": \"2022-11-07T23:03:09.000Z\",\n" +
                "                    \"@timestamp\": \"2024-01-25T20:56:36.520Z\",\n" +
                "                    \"short_description\": null,\n" +
                "                    \"data_species\": \"Human Data\",\n" +
                "                    \"release_date\": \"2023-03-28\",\n" +
                "                    \"@version\": \"8\",\n" +
                "                    \"source_array\": [\n" +
                "                        \"SourceTest\",\n" +
                "                        \"testSource\"\n" +
                "                    ],\n" +
                "                    \"disease_specific_group\": null,\n" +
                "                    \"study_id\": 10,\n" +
                "                    \"dcc\": \"Tech\",\n" +
                "                    \"is_multi_center\": \"Yes\",\n" +
                "                    \"publication_url\": null,\n" +
                "                    \"foa_number\": null,\n" +
                "                    \"study_version\": null,\n" +
                "                    \"health_biomed_group\": null,\n" +
                "                    \"study_website_url\": null\n" +
                "                },\n" +
                "                \"sort\": [\n" +
                "                    \"SortTest\"\n" +
                "                ]\n" +
                "            }\n" +
                "        ]\n" +
                "    }\n" +
                "}";
    }
}

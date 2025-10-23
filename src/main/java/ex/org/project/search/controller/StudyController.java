package ex.org.project.search.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import ex.org.project.search.models.FacetDTO;
import ex.org.project.search.models.SearchQuery;
import ex.org.project.search.service.StudyService;
import ex.org.project.search.util.RequestValidator;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/studies")
public class StudyController {

    private final StudyService studyService;

    private final TypeReference<List<FacetDTO>> typeReference;
    private final ObjectMapper mapper;

    @Autowired
    public StudyController(StudyService studyService) {
        this.studyService = studyService;
        typeReference = new TypeReference<List<FacetDTO>>() {};
        mapper = new ObjectMapper();
    }

    @Validated
    @GetMapping(value = "", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> searchStudies(@Valid SearchQuery searchQuery) {
        RequestValidator.validateSearchQuery(searchQuery);
        return new ResponseEntity<>(
                studyService.searchStudies(searchQuery),
                HttpStatus.OK
        );
    }

    @GetMapping(value = "/csv", produces = MediaType.APPLICATION_JSON_VALUE)
    public void searchStudiesToCSV(HttpServletResponse response, SearchQuery searchQuery) {
        RequestValidator.validateSearchQuery(searchQuery);
        // size is hard-coded to 999 to show all results by default
        searchQuery.setSize(999);
        String esResult = studyService.searchStudies(searchQuery);
        studyService.convertSearchStringToCSV(response, esResult);
    }

    @GetMapping(value = "/autocomplete", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> searchAutocomplete(@RequestParam(defaultValue = "") String q) {
        RequestValidator.validateStringRequestParams(List.of(q));
        return ResponseEntity.ok(studyService.searchAutocomplete(q));
    }

}

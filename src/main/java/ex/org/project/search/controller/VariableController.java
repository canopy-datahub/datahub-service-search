package ex.org.project.search.controller;

import ex.org.project.search.service.VariableService;
import ex.org.project.search.models.SearchQuery;
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
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/variables")
public class VariableController {

    private final VariableService variableService;

    @Autowired
    public VariableController(VariableService variableService) {
        this.variableService = variableService;
    }

    @Validated
    @GetMapping(value = "", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> searchVariables(@Valid SearchQuery searchQuery) {
        RequestValidator.validateSearchQuery(searchQuery);
        return new ResponseEntity<>(
                variableService.searchVariables(searchQuery),
                HttpStatus.OK
        );
    }

    @GetMapping(value = "/csv", produces = MediaType.APPLICATION_JSON_VALUE)
    public void searchVariablesToCSV(HttpServletResponse response, SearchQuery searchQuery) {
        RequestValidator.validateSearchQuery(searchQuery);
        // size is set to 7000 to match the requirement from the UI
        searchQuery.setSize(7000);
        String esResult = variableService.searchVariables(searchQuery);
        variableService.convertSearchStringToCSV(response, esResult);
    }

}


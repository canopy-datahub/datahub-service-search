package ex.org.project.search;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.web.client.RestTemplate;

@SpringBootApplication(proxyBeanMethods = false)
@ComponentScan(basePackages = {
    "ex.org.project.search",                    // Search service components
    "ex.org.project.datahub.auth"               // Keycloak library components
})
@EnableJpaRepositories(basePackages = {
    "ex.org.project.search.repositories",       // Search service repositories
    "ex.org.project.datahub.auth.repository"    // Keycloak library repositories
})
@EntityScan(basePackages = {
    "ex.org.project.search.models",             // Search service entities
    "ex.org.project.datahub.auth.model"         // Keycloak library entities
})
public class SearchApplication {

	public static void main(String[] args) {
		SpringApplication.run(SearchApplication.class, args);
	}

	@Bean
	public RestTemplate getRestTemplate() {
		return new RestTemplate();
	}
}

# Search Service

Spring Boot 3 microservice for Canopy. It is running on Java 17.

Provides full-text and faceted search functionality across studies and variables using OpenSearch.

# Install and Run

## Maven

### Local

There are a few environment variables that need to be set:
* SEARCH_USERNAME
  * OpenSearch username for basic auth
* SEARCH_PASSWORD
  * OpenSearch password for basic auth
* SPRING_PROFILES_ACTIVE
  * This should be set to 'local'
* HostURL
  * hostname of the Canopy frontend

I typically just set these via my environment variables in IntelliJ.

Once the environment variables are set:
```
mvn clean install -DskipTests
```
Once all classes are generated, you can run the application with maven or via the application context.
```
mvn spring-boot:run
```

### Cloud

If running locally, AWS CLI needs to be installed and configured.

There are a few environment variables that need to be set in AWS Secrets Manager:
* opensearch.hostname
    * OpenSearch hostname / url
* opensearch.port
    * Port on which OpenSearch is accepting connections
* opensearch.scheme
    * Scheme type on which OpenSearch is accepting connections
* opensearch.username
    * OpenSearch username for basic auth
* opensearch.password
    * OpenSearch password for basic auth
* HostURL
  * hostname of the Canopy frontend

In a specific instance, the only environment variable that needs to be set is:
* SPRING_PROFILES_ACTIVE
    * This should be set to '{environment}'
      * The current environments are dev, test, prod

Once the environment variables are set:
```
mvn clean install -DskipTests
```
Once all classes are generated, you can run the application with maven or via the application context.
```
mvn spring-boot:run
```

### Endpoint

The base endpoint for this service is:
```
{{hostname}}/api/search/v1/
```

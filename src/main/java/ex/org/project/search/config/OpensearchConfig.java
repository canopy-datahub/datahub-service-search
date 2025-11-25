package ex.org.project.search.config;

import ex.org.project.search.models.OpensearchIndices;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestHighLevelClient;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.util.Map;

@Configuration
@EnableConfigurationProperties
@ConfigurationProperties(prefix = "opensearch")
@Getter(AccessLevel.PACKAGE)
@Setter(AccessLevel.PACKAGE)
public class OpensearchConfig {

    private String hostname;
    private String scheme;
    private String username;
    private String password;
    private Integer port;
    private Map<String, String> index;
    private final CredentialsProvider credentialsProvider = new BasicCredentialsProvider();

    @Primary @Bean(name = "client")
    public RestHighLevelClient getOpensearchClient(){
        credentialsProvider.setCredentials(
                AuthScope.ANY,
                new UsernamePasswordCredentials(this.username, this.password));
        return new RestHighLevelClient(
                RestClient.builder(new HttpHost(hostname, port, scheme))
                        .setHttpClientConfigCallback(
                                httpClientBuilder -> httpClientBuilder.setDefaultCredentialsProvider(
                                        credentialsProvider))
        );
    }

    @Bean
    public OpensearchIndices OpensearchIndices(){
        return new OpensearchIndices(index.get("studies"), index.get("autocomplete"), index.get("variables"));
    }

}

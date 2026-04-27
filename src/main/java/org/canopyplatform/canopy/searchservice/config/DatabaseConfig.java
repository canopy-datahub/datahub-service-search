package org.canopyplatform.canopy.searchservice.config;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

@Configuration
@EnableConfigurationProperties
@ConfigurationProperties(prefix = "database")
@Getter(AccessLevel.PACKAGE)
@Setter(AccessLevel.PACKAGE)
public class DatabaseConfig {

    private String dbUser, dbPassword, host, port, dbName, dbDriverClassName;

    @Bean
    public DataSource getDataSource() {
        DataSourceBuilder dataSourceBuilder = DataSourceBuilder.create()
                .driverClassName(dbDriverClassName)
                .username(this.dbUser)
                .password(this.dbPassword)
                .url("jdbc:postgresql://" + this.host + ":" + this.port + "/" + this.dbName);
        return dataSourceBuilder.build();
    }
}

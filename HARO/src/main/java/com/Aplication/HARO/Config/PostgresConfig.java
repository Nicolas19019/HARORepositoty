// com/Aplication/HARO/Config/PostgresConfig.java
package com.Aplication.HARO.Config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.*;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import javax.sql.DataSource;

@Configuration
@EnableJpaRepositories(basePackages = "com.Aplication.HARO.Repository")
@EntityScan(basePackages = "com.Aplication.HARO.Model")
public class PostgresConfig {

    // NO declares @Bean DataSourceProperties aquí (para no duplicar).
    // Usa el que Spring Boot ya crea desde spring.datasource.*

    @Bean
    @Primary
    @ConfigurationProperties("spring.datasource.hikari") // opcional (tuning del pool)
    public DataSource postgresDataSource(DataSourceProperties props) {
        return props.initializeDataSourceBuilder()
                .type(HikariDataSource.class)
                .build();
    }
}

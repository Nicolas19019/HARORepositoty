package com.Carritos.AcademiaCarros.Config;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.boot.orm.jpa.EntityManagerFactoryBuilder;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@Configuration
@EnableTransactionManagement
@EnableJpaRepositories(
    basePackages = "com.Carritos.AcademiaCarros.Repository.Postgres1", // cambia al paquete de tus repos
    entityManagerFactoryRef = "postgresEMF",
    transactionManagerRef = "postgresTrxManager"
)
public class PostgresConfig {

    @Primary
    @Bean("postgresProperties")
    @ConfigurationProperties(prefix = "spring.datasource.postgres")
    public DataSourceProperties postgresProperties() {
        return new DataSourceProperties();
    }

    @Primary
    @Bean("postgresDatasource")
    public DataSource postgresDataSource(@Qualifier("postgresProperties") DataSourceProperties properties) {
        return properties.initializeDataSourceBuilder().build();
    }

    @Primary
    @Bean("postgresEMF")
    public LocalContainerEntityManagerFactoryBean postgresEntityManagerFactory(
            @Qualifier("postgresDatasource") DataSource dataSource,
            EntityManagerFactoryBuilder builder) {

        Map<String, Object> jpaProps = new HashMap<>();
        jpaProps.put("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect");
        // Recomendados para Postgres:
        jpaProps.put("hibernate.jdbc.lob.non_contextual_creation", true);
        // jpaProps.put("hibernate.default_schema", "public"); // opcional si usas esquema distinto

        return builder
                .dataSource(dataSource)
                .packages("com.Carritos.AcademiaCarros.Model.Postgres1") // cambia al paquete de tus entidades
                .persistenceUnit("postgres")
                .properties(jpaProps)
                .build();
    }

    @Primary
    @Bean("postgresTrxManager")
    public JpaTransactionManager postgresTrxManager(
            @Qualifier("postgresEMF") LocalContainerEntityManagerFactoryBean emfBean) {
        return new JpaTransactionManager(Objects.requireNonNull(emfBean.getObject()));
    }
}

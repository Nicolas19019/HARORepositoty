package com.Aplication.HARO.Config;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;

import org.springframework.boot.orm.jpa.EntityManagerFactoryBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@Configuration
@EnableJpaRepositories(
    basePackages = "com.Carritos.AcademiaCarros.Repository.MySQL1", 
    entityManagerFactoryRef = "mysqlEMF", 
    transactionManagerRef = "mysqlTrxManager"
)
@EnableTransactionManagement
public class MySQLConfig {

    @Primary
    @Bean("mysqlProperties")
    @ConfigurationProperties(prefix = "spring.datasource.mysql")
    public DataSourceProperties getMysqlProperties() {
        return new DataSourceProperties();
    }

    @Primary
    @Bean("mysqlDatasource")
    public DataSource getMysqlDataSource(@Qualifier("mysqlProperties") DataSourceProperties properties) {
        return properties.initializeDataSourceBuilder().build();
    }

    @Primary
    @Bean("mysqlEMF")
    public LocalContainerEntityManagerFactoryBean mysqlEntityManagerFactory(
            @Qualifier("mysqlDatasource") DataSource dataSource, EntityManagerFactoryBuilder builder) {

        Map<String, String> additionalProps = new HashMap<>();
        additionalProps.put("hibernate.dialect", "org.hibernate.dialect.MySQL8Dialect");

        return builder.dataSource(dataSource)
                .persistenceUnit("mysql")
                .properties(additionalProps)
                .packages("com.Carritos.AcademiaCarros.Model.MySQL1")
                .build();
    }

    @Primary
    @Bean("mysqlTrxManager")
    public JpaTransactionManager getMysqlTrxManager(@Qualifier("mysqlEMF") LocalContainerEntityManagerFactoryBean mysqlEMF) {
        return new JpaTransactionManager(Objects.requireNonNull(mysqlEMF.getObject()));
    }
}

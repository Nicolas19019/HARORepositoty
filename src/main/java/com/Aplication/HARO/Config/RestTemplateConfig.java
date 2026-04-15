package com.Aplication.HARO.Config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

/**
 * Configuraci?n de Spring para REST template.
 */
@Configuration
public class RestTemplateConfig {

    @Bean
    @Primary
    public RestTemplate restTemplate(
            @Value("${app.http.connect-timeout-ms:5000}") int connectTimeoutMs,
            @Value("${app.http.read-timeout-ms:10000}") int readTimeoutMs
    ) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Math.max(1000, connectTimeoutMs));
        factory.setReadTimeout(Math.max(1000, readTimeoutMs));
        return new RestTemplate(factory);
    }
}


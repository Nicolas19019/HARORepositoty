package com.Aplication.HARO.Config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.*;

/**
 * Configuraci?n de Spring para cors.
 */
@Configuration
public class CorsConfig {

    @Bean
    public WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/api/**")
                        .allowedOriginPatterns(
                                "https://ceaharo.com",
                                "https://www.ceaharo.com",
                                "https://*.ceaharo.com"
                        )
                        .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                        .allowedHeaders("*");

                registry.addMapping("/epayco/**")
                        .allowedOrigins("https://ceaharo.com", "https://www.ceaharo.com")
                        .allowedMethods("GET", "POST", "OPTIONS", "HEAD")
                        .allowedHeaders("*");

                registry.addMapping("/response")
                        .allowedOrigins("https://ceaharo.com", "https://www.ceaharo.com")
                        .allowedMethods("GET", "OPTIONS", "HEAD")
                        .allowedHeaders("*");

                registry.addMapping("/confirmation")
                        .allowedOrigins("https://ceaharo.com", "https://www.ceaharo.com")
                        .allowedMethods("GET", "POST", "OPTIONS", "HEAD")
                        .allowedHeaders("*");
            }
        };
    }
}

package com.Aplication.HARO.Config;

import com.fasterxml.jackson.core.JsonGenerator;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuraci?n de Spring para jackson utf 8 safety.
 */
@Configuration
public class JacksonUtf8SafetyConfig {

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer escapeNonAsciiCustomizer() {
        return builder -> builder.featuresToEnable(JsonGenerator.Feature.ESCAPE_NON_ASCII);
    }
}

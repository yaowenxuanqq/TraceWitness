package com.tracewitness.platform.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JsonConfig {
    @Bean
    public Jackson2ObjectMapperBuilderCustomizer strictRequestJson() {
        return builder -> {
            builder.featuresToEnable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
            builder.featuresToDisable(MapperFeature.ALLOW_COERCION_OF_SCALARS);
        };
    }
}

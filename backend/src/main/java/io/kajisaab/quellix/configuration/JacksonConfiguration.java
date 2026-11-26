package io.kajisaab.quellix.configuration;

import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.cfg.DateTimeFeature;

/**
 * Customizes Spring Boot's autoconfigured {@code JsonMapper} rather than
 * replacing it. Declaring a {@code JsonMapper} bean directly would discard Boot's
 * customizers, including the ProblemDetail mixin that flattens extension
 * members ({@code code}, {@code requestId}, ...) into the error JSON.
 */
@Configuration
public class JacksonConfiguration {

    @Bean
    public JsonMapperBuilderCustomizer isoDateTimeCustomizer() {
        // Already the Jackson 3 default; kept explicit because the API contract depends on ISO-8601.
        return builder -> builder.disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS);
    }
}

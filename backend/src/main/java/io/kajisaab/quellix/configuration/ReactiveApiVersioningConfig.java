package io.kajisaab.quellix.configuration;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.accept.StandardApiVersionDeprecationHandler;
import org.springframework.web.reactive.config.ApiVersionConfigurer;
import org.springframework.web.reactive.config.WebFluxConfigurer;

import java.net.URI;
import java.time.ZonedDateTime;

@Configuration
public class ReactiveApiVersioningConfig implements WebFluxConfigurer {

    @Override
    public void configureApiVersioning(ApiVersionConfigurer apiVersionConfigurer){
        // 1. Configure deprecation metadata for version 1.0 (RFC 9745 & RFC 8594)
        StandardApiVersionDeprecationHandler deprecationHandler = new StandardApiVersionDeprecationHandler();
        deprecationHandler.configureVersion("1.0")
                .setDeprecationDate(ZonedDateTime.parse("2026-06-01T00:00:00Z"))
                .setSunsetDate(ZonedDateTime.parse("2026-12-31T23:59:59Z"))
                .setSunsetLink(URI.create("https://example.com"));

        // 2. Wire up header strategy, supported versions, default fallback, and deprecation.
        apiVersionConfigurer
                .useRequestHeader("X-API-Version")
                .addSupportedVersions("1.0", "2.0")
                .setDefaultVersion("2.0")
                .setDeprecationHandler(deprecationHandler);
    }
}

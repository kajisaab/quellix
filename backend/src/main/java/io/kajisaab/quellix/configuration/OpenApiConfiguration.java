package io.kajisaab.quellix.configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.HeaderParameter;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.util.List;

/**
 * API metadata shown in Swagger UI ({@code /swagger-ui.html}) and the OpenAPI spec ({@code /v3/api-docs}).
 * Active only in the {@code dev} profile; the endpoints themselves are switched on in {@code application-dev.yaml}.
 */
@Profile("dev")
@Configuration
public class OpenApiConfiguration {

    private static final String API_VERSION_HEADER = "X-API-Version";

    private static final String SESSION_SCHEME = "sessionCookie";
    private static final String SESSION_COOKIE_NAME = "QUELLIX_SESSION";

    @Bean
    public OpenAPI quellixOpenApi() {
        return new OpenAPI()
                .components(new Components()
                        .addSecuritySchemes(SESSION_SCHEME, new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.COOKIE)
                                .name(SESSION_COOKIE_NAME)
                                .description("Session cookie issued after sign-in. Browsers send it automatically "
                                        + "on same-origin requests, including Swagger UI's \"Try it out\".")))
                .addSecurityItem(new SecurityRequirement().addList(SESSION_SCHEME))
                .info(new Info()
                        .title("Quellix API")
                        .version("2.0")
                        .description("""
                                Multi-tenant incident command platform for regulated payment operators.

                                **Versioning:** send the `X-API-Version` header. It defaults to `2.0`; \
                                `1.0` is deprecated and its responses carry deprecation and sunset headers.

                                **Responses:** successful responses are wrapped in an envelope with \
                                `message`, `data` and `timestamp`. Errors are reported through the HTTP status code.

                                **Authentication:** endpoints other than the health check require the \
                                `QUELLIX_SESSION` session cookie (see **Authorize**). No sign-in method is \
                                configured yet, so they currently return `401`.
                                """));
    }

    /**
     * Documents the version header on every operation, since versioning is configured globally
     * rather than per endpoint.
     */
    @Bean
    public OperationCustomizer apiVersionHeaderCustomizer() {
        return (operation, handlerMethod) -> {
            boolean alreadyDocumented = operation.getParameters() != null && operation.getParameters().stream()
                    .anyMatch(parameter -> API_VERSION_HEADER.equalsIgnoreCase(parameter.getName()));

            if (!alreadyDocumented) {
                operation.addParametersItem(new HeaderParameter()
                        .name(API_VERSION_HEADER)
                        .required(false)
                        .description("API version. Defaults to 2.0; 1.0 is deprecated.")
                        .schema(new StringSchema()._default("2.0")._enum(List.of("1.0", "2.0"))));
            }
            return operation;
        };
    }
}

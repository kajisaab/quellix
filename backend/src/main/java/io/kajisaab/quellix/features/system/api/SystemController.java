package io.kajisaab.quellix.features.system.api;

import io.kajisaab.quellix.core.response.ApiResponse;
import io.kajisaab.quellix.features.system.application.request.HealthUseCaseRequest;
import io.kajisaab.quellix.features.system.application.response.HealthUseCaseResponse;
import io.kajisaab.quellix.features.system.application.usecase.HealthUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@Tag(name = "System", description = "Application status")
@RestController
@RequiredArgsConstructor
@RequestMapping(path = "/system", produces = MediaType.APPLICATION_JSON_VALUE)
public class SystemController {

    private final HealthUseCase healthUseCase;

    @Operation(
            summary = "Health check",
            description = "Returns `Healthy` while the application is running. Public; no sign-in needed. "
                    + "It doesn't check the database or other dependencies; use `/actuator/health` for that.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "The application is running")
    @SecurityRequirements
    @GetMapping(value = "/health", version = "2.0")
    public Mono<ApiResponse<HealthUseCaseResponse>> health() {
        return healthUseCase.execute(new HealthUseCaseRequest())
                .map(response -> ApiResponse.of("Success", response));
    }
}

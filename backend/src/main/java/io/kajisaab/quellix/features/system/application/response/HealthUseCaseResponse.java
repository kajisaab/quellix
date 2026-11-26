package io.kajisaab.quellix.features.system.application.response;

import io.kajisaab.quellix.core.usecase.UseCaseResponse;

import java.time.Instant;

@io.swagger.v3.oas.annotations.media.Schema(description = "Application health")
public record HealthUseCaseResponse(
        @io.swagger.v3.oas.annotations.media.Schema(description = "Application status", example = "Healthy")
        String status
) implements UseCaseResponse {
}

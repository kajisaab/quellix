package io.kajisaab.quellix.features.user.application.response;

import io.kajisaab.quellix.core.usecase.UseCaseResponse;
import io.kajisaab.quellix.features.user.domain.model.User;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "A user")
public record UserUseCaseResponse(
        @Schema(description = "User id", example = "3f2b8c1e-7d4a-4f6b-9a2e-1c5d8e9f0a7b")
        UUID id,

        @Schema(description = "Display name", example = "Jane Doe")
        String name,

        @Schema(description = "Email address, lower-cased", example = "jane.doe@example.com")
        String email,

        @Schema(description = "When the user was created (ISO-8601, UTC)", example = "2026-11-26T10:15:30Z")
        Instant createdAt
) implements UseCaseResponse {

    public static UserUseCaseResponse from(User user) {
        return new UserUseCaseResponse(user.id(), user.name(), user.email(), user.createdAt());
    }
}

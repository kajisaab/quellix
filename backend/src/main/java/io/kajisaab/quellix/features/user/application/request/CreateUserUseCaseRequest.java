package io.kajisaab.quellix.features.user.application.request;

import io.kajisaab.quellix.core.usecase.UseCaseRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Details of the user to create")
public record CreateUserUseCaseRequest(
        @Schema(description = "Display name", example = "Jane Doe")
        @NotBlank @Size(max = 100) String name,

        @Schema(description = "Email address. Stored lower-cased and must be unique.", example = "jane.doe@example.com")
        @NotBlank @Email @Size(max = 255) String email
) implements UseCaseRequest {
}

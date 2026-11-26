package io.kajisaab.quellix.features.user.api;

import io.kajisaab.quellix.core.response.ApiResponse;
import io.kajisaab.quellix.features.user.application.request.CreateUserUseCaseRequest;
import io.kajisaab.quellix.features.user.application.request.GetUserUseCaseRequest;
import io.kajisaab.quellix.features.user.application.response.UserUseCaseResponse;
import io.kajisaab.quellix.features.user.application.usecase.CreateUserUseCase;
import io.kajisaab.quellix.features.user.application.usecase.GetUserUseCase;
import io.kajisaab.quellix.features.user.domain.exception.UserAlreadyExistsException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * HTTP adapter for the user feature: translates requests into use case calls
 * and use case outcomes into HTTP status codes. No business logic lives here.
 */
@Tag(name = "Users", description = "Create and look up users")
@RestController
@RequiredArgsConstructor
@RequestMapping(path = "/users", produces = MediaType.APPLICATION_JSON_VALUE)
public class UserController {

    private final CreateUserUseCase createUserUseCase;
    private final GetUserUseCase getUserUseCase;

    @Operation(
            summary = "Create a user",
            description = "Creates a user. The name is trimmed, and the email is trimmed and lower-cased "
                    + "before it is checked for uniqueness and stored.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "User created")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "The request body failed validation", content = @Content)
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Not signed in", content = @Content)
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "A user with this email already exists", content = @Content)
    @PostMapping(version = "2.0", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<ApiResponse<UserUseCaseResponse>> createUser(@Valid @RequestBody CreateUserUseCaseRequest request) {
        return createUserUseCase.execute(request)
                .map(user -> ApiResponse.of("User created", user))
                .onErrorMap(UserAlreadyExistsException.class,
                        e -> new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage(), e));
    }

    @Operation(summary = "Get a user by id")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "The user")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Not signed in", content = @Content)
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "No user has this id", content = @Content)
    @GetMapping(path = "/{id}", version = "2.0")
    public Mono<ApiResponse<UserUseCaseResponse>> getUser(
            @Parameter(description = "The user's id", example = "3f2b8c1e-7d4a-4f6b-9a2e-1c5d8e9f0a7b")
            @PathVariable UUID id) {
        return getUserUseCase.execute(new GetUserUseCaseRequest(id))
                .map(user -> ApiResponse.of(user))
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found")));
    }
}

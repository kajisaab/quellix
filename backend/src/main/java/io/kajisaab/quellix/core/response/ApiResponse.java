package io.kajisaab.quellix.core.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/**
 * Envelope for every successful (2xx) response body.
 *
 * <pre>
 * { "data": { ... }, "message": "User created", "timestamp": "2026-11-25T10:15:30Z" }
 * </pre>
 *
 * Errors never use this type; they are returned as RFC 9457
 * {@code application/problem+json} by the global exception handler, so clients
 * branch on the HTTP status instead of a {@code success} flag.
 */
@Schema(description = "Envelope for every successful response")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(
        @Schema(description = "Human-readable outcome, if any", example = "Success") String message,
        @Schema(description = "The response payload") T data,
        @Schema(description = "When the response was created (ISO-8601, UTC)", example = "2026-11-26T10:15:30Z") Instant timestamp) {
    public static <T> ApiResponse<T> of(T data) {return new ApiResponse<>(null,data, Instant.now());}

    public static <T> ApiResponse<T> of(String message, T data) { return new ApiResponse<>( message,data, Instant.now());}

    public static <T> ApiResponse<T> of(String message) { return new ApiResponse<>( message,null, Instant.now());}
}

package io.kajisaab.quellix.features.user.application.request;

import io.kajisaab.quellix.core.usecase.UseCaseRequest;

import java.util.UUID;

public record GetUserUseCaseRequest(UUID id) implements UseCaseRequest {
}

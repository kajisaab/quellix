package io.kajisaab.quellix.features.system.application.usecase;

import io.kajisaab.quellix.core.usecase.UseCase;
import io.kajisaab.quellix.features.system.application.request.HealthUseCaseRequest;
import io.kajisaab.quellix.features.system.application.response.HealthUseCaseResponse;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Instant;

@Service
public class HealthUseCase implements UseCase<HealthUseCaseRequest, HealthUseCaseResponse> {

    @Override
    public Mono<HealthUseCaseResponse> execute(HealthUseCaseRequest request) {
        return Mono.just(new HealthUseCaseResponse("Healthy"));
    }
}

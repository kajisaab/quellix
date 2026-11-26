package io.kajisaab.quellix.core.usecase;

import reactor.core.publisher.Mono;

@FunctionalInterface
public interface UseCase<I extends UseCaseRequest, O extends UseCaseResponse>{
    Mono<O> execute(I request);
}

package io.kajisaab.quellix.features.user.application.usecase;

import io.kajisaab.quellix.core.usecase.UseCase;
import io.kajisaab.quellix.features.user.application.request.GetUserUseCaseRequest;
import io.kajisaab.quellix.features.user.application.response.UserUseCaseResponse;
import io.kajisaab.quellix.features.user.domain.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * Completes empty when no user has the requested id; the caller decides how to report that.
 */
@Service
@RequiredArgsConstructor
public class GetUserUseCase implements UseCase<GetUserUseCaseRequest, UserUseCaseResponse> {

    private final UserRepository userRepository;

    @Override
    public Mono<UserUseCaseResponse> execute(GetUserUseCaseRequest request) {
        return userRepository.findById(request.id())
                .map(UserUseCaseResponse::from);
    }
}

package io.kajisaab.quellix.features.user.application.usecase;

import io.kajisaab.quellix.core.usecase.UseCase;
import io.kajisaab.quellix.features.user.application.request.CreateUserUseCaseRequest;
import io.kajisaab.quellix.features.user.application.response.UserUseCaseResponse;
import io.kajisaab.quellix.features.user.domain.exception.UserAlreadyExistsException;
import io.kajisaab.quellix.features.user.domain.model.User;
import io.kajisaab.quellix.features.user.domain.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class CreateUserUseCase implements UseCase<CreateUserUseCaseRequest, UserUseCaseResponse> {

    private final UserRepository userRepository;

    @Override
    public Mono<UserUseCaseResponse> execute(CreateUserUseCaseRequest request) {
        User user = User.create(request.name(), request.email());

        return userRepository.existsByEmail(user.email())
                .flatMap(exists -> exists
                        ? Mono.<User>error(new UserAlreadyExistsException(user.email()))
                        : userRepository.save(user))
                .map(UserUseCaseResponse::from);
    }
}

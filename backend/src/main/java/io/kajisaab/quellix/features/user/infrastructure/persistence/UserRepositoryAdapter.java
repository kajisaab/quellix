package io.kajisaab.quellix.features.user.infrastructure.persistence;

import io.kajisaab.quellix.features.user.domain.exception.UserAlreadyExistsException;
import io.kajisaab.quellix.features.user.domain.model.User;
import io.kajisaab.quellix.features.user.domain.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Implements the domain {@link UserRepository} with Spring Data R2DBC,
 * mapping between {@link UserEntity} rows and {@link User} domain objects.
 */
@Repository
@RequiredArgsConstructor
public class UserRepositoryAdapter implements UserRepository {

    private final UserR2dbcRepository userR2dbcRepository;

    @Override
    public Mono<User> save(User user) {
        return userR2dbcRepository.save(UserEntity.fromDomain(user))
                .map(UserEntity::toDomain)
                // Two concurrent creates can both pass the existsByEmail check; the unique index decides.
                .onErrorMap(DuplicateKeyException.class, e -> new UserAlreadyExistsException(user.email()));
    }

    @Override
    public Mono<User> findById(UUID id) {
        return userR2dbcRepository.findById(id)
                .map(UserEntity::toDomain);
    }

    @Override
    public Mono<Boolean> existsByEmail(String email) {
        return userR2dbcRepository.existsByEmail(email);
    }
}

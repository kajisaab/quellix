package io.kajisaab.quellix.features.user.domain.repository;

import io.kajisaab.quellix.features.user.domain.model.User;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * What the user feature needs from storage. Implemented in the infrastructure layer,
 * so use cases depend on this interface rather than on R2DBC.
 */
public interface UserRepository {

    Mono<User> save(User user);

    Mono<User> findById(UUID id);

    Mono<Boolean> existsByEmail(String email);
}

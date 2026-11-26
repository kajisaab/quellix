package io.kajisaab.quellix.features.user.infrastructure.persistence;

import org.springframework.data.r2dbc.repository.R2dbcRepository;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface UserR2dbcRepository extends R2dbcRepository<UserEntity, UUID> {

    Mono<Boolean> existsByEmail(String email);
}

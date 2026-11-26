package io.kajisaab.quellix.features.user.infrastructure.persistence;

import io.kajisaab.quellix.features.user.domain.model.User;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Row mapping for the {@code users} table. A null {@code id} means a new row;
 * the database generates it on insert.
 */
@Getter
@Setter
@NoArgsConstructor
@Table("users")
public class UserEntity {

    @Id
    private UUID id;

    private String name;

    private String email;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;

    static UserEntity fromDomain(User user) {
        UserEntity entity = new UserEntity();
        entity.setId(user.id());
        entity.setName(user.name());
        entity.setEmail(user.email());
        entity.setCreatedAt(user.createdAt());
        return entity;
    }

    User toDomain() {
        return new User(id, name, email, createdAt);
    }
}

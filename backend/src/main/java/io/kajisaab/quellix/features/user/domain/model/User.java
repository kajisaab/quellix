package io.kajisaab.quellix.features.user.domain.model;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

/**
 * The user as the business sees it. Free of Spring and database annotations,
 * so it can change independently of how it is stored.
 */
public record User(UUID id, String name, String email, Instant createdAt) {

    /**
     * A user that has not been saved yet; {@code id} and {@code createdAt} are assigned on save.
     * Emails are compared case-insensitively, so they are stored lower-cased.
     */
    public static User create(String name, String email) {
        return new User(null, name.trim(), email.trim().toLowerCase(Locale.ROOT), null);
    }
}

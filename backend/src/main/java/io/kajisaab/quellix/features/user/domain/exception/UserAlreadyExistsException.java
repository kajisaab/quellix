package io.kajisaab.quellix.features.user.domain.exception;

public class UserAlreadyExistsException extends RuntimeException {

    public UserAlreadyExistsException(String email) {
        super("A user with email " + email + " already exists");
    }
}

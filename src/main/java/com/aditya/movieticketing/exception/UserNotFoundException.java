package com.aditya.movieticketing.exception;

import org.springframework.http.HttpStatus;

public class UserNotFoundException extends ApiException {

    public UserNotFoundException(Long userId) {
        super(HttpStatus.NOT_FOUND, "User not found", "No user exists with id " + userId);
    }
}

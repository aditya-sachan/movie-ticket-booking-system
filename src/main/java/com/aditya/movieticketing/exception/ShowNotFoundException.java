package com.aditya.movieticketing.exception;

import org.springframework.http.HttpStatus;

public class ShowNotFoundException extends ApiException {

    public ShowNotFoundException(Long showId) {
        super(HttpStatus.NOT_FOUND, "Show not found", "No show exists with id " + showId);
    }
}

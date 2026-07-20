package com.aditya.movieticketing.exception;

import org.springframework.http.HttpStatus;

public class InvalidSeatSelectionException extends ApiException {

    public InvalidSeatSelectionException(String detail) {
        super(HttpStatus.UNPROCESSABLE_ENTITY, "Invalid seat selection", detail);
    }
}

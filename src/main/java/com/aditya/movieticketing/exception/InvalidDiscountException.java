package com.aditya.movieticketing.exception;

import org.springframework.http.HttpStatus;

public class InvalidDiscountException extends ApiException {

    public InvalidDiscountException(String detail) {
        super(HttpStatus.UNPROCESSABLE_ENTITY, "Invalid discount", detail);
    }
}

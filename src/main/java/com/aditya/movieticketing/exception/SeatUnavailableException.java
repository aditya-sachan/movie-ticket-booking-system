package com.aditya.movieticketing.exception;

import org.springframework.http.HttpStatus;

public class SeatUnavailableException extends ApiException {

    public SeatUnavailableException(String detail) {
        super(HttpStatus.CONFLICT, "Seat unavailable", detail);
    }
}

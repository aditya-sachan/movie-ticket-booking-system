package com.aditya.movieticketing.exception;

import org.springframework.http.HttpStatus;

public class BookingNotFoundException extends ApiException {

    public BookingNotFoundException(Long bookingId) {
        super(HttpStatus.NOT_FOUND, "Booking not found", "No booking exists with id " + bookingId);
    }
}

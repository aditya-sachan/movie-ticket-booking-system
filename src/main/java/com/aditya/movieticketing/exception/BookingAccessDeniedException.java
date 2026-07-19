package com.aditya.movieticketing.exception;

import org.springframework.http.HttpStatus;

/**
 * Ownership guard: a customer may not act on another customer's booking. In slice 2-4 this
 * is checked against the {@code userId} in the request; slice 5 moves it to {@code @PreAuthorize}
 * against the authenticated principal.
 */
public class BookingAccessDeniedException extends ApiException {

    public BookingAccessDeniedException(String detail) {
        super(HttpStatus.FORBIDDEN, "Access denied", detail);
    }
}

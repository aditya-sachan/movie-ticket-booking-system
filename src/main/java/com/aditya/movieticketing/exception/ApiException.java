package com.aditya.movieticketing.exception;

import org.springframework.http.HttpStatus;

/**
 * Base class for domain exceptions that map to a specific HTTP status. The single
 * {@code @RestControllerAdvice} turns any subclass into an RFC 7807 ProblemDetail.
 */
public abstract class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String title;

    protected ApiException(HttpStatus status, String title, String detail) {
        super(detail);
        this.status = status;
        this.title = title;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getTitle() {
        return title;
    }
}

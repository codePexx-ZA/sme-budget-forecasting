package com.smebudget.api.common;

import org.springframework.http.HttpStatus;

/** Throw this from any feature to send a status code and a user-friendly message to the app. */
public class ApiException extends RuntimeException {

    private final HttpStatus status;

    public ApiException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}

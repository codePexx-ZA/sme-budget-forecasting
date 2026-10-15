package com.smebudget.api.common;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Turns errors from every controller into the same {@link ApiError} JSON shape. */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiError> handleApiException(ApiException ex) {
        return ResponseEntity.status(ex.getStatus()).body(new ApiError(ex.getMessage()));
    }

    /** Failed @Valid checks: return the first field's message, e.g. "Email is required." */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex) {
        FieldError first = ex.getBindingResult().getFieldError();
        String message = first != null ? first.getDefaultMessage() : "Some fields are invalid.";
        return ResponseEntity.badRequest().body(new ApiError(message));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadableBody(HttpMessageNotReadableException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ApiError("The request body is missing or not valid JSON."));
    }
}

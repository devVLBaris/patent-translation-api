package com.example.patent.translation.api;

import com.example.patent.shared.api.ErrorResponse;
import com.example.patent.translation.application.exception.TranslationNotFoundException;
import com.example.patent.translation.application.exception.TranslationSchedulingException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Translates application failures into HTTP without coupling the service to HTTP. */
@RestControllerAdvice(assignableTypes = TranslationController.class)
public class TranslationErrorHandler {
    @ExceptionHandler(TranslationNotFoundException.class)
    ResponseEntity<ErrorResponse> handleNotFound(TranslationNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse("NOT_FOUND", "Translation request not found"));
    }

    @ExceptionHandler(TranslationSchedulingException.class)
    ResponseEntity<ErrorResponse> handleScheduling(TranslationSchedulingException exception) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new ErrorResponse("SERVICE_UNAVAILABLE", exception.getMessage()));
    }
}

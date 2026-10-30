package com.example.patent.translation.application.exception;

public class TranslationSchedulingException extends RuntimeException {
    public TranslationSchedulingException(Throwable cause) {
        super("Unable to schedule translation", cause);
    }
}

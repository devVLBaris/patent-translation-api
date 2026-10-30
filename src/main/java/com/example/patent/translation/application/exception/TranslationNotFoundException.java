package com.example.patent.translation.application.exception;

import java.util.UUID;

public class TranslationNotFoundException extends RuntimeException {
    public TranslationNotFoundException(UUID requestId) {
        super("Translation request not found: " + requestId);
    }
}

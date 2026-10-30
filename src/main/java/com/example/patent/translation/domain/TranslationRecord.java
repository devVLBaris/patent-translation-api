package com.example.patent.translation.domain;

import java.util.UUID;

// Immutable snapshots give concurrent readers a consistent status/result pair.
public record TranslationRecord(UUID requestId, TranslationDocument input,
                                TranslationStatus status, String resultText) {
    public TranslationRecord completed(String text) {
        return new TranslationRecord(requestId, input, TranslationStatus.COMPLETED, text);
    }

    public TranslationRecord failed() {
        return new TranslationRecord(requestId, input, TranslationStatus.FAILED, null);
    }
}

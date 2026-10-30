package com.example.patent.notification.domain;

import java.time.Instant;
import java.util.UUID;

public record TranslationCompletedEvent(String eventType, String patentId, UUID requestId,
                                        String targetLang, Instant timestamp) {
}

package com.example.patent.notification.application.port;

import com.example.patent.notification.domain.TranslationCompletedEvent;

/** Implementations return after publication is acknowledged, or throw on failure. */
public interface EventPublisher {
    void publish(String topic, TranslationCompletedEvent event);
}

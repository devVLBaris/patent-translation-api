package com.example.patent.notification.infrastructure.messaging;

import com.example.patent.notification.application.port.EventPublisher;
import com.example.patent.notification.domain.TranslationCompletedEvent;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class InMemoryEventPublisher implements EventPublisher {
    private static final Logger log = LoggerFactory.getLogger(InMemoryEventPublisher.class);
    private final List<PublishedEvent> events = new CopyOnWriteArrayList<>();

    public record PublishedEvent(String topic, TranslationCompletedEvent event) { }

    @Override
    public void publish(String topic, TranslationCompletedEvent event) {
        events.add(new PublishedEvent(topic, event));
        log.info("Simulated Kafka publish: topic={}, event={}", topic, event);
    }

    public List<PublishedEvent> events() {
        return List.copyOf(events);
    }
}

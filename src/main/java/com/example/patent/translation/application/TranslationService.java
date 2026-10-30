package com.example.patent.translation.application;

import com.example.patent.notification.application.port.EventPublisher;
import com.example.patent.notification.domain.TranslationCompletedEvent;
import com.example.patent.translation.application.exception.TranslationNotFoundException;
import com.example.patent.translation.application.exception.TranslationSchedulingException;
import com.example.patent.translation.application.port.TranslationRepository;
import com.example.patent.translation.application.port.Translator;
import com.example.patent.translation.domain.TranslationDocument;
import com.example.patent.translation.domain.TranslationRecord;
import com.example.patent.translation.domain.TranslationStatus;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class TranslationService {
    private static final Logger log = LoggerFactory.getLogger(TranslationService.class);
    private final TranslationRepository repository;
    private final EventPublisher publisher;
    private final Translator translator;
    private final ScheduledExecutorService executor;
    private final long minDelayMs;
    private final long maxDelayMs;

    public TranslationService(TranslationRepository repository, EventPublisher publisher,
                              Translator translator, ScheduledExecutorService executor,
                              @Value("${translation.min-delay-ms}") long minDelayMs,
                              @Value("${translation.max-delay-ms}") long maxDelayMs) {
        if (minDelayMs < 0 || maxDelayMs < minDelayMs || maxDelayMs == Long.MAX_VALUE) {
            throw new IllegalArgumentException("Invalid translation delay range");
        }
        this.repository = repository;
        this.publisher = publisher;
        this.translator = translator;
        this.executor = executor;
        this.minDelayMs = minDelayMs;
        this.maxDelayMs = maxDelayMs;
    }

    public TranslationRecord submit(TranslationDocument input) {
        var request = new TranslationRecord(UUID.randomUUID(), input, TranslationStatus.PENDING, null);
        repository.save(request);
        try {
            long delay = ThreadLocalRandom.current().nextLong(minDelayMs, maxDelayMs + 1);
            executor.schedule(() -> complete(request), delay, TimeUnit.MILLISECONDS);
        } catch (RuntimeException exception) {
            repository.save(request.failed());
            throw new TranslationSchedulingException(exception);
        }
        return request;
    }

    public TranslationRecord find(UUID requestId) {
        return repository.find(requestId).orElseThrow(() ->
                new TranslationNotFoundException(requestId));
    }

    private void complete(TranslationRecord request) {
        try {
            String result = translator.translate(request.input());
            publisher.publish("patent-updates", new TranslationCompletedEvent(
                    "TRANSLATION_COMPLETED", request.input().patentId(), request.requestId(),
                    request.input().targetLang(), Instant.now()));
            repository.save(request.completed(result));
        } catch (Exception exception) {
            repository.save(request.failed());
            log.error("Translation or publication failed for request {}", request.requestId(), exception);
        }
    }
}

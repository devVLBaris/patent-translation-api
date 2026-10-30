package com.example.patent.translation.application;

import com.example.patent.notification.application.port.EventPublisher;
import com.example.patent.translation.application.exception.TranslationSchedulingException;
import com.example.patent.translation.application.port.TranslationRepository;
import com.example.patent.translation.domain.TranslationDocument;
import com.example.patent.translation.domain.TranslationStatus;
import com.example.patent.translation.infrastructure.persistence.InMemoryTranslationRepository;
import com.example.patent.translation.infrastructure.translation.MockTranslator;

import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class TranslationServiceTest {
    private final ScheduledExecutorService executor = Executors.newScheduledThreadPool(2);
    private final TranslationRepository repository = new InMemoryTranslationRepository();
    private final TranslationDocument input = new TranslationDocument("EP123", "en", "tr", "Patent");

    @AfterEach
    void tearDown() { executor.shutdownNow(); }

    @Test
    void translatorFailureMarksFailedAndDoesNotPublish() {
        var translator = mock(MockTranslator.class);
        when(translator.translate(any())).thenThrow(new IllegalStateException("Translation unavailable"));
        var publisher = mock(EventPublisher.class);
        var service = new TranslationService(repository, publisher, translator, executor, 0, 0);
        var request = service.submit(input);
        await().atMost(Duration.ofSeconds(3)).untilAsserted(() ->
                assertThat(service.find(request.requestId()).status()).isEqualTo(TranslationStatus.FAILED));
        verifyNoInteractions(publisher);
        assertThat(service.find(request.requestId()).resultText()).isNull();
    }

    @Test
    void publishFailureDoesNotClaimCompletion() {
        var publisher = mock(EventPublisher.class);
        doThrow(new IllegalStateException("Broker unavailable")).when(publisher).publish(anyString(), any());
        var service = new TranslationService(repository, publisher, new MockTranslator(), executor, 0, 0);
        var request = service.submit(input);
        await().atMost(Duration.ofSeconds(3)).untilAsserted(() ->
                assertThat(service.find(request.requestId()).status()).isEqualTo(TranslationStatus.FAILED));
        verify(publisher, times(1)).publish(eq("patent-updates"), any());
    }

    @Test
    void rejectedSchedulingReturnsServiceUnavailable() {
        executor.shutdownNow();
        var service = new TranslationService(repository, mock(EventPublisher.class), new MockTranslator(), executor, 0, 0);
        assertThatThrownBy(() -> service.submit(input)).isInstanceOfSatisfying(TranslationSchedulingException.class,
                exception -> assertThat(exception.getMessage()).isEqualTo("Unable to schedule translation"));
    }

    @Test
    void rejectsInvalidDelayConfiguration() {
        assertThatThrownBy(() -> new TranslationService(repository, mock(EventPublisher.class),
                new MockTranslator(), executor, 10, 5)).isInstanceOf(IllegalArgumentException.class);
    }
}

package com.example.patent.translation.api;

import com.example.patent.notification.infrastructure.messaging.InMemoryEventPublisher;
import com.example.patent.translation.application.TranslationService;
import com.example.patent.translation.application.port.TranslationRepository;
import com.example.patent.translation.domain.TranslationDocument;
import com.example.patent.translation.domain.TranslationRecord;
import com.example.patent.translation.domain.TranslationStatus;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"translation.min-delay-ms=300", "translation.max-delay-ms=300",
        "translation.api-key=test-key"})
@AutoConfigureMockMvc
class TranslationApiTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired TranslationService service;
    @Autowired TranslationRepository repository;
    @Autowired InMemoryEventPublisher publisher;

    private static final String BODY = """
            {"patentId":"EP123","sourceLang":"en","targetLang":"tr","documentText":"Patent 🌍"}
            """;

    private String submit() throws Exception {
        String json = mvc.perform(post("/api/translate").header("X-API-Key", "test-key")
                        .contentType("application/json").content(BODY))
                .andExpect(status().isAccepted()).andExpect(jsonPath("status").value("PENDING"))
                .andReturn().getResponse().getContentAsString();
        return mapper.readTree(json).get("requestId").asText();
    }

    @Test
    void fullLifecyclePublishesExactlyOneMatchingEvent() throws Exception {
        String id = submit();
        mvc.perform(get("/api/translate/{id}/status", id).header("X-API-Key", "test-key"))
                .andExpect(status().isOk()).andExpect(jsonPath("status").value("PENDING"));
        mvc.perform(get("/api/translate/{id}/result", id).header("X-API-Key", "test-key"))
                .andExpect(status().isConflict()).andExpect(jsonPath("error").value("NOT_READY"));

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                assertThat(service.find(UUID.fromString(id)).status()).isEqualTo(TranslationStatus.COMPLETED));

        mvc.perform(get("/api/translate/{id}/status", id).header("X-API-Key", "test-key"))
                .andExpect(status().isOk()).andExpect(jsonPath("status").value("COMPLETED"));
        mvc.perform(get("/api/translate/{id}/result", id).header("X-API-Key", "test-key"))
                .andExpect(status().isOk()).andExpect(jsonPath("requestId").value(id))
                .andExpect(jsonPath("resultText").value("🌍 tnetaP"));

        var events = publisher.events().stream().filter(e -> e.event().requestId().toString().equals(id)).toList();
        assertThat(events).hasSize(1);
        assertThat(events.getFirst().topic()).isEqualTo("patent-updates");
        var event = events.getFirst().event();
        assertThat(event.eventType()).isEqualTo("TRANSLATION_COMPLETED");
        assertThat(event.patentId()).isEqualTo("EP123");
        assertThat(event.targetLang()).isEqualTo("tr");
        assertThat(event.timestamp()).isNotNull();
    }

    @Test
    void requiresApiKeyOnAllTranslationEndpoints() throws Exception {
        mvc.perform(post("/api/translate").contentType("application/json").content(BODY))
                .andExpect(status().isUnauthorized());
        for (String suffix : List.of("status", "result")) {
            mvc.perform(get("/api/translate/{id}/" + suffix, UUID.randomUUID())
                            .header("X-API-Key", "incorrect"))
                    .andExpect(status().isUnauthorized()).andExpect(jsonPath("error").value("UNAUTHORIZED"));
        }
    }

    @Test
    void rejectsInvalidInputAndMalformedJson() throws Exception {
        for (String body : List.of("{", "null", "[]", "{}", BODY.replace("EP123", " "))) {
            mvc.perform(post("/api/translate").header("X-API-Key", "test-key")
                            .contentType("application/json").content(body))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(post("/api/translate").header("X-API-Key", "test-key")
                        .contentType("text/plain").content(BODY))
                .andExpect(status().isUnsupportedMediaType());
    }

    @Test
    void unknownAndInvalidIdsHaveUsefulErrors() throws Exception {
        for (String suffix : List.of("status", "result")) {
            mvc.perform(get("/api/translate/{id}/" + suffix, UUID.randomUUID()).header("X-API-Key", "test-key"))
                    .andExpect(status().isNotFound()).andExpect(jsonPath("error").value("NOT_FOUND"));
            mvc.perform(get("/api/translate/invalid/" + suffix).header("X-API-Key", "test-key"))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void exposesOpenApiAndSwaggerWithoutApiKey() throws Exception {
        mvc.perform(get("/openapi.json")).andExpect(status().isOk())
                .andExpect(jsonPath("paths['/api/translate'].post").exists())
                .andExpect(jsonPath("components.securitySchemes.ApiKey.name").value("X-API-Key"));
        mvc.perform(get("/swagger-ui.html")).andExpect(status().is3xxRedirection());
    }

    @Test
    void failedRequestsExposeStatusAndRejectResults() throws Exception {
        UUID id = UUID.randomUUID();
        repository.save(new TranslationRecord(id, new TranslationDocument("EP1", "en", "tr", "text"),
                TranslationStatus.FAILED, null));
        mvc.perform(get("/api/translate/{id}/status", id).header("X-API-Key", "test-key"))
                .andExpect(status().isOk()).andExpect(jsonPath("status").value("FAILED"));
        mvc.perform(get("/api/translate/{id}/result", id).header("X-API-Key", "test-key"))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("error").value("TRANSLATION_FAILED"));
    }

    @Test
    void concurrentRequestsKeepSeparateIdsAndPublishOnceEach() {
        var futures = IntStream.range(0, 10).mapToObj(i -> CompletableFuture.supplyAsync(() -> {
            try { return submit(); } catch (Exception exception) { throw new RuntimeException(exception); }
        })).toList();
        var ids = futures.stream().map(CompletableFuture::join).toList();
        assertThat(ids).doesNotHaveDuplicates();
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            for (String id : ids) {
                assertThat(service.find(UUID.fromString(id)).status()).isEqualTo(TranslationStatus.COMPLETED);
                assertThat(publisher.events().stream().filter(e -> e.event().requestId().toString().equals(id)).count())
                        .isEqualTo(1);
            }
        });
    }
}

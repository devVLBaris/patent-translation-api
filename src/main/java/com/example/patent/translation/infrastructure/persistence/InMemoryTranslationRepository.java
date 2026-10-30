package com.example.patent.translation.infrastructure.persistence;

import com.example.patent.translation.application.port.TranslationRepository;
import com.example.patent.translation.domain.TranslationRecord;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.stereotype.Repository;

@Repository
public class InMemoryTranslationRepository implements TranslationRepository {
    private final ConcurrentMap<UUID, TranslationRecord> requests = new ConcurrentHashMap<>();

    @Override
    public void save(TranslationRecord request) {
        requests.put(request.requestId(), request);
    }

    @Override
    public Optional<TranslationRecord> find(UUID requestId) {
        return Optional.ofNullable(requests.get(requestId));
    }
}

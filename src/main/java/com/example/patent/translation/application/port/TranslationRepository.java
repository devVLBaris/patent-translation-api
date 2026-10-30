package com.example.patent.translation.application.port;

import com.example.patent.translation.domain.TranslationRecord;
import java.util.Optional;
import java.util.UUID;

public interface TranslationRepository {
    void save(TranslationRecord request);
    Optional<TranslationRecord> find(UUID requestId);
}

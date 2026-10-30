package com.example.patent.translation.api.dto;

import com.example.patent.translation.domain.TranslationStatus;
import java.util.UUID;

public record StatusResponse(UUID requestId, TranslationStatus status) {
}

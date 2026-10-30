package com.example.patent.translation.api.dto;

import com.example.patent.translation.domain.TranslationDocument;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SubmitTranslationRequest(
        @NotBlank @Size(max = 200) String patentId,
        @NotBlank @Size(max = 35) String sourceLang,
        @NotBlank @Size(max = 35) String targetLang,
        @NotBlank @Size(max = 1_000_000) String documentText) {
    public TranslationDocument toDocument() {
        return new TranslationDocument(patentId, sourceLang, targetLang, documentText);
    }
}

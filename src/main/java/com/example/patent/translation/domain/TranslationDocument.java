package com.example.patent.translation.domain;

/** Document data used by the translation workflow, independent of HTTP validation. */
public record TranslationDocument(String patentId, String sourceLang, String targetLang, String documentText) {
}

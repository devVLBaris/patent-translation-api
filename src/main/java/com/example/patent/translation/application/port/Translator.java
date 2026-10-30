package com.example.patent.translation.application.port;

import com.example.patent.translation.domain.TranslationDocument;

public interface Translator {
    String translate(TranslationDocument document);
}

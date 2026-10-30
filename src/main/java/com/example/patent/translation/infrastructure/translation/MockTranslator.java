package com.example.patent.translation.infrastructure.translation;

import com.example.patent.translation.application.port.Translator;
import com.example.patent.translation.domain.TranslationDocument;

import org.springframework.stereotype.Component;

@Component
public class MockTranslator implements Translator {
    @Override
    public String translate(TranslationDocument input) {
        return new StringBuilder(input.documentText()).reverse().toString();
    }
}

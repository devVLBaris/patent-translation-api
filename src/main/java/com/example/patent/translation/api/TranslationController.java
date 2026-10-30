package com.example.patent.translation.api;

import com.example.patent.shared.api.ApiException;
import com.example.patent.translation.api.dto.ResultResponse;
import com.example.patent.translation.api.dto.StatusResponse;
import com.example.patent.translation.api.dto.SubmitTranslationRequest;
import com.example.patent.translation.application.TranslationService;
import com.example.patent.translation.domain.TranslationStatus;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/translate")
public class TranslationController {
    private final TranslationService translationService;

    public TranslationController(TranslationService translationService) {
        this.translationService = translationService;
    }


    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "Submit a patent document for mock translation")
    public StatusResponse submit(@Valid @RequestBody SubmitTranslationRequest input) {
        var request = translationService.submit(input.toDocument());
        return new StatusResponse(request.requestId(), request.status());
    }

    @GetMapping("/{requestId}/status")
    @Operation(summary = "Read translation status")
    public StatusResponse status(@PathVariable UUID requestId) {
        var request = translationService.find(requestId);
        return new StatusResponse(request.requestId(), request.status());
    }

    @GetMapping("/{requestId}/result")
    @Operation(summary = "Read completed translated document")
    public ResultResponse result(@PathVariable UUID requestId) {
        var request = translationService.find(requestId);
        if (request.status() == TranslationStatus.PENDING) {
            throw new ApiException(HttpStatus.CONFLICT, "NOT_READY", "Translation is pending");
        }
        if (request.status() == TranslationStatus.FAILED) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "TRANSLATION_FAILED",
                    "Translation or notification failed");
        }
        return new ResultResponse(request.requestId(), request.resultText());
    }
}

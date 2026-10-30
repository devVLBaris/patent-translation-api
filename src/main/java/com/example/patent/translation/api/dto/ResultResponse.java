package com.example.patent.translation.api.dto;

import java.util.UUID;

public record ResultResponse(UUID requestId, String resultText) {
}

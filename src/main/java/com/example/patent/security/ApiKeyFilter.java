package com.example.patent.security;

import com.example.patent.shared.api.ErrorResponse;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class ApiKeyFilter extends OncePerRequestFilter {
    private final byte[] apiKey;
    private final ObjectMapper mapper;

    public ApiKeyFilter(@Value("${translation.api-key}") String apiKey, ObjectMapper mapper) {
        if (apiKey.isBlank()) throw new IllegalArgumentException("API_KEY must not be blank");
        this.apiKey = apiKey.getBytes(StandardCharsets.UTF_8);
        this.mapper = mapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        if (path.isEmpty()) {
            path = request.getRequestURI().substring(request.getContextPath().length());
        }
        return !(path.equals("/api") || path.startsWith("/api/"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String supplied = request.getHeader("X-API-Key");
        if (supplied == null || !MessageDigest.isEqual(apiKey, supplied.getBytes(StandardCharsets.UTF_8))) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            mapper.writeValue(response.getOutputStream(),
                    new ErrorResponse("UNAUTHORIZED", "Valid X-API-Key required"));
            return;
        }
        chain.doFilter(request, response);
    }
}

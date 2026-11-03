/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.security.guardrail;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Backend that calls a remote model API.
 * <p>
 * Mirrors Python's {@code APIModelBackend} in
 * {@code openjiuwen/core/security/guardrail/backends.py}.
 */
public class APIModelBackend extends GuardrailBackend {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final String apiUrl;
    private final ModelOutputParser parser;
    private final String apiKey;
    private final double timeout;
    private final String riskType;

    public APIModelBackend(APIModelBackendConfig config) {
        this(
                config != null ? config.apiUrl() : null,
                config != null ? config.parser() : null,
                config != null ? config.apiKey() : null,
                config != null ? config.timeout() : 30.0d,
                config != null ? config.riskType() : "model_detection"
        );
    }

    public APIModelBackend(String apiUrl, ModelOutputParser parser, String apiKey, double timeout, String riskType) {
        this.apiUrl = apiUrl;
        this.parser = parser;
        this.apiKey = apiKey;
        this.timeout = timeout;
        this.riskType = riskType == null || riskType.isBlank() ? "model_detection" : riskType;
    }

    @Override
    public RiskAssessment analyze(GuardrailContext ctx) {
        String text = contextText(ctx);
        if (text.isBlank()) {
            return new RiskAssessment(false, RiskLevel.SAFE, null, 1.0d, null);
        }
        if (parser == null) {
            throw new IllegalStateException("APIModelBackend requires parser");
        }
        return parser.parse(callApi(text));
    }

    public String getApiUrl() {
        return apiUrl;
    }

    public ModelOutputParser getParser() {
        return parser;
    }

    public String getApiKey() {
        return apiKey;
    }

    public double getTimeout() {
        return timeout;
    }

    public String getRiskType() {
        return riskType;
    }

    protected Object callApi(String text) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("text", text);
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl))
                    .timeout(Duration.ofMillis((long) (timeout * 1000)))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(OBJECT_MAPPER.writeValueAsString(payload)));
            if (apiKey != null && !apiKey.isBlank()) {
                builder.header("Authorization", "Bearer " + apiKey);
            }
            HttpResponse<String> response = HttpClient.newHttpClient().send(
                    builder.build(),
                    HttpResponse.BodyHandlers.ofString()
            );
            if (response.statusCode() >= 400) {
                throw new IllegalStateException("Remote guardrail API failed: " + response.statusCode());
            }
            return OBJECT_MAPPER.readValue(response.body(), Object.class);
        } catch (IOException | InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Failed to call remote guardrail API", exception);
        }
    }
}

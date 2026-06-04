/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.security.guardrail;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/**
 * Backend that calls a remote model API and parses the model output.
 *
 * <p>Mirrors Python's {@code APIModelBackend} in
 * {@code openjiuwen.core.security.guardrail.backends}.</p>
 */
public class APIModelBackend implements GuardrailBackend {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final String apiUrl;
    private final ModelOutputParser parser;
    private final String apiKey;
    private final double timeout;
    private final String riskType;

    public APIModelBackend(APIModelBackendConfig config) {
        this(
                config != null ? config.getApiUrl() : null,
                config != null ? config.getParser() : null,
                config != null ? config.getApiKey() : null,
                config != null ? config.getTimeout() : 30.0,
                config != null ? config.getRiskType() : "model_detection"
        );
    }

    public APIModelBackend(String apiUrl, ModelOutputParser parser, String apiKey, double timeout, String riskType) {
        this.apiUrl = apiUrl;
        this.parser = parser;
        this.apiKey = apiKey;
        this.timeout = timeout;
        this.riskType = riskType != null ? riskType : "model_detection";
    }

    @Override
    public RiskAssessment analyze(Map<String, Object> data) throws Exception {
        return analyze(toContext(data));
    }

    public RiskAssessment analyze(GuardrailContext context) throws Exception {
        String text = extractText(context);
        if (text.isEmpty()) {
            return RiskAssessment.builder()
                    .hasRisk(false)
                    .riskLevel(RiskLevel.SAFE)
                    .confidence(1.0)
                    .build();
        }
        if (parser == null) {
            throw new IllegalStateException("parser is required for API model backend");
        }
        return parser.parse(callApi(text));
    }

    protected Object callApi(String text) throws Exception {
        if (apiUrl == null || apiUrl.isBlank()) {
            throw new IllegalStateException("api_url is required for api mode");
        }
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(timeoutMillis()))
                .build();
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(apiUrl))
                .timeout(Duration.ofMillis(timeoutMillis()))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(OBJECT_MAPPER.writeValueAsString(Map.of("text", text))));
        if (apiKey != null && !apiKey.isBlank()) {
            builder.header("Authorization", "Bearer " + apiKey);
        }

        HttpResponse<String> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("API model backend returned HTTP " + response.statusCode());
        }
        return OBJECT_MAPPER.readValue(response.body(), Object.class);
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

    private long timeoutMillis() {
        return Math.max(1L, Math.round(timeout * 1000.0));
    }

    private GuardrailContext toContext(Map<String, Object> data) {
        Object text = data != null
                ? firstNonNull(data.get("text"), data.get("content"), data.get("prompt"), data.get("result"))
                : null;
        return new GuardrailContext(
                GuardrailContentType.TEXT,
                text != null ? String.valueOf(text) : "",
                data != null ? String.valueOf(data.getOrDefault("event", "")) : "",
                data
        );
    }

    private String extractText(GuardrailContext context) {
        if (context == null) {
            return "";
        }
        return context.getText().orElse(context.getContent() != null ? String.valueOf(context.getContent()) : "");
    }

    private Object firstNonNull(Object... values) {
        for (Object value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }
}

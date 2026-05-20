/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentteams.schema.team;

import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
/**
 * Public class ModelPoolEntry used by the Java parity implementation.
 *
 * @since 1.0
 */
@AllArgsConstructor
public class ModelPoolEntry {
    @Builder.Default
    private String modelId = UUID.randomUUID().toString();
    @Builder.Default
    private String provider = "";
    @Builder.Default
    private String modelName = "";
    @Builder.Default
    private String apiKey = "";
    @Builder.Default
    private String apiBaseUrl = "";
    @Builder.Default
    private String description = "";
    @Builder.Default
    private Map<String, Object> metadata = new LinkedHashMap<>();
    @Builder.Default
    private int weight = 1;

    /**
     * Auto-generated for codecheck compliance.
     */
    public TeamModelConfig toTeamModelConfig() {
        ModelClientConfig.Builder clientBuilder = ModelClientConfig.builder();
        applyClientMetadata(clientBuilder, nestedMap("client"));
        clientBuilder.clientId(modelId);
        clientBuilder.clientProvider(provider);
        clientBuilder.apiKey(apiKey);
        clientBuilder.apiBase(apiBaseUrl);

        ModelRequestConfig.ModelRequestConfigBuilder requestBuilder = ModelRequestConfig.builder();
        Map<String, Object> requestExtras = new LinkedHashMap<>();
        applyRequestMetadata(requestBuilder, requestExtras, nestedMap("request"));
        requestBuilder.modelName(modelName);
        requestBuilder.extraFields(requestExtras);
        return new TeamModelConfig(clientBuilder.build(), requestBuilder.build());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> nestedMap(String key) {
        if (metadata == null) {
            return Map.of();
        }
        Object value = metadata.get(key);
        if (value instanceof Map<?, ?> raw) {
            Map<String, Object> result = new LinkedHashMap<>();
            raw.forEach((nestedKey, nestedValue) -> {
                if (nestedKey != null) {
                    result.put(String.valueOf(nestedKey), nestedValue);
                }
            });
            return result;
        }
        return Map.of();
    }

    private void applyClientMetadata(ModelClientConfig.Builder builder, Map<String, Object> clientMetadata) {
        for (Map.Entry<String, Object> entry : clientMetadata.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            switch (key) {
                case "client_id", "clientId" -> builder.clientId(asString(value));
                case "client_provider", "clientProvider" -> builder.clientProvider(asString(value));
                case "api_key", "apiKey" -> builder.apiKey(asString(value));
                case "api_base", "apiBase" -> builder.apiBase(asString(value));
                case "timeout" -> {
                    Double timeout = asDouble(value);
                    if (timeout != null) {
                        builder.timeout(timeout);
                    }
                }
                case "max_retries", "maxRetries" -> {
                    Integer maxRetries = asInteger(value);
                    if (maxRetries != null) {
                        builder.maxRetries(maxRetries);
                    }
                }
                case "verify_ssl", "verifySsl" -> {
                    Boolean isVerifySsl = asBoolean(value);
                    if (isVerifySsl != null) {
                        builder.verifySsl(isVerifySsl);
                    }
                }
                case "ssl_cert", "sslCert" -> builder.sslCert(asString(value));
                case "headers" -> {
                    if (value instanceof Map<?, ?> headers) {
                        Map<String, Object> normalizedHeaders = new LinkedHashMap<>();
                        headers.forEach((headerKey, headerValue) -> {
                            if (headerKey != null) {
                                normalizedHeaders.put(String.valueOf(headerKey), headerValue);
                            }
                        });
                        builder.headers(normalizedHeaders);
                    }
                }
                default -> builder.extraField(key, value);
            }
        }
    }

    private void applyRequestMetadata(
            ModelRequestConfig.ModelRequestConfigBuilder builder,
            Map<String, Object> requestExtras,
            Map<String, Object> requestMetadata
    ) {
        for (Map.Entry<String, Object> entry : requestMetadata.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            switch (key) {
                case "model", "model_name", "modelName" -> builder.modelName(asString(value));
                case "temperature" -> {
                    Double temperature = asDouble(value);
                    if (temperature != null) {
                        builder.temperature(temperature);
                    }
                }
                case "top_p", "topP" -> {
                    Double topP = asDouble(value);
                    if (topP != null) {
                        builder.topP(topP);
                    }
                }
                case "max_tokens", "maxTokens" -> {
                    Integer maxTokens = asInteger(value);
                    if (maxTokens != null) {
                        builder.maxTokens(maxTokens);
                    }
                }
                case "stop" -> builder.stop(asString(value));
                case "user" -> builder.user(asString(value));
                case "seed" -> {
                    Integer seed = asInteger(value);
                    if (seed != null) {
                        builder.seed(seed);
                    }
                }
                default -> requestExtras.put(key, value);
            }
        }
    }

    private static String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static Integer asInteger(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text) {
            try {
                return Integer.parseInt(text);
            } catch (NumberFormatException ignored) {
                return nullValue();
            }
        }
        return nullValue();
    }

    private static Double asDouble(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof String text) {
            try {
                return Double.parseDouble(text);
            } catch (NumberFormatException ignored) {
                return nullValue();
            }
        }
        return nullValue();
    }

    private static Boolean asBoolean(Object value) {
        if (value instanceof Boolean isBoolValue) {
            return isBoolValue;
        }
        if (value instanceof String text) {
            if ("true".equalsIgnoreCase(text)) {
                return true;
            }
            if ("false".equalsIgnoreCase(text)) {
                return false;
            }
        }
        return nullValue();
    }
    private static <T> T nullValue() {
        return null;
    }

}

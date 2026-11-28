/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.schema.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Maps declarative model settings to the typed model configuration objects.
 *
 * @since 2026-11-28
 */
public final class ModelConfigMapper {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ModelConfigMapper() {
    }

    /**
     * Converts a YAML backend map into a client configuration.
     *
     * @param source YAML backend settings
     * @return typed client configuration, empty for unsupported or incomplete input
     */
    public static Optional<ModelClientConfig> toClientConfig(Object source) {
        if (source instanceof ModelClientConfig clientConfig) {
            return Optional.of(clientConfig);
        }
        if (!(source instanceof Map<?, ?> sourceMap)) {
            return Optional.empty();
        }
        Map<String, Object> fields = normalizeKeys(sourceMap);
        if (!hasText(fields, "client_provider") || !hasText(fields, "api_key") || !hasText(fields, "api_base")) {
            return Optional.empty();
        }
        if (!fields.containsKey("timeout")) {
            return Optional.of(MAPPER.convertValue(fields, ModelClientConfig.class));
        }
        Object legacyTimeout = fields.remove("timeout");
        ModelClientConfig client = MAPPER.convertValue(fields, ModelClientConfig.class);
        if (legacyTimeout != null) {
            client.setTimeout(MAPPER.convertValue(legacyTimeout, Double.class));
        }
        return Optional.of(client);
    }

    private static Map<String, Object> normalizeKeys(Map<?, ?> source) {
        Map<String, Object> fields = new LinkedHashMap<>();
        source.forEach((key, value) -> fields.put(String.valueOf(key), value));
        renameIfMissing(fields, "model_provider", "client_provider");
        renameIfMissing(fields, "modelProvider", "client_provider");
        renameIfMissing(fields, "provider", "client_provider");
        renameIfMissing(fields, "backend", "client_provider");
        renameIfMissing(fields, "base_url", "api_base");
        renameIfMissing(fields, "baseUrl", "api_base");
        renameIfMissing(fields, "clientProvider", "client_provider");
        renameIfMissing(fields, "clientId", "client_id");
        renameIfMissing(fields, "apiKey", "api_key");
        renameIfMissing(fields, "apiBase", "api_base");
        renameIfMissing(fields, "maxRetries", "max_retries");
        renameIfMissing(fields, "connectTimeoutSeconds", "connect_timeout_seconds");
        renameIfMissing(fields, "streamIdleSeconds", "stream_idle_seconds");
        renameIfMissing(fields, "retryOnRateLimit", "retry_on_rate_limit");
        renameIfMissing(fields, "retryOnTemporary", "retry_on_temporary");
        renameIfMissing(fields, "verifySsl", "verify_ssl");
        renameIfMissing(fields, "sslCert", "ssl_cert");
        renameIfMissing(fields, "headers", "custom_headers");
        return fields;
    }

    private static void renameIfMissing(Map<String, Object> fields, String alias, String target) {
        if (!fields.containsKey(target) && fields.containsKey(alias)) {
            fields.put(target, fields.get(alias));
        }
        fields.remove(alias);
    }

    private static boolean hasText(Map<String, Object> fields, String key) {
        Object value = fields.get(key);
        return value != null && !String.valueOf(value).isBlank();
    }
}

/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.schema.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Verifies YAML-compatible model client maps retain timeout and retry settings.
 *
 * @since 2026-11-28
 */
class ModelConfigMapperTest {
    @Test
    void toClientConfig_mapsTimeoutLayersAndRetryPolicy() {
        Map<String, Object> yaml = new LinkedHashMap<>();
        yaml.put("client_provider", "openai");
        yaml.put("api_key", "test-key");
        yaml.put("base_url", "https://model.example.com/v1");
        yaml.put("connect_timeout_seconds", 12);
        yaml.put("stream_idle_seconds", 300);
        yaml.put("timeout", 120);
        yaml.put("max_retries", 4);
        yaml.put("retry_on_rate_limit", false);
        yaml.put("retry_on_temporary", false);

        ModelClientConfig client = ModelConfigMapper.toClientConfig(yaml).orElseThrow();

        assertThat(client.getApiBase()).isEqualTo("https://model.example.com/v1");
        assertThat(client.getConnectTimeoutSeconds()).isEqualTo(12.0D);
        assertThat(client.getStreamIdleSeconds()).isEqualTo(300.0D);
        assertThat(client.getTimeout()).isEqualTo(120.0D);
        assertThat(client.isTimeoutExplicit()).isTrue();
        assertThat(client.getMaxRetries()).isEqualTo(4);
        assertThat(client.isRetryOnRateLimit()).isFalse();
        assertThat(client.isRetryOnTemporary()).isFalse();
    }

    @Test
    void toClientConfig_omittedLayersKeepCoreDefaults() {
        ModelClientConfig client = ModelConfigMapper.toClientConfig(Map.of(
                "client_provider", "openai",
                "api_key", "test-key",
                "api_base", "https://model.example.com/v1")).orElseThrow();

        assertThat(client.getConnectTimeoutSeconds()).isNull();
        assertThat(client.getStreamIdleSeconds()).isNull();
        assertThat(client.isTimeoutExplicit()).isFalse();
        assertThat(client.getMaxRetries()).isEqualTo(2);
        assertThat(client.isRetryOnRateLimit()).isTrue();
        assertThat(client.isRetryOnTemporary()).isTrue();
    }

    @Test
    void toClientConfig_invalidLayerIsRejected() {
        assertThatThrownBy(() -> ModelConfigMapper.toClientConfig(Map.of(
                "client_provider", "openai",
                "api_key", "test-key",
                "api_base", "https://model.example.com/v1",
                "stream_idle_seconds", 30)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("streamIdleSeconds");
    }

    @Test
    void toClientConfig_nullLegacyTimeoutKeepsDefault() {
        Map<String, Object> yaml = new LinkedHashMap<>();
        yaml.put("client_provider", "openai");
        yaml.put("api_key", "test-key");
        yaml.put("api_base", "https://model.example.com/v1");
        yaml.put("timeout", null);

        ModelClientConfig client = ModelConfigMapper.toClientConfig(yaml).orElseThrow();

        assertThat(client.getTimeout()).isEqualTo(60.0D);
        assertThat(client.isTimeoutExplicit()).isFalse();
    }

    @Test
    void toClientConfig_mapsJavaStyleBackendKeys() {
        ModelClientConfig client = ModelConfigMapper.toClientConfig(Map.ofEntries(
                Map.entry("clientProvider", "openai"),
                Map.entry("apiKey", "test-key"),
                Map.entry("apiBase", "https://model.example.com/v1"),
                Map.entry("connectTimeoutSeconds", 12),
                Map.entry("streamIdleSeconds", 300),
                Map.entry("maxRetries", 4),
                Map.entry("retryOnRateLimit", false),
                Map.entry("retryOnTemporary", false),
                Map.entry("verifySsl", false),
                Map.entry("sslCert", "test-cert"),
                Map.entry("headers", Map.of("X-Test", "value")))).orElseThrow();

        assertThat(client.getConnectTimeoutSeconds()).isEqualTo(12.0D);
        assertThat(client.getStreamIdleSeconds()).isEqualTo(300.0D);
        assertThat(client.getMaxRetries()).isEqualTo(4);
        assertThat(client.isRetryOnRateLimit()).isFalse();
        assertThat(client.isRetryOnTemporary()).isFalse();
        assertThat(client.isVerifySsl()).isFalse();
        assertThat(client.getSslCert()).isEqualTo("test-cert");
        assertThat(client.getHeaders()).containsEntry("X-Test", "value");
    }
}

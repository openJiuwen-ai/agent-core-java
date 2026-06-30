/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.clients;

import com.openai.client.OpenAIClient;
import com.openai.client.OpenAIClientAsync;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.client.okhttp.OpenAIOkHttpClientAsync;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;

import java.time.Duration;

/**
 * Static entrypoints mirroring Python's openjiuwen.core.common.clients exports.
 */
public final class Clients {
    private Clients() {
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static ClientRegistry getClientRegistry() {
        return ClientRegistry.getInstance();
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static ConnectorPoolManager getConnectorPoolManager() {
        return ConnectorPoolManager.getInstance();
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static HttpSessionManager getHttpSessionManager() {
        return HttpSessionManager.getInstance();
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static java.net.http.HttpClient createHttpxClient(HttpXConnectorPoolConfig config) {
        Object conn = getConnectorPoolManager().getConnectorPool("httpx", config).conn();
        if (!(conn instanceof java.net.http.HttpClient httpClient)) {
            throw new IllegalStateException("httpx connector pool did not return HttpClient");
        }
        return httpClient;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static OpenAIClientAsync createAsyncOpenAiClient(ModelClientConfig config) {
        return OpenAIOkHttpClientAsync.builder()
                .apiKey(config.getApiKey())
                .baseUrl(config.getApiBase())
                .timeout(Duration.ofSeconds(Math.max(1L, Math.round(config.getTimeout()))))
                .maxRetries(config.getMaxRetries())
                .build();
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static OpenAIClient createOpenAiClient(ModelClientConfig config) {
        return OpenAIOkHttpClient.builder()
                .apiKey(config.getApiKey())
                .baseUrl(config.getApiBase())
                .timeout(Duration.ofSeconds(Math.max(1L, Math.round(config.getTimeout()))))
                .maxRetries(config.getMaxRetries())
                .build();
    }
}

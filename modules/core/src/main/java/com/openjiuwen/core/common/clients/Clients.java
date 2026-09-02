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
 * 
 * @since 0.1.7
 */
public final class Clients {
    /**
     * Clients.
     * 
     * @since 0.1.7
     */
    private Clients() {
    }

    /**
     * getClientRegistry.
     * 
     * @return the result
     * @since 0.1.7
     */
    public static ClientRegistry getClientRegistry() {
        return ClientRegistry.getInstance();
    }

    /**
     * getConnectorPoolManager.
     * 
     * @return the result
     * @since 0.1.7
     */
    public static ConnectorPoolManager getConnectorPoolManager() {
        return ConnectorPoolManager.getInstance();
    }

    /**
     * getHttpSessionManager.
     * 
     * @return the result
     * @since 0.1.7
     */
    public static HttpSessionManager getHttpSessionManager() {
        return HttpSessionManager.getInstance();
    }

    /**
     * createHttpxClient.
     * 
     * @param config config
     * @return HttpClient
     * @since 0.1.7
     */
    public static java.net.http.HttpClient createHttpxClient(HttpXConnectorPoolConfig config) {
        Object conn = getConnectorPoolManager().getConnectorPool("httpx", config).conn();
        if (!(conn instanceof java.net.http.HttpClient httpClient)) {
            throw new IllegalStateException("httpx connector pool did not return HttpClient");
        }
        return httpClient;
    }

    /**
     * createAsyncOpenAiClient.
     * 
     * @param config config
     * @return the result
     * @since 0.1.7
     */
    public static OpenAIClientAsync createAsyncOpenAiClient(ModelClientConfig config) {
        return OpenAIOkHttpClientAsync.builder().apiKey(config.getApiKey()).baseUrl(config.getApiBase())
                .timeout(Duration.ofSeconds(Math.max(1L, Math.round(config.getTimeout()))))
                .maxRetries(config.getMaxRetries()).build();
    }

    /**
     * createOpenAiClient.
     * 
     * @param config config
     * @return the result
     * @since 0.1.7
     */
    public static OpenAIClient createOpenAiClient(ModelClientConfig config) {
        return OpenAIOkHttpClient.builder().apiKey(config.getApiKey()).baseUrl(config.getApiBase())
                .timeout(Duration.ofSeconds(Math.max(1L, Math.round(config.getTimeout()))))
                .maxRetries(config.getMaxRetries()).build();
    }
}

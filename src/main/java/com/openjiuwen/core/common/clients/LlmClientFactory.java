/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.clients;

import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;

import java.net.http.HttpClient;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Factory for creating HTTPX clients and OpenAI clients.
 * <p>
 * Mirrors Python's {@code llm_client.py} module from
 * <code>openjiuwen/core/common/clients/llm_client.py</code>.
 *
 * <p>Provides factory methods for creating HTTPX-style clients and
 * OpenAI-compatible model clients with connection pooling.
 */
public class LlmClientFactory {

    private static final ClientRegistry clientRegistry = new ClientRegistry();

    /**
     * Create an HTTPX-style client with connection pooling.
     *
     * @param config configuration for the connection pool
     * @param needAsync if true, returns an async-compatible client
     * @return configured HttpClient instance
     */
    public static CompletableFuture<HttpClient> createHttpxClient(
            HttpXConnectorPoolConfig config, boolean needAsync) {

        if (config == null) {
            config = new HttpXConnectorPoolConfig();
        }
        config.setNeedAsync(needAsync);

        HttpXConnectorPool pool = new HttpXConnectorPool(config);
        return CompletableFuture.completedFuture((HttpClient) pool.conn());
    }

    /**
     * Create an HTTPX-style client from a config map.
     *
     * @param configMap configuration map
     * @param needAsync if true, returns async client
     * @return configured HttpClient
     */
    public static CompletableFuture<HttpClient> createHttpxClient(
            Map<String, Object> configMap, boolean needAsync) {

        HttpXConnectorPoolConfig config = new HttpXConnectorPoolConfig();

        if (configMap.containsKey("max_keepalive_connections")) {
            config.setMaxKeepaliveConnections((Integer) configMap.get("max_keepalive_connections"));
        }
        if (configMap.containsKey("local_address")) {
            config.setLocalAddress((String) configMap.get("local_address"));
        }
        if (configMap.containsKey("proxy")) {
            config.setProxy((String) configMap.get("proxy"));
        }
        if (configMap.containsKey("ssl_verify")) {
            // SSL verification settings
        }

        return createHttpxClient(config, needAsync);
    }

    /**
     * Create an async OpenAI-compatible client.
     *
     * @param config model client configuration
     * @param kwargs additional arguments
     * @return HttpClient configured for OpenAI API calls
     */
    public static CompletableFuture<HttpClient> createAsyncOpenaiClient(
            ModelClientConfig config, Map<String, Object> kwargs) {

        Map<String, Object> httpConfig = new HashMap<>();

        if (kwargs != null) {
            httpConfig.putAll(kwargs);
        }

        // Add proxy if configured
        if (config.getApiBase() != null) {
            // Get global proxy URL if available
            String proxyUrl = getGlobalProxyUrl(config.getApiBase());
            if (proxyUrl != null) {
                httpConfig.put("proxy", proxyUrl);
            }
        }

        return createHttpxClient(httpConfig, true);
    }

    /**
     * Create a synchronous OpenAI-compatible client.
     *
     * @param config model client configuration
     * @param kwargs additional arguments
     * @return HttpClient configured for OpenAI API calls
     */
    public static CompletableFuture<HttpClient> createOpenaiClient(
            ModelClientConfig config, Map<String, Object> kwargs) {

        Map<String, Object> httpConfig = new HashMap<>();

        if (kwargs != null) {
            httpConfig.putAll(kwargs);
        }

        // Add proxy if configured
        if (config.getApiBase() != null) {
            String proxyUrl = getGlobalProxyUrl(config.getApiBase());
            if (proxyUrl != null) {
                httpConfig.put("proxy", proxyUrl);
            }
        }

        return createHttpxClient(httpConfig, false);
    }

    /**
     * Get global proxy URL for a given API base URL.
     * Placeholder implementation - should integrate with UrlUtils.
     *
     * @param apiBase the API base URL
     * @return proxy URL or null
     */
    private static String getGlobalProxyUrl(String apiBase) {
        // TODO: Integrate with UrlUtils.getGlobalProxyUrl
        return null;
    }

    /**
     * Sanitize headers for use with HTTP clients.
     *
     * @param headers map of headers to sanitize
     * @return sanitized headers map
     */
    public static Map<String, String> sanitizeHeaders(Map<String, String> headers) {
        if (headers == null || headers.isEmpty()) {
            return new HashMap<>();
        }
        Map<String, String> sanitized = new HashMap<>();
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            String key = entry.getKey().trim();
            String value = entry.getValue();
            if (!key.isEmpty() && value != null) {
                sanitized.put(key, value.trim());
            }
        }
        return sanitized;
    }
}
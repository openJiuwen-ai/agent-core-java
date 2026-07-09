/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.retrieval.common.RetrievalExceptions;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;

/**
 * Shared HTTP request helper for retrieval services with retry support.
 * <p>
 * Aligned with Python {@code api_requests.py}: supports sync requests with retry,
 * async requests via {@link CompletableFuture}, and pluggable status-code callbacks.
 * </p>
 * 
 * @since 0.1.7
 */
public final class ApiRequestUtils {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * Callback for custom status code handling.
     * Takes (statusCode, attempt) and returns whether to retry.
     * 
     * @since 0.1.7
     */
    @FunctionalInterface
    public interface StatusCodeCallback extends BiFunction<Integer, Integer, Boolean> {
    }

    /**
     * DEFAULT_CALLBACK.
     * 
     * @since 0.1.7
     */
    public static final StatusCodeCallback DEFAULT_CALLBACK =
        (statusCode, attempt) -> statusCode == 429 || statusCode == 500 || statusCode == 503;

    /**
     * ApiRequestUtils.
     * 
     * @since 0.1.7
     */
    private ApiRequestUtils() {
    }

    /**
     * Send a POST request with JSON payload and retry support (sync).
     * 
     * @param httpClient httpClient
     * @param url url
     * @param payload payload
     * @param headers headers
     * @param timeout timeout
     * @param maxRetries maxRetries
     * @param failureCode failureCode
     * @param taskName taskName
     * @return the result
     * @since 0.1.7
     */
    public static JsonNode postJsonWithRetry(HttpClient httpClient, String url, Map<String, Object> payload,
            Map<String, String> headers, Duration timeout, int maxRetries, StatusCode failureCode, String taskName) {
        return postJsonWithRetry(httpClient, url, payload, headers, timeout, maxRetries, failureCode, taskName,
                DEFAULT_CALLBACK);
    }

    /**
     * Send a POST request with JSON payload, retry support, and pluggable status-code callback (sync).
     * 
     * @param httpClient httpClient
     * @param url url
     * @param payload payload
     * @param headers headers
     * @param timeout timeout
     * @param maxRetries maxRetries
     * @param failureCode failureCode
     * @param taskName taskName
     * @param callback callback
     * @return the result
     * @since 0.1.7
     */
    public static JsonNode postJsonWithRetry(HttpClient httpClient, String url, Map<String, Object> payload,
            Map<String, String> headers, Duration timeout, int maxRetries, StatusCode failureCode, String taskName,
            StatusCodeCallback callback) {
        for (int attempt = 1; attempt <= Math.max(1, maxRetries); attempt++) {
            try {
                String requestBody = MAPPER.writeValueAsString(payload);
                HttpRequest.Builder requestBuilder = HttpRequest.newBuilder().uri(URI.create(url)).timeout(timeout)
                        .POST(HttpRequest.BodyPublishers.ofString(requestBody));
                if (headers != null) {
                    headers.forEach(requestBuilder::header);
                }
                HttpResponse<String> response =
                    httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    return MAPPER.readTree(response.body());
                }
                boolean shouldRetry = callback != null && callback.apply(response.statusCode(), attempt);
                if (!shouldRetry || attempt >= maxRetries) {
                    throw RetrievalExceptions.error(failureCode,
                            taskName + " request failed with status " + response.statusCode());
                }
            } catch (IOException | InterruptedException ex) {
                if (ex instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                if (attempt >= maxRetries) {
                    throw RetrievalExceptions.error(failureCode,
                            taskName + " request failed, reason: " + ex.getMessage());
                }
            }
        }
        throw RetrievalExceptions.error(StatusCode.RETRIEVAL_RERANKER_UNREACHABLE_CALL_FAILED,
                taskName + " request reached unreachable state");
    }

    /**
     * Send a POST request with JSON payload asynchronously with retry support.
     * Corresponds to Python {@code async_request_with_retry}.
     * 
     * @param httpClient httpClient
     * @param url url
     * @param payload payload
     * @param headers headers
     * @param timeout timeout
     * @param maxRetries maxRetries
     * @param failureCode failureCode
     * @param taskName taskName
     * @return the result
     * @since 0.1.7
     */
    public static CompletableFuture<JsonNode> postJsonWithRetryAsync(HttpClient httpClient, String url,
            Map<String, Object> payload, Map<String, String> headers, Duration timeout, int maxRetries,
            StatusCode failureCode, String taskName) {
        return postJsonWithRetryAsync(httpClient, url, payload, headers, timeout, maxRetries, failureCode, taskName,
                DEFAULT_CALLBACK);
    }

    /**
     * Send a POST request with JSON payload asynchronously with retry and pluggable callback.
     * 
     * @param httpClient httpClient
     * @param url url
     * @param payload payload
     * @param headers headers
     * @param timeout timeout
     * @param maxRetries maxRetries
     * @param failureCode failureCode
     * @param taskName taskName
     * @param callback callback
     * @return the result
     * @since 0.1.7
     */
    public static CompletableFuture<JsonNode> postJsonWithRetryAsync(HttpClient httpClient, String url,
            Map<String, Object> payload, Map<String, String> headers, Duration timeout, int maxRetries,
            StatusCode failureCode, String taskName, StatusCodeCallback callback) {
        return CompletableFuture.supplyAsync(() -> postJsonWithRetry(httpClient, url, payload, headers, timeout,
                maxRetries, failureCode, taskName, callback));
    }
}

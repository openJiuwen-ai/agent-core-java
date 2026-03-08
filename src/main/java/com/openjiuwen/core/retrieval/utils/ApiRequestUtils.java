/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
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

/**
 * Shared HTTP request helper for retrieval services with retry support.
 */
public final class ApiRequestUtils {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ApiRequestUtils() {
    }

    public static JsonNode postJsonWithRetry(HttpClient httpClient,
                                             String url,
                                             Map<String, Object> payload,
                                             Map<String, String> headers,
                                             Duration timeout,
                                             int maxRetries,
                                             StatusCode failureCode,
                                             String taskName) {
        for (int attempt = 1; attempt <= Math.max(1, maxRetries); attempt++) {
            try {
                String requestBody = MAPPER.writeValueAsString(payload);
                HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .timeout(timeout)
                        .POST(HttpRequest.BodyPublishers.ofString(requestBody));
                if (headers != null) {
                    headers.forEach(requestBuilder::header);
                }
                HttpResponse<String> response = httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    return MAPPER.readTree(response.body());
                }
                if (attempt >= maxRetries) {
                    throw RetrievalExceptions.error(
                            failureCode,
                            taskName + " request failed with status " + response.statusCode());
                }
            } catch (IOException | InterruptedException ex) {
                if (ex instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                if (attempt >= maxRetries) {
                    throw RetrievalExceptions.error(
                            failureCode,
                            taskName + " request failed, reason: " + ex.getMessage());
                }
            }
        }
        throw RetrievalExceptions.error(
                StatusCode.RETRIEVAL_RERANKER_UNREACHABLE_CALL_FAILED,
                taskName + " request reached unreachable state");
    }
}

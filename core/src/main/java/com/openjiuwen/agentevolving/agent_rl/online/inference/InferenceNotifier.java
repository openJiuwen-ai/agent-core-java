/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.agent_rl.online.inference;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Notify a vLLM runtime to hot-load user LoRA adapters.
 *
 * <p>Mirrors Python's {@code InferenceNotifier} in
 * {@code openjiuwen/agent_evolving/agent_rl/online/inference/notifier.py}.</p>
 */
public class InferenceNotifier {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final String vllmBaseUrl;
    private final double timeout;
    private final boolean ownedClient;
    private final HttpClient httpClient;

    public InferenceNotifier(String vllmBaseUrl) {
        this(vllmBaseUrl, 120.0, null);
    }

    public InferenceNotifier(String vllmBaseUrl, double timeout, HttpClient httpClient) {
        this.vllmBaseUrl = vllmBaseUrl.replaceAll("/+$", "");
        this.timeout = timeout;
        this.ownedClient = httpClient == null;
        this.httpClient = httpClient != null
                ? httpClient
                : HttpClient.newBuilder().connectTimeout(Duration.ofSeconds((long) timeout)).build();
    }

    public CompletionStage<Void> close() {
        return CompletableFuture.completedFuture(null);
    }

    public CompletionStage<Void> notifyUpdate(String userId, String loraPath) {
        return postJson(
                "/v1/load_lora_adapter",
                Map.of(
                        "lora_name", userId,
                        "lora_path", loraPath,
                        "load_inplace", true
                ),
                "vLLM load_lora_adapter failed"
        );
    }

    public CompletionStage<Void> unload(String userId) {
        return postJson(
                "/v1/unload_lora_adapter",
                Map.of("lora_name", userId),
                "vLLM unload_lora_adapter failed"
        );
    }

    public String getVllmBaseUrl() {
        return vllmBaseUrl;
    }

    public double getTimeout() {
        return timeout;
    }

    public boolean isOwnedClient() {
        return ownedClient;
    }

    private CompletionStage<Void> postJson(String path, Map<String, Object> payload, String errorPrefix) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(vllmBaseUrl + path))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds((long) timeout))
                .POST(HttpRequest.BodyPublishers.ofString(toJson(payload)))
                .build();
        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenAccept(response -> {
                    if (response.statusCode() >= 400) {
                        String body = response.body() == null ? "" : response.body();
                        throw new RuntimeException(
                                errorPrefix + ": status=" + response.statusCode() + ", body=" + truncate(body)
                        );
                    }
                });
    }

    private static String toJson(Map<String, Object> payload) {
        try {
            return OBJECT_MAPPER.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Failed to serialize notifier payload", exception);
        }
    }

    private static String truncate(String value) {
        return value.length() <= 400 ? value : value.substring(0, 400);
    }
}

/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.lite;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * OpenAI-compatible embedding provider.
 * <p>
 * Mirrors Python's {@code OpenAICompatibleEmbeddingProvider} in
 * {@code openjiuwen/core/memory/lite/embeddings.py}.
 */
public class OpenAICompatibleEmbeddingProvider extends EmbeddingProvider {

    protected record EmbeddingHttpResponse(int statusCode, String body) {
    }

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String apiKey;
    private final String baseUrl;
    private volatile HttpClient client;

    public OpenAICompatibleEmbeddingProvider(String apiKey, String model, String baseUrl) {
        this.id = "openai_compatible";
        this.apiKey = apiKey;
        this.model = model;
        String normalizedBaseUrl = baseUrl;
        if (normalizedBaseUrl != null && normalizedBaseUrl.endsWith("/embeddings")) {
            normalizedBaseUrl = normalizedBaseUrl.substring(0, normalizedBaseUrl.length() - "/embeddings".length());
        }
        this.baseUrl = normalizedBaseUrl;
        this.dims = 1024;
    }

    protected HttpClient getClient() {
        if (client == null) {
            synchronized (this) {
                if (client == null) {
                    client = HttpClient.newBuilder()
                            .connectTimeout(Duration.ofSeconds(60))
                            .build();
                }
            }
        }
        return client;
    }

    @Override
    public CompletableFuture<List<Float>> embedQuery(String text) {
        return embedDocuments(List.of(text))
                .thenApply(embeddings -> embeddings.isEmpty() ? List.of() : embeddings.get(0));
    }

    @Override
    public CompletableFuture<List<List<Float>>> embedDocuments(List<String> texts) {
        if (apiKey == null || apiKey.isEmpty()) {
            return CompletableFuture.failedFuture(new IllegalArgumentException(
                    "Embedding API key not configured. "
                            + "Set EMBED_API_KEY environment variable or provide embedding_config parameter."
            ));
        }

        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("model", model);
        requestBody.put("input", texts);
        requestBody.put("encoding_format", "float");

        try {
            String body = MAPPER.writeValueAsString(requestBody);
            return sendEmbeddingsRequest(body).thenApply(response -> {
                if (response.statusCode() != 200) {
                    throw new RuntimeException("Embedding API failed: " + response.body());
                }
                return parseEmbeddings(response.body());
            });
        } catch (Exception exception) {
            return CompletableFuture.failedFuture(exception);
        }
    }

    protected CompletableFuture<EmbeddingHttpResponse> sendEmbeddingsRequest(String requestBody) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/embeddings"))
                .timeout(Duration.ofSeconds(60))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();
        return getClient()
                .sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> new EmbeddingHttpResponse(response.statusCode(), response.body()));
    }

    private List<List<Float>> parseEmbeddings(String responseBody) {
        try {
            JsonNode root = MAPPER.readTree(responseBody);
            List<JsonNode> items = new ArrayList<>();
            JsonNode dataNode = root.get("data");
            if (dataNode != null && dataNode.isArray()) {
                dataNode.forEach(items::add);
            }
            items.sort(Comparator.comparingInt(item -> item.path("index").asInt(0)));

            List<List<Float>> embeddings = new ArrayList<>();
            for (JsonNode item : items) {
                List<Float> embedding = new ArrayList<>();
                JsonNode embeddingNode = item.path("embedding");
                if (embeddingNode.isArray()) {
                    embeddingNode.forEach(value -> embedding.add((float) value.asDouble()));
                }
                embeddings.add(embedding);
            }

            if (!embeddings.isEmpty()) {
                dims = embeddings.get(0).size();
            }
            return embeddings;
        } catch (Exception exception) {
            throw new RuntimeException("Failed to parse embedding response", exception);
        }
    }
}

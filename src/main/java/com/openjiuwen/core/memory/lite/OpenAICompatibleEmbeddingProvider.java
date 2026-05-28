/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.lite;

import java.net.http.HttpClient;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.common.logging.Loggers;

/**
 * OpenAI-compatible embedding provider (supports DashScope, OpenAI, etc.).
 * <p>
 * Mirrors Python's {@code OpenAICompatibleEmbeddingProvider} from
 * {@code core/memory/lite/embeddings.py}.
 */
public class OpenAICompatibleEmbeddingProvider extends EmbeddingProvider {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String apiKey;
    private final String baseUrl;
    private volatile HttpClient client;

    public OpenAICompatibleEmbeddingProvider(String apiKey, String model, String baseUrl) {
        this.id = "openai_compatible";
        this.apiKey = apiKey;
        this.model = model;
        String url = baseUrl;
        if (url != null && url.endsWith("/embeddings")) {
            url = url.substring(0, url.lastIndexOf("/embeddings"));
        }
        this.baseUrl = url;
        this.dims = 1024;
    }

    private HttpClient getOrCreateClient() {
        if (client == null) {
            synchronized (this) {
                if (client == null) {
                    client = HttpClient.newBuilder()
                            .connectTimeout(java.time.Duration.ofSeconds(60))
                            .build();
                }
            }
        }
        return client;
    }

    @Override
    public CompletableFuture<List<Float>> embedQuery(String text) {
        return embedDocuments(List.of(text))
                .thenApply(embeddings -> embeddings.isEmpty() ? List.<Float>of() : embeddings.get(0));
    }

    @Override
    @SuppressWarnings("unchecked")
    public CompletableFuture<List<List<Float>>> embedDocuments(List<String> texts) {
        if (apiKey == null || apiKey.isEmpty()) {
            return CompletableFuture.failedFuture(new IllegalArgumentException(
                    "Embedding API key not configured. Set EMBED_API_KEY environment variable or provide embedding_config parameter."));
        }

        try {
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", model);
            requestBody.put("input", texts);
            if (dims > 0) {
                requestBody.put("dimensions", dims);
            }

            String jsonBody = MAPPER.writeValueAsString(requestBody);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/embeddings"))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            return getOrCreateClient().sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenApply(response -> {
                        try {
                            JsonNode root = MAPPER.readTree(response.body());
                            JsonNode dataNode = root.get("data");
                            List<List<Float>> result = new ArrayList<>();
                            if (dataNode != null && dataNode.isArray()) {
                                for (JsonNode item : dataNode) {
                                    JsonNode embeddingNode = item.get("embedding");
                                    List<Float> embedding = new ArrayList<>();
                                    if (embeddingNode != null && embeddingNode.isArray()) {
                                        for (JsonNode val : embeddingNode) {
                                            embedding.add((float) val.asDouble());
                                        }
                                    }
                                    result.add(embedding);
                                }
                            }
                            return result;
                        } catch (Exception e) {
                            throw new RuntimeException("Failed to parse embedding response", e);
                        }
                    });
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }
}

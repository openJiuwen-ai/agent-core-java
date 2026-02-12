/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */

package com.openjiuwen.core.memory.store.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.common.exception.ErrorBuilder;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.common.logging.LoggerProtocol;
import com.openjiuwen.core.retrieval.common.SearchResult;
import com.openjiuwen.core.retrieval.vectorstore.VectorStore;
import okhttp3.*;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * ChromaDB vector store implementation using HTTP REST API.
 * Provides vector storage and search capabilities using ChromaDB.
 */
public class MemoryChromaVectorStore implements VectorStore {

    private static final LoggerProtocol logger = Loggers.MEMORY;
    private static final MediaType JSON = MediaType.parse("application/json");
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final String baseUrl;
    private final Map<String, String> collectionCache;
    private OkHttpClient httpClient;

    /**
     * Create a new MemoryChromaVectorStore.
     *
     * @param baseUrl ChromaDB server base URL (e.g., "http://localhost:8000")
     */
    public MemoryChromaVectorStore(String baseUrl) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.collectionCache = new ConcurrentHashMap<>();
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
    }

    /**
     * Create client (not implemented for MemoryChromaVectorStore).
     * This method is kept for API compatibility with Python version.
     *
     * @param databaseName database name
     * @param pathOrUri path or URI
     * @param token optional token
     * @return null (not implemented)
     */
    public static Object createClient(String databaseName, String pathOrUri, String token) {
        logger.error("create_client not implemented in MemoryChromaVectorStore");
        return null;
    }

    // Getters for testing
    public String getBaseUrl() {
        return baseUrl;
    }

    public Map<String, String> getCollectionCache() {
        return collectionCache;
    }

    /**
     * Get the HTTP client.
     * Can be overridden for testing.
     *
     * @return OkHttpClient instance
     */
    public OkHttpClient getHttpClient() {
        return httpClient;
    }

    /**
     * Check if table name is valid.
     *
     * @param tableName table name to check
     * @param operation operation name for error message
     * @throws RuntimeException if table name is invalid
     */
    public void checkTableName(String tableName, String operation) {
        if (tableName == null || tableName.trim().isEmpty()) {
            throw ErrorBuilder.build(StatusCode.MEMORY_STORE_VALIDATION_INVALID,
                    "chroma collection name is required for " + operation);
        }
    }

    /**
     * Remove collection from cache.
     *
     * @param tableName collection name to remove
     */
    public void removeCollectionFromCache(String tableName) {
        collectionCache.remove(tableName);
    }

    /**
     * Get or create a collection by name.
     *
     * @param tableName collection name
     * @return CompletableFuture containing collection ID
     */
    public CompletableFuture<String> getOrCreateCollection(String tableName) {
        return CompletableFuture.supplyAsync(() -> {
            if (collectionCache.containsKey(tableName)) {
                return collectionCache.get(tableName);
            }

            try {
                // Try to get or create the collection
                Map<String, Object> body = new HashMap<>();
                body.put("name", tableName);
                body.put("metadata", Map.of("hnsw:space", "ip"));

                Request request = new Request.Builder()
                        .url(baseUrl + "/api/v1/collections")
                        .post(RequestBody.create(objectMapper.writeValueAsString(body), JSON))
                        .build();

                try (Response response = httpClient.newCall(request).execute()) {
                    if (response.isSuccessful() && response.body() != null) {
                        Map<String, Object> result = objectMapper.readValue(
                                response.body().string(),
                                new TypeReference<>() {});
                        String collectionId = (String) result.get("id");
                        collectionCache.put(tableName, collectionId);
                        return collectionId;
                    } else if (response.code() == 409) {
                        // Collection already exists, get it
                        return getCollectionByName(tableName);
                    } else {
                        throw new RuntimeException("Failed to create collection: " + response.message());
                    }
                }
            } catch (IOException e) {
                throw new RuntimeException("Failed to get or create collection", e);
            }
        });
    }

    private String getCollectionByName(String tableName) throws IOException {
        Request request = new Request.Builder()
                .url(baseUrl + "/api/v1/collections/" + tableName)
                .get()
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (response.isSuccessful() && response.body() != null) {
                Map<String, Object> result = objectMapper.readValue(
                        response.body().string(),
                        new TypeReference<>() {});
                String collectionId = (String) result.get("id");
                collectionCache.put(tableName, collectionId);
                return collectionId;
            }
            throw new RuntimeException("Collection not found: " + tableName);
        }
    }

    @Override
    public CompletableFuture<Boolean> tableExists(String tableName) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Request request = new Request.Builder()
                        .url(baseUrl + "/api/v1/collections/" + tableName)
                        .get()
                        .build();

                try (Response response = httpClient.newCall(request).execute()) {
                    return response.isSuccessful();
                }
            } catch (IOException e) {
                return false;
            }
        });
    }

    @Override
    public CompletableFuture<Void> add(List<Map<String, Object>> data, Integer batchSize) {
        // table_name is required for ChromaDB operations
        // This method should not be called directly, use add(data, tableName) instead
        checkTableName(null, "add");
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> add(List<Map<String, Object>> data, String tableName) {
        checkTableName(tableName, "add");
        int actualBatchSize = 128;
        return add(data, actualBatchSize, tableName);
    }

    /**
     * Add vectors with specified batch size.
     *
     * @param data list of records with "id" and "embedding" fields
     * @param batchSize batch size for insertion
     * @param tableName target collection name
     * @return CompletableFuture that completes when insertion is done
     */
    public CompletableFuture<Void> add(List<Map<String, Object>> data, int batchSize, String tableName) {
        checkTableName(tableName, "add");

        return getOrCreateCollection(tableName).thenAcceptAsync(collectionId -> {
            try {
                // Process in batches
                for (int i = 0; i < data.size(); i += batchSize) {
                    List<Map<String, Object>> batch = data.subList(i, Math.min(i + batchSize, data.size()));
                    
                    List<String> ids = new ArrayList<>();
                    List<List<Double>> embeddings = new ArrayList<>();
                    
                    for (Map<String, Object> item : batch) {
                        ids.add((String) item.get("id"));
                        @SuppressWarnings("unchecked")
                        List<? extends Number> embedding = (List<? extends Number>) item.get("embedding");
                        List<Double> doubleEmbedding = new ArrayList<>();
                        for (Number n : embedding) {
                            doubleEmbedding.add(n.doubleValue());
                        }
                        embeddings.add(doubleEmbedding);
                    }

                    Map<String, Object> body = new HashMap<>();
                    body.put("ids", ids);
                    body.put("embeddings", embeddings);

                    Request request = new Request.Builder()
                            .url(baseUrl + "/api/v1/collections/" + collectionId + "/add")
                            .post(RequestBody.create(objectMapper.writeValueAsString(body), JSON))
                            .build();

                    try (Response response = httpClient.newCall(request).execute()) {
                        if (!response.isSuccessful()) {
                            throw new RuntimeException("Failed to add vectors: " + response.message());
                        }
                    }
                }
            } catch (IOException e) {
                throw new RuntimeException("Failed to add vectors", e);
            }
        });
    }

    @Override
    public CompletableFuture<List<SearchResult>> search(List<Double> queryVector, int topK, Map<String, Object> filters) {
        // table_name is required for ChromaDB operations
        // This method should not be called directly, use search(queryVector, topK, tableName) instead
        checkTableName(null, "search");
        return CompletableFuture.completedFuture(Collections.emptyList());
    }

    @Override
    public CompletableFuture<List<SearchResult>> search(List<Double> queryVector, int topK, String tableName) {
        checkTableName(tableName, "search");

        return getOrCreateCollection(tableName).thenApplyAsync(collectionId -> {
            try {
                Map<String, Object> body = new HashMap<>();
                body.put("query_embeddings", List.of(queryVector));
                body.put("n_results", topK);

                Request request = new Request.Builder()
                        .url(baseUrl + "/api/v1/collections/" + collectionId + "/query")
                        .post(RequestBody.create(objectMapper.writeValueAsString(body), JSON))
                        .build();

                try (Response response = httpClient.newCall(request).execute()) {
                    if (!response.isSuccessful() || response.body() == null) {
                        return Collections.<SearchResult>emptyList();
                    }

                    Map<String, Object> result = objectMapper.readValue(
                            response.body().string(),
                            new TypeReference<>() {});

                    @SuppressWarnings("unchecked")
                    List<List<String>> ids = (List<List<String>>) result.get("ids");
                    @SuppressWarnings("unchecked")
                    List<List<Double>> distances = (List<List<Double>>) result.get("distances");

                    List<SearchResult> searchResults = new ArrayList<>();
                    if (ids != null && !ids.isEmpty() && ids.get(0) != null && !ids.get(0).isEmpty()) {
                        List<String> resultIds = ids.get(0);
                        List<Double> resultDistances = distances != null && !distances.isEmpty() 
                                ? distances.get(0) 
                                : Collections.nCopies(resultIds.size(), 1.0);

                        for (int i = 0; i < resultIds.size(); i++) {
                            double distance = resultDistances.get(i);
                            double score = 1 - distance; // Convert distance to score (higher is better)
                            searchResults.add(SearchResult.builder()
                                    .id(resultIds.get(i))
                                    .text("")
                                    .score(score)
                                    .build());
                        }
                    }

                    return searchResults;
                }
            } catch (IOException e) {
                logger.error("Search failed", e);
                return Collections.emptyList();
            }
        });
    }

    @Override
    public CompletableFuture<Boolean> delete(List<String> ids, String filterExpr) {
        // table_name is required for ChromaDB operations
        // This method should not be called directly, use deleteFromTable(ids, tableName) instead
        checkTableName(null, "delete");
        return CompletableFuture.completedFuture(true);
    }

    @Override
    public CompletableFuture<Boolean> deleteFromTable(List<String> ids, String tableName) {
        checkTableName(tableName, "delete");

        return tableExists(tableName).thenComposeAsync(exists -> {
            if (!exists) {
                logger.debug("Chroma Collection {} does not exist, skip delete vector", tableName);
                return CompletableFuture.completedFuture(true);
            }
            
            // Check ids after table existence check (matching Python behavior)
            if (ids == null || ids.isEmpty()) {
                logger.debug("ids is {}, skip delete vector", ids);
                return CompletableFuture.completedFuture(true);
            }

            return getOrCreateCollection(tableName).thenApplyAsync(collectionId -> {
                try {
                    Map<String, Object> body = Map.of("ids", ids);

                    Request request = new Request.Builder()
                            .url(baseUrl + "/api/v1/collections/" + collectionId + "/delete")
                            .post(RequestBody.create(objectMapper.writeValueAsString(body), JSON))
                            .build();

                    try (Response response = httpClient.newCall(request).execute()) {
                        return response.isSuccessful();
                    }
                } catch (IOException e) {
                    logger.error("Delete failed", e);
                    return false;
                }
            });
        });
    }

    @Override
    public CompletableFuture<Boolean> deleteTable(String tableName) {
        return tableExists(tableName).thenComposeAsync(exists -> {
            if (!exists) {
                logger.debug("Chroma Collection {} does not exist, skip delete collection", tableName);
                return CompletableFuture.completedFuture(true);
            }

            return CompletableFuture.supplyAsync(() -> {
                try {
                    Request request = new Request.Builder()
                            .url(baseUrl + "/api/v1/collections/" + tableName)
                            .delete()
                            .build();

                    try (Response response = httpClient.newCall(request).execute()) {
                        if (response.isSuccessful()) {
                            removeCollectionFromCache(tableName);
                            return true;
                        }
                        return false;
                    }
                } catch (IOException e) {
                    logger.error("Delete table failed", e);
                    return false;
                }
            });
        });
    }

    @Override
    public CompletableFuture<List<SearchResult>> sparseSearch(String queryText, int topK, Map<String, Object> filters) {
        // Not implemented for ChromaDB
        return CompletableFuture.completedFuture(Collections.emptyList());
    }

    @Override
    public CompletableFuture<List<SearchResult>> hybridSearch(String queryText, List<Double> queryVector,
                                                               int topK, double alpha, Map<String, Object> filters) {
        // Not implemented for ChromaDB
        return CompletableFuture.completedFuture(Collections.emptyList());
    }

    @Override
    public void checkVectorField() {
        logger.error("check_vector_field not implemented in MemoryChromaVectorStore");
    }
}


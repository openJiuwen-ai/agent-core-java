/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */

package com.openjiuwen.core.memory.manage.memmodel;

import com.openjiuwen.core.common.exception.ErrorBuilder;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.common.logging.LoggerProtocol;
import com.openjiuwen.core.common.utils.Pair;
import com.openjiuwen.core.retrieval.common.SearchResult;
import com.openjiuwen.core.retrieval.embedding.Embedding;
import com.openjiuwen.core.retrieval.vectorstore.VectorStore;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Concrete implementation of semantic storage that uses an embedding model and vector store.
 * <p>
 * This class provides an implementation of semantic storage that uses an embedding model
 * to generate vector representations of text documents and a vector store to store and
 * search these embeddings efficiently.
 * <p>
 * Corresponds to Python: manage/mem_model/semantic_store.py
 */
public class SemanticStore {

    private static final LoggerProtocol logger = Loggers.MEMORY;

    private Embedding embeddingModel;
    private final VectorStore vectorStore;

    /**
     * Initialize the semantic store with an embedding model and vector store.
     *
     * @param vectorStore    The vector store to use for storing and searching embeddings.
     * @param embeddingModel Optional embedding model to use for generating embeddings.
     *                       If not provided, must be initialized later using initializeEmbeddingModel.
     */
    public SemanticStore(VectorStore vectorStore, Embedding embeddingModel) {
        this.vectorStore = vectorStore;
        this.embeddingModel = embeddingModel;
    }

    /**
     * Initialize or update the embedding model used by the semantic store.
     *
     * @param embeddingModel The embedding model to use for generating text embeddings.
     */
    public void initializeEmbeddingModel(Embedding embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    /**
     * Get the current embedding model.
     *
     * @return the embedding model or null if not initialized
     */
    public Embedding getEmbeddingModel() {
        return embeddingModel;
    }

    /**
     * Add documents to a specified table after generating their embeddings.
     *
     * @param docs      A list of (id, text) pairs where id is a unique identifier
     *                  and text is the raw string to be embedded.
     * @param tableName The name of the table where the embeddings will be stored.
     * @param scopeId   Optional scope identifier to associate with the documents.
     * @return CompletableFuture containing true if successful, false otherwise
     */
    public CompletableFuture<Boolean> addDocs(List<Pair<String, String>> docs, String tableName, String scopeId) {
        if (embeddingModel == null) {
            logger.error("Embedding model not initialized, please call initializeEmbeddingModel first.");
            return CompletableFuture.completedFuture(false);
        }

        try {
            // Extract IDs and texts from docs
            List<String> memoryIds = new ArrayList<>();
            List<String> texts = new ArrayList<>();
            for (Pair<String, String> doc : docs) {
                memoryIds.add(doc.getKey());
                texts.add(doc.getValue());
            }

            // Generate embeddings for the texts
            return embeddingModel.embedDocuments(texts)
                .thenCompose(embeddings -> {
                    try {
                        if (memoryIds.size() != embeddings.size()) {
                            throw ErrorBuilder.build(
                                StatusCode.MEMORY_STORE_VALIDATION_INVALID,
                                "memory_ids and embeddings must have same length"
                            );
                        }

                        // Prepare data for vector store
                        List<Map<String, Object>> data = new ArrayList<>();
                        for (int i = 0; i < memoryIds.size(); i++) {
                            Map<String, Object> record = new HashMap<>();
                            record.put("id", memoryIds.get(i));
                            record.put("embedding", embeddings.get(i));
                            data.add(record);
                        }

                        // Add to vector store
                        return vectorStore.add(data, tableName)
                            .thenApply(v -> true);
                    } catch (Exception e) {
                        logger.error("Failed to add documents to semantic store: {}", e.getMessage());
                        return CompletableFuture.completedFuture(false);
                    }
                })
                .exceptionally(e -> {
                    logger.error("Failed to add documents to semantic store: {}", e.getMessage());
                    return false;
                });
        } catch (Exception e) {
            logger.error("Failed to add documents to semantic store: {}", e.getMessage());
            return CompletableFuture.completedFuture(false);
        }
    }

    /**
     * Delete documents from a specified table by their unique identifiers.
     *
     * @param ids       A list of unique document ids whose embeddings should be removed.
     * @param tableName The name of the table from which to delete embeddings.
     * @return CompletableFuture containing true if successful, false otherwise
     */
    public CompletableFuture<Boolean> deleteDocs(List<String> ids, String tableName) {
        try {
            return vectorStore.deleteFromTable(ids, tableName)
                .exceptionally(e -> {
                    logger.error("Failed to delete documents from semantic store: {}", e.getMessage());
                    return false;
                });
        } catch (Exception e) {
            logger.error("Failed to delete documents from semantic store: {}", e.getMessage());
            return CompletableFuture.completedFuture(false);
        }
    }

    /**
     * Search for the top-k most similar documents to a query string.
     *
     * @param query     The raw query string to embed and search for.
     * @param tableName The name of the table to search within.
     * @param scopeId   Optional scope identifier to filter results.
     * @param topK      The number of most similar results to return.
     * @return CompletableFuture containing list of (id, score) pairs
     */
    public CompletableFuture<List<Pair<String, Double>>> search(String query, String tableName, String scopeId, int topK) {
        if (embeddingModel == null) {
            logger.error("Embedding model not initialized, please call initializeEmbeddingModel first.");
            return CompletableFuture.completedFuture(Collections.emptyList());
        }

        try {
            // Generate embedding for the query
            return embeddingModel.embedDocuments(List.of(query))
                .thenCompose(queryEmbeddings -> {
                    if (queryEmbeddings.isEmpty()) {
                        logger.error("Failed to embed query: {}", query);
                        return CompletableFuture.completedFuture(Collections.<Pair<String, Double>>emptyList());
                    }
                    List<Double> queryEmbedding = queryEmbeddings.get(0);

                    // Search in vector store
                    return vectorStore.search(queryEmbedding, topK, tableName)
                        .thenApply(results -> {
                            // Convert to required format
                            List<Pair<String, Double>> pairs = new ArrayList<>();
                            for (SearchResult result : results) {
                                pairs.add(new Pair<>(result.getId(), result.getScore()));
                            }
                            return pairs;
                        });
                })
                .exceptionally(e -> {
                    logger.error("Failed to search semantic store: {}", e.getMessage());
                    return Collections.emptyList();
                });
        } catch (Exception e) {
            logger.error("Failed to search semantic store: {}", e.getMessage());
            return CompletableFuture.completedFuture(Collections.emptyList());
        }
    }

    /**
     * Delete an entire table and all its stored embeddings.
     *
     * @param tableName The name of the table to delete.
     * @return CompletableFuture containing true if successful, false otherwise
     */
    public CompletableFuture<Boolean> deleteTable(String tableName) {
        try {
            return vectorStore.deleteTable(tableName)
                .exceptionally(e -> {
                    logger.error("Failed to delete table from semantic store: {}", e.getMessage());
                    return false;
                });
        } catch (Exception e) {
            logger.error("Failed to delete table from semantic store: {}", e.getMessage());
            return CompletableFuture.completedFuture(false);
        }
    }
}


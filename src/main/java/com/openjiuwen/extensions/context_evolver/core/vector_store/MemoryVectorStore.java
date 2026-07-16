/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.context_evolver.core.vector_store;

import com.openjiuwen.extensions.context_evolver.core.schema.VectorNode;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Mirrors Python's
 * {@code openjiuwen.extensions.context_evolver.core.vector_store.memory_vector_store.MemoryVectorStore}.
 * Simple in-memory vector store using cosine similarity for search.
 * 
 * @since 0.1.7
 */
public class MemoryVectorStore {
    private static final Logger log = LoggerFactory.getLogger(MemoryVectorStore.class);

    private final Map<String, VectorNode> vectors;

    /**
     * MemoryVectorStore.
     * 
     * @since 0.1.7
     */
    public MemoryVectorStore() {
        this.vectors = new ConcurrentHashMap<>();
        log.info("Initialized MemoryVectorStore");
    }

    /**
     * Insert or update a vector node.
     * 
     * @param node VectorNode to store
     * @return the result
     * @since 0.1.7
     */
    public CompletableFuture<Void> asyncUpsert(VectorNode node) {
        if (node.getEmbedding() == null) {
            return CompletableFuture
                    .failedFuture(new IllegalArgumentException("Node " + node.getId() + " has no embedding"));
        }

        vectors.put(node.getId(), node);
        log.debug("Upserted vector: {}", node.getId());
        return CompletableFuture.completedFuture(null);
    }

    /**
     * Search for similar vectors.
     * 
     * @param embedding query embedding
     * @param topK number of results to return
     * @param metadataFilter optional metadata filter
     * @return list of most similar VectorNodes
     * @since 0.1.7
     */
    public CompletableFuture<List<VectorNode>> asyncSearch(List<Double> embedding, int topK,
            Map<String, Object> metadataFilter) {
        if (vectors.isEmpty()) {
            log.debug("Vector store is empty");
            return CompletableFuture.completedFuture(Collections.emptyList());
        }

        // Filter vectors by metadata if provided
        List<VectorNode> candidates = new ArrayList<>(vectors.values());
        if (metadataFilter != null && !metadataFilter.isEmpty()) {
            candidates = candidates.stream().filter(v -> matchesFilter(v, metadataFilter)).collect(Collectors.toList());
        }

        if (candidates.isEmpty()) {
            log.debug("No vectors match filter");
            return CompletableFuture.completedFuture(Collections.emptyList());
        }

        // Calculate cosine similarity
        double[] queryVec = toDoubleArray(embedding);
        double queryNorm = norm(queryVec);

        List<Map.Entry<Double, VectorNode>> similarities = new ArrayList<>();
        for (VectorNode node : candidates) {
            if (node.getEmbedding() == null) {
                continue;
            }

            double[] vec = toDoubleArray(node.getEmbedding());
            double vecNorm = norm(vec);

            double similarity;
            if (vecNorm == 0 || queryNorm == 0) {
                similarity = 0.0;
            } else {
                similarity = dotProduct(queryVec, vec) / (queryNorm * vecNorm);
            }

            similarities.add(new AbstractMap.SimpleEntry<>(similarity, node));
        }

        // Sort by similarity (highest first)
        similarities.sort((a, b) -> Double.compare(b.getKey(), a.getKey()));

        // Return topK results
        List<VectorNode> results =
            similarities.stream().limit(topK).map(Map.Entry::getValue).collect(Collectors.toList());

        log.debug("Found {} similar vectors", results.size());
        return CompletableFuture.completedFuture(results);
    }

    /**
     * Delete a vector node.
     * 
     * @param nodeId ID of node to delete
     * @return true if deleted, false if not found
     * @since 0.1.7
     */
    public CompletableFuture<Boolean> asyncDelete(String nodeId) {
        if (vectors.containsKey(nodeId)) {
            vectors.remove(nodeId);
            log.debug("Deleted vector: {}", nodeId);
            return CompletableFuture.completedFuture(true);
        }
        return CompletableFuture.completedFuture(false);
    }

    /**
     * Clear all vectors.
     * 
     * @since 0.1.7
     */
    public void clear() {
        vectors.clear();
        log.info("Cleared all vectors");
    }

    /**
     * Get number of stored vectors.
     * 
     * @return the result
     * @since 0.1.7
     */
    public int count() {
        return vectors.size();
    }

    /**
     * Get all stored vectors, optionally filtered by metadata.
     * 
     * @param metadataFilter metadataFilter
     * @return the result
     * @since 0.1.7
     */
    public List<VectorNode> getAll(Map<String, Object> metadataFilter) {
        if (vectors.isEmpty()) {
            return Collections.emptyList();
        }

        if (metadataFilter != null && !metadataFilter.isEmpty()) {
            return vectors.values().stream().filter(v -> matchesFilter(v, metadataFilter)).collect(Collectors.toList());
        }

        return new ArrayList<>(vectors.values());
    }

    /**
     * Get all stored vectors without filter.
     * 
     * @return the result
     * @since 0.1.7
     */
    public List<VectorNode> getAll() {
        return getAll(null);
    }

    /**
     * Load a single vector node directly (for deserialization).
     * 
     * @param nodeId nodeId
     * @param node node
     * @since 0.1.7
     */
    public void loadNode(String nodeId, VectorNode node) {
        vectors.put(nodeId, node);
    }

    /**
     * loadFromDict.
     * 
     * @param data data
     * @return the result
     * @since 0.1.7
     */
    @SuppressWarnings("unchecked")
    public CompletableFuture<Void> loadFromDict(Map<String, Map<String, Object>> data) {
        for (Map.Entry<String, Map<String, Object>> entry : data.entrySet()) {
            String nodeId = entry.getKey();
            Map<String, Object> nodeData = entry.getValue();
            VectorNode node = VectorNode.fromDict(nodeData);
            if (node.getEmbedding() != null) {
                vectors.put(nodeId, node);
                log.debug("Loaded vector: {}", nodeId);
            }
        }
        return CompletableFuture.completedFuture(null);
    }

    /**
     * matchesFilter.
     * 
     * @param node node
     * @param filter filter
     * @return the result
     * @since 0.1.7
     */
    private boolean matchesFilter(VectorNode node, Map<String, Object> filter) {
        Map<String, Object> metadata = node.getMetadata();
        for (Map.Entry<String, Object> entry : filter.entrySet()) {
            Object value = metadata.get(entry.getKey());
            if (!Objects.equals(value, entry.getValue())) {
                return false;
            }
        }
        return true;
    }

    /**
     * toDoubleArray.
     * 
     * @param list list
     * @return the result
     * @since 0.1.7
     */
    private double[] toDoubleArray(List<Double> list) {
        double[] arr = new double[list.size()];
        for (int i = 0; i < list.size(); i++) {
            arr[i] = list.get(i);
        }
        return arr;
    }

    /**
     * dotProduct.
     * 
     * @param a a
     * @param b b
     * @return the result
     * @since 0.1.7
     */
    private double dotProduct(double[] a, double[] b) {
        double sum = 0;
        for (int i = 0; i < Math.min(a.length, b.length); i++) {
            sum += a[i] * b[i];
        }
        return sum;
    }

    /**
     * norm.
     * 
     * @param v v
     * @return the result
     * @since 0.1.7
     */
    private double norm(double[] v) {
        double sum = 0;
        for (double d : v) {
            sum += d * d;
        }
        return Math.sqrt(sum);
    }

    /**
     * toString.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Override
    public String toString() {
        return "MemoryVectorStore(count=" + vectors.size() + ")";
    }
}

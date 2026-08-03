/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.context_evolver.core.vector_store;

import com.openjiuwen.core.common.logging.LoggerProtocol;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.extensions.context_evolver.core.schema.VectorNode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * Mirrors Python's {@code MemoryVectorStore} in
 * {@code openjiuwen/extensions/context_evolver/core/vector_store/memory_vector_store.py}.
 */
public class MemoryVectorStore {

    private static final LoggerProtocol LOGGER = Loggers.CONTEXT_ENGINE;

    private final Map<String, VectorNode> vectors = new LinkedHashMap<>();

    public MemoryVectorStore() {
        LOGGER.info("Initialized MemoryVectorStore");
    }

    public CompletableFuture<Void> asyncUpsert(VectorNode node) {
        if (node.getEmbedding() == null) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Node " + node.getId() + " has no embedding"));
        }
        vectors.put(node.getId(), node);
        LOGGER.debug("Upserted vector: %s", node.getId());
        return CompletableFuture.completedFuture(null);
    }

    public CompletableFuture<List<VectorNode>> asyncSearch(
            List<Double> embedding,
            int topK,
            Map<String, Object> metadataFilter
    ) {
        if (vectors.isEmpty()) {
            LOGGER.debug("Vector store is empty");
            return CompletableFuture.completedFuture(List.of());
        }

        List<VectorNode> candidates = new ArrayList<>(vectors.values());
        if (metadataFilter != null && !metadataFilter.isEmpty()) {
            candidates = candidates.stream()
                    .filter(node -> metadataMatches(node, metadataFilter))
                    .toList();
        }
        if (candidates.isEmpty()) {
            LOGGER.debug("No vectors match filter");
            return CompletableFuture.completedFuture(List.of());
        }

        double queryNorm = norm(embedding);
        List<ScoredNode> scored = new ArrayList<>();
        for (VectorNode candidate : candidates) {
            if (candidate.getEmbedding() == null) {
                continue;
            }
            double candidateNorm = norm(candidate.getEmbedding());
            double similarity = 0.0d;
            if (queryNorm != 0.0d && candidateNorm != 0.0d) {
                similarity = dot(embedding, candidate.getEmbedding()) / (queryNorm * candidateNorm);
            }
            scored.add(new ScoredNode(similarity, candidate));
        }

        scored.sort(Comparator.comparingDouble(ScoredNode::score).reversed());
        List<VectorNode> results = scored.stream()
                .limit(topK)
                .map(ScoredNode::node)
                .toList();
        LOGGER.debug("Found %s similar vectors", results.size());
        return CompletableFuture.completedFuture(results);
    }

    public CompletableFuture<List<VectorNode>> asyncSearch(List<Double> embedding, int topK) {
        return asyncSearch(embedding, topK, null);
    }

    public CompletableFuture<Boolean> asyncDelete(String nodeId) {
        if (vectors.containsKey(nodeId)) {
            vectors.remove(nodeId);
            LOGGER.debug("Deleted vector: %s", nodeId);
            return CompletableFuture.completedFuture(true);
        }
        return CompletableFuture.completedFuture(false);
    }

    public void clear() {
        vectors.clear();
        LOGGER.info("Cleared all vectors");
    }

    public int count() {
        return vectors.size();
    }

    public List<VectorNode> getAll(Map<String, Object> metadataFilter) {
        if (vectors.isEmpty()) {
            return List.of();
        }
        if (metadataFilter == null || metadataFilter.isEmpty()) {
            return new ArrayList<>(vectors.values());
        }
        return vectors.values().stream()
                .filter(node -> metadataMatches(node, metadataFilter))
                .toList();
    }

    public List<VectorNode> getAll() {
        return getAll(null);
    }

    public void loadNode(String nodeId, VectorNode node) {
        vectors.put(nodeId, node);
    }

    @SuppressWarnings("unchecked")
    public CompletableFuture<Void> loadFromDict(Map<String, Map<String, Object>> data) {
        for (Map.Entry<String, Map<String, Object>> entry : data.entrySet()) {
            VectorNode node = VectorNode.fromDict(entry.getValue());
            if (node.getEmbedding() != null) {
                vectors.put(entry.getKey(), node);
                LOGGER.debug("Loaded vector: %s", entry.getKey());
            }
        }
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public String toString() {
        return "MemoryVectorStore(count=" + vectors.size() + ")";
    }

    private static boolean metadataMatches(VectorNode node, Map<String, Object> metadataFilter) {
        if (node.getMetadata() == null) {
            return false;
        }
        for (Map.Entry<String, Object> entry : metadataFilter.entrySet()) {
            if (!Objects.equals(node.getMetadata().get(entry.getKey()), entry.getValue())) {
                return false;
            }
        }
        return true;
    }

    private static double dot(List<Double> left, List<Double> right) {
        int size = Math.min(left.size(), right.size());
        double sum = 0.0d;
        for (int index = 0; index < size; index++) {
            sum += left.get(index) * right.get(index);
        }
        return sum;
    }

    private static double norm(List<Double> vector) {
        double sum = 0.0d;
        for (Double value : vector) {
            if (value != null) {
                sum += value * value;
            }
        }
        return Math.sqrt(sum);
    }

    private record ScoredNode(double score, VectorNode node) {
    }
}

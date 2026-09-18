/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.context_evolver.summary.task.reme;

import com.openjiuwen.core.common.logging.LoggerProtocol;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.foundation.store.Embedding;
import com.openjiuwen.extensions.context_evolver.core.context.RuntimeContext;
import com.openjiuwen.extensions.context_evolver.core.op.BaseOp;
import com.openjiuwen.extensions.context_evolver.core.schema.VectorNode;
import com.openjiuwen.extensions.context_evolver.core.vector_store.MemoryVectorStore;
import com.openjiuwen.extensions.context_evolver.schema.ReMeMemory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Deduplicate ReMe memories by embedding similarity.
 * <p>
 * Mirrors Python's {@code MemoryDeduplicationOp} in
 * {@code openjiuwen/extensions/context_evolver/summary/task/reme/update.py}.
 * </p>
 */
public class MemoryDeduplicationOp extends BaseOp {

    private static final LoggerProtocol LOGGER = Loggers.CONTEXT_ENGINE;
    private static final int DUMMY_EMBEDDING_SIZE = 2560;

    private final boolean useDeduplication;
    private final double similarityThreshold;
    private final Map<Integer, List<Double>> batchEmbeddingsCache = new LinkedHashMap<>();

    public MemoryDeduplicationOp() {
        this(true, 0.5d);
    }

    public MemoryDeduplicationOp(boolean useDeduplication, double similarityThreshold) {
        super(Map.of("use_deduplication", useDeduplication, "similarity_threshold", similarityThreshold));
        this.useDeduplication = useDeduplication;
        this.similarityThreshold = similarityThreshold;
    }

    @Override
    public CompletableFuture<Void> asyncExecute(RuntimeContext context) {
        List<ReMeMemory> memories = MemoryValidationOp.memories(context.get("validated_memories", List.of()));
        String workspaceId = String.valueOf(context.get("user_id", "default"));
        if (memories.isEmpty()) {
            LOGGER.info("No memories to deduplicate");
            context.set("deduplicated_memories", List.of());
            context.set("duplicate_count", 0);
            return CompletableFuture.completedFuture(null);
        }
        if (!useDeduplication) {
            context.set("deduplicated_memories", memories);
            context.set("duplicate_count", 0);
            return CompletableFuture.completedFuture(null);
        }
        LOGGER.info("Starting deduplication for %s memories", memories.size());
        return existingMemoryEmbeddings(workspaceId)
                .thenCompose(existingEmbeddings -> deduplicate(memories, existingEmbeddings))
                .thenAccept(result -> {
                    context.set("deduplicated_memories", result.uniqueMemories());
                    context.set("duplicate_count", result.duplicateCount());
                    LOGGER.info(
                            "Deduplicated %s memories to %s (removed %s duplicates)",
                            memories.size(),
                            result.uniqueMemories().size(),
                            result.duplicateCount()
                    );
                });
    }

    private CompletableFuture<List<List<Double>>> existingMemoryEmbeddings(String workspaceId) {
        Object vectorStoreObject = getVectorStore();
        if (!(vectorStoreObject instanceof MemoryVectorStore vectorStore) || workspaceId.isEmpty()) {
            return CompletableFuture.completedFuture(List.of());
        }
        Map<String, Object> metadataFilter = new LinkedHashMap<>();
        metadataFilter.put("workspace_id", workspaceId);
        metadataFilter.put("type", "reme_memory");
        return vectorStore.asyncSearch(Collections.nCopies(DUMMY_EMBEDDING_SIZE, 0.0d), 1000, metadataFilter)
                .handle((nodes, error) -> {
                    if (error != null) {
                        LOGGER.warning("Failed to retrieve existing memory embeddings: %s", error);
                        return List.of();
                    }
                    List<List<Double>> embeddings = new ArrayList<>();
                    for (VectorNode node : nodes) {
                        if (node.getEmbedding() != null && !node.getEmbedding().isEmpty()) {
                            embeddings.add(node.getEmbedding());
                        }
                    }
                    LOGGER.debug("Retrieved %s existing memory embeddings", embeddings.size());
                    return embeddings;
                });
    }

    private CompletableFuture<DeduplicationResult> deduplicate(List<ReMeMemory> memories,
                                                               List<List<Double>> existingEmbeddings) {
        List<ReMeMemory> uniqueMemories = new ArrayList<>();
        int[] duplicateCount = {0};
        CompletableFuture<Void> future = CompletableFuture.completedFuture(null);
        for (ReMeMemory memory : memories) {
            future = future.thenCompose(ignored -> memoryEmbedding(memory)
                    .thenAccept(currentEmbedding -> {
                        if (currentEmbedding == null) {
                            LOGGER.warning("Failed to generate embedding for memory: %s...", preview(memory.getWhenToUse()));
                            return;
                        }
                        if (isSimilarToExisting(currentEmbedding, existingEmbeddings)) {
                            duplicateCount[0] += 1;
                            LOGGER.debug("Removed duplicate (similar to existing): %s...", preview(memory.getWhenToUse()));
                            return;
                        }
                        if (isSimilarToCurrentBatch(currentEmbedding, uniqueMemories)) {
                            duplicateCount[0] += 1;
                            LOGGER.debug("Removed duplicate (similar in batch): %s...", preview(memory.getWhenToUse()));
                            return;
                        }
                        uniqueMemories.add(memory);
                    }));
        }
        return future.thenApply(ignored -> new DeduplicationResult(uniqueMemories, duplicateCount[0]));
    }

    private CompletableFuture<List<Double>> memoryEmbedding(ReMeMemory memory) {
        Object embeddingModelObject = getEmbeddingModel();
        if (!(embeddingModelObject instanceof Embedding embeddingModel)) {
            LOGGER.warning("No embedding model available");
            return CompletableFuture.completedFuture(null);
        }
        String text = ReasoningValue.safe(memory.getWhenToUse()) + " " + ReasoningValue.safe(memory.getContent());
        return embeddingModel.embedDocuments(List.of(text))
                .handle((embeddings, error) -> {
                    if (error != null || embeddings == null || embeddings.isEmpty()) {
                        LOGGER.error("Error generating embedding: %s", error);
                        return null;
                    }
                    return embeddings.get(0);
                });
    }

    private boolean isSimilarToExisting(List<Double> currentEmbedding, List<List<Double>> existingEmbeddings) {
        for (List<Double> existingEmbedding : existingEmbeddings) {
            double similarity = ReMeUtils.calculateCosineSimilarity(currentEmbedding, existingEmbedding);
            if (similarity > similarityThreshold) {
                LOGGER.debug("Found similar existing memory (similarity: %.3f)", similarity);
                return true;
            }
        }
        return false;
    }

    private boolean isSimilarToCurrentBatch(List<Double> currentEmbedding, List<ReMeMemory> currentMemories) {
        for (int index = 0; index < currentMemories.size(); index++) {
            List<Double> existingEmbedding = batchEmbeddingsCache.get(index);
            if (existingEmbedding == null) {
                continue;
            }
            double similarity = ReMeUtils.calculateCosineSimilarity(currentEmbedding, existingEmbedding);
            if (similarity > similarityThreshold) {
                LOGGER.debug("Found similar memory in current batch (similarity: %.3f)", similarity);
                return true;
            }
        }
        batchEmbeddingsCache.put(currentMemories.size(), currentEmbedding);
        return false;
    }

    private static String preview(String value) {
        String text = value != null ? value : "";
        return text.length() > 30 ? text.substring(0, 30) : text;
    }

    private record DeduplicationResult(List<ReMeMemory> uniqueMemories, int duplicateCount) {
    }

    private static final class ReasoningValue {
        private static String safe(String value) {
            return value != null ? value : "";
        }
    }
}

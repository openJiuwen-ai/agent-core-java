/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.context_evolver.summary.task.reasoning_bank;

import com.openjiuwen.core.common.logging.LoggerProtocol;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.foundation.store.Embedding;
import com.openjiuwen.extensions.context_evolver.core.context.RuntimeContext;
import com.openjiuwen.extensions.context_evolver.core.op.BaseOp;
import com.openjiuwen.extensions.context_evolver.core.schema.VectorNode;
import com.openjiuwen.extensions.context_evolver.core.vector_store.MemoryVectorStore;
import com.openjiuwen.extensions.context_evolver.schema.ReasoningBankMemory;
import com.openjiuwen.extensions.context_evolver.schema.SchemaUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Store ReasoningBank memories into the vector store.
 * <p>
 * Mirrors Python's {@code UpdateVectorStoreOp} in
 * {@code openjiuwen/extensions/context_evolver/summary/task/reasoning_bank/update.py}.
 * </p>
 */
public class UpdateVectorStoreOp extends BaseOp {

    private static final LoggerProtocol LOGGER = Loggers.CONTEXT_ENGINE;

    @Override
    public CompletableFuture<Void> asyncExecute(RuntimeContext context) {
        List<ReasoningBankMemory> memories = memories(context.get("memories", List.of()));
        String userId = String.valueOf(context.get("user_id", "default"));
        if (memories.isEmpty()) {
            LOGGER.info("No memories to store");
            context.set("stored_count", 0);
            context.set("memory_ids", List.of());
            return CompletableFuture.completedFuture(null);
        }

        Object embeddingModelObject = getEmbeddingModel();
        if (!(embeddingModelObject instanceof Embedding embeddingModel)) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException("Embedding model not configured in ServiceContext"));
        }
        Object vectorStoreObject = getVectorStore();
        if (!(vectorStoreObject instanceof MemoryVectorStore vectorStore)) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException("Vector store not configured in ServiceContext"));
        }

        List<VectorNode> vectorNodes = new ArrayList<>();
        for (ReasoningBankMemory memory : memories) {
            memory.setWorkspaceId(userId);
            vectorNodes.add(memory.toVectorNode());
        }
        List<String> contents = vectorNodes.stream().map(VectorNode::getContent).toList();
        LOGGER.debug("Generating embeddings for %s memories...", vectorNodes.size());

        return embeddingModel.embedDocuments(contents)
                .thenCompose(embeddings -> {
                    List<String> storedIds = new ArrayList<>();
                    CompletableFuture<Void> future = CompletableFuture.completedFuture(null);
                    int count = Math.min(vectorNodes.size(), embeddings.size());
                    for (int index = 0; index < count; index++) {
                        VectorNode node = vectorNodes.get(index);
                        List<Double> embedding = embeddings.get(index);
                        future = future.thenCompose(ignored -> {
                            node.setEmbedding(embedding);
                            return vectorStore.asyncUpsert(node)
                                    .thenRun(() -> {
                                        storedIds.add(node.getId());
                                        LOGGER.debug("Stored memory: %s", node.getId());
                                    });
                        });
                    }
                    return future.thenRun(() -> {
                        context.set("stored_count", storedIds.size());
                        context.set("memory_ids", storedIds);
                        LOGGER.info("Stored %s memories in vector store", storedIds.size());
                    });
                });
    }

    private static List<ReasoningBankMemory> memories(Object value) {
        if (!(value instanceof List<?> rawList)) {
            return List.of();
        }
        List<ReasoningBankMemory> result = new ArrayList<>();
        for (Object item : rawList) {
            if (item instanceof ReasoningBankMemory memory) {
                result.add(memory);
            } else if (item instanceof Map<?, ?>) {
                result.add(ReasoningBankMemory.fromMap(SchemaUtils.mapValue(item)));
            }
        }
        return result;
    }
}

/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.context_evolver.retrieve.task.reasoning_bank;

import com.openjiuwen.core.common.logging.LoggerProtocol;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.foundation.store.Embedding;
import com.openjiuwen.extensions.context_evolver.core.context.RuntimeContext;
import com.openjiuwen.extensions.context_evolver.core.op.BaseOp;
import com.openjiuwen.extensions.context_evolver.core.schema.VectorNode;
import com.openjiuwen.extensions.context_evolver.core.vector_store.MemoryVectorStore;
import com.openjiuwen.extensions.context_evolver.schema.ReasoningBankMemory;
import com.openjiuwen.extensions.context_evolver.schema.ReasoningBankRetrievedMemory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Retrieve relevant reasoning strategies from ReasoningBank.
 * <p>
 * Mirrors Python's {@code RecallMemoryOp} in
 * {@code openjiuwen/extensions/context_evolver/retrieve/task/reasoning_bank/run.py}.
 * </p>
 */
public class RecallMemoryOp extends BaseOp {

    private static final LoggerProtocol LOGGER = Loggers.CONTEXT_ENGINE;

    private final int topK;

    public RecallMemoryOp() {
        this(1);
    }

    public RecallMemoryOp(int topK) {
        super(Map.of("top_k", topK));
        this.topK = topK;
    }

    @Override
    public CompletableFuture<Void> asyncExecute(RuntimeContext context) {
        Object queryObject = context.get("query");
        if (queryObject == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("Context has no attribute 'query'"));
        }
        String query = String.valueOf(queryObject);
        String userId = String.valueOf(context.get("user_id", "default"));

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

        LOGGER.debug("Generating query embedding...");
        try {
            return embeddingModel.embedQuery(query)
                    .thenCompose(queryEmbedding -> {
                        Map<String, Object> metadataFilter = new LinkedHashMap<>();
                        metadataFilter.put("workspace_id", userId);
                        metadataFilter.put("type", "reasoning_bank_memory");
                        LOGGER.debug("Searching ReasoningBank for top %s results...", topK);
                        return vectorStore.asyncSearch(queryEmbedding, topK, metadataFilter);
                    })
                    .thenAccept(vectorNodes -> {
                        List<ReasoningBankRetrievedMemory> memories = new ArrayList<>();
                        for (VectorNode node : vectorNodes) {
                            try {
                                ReasoningBankMemory memory = ReasoningBankMemory.fromVectorNode(node);
                                memories.addAll(memory.toRetrievedMemories());
                            } catch (RuntimeException exception) {
                                LOGGER.warning("Failed to convert vector node to memory: %s", exception);
                            }
                        }
                        context.set("retrieved_memories", memories);
                        LOGGER.info("Retrieved %s reasoning strategies from ReasoningBank", memories.size());
                    });
        } catch (RuntimeException exception) {
            return CompletableFuture.failedFuture(exception);
        }
    }
}

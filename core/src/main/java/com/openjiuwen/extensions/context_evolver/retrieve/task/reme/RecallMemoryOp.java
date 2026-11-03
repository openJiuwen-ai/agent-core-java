/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.context_evolver.retrieve.task.reme;

import com.openjiuwen.core.common.logging.LoggerProtocol;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.foundation.store.Embedding;
import com.openjiuwen.extensions.context_evolver.core.context.RuntimeContext;
import com.openjiuwen.extensions.context_evolver.core.op.BaseOp;
import com.openjiuwen.extensions.context_evolver.core.schema.VectorNode;
import com.openjiuwen.extensions.context_evolver.core.vector_store.MemoryVectorStore;
import com.openjiuwen.extensions.context_evolver.schema.ReMeMemory;
import com.openjiuwen.extensions.context_evolver.schema.ReMeRetrievedMemory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Retrieve relevant ReMe memories.
 * <p>
 * Mirrors Python's {@code RecallMemoryOp} in
 * {@code openjiuwen/extensions/context_evolver/retrieve/task/reme/run.py}.
 * </p>
 */
public class RecallMemoryOp extends BaseOp {

    private static final LoggerProtocol LOGGER = Loggers.CONTEXT_ENGINE;

    private final int topkRetrieval;

    public RecallMemoryOp() {
        this(10);
    }

    public RecallMemoryOp(int topkRetrieval) {
        super(Map.of("topk_retrieval", topkRetrieval));
        this.topkRetrieval = topkRetrieval;
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
                        metadataFilter.put("type", "reme_memory");
                        LOGGER.debug("Searching ReMe for top %s results...", topkRetrieval);
                        return vectorStore.asyncSearch(queryEmbedding, topkRetrieval, metadataFilter);
                    })
                    .thenAccept(vectorNodes -> {
                        List<ReMeRetrievedMemory> memories = new ArrayList<>();
                        for (VectorNode node : vectorNodes) {
                            try {
                                ReMeMemory memory = ReMeMemory.fromVectorNode(node);
                                memories.add(memory.toRetrievedMemory());
                            } catch (RuntimeException exception) {
                                LOGGER.warning("Failed to convert vector node to memory: %s", exception);
                            }
                        }
                        context.set("retrieved_memories", memories);
                        LOGGER.info("Retrieved %s ReMe from ReMe", memories.size());
                    });
        } catch (RuntimeException exception) {
            return CompletableFuture.failedFuture(exception);
        }
    }
}

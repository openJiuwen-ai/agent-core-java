/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.context_evolver.retrieve.task.ace;

import com.openjiuwen.core.common.logging.LoggerProtocol;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.extensions.context_evolver.core.context.RuntimeContext;
import com.openjiuwen.extensions.context_evolver.core.op.BaseOp;
import com.openjiuwen.extensions.context_evolver.core.schema.VectorNode;
import com.openjiuwen.extensions.context_evolver.core.vector_store.MemoryVectorStore;
import com.openjiuwen.extensions.context_evolver.schema.ACEMemory;
import com.openjiuwen.extensions.context_evolver.schema.ACERetrievedMemory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Retrieve all ACE playbook memories from the configured vector store.
 * <p>
 * Mirrors Python's {@code RecallMemoryOp} in
 * {@code openjiuwen/extensions/context_evolver/retrieve/task/ace/run.py}.
 * </p>
 */
public class RecallMemoryOp extends BaseOp {

    private static final LoggerProtocol LOGGER = Loggers.CONTEXT_ENGINE;
    private static final int DUMMY_EMBEDDING_SIZE = 2560;
    private static final int MAX_BULLETS = 50;

    @Override
    public CompletableFuture<Void> asyncExecute(RuntimeContext context) {
        String userId = String.valueOf(context.get("user_id", "default"));
        Object vectorStoreObject = getVectorStore();
        if (!(vectorStoreObject instanceof MemoryVectorStore vectorStore)) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException("Vector store not configured in ServiceContext"));
        }

        Map<String, Object> metadataFilter = new LinkedHashMap<>();
        metadataFilter.put("workspace_id", userId);
        metadataFilter.put("type", "ace_memory");

        LOGGER.debug("Loading all ACE memories from vector store...");
        return vectorStore.asyncSearch(dummyEmbedding(), MAX_BULLETS, metadataFilter)
                .thenAccept(nodes -> {
                    List<ACERetrievedMemory> retrievedMemories = new ArrayList<>();
                    for (VectorNode node : nodes) {
                        try {
                            ACEMemory aceMemory = ACEMemory.fromVectorNode(node);
                            retrievedMemories.add(new ACERetrievedMemory(
                                    aceMemory.getId(),
                                    aceMemory.getSection(),
                                    aceMemory.getContent(),
                                    aceMemory.getHelpful(),
                                    aceMemory.getHarmful(),
                                    aceMemory.getNeutral()
                            ));
                        } catch (RuntimeException exception) {
                            LOGGER.warning("Failed to convert ACE memory from node %s: %s", node.getId(), exception);
                        }
                    }
                    context.set("retrieved_memories", retrievedMemories);
                    LOGGER.info("Retrieved %s ACE memories (playbook bullets)", retrievedMemories.size());
                });
    }

    private static List<Double> dummyEmbedding() {
        return Collections.nCopies(DUMMY_EMBEDDING_SIZE, 0.0d);
    }
}

/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.context_evolver.summary.task.reme;

import com.openjiuwen.core.common.logging.LoggerProtocol;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.extensions.context_evolver.core.MemoryPersistenceHelper;
import com.openjiuwen.extensions.context_evolver.core.context.RuntimeContext;
import com.openjiuwen.extensions.context_evolver.core.op.BaseOp;
import com.openjiuwen.extensions.context_evolver.core.schema.VectorNode;
import com.openjiuwen.extensions.context_evolver.core.vector_store.MemoryVectorStore;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Persist ReMe vector nodes for the current user.
 * <p>
 * Mirrors Python's {@code PersistMemoryOp} in
 * {@code openjiuwen/extensions/context_evolver/summary/task/reme/update.py}.
 * </p>
 */
public class PersistMemoryOp extends BaseOp {

    private static final LoggerProtocol LOGGER = Loggers.CONTEXT_ENGINE;
    private static final String ALGO_NAME = "reme";

    private final MemoryPersistenceHelper helper;

    public PersistMemoryOp() {
        this("auto", "./memories/{algo_name}/{user_id}.json", "localhost", 19530, "vector_nodes");
    }

    public PersistMemoryOp(String persistType,
                           String persistPath,
                           String milvusHost,
                           int milvusPort,
                           String milvusCollection) {
        this(persistType, persistPath, milvusHost, milvusPort, milvusCollection, null);
    }

    public PersistMemoryOp(String persistType,
                           String persistPath,
                           String milvusHost,
                           int milvusPort,
                           String milvusCollection,
                           Path allowedRoot) {
        super(Map.of(
                "persist_type", persistType,
                "persist_path", persistPath,
                "milvus_host", milvusHost,
                "milvus_port", milvusPort,
                "milvus_collection", milvusCollection
        ));
        this.helper = new MemoryPersistenceHelper(
                persistType,
                persistPath,
                milvusHost,
                milvusPort,
                milvusCollection,
                allowedRoot
        );
    }

    public MemoryPersistenceHelper getHelper() {
        return helper;
    }

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
        metadataFilter.put("type", "reme_memory");
        java.util.List<VectorNode> allNodes = vectorStore.getAll(metadataFilter);
        if (allNodes.isEmpty()) {
            LOGGER.info("PersistMemoryOp (ReMe): no memories to persist for user=%s", userId);
            context.set("persist_count", 0);
            return CompletableFuture.completedFuture(null);
        }

        Map<String, Object> nodesDict = new LinkedHashMap<>();
        for (VectorNode node : allNodes) {
            nodesDict.put(node.getId(), node.toDict());
        }
        helper.save(userId, ALGO_NAME, nodesDict);
        context.set("persist_count", nodesDict.size());
        LOGGER.info(
                "PersistMemoryOp (ReMe): persisted %d memories for user=%s via %s",
                nodesDict.size(),
                userId,
                helper.getPersistType()
        );
        return CompletableFuture.completedFuture(null);
    }
}

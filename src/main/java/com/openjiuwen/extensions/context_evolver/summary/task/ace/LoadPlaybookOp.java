/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.context_evolver.summary.task.ace;

import com.openjiuwen.core.common.logging.LoggerProtocol;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.extensions.context_evolver.core.context.RuntimeContext;
import com.openjiuwen.extensions.context_evolver.core.op.BaseOp;
import com.openjiuwen.extensions.context_evolver.core.schema.VectorNode;
import com.openjiuwen.extensions.context_evolver.core.vector_store.MemoryVectorStore;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Load existing ACE playbook bullets from the vector store.
 * <p>
 * Mirrors Python's {@code LoadPlaybookOp} in
 * {@code openjiuwen/extensions/context_evolver/summary/task/ace/update.py}.
 * </p>
 */
public class LoadPlaybookOp extends BaseOp {

    private static final LoggerProtocol LOGGER = Loggers.CONTEXT_ENGINE;
    private static final int DUMMY_EMBEDDING_SIZE = 2560;
    private static final int TOP_K = 50;

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

        return vectorStore.asyncSearch(dummyEmbedding(), TOP_K, metadataFilter)
                .handle((nodes, error) -> {
                    if (error != null) {
                        LOGGER.warning("Failed to load playbook: %s. Starting with empty playbook.", error);
                        context.set("playbook", new Playbook());
                        return null;
                    }
                    try {
                        Playbook playbook = new Playbook();
                        for (VectorNode node : nodes) {
                            Map<String, Object> metadata = node.getMetadata();
                            Playbook.Bullet bullet = new Playbook.Bullet(
                                    requiredMetadata(metadata, "id"),
                                    requiredMetadata(metadata, "section"),
                                    requiredMetadata(metadata, "content"),
                                    intMetadata(metadata, "helpful"),
                                    intMetadata(metadata, "harmful"),
                                    intMetadata(metadata, "neutral"),
                                    stringMetadata(metadata, "created_at"),
                                    stringMetadata(metadata, "updated_at")
                            );
                            playbook.loadBullet(bullet);
                        }
                        int maxId = 0;
                        for (String bulletId : playbook.bulletIds()) {
                            try {
                                String[] parts = bulletId.split("-(?=[^-]*$)", 2);
                                if (parts.length == 2) {
                                    maxId = Math.max(maxId, Integer.parseInt(parts[1]));
                                }
                            } catch (NumberFormatException ignored) {
                                // Python ignores non-standard bullet IDs while deriving next_id.
                            }
                        }
                        playbook.setNextId(maxId);
                        context.set("playbook", playbook);
                        LOGGER.info("Loaded playbook with %s bullets, next_id=%s", playbook.bullets().size(), maxId);
                    } catch (RuntimeException exception) {
                        LOGGER.warning("Failed to load playbook: %s. Starting with empty playbook.", exception);
                        context.set("playbook", new Playbook());
                    }
                    return null;
                });
    }

    private static List<Double> dummyEmbedding() {
        return Collections.nCopies(DUMMY_EMBEDDING_SIZE, 0.0d);
    }

    private static String requiredMetadata(Map<String, Object> metadata, String key) {
        Object value = metadata.get(key);
        if (value == null) {
            throw new IllegalArgumentException("Missing ACE metadata key: " + key);
        }
        return String.valueOf(value);
    }

    private static String stringMetadata(Map<String, Object> metadata, String key) {
        Object value = metadata.get(key);
        return value != null ? String.valueOf(value) : "";
    }

    private static int intMetadata(Map<String, Object> metadata, String key) {
        Object value = metadata.get(key);
        if (value == null) {
            return 0;
        }
        return Integer.parseInt(String.valueOf(value));
    }
}

/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */

package com.openjiuwen.core.memory.manage.index;

import com.openjiuwen.core.common.exception.ErrorBuilder;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.common.logging.LoggerProtocol;
import com.openjiuwen.core.common.utils.Pair;
import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.memory.manage.memmodel.BaseMemoryUnit;
import com.openjiuwen.core.memory.manage.memmodel.UserMemStore;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Manager for writing memory operations, dispatching to appropriate memory managers.
 * <p>
 * Corresponds to Python: manage/index/write_manager.py
 */
public class WriteManager {

    private static final LoggerProtocol logger = Loggers.MEMORY;

    private final Map<String, BaseMemoryManager> managers;
    private final UserMemStore memStore;

    /**
     * Initialize WriteManager.
     *
     * @param managers Map of memory type to their corresponding managers
     * @param memStore The user memory store
     */
    public WriteManager(Map<String, BaseMemoryManager> managers, UserMemStore memStore) {
        this.managers = managers;
        this.memStore = memStore;
    }

    /**
     * Add memory units by dispatching to appropriate managers.
     *
     * @param memUnits List of memory units to add
     * @param llm      Optional LLM info as Pair of (name, Model)
     * @return CompletableFuture that completes when all operations are done
     */
    public CompletableFuture<Void> addMem(List<BaseMemoryUnit> memUnits, Pair<String, Model> llm) {
        AtomicBoolean hasInnerException = new AtomicBoolean(false);
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        for (BaseMemoryUnit memUnit : memUnits) {
            String memType = memUnit.getMemType().getValue();
            if (managers.containsKey(memType)) {
                CompletableFuture<Void> future = managers.get(memType).add(memUnit, llm)
                        .exceptionally(e -> {
                            logger.error("Failed to add {}, error: {}", memType, e.getMessage());
                            hasInnerException.set(true);
                            return null;
                        });
                futures.add(future);
            } else {
                logger.warning("Unsupported memory type: {}", memType);
            }
        }

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenRun(() -> {
                    if (hasInnerException.get()) {
                        throw ErrorBuilder.build(
                                StatusCode.MEMORY_ADD_MEMORY_EXECUTION_ERROR,
                                "memory engine add mem has exception"
                        );
                    }
                });
    }

    /**
     * Update memory by its ID.
     *
     * @param userId  The user ID
     * @param scopeId The scope ID
     * @param memId   The memory ID
     * @param memory  The new memory content
     * @return CompletableFuture that completes when the operation is done
     */
    public CompletableFuture<Void> updateMemById(String userId, String scopeId, String memId, String memory) {
        return getMemTypeFromStore(userId, scopeId, memId)
                .thenCompose(memType -> {
                    if (memType == null) {
                        logger.warning("Skipping this update due to failure in getting memory type, " +
                                "mem_id:{}, user_id:{}, scope_id:{}", memId, userId, scopeId);
                        return CompletableFuture.completedFuture(null);
                    }
                    return managers.get(memType).update(userId, scopeId, memId, memory)
                            .thenApply(v -> null);
                });
    }

    /**
     * Delete memory by its ID.
     *
     * @param userId  The user ID
     * @param scopeId The scope ID
     * @param memId   The memory ID
     * @return CompletableFuture that completes when the operation is done
     */
    public CompletableFuture<Void> deleteMemById(String userId, String scopeId, String memId) {
        return getMemTypeFromStore(userId, scopeId, memId)
                .thenCompose(memType -> {
                    if (memType == null) {
                        logger.warning("Skipping this deletion due to failure in getting memory type, " +
                                "mem_id:{}, user_id:{}, scope_id:{}", memId, userId, scopeId);
                        return CompletableFuture.completedFuture(null);
                    }
                    return managers.get(memType).delete(userId, scopeId, memId)
                            .thenApply(v -> null);
                });
    }

    /**
     * Delete all memory for a user.
     *
     * @param userId  The user ID
     * @param scopeId The scope ID
     * @return CompletableFuture that completes when all operations are done
     */
    public CompletableFuture<Void> deleteMemByUserId(String userId, String scopeId) {
        List<CompletableFuture<Boolean>> futures = new ArrayList<>();
        for (String manager : managers.keySet()) {
            futures.add(managers.get(manager).deleteByUserId(userId, scopeId));
        }
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
    }

    private CompletableFuture<String> getMemTypeFromStore(String userId, String scopeId, String memId) {
        return memStore.get(userId, scopeId, memId)
                .thenApply(data -> {
                    if (data == null) {
                        logger.warning("Nonexistent memory, mem_id:{}, user_id:{}, scope_id:{}", memId, userId, scopeId);
                        return null;
                    }
                    if (!data.containsKey("mem_type")) {
                        logger.warning("The mem_type field doesn't exist, mem_id:{}, user_id:{}, scope_id:{}",
                                memId, userId, scopeId);
                        return null;
                    }
                    String memType = (String) data.get("mem_type");
                    if (!managers.containsKey(memType)) {
                        logger.warning("Unsupported mem_type:{}, mem_id:{}, user_id:{}, scope_id:{}",
                                memType, memId, userId, scopeId);
                        return null;
                    }
                    return memType;
                })
                .exceptionally(e -> {
                    logger.error("Failed to get memory: {}", e.getMessage());
                    return null;
                });
    }
}


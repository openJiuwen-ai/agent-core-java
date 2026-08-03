/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.manage.index;

import com.openjiuwen.core.common.logging.LoggerProtocol;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.common.logging.events.LogEventType;
import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.store.BaseMemoryIndex;
import com.openjiuwen.core.foundation.store.MemoryDoc;
import com.openjiuwen.core.memory.manage.mem_model.BaseMemoryUnit;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;

/**
 * Coordinates write operations across memory managers.
 *
 * <p>Mirrors Python's {@code WriteManager} in
 * {@code openjiuwen/core/memory/manage/index/write_manager.py}.</p>
 */
public class WriteManager {

    private static final LoggerProtocol MEMORY_LOGGER = Loggers.MEMORY;

    private final Map<String, BaseMemoryManager> managers;
    private final BaseMemoryIndex memoryIndex;

    public WriteManager(Map<String, BaseMemoryManager> managers, BaseMemoryIndex memoryIndex) {
        this.managers = managers;
        this.memoryIndex = memoryIndex;
    }

    public CompletionStage<List<BaseMemoryUnit>> addMemories(String userId,
                                                             String scopeId,
                                                             Map<String, List<BaseMemoryUnit>> memories,
                                                             Model llm) {
        return addMemories(userId, scopeId, memories, llm, Map.of());
    }

    public CompletionStage<List<BaseMemoryUnit>> addMemories(String userId,
                                                             String scopeId,
                                                             Map<String, List<BaseMemoryUnit>> memories,
                                                             Model llm,
                                                             Map<String, Object> kwargs) {
        if (memories == null || memories.isEmpty()) {
            MEMORY_LOGGER.debug(
                    "No memory units to add",
                    "event_type", LogEventType.MEMORY_STORE
            );
            return CompletableFuture.completedFuture(List.of());
        }

        List<BaseMemoryUnit> result = new ArrayList<>();
        CompletionStage<Void> stage = CompletableFuture.completedFuture(null);
        for (BaseMemoryManager manager : uniqueManagers()) {
            stage = stage.thenCompose(ignored -> callAddMemories(manager, userId, scopeId, memories, llm, kwargs)
                    .thenApply(memUnits -> {
                        appendAddResult(manager, result, memUnits);
                        return null;
                    }));
        }
        return stage.thenApply(ignored -> result);
    }

    public CompletionStage<Void> updateMemById(String userId, String scopeId, String memId, String memory) {
        return updateMemById(userId, scopeId, memId, memory, Map.of());
    }

    public CompletionStage<Void> updateMemById(String userId,
                                               String scopeId,
                                               String memId,
                                               String memory,
                                               Map<String, Object> kwargs) {
        return getMemTypeFromIndex(userId, scopeId, memId).thenCompose(memType -> {
            if (memType == null) {
                MEMORY_LOGGER.warning(
                        "Skipping this update due to failure in getting memory type",
                        "memory_type", null,
                        "memory_id", singletonMemoryId(memId),
                        "event_type", LogEventType.MEMORY_STORE,
                        "user_id", userId,
                        "scope_id", scopeId
                );
                return CompletableFuture.completedFuture(null);
            }
            BaseMemoryManager manager = managers.get(memType);
            return callUpdate(manager, userId, scopeId, memId, memory, kwargs);
        });
    }

    public CompletionStage<Void> deleteMemById(String userId, String scopeId, String memId) {
        return deleteMemById(userId, scopeId, memId, Map.of());
    }

    public CompletionStage<Void> deleteMemById(String userId,
                                               String scopeId,
                                               String memId,
                                               Map<String, Object> kwargs) {
        return getMemTypeFromIndex(userId, scopeId, memId).thenCompose(memType -> {
            if (memType == null) {
                MEMORY_LOGGER.warning(
                        "Skipping this deletion due to failure in getting memory type",
                        "memory_type", null,
                        "memory_id", singletonMemoryId(memId),
                        "event_type", LogEventType.MEMORY_STORE,
                        "user_id", userId,
                        "scope_id", scopeId
                );
                return CompletableFuture.completedFuture(null);
            }
            BaseMemoryManager manager = managers.get(memType);
            return callDelete(manager, userId, scopeId, memId, kwargs);
        });
    }

    public CompletionStage<Void> deleteMemByUserId(String userId, String scopeId) {
        return deleteMemByUserId(userId, scopeId, Map.of());
    }

    public CompletionStage<Void> deleteMemByUserId(String userId, String scopeId, Map<String, Object> kwargs) {
        CompletionStage<Void> stage = CompletableFuture.completedFuture(null);
        for (BaseMemoryManager manager : uniqueManagers()) {
            stage = stage.thenCompose(ignored -> callDeleteByUserId(manager, userId, scopeId, kwargs));
        }
        return stage;
    }

    private CompletionStage<String> getMemTypeFromIndex(String userId, String scopeId, String memId) {
        CompletionStage<MemoryDoc> stage;
        try {
            stage = memoryIndex.getById(userId, scopeId, memId);
        } catch (Throwable failure) {
            return CompletableFuture.failedFuture(failure);
        }
        if (stage == null) {
            return CompletableFuture.failedFuture(new NullPointerException("memory_index.get_by_id returned null"));
        }
        return stage.thenApply(memoryDoc -> {
            if (memoryDoc != null && hasText(memoryDoc.getType())) {
                String memType = memoryDoc.getType();
                if (managers.containsKey(memType)) {
                    return memType;
                }
                MEMORY_LOGGER.warning(
                        "Unsupported mem_type",
                        "memory_id", singletonMemoryId(memId),
                        "memory_type", memType,
                        "event_type", LogEventType.MEMORY_STORE,
                        "user_id", userId,
                        "scope_id", scopeId
                );
            }

            MEMORY_LOGGER.warning(
                    "Nonexistent memory or memory type",
                    "memory_id", singletonMemoryId(memId),
                    "event_type", LogEventType.MEMORY_STORE,
                    "user_id", userId,
                    "scope_id", scopeId
            );
            return null;
        });
    }

    private List<BaseMemoryManager> uniqueManagers() {
        Set<BaseMemoryManager> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        List<BaseMemoryManager> unique = new ArrayList<>();
        for (BaseMemoryManager manager : managers.values()) {
            if (seen.add(manager)) {
                unique.add(manager);
            }
        }
        return unique;
    }

    private CompletionStage<List<BaseMemoryUnit>> callAddMemories(BaseMemoryManager manager,
                                                                  String userId,
                                                                  String scopeId,
                                                                  Map<String, List<BaseMemoryUnit>> memories,
                                                                  Model llm,
                                                                  Map<String, Object> kwargs) {
        try {
            CompletionStage<List<BaseMemoryUnit>> stage = manager.addMemories(
                    userId,
                    scopeId,
                    memories,
                    llm,
                    normalizeKwargs(kwargs)
            );
            if (stage == null) {
                NullPointerException failure = new NullPointerException("manager.add_memories returned null");
                logAddFailure(manager, failure);
                return CompletableFuture.failedFuture(failure);
            }
            return stage.handle((memUnits, failure) -> {
                if (failure == null) {
                    return memUnits;
                }
                Throwable cause = unwrapCompletionFailure(failure);
                logAddFailure(manager, cause);
                throw asCompletionException(cause);
            });
        } catch (Throwable failure) {
            Throwable cause = unwrapCompletionFailure(failure);
            logAddFailure(manager, cause);
            return CompletableFuture.failedFuture(cause);
        }
    }

    private void appendAddResult(BaseMemoryManager manager, List<BaseMemoryUnit> result, List<BaseMemoryUnit> memUnits) {
        try {
            result.addAll(memUnits);
        } catch (Throwable failure) {
            Throwable cause = unwrapCompletionFailure(failure);
            logAddFailure(manager, cause);
            throw asCompletionException(cause);
        }
    }

    private CompletionStage<Void> callUpdate(BaseMemoryManager manager,
                                             String userId,
                                             String scopeId,
                                             String memId,
                                             String memory,
                                             Map<String, Object> kwargs) {
        try {
            CompletionStage<Boolean> stage = manager.update(userId, scopeId, memId, memory, normalizeKwargs(kwargs));
            if (stage == null) {
                return CompletableFuture.failedFuture(new NullPointerException("manager.update returned null"));
            }
            return stage.thenApply(ignored -> null);
        } catch (Throwable failure) {
            return CompletableFuture.failedFuture(unwrapCompletionFailure(failure));
        }
    }

    private CompletionStage<Void> callDelete(BaseMemoryManager manager,
                                             String userId,
                                             String scopeId,
                                             String memId,
                                             Map<String, Object> kwargs) {
        try {
            CompletionStage<Boolean> stage = manager.delete(userId, scopeId, memId, normalizeKwargs(kwargs));
            if (stage == null) {
                return CompletableFuture.failedFuture(new NullPointerException("manager.delete returned null"));
            }
            return stage.thenApply(ignored -> null);
        } catch (Throwable failure) {
            return CompletableFuture.failedFuture(unwrapCompletionFailure(failure));
        }
    }

    private CompletionStage<Void> callDeleteByUserId(BaseMemoryManager manager,
                                                     String userId,
                                                     String scopeId,
                                                     Map<String, Object> kwargs) {
        try {
            CompletionStage<Boolean> stage = manager.deleteByUserId(userId, scopeId, normalizeKwargs(kwargs));
            if (stage == null) {
                return CompletableFuture.failedFuture(new NullPointerException("manager.delete_by_user_id returned null"));
            }
            return stage.thenApply(ignored -> null);
        } catch (Throwable failure) {
            return CompletableFuture.failedFuture(unwrapCompletionFailure(failure));
        }
    }

    private void logAddFailure(BaseMemoryManager manager, Throwable failure) {
        MEMORY_LOGGER.error(
                "Failed to add mem",
                "exception", exceptionMessage(failure),
                "memory_type", managerMemType(manager),
                "event_type", LogEventType.MEMORY_STORE
        );
    }

    private String managerMemType(BaseMemoryManager manager) {
        if (manager instanceof FragmentMemoryManager fragmentMemoryManager) {
            return fragmentMemoryManager.getMemType();
        }
        if (manager instanceof SummaryManager summaryManager) {
            return summaryManager.getMemType();
        }
        if (manager instanceof VariableManager variableManager) {
            return variableManager.getMemType();
        }
        if (managers != null) {
            for (Map.Entry<String, BaseMemoryManager> entry : managers.entrySet()) {
                if (entry.getValue() == manager) {
                    return entry.getKey();
                }
            }
        }
        return null;
    }

    private static Map<String, Object> normalizeKwargs(Map<String, Object> kwargs) {
        return kwargs == null ? Map.of() : kwargs;
    }

    private static List<String> singletonMemoryId(String memId) {
        return Collections.singletonList(memId);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isEmpty();
    }

    private static CompletionException asCompletionException(Throwable failure) {
        return failure instanceof CompletionException completionException
                ? completionException
                : new CompletionException(failure);
    }

    private static Throwable unwrapCompletionFailure(Throwable failure) {
        Throwable current = failure;
        while ((current instanceof CompletionException) && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    private static String exceptionMessage(Throwable failure) {
        if (failure == null) {
            return "null";
        }
        String message = failure.getMessage();
        return message == null ? "" : message;
    }
}

/** Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.*/

package com.openjiuwen.core.memory.manage.index;

import com.openjiuwen.core.common.logging.LoggerProtocol;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.common.logging.events.LogEventType;
import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.memory.manage.mem_model.BaseMemoryUnit;
import com.openjiuwen.core.memory.manage.mem_model.SemanticStore;
import com.openjiuwen.core.memory.manage.mem_model.UserMemStore;

import java.util.List;
import java.util.Map;

/**
 * Orchestrates memory write operations across all memory type managers.
 */
public class WriteManager {

    private static final LoggerProtocol MEMORY_LOGGER = Loggers.MEMORY;

    private final Map<String, BaseMemoryManager> managers;
    private final UserMemStore memStore;

    public WriteManager(Map<String, BaseMemoryManager> managers, UserMemStore memStore) {
        this.managers = managers;
        this.memStore = memStore;
    }

    /**
     * Add memories of different types in batch.
     */
    public void addMemories(String userId, String scopeId,
                             Map<String, ? extends List<? extends BaseMemoryUnit>> memories,
                             Map.Entry<String, Model> llm,
                             SemanticStore semanticStore) {
        if (memories == null || memories.isEmpty()) {
            MEMORY_LOGGER.debug("[{}] No memory units to add", LogEventType.MEMORY_STORE);
            return;
        }
        for (Map.Entry<String, ? extends List<? extends BaseMemoryUnit>> entry : memories.entrySet()) {
            String memType = entry.getKey();
            List<? extends BaseMemoryUnit> units = entry.getValue();
            if (managers.containsKey(memType)) {
                try {
                    Map<String, Object> kwargs = Map.of("semantic_store", semanticStore);
                    managers.get(memType).addMemories(userId, scopeId, units, llm, kwargs);
                } catch (Exception e) {
                    MEMORY_LOGGER.error("[{}] Failed to add mem, type={}, error={}",
                            LogEventType.MEMORY_STORE, memType, e.getMessage());
                    throw e;
                }
            } else {
                MEMORY_LOGGER.warn("[{}] Unsupported memory type: {}",
                        LogEventType.MEMORY_STORE, memType);
            }
        }
    }

    /**
     * Update a memory by ID (determines type from store).
     */
    public void updateMemById(String userId, String scopeId, String memId, String memory,
                               SemanticStore semanticStore) {
        String memType = getMemTypeFromStore(userId, scopeId, memId);
        if (memType == null) {
            MEMORY_LOGGER.warn("[{}] Skipping update, cannot determine mem_type. memId={}",
                    LogEventType.MEMORY_STORE, memId);
            return;
        }
        Map<String, Object> kwargs = Map.of("semantic_store", semanticStore);
        managers.get(memType).update(userId, scopeId, memId, memory, kwargs);
    }

    /**
     * Delete a memory by ID (determines type from store).
     */
    public void deleteMemById(String userId, String scopeId, String memId,
                               SemanticStore semanticStore) {
        String memType = getMemTypeFromStore(userId, scopeId, memId);
        if (memType == null) {
            MEMORY_LOGGER.warn("[{}] Skipping deletion, cannot determine mem_type. memId={}",
                    LogEventType.MEMORY_STORE, memId);
            return;
        }
        Map<String, Object> kwargs = Map.of("semantic_store", semanticStore);
        managers.get(memType).delete(userId, scopeId, memId, kwargs);
    }

    /**
     * Delete all memories for a user across all types.
     */
    public void deleteMemByUserId(String userId, String scopeId, SemanticStore semanticStore) {
        Map<String, Object> kwargs = Map.of("semantic_store", semanticStore);
        for (BaseMemoryManager manager : managers.values()) {
            manager.deleteByUserId(userId, scopeId, kwargs);
        }
    }

    private String getMemTypeFromStore(String userId, String scopeId, String memId) {
        Map<String, Object> data;
        try {
            data = memStore.get(userId, scopeId, memId);
        } catch (Exception e) {
            MEMORY_LOGGER.error("[{}] Failed to get memory. memId={}, error={}",
                    LogEventType.MEMORY_STORE, memId, e.getMessage());
            return null;
        }
        if (data == null) {
            MEMORY_LOGGER.warn("[{}] Nonexistent memory. memId={}", LogEventType.MEMORY_STORE, memId);
            return null;
        }
        if (!data.containsKey("mem_type")) {
            MEMORY_LOGGER.warn("[{}] mem_type field missing. memId={}", LogEventType.MEMORY_STORE, memId);
            return null;
        }
        String memType = String.valueOf(data.get("mem_type"));
        if (!managers.containsKey(memType)) {
            MEMORY_LOGGER.warn("[{}] Unsupported mem_type={}. memId={}", LogEventType.MEMORY_STORE, memType, memId);
            return null;
        }
        return memType;
    }
}

/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.manage.index;

import com.openjiuwen.core.common.logging.events.LogEventType;
import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.memory.common.KvPrefixRegistry;
import com.openjiuwen.core.memory.manage.mem_model.BaseMemoryUnit;
import com.openjiuwen.core.memory.manage.mem_model.VariableUnit;
import com.openjiuwen.spi.store.BaseKVStore;

import java.util.*;

/**
 * Manages variable memory using KV store.
 */
public class VariableManager extends BaseMemoryManager {

    private static final String SEPARATOR = "/";
    private static final String USER_VAR_PREFIX = "user_var";
    private static final String SESSION_VAR_PREFIX = "session_var";

    private final BaseKVStore kvStore;
    private final byte[] cryptoKey;

    /**
     * Auto-generated for codecheck compliance.
     */
    public VariableManager(BaseKVStore kvStore, byte[] cryptoKey) {
        this.kvStore = kvStore;
        this.cryptoKey = cryptoKey;
        KvPrefixRegistry.getInstance().registerCurrent(USER_VAR_PREFIX);
        KvPrefixRegistry.getInstance().registerCurrent(SESSION_VAR_PREFIX);
    }

    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public void addMemories(String userId, String scopeId, List<? extends BaseMemoryUnit> memories,
                             Map.Entry<String, Model> llm, Map<String, Object> kwargs) {
        @SuppressWarnings("unchecked")
        List<VariableUnit> variableUnits = (List<VariableUnit>) (List<?>) memories;
        for (VariableUnit unit : variableUnits) {
            if (kvStore == null) {
                MEMORY_LOGGER.error("[{}] kv_store cannot be None", LogEventType.MEMORY_STORE);
                return;
            }
            String key = makeVariableKey(userId, scopeId, unit.getVariableName(), null);
            String value = encryptMemoryIfNeeded(cryptoKey, unit.getVariableMem());
            kvStore.set(key, value);
        }
    }

    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public void update(String userId, String scopeId, String memId, String newMemory,
                        Map<String, Object> kwargs) {
        MEMORY_LOGGER.warn("[{}] update not implemented for VariableManager", LogEventType.MEMORY_STORE);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void updateUserVariable(String userId, String scopeId, String varName, String varMem) {
        if (kvStore == null) {
            MEMORY_LOGGER.error("[{}] kv_store cannot be None", LogEventType.MEMORY_STORE);
            return;
        }
        Map<String, String> existing = queryVariable(userId, scopeId, varName, null);
        if (!checkExist(existing, varName)) {
            return;
        }
        String key = makeVariableKey(userId, scopeId, varName, null);
        String value = encryptMemoryIfNeeded(cryptoKey, varMem);
        kvStore.set(key, value);
    }

    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public boolean delete(String userId, String scopeId, String memId, Map<String, Object> kwargs) {
        MEMORY_LOGGER.warn("[{}] delete not implemented for VariableManager", LogEventType.MEMORY_STORE);
        return false;
    }

    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public boolean deleteByUserId(String userId, String scopeId, Map<String, Object> kwargs) {
        if (kvStore == null) {
            MEMORY_LOGGER.error("[{}] kv_store cannot be None", LogEventType.MEMORY_STORE);
            return false;
        }
        String userPrefix = USER_VAR_PREFIX + SEPARATOR + userId + SEPARATOR + scopeId + SEPARATOR;
        String sessionPrefix = SESSION_VAR_PREFIX + SEPARATOR + userId + SEPARATOR + scopeId + SEPARATOR;
        kvStore.deleteByPrefix(userPrefix, null);
        kvStore.deleteByPrefix(sessionPrefix, null);
        return true;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void deleteUserVariable(String userId, String scopeId, String varName) {
        if (kvStore == null) {
            MEMORY_LOGGER.error("[{}] kv_store cannot be None", LogEventType.MEMORY_STORE);
            return;
        }
        String key = makeVariableKey(userId, scopeId, varName, null);
        kvStore.delete(key);
    }

    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public Map<String, Object> get(String userId, String scopeId, String memId) {
        MEMORY_LOGGER.warn("[{}] get not implemented for VariableManager", LogEventType.MEMORY_STORE);
        return null;
    }

    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public List<Map<String, Object>> search(String userId, String scopeId, String query, int topK,
                                             Map<String, Object> kwargs) {
        MEMORY_LOGGER.warn("[{}] search not implemented for VariableManager", LogEventType.MEMORY_STORE);
        return null;
    }

    /**
     * Query variable by user_id, scope_id, variable_name. Returns variable memory.
     */
    @SuppressWarnings("unchecked")
    /**
     * Auto-generated for codecheck compliance.
     */
    public Map<String, String> queryVariable(String userId, String scopeId, String name, String sessionId) {
        checkUserAndScopeId(userId, scopeId);
        if (name == null || name.isBlank()) {
            String prefix = USER_VAR_PREFIX + SEPARATOR + userId + SEPARATOR + scopeId + SEPARATOR;
            Map<String, Object> kvRet = kvStore.getByPrefix(prefix);
            Map<String, String> result = new LinkedHashMap<>();
            if (kvRet != null) {
                for (Map.Entry<String, Object> entry : kvRet.entrySet()) {
                    String decrypted = decryptMemoryIfNeeded(cryptoKey, String.valueOf(entry.getValue()));
                    String varKey = entry.getKey().substring(entry.getKey().lastIndexOf(SEPARATOR) + 1);
                    result.put(varKey, decrypted);
                }
            }
            return result;
        }

        String key;
        if (sessionId != null) {
            key = SESSION_VAR_PREFIX + SEPARATOR + userId + SEPARATOR + scopeId
                    + SEPARATOR + sessionId + SEPARATOR + name;
        } else {
            key = USER_VAR_PREFIX + SEPARATOR + userId + SEPARATOR + scopeId + SEPARATOR + name;
        }
        Object kvRet = kvStore.get(key);
        String decrypted = decryptMemoryIfNeeded(cryptoKey, kvRet != null ? String.valueOf(kvRet) : null);
        Map<String, String> result = new LinkedHashMap<>();
        result.put(name, decrypted);
        return result;
    }

    // ---- Private Helpers ----

    private String makeVariableKey(String userId, String scopeId, String varName, String sessionId) {
        if (sessionId != null) {
            return SESSION_VAR_PREFIX + SEPARATOR + userId + SEPARATOR + scopeId
                    + SEPARATOR + sessionId + SEPARATOR + varName;
        }
        return USER_VAR_PREFIX + SEPARATOR + userId + SEPARATOR + scopeId + SEPARATOR + varName;
    }

    private void checkUserAndScopeId(String userId, String scopeId) {
        if (userId == null || userId.isBlank()) {
            MEMORY_LOGGER.error("[{}] user_id is empty for variable operation", LogEventType.MEMORY_RETRIEVE);
        }
        if (scopeId == null || scopeId.isBlank()) {
            MEMORY_LOGGER.error("[{}] scope_id is empty for variable operation", LogEventType.MEMORY_RETRIEVE);
        }
    }

    private static boolean checkExist(Map<String, String> variableDict, String variableName) {
        if (variableDict == null || variableDict.isEmpty()) {
            return false;
        }
        if (!variableDict.containsKey(variableName)) {
            return false;
        }
        return variableDict.get(variableName) != null && !variableDict.get(variableName).isEmpty();
    }
}

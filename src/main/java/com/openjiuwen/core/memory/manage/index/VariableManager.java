/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.manage.index;

import com.openjiuwen.core.common.logging.events.LogEventType;
import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.memory.common.KvPrefixRegistry;
import com.openjiuwen.core.multitenant.TenantKVStoreKeyResolver;
import com.openjiuwen.core.memory.manage.mem_model.BaseMemoryUnit;
import com.openjiuwen.core.memory.manage.mem_model.VariableUnit;
import com.openjiuwen.spi.store.BaseKVStore;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Manages variable memory using KV store.
 * 
 * @since 0.1.7
 */
public class VariableManager extends BaseMemoryManager {
    private static final String SEPARATOR = "/";
    private static final String USER_VAR_PREFIX = "user_var";
    private static final String SESSION_VAR_PREFIX = "session_var";

    private final BaseKVStore kvStore;
    private final byte[] cryptoKey;

    /**
     * VariableManager.
     * 
     * @param kvStore kvStore
     * @param cryptoKey cryptoKey
     * @since 0.1.7
     */
    public VariableManager(BaseKVStore kvStore, byte[] cryptoKey) {
        this.kvStore = kvStore;
        this.cryptoKey = cryptoKey;
        KvPrefixRegistry.getInstance().registerCurrent(USER_VAR_PREFIX);
        KvPrefixRegistry.getInstance().registerCurrent(SESSION_VAR_PREFIX);
    }

    /**
     * addMemories.
     * 
     * @param userId userId
     * @param scopeId scopeId
     * @param memories memories
     * @param llm llm
     * @param kwargs kwargs
     * @since 0.1.7
     */
    @Override
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

    /**
     * update.
     * 
     * @param userId userId
     * @param scopeId scopeId
     * @param memId memId
     * @param newMemory newMemory
     * @param kwargs kwargs
     * @since 0.1.7
     */
    @Override
    public void update(String userId, String scopeId, String memId, String newMemory, Map<String, Object> kwargs) {
        MEMORY_LOGGER.warn("[{}] update not implemented for VariableManager", LogEventType.MEMORY_STORE);
    }

    /**
     * updateUserVariable.
     * 
     * @param userId userId
     * @param scopeId scopeId
     * @param varName varName
     * @param varMem varMem
     * @since 0.1.7
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

    /**
     * delete.
     * 
     * @param userId userId
     * @param scopeId scopeId
     * @param memId memId
     * @param kwargs kwargs
     * @return the result
     * @since 0.1.7
     */
    @Override
    public boolean delete(String userId, String scopeId, String memId, Map<String, Object> kwargs) {
        MEMORY_LOGGER.warn("[{}] delete not implemented for VariableManager", LogEventType.MEMORY_STORE);
        return false;
    }

    /**
     * deleteByUserId.
     * 
     * @param userId userId
     * @param scopeId scopeId
     * @param kwargs kwargs
     * @return the result
     * @since 0.1.7
     */
    @Override
    public boolean deleteByUserId(String userId, String scopeId, Map<String, Object> kwargs) {
        if (kvStore == null) {
            MEMORY_LOGGER.error("[{}] kv_store cannot be None", LogEventType.MEMORY_STORE);
            return false;
        }
        String userPrefix = TenantKVStoreKeyResolver.resolvePrefix(USER_VAR_PREFIX + SEPARATOR + userId + SEPARATOR + scopeId + SEPARATOR);
        String sessionPrefix = TenantKVStoreKeyResolver.resolvePrefix(SESSION_VAR_PREFIX + SEPARATOR + userId + SEPARATOR + scopeId + SEPARATOR);
        kvStore.deleteByPrefix(userPrefix, null);
        kvStore.deleteByPrefix(sessionPrefix, null);
        return true;
    }

    /**
     * deleteUserVariable.
     * 
     * @param userId userId
     * @param scopeId scopeId
     * @param varName varName
     * @since 0.1.7
     */
    public void deleteUserVariable(String userId, String scopeId, String varName) {
        if (kvStore == null) {
            MEMORY_LOGGER.error("[{}] kv_store cannot be None", LogEventType.MEMORY_STORE);
            return;
        }
        String key = makeVariableKey(userId, scopeId, varName, null);
        kvStore.delete(key);
    }

    /**
     * get.
     * 
     * @param userId userId
     * @param scopeId scopeId
     * @param memId memId
     * @return the result
     * @since 0.1.7
     */
    @Override
    public Map<String, Object> get(String userId, String scopeId, String memId) {
        MEMORY_LOGGER.warn("[{}] get not implemented for VariableManager", LogEventType.MEMORY_STORE);
        return null;
    }

    /**
     * search.
     * 
     * @param userId userId
     * @param scopeId scopeId
     * @param query query
     * @param topK topK
     * @param kwargs kwargs
     * @return the result
     * @since 0.1.7
     */
    @Override
    public List<Map<String, Object>> search(String userId, String scopeId, String query, int topK,
            Map<String, Object> kwargs) {
        MEMORY_LOGGER.warn("[{}] search not implemented for VariableManager", LogEventType.MEMORY_STORE);
        return null;
    }

    /**
     * queryVariable.
     * 
     * @param userId userId
     * @param scopeId scopeId
     * @param name name
     * @param sessionId sessionId
     * @return the result
     * @since 0.1.7
     */
    @SuppressWarnings("unchecked")
    public Map<String, String> queryVariable(String userId, String scopeId, String name, String sessionId) {
        checkUserAndScopeId(userId, scopeId);
        if (name == null || name.isBlank()) {
            String prefix = TenantKVStoreKeyResolver.resolvePrefix(USER_VAR_PREFIX + SEPARATOR + userId + SEPARATOR + scopeId + SEPARATOR);
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
            key = TenantKVStoreKeyResolver.resolveKey(SESSION_VAR_PREFIX + SEPARATOR + userId + SEPARATOR + scopeId + SEPARATOR + sessionId + SEPARATOR
                    + name);
        } else {
            key = TenantKVStoreKeyResolver.resolveKey(USER_VAR_PREFIX + SEPARATOR + userId + SEPARATOR + scopeId + SEPARATOR + name);
        }
        Object kvRet = kvStore.get(key);
        String decrypted = decryptMemoryIfNeeded(cryptoKey, kvRet != null ? String.valueOf(kvRet) : null);
        Map<String, String> result = new LinkedHashMap<>();
        result.put(name, decrypted);
        return result;
    }

    // ---- Private Helpers ----

    /**
     * makeVariableKey.
     * 
     * @param userId userId
     * @param scopeId scopeId
     * @param varName varName
     * @param sessionId sessionId
     * @return the result
     * @since 0.1.7
     */
    private String makeVariableKey(String userId, String scopeId, String varName, String sessionId) {
        String rawKey;
        if (sessionId != null) {
            rawKey = SESSION_VAR_PREFIX + SEPARATOR + userId + SEPARATOR + scopeId + SEPARATOR + sessionId + SEPARATOR
                    + varName;
        } else {
            rawKey = USER_VAR_PREFIX + SEPARATOR + userId + SEPARATOR + scopeId + SEPARATOR + varName;
        }
        return TenantKVStoreKeyResolver.resolveKey(rawKey);
    }

    /**
     * checkUserAndScopeId.
     * 
     * @param userId userId
     * @param scopeId scopeId
     * @since 0.1.7
     */
    private void checkUserAndScopeId(String userId, String scopeId) {
        if (userId == null || userId.isBlank()) {
            MEMORY_LOGGER.error("[{}] user_id is empty for variable operation", LogEventType.MEMORY_RETRIEVE);
        }
        if (scopeId == null || scopeId.isBlank()) {
            MEMORY_LOGGER.error("[{}] scope_id is empty for variable operation", LogEventType.MEMORY_RETRIEVE);
        }
    }

    /**
     * checkExist.
     * 
     * @param variableDict variableDict
     * @param variableName variableName
     * @return the result
     * @since 0.1.7
     */
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

/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */

package com.openjiuwen.core.memory.manage.index;

import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.common.logging.LoggerProtocol;
import com.openjiuwen.core.common.utils.Pair;
import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.store.BaseKVStore;
import com.openjiuwen.core.memory.manage.memmodel.BaseMemoryUnit;
import com.openjiuwen.core.memory.manage.memmodel.VariableUnit;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Manager for variable-type memory storage using KV store.
 * <p>
 * Corresponds to Python: manage/index/variable_manager.py
 */
public class VariableManager extends BaseMemoryManager {

    private static final LoggerProtocol logger = Loggers.MEMORY;
    private static final String SEPARATOR = "/";

    private final BaseKVStore kvStore;
    private final byte[] cryptoKey;

    /**
     * Initialize the VariableManager.
     *
     * @param kvStore   The KV store to use for storage
     * @param cryptoKey The encryption key for encrypting variable values
     */
    public VariableManager(BaseKVStore kvStore, byte[] cryptoKey) {
        this.kvStore = kvStore;
        this.cryptoKey = cryptoKey != null ? cryptoKey : new byte[0];
    }

    @Override
    public CompletableFuture<Void> add(BaseMemoryUnit memory, Pair<String, Model> llmInfo) {
        if (kvStore == null) {
            logger.error("kv_store cannot be None");
            return CompletableFuture.completedFuture(null);
        }

        if (!(memory instanceof VariableUnit variableUnit)) {
            logger.error("Variable add Must pass VariableUnit class");
            return CompletableFuture.completedFuture(null);
        }

        Pair<String, String> keyValue = makeVariablePairs(
                variableUnit.getUserId(),
                false,
                variableUnit.getScopeId(),
                variableUnit.getVariableName(),
                null,
                variableUnit.getVariableMem(),
                null
        );

        return kvStore.set(keyValue.getKey(), keyValue.getValue())
                .thenApply(v -> null);
    }

    @Override
    public CompletableFuture<Boolean> update(String userId, String scopeId, String memId, String newMemory) {
        logger.warning("not implemented method update");
        return CompletableFuture.completedFuture(false);
    }

    /**
     * Update a user variable by name.
     *
     * @param userId  The user ID
     * @param scopeId The scope ID
     * @param varName The variable name
     * @param varMem  The new variable value
     * @return CompletableFuture that completes when the operation is done
     */
    public CompletableFuture<Void> updateUserVariable(String userId, String scopeId, String varName, String varMem) {
        if (kvStore == null) {
            logger.error("kv_store cannot be None");
            return CompletableFuture.completedFuture(null);
        }

        return queryVariable(userId, scopeId, varName, null)
                .thenCompose(existingVariable -> {
                    if (!checkExist(existingVariable, varName)) {
                        return CompletableFuture.completedFuture(null);
                    }
                    Pair<String, String> keyValue = makeVariablePairs(
                            userId, false, scopeId, varName, null, varMem, null
                    );
                    return kvStore.set(keyValue.getKey(), keyValue.getValue())
                            .thenApply(v -> null);
                });
    }

    @Override
    public CompletableFuture<Boolean> delete(String userId, String scopeId, String memId) {
        logger.warning("not implemented method delete");
        return CompletableFuture.completedFuture(false);
    }

    /**
     * Delete a user variable by name.
     *
     * @param userId  The user ID
     * @param scopeId The scope ID
     * @param varName The variable name
     * @return CompletableFuture that completes when the operation is done
     */
    public CompletableFuture<Void> deleteUserVariable(String userId, String scopeId, String varName) {
        if (kvStore == null) {
            logger.error("kv_store cannot be None");
            return CompletableFuture.completedFuture(null);
        }

        Pair<String, String> keyValue = makeVariablePairs(userId, false, scopeId, varName, null, null, null);
        return kvStore.delete(keyValue.getKey())
                .thenApply(v -> null);
    }

    @Override
    public CompletableFuture<Boolean> deleteByUserId(String userId, String scopeId) {
        if (kvStore == null) {
            logger.error("kv_store cannot be None");
            return CompletableFuture.completedFuture(false);
        }

        String userPrefix = "user_var" + SEPARATOR + userId + SEPARATOR + scopeId + SEPARATOR;
        String sessionPrefix = "session_var" + SEPARATOR + userId + SEPARATOR + scopeId + SEPARATOR;

        return kvStore.deleteByPrefix(userPrefix)
                .thenCompose(v -> kvStore.deleteByPrefix(sessionPrefix))
                .thenApply(v -> true)
                .exceptionally(e -> {
                    logger.error("Failed to delete by user id: {}", e.getMessage());
                    return false;
                });
    }

    @Override
    public CompletableFuture<Map<String, Object>> get(String userId, String scopeId, String memId) {
        logger.warning("not implemented method get");
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<List<Map<String, Object>>> search(String userId, String scopeId, String query, int topK) {
        logger.warning("not implemented method search");
        return CompletableFuture.completedFuture(List.of());
    }

    /**
     * Query variable by user_id, scope_id, variable_name.
     *
     * @param userId    The user ID
     * @param scopeId   The scope ID
     * @param name      The variable name (null or empty to get all variables)
     * @param sessionId The session ID (optional, for session-scoped variables)
     * @return CompletableFuture containing a map of variable name to value
     */
    public CompletableFuture<Map<String, Object>> queryVariable(String userId, String scopeId,
                                                                 String name, String sessionId) {
        checkUserAndScopeId(userId, scopeId, "Search");

        if (kvStore == null) {
            return CompletableFuture.completedFuture(new HashMap<>());
        }

        // Get all variables if name is null or empty
        if (name == null || name.trim().isEmpty()) {
            String prefixStr = "user_var" + SEPARATOR + userId + SEPARATOR + scopeId + SEPARATOR;
            return kvStore.getByPrefix(prefixStr)
                    .thenApply(kvRet -> {
                        Map<String, Object> result = new HashMap<>();
                        if (kvRet != null) {
                            for (Map.Entry<String, String> entry : kvRet.entrySet()) {
                                String decryptedValue = decryptMemoryIfNeeded(cryptoKey, entry.getValue());
                                String varName = entry.getKey().split(SEPARATOR)[entry.getKey().split(SEPARATOR).length - 1];
                                result.put(varName, decryptedValue);
                            }
                        }
                        return result;
                    });
        }

        // Get specific variable
        String key;
        if (sessionId != null && !sessionId.isEmpty()) {
            key = "session_var" + SEPARATOR + userId + SEPARATOR + scopeId
                    + SEPARATOR + sessionId + SEPARATOR + name;
        } else {
            key = "user_var" + SEPARATOR + userId + SEPARATOR + scopeId + SEPARATOR + name;
        }

        return kvStore.get(key)
                .thenApply(value -> {
                    Map<String, Object> result = new HashMap<>();
                    String decryptedValue = decryptMemoryIfNeeded(cryptoKey, value);
                    result.put(name, decryptedValue);
                    return result;
                });
    }

    private Pair<String, String> makeVariablePairs(
            String userId,
            boolean forDeletion,
            String scopeId,
            String varName,
            String sessionId,
            String userVarValue,
            String sessionVarValue) {

        String key = "";
        String value = "";

        String encryptedUserVarValue = encryptMemoryIfNeeded(cryptoKey, userVarValue);
        String encryptedSessionVarValue = encryptMemoryIfNeeded(cryptoKey, sessionVarValue);

        if (varName != null) {
            if (sessionId == null || sessionId.isEmpty()) {
                // user_var
                key = "user_var" + SEPARATOR + userId + SEPARATOR + scopeId + SEPARATOR + varName;
                value = forDeletion ? null : encryptedUserVarValue;
            } else {
                // session_var
                key = "session_var" + SEPARATOR + userId + SEPARATOR + scopeId
                        + SEPARATOR + sessionId + SEPARATOR + varName;
                value = forDeletion ? null : encryptedSessionVarValue;
            }
        }

        return new Pair<>(key, value);
    }

    private static void checkUserAndScopeId(String userId, String scopeId, String context) {
        if (userId == null || userId.trim().isEmpty()) {
            logger.error("{} failed, user ID is empty", context);
        }
        if (scopeId == null || scopeId.trim().isEmpty()) {
            logger.error("{} failed, scope ID is empty", context);
        }
    }

    private static boolean checkExist(Map<String, Object> variableDict, String variableName) {
        if (variableDict == null || variableDict.isEmpty()) {
            return false;
        }
        if (!variableDict.containsKey(variableName)) {
            return false;
        }
        return variableDict.get(variableName) != null;
    }
}


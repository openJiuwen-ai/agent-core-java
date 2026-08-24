/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.manage.index;

import com.openjiuwen.core.common.logging.events.LogEventType;
import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.store.BaseKVStore;
import com.openjiuwen.core.memory.codec.AesStorageCodec;
import com.openjiuwen.core.memory.common.KvPrefixRegistry;
import com.openjiuwen.core.multitenant.TenantKVStoreKeyResolver;
import com.openjiuwen.core.memory.manage.mem_model.BaseMemoryUnit;
import com.openjiuwen.core.memory.manage.mem_model.MemoryType;
import com.openjiuwen.core.memory.manage.mem_model.VariableUnit;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Manages variable memories backed by a key-value store.
 *
 * <p>Mirrors Python's {@code VariableManager} in
 * {@code openjiuwen/core/memory/manage/index/variable_manager.py}.</p>
 */
public class VariableManager extends BaseMemoryManager {

    public static final String SEPARATOR = "/";
    public static final String USER_VAR_PREFIX = "user_var";
    public static final String SESSION_VAR_PREFIX = "session_var";
    private static final List<String> LEGACY_PREFIXES = List.of();

    private final BaseKVStore kvStore;
    private final byte[] cryptoKey;
    private final AesStorageCodec codec;
    private final String memType;

    public VariableManager(BaseKVStore kvStore, byte[] cryptoKey) {
        this.kvStore = kvStore;
        this.cryptoKey = cryptoKey == null ? new byte[0] : cryptoKey.clone();
        this.codec = new AesStorageCodec(this.cryptoKey);
        this.memType = MemoryType.VARIABLE.getValue();

        KvPrefixRegistry registry = KvPrefixRegistry.getInstance();
        registry.registerCurrent(USER_VAR_PREFIX);
        registry.registerCurrent(SESSION_VAR_PREFIX);
        for (String legacyPrefix : LEGACY_PREFIXES) {
            registry.registerLegacy(legacyPrefix);
        }
    }

    public String getMemType() {
        return memType;
    }

    @Override
    public CompletionStage<List<BaseMemoryUnit>> addMemories(String userId, String scopeId,
                                                             Map<String, List<BaseMemoryUnit>> memories,
                                                             Model llm, Map<String, Object> kwargs) {
        Objects.requireNonNull(memories, "memories");
        List<BaseMemoryUnit> variableMemories = memories.getOrDefault(memType, List.of());
        CompletionStage<Void> stage = CompletableFuture.completedFuture(null);

        for (Map.Entry<String, List<BaseMemoryUnit>> entry : memories.entrySet()) {
            if (!memType.equals(entry.getKey())) {
                continue;
            }
            for (BaseMemoryUnit unit : entry.getValue()) {
                if (!(unit instanceof VariableUnit variableUnit)) {
                    MEMORY_LOGGER.warning(
                            "mem_unit is not a VariableUnit",
                            "event_type", LogEventType.MEMORY_STORE,
                            "memory_type", memType,
                            "user_id", userId,
                            "scope_id", scopeId
                    );
                    continue;
                }
                if (kvStore == null) {
                    MEMORY_LOGGER.error(
                            "kv_store cannot be None",
                            "event_type", LogEventType.MEMORY_STORE,
                            "memory_type", memType,
                            "user_id", userId,
                            "scope_id", scopeId
                    );
                    return CompletableFuture.completedFuture(List.of());
                }
                VariablePair pair = makeVariablePairs(
                        userId,
                        false,
                        scopeId,
                        variableUnit.getVariableName(),
                        null,
                        variableUnit.getVariableMem(),
                        null
                );
                stage = stage.thenCompose(ignored -> kvStore.set(pair.key(), pair.value()));
            }
        }

        return stage.thenApply(ignored -> variableMemories);
    }

    @Override
    public CompletionStage<Boolean> update(String userId, String scopeId, String memId, String newMemory,
                                           Map<String, Object> kwargs) {
        MEMORY_LOGGER.warning(
                "Not implemented method update",
                "event_type", LogEventType.MEMORY_STORE,
                "memory_type", memType,
                "memory_id", Collections.singletonList(memId),
                "user_id", userId,
                "scope_id", scopeId
        );
        return CompletableFuture.completedFuture(null);
    }

    public CompletionStage<Void> updateUserVariable(String userId, String scopeId, String varName, String varMem) {
        if (kvStore == null) {
            MEMORY_LOGGER.error(
                    "KV_store cannot be None",
                    "event_type", LogEventType.MEMORY_STORE,
                    "memory_type", memType,
                    "user_id", userId,
                    "scope_id", scopeId
            );
            return CompletableFuture.completedFuture(null);
        }

        return queryVariable(userId, scopeId, varName, null)
                .thenCompose(existingVariable -> {
                    if (!checkExist(existingVariable, varName)) {
                        return CompletableFuture.completedFuture(null);
                    }
                    VariablePair pair = makeVariablePairs(userId, false, scopeId, varName, null, varMem, null);
                    return kvStore.set(pair.key(), pair.value());
                });
    }

    @Override
    public CompletionStage<Boolean> delete(String userId, String scopeId, String memId, Map<String, Object> kwargs) {
        MEMORY_LOGGER.error(
                "Not implemented method delete",
                "event_type", LogEventType.MEMORY_STORE,
                "memory_id", Collections.singletonList(memId),
                "memory_type", memType,
                "user_id", userId,
                "scope_id", scopeId
        );
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletionStage<Boolean> deleteByUserId(String userId, String scopeId, Map<String, Object> kwargs) {
        if (kvStore == null) {
            MEMORY_LOGGER.error(
                    "kv_store cannot be None",
                    "event_type", LogEventType.MEMORY_STORE,
                    "memory_type", memType,
                    "user_id", userId,
                    "scope_id", scopeId
            );
            return CompletableFuture.completedFuture(null);
        }
        String userPrefix = makeVariablePrefix(USER_VAR_PREFIX, userId, scopeId);
        String sessionPrefix = makeVariablePrefix(SESSION_VAR_PREFIX, userId, scopeId);
        return kvStore.deleteByPrefix(userPrefix, null)
                .thenCompose(ignored -> kvStore.deleteByPrefix(sessionPrefix, null))
                .thenApply(ignored -> null);
    }

    public CompletionStage<Void> deleteUserVariable(String userId, String scopeId, String varName) {
        if (kvStore == null) {
            MEMORY_LOGGER.error(
                    "kv_store cannot be None",
                    "event_type", LogEventType.MEMORY_STORE,
                    "memory_type", memType,
                    "user_id", userId,
                    "scope_id", scopeId
            );
            return CompletableFuture.completedFuture(null);
        }
        VariablePair pair = makeVariablePairs(userId, false, scopeId, varName, null, null, null);
        return kvStore.delete(pair.key());
    }

    @Override
    public CompletionStage<Map<String, Object>> get(String userId, String scopeId, String memId) {
        MEMORY_LOGGER.warning(
                "Not implemented method get",
                "memory_id", Collections.singletonList(memId),
                "event_type", LogEventType.MEMORY_STORE,
                "memory_type", memType,
                "user_id", userId,
                "scope_id", scopeId
        );
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletionStage<List<Map<String, Object>>> search(String userId, String scopeId, String query, int topK,
                                                             Map<String, Object> kwargs) {
        MEMORY_LOGGER.warning(
                "Not implemented method search",
                "event_type", LogEventType.MEMORY_STORE,
                "memory_type", memType,
                "query", query,
                "user_id", userId,
                "scope_id", scopeId
        );
        return CompletableFuture.completedFuture(null);
    }

    /**
     * Query variable by user_id, scope_id and optional variable or session id.
     *
     * <p>Mirrors Python's {@code query_variable(...)} in
     * {@code openjiuwen/core/memory/manage/index/variable_manager.py}.</p>
     */
    public CompletionStage<Map<String, String>> queryVariable(String userId, String scopeId, String name,
                                                              String sessionId) {
        checkUserAndScopeId(userId, scopeId, "Search");
        if (name == null || name.strip().isEmpty()) {
            String prefix = makeVariablePrefix(USER_VAR_PREFIX, userId, scopeId);
            return kvStore.getByPrefix(prefix).thenApply(kvRet -> {
                Map<String, String> result = new LinkedHashMap<>();
                for (Map.Entry<String, Object> entry : kvRet.entrySet()) {
                    String value = codec.decode(asNullableString(entry.getValue()));
                    String variableName = entry.getKey().substring(entry.getKey().lastIndexOf(SEPARATOR) + 1);
                    result.put(variableName, value);
                }
                return result;
            });
        }

        String key = makeVariableKey(userId, scopeId, name, sessionId);

        return kvStore.get(key).thenApply(kvRet -> {
            Map<String, String> result = new LinkedHashMap<>();
            result.put(name, codec.decode(asNullableString(kvRet)));
            return result;
        });
    }

    public CompletionStage<Map<String, String>> queryVariable(String userId, String scopeId, String name) {
        return queryVariable(userId, scopeId, name, null);
    }

    public CompletionStage<Map<String, String>> queryVariable(String userId, String scopeId) {
        return queryVariable(userId, scopeId, null, null);
    }

    VariablePair makeVariablePairs(String usrId, boolean forDeletion, String scopeId, String varName,
                                   String sessionId, String userVarValue, String sessionVarValue) {
        String key = "";
        String value = "";
        String encodedUserVarValue = codec.encode(userVarValue);
        String encodedSessionVarValue = codec.encode(sessionVarValue);
        if (varName != null) {
            if (sessionId == null) {
                key = makeVariableKey(usrId, scopeId, varName, null);
                value = forDeletion ? null : encodedUserVarValue;
            } else {
                key = makeVariableKey(usrId, scopeId, varName, sessionId);
                value = forDeletion ? null : encodedSessionVarValue;
            }
        }
        return new VariablePair(key, value);
    }

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

    private String makeVariablePrefix(String prefix, String userId, String scopeId) {
        String rawPrefix = prefix + SEPARATOR + userId + SEPARATOR + scopeId + SEPARATOR;
        return TenantKVStoreKeyResolver.resolvePrefix(rawPrefix);
    }

    static void checkUserAndScopeId(String userId, String scopeId, String context) {
        if (userId == null || userId.strip().isEmpty()) {
            MEMORY_LOGGER.error(
                    "Check user and scope id operation failed, user ID is empty",
                    "event_type", LogEventType.MEMORY_RETRIEVE,
                    "memory_type", "variable",
                    "user_id", userId,
                    "scope_id", scopeId,
                    "metadata", Map.of("context", context)
            );
        }
        if (scopeId == null || scopeId.strip().isEmpty()) {
            MEMORY_LOGGER.error(
                    "Check user and scope id operation failed, scope ID is empty",
                    "event_type", LogEventType.MEMORY_RETRIEVE,
                    "memory_type", "variable",
                    "user_id", userId,
                    "scope_id", scopeId,
                    "metadata", Map.of("context", context)
            );
        }
    }

    static boolean checkExist(Map<String, String> variableDict, String variableName) {
        if (variableDict == null || variableDict.isEmpty()) {
            return false;
        }
        if (!variableDict.containsKey(variableName)) {
            return false;
        }
        String value = variableDict.get(variableName);
        return value != null && !value.isEmpty();
    }

    private static String asNullableString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    record VariablePair(String key, String value) {
    }
}

/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.manage.mem_model;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.common.logging.events.LogEventType;
import com.openjiuwen.core.foundation.store.BaseKVStore;
import com.openjiuwen.core.memory.common.KvPrefixRegistry;
import com.openjiuwen.core.multitenant.TenantKVStoreKeyResolver;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * Mirrors Python's {@code UserMemStore} in
 * {@code openjiuwen/core/memory/manage/mem_model/user_mem_store.py}.
 */
public class UserMemStore {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<LinkedHashMap<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    public static final int BYTE_NUM_PER_ID = 24;
    public static final String IDS_STR = "ids";
    public static final String USER_PROFILE_TOPIC_STR = "UPT";
    public static final String KEY_PREFIX_STR = "UMD";
    public static final List<String> LEGACY_PREFIXES = List.of();
    public static final String MEM_TYPE_FIELD_KEY = "mem_type";
    public static final List<String> FRAGMENT_MEMORY_TYPE = List.of(
            MemoryType.USER_PROFILE.getValue(),
            MemoryType.SEMANTIC_MEMORY.getValue(),
            MemoryType.EPISODIC_MEMORY.getValue()
    );
    public static final String SEPARATOR = "/";

    private final BaseKVStore kvStore;

    public UserMemStore(BaseKVStore kvStoreInstance) {
        if (kvStoreInstance == null) {
            throw ErrorHelper.buildError(
                    StatusCode.MEMORY_STORE_INIT_FAILED,
                    "store_type",
                    "user mem store",
                    "error_msg",
                    "kv store instance is None in UserMemStore"
            );
        }
        this.kvStore = kvStoreInstance;
        KvPrefixRegistry.getInstance().registerCurrent(KEY_PREFIX_STR);
        for (String legacyPrefix : LEGACY_PREFIXES) {
            KvPrefixRegistry.getInstance().registerLegacy(legacyPrefix);
        }
    }

    public CompletableFuture<Boolean> write(String userId, String scopeId, String memId, Map<String, Object> data) {
        if (data == null || data.isEmpty()) {
            Loggers.MEMORY.error(
                    "Write failed, because data is empty. event_type={} memory_id={} user_id={} scope_id={}",
                    LogEventType.MEMORY_STORE.getValue(),
                    memId,
                    userId,
                    scopeId
            );
            return CompletableFuture.completedFuture(false);
        }

        String userMemKey = getUserMemKey(userId, scopeId, memId);
        return kvStore.exists(userMemKey).thenCompose(exists -> {
            if (exists) {
                Loggers.MEMORY.error(
                        "Write failed, user memory already exists. event_type={} memory_id={} user_id={} scope_id={}",
                        LogEventType.MEMORY_STORE.getValue(),
                        memId,
                        userId,
                        scopeId
                );
                return CompletableFuture.completedFuture(false);
            }

            return kvStore.set(userMemKey, toJson(data))
                    .thenCompose(ignored -> appendIdsForWrite(userId, scopeId, memId, data))
                    .thenApply(ignored -> true);
        });
    }

    public CompletableFuture<Boolean> update(String userId, String scopeId, String memId, Map<String, Object> data) {
        String userMemKey = getUserMemKey(userId, scopeId, memId);
        return kvStore.exists(userMemKey).thenCompose(exists -> {
            if (!exists) {
                Loggers.MEMORY.error(
                        "Update failed, user memory does not exists. event_type={} memory_id={} user_id={} scope_id={}",
                        LogEventType.MEMORY_UPDATE.getValue(),
                        memId,
                        userId,
                        scopeId
                );
                return CompletableFuture.completedFuture(false);
            }

            return kvStore.get(userMemKey).thenCompose(oldData -> {
                String oldDataText = readStringValue(oldData);
                if (oldDataText == null || oldDataText.isEmpty()) {
                    return kvStore.set(userMemKey, toJson(data)).thenApply(ignored -> true);
                }

                Map<String, Object> merged = fromJson(oldDataText);
                if (data != null) {
                    merged.putAll(data);
                }
                return kvStore.set(userMemKey, toJson(merged)).thenApply(ignored -> true);
            });
        });
    }

    public CompletableFuture<Void> delete(String userId, String scopeId, String memId) {
        return innerDelete(userId, scopeId, memId);
    }

    public CompletableFuture<Void> batchDelete(String userId, String scopeId, List<String> memIds) {
        CompletableFuture<Void> chain = CompletableFuture.completedFuture(null);
        for (String memId : memIds) {
            chain = chain.thenCompose(ignored -> innerDelete(userId, scopeId, memId));
        }
        return chain;
    }

    public CompletableFuture<Map<String, Object>> get(String userId, String scopeId, String memId) {
        return get(getUserMemKey(userId, scopeId, memId));
    }

    public CompletableFuture<List<Map<String, Object>>> batchGet(String userId, String scopeId, List<String> memIds) {
        List<String> keys = new ArrayList<>(memIds.size());
        for (String memId : memIds) {
            keys.add(getUserMemKey(userId, scopeId, memId));
        }
        return kvStore.mget(keys).thenApply(values -> {
            if (values == null || values.isEmpty()) {
                return List.of();
            }
            List<Map<String, Object>> result = new ArrayList<>();
            for (Object value : values) {
                String text = readStringValue(value);
                if (text != null) {
                    result.add(fromJson(text));
                }
            }
            return result;
        });
    }

    public CompletableFuture<List<Map<String, Object>>> getAll(String userId, String scopeId) {
        return getAll(userId, scopeId, null);
    }

    public CompletableFuture<List<Map<String, Object>>> getAll(String userId, String scopeId, String memType) {
        String userIdsKey = getUserIdsKey(userId, scopeId, memType);
        return kvStore.exists(userIdsKey).thenCompose(exists -> {
            if (!exists) {
                return CompletableFuture.completedFuture(null);
            }
            return kvStore.get(userIdsKey).thenCompose(userIdsValue -> {
                String idsValue = readStringValue(userIdsValue);
                if (idsValue == null || idsValue.isEmpty()) {
                    return CompletableFuture.completedFuture(null);
                }
                return batchGet(userId, scopeId, getAllIds(idsValue));
            });
        });
    }

    public CompletableFuture<List<Map<String, Object>>> getByTopic(String userId, String scopeId, String topic) {
        String userMemTopicKey = getConcatenationKey(List.of(userId, scopeId, USER_PROFILE_TOPIC_STR, topic, IDS_STR));
        return kvStore.exists(userMemTopicKey).thenCompose(exists -> {
            if (!exists) {
                return CompletableFuture.completedFuture(null);
            }
            return kvStore.get(userMemTopicKey).thenCompose(value -> {
                String topicValue = readStringValue(value);
                if (topicValue == null || topicValue.isEmpty()) {
                    return CompletableFuture.completedFuture(null);
                }
                return batchGet(userId, scopeId, getAllIds(topicValue));
            });
        });
    }

    public CompletableFuture<List<Map<String, Object>>> getInRange(
            String userId,
            String scopeId,
            int startIdx,
            int endIdx
    ) {
        return getInRange(userId, scopeId, startIdx, endIdx, null);
    }

    public CompletableFuture<List<Map<String, Object>>> getInRange(
            String userId,
            String scopeId,
            int startIdx,
            int endIdx,
            String memType
    ) {
        String userIdsKey = getUserIdsKey(userId, scopeId, memType);
        return kvStore.exists(userIdsKey).thenCompose(exists -> {
            if (!exists) {
                return CompletableFuture.completedFuture(null);
            }
            return kvStore.get(userIdsKey).thenCompose(userIdsValue -> {
                String idsValue = readStringValue(userIdsValue);
                if (idsValue == null || idsValue.isEmpty()) {
                    return CompletableFuture.completedFuture(null);
                }
                return batchGet(userId, scopeId, getIdsInRange(idsValue, startIdx, endIdx));
            });
        });
    }

    private CompletableFuture<Void> appendIdsForWrite(
            String userId,
            String scopeId,
            String memId,
            Map<String, Object> data
    ) {
        CompletableFuture<Void> chain = CompletableFuture.completedFuture(null);

        if (data.containsKey(MEM_TYPE_FIELD_KEY)) {
            String memType = String.valueOf(data.get(MEM_TYPE_FIELD_KEY));
            String userMemIdsKey = getUserIdsKey(userId, scopeId, memType);
            chain = chain.thenCompose(ignored -> kvStore.get(userMemIdsKey)).thenCompose(userMemIdsValue -> {
                String idsValue = readStringValue(userMemIdsValue);
                return kvStore.set(userMemIdsKey, writeId(idsValue == null ? "" : idsValue, memId));
            });

            if (FRAGMENT_MEMORY_TYPE.contains(memType)) {
                String userMemTopicKey = getConcatenationKey(List.of(userId, scopeId, USER_PROFILE_TOPIC_STR, IDS_STR));
                chain = chain.thenCompose(ignored -> kvStore.get(userMemTopicKey)).thenCompose(userMemTopicValue -> {
                    String topicValue = readStringValue(userMemTopicValue);
                    return kvStore.set(userMemTopicKey, writeId(topicValue == null ? "" : topicValue, memId));
                });
            }
        }

        String userIdsKey = getUserIdsKey(userId, scopeId, null);
        return chain.thenCompose(ignored -> kvStore.get(userIdsKey)).thenCompose(userIdsValue -> {
            String idsValue = readStringValue(userIdsValue);
            return kvStore.set(userIdsKey, writeId(idsValue == null ? "" : idsValue, memId));
        });
    }

    private String getUserIdsKey(String userId, String scopeId, String memType) {
        if (memType == null) {
            return getConcatenationKey(List.of(userId, scopeId, IDS_STR));
        }
        return getConcatenationKey(List.of(userId, scopeId, memType, IDS_STR));
    }

    private String getUserMemKey(String userId, String scopeId, String memId) {
        return getConcatenationKey(List.of(userId, scopeId, memId));
    }

    private String getConcatenationKey(List<String> fields) {
        StringBuilder key = new StringBuilder(KEY_PREFIX_STR);
        for (String field : fields) {
            key.append(SEPARATOR).append(field);
        }
        return TenantKVStoreKeyResolver.resolveKey(key.toString());
    }

    private CompletableFuture<Void> innerDelete(String userId, String scopeId, String memId) {
        String userMemKey = getUserMemKey(userId, scopeId, memId);
        return kvStore.exists(userMemKey).thenCompose(exists -> {
            if (!exists) {
                Loggers.MEMORY.warning(
                        "Delete failed, user memory does not exists. event_type={} memory_id={} user_id={} scope_id={}",
                        LogEventType.MEMORY_STORE.getValue(),
                        memId,
                        userId,
                        scopeId
                );
                return CompletableFuture.completedFuture(null);
            }

            return kvStore.get(userMemKey).thenCompose(data -> {
                String dataText = readStringValue(data);
                CompletableFuture<Void> chain = CompletableFuture.completedFuture(null);
                if (dataText != null && !dataText.isEmpty()) {
                    Map<String, Object> dictValue = fromJson(dataText);
                    if (dictValue.containsKey(MEM_TYPE_FIELD_KEY)) {
                        String memType = String.valueOf(dictValue.get(MEM_TYPE_FIELD_KEY));
                        String userMemIdsKey = getUserIdsKey(userId, scopeId, memType);
                        chain = chain.thenCompose(ignored -> deleteMemId(userMemIdsKey, memId));

                        if (FRAGMENT_MEMORY_TYPE.contains(memType)) {
                            String userMemTopicKey =
                                    getConcatenationKey(List.of(userId, scopeId, USER_PROFILE_TOPIC_STR, IDS_STR));
                            chain = chain.thenCompose(ignored -> deleteMemId(userMemTopicKey, memId));
                        }
                    }
                }

                String userIdsKey = getUserIdsKey(userId, scopeId, null);
                return chain.thenCompose(ignored -> deleteMemId(userIdsKey, memId))
                        .thenCompose(ignored -> kvStore.delete(userMemKey));
            });
        });
    }

    private CompletableFuture<Void> deleteMemId(String idsKey, String memId) {
        return kvStore.exists(idsKey).thenCompose(exists -> {
            if (!exists) {
                return CompletableFuture.completedFuture(null);
            }
            return kvStore.get(idsKey).thenCompose(idsValue -> {
                String idsText = readStringValue(idsValue);
                String newIdsValue = deleteIdByValue(idsText == null ? "" : idsText, memId);
                if (!newIdsValue.isEmpty()) {
                    return kvStore.set(idsKey, newIdsValue);
                }
                return kvStore.delete(idsKey);
            });
        });
    }

    private CompletableFuture<Map<String, Object>> get(String memKey) {
        return kvStore.get(memKey).thenApply(memValue -> {
            String text = readStringValue(memValue);
            if (text == null || text.isEmpty()) {
                return null;
            }
            return fromJson(text);
        });
    }

    private static String writeId(String dataList, String num) {
        return dataList + num;
    }

    private String deleteIdByValue(String dataList, String idStr) {
        int total = dataList.length() / BYTE_NUM_PER_ID;
        for (int index = 0; index < total; index++) {
            String chunk = dataList.substring(index * BYTE_NUM_PER_ID, (index + 1) * BYTE_NUM_PER_ID);
            if (Objects.equals(chunk, idStr)) {
                return dataList.substring(0, index * BYTE_NUM_PER_ID)
                        + dataList.substring((index + 1) * BYTE_NUM_PER_ID);
            }
        }
        return dataList;
    }

    private List<String> getAllIds(String dataList) {
        int total = dataList.length() / BYTE_NUM_PER_ID;
        List<String> ids = new ArrayList<>(total);
        for (int index = 0; index < total; index++) {
            ids.add(dataList.substring(index * BYTE_NUM_PER_ID, (index + 1) * BYTE_NUM_PER_ID));
        }
        return ids;
    }

    private List<String> getIdsInRange(String dataList, int startIdx, int endIdx) {
        int total = dataList.length() / BYTE_NUM_PER_ID;
        int safeStart = Math.max(startIdx, 0);
        int safeEnd = Math.min(endIdx, total);
        if (safeStart >= safeEnd) {
            return List.of();
        }

        List<String> ids = new ArrayList<>(safeEnd - safeStart);
        for (int index = safeStart; index < safeEnd; index++) {
            ids.add(dataList.substring(index * BYTE_NUM_PER_ID, (index + 1) * BYTE_NUM_PER_ID));
        }
        return ids;
    }

    private static String readStringValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof byte[] bytes) {
            return new String(bytes, StandardCharsets.UTF_8);
        }
        return String.valueOf(value);
    }

    private static String toJson(Map<String, Object> data) {
        try {
            return MAPPER.writeValueAsString(data);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize user memory data", exception);
        }
    }

    private static Map<String, Object> fromJson(String json) {
        try {
            return MAPPER.readValue(json, MAP_TYPE);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to deserialize user memory data", exception);
        }
    }
}

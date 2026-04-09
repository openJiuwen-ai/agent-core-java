/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.manage.mem_model;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.common.logging.LoggerProtocol;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.common.logging.events.LogEventType;
import com.openjiuwen.core.memory.common.KvPrefixRegistry;
import com.openjiuwen.spi.store.BaseKVStore;

import java.util.*;

/**
 * KV-based memory data storage with ID index management.
 */
public class UserMemStore {

    private static final LoggerProtocol MEMORY_LOGGER = Loggers.MEMORY;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static final int BYTE_NUM_PER_ID = 24;
    public static final String IDS_STR = "ids";
    public static final String USER_PROFILE_TOPIC_STR = "UPT";
    public static final String KEY_PREFIX_STR = "UMD";
    public static final String MEM_TYPE_FIELD_KEY = "mem_type";
    public static final String TOPIC_FIELD_KEY = "profile_type";
    public static final String SEPARATOR = "/";

    private final BaseKVStore kvStore;

    public UserMemStore(BaseKVStore kvStore) {
        if (kvStore == null) {
            throw ErrorHelper.buildError(
                    StatusCode.MEMORY_STORE_INIT_FAILED,
                    "store_type", "user mem store",
                    "error_msg", "kv store instance is None in UserMemStore"
            );
        }
        this.kvStore = kvStore;
        KvPrefixRegistry.getInstance().registerCurrent(KEY_PREFIX_STR);
    }

    public boolean write(String userId, String scopeId, String memId, Map<String, Object> data) {
        if (data == null || data.isEmpty()) {
            MEMORY_LOGGER.error("[{}] Write failed, because data is empty. memId={}", LogEventType.MEMORY_STORE, memId);
            return false;
        }
        String userMemKey = getUserMemKey(userId, scopeId, memId);
        if (kvStore.exists(userMemKey)) {
            MEMORY_LOGGER.error("[{}] Write failed, user memory already exists. memId={}", LogEventType.MEMORY_STORE, memId);
            return false;
        }

        kvStore.set(userMemKey, toJson(data));

        // Append id to mem_type ids
        if (data.containsKey(MEM_TYPE_FIELD_KEY)) {
            String memType = String.valueOf(data.get(MEM_TYPE_FIELD_KEY));
            String userMemIdsKey = getUserIdsKey(userId, scopeId, memType);
            String userMemIdsValue = getStringOrDefault(userMemIdsKey, "");
            kvStore.set(userMemIdsKey, writeId(userMemIdsValue, memId));

            // user profile topic ids
            if (MemoryType.FRAGMENT_MEMORY.getValue().equals(memType)
                    && data.containsKey(TOPIC_FIELD_KEY)
                    && data.get(TOPIC_FIELD_KEY) != null) {
                String topic = String.valueOf(data.get(TOPIC_FIELD_KEY));
                String topicKey = getConcatenationKey(Arrays.asList(userId, scopeId, USER_PROFILE_TOPIC_STR, topic, IDS_STR));
                String topicValue = getStringOrDefault(topicKey, "");
                kvStore.set(topicKey, writeId(topicValue, memId));
            }
        }

        // Append id to user ids
        String userIdsKey = getUserIdsKey(userId, scopeId, null);
        String userIdsValue = getStringOrDefault(userIdsKey, "");
        kvStore.set(userIdsKey, writeId(userIdsValue, memId));
        return true;
    }

    public boolean update(String userId, String scopeId, String memId, Map<String, Object> data) {
        String userMemKey = getUserMemKey(userId, scopeId, memId);
        if (!kvStore.exists(userMemKey)) {
            MEMORY_LOGGER.error("[{}] Update failed, user memory does not exist. memId={}", LogEventType.MEMORY_UPDATE, memId);
            return false;
        }
        String oldData = getStringOrDefault(userMemKey, "");
        if (oldData.isEmpty()) {
            kvStore.set(userMemKey, toJson(data));
            return true;
        }
        Map<String, Object> dictValue = fromJson(oldData);
        dictValue.putAll(data);
        kvStore.set(userMemKey, toJson(dictValue));
        return true;
    }

    public void delete(String userId, String scopeId, String memId) {
        innerDelete(userId, scopeId, memId);
    }

    public void batchDelete(String userId, String scopeId, List<String> memIds) {
        for (String memId : memIds) {
            innerDelete(userId, scopeId, memId);
        }
    }

    public Map<String, Object> get(String userId, String scopeId, String memId) {
        String userMemKey = getUserMemKey(userId, scopeId, memId);
        return getMap(userMemKey);
    }

    public List<Map<String, Object>> batchGet(String userId, String scopeId, List<String> memIds) {
        List<String> keys = new ArrayList<>();
        for (String memId : memIds) {
            keys.add(getUserMemKey(userId, scopeId, memId));
        }
        List<Object> values = kvStore.mget(keys);
        if (values == null) {
            return Collections.emptyList();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object val : values) {
            if (val != null) {
                result.add(fromJson(String.valueOf(val)));
            }
        }
        return result;
    }

    public List<Map<String, Object>> getAll(String userId, String scopeId, String memType) {
        String userIdsKey = getUserIdsKey(userId, scopeId, memType);
        if (!kvStore.exists(userIdsKey)) {
            return null;
        }
        String userIdsValue = getStringOrDefault(userIdsKey, "");
        if (userIdsValue.isEmpty()) {
            return null;
        }
        List<String> allIds = getAllIds(userIdsValue);
        return batchGet(userId, scopeId, allIds);
    }

    public List<Map<String, Object>> getByTopic(String userId, String scopeId, String topic) {
        String topicKey = getConcatenationKey(Arrays.asList(userId, scopeId, USER_PROFILE_TOPIC_STR, topic, IDS_STR));
        if (!kvStore.exists(topicKey)) {
            return null;
        }
        String topicValue = getStringOrDefault(topicKey, "");
        if (topicValue.isEmpty()) {
            return null;
        }
        List<String> allIds = getAllIds(topicValue);
        return batchGet(userId, scopeId, allIds);
    }

    public List<Map<String, Object>> getInRange(String userId, String scopeId,
                                                  int startIdx, int endIdx, String memType) {
        String userIdsKey = getUserIdsKey(userId, scopeId, memType);
        if (!kvStore.exists(userIdsKey)) {
            return null;
        }
        String userIdsValue = getStringOrDefault(userIdsKey, "");
        if (userIdsValue.isEmpty()) {
            return null;
        }
        List<String> memIds = getIdsInRange(userIdsValue, startIdx, endIdx);
        return batchGet(userId, scopeId, memIds);
    }

    // ---- Private Helpers ----

    private String getUserIdsKey(String userId, String scopeId, String memType) {
        if (memType == null) {
            return getConcatenationKey(Arrays.asList(userId, scopeId, IDS_STR));
        } else {
            return getConcatenationKey(Arrays.asList(userId, scopeId, memType, IDS_STR));
        }
    }

    private String getUserMemKey(String userId, String scopeId, String memId) {
        return getConcatenationKey(Arrays.asList(userId, scopeId, memId));
    }

    private String getConcatenationKey(List<String> fields) {
        StringBuilder keyStr = new StringBuilder(KEY_PREFIX_STR);
        for (String field : fields) {
            keyStr.append(SEPARATOR).append(field);
        }
        return keyStr.toString();
    }

    private void innerDelete(String userId, String scopeId, String memId) {
        String userMemKey = getUserMemKey(userId, scopeId, memId);
        if (!kvStore.exists(userMemKey)) {
            MEMORY_LOGGER.warn("[{}] Delete failed, user memory does not exist. memId={}", LogEventType.MEMORY_STORE, memId);
            return;
        }
        String dataStr = getStringOrDefault(userMemKey, "");
        if (!dataStr.isEmpty()) {
            Map<String, Object> dictValue = fromJson(dataStr);
            if (dictValue.containsKey(MEM_TYPE_FIELD_KEY)) {
                String memType = String.valueOf(dictValue.get(MEM_TYPE_FIELD_KEY));
                String userMemIdsKey = getUserIdsKey(userId, scopeId, memType);
                deleteMemId(userMemIdsKey, memId);

                if (MemoryType.FRAGMENT_MEMORY.getValue().equals(memType)
                        && dictValue.containsKey(TOPIC_FIELD_KEY)
                        && dictValue.get(TOPIC_FIELD_KEY) != null) {
                    String topic = String.valueOf(dictValue.get(TOPIC_FIELD_KEY));
                    String topicKey = getConcatenationKey(Arrays.asList(userId, scopeId, USER_PROFILE_TOPIC_STR, topic, IDS_STR));
                    deleteMemId(topicKey, memId);
                }
            }
        }

        // Delete user ids
        String userIdsKey = getUserIdsKey(userId, scopeId, null);
        deleteMemId(userIdsKey, memId);

        // Delete user mem
        kvStore.delete(userMemKey);
    }

    private void deleteMemId(String idsKey, String memId) {
        String idsValue = getStringOrDefault(idsKey, "");
        if (idsValue.isEmpty()) {
            return;
        }
        // Remove the memId from the concatenated IDs string
        int idLen = BYTE_NUM_PER_ID;
        StringBuilder newIds = new StringBuilder();
        for (int i = 0; i + idLen <= idsValue.length(); i += idLen) {
            String id = idsValue.substring(i, i + idLen);
            if (!id.equals(memId)) {
                newIds.append(id);
            }
        }
        if (newIds.isEmpty()) {
            kvStore.delete(idsKey);
            return;
        }
        kvStore.set(idsKey, newIds.toString());
    }

    private String writeId(String existingIds, String newId) {
        return existingIds + newId;
    }

    private List<String> getAllIds(String idsValue) {
        List<String> ids = new ArrayList<>();
        int idLen = BYTE_NUM_PER_ID;
        for (int i = 0; i + idLen <= idsValue.length(); i += idLen) {
            ids.add(idsValue.substring(i, i + idLen));
        }
        return ids;
    }

    private List<String> getIdsInRange(String idsValue, int startIdx, int endIdx) {
        List<String> allIds = getAllIds(idsValue);
        int start = Math.max(0, startIdx);
        int end = Math.min(allIds.size(), endIdx);
        if (start >= end) {
            return Collections.emptyList();
        }
        return allIds.subList(start, end);
    }

    private String getStringOrDefault(String key, String defaultValue) {
        Object val = kvStore.get(key);
        return val != null ? String.valueOf(val) : defaultValue;
    }

    private Map<String, Object> getMap(String key) {
        Object val = kvStore.get(key);
        if (val == null) {
            return null;
        }
        return fromJson(String.valueOf(val));
    }

    private String toJson(Map<String, Object> data) {
        try {
            return MAPPER.writeValueAsString(data);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize to JSON", e);
        }
    }

    private Map<String, Object> fromJson(String json) {
        try {
            return MAPPER.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to deserialize from JSON", e);
        }
    }
}

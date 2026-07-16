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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * KV-based memory data storage with ID index management.
 * 
 * @since 0.1.7
 */
public class UserMemStore {
    private static final LoggerProtocol MEMORY_LOGGER = Loggers.MEMORY;

    /**
     * ObjectMapper.
     * 
     * @since 0.1.7
     */
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * BYTE_NUM_PER_ID.
     * 
     * @since 0.1.7
     */
    public static final int BYTE_NUM_PER_ID = 24;

    /**
     * IDS_STR.
     * 
     * @since 0.1.7
     */
    public static final String IDS_STR = "ids";

    /**
     * USER_PROFILE_TOPIC_STR.
     * 
     * @since 0.1.7
     */
    public static final String USER_PROFILE_TOPIC_STR = "UPT";

    /**
     * KEY_PREFIX_STR.
     * 
     * @since 0.1.7
     */
    public static final String KEY_PREFIX_STR = "UMD";

    /**
     * MEM_TYPE_FIELD_KEY.
     * 
     * @since 0.1.7
     */
    public static final String MEM_TYPE_FIELD_KEY = "mem_type";

    /**
     * TOPIC_FIELD_KEY.
     * 
     * @since 0.1.7
     */
    public static final String TOPIC_FIELD_KEY = "profile_type";

    /**
     * SEPARATOR.
     * 
     * @since 0.1.7
     */
    public static final String SEPARATOR = "/";

    /**
     * FRAGMENT_MEMORY_TYPES.
     * 
     * @since 0.1.7
     */
    public static final List<String> FRAGMENT_MEMORY_TYPES = List.of(MemoryType.USER_PROFILE.getValue(),
            MemoryType.SEMANTIC_MEMORY.getValue(), MemoryType.EPISODIC_MEMORY.getValue());

    private final BaseKVStore kvStore;

    /**
     * UserMemStore.
     * 
     * @param kvStore kvStore
     * @since 0.1.7
     */
    public UserMemStore(BaseKVStore kvStore) {
        if (kvStore == null) {
            throw ErrorHelper.buildError(StatusCode.MEMORY_STORE_INIT_FAILED, "store_type", "user mem store",
                    "error_msg", "kv store instance is None in UserMemStore");
        }
        this.kvStore = kvStore;
        KvPrefixRegistry.getInstance().registerCurrent(KEY_PREFIX_STR);
    }

    /**
     * write.
     * 
     * @param userId userId
     * @param scopeId scopeId
     * @param memId memId
     * @param data data
     * @return the result
     * @since 0.1.7
     */
    public boolean write(String userId, String scopeId, String memId, Map<String, Object> data) {
        if (data == null || data.isEmpty()) {
            MEMORY_LOGGER.error("[{}] Write failed, because data is empty. memId={}", LogEventType.MEMORY_STORE, memId);
            return false;
        }
        String userMemKey = getUserMemKey(userId, scopeId, memId);
        if (kvStore.isExists(userMemKey)) {
            MEMORY_LOGGER.error("[{}] Write failed, user memory already exists. memId={}", LogEventType.MEMORY_STORE,
                    memId);
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
            if (FRAGMENT_MEMORY_TYPES.contains(memType)) {
                String topicKey = getUserProfileTopicIdsKey(userId, scopeId);
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

    /**
     * update.
     * 
     * @param userId userId
     * @param scopeId scopeId
     * @param memId memId
     * @param data data
     * @return the result
     * @since 0.1.7
     */
    public boolean update(String userId, String scopeId, String memId, Map<String, Object> data) {
        String userMemKey = getUserMemKey(userId, scopeId, memId);
        if (!kvStore.isExists(userMemKey)) {
            MEMORY_LOGGER.error("[{}] Update failed, user memory does not exist. memId={}", LogEventType.MEMORY_UPDATE,
                    memId);
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

    /**
     * delete.
     * 
     * @param userId userId
     * @param scopeId scopeId
     * @param memId memId
     * @since 0.1.7
     */
    public void delete(String userId, String scopeId, String memId) {
        innerDelete(userId, scopeId, memId);
    }

    /**
     * batchDelete.
     * 
     * @param userId userId
     * @param scopeId scopeId
     * @param memIds memIds
     * @since 0.1.7
     */
    public void batchDelete(String userId, String scopeId, List<String> memIds) {
        for (String memId : memIds) {
            innerDelete(userId, scopeId, memId);
        }
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
    public Map<String, Object> get(String userId, String scopeId, String memId) {
        String userMemKey = getUserMemKey(userId, scopeId, memId);
        return getMap(userMemKey);
    }

    /**
     * batchGet.
     * 
     * @param userId userId
     * @param scopeId scopeId
     * @param memIds memIds
     * @return the result
     * @since 0.1.7
     */
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

    /**
     * getAll.
     * 
     * @param userId userId
     * @param scopeId scopeId
     * @param memType memType
     * @return the result
     * @since 0.1.7
     */
    public List<Map<String, Object>> getAll(String userId, String scopeId, String memType) {
        String userIdsKey = getUserIdsKey(userId, scopeId, memType);
        if (!kvStore.isExists(userIdsKey)) {
            return null;
        }
        String userIdsValue = getStringOrDefault(userIdsKey, "");
        if (userIdsValue.isEmpty()) {
            return null;
        }
        List<String> allIds = getAllIds(userIdsValue);
        return batchGet(userId, scopeId, allIds);
    }

    /**
     * getByTopic.
     * 
     * @param userId userId
     * @param scopeId scopeId
     * @param topic topic
     * @return the result
     * @since 0.1.7
     */
    public List<Map<String, Object>> getByTopic(String userId, String scopeId, String topic) {
        String topicKey = getConcatenationKey(Arrays.asList(userId, scopeId, USER_PROFILE_TOPIC_STR, topic, IDS_STR));
        if (!kvStore.isExists(topicKey)) {
            return null;
        }
        String topicValue = getStringOrDefault(topicKey, "");
        if (topicValue.isEmpty()) {
            return null;
        }
        List<String> allIds = getAllIds(topicValue);
        return batchGet(userId, scopeId, allIds);
    }

    /**
     * getInRange.
     * 
     * @param userId userId
     * @param scopeId scopeId
     * @param startIdx startIdx
     * @param endIdx endIdx
     * @param memType memType
     * @return the result
     * @since 0.1.7
     */
    public List<Map<String, Object>> getInRange(String userId, String scopeId, int startIdx, int endIdx,
            String memType) {
        String userIdsKey = getUserIdsKey(userId, scopeId, memType);
        if (!kvStore.isExists(userIdsKey)) {
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

    /**
     * getUserIdsKey.
     * 
     * @param userId userId
     * @param scopeId scopeId
     * @param memType memType
     * @return the result
     * @since 0.1.7
     */
    private String getUserIdsKey(String userId, String scopeId, String memType) {
        if (memType == null) {
            return getConcatenationKey(Arrays.asList(userId, scopeId, IDS_STR));
        } else {
            return getConcatenationKey(Arrays.asList(userId, scopeId, memType, IDS_STR));
        }
    }

    /**
     * getUserMemKey.
     * 
     * @param userId userId
     * @param scopeId scopeId
     * @param memId memId
     * @return the result
     * @since 0.1.7
     */
    private String getUserMemKey(String userId, String scopeId, String memId) {
        return getConcatenationKey(Arrays.asList(userId, scopeId, memId));
    }

    /**
     * getUserProfileTopicIdsKey.
     * 
     * @param userId userId
     * @param scopeId scopeId
     * @return the result
     * @since 0.1.7
     */
    private String getUserProfileTopicIdsKey(String userId, String scopeId) {
        return getConcatenationKey(Arrays.asList(userId, scopeId, USER_PROFILE_TOPIC_STR, IDS_STR));
    }

    /**
     * getConcatenationKey.
     * 
     * @param fields fields
     * @return the result
     * @since 0.1.7
     */
    private String getConcatenationKey(List<String> fields) {
        StringBuilder keyStr = new StringBuilder(KEY_PREFIX_STR);
        for (String field : fields) {
            keyStr.append(SEPARATOR).append(field);
        }
        return keyStr.toString();
    }

    /**
     * innerDelete.
     * 
     * @param userId userId
     * @param scopeId scopeId
     * @param memId memId
     * @since 0.1.7
     */
    private void innerDelete(String userId, String scopeId, String memId) {
        String userMemKey = getUserMemKey(userId, scopeId, memId);
        if (!kvStore.isExists(userMemKey)) {
            MEMORY_LOGGER.warn("[{}] Delete failed, user memory does not exist. memId={}", LogEventType.MEMORY_STORE,
                    memId);
            return;
        }
        String dataStr = getStringOrDefault(userMemKey, "");
        if (!dataStr.isEmpty()) {
            Map<String, Object> dictValue = fromJson(dataStr);
            if (dictValue.containsKey(MEM_TYPE_FIELD_KEY)) {
                String memType = String.valueOf(dictValue.get(MEM_TYPE_FIELD_KEY));
                String userMemIdsKey = getUserIdsKey(userId, scopeId, memType);
                deleteMemId(userMemIdsKey, memId);

                if (FRAGMENT_MEMORY_TYPES.contains(memType)) {
                    String topicKey = getUserProfileTopicIdsKey(userId, scopeId);
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

    /**
     * deleteMemId.
     * 
     * @param idsKey idsKey
     * @param memId memId
     * @since 0.1.7
     */
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

    /**
     * writeId.
     * 
     * @param existingIds existingIds
     * @param newId newId
     * @return the result
     * @since 0.1.7
     */
    private String writeId(String existingIds, String newId) {
        return existingIds + newId;
    }

    /**
     * getAllIds.
     * 
     * @param idsValue idsValue
     * @return the result
     * @since 0.1.7
     */
    private List<String> getAllIds(String idsValue) {
        List<String> ids = new ArrayList<>();
        int idLen = BYTE_NUM_PER_ID;
        for (int i = 0; i + idLen <= idsValue.length(); i += idLen) {
            ids.add(idsValue.substring(i, i + idLen));
        }
        return ids;
    }

    /**
     * getIdsInRange.
     * 
     * @param idsValue idsValue
     * @param startIdx startIdx
     * @param endIdx endIdx
     * @return the result
     * @since 0.1.7
     */
    private List<String> getIdsInRange(String idsValue, int startIdx, int endIdx) {
        List<String> allIds = getAllIds(idsValue);
        int start = Math.max(0, startIdx);
        int end = Math.min(allIds.size(), endIdx);
        if (start >= end) {
            return Collections.emptyList();
        }
        return allIds.subList(start, end);
    }

    /**
     * getStringOrDefault.
     * 
     * @param key key
     * @param defaultValue defaultValue
     * @return the result
     * @since 0.1.7
     */
    private String getStringOrDefault(String key, String defaultValue) {
        Object val = kvStore.get(key);
        return val != null ? String.valueOf(val) : defaultValue;
    }

    /**
     * getMap.
     * 
     * @param key key
     * @return the result
     * @since 0.1.7
     */
    private Map<String, Object> getMap(String key) {
        Object val = kvStore.get(key);
        if (val == null) {
            return null;
        }
        return fromJson(String.valueOf(val));
    }

    /**
     * toJson.
     * 
     * @param data data
     * @return the result
     * @since 0.1.7
     */
    private String toJson(Map<String, Object> data) {
        try {
            return MAPPER.writeValueAsString(data);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize to JSON", e);
        }
    }

    /**
     * fromJson.
     * 
     * @param json json
     * @return the result
     * @since 0.1.7
     */
    private Map<String, Object> fromJson(String json) {
        try {
            return MAPPER.readValue(json, new TypeReference<Map<String, Object>>() {
            });
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to deserialize from JSON", e);
        }
    }
}

/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */

package com.openjiuwen.core.memory.manage.memmodel;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.common.exception.ErrorBuilder;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.common.logging.LoggerProtocol;
import com.openjiuwen.core.foundation.store.BaseKVStore;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * User memory store using KV store as backend.
 * Corresponds to Python: manage/mem_model/user_mem_store.py
 */
public class UserMemStore {

    private static final LoggerProtocol logger = Loggers.MEMORY;
    private static final ObjectMapper objectMapper = new ObjectMapper();

    public static final int BYTE_NUM_PER_ID = 24;
    public static final String IDS_STR = "ids";
    public static final String USER_PROFILE_TOPIC_STR = "UPT";
    public static final String KEY_PREFIX_STR = "UMD";
    public static final String MEM_TYPE_FIELD_KEY = "mem_type";
    public static final String TOPIC_FIELD_KEY = "profile_type";
    public static final String SEPARATOR = "/";

    private final BaseKVStore kvStore;

    public UserMemStore(BaseKVStore kvStoreInstance) {
        if (kvStoreInstance == null) {
            throw ErrorBuilder.build(
                StatusCode.MEMORY_STORE_INIT_FAILED,
                "kv store instance is None in UserMemStore"
            );
        }
        this.kvStore = kvStoreInstance;
    }

    /**
     * Write data to store.
     */
    public CompletableFuture<Boolean> write(String userId, String scopeId, String memId, Map<String, Object> data) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (data == null || data.isEmpty()) {
                    logger.error("write failed, because data is empty");
                    return false;
                }

                String userMemKey = getUserMemKey(userId, scopeId, memId);
                if (kvStore.exists(userMemKey).join()) {
                    logger.error("write failed, user memory already exists for user_id={}, scope_id={}, mem_id={}",
                        userId, scopeId, memId);
                    return false;
                }

                // Set user mem id
                kvStore.set(userMemKey, toJson(data)).join();

                // Append id to mem_type ids and user profile topic ids
                if (data.containsKey(MEM_TYPE_FIELD_KEY)) {
                    String memType = String.valueOf(data.get(MEM_TYPE_FIELD_KEY));

                    // mem_type ids
                    String userMemIdsKey = getUserIdsKey(userId, scopeId, memType);
                    String userMemIdsValue = kvStore.get(userMemIdsKey).join();
                    if (userMemIdsValue == null) userMemIdsValue = "";
                    kvStore.set(userMemIdsKey, writeId(userMemIdsValue, memId)).join();

                    // user profile topic ids
                    if (MemoryType.USER_PROFILE.getValue().equals(memType)
                        && data.containsKey(TOPIC_FIELD_KEY)
                        && data.get(TOPIC_FIELD_KEY) != null) {

                        String topic = String.valueOf(data.get(TOPIC_FIELD_KEY));
                        String userMemTopicKey = getConcatenationKey(List.of(userId, scopeId,
                            USER_PROFILE_TOPIC_STR, topic, IDS_STR));
                        String userMemTopicValue = kvStore.get(userMemTopicKey).join();
                        if (userMemTopicValue == null) userMemTopicValue = "";
                        kvStore.set(userMemTopicKey, writeId(userMemTopicValue, memId)).join();
                    }
                }

                // Append id to user ids
                String userIdsKey = getUserIdsKey(userId, scopeId, null);
                String userIdsValue = kvStore.get(userIdsKey).join();
                if (userIdsValue == null) userIdsValue = "";
                kvStore.set(userIdsKey, writeId(userIdsValue, memId)).join();

                return true;
            } catch (Exception e) {
                logger.error("Write failed", e);
                return false;
            }
        });
    }

    /**
     * Update the data of given id.
     */
    public CompletableFuture<Boolean> update(String userId, String scopeId, String memId, Map<String, Object> data) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String userMemKey = getUserMemKey(userId, scopeId, memId);
                if (!kvStore.exists(userMemKey).join()) {
                    logger.error("update failed, user memory does not exists for user_id={}, scope_id={}, mem_id={}",
                        userId, scopeId, memId);
                    return false;
                }

                String oldData = kvStore.get(userMemKey).join();
                if (oldData == null || oldData.isEmpty()) {
                    kvStore.set(userMemKey, toJson(data)).join();
                    return true;
                }

                Map<String, Object> dictValue = fromJson(oldData);
                dictValue.putAll(data);
                kvStore.set(userMemKey, toJson(dictValue)).join();
                return true;
            } catch (Exception e) {
                logger.error("Update failed", e);
                return false;
            }
        });
    }

    /**
     * Delete data by given id.
     */
    public CompletableFuture<Void> delete(String userId, String scopeId, String memId) {
        return CompletableFuture.runAsync(() -> innerDelete(userId, scopeId, memId));
    }

    /**
     * Batch delete data by given ids.
     */
    public CompletableFuture<Void> batchDelete(String userId, String scopeId, List<String> memIds) {
        return CompletableFuture.runAsync(() -> {
            for (String memId : memIds) {
                innerDelete(userId, scopeId, memId);
            }
        });
    }

    /**
     * Get data from given id.
     */
    public CompletableFuture<Map<String, Object>> get(String userId, String scopeId, String memId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String userMemKey = getUserMemKey(userId, scopeId, memId);
                return getData(userMemKey);
            } catch (Exception e) {
                logger.error("Get failed", e);
                return null;
            }
        });
    }

    /**
     * Get data from given ids.
     */
    public CompletableFuture<List<Map<String, Object>>> batchGet(String userId, String scopeId, List<String> memIds) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                List<String> keysList = memIds.stream()
                    .map(memId -> getUserMemKey(userId, scopeId, memId))
                    .toList();

                List<String> valueList = kvStore.mget(keysList).join();
                if (valueList == null || valueList.isEmpty()) {
                    return new ArrayList<>();
                }

                List<Map<String, Object>> result = new ArrayList<>();
                for (String value : valueList) {
                    if (value != null) {
                        result.add(fromJson(value));
                    }
                }
                return result;
            } catch (Exception e) {
                logger.error("Batch get failed", e);
                return new ArrayList<>();
            }
        });
    }

    /**
     * Get data from given user_id|scope_id|mem_type.
     */
    public CompletableFuture<List<Map<String, Object>>> getAll(String userId, String scopeId, String memType) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String userIdsKey = getUserIdsKey(userId, scopeId, memType);
                if (!kvStore.exists(userIdsKey).join()) {
                    return null;
                }

                String userIdsValue = kvStore.get(userIdsKey).join();
                if (userIdsValue == null || userIdsValue.isEmpty()) {
                    return null;
                }

                List<String> allIds = getAllIds(userIdsValue);
                return batchGet(userId, scopeId, allIds).join();
            } catch (Exception e) {
                logger.error("Get all failed", e);
                return null;
            }
        });
    }

    /**
     * Get data from given user_id|scope_id|topic.
     */
    public CompletableFuture<List<Map<String, Object>>> getByTopic(String userId, String scopeId, String topic) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String userMemTopicKey = getConcatenationKey(
                    List.of(userId, scopeId, USER_PROFILE_TOPIC_STR, topic, IDS_STR));

                if (!kvStore.exists(userMemTopicKey).join()) {
                    return null;
                }

                String userMemTopicValue = kvStore.get(userMemTopicKey).join();
                if (userMemTopicValue == null || userMemTopicValue.isEmpty()) {
                    return null;
                }

                List<String> allIds = getAllIds(userMemTopicValue);
                return batchGet(userId, scopeId, allIds).join();
            } catch (Exception e) {
                logger.error("Get by topic failed", e);
                return null;
            }
        });
    }

    /**
     * Get data in range.
     */
    public CompletableFuture<List<Map<String, Object>>> getInRange(String userId, String scopeId, int startIdx, int endIdx) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String userIdsKey = getUserIdsKey(userId, scopeId, null);
                if (!kvStore.exists(userIdsKey).join()) {
                    return null;
                }

                String userIdsValue = kvStore.get(userIdsKey).join();
                if (userIdsValue == null || userIdsValue.isEmpty()) {
                    return null;
                }

                List<String> memIds = getIdsInRange(userIdsValue, startIdx, endIdx);
                return batchGet(userId, scopeId, memIds).join();
            } catch (Exception e) {
                logger.error("Get in range failed", e);
                return null;
            }
        });
    }

    // Private helper methods

    private String getUserIdsKey(String userId, String scopeId, String memType) {
        if (memType == null) {
            return getConcatenationKey(List.of(userId, scopeId, IDS_STR));
        } else {
            return getConcatenationKey(List.of(userId, scopeId, memType, IDS_STR));
        }
    }

    private String getUserMemKey(String userId, String scopeId, String memId) {
        return getConcatenationKey(List.of(userId, scopeId, memId));
    }

    private String getConcatenationKey(List<String> fields) {
        StringBuilder keyStr = new StringBuilder(KEY_PREFIX_STR);
        for (String field : fields) {
            keyStr.append(SEPARATOR).append(field);
        }
        return keyStr.toString();
    }

    private void innerDelete(String userId, String scopeId, String memId) {
        try {
            String userMemKey = getUserMemKey(userId, scopeId, memId);
            if (!kvStore.exists(userMemKey).join()) {
                logger.warning("delete failed, user memory does not exists for user_id={}, scope_id={}, mem_id={}",
                    userId, scopeId, memId);
                return;
            }

            String data = kvStore.get(userMemKey).join();
            if (data != null && !data.isEmpty()) {
                Map<String, Object> dictValue = fromJson(data);

                if (dictValue.containsKey(MEM_TYPE_FIELD_KEY)) {
                    String memType = String.valueOf(dictValue.get(MEM_TYPE_FIELD_KEY));

                    // Delete user mem_type ids
                    String userMemIdsKey = getUserIdsKey(userId, scopeId, memType);
                    deleteMemId(userMemIdsKey, memId);

                    // Delete user profile topic ids
                    if (MemoryType.USER_PROFILE.getValue().equals(memType)
                        && dictValue.containsKey(TOPIC_FIELD_KEY)
                        && dictValue.get(TOPIC_FIELD_KEY) != null) {

                        String topic = String.valueOf(dictValue.get(TOPIC_FIELD_KEY));
                        String userMemTopicKey = getConcatenationKey(
                            List.of(userId, scopeId, USER_PROFILE_TOPIC_STR, topic, IDS_STR));
                        deleteMemId(userMemTopicKey, memId);
                    }
                }
            }

            // Delete user ids
            String userIdsKey = getUserIdsKey(userId, scopeId, null);
            deleteMemId(userIdsKey, memId);

            // Delete user mem
            kvStore.delete(userMemKey).join();
        } catch (Exception e) {
            logger.error("Inner delete failed", e);
        }
    }

    private void deleteMemId(String idsKey, String memId) {
        try {
            if (kvStore.exists(idsKey).join()) {
                String idsValue = kvStore.get(idsKey).join();
                if (idsValue == null) idsValue = "";
                String newIdsValue = deleteIdByValue(idsValue, memId);
                if (!newIdsValue.isEmpty()) {
                    kvStore.set(idsKey, newIdsValue).join();
                } else {
                    kvStore.delete(idsKey).join();
                }
            }
        } catch (Exception e) {
            logger.error("Delete mem id failed", e);
        }
    }

    private Map<String, Object> getData(String memKey) {
        try {
            String memValue = kvStore.get(memKey).join();
            if (memValue == null || memValue.isEmpty()) {
                return null;
            }
            return fromJson(memValue);
        } catch (Exception e) {
            logger.error("Get data failed", e);
            return null;
        }
    }

    private static String writeId(String dataList, String id) {
        return dataList + id;
    }

    private String deleteIdByValue(String dataList, String idStr) {
        int total = dataList.length() / BYTE_NUM_PER_ID;
        for (int i = 0; i < total; i++) {
            String chunk = dataList.substring(i * BYTE_NUM_PER_ID, (i + 1) * BYTE_NUM_PER_ID);
            if (chunk.equals(idStr)) {
                return dataList.substring(0, i * BYTE_NUM_PER_ID) +
                       dataList.substring((i + 1) * BYTE_NUM_PER_ID);
            }
        }
        return dataList;
    }

    private List<String> getAllIds(String dataList) {
        int total = dataList.length() / BYTE_NUM_PER_ID;
        List<String> result = new ArrayList<>();
        for (int i = 0; i < total; i++) {
            result.add(dataList.substring(i * BYTE_NUM_PER_ID, (i + 1) * BYTE_NUM_PER_ID));
        }
        return result;
    }

    private List<String> getIdsInRange(String dataList, int startIdx, int endIdx) {
        int total = dataList.length() / BYTE_NUM_PER_ID;
        startIdx = Math.max(startIdx, 0);
        endIdx = Math.min(endIdx, total);
        if (startIdx >= endIdx) {
            return new ArrayList<>();
        }

        List<String> result = new ArrayList<>();
        for (int i = startIdx; i < endIdx; i++) {
            result.add(dataList.substring(i * BYTE_NUM_PER_ID, (i + 1) * BYTE_NUM_PER_ID));
        }
        return result;
    }

    private static String toJson(Map<String, Object> data) {
        try {
            return objectMapper.writeValueAsString(data);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize to JSON", e);
        }
    }

    private static Map<String, Object> fromJson(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to deserialize from JSON", e);
        }
    }
}


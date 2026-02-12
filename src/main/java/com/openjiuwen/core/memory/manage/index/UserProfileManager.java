/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */

package com.openjiuwen.core.memory.manage.index;

import com.openjiuwen.core.common.exception.ErrorBuilder;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.common.logging.LoggerProtocol;
import com.openjiuwen.core.common.utils.Pair;
import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.memory.common.MemoryUtils;
import com.openjiuwen.core.memory.manage.memmodel.BaseMemoryUnit;
import com.openjiuwen.core.memory.manage.memmodel.ConflictType;
import com.openjiuwen.core.memory.manage.memmodel.DataIdManager;
import com.openjiuwen.core.memory.manage.memmodel.MemoryType;
import com.openjiuwen.core.memory.manage.memmodel.SemanticStore;
import com.openjiuwen.core.memory.manage.memmodel.UserMemStore;
import com.openjiuwen.core.memory.manage.memmodel.UserProfileUnit;
import com.openjiuwen.core.memory.manage.update.ConflictResolution;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Manager for user profile memory storage and retrieval.
 * <p>
 * Corresponds to Python: manage/index/user_profile_manager.py
 */
public class UserProfileManager extends BaseMemoryManager {

    private static final LoggerProtocol logger = Loggers.MEMORY;
    private static final int CHECK_CONFLICT_OLD_MEMORY_NUM = 5;
    private static final DateTimeFormatter DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final UserMemStore memStore;
    private final SemanticStore semanticRecall;
    private final DataIdManager dataIdGenerator;
    private final byte[] cryptoKey;

    /**
     * Initialize UserProfileManager.
     *
     * @param semanticRecallInstance The semantic store for vector search
     * @param userMemStore           The user memory store for KV storage
     * @param dataIdGenerator        The ID generator for memory IDs
     * @param cryptoKey              The encryption key
     */
    public UserProfileManager(
            SemanticStore semanticRecallInstance,
            UserMemStore userMemStore,
            DataIdManager dataIdGenerator,
            byte[] cryptoKey) {
        this.memStore = userMemStore;
        this.semanticRecall = semanticRecallInstance;
        this.dataIdGenerator = dataIdGenerator;
        this.cryptoKey = cryptoKey != null ? cryptoKey : new byte[0];
    }

    @Override
    public CompletableFuture<Void> add(BaseMemoryUnit memory, Pair<String, Model> llmInfo) {
        if (!(memory instanceof UserProfileUnit profileUnit)) {
            throw ErrorBuilder.build(
                    StatusCode.MEMORY_ADD_MEMORY_EXECUTION_ERROR,
                    "user profile add Must pass UserProfileUnit class"
            );
        }

        validateUserProfileUnit(profileUnit);

        return getConflictInfo(profileUnit, llmInfo)
                .thenCompose(conflictInfo -> processConflictInfo(conflictInfo, profileUnit))
                .thenApply(v -> null);
    }

    private void validateUserProfileUnit(UserProfileUnit unit) {
        if (unit.getUserId() == null || unit.getUserId().isEmpty()) {
            throw ErrorBuilder.build(
                    StatusCode.MEMORY_ADD_MEMORY_EXECUTION_ERROR,
                    "user_profile_manager add operation must pass user_id"
            );
        }
        if (unit.getScopeId() == null || unit.getScopeId().isEmpty()) {
            throw ErrorBuilder.build(
                    StatusCode.MEMORY_ADD_MEMORY_EXECUTION_ERROR,
                    "user_profile_manager add operation must pass scope_id"
            );
        }
        if (unit.getProfileMem() == null || unit.getProfileMem().isEmpty()) {
            throw ErrorBuilder.build(
                    StatusCode.MEMORY_ADD_MEMORY_EXECUTION_ERROR,
                    "user_profile_manager add operation must pass profile_mem"
            );
        }
        if (unit.getProfileType() == null || unit.getProfileType().isEmpty()) {
            throw ErrorBuilder.build(
                    StatusCode.MEMORY_ADD_MEMORY_EXECUTION_ERROR,
                    "user_profile_manager add operation must pass profile_type"
            );
        }
    }

    private CompletableFuture<Void> processConflictInfo(List<Map<String, Object>> conflictInfo,
                                                         UserProfileUnit memory) {
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        for (Map<String, Object> conflict : conflictInfo) {
            String confId = String.valueOf(conflict.get("id"));
            String confMem = (String) conflict.get("text");
            String confEvent = (String) conflict.get("event");

            if (confMem == null || confMem.isEmpty()) {
                continue;
            }

            if ("-1".equals(confId) && ConflictType.ADD.getValue().equals(confEvent)) {
                logger.debug("add conflict info: {}", conflict);
                UserProfileSearchParams searchParams = UserProfileSearchParams.builder()
                        .userId(memory.getUserId())
                        .scopeId(memory.getScopeId())
                        .profileType(memory.getProfileType())
                        .profileMem(confMem)
                        .sourceId(memory.getMessageMemId())
                        .build();
                futures.add(addUserProfileMemory(searchParams)
                        .thenCompose(memId -> addVectorUserProfileMemory(
                                memory.getUserId(),
                                memory.getScopeId(),
                                memId,
                                confMem,
                                MemoryType.USER_PROFILE.getValue()
                        )));
            } else if (ConflictType.NONE.getValue().equals(confEvent)) {
                logger.debug("none conflict info: {}, new_profile: {}", conflict, memory.getProfileMem());
            } else if (ConflictType.UPDATE.getValue().equals(confEvent)) {
                logger.debug("update conflict info: {}, update_profile: {}", conflict, memory.getProfileMem());
                futures.add(update(memory.getUserId(), memory.getScopeId(), confId, memory.getProfileMem())
                        .thenApply(v -> null));
            } else if (ConflictType.DELETE.getValue().equals(confEvent)) {
                logger.debug("delete conflict info: {}, new_profile: {}", conflict, memory.getProfileMem());
                futures.add(delete(memory.getUserId(), memory.getScopeId(), confId)
                        .thenApply(v -> null));
            } else {
                logger.debug("unknown conflict event: {}", conflict);
            }
        }

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
    }

    @Override
    public CompletableFuture<Boolean> update(String userId, String scopeId, String memId, String newMemory) {
        String time = ZonedDateTime.now(ZoneOffset.UTC).format(DATE_TIME_FORMATTER);
        String encryptedNewMemory = encryptMemoryIfNeeded(cryptoKey, newMemory);

        Map<String, Object> newData = new HashMap<>();
        newData.put("mem", encryptedNewMemory);
        newData.put("time", time);

        return memStore.update(memId, userId, scopeId, newData)
                .thenCompose(v -> {
                    String tableName = MemoryUtils.generateIdxName(userId, scopeId, MemoryType.USER_PROFILE.getValue());
                    return semanticRecall.deleteDocs(List.of(memId), tableName);
                })
                .thenCompose(v -> {
                    String tableName = MemoryUtils.generateIdxName(userId, scopeId, MemoryType.USER_PROFILE.getValue());
                    return semanticRecall.addDocs(
                            List.of(new Pair<>(memId, newMemory)),
                            tableName,
                            scopeId
                    );
                })
                .thenApply(v -> true)
                .exceptionally(e -> {
                    logger.error("Failed to update user profile: {}", e.getMessage());
                    return false;
                });
    }

    @Override
    public CompletableFuture<Boolean> delete(String userId, String scopeId, String memId) {
        return memStore.get(userId, scopeId, memId)
                .thenCompose(data -> {
                    if (data == null) {
                        logger.error("Delete user_profile in store failed, the mem of mem_id({}) is not exist.", memId);
                        return CompletableFuture.completedFuture(false);
                    }
                    return memStore.delete(userId, scopeId, memId)
                            .thenCompose(v -> deleteVectorUserProfileMemory(
                                    List.of(memId), userId, scopeId, MemoryType.USER_PROFILE.getValue()
                            ))
                            .thenApply(v -> true);
                })
                .exceptionally(e -> {
                    logger.error("Failed to delete user profile: {}", e.getMessage());
                    return false;
                });
    }

    @Override
    public CompletableFuture<Boolean> deleteByUserId(String userId, String scopeId) {
        return memStore.getAll(userId, scopeId, MemoryType.USER_PROFILE.getValue())
                .thenCompose(data -> {
                    if (data == null || data.isEmpty()) {
                        logger.error("Delete user_profile in store failed, the mem of user_id({}) is not exist.", userId);
                        return CompletableFuture.completedFuture(false);
                    }
                    List<String> memIds = new ArrayList<>();
                    for (Map<String, Object> item : data) {
                        memIds.add((String) item.get("id"));
                    }
                    return memStore.batchDelete(userId, scopeId, memIds)
                            .thenCompose(v -> deleteVectorStoreTable(userId, scopeId, MemoryType.USER_PROFILE.getValue()))
                            .thenApply(v -> true);
                })
                .exceptionally(e -> {
                    logger.error("Failed to delete by user id: {}", e.getMessage());
                    return false;
                });
    }

    @Override
    public CompletableFuture<Map<String, Object>> get(String userId, String scopeId, String memId) {
        return memStore.get(userId, scopeId, memId)
                .thenApply(result -> {
                    if (result == null) {
                        return null;
                    }
                    result.put("mem", decryptMemoryIfNeeded(cryptoKey, (String) result.get("mem")));
                    result.put("context_summary", decryptMemoryIfNeeded(cryptoKey, (String) result.get("context_summary")));
                    return result;
                });
    }

    @Override
    public CompletableFuture<List<Map<String, Object>>> search(String userId, String scopeId, String query, int topK) {
        return recallByVector(query, userId, scopeId, topK, MemoryType.USER_PROFILE.getValue())
                .thenCompose(recallResult -> {
                    List<String> memIds = recallResult.getKey();
                    Map<String, Double> scores = recallResult.getValue();
                    return memStore.batchGet(userId, scopeId, memIds)
                            .thenApply(retrieveRes -> {
                                if (retrieveRes == null) {
                                    return List.<Map<String, Object>>of();
                                }
                                for (Map<String, Object> item : retrieveRes) {
                                    item.put("score", scores.getOrDefault((String) item.get("id"), 0.0));
                                    item.put("mem", decryptMemoryIfNeeded(cryptoKey, (String) item.get("mem")));
                                    item.put("context_summary", decryptMemoryIfNeeded(cryptoKey,
                                            (String) item.get("context_summary")));
                                }
                                retrieveRes.sort((a, b) -> {
                                    Double scoreA = (Double) a.getOrDefault("score", 0.0);
                                    Double scoreB = (Double) b.getOrDefault("score", 0.0);
                                    return scoreB.compareTo(scoreA);
                                });
                                return retrieveRes;
                            });
                });
    }

    /**
     * List user profiles with optional filtering by profile type.
     *
     * @param userId      The user ID
     * @param scopeId     The scope ID
     * @param profileType The profile type to filter by (optional)
     * @return CompletableFuture containing list of user profiles
     */
    public CompletableFuture<List<Map<String, Object>>> listUserProfile(
            String userId, String scopeId, String profileType) {
        return memStore.getAll(userId, scopeId, MemoryType.USER_PROFILE.getValue())
                .thenApply(datas -> {
                    if (datas == null || datas.isEmpty()) {
                        logger.debug("End to get user profile, result is None, params user_id:{}, scope_id:{}", userId, scopeId);
                        return List.<Map<String, Object>>of();
                    }

                    List<Map<String, Object>> newDatas = new ArrayList<>();
                    if (profileType != null) {
                        for (Map<String, Object> data : datas) {
                            if (profileType.equals(data.get("profile_type"))) {
                                newDatas.add(data);
                            }
                        }
                    } else {
                        newDatas.addAll(datas);
                    }

                    for (Map<String, Object> data : newDatas) {
                        data.put("mem", decryptMemoryIfNeeded(cryptoKey, (String) data.get("mem")));
                        data.put("context_summary", decryptMemoryIfNeeded(cryptoKey,
                                (String) data.get("context_summary")));
                    }

                    newDatas.sort((a, b) -> {
                        String memA = (String) a.getOrDefault("mem", "");
                        String memB = (String) b.getOrDefault("mem", "");
                        int memCompare = memB.compareTo(memA);
                        if (memCompare != 0) {
                            return memCompare;
                        }
                        String timestampA = (String) a.getOrDefault("timestamp", "");
                        String timestampB = (String) b.getOrDefault("timestamp", "");
                        return timestampB.compareTo(timestampA);
                    });

                    return newDatas;
                });
    }

    private CompletableFuture<Pair<List<String>, Map<String, Double>>> recallByVector(
            String query, String userId, String scopeId, int topK, String memType) {
        String tableName = MemoryUtils.generateIdxName(userId, scopeId, memType);
        return semanticRecall.search(query, tableName, scopeId, topK)
                .thenApply(MemoryUtils::parseMemoryHitInfosAsPair);
    }

    private CompletableFuture<List<Map<String, Object>>> getConflictInfo(
            UserProfileUnit memory,
            Pair<String, Model> llm) {
        return getConflictInput(memory.getUserId(), memory.getScopeId(), memory.getProfileMem())
                .thenCompose(inputData -> {
                    List<String> inputMemories = inputData.getKey();
                    Map<Integer, String> inputMemoryIdsMap = inputData.getValue();
                    return ConflictResolution.checkConflict(inputMemories, memory.getProfileMem(), llm)
                            .thenApply(tmpConflictInfo ->
                                    processConflictInfoIds(tmpConflictInfo, inputMemoryIdsMap));
                });
    }

    private CompletableFuture<Pair<List<String>, Map<Integer, String>>> getConflictInput(
            String userId, String scopeId, String newMemory) {
        return search(userId, scopeId, newMemory, CHECK_CONFLICT_OLD_MEMORY_NUM)
                .thenApply(searchResults -> {
                    List<String> inputMemories = new ArrayList<>();
                    Map<Integer, String> inputMemoryIdsMap = new HashMap<>();

                    int i = 1;
                    for (Map<String, Object> searchResult : searchResults) {
                        String memId = (String) searchResult.get("id");
                        String memContent = (String) searchResult.get("mem");
                        inputMemories.add(memContent);
                        inputMemoryIdsMap.put(i, memId);
                        i++;
                    }

                    return new Pair<>(inputMemories, inputMemoryIdsMap);
                });
    }

    private static List<Map<String, Object>> processConflictInfoIds(
            List<Map<String, Object>> conflictInfo,
            Map<Integer, String> inputMemoryIdsMap) {
        List<Map<String, Object>> processedInfo = new ArrayList<>();

        for (Map<String, Object> conflict : conflictInfo) {
            int confId = Integer.parseInt(String.valueOf(conflict.get("id")));
            String confMem = (String) conflict.get("text");
            String confEvent = (String) conflict.get("event");

            Map<String, Object> processed = new HashMap<>();
            if (confId == 0) {
                processed.put("id", "-1");
            } else {
                processed.put("id", inputMemoryIdsMap.get(confId));
            }
            processed.put("text", confMem);
            processed.put("event", confEvent);
            processedInfo.add(processed);
        }

        return processedInfo;
    }

    private CompletableFuture<String> addUserProfileMemory(UserProfileSearchParams req) {
        String memId = dataIdGenerator.generateNextId(req.getUserId());

        ZonedDateTime time = ZonedDateTime.now(ZoneOffset.UTC);
        String encryptedProfileMem = encryptMemoryIfNeeded(cryptoKey, req.getProfileMem());
        String encryptedContextSummary = encryptMemoryIfNeeded(cryptoKey, req.getContextSummary());

        Map<String, Object> data = new HashMap<>();
        data.put("id", memId);
        data.put("user_id", req.getUserId() != null ? req.getUserId() : "");
        data.put("scope_id", req.getScopeId() != null ? req.getScopeId() : "");
        data.put("is_implicit", req.isImplicit());
        data.put("profile_type", req.getProfileType());
        data.put("mem", encryptedProfileMem);
        data.put("source_id", req.getSourceId());
        data.put("reasoning", req.getReasoning());
        data.put("context_summary", encryptedContextSummary);
        data.put("mem_type", req.getMemType());
        data.put("timestamp", time.format(DATE_TIME_FORMATTER));

        return memStore.write(req.getUserId(), req.getScopeId(), memId, data)
                .thenApply(v -> memId);
    }

    private CompletableFuture<Void> addVectorUserProfileMemory(
            String userId, String scopeId, String memoryId, String mem, String memType) {
        if (semanticRecall == null) {
            throw ErrorBuilder.build(
                    StatusCode.MEMORY_ADD_MEMORY_EXECUTION_ERROR,
                    "vector store must not be None"
            );
        }
        String tableName = MemoryUtils.generateIdxName(userId, scopeId, memType);
        return semanticRecall.addDocs(List.of(new Pair<>(memoryId, mem)), tableName, scopeId)
                .thenApply(v -> null);
    }

    private CompletableFuture<Void> deleteVectorUserProfileMemory(
            List<String> memoryIds, String userId, String scopeId, String memType) {
        if (semanticRecall == null) {
            throw ErrorBuilder.build(
                    StatusCode.MEMORY_DELETE_MEMORY_EXECUTION_ERROR,
                    "vector store must not be None"
            );
        }
        String tableName = MemoryUtils.generateIdxName(userId, scopeId, memType);
        return semanticRecall.deleteDocs(memoryIds, tableName)
                .thenApply(v -> null);
    }

    private CompletableFuture<Void> deleteVectorStoreTable(String userId, String scopeId, String memType) {
        if (semanticRecall == null) {
            throw ErrorBuilder.build(
                    StatusCode.MEMORY_DELETE_MEMORY_EXECUTION_ERROR,
                    "vector store must not be None"
            );
        }
        String tableName = MemoryUtils.generateIdxName(userId, scopeId, memType);
        return semanticRecall.deleteTable(tableName)
                .thenApply(v -> null);
    }
}


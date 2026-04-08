/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.memory.manage.index;

import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.common.logging.events.LogEventType;
import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.memory.common.MemoryUtils;
import com.openjiuwen.core.memory.manage.mem_model.*;
import com.openjiuwen.core.memory.manage.update.MemUpdateChecker;
import com.openjiuwen.core.memory.manage.update.MemoryActionItem;
import com.openjiuwen.core.memory.manage.update.MemoryStatus;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Manages fragment (user profile) memory CRUD with encryption and vector storage.
 */
public class FragmentMemoryManager extends BaseMemoryManager {

    public static final int UPDATE_CHECK_OLD_MEMORY_NUM = 5;
    public static final double UPDATE_CHECK_OLD_MEMORY_RELEVANCE_THRESHOLD = 0.75;

    private final UserMemStore memStore;
    private final DataIdManager dataIdGenerator;
    private final byte[] cryptoKey;

    public FragmentMemoryManager(UserMemStore memStore, DataIdManager dataIdGenerator, byte[] cryptoKey) {
        this.memStore = memStore;
        this.dataIdGenerator = dataIdGenerator;
        this.cryptoKey = cryptoKey;
    }

    @Override
    public void addMemories(String userId, String scopeId, List<? extends BaseMemoryUnit> memories,
                             Map.Entry<String, Model> llm, Map<String, Object> kwargs) {
        SemanticStore semanticStore = getSemanticStore("add", kwargs);

        @SuppressWarnings("unchecked")
        List<FragmentMemoryUnit> fragmentMemories = (List<FragmentMemoryUnit>) (List<?>) memories;

        // Step 1: Prepare new memories dictionary for checker
        Map<String, String> newMemContent = new LinkedHashMap<>();
        Map<String, FragmentMemoryUnit> newMemUnits = new LinkedHashMap<>();
        for (FragmentMemoryUnit unit : fragmentMemories) {
            if (unit.getContent() != null) {
                newMemContent.put(unit.getMemId(), unit.getContent());
                newMemUnits.put(unit.getMemId(), unit);
            }
        }

        // Step 2: Query existing memories for context
        Map<String, String> oldMemories = new LinkedHashMap<>();
        Set<String> oldMemIds = new HashSet<>();
        for (String newMem : newMemContent.values()) {
            List<Map<String, Object>> searchResults = search(userId, scopeId, newMem,
                    UPDATE_CHECK_OLD_MEMORY_NUM, kwargs);
            if (searchResults != null) {
                for (Map<String, Object> result : searchResults) {
                    String resultId = String.valueOf(result.getOrDefault("id", ""));
                    double resultScore = result.get("score") instanceof Number
                            ? ((Number) result.get("score")).doubleValue() : 0.0;
                    String resultContent = String.valueOf(result.getOrDefault("mem", ""));
                    if (!resultId.isEmpty() && resultScore > UPDATE_CHECK_OLD_MEMORY_RELEVANCE_THRESHOLD
                            && !oldMemIds.contains(resultId)) {
                        oldMemories.put(resultId, resultContent);
                        oldMemIds.add(resultId);
                    }
                }
            }
        }

        // If no existing memories and only one new memory, skip check
        if (oldMemories.isEmpty() && fragmentMemories.size() == 1) {
            addMemoryToStore(userId, scopeId, fragmentMemories.get(0), semanticStore);
            return;
        }

        // Step 3: Use MemChecker to analyze for redundancy/conflicts
        MemUpdateChecker checker = new MemUpdateChecker();
        List<MemoryActionItem> actionItems = checker.check(newMemContent, oldMemories, llm);
        MEMORY_LOGGER.info("[{}] Memory check completed, got {} action items",
                LogEventType.MEMORY_PROCESS, actionItems.size());

        // Step 4: Execute actions
        for (MemoryActionItem actionItem : actionItems) {
            if (actionItem.getStatus() == MemoryStatus.ADD) {
                FragmentMemoryUnit unit = newMemUnits.get(actionItem.getId());
                if (unit != null) {
                    addMemoryToStore(userId, scopeId, unit, semanticStore);
                }
            } else if (actionItem.getStatus() == MemoryStatus.DELETE) {
                delete(userId, scopeId, actionItem.getId(), kwargs);
            }
        }
    }

    @Override
    public void update(String userId, String scopeId, String memId, String newMemory,
                        Map<String, Object> kwargs) {
        SemanticStore semanticStore = getSemanticStore("update", kwargs);
        String time = OffsetDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        String encryptedMemory = encryptMemoryIfNeeded(cryptoKey, newMemory);
        Map<String, Object> newData = new LinkedHashMap<>();
        newData.put("mem", encryptedMemory);
        newData.put("time", time);
        memStore.update(userId, scopeId, memId, newData);
        String tableName = MemoryUtils.generateIdxName(userId, scopeId, MemoryType.FRAGMENT_MEMORY.getValue());
        semanticStore.deleteDocs(List.of(memId), tableName);
        semanticStore.addDocs(List.of(new AbstractMap.SimpleEntry<>(memId, newMemory)), tableName);
    }

    @Override
    public List<Map<String, Object>> search(String userId, String scopeId, String query, int topK,
                                             Map<String, Object> kwargs) {
        SemanticStore semanticStore = getSemanticStore("search", kwargs);
        String memType = kwargs != null && kwargs.containsKey("mem_type")
                ? String.valueOf(kwargs.get("mem_type"))
                : MemoryType.FRAGMENT_MEMORY.getValue();

        String tableName = MemoryUtils.generateIdxName(userId, scopeId, memType);
        List<Map.Entry<String, Double>> hitInfo = semanticStore.search(query, tableName, topK);
        MemoryUtils.HitParseResult parsed = MemoryUtils.parseMemoryHitInfos(hitInfo);
        List<String> memIds = parsed.ids();
        Map<String, Double> scores = parsed.scores();

        List<Map<String, Object>> retrieveRes = memStore.batchGet(userId, scopeId, memIds);
        if (retrieveRes == null || retrieveRes.isEmpty()) {
            return null;
        }
        for (Map<String, Object> item : retrieveRes) {
            String id = String.valueOf(item.getOrDefault("id", ""));
            item.put("score", scores.getOrDefault(id, 0.0));
            item.put("mem", decryptMemoryIfNeeded(cryptoKey, String.valueOf(item.getOrDefault("mem", ""))));
        }
        retrieveRes.sort((a, b) -> Double.compare(
                ((Number) b.getOrDefault("score", 0.0)).doubleValue(),
                ((Number) a.getOrDefault("score", 0.0)).doubleValue()));
        return retrieveRes;
    }

    @Override
    public Map<String, Object> get(String userId, String scopeId, String memId) {
        Map<String, Object> result = memStore.get(userId, scopeId, memId);
        if (result != null && result.containsKey("mem")) {
            result.put("mem", decryptMemoryIfNeeded(cryptoKey, String.valueOf(result.get("mem"))));
        }
        return result;
    }

    @Override
    public boolean delete(String userId, String scopeId, String memId, Map<String, Object> kwargs) {
        SemanticStore semanticStore = getSemanticStore("delete", kwargs);
        Map<String, Object> data = memStore.get(userId, scopeId, memId);
        if (data == null) {
            MEMORY_LOGGER.error("[{}] Delete fragment failed, mem not found. memId={}",
                    LogEventType.MEMORY_STORE, memId);
            return false;
        }
        String memType = kwargs != null && kwargs.containsKey("mem_type")
                ? String.valueOf(kwargs.get("mem_type"))
                : MemoryType.FRAGMENT_MEMORY.getValue();
        memStore.delete(userId, scopeId, memId);
        String tableName = MemoryUtils.generateIdxName(userId, scopeId, memType);
        semanticStore.deleteDocs(List.of(memId), tableName);
        return true;
    }

    @Override
    public boolean deleteByUserId(String userId, String scopeId, Map<String, Object> kwargs) {
        SemanticStore semanticStore = getSemanticStore("delete", kwargs);
        List<Map<String, Object>> data = memStore.getAll(userId, scopeId, MemoryType.FRAGMENT_MEMORY.getValue());
        if (data == null) {
            MEMORY_LOGGER.error("[{}] Delete fragment failed, no memories for user. userId={}",
                    LogEventType.MEMORY_STORE, userId);
            return false;
        }
        List<String> memIds = new ArrayList<>();
        for (Map<String, Object> item : data) {
            memIds.add(String.valueOf(item.get("id")));
        }
        memStore.batchDelete(userId, scopeId, memIds);
        String tableName = MemoryUtils.generateIdxName(userId, scopeId, MemoryType.FRAGMENT_MEMORY.getValue());
        semanticStore.deleteTable(tableName);
        return true;
    }

    public List<Map<String, Object>> listFragmentMemories(String userId, String scopeId,
                                                           String profileType) {
        List<Map<String, Object>> datas = memStore.getAll(userId, scopeId, MemoryType.FRAGMENT_MEMORY.getValue());
        if (datas == null || datas.isEmpty()) {
            return Collections.emptyList();
        }
        List<Map<String, Object>> filtered = new ArrayList<>();
        if (profileType != null) {
            for (Map<String, Object> data : datas) {
                if (profileType.equals(data.get("profile_type"))) {
                    filtered.add(data);
                }
            }
        } else {
            filtered = datas;
        }
        for (Map<String, Object> data : filtered) {
            data.put("mem", decryptMemoryIfNeeded(cryptoKey, String.valueOf(data.getOrDefault("mem", ""))));
        }
        filtered.sort((a, b) -> {
            String memA = String.valueOf(a.getOrDefault("mem", ""));
            String memB = String.valueOf(b.getOrDefault("mem", ""));
            int cmp = memB.compareTo(memA);
            if (cmp != 0) return cmp;
            String tsA = String.valueOf(a.getOrDefault("timestamp", ""));
            String tsB = String.valueOf(b.getOrDefault("timestamp", ""));
            return tsB.compareTo(tsA);
        });
        return filtered;
    }

    // ---- Private Helpers ----

    private void addMemoryToStore(String userId, String scopeId,
                                   FragmentMemoryUnit memory, SemanticStore semanticStore) {
        if (userId == null || userId.isEmpty()) {
            throw ErrorHelper.buildError(StatusCode.MEMORY_ADD_MEMORY_EXECUTION_ERROR,
                    "memory_type", memory.getMemType().getValue(),
                    "error_msg", "add operation must pass user_id");
        }
        if (scopeId == null || scopeId.isEmpty()) {
            throw ErrorHelper.buildError(StatusCode.MEMORY_ADD_MEMORY_EXECUTION_ERROR,
                    "memory_type", memory.getMemType().getValue(),
                    "error_msg", "add operation must pass scope_id");
        }
        if (memory.getContent() == null || memory.getContent().isEmpty()) {
            throw ErrorHelper.buildError(StatusCode.MEMORY_ADD_MEMORY_EXECUTION_ERROR,
                    "memory_type", memory.getMemType().getValue(),
                    "error_msg", "add operation must pass content");
        }
        if (memory.getFragmentType() == null || memory.getFragmentType().isEmpty()) {
            throw ErrorHelper.buildError(StatusCode.MEMORY_ADD_MEMORY_EXECUTION_ERROR,
                    "memory_type", memory.getMemType().getValue(),
                    "error_msg", "add operation must pass fragment_type");
        }

        String memId = dataIdGenerator.generateNextId(userId);
        String time = OffsetDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        String encContent = encryptMemoryIfNeeded(cryptoKey, memory.getContent());

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", memId);
        data.put("user_id", userId);
        data.put("scope_id", scopeId);
        data.put("profile_type", memory.getFragmentType());
        data.put("mem", encContent);
        data.put("source_id", memory.getMessageMemId());
        data.put("mem_type", MemoryType.FRAGMENT_MEMORY.getValue());
        data.put("timestamp", time);

        memStore.write(userId, scopeId, memId, data);

        // Add to vector store (use unencrypted content for embedding)
        String tableName = MemoryUtils.generateIdxName(userId, scopeId, MemoryType.FRAGMENT_MEMORY.getValue());
        boolean vectorSuccess = semanticStore.addDocs(
                List.of(new AbstractMap.SimpleEntry<>(memId, memory.getContent())),
                tableName
        );
        if (!vectorSuccess) {
            memStore.delete(userId, scopeId, memId);
            throw ErrorHelper.buildError(StatusCode.MEMORY_ADD_MEMORY_EXECUTION_ERROR,
                    "memory_type", memory.getMemType().getValue(),
                    "error_msg", "add vector store failed");
        }
    }

    private SemanticStore getSemanticStore(String operationType, Map<String, Object> kwargs) {
        SemanticStore store = kwargs != null ? (SemanticStore) kwargs.get("semantic_store") : null;
        if (store == null) {
            StatusCode code = switch (operationType) {
                case "update" -> StatusCode.MEMORY_UPDATE_MEMORY_EXECUTION_ERROR;
                case "delete" -> StatusCode.MEMORY_DELETE_MEMORY_EXECUTION_ERROR;
                case "search" -> StatusCode.MEMORY_GET_MEMORY_EXECUTION_ERROR;
                default -> StatusCode.MEMORY_ADD_MEMORY_EXECUTION_ERROR;
            };
            throw ErrorHelper.buildError(code,
                    "memory_type", MemoryType.FRAGMENT_MEMORY.getValue(),
                    "error_msg", "semantic_store is required");
        }
        return store;
    }
}

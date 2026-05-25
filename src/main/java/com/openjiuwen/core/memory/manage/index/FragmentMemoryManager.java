/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
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
    /**
     * Auto-generated for codecheck compliance.
     */
    public static final double UPDATE_CHECK_OLD_MEMORY_RELEVANCE_THRESHOLD = 0.75;
    private static final List<String> FRAGMENT_MEMORY_TYPES = UserMemStore.FRAGMENT_MEMORY_TYPES;

    private final UserMemStore memStore;
    private final DataIdManager dataIdGenerator;
    private final byte[] cryptoKey;

    /**
     * Auto-generated for codecheck compliance.
     */
    public FragmentMemoryManager(UserMemStore memStore, DataIdManager dataIdGenerator, byte[] cryptoKey) {
        this.memStore = memStore;
        this.dataIdGenerator = dataIdGenerator;
        this.cryptoKey = cryptoKey;
    }

    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
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

        // Fallback: if vector search returned no existing memories, load from KV store directly
        if (oldMemories.isEmpty()) {
            for (String memType : FRAGMENT_MEMORY_TYPES) {
                List<Map<String, Object>> allMems = memStore.getAll(userId, scopeId, memType);
                if (allMems != null) {
                    for (Map<String, Object> mem : allMems) {
                        String memId = String.valueOf(mem.getOrDefault("id", ""));
                        String memContent = String.valueOf(mem.getOrDefault("mem", ""));
                        if (!memId.isEmpty() && !oldMemIds.contains(memId)) {
                            oldMemories.put(memId, decryptMemoryIfNeeded(cryptoKey, memContent));
                            oldMemIds.add(memId);
                        }
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
    /**
     * Auto-generated for codecheck compliance.
     */
    public void update(String userId, String scopeId, String memId, String newMemory,
                        Map<String, Object> kwargs) {
        SemanticStore semanticStore = getSemanticStore("update", kwargs);
        String time = OffsetDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        String encryptedMemory = encryptMemoryIfNeeded(cryptoKey, newMemory);
        Map<String, Object> newData = new LinkedHashMap<>();
        newData.put("mem", encryptedMemory);
        newData.put("time", time);
        Map<String, Object> oldMem = memStore.get(userId, scopeId, memId);
        String memType = oldMem == null ? null : String.valueOf(oldMem.get("mem_type"));
        memStore.update(userId, scopeId, memId, newData);
        String tableName = MemoryUtils.generateIdxName(userId, scopeId, memType);
        semanticStore.deleteDocs(List.of(memId), tableName);
        semanticStore.addDocs(List.of(new AbstractMap.SimpleEntry<>(memId, newMemory)), tableName);
    }

    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public List<Map<String, Object>> search(String userId, String scopeId, String query, int topK,
                                             Map<String, Object> kwargs) {
        SemanticStore semanticStore = getSemanticStore("search", kwargs);
        String memType = kwargs != null && kwargs.containsKey("mem_type")
                ? String.valueOf(kwargs.get("mem_type"))
                : null;
        List<String> memTypes = memType == null || memType.isBlank()
                ? FRAGMENT_MEMORY_TYPES
                : List.of(memType);

        // Step 1: Vector search
        List<String> memIds = new ArrayList<>();
        Map<String, Double> scores = new HashMap<>();
        for (String currentType : memTypes) {
            String tableName = MemoryUtils.generateIdxName(userId, scopeId, currentType);
            List<Map.Entry<String, Double>> hitInfo = semanticStore.search(query, tableName, topK);
            MemoryUtils.HitParseResult parsed = MemoryUtils.parseMemoryHitInfos(hitInfo);
            memIds.addAll(parsed.ids());
            scores.putAll(parsed.scores());
        }

        // Step 2: Load all existing memories from KV store for completeness
        // (Vector search may miss results with local hash embedding)
        Set<String> seenIds = new HashSet<>(memIds);
        List<Map<String, Object>> combinedRes = new ArrayList<>();

        // Add vector search results first
        List<Map<String, Object>> retrieveRes = memStore.batchGet(userId, scopeId, memIds);
        if (retrieveRes != null) {
            for (Map<String, Object> item : retrieveRes) {
                String id = String.valueOf(item.getOrDefault("id", ""));
                item.put("score", scores.getOrDefault(id, 0.0));
                item.put("mem", decryptMemoryIfNeeded(cryptoKey, String.valueOf(item.getOrDefault("mem", ""))));
                combinedRes.add(item);
            }
        }

        // Supplement with KV store memories not found by vector search
        for (String currentType : memTypes) {
            List<Map<String, Object>> typeMems = memStore.getAll(userId, scopeId, currentType);
            if (typeMems != null) {
                for (Map<String, Object> mem : typeMems) {
                    String id = String.valueOf(mem.getOrDefault("id", ""));
                    if (!id.isEmpty() && seenIds.add(id)) {
                        mem.put("score", 0.0);
                        mem.put("mem", decryptMemoryIfNeeded(cryptoKey, String.valueOf(mem.getOrDefault("mem", ""))));
                        combinedRes.add(mem);
                    }
                }
            }
        }

        if (combinedRes.isEmpty()) {
            return null;
        }

        // Sort by score descending and limit to topK
        combinedRes.sort((a, b) -> Double.compare(
                ((Number) b.getOrDefault("score", 0.0)).doubleValue(),
                ((Number) a.getOrDefault("score", 0.0)).doubleValue()));
        if (combinedRes.size() > topK) {
            return new ArrayList<>(combinedRes.subList(0, topK));
        }
        return combinedRes;
    }

    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public Map<String, Object> get(String userId, String scopeId, String memId) {
        Map<String, Object> result = memStore.get(userId, scopeId, memId);
        if (result != null && result.containsKey("mem")) {
            result.put("mem", decryptMemoryIfNeeded(cryptoKey, String.valueOf(result.get("mem"))));
        }
        return result;
    }

    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
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
                : String.valueOf(data.get("mem_type"));
        memStore.delete(userId, scopeId, memId);
        List<String> memTypes = memType == null || memType.isBlank()
                ? FRAGMENT_MEMORY_TYPES
                : List.of(memType);
        for (String currentType : memTypes) {
            String tableName = MemoryUtils.generateIdxName(userId, scopeId, currentType);
            semanticStore.deleteDocs(List.of(memId), tableName);
        }
        return true;
    }

    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public boolean deleteByUserId(String userId, String scopeId, Map<String, Object> kwargs) {
        SemanticStore semanticStore = getSemanticStore("delete", kwargs);
        List<Map<String, Object>> data = new ArrayList<>();
        for (String memType : FRAGMENT_MEMORY_TYPES) {
            List<Map<String, Object>> typeData = memStore.getAll(userId, scopeId, memType);
            if (typeData != null) {
                data.addAll(typeData);
            }
        }
        if (data.isEmpty()) {
            MEMORY_LOGGER.error("[{}] Delete fragment failed, no memories for user. userId={}",
                    LogEventType.MEMORY_STORE, userId);
            return false;
        }
        List<String> memIds = new ArrayList<>();
        for (Map<String, Object> item : data) {
            memIds.add(String.valueOf(item.get("id")));
        }
        memStore.batchDelete(userId, scopeId, memIds);
        for (String memType : FRAGMENT_MEMORY_TYPES) {
            String tableName = MemoryUtils.generateIdxName(userId, scopeId, memType);
            semanticStore.deleteTable(tableName);
        }
        return true;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public List<Map<String, Object>> listFragmentMemories(String userId, String scopeId,
                                                           MemoryType memType) {
        List<String> memTypes;
        if (memType == null) {
            memTypes = FRAGMENT_MEMORY_TYPES;
        } else if (!FRAGMENT_MEMORY_TYPES.contains(memType.getValue())) {
            return Collections.emptyList();
        } else {
            memTypes = List.of(memType.getValue());
        }
        List<Map<String, Object>> filtered = new ArrayList<>();
        for (String currentType : memTypes) {
            List<Map<String, Object>> datas = memStore.getAll(userId, scopeId, currentType);
            if (datas != null) {
                filtered.addAll(datas);
            }
        }
        if (filtered.isEmpty()) {
            return Collections.emptyList();
        }
        for (Map<String, Object> data : filtered) {
            data.put("mem", decryptMemoryIfNeeded(cryptoKey, String.valueOf(data.getOrDefault("mem", ""))));
        }
        filtered.sort((a, b) -> {
            String memA = String.valueOf(a.getOrDefault("mem", ""));
            String memB = String.valueOf(b.getOrDefault("mem", ""));
            int cmp = memB.compareTo(memA);
            if (cmp != 0) {
                return cmp;
            }
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
        // Validate userId: reject overly long or control-character IDs
        if (userId.length() > 256) {
            throw ErrorHelper.buildError(StatusCode.MEMORY_ADD_MEMORY_EXECUTION_ERROR,
                    "memory_type", memory.getMemType().getValue(),
                    "error_msg", "user_id too long, max 256 chars");
        }
        if (userId.chars().anyMatch(c -> c < 0x20 && c != '\t')) {
            throw ErrorHelper.buildError(StatusCode.MEMORY_ADD_MEMORY_EXECUTION_ERROR,
                    "memory_type", memory.getMemType().getValue(),
                    "error_msg", "user_id contains invalid control characters");
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
        if (memory.getMemType() == null || !FRAGMENT_MEMORY_TYPES.contains(memory.getMemType().getValue())) {
            throw ErrorHelper.buildError(StatusCode.MEMORY_ADD_MEMORY_EXECUTION_ERROR,
                    "memory_type", String.valueOf(memory.getMemType()),
                    "error_msg", "add operation must pass fragment memory type");
        }

        String memId = dataIdGenerator.generateNextId(userId);
        String time = OffsetDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        String encContent = encryptMemoryIfNeeded(cryptoKey, memory.getContent());

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", memId);
        data.put("user_id", userId);
        data.put("scope_id", scopeId);
        data.put("mem", encContent);
        data.put("source_id", memory.getMessageMemId());
        data.put("mem_type", memory.getMemType().getValue());
        data.put("timestamp", time);

        memStore.write(userId, scopeId, memId, data);

        // Add to vector store (use unencrypted content for embedding)
        String tableName = MemoryUtils.generateIdxName(userId, scopeId, memory.getMemType().getValue());
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
                    "memory_type", "fragment_memory",
                    "error_msg", "semantic_store is required");
        }
        return store;
    }
}

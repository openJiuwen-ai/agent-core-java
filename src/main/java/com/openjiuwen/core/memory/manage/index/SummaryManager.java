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

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Manages summary memory CRUD with encryption and vector storage.
 * 
 * @since 0.1.7
 */
public class SummaryManager extends BaseMemoryManager {
    private final UserMemStore memStore;
    private final byte[] cryptoKey;

    /**
     * SummaryManager.
     * 
     * @param memStore memStore
     * @param cryptoKey cryptoKey
     * @since 0.1.7
     */
    public SummaryManager(UserMemStore memStore, byte[] cryptoKey) {
        this.memStore = memStore;
        this.cryptoKey = cryptoKey;
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
        SemanticStore semanticStore = getSemanticStore("add", kwargs);
        @SuppressWarnings("unchecked")
        List<SummaryUnit> summaryUnits = (List<SummaryUnit>) (List<?>) memories;

        for (SummaryUnit unit : summaryUnits) {
            boolean vectorSuccess = addSummaryToVector(unit, userId, scopeId, semanticStore);
            if (!vectorSuccess) {
                throw ErrorHelper.buildError(StatusCode.MEMORY_ADD_MEMORY_EXECUTION_ERROR, "memory_type", "summary",
                        "error_msg", "summary add to vector store failed");
            }
            addSummaryToMemStore(userId, scopeId, unit);
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
        SemanticStore semanticStore = getSemanticStore("update", kwargs);
        String time = OffsetDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        String encryptedMemory = encryptMemoryIfNeeded(cryptoKey, newMemory);
        Map<String, Object> newData = new LinkedHashMap<>();
        newData.put("mem", encryptedMemory);
        newData.put("time", time);
        memStore.update(userId, scopeId, memId, newData);

        String tableName = MemoryUtils.generateTenantAwareIdxName(userId, scopeId, MemoryType.SUMMARY.getValue());
        semanticStore.deleteDocs(List.of(memId), tableName);
        semanticStore.addDocs(List.of(new AbstractMap.SimpleEntry<>(memId, newMemory)), tableName);
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
        SemanticStore semanticStore = getSemanticStore("delete", kwargs);
        Map<String, Object> data = memStore.get(userId, scopeId, memId);
        if (data == null) {
            MEMORY_LOGGER.error("[{}] Delete summary failed, not found. memId={}", LogEventType.MEMORY_STORE, memId);
            return false;
        }
        memStore.delete(userId, scopeId, memId);
        String tableName = MemoryUtils.generateTenantAwareIdxName(userId, scopeId, MemoryType.SUMMARY.getValue());
        semanticStore.deleteDocs(List.of(memId), tableName);
        return true;
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
        SemanticStore semanticStore = getSemanticStore("delete", kwargs);
        List<Map<String, Object>> data = memStore.getAll(userId, scopeId, MemoryType.SUMMARY.getValue());
        if (data == null) {
            MEMORY_LOGGER.error("[{}] Delete summary failed, no memories for user. userId={}",
                    LogEventType.MEMORY_STORE, userId);
            return false;
        }
        List<String> memIds = new ArrayList<>();
        for (Map<String, Object> item : data) {
            memIds.add(String.valueOf(item.get("id")));
        }
        memStore.batchDelete(userId, scopeId, memIds);
        String tableName = MemoryUtils.generateTenantAwareIdxName(userId, scopeId, MemoryType.SUMMARY.getValue());
        semanticStore.deleteTable(tableName);
        return true;
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
        Map<String, Object> result = memStore.get(userId, scopeId, memId);
        if (result != null && result.containsKey("mem")) {
            result.put("mem", decryptMemoryIfNeeded(cryptoKey, String.valueOf(result.get("mem"))));
        }
        return result;
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
        SemanticStore semanticStore = getSemanticStore("search", kwargs);
        String tableName = MemoryUtils.generateTenantAwareIdxName(userId, scopeId, MemoryType.SUMMARY.getValue());
        List<Map.Entry<String, Double>> hitInfo = semanticStore.search(query, tableName, topK);
        MemoryUtils.HitParseResult parsed = MemoryUtils.parseMemoryHitInfos(hitInfo);

        List<Map<String, Object>> retrieveRes = memStore.batchGet(userId, scopeId, parsed.ids());
        if (retrieveRes == null || retrieveRes.isEmpty()) {
            return null;
        }
        for (Map<String, Object> item : retrieveRes) {
            String id = String.valueOf(item.getOrDefault("id", ""));
            item.put("score", parsed.scores().getOrDefault(id, 0.0));
            item.put("mem", decryptMemoryIfNeeded(cryptoKey, String.valueOf(item.getOrDefault("mem", ""))));
        }
        retrieveRes.sort((a, b) -> Double.compare(((Number) b.getOrDefault("score", 0.0)).doubleValue(),
                ((Number) a.getOrDefault("score", 0.0)).doubleValue()));
        return retrieveRes;
    }

    /**
     * listUserSummary.
     * 
     * @param userId userId
     * @param scopeId scopeId
     * @return the result
     * @since 0.1.7
     */
    public List<Map<String, Object>> listUserSummary(String userId, String scopeId) {
        List<Map<String, Object>> data = memStore.getAll(userId, scopeId, MemoryType.SUMMARY.getValue());
        if (data == null || data.isEmpty()) {
            return Collections.emptyList();
        }
        for (Map<String, Object> item : data) {
            item.put("mem", decryptMemoryIfNeeded(cryptoKey, String.valueOf(item.getOrDefault("mem", ""))));
        }
        data.sort((a, b) -> {
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
        return data;
    }

    // ---- Private Helpers ----

    /**
     * addSummaryToMemStore.
     * 
     * @param userId userId
     * @param scopeId scopeId
     * @param unit unit
     * @since 0.1.7
     */
    private void addSummaryToMemStore(String userId, String scopeId, SummaryUnit unit) {
        String mem = encryptMemoryIfNeeded(cryptoKey, unit.getSummary());
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", unit.getMemId());
        data.put("user_id", userId != null ? userId : "");
        data.put("scope_id", scopeId != null ? scopeId : "");
        data.put("mem", mem);
        data.put("source_id", unit.getMessageMemId());
        data.put("mem_type", MemoryType.SUMMARY.getValue());
        data.put("timestamp", unit.getTimestamp());
        memStore.write(userId, scopeId, unit.getMemId(), data);
    }

    /**
     * addSummaryToVector.
     * 
     * @param unit unit
     * @param userId userId
     * @param scopeId scopeId
     * @param semanticStore semanticStore
     * @return the result
     * @since 0.1.7
     */
    private boolean addSummaryToVector(SummaryUnit unit, String userId, String scopeId, SemanticStore semanticStore) {
        if (semanticStore == null) {
            throw ErrorHelper.buildError(StatusCode.MEMORY_ADD_MEMORY_EXECUTION_ERROR, "memory_type", "summary",
                    "error_msg", "vector store must not be None");
        }
        String tableName = MemoryUtils.generateTenantAwareIdxName(userId, scopeId, MemoryType.SUMMARY.getValue());
        return semanticStore.addDocs(List.of(new AbstractMap.SimpleEntry<>(unit.getMemId(), unit.getSummary())),
                tableName);
    }

    /**
     * getSemanticStore.
     * 
     * @param operationType operationType
     * @param kwargs kwargs
     * @return the result
     * @since 0.1.7
     */
    private SemanticStore getSemanticStore(String operationType, Map<String, Object> kwargs) {
        SemanticStore store = kwargs != null ? (SemanticStore) kwargs.get("semantic_store") : null;
        if (store == null) {
            StatusCode code = switch (operationType) {
                case "update" -> StatusCode.MEMORY_UPDATE_MEMORY_EXECUTION_ERROR;
                case "delete" -> StatusCode.MEMORY_DELETE_MEMORY_EXECUTION_ERROR;
                case "search" -> StatusCode.MEMORY_GET_MEMORY_EXECUTION_ERROR;
                default -> StatusCode.MEMORY_ADD_MEMORY_EXECUTION_ERROR;
            };
            throw ErrorHelper.buildError(code, "memory_type", MemoryType.SUMMARY.getValue(), "error_msg",
                    "semantic_store is required");
        }
        return store;
    }
}

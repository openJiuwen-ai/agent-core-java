/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.manage.index;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.common.logging.events.LogEventType;
import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.store.BaseMemoryIndex;
import com.openjiuwen.core.foundation.store.MemoryDoc;
import com.openjiuwen.core.memory.manage.mem_model.BaseMemoryUnit;
import com.openjiuwen.core.memory.manage.mem_model.MemoryType;
import com.openjiuwen.core.memory.manage.mem_model.SummaryUnit;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;

/**
 * Manages summary memories through the shared memory index.
 *
 * <p>Mirrors Python's {@code SummaryManager} in
 * {@code openjiuwen/core/memory/manage/index/summary_manager.py}.</p>
 */
public class SummaryManager extends BaseMemoryManager {

    private static final DateTimeFormatter DASH_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH-mm-ss");
    private static final DateTimeFormatter COLON_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final BaseMemoryIndex memoryIndex;
    private final byte[] cryptoKey;
    private final String memType;

    public SummaryManager(BaseMemoryIndex memoryIndex) {
        this(memoryIndex, null);
    }

    public SummaryManager(BaseMemoryIndex memoryIndex, byte[] cryptoKey) {
        this.memoryIndex = memoryIndex;
        this.cryptoKey = cryptoKey == null ? null : cryptoKey.clone();
        this.memType = MemoryType.SUMMARY.getValue();
    }

    @Override
    public CompletionStage<List<BaseMemoryUnit>> addMemories(
            String userId,
            String scopeId,
            Map<String, List<BaseMemoryUnit>> memories,
            Model llm,
            Map<String, Object> kwargs
    ) {
        validateRequiredParams(userId, scopeId, memoryIndex, StatusCode.MEMORY_ADD_MEMORY_EXECUTION_ERROR, memType);

        return callIndex(() -> {
            List<MemoryDoc> memoryDocs = convertToMemoryDocs(memories);
            if (memoryDocs.isEmpty()) {
                MEMORY_LOGGER.warning(
                        "No valid summary docs to add",
                        "event_type", LogEventType.MEMORY_STORE,
                        "memory_type", memType,
                        "user_id", userId,
                        "scope_id", scopeId
                );
                return completed(List.of());
            }
            return memoryIndex.addMemories(userId, scopeId, memoryDocs)
                    .thenApply(ignored -> memories.get(memType));
        }, StatusCode.MEMORY_ADD_MEMORY_EXECUTION_ERROR);
    }

    @Override
    public CompletionStage<Boolean> update(
            String userId,
            String scopeId,
            String memId,
            String newMemory,
            Map<String, Object> kwargs
    ) {
        validateRequiredParams(userId, scopeId, memoryIndex, StatusCode.MEMORY_UPDATE_MEMORY_EXECUTION_ERROR, memType);

        return callIndex(() -> memoryIndex.getById(userId, scopeId, memId)
                .thenCompose(memoryDoc -> {
                    if (memoryDoc == null) {
                        return completed(Boolean.FALSE);
                    }
                    MemoryDoc updatedDoc = new MemoryDoc(
                            memId,
                            newMemory,
                            memType,
                            ZonedDateTime.now(ZoneId.systemDefault()),
                            memoryDoc.getFields()
                    );
                    return memoryIndex.updateMemories(userId, scopeId, List.of(updatedDoc))
                            .thenApply(ignored -> Boolean.TRUE);
                }), StatusCode.MEMORY_UPDATE_MEMORY_EXECUTION_ERROR);
    }

    @Override
    public CompletionStage<Boolean> delete(String userId, String scopeId, String memId, Map<String, Object> kwargs) {
        validateRequiredParams(userId, scopeId, memoryIndex, StatusCode.MEMORY_DELETE_MEMORY_EXECUTION_ERROR, memType);

        return callIndex(() -> memoryIndex.deleteMemories(userId, scopeId, List.of(memId))
                .thenApply(ignored -> Boolean.TRUE), StatusCode.MEMORY_DELETE_MEMORY_EXECUTION_ERROR);
    }

    @Override
    public CompletionStage<Boolean> deleteByUserId(String userId, String scopeId, Map<String, Object> kwargs) {
        validateRequiredParams(userId, scopeId, memoryIndex, StatusCode.MEMORY_DELETE_MEMORY_EXECUTION_ERROR, memType);

        return callIndex(() -> memoryIndex.deleteByUserAndScope(userId, scopeId)
                .thenApply(ignored -> Boolean.TRUE), StatusCode.MEMORY_DELETE_MEMORY_EXECUTION_ERROR);
    }

    @Override
    public CompletionStage<Map<String, Object>> get(String userId, String scopeId, String memId) {
        validateRequiredParams(userId, scopeId, memoryIndex, StatusCode.MEMORY_GET_MEMORY_EXECUTION_ERROR, memType);

        return callIndex(() -> memoryIndex.getById(userId, scopeId, memId)
                .thenApply(memoryDoc -> memoryDoc == null ? null : docToMap(memoryDoc, null)),
                StatusCode.MEMORY_GET_MEMORY_EXECUTION_ERROR);
    }

    @Override
    public CompletionStage<List<Map<String, Object>>> search(
            String userId,
            String scopeId,
            String query,
            int topK,
            Map<String, Object> kwargs
    ) {
        validateRequiredParams(userId, scopeId, memoryIndex, StatusCode.MEMORY_GET_MEMORY_EXECUTION_ERROR, memType);

        if (topK <= 0) {
            return completed(List.of());
        }
        return callIndex(() -> memoryIndex.search(userId, scopeId, query, List.of(memType), topK)
                .thenApply(searchResults -> {
                    List<Map<String, Object>> result = new ArrayList<>();
                    for (BaseMemoryIndex.MemorySearchResult searchResult : searchResults) {
                        result.add(docToMap(searchResult.document(), searchResult.score()));
                    }
                    return result;
                }), StatusCode.MEMORY_GET_MEMORY_EXECUTION_ERROR);
    }

    public CompletionStage<List<Map<String, Object>>> listUserSummary(
            String userId,
            String scopeId,
            int offset,
            int batchSize
    ) {
        validateRequiredParams(userId, scopeId, memoryIndex, StatusCode.MEMORY_GET_MEMORY_EXECUTION_ERROR, memType);

        return callIndex(() -> memoryIndex.listMemories(userId, scopeId, offset, batchSize, List.of(memType))
                .thenApply(summaryMemories -> {
                    if (summaryMemories == null || summaryMemories.isEmpty()) {
                        return List.of();
                    }
                    List<Map<String, Object>> result = new ArrayList<>();
                    for (MemoryDoc memoryDoc : summaryMemories) {
                        result.add(docToMap(memoryDoc, null));
                    }
                    result.sort(Comparator.comparing(
                            SummaryManager::timestampFromResult,
                            Comparator.nullsLast(Comparator.naturalOrder())
                    ).reversed());
                    return result;
                }), StatusCode.MEMORY_GET_MEMORY_EXECUTION_ERROR);
    }

    public CompletionStage<List<Map<String, Object>>> listUserSummary(String userId, String scopeId) {
        return listUserSummary(userId, scopeId, 0, 100);
    }

    byte[] getCryptoKey() {
        return cryptoKey == null ? null : cryptoKey.clone();
    }

    String getMemType() {
        return memType;
    }

    static ZonedDateTime parseTimestamp(String timestamp) {
        if (timestamp == null || timestamp.isEmpty()) {
            return ZonedDateTime.now(ZoneId.systemDefault());
        }
        for (DateTimeFormatter formatter : List.of(DASH_TIME_FORMAT, COLON_TIME_FORMAT)) {
            try {
                return LocalDateTime.parse(timestamp, formatter).atZone(ZoneOffset.UTC);
            } catch (DateTimeParseException ignored) {
            }
        }
        try {
            return ZonedDateTime.parse(timestamp);
        } catch (DateTimeParseException ignored) {
            try {
                return LocalDateTime.parse(timestamp).atZone(ZoneId.systemDefault());
            } catch (DateTimeParseException ignoredAgain) {
                return ZonedDateTime.now(ZoneId.systemDefault());
            }
        }
    }

    private List<MemoryDoc> convertToMemoryDocs(Map<String, List<BaseMemoryUnit>> memories) {
        List<MemoryDoc> memoryDocs = new ArrayList<>();
        for (Map.Entry<String, List<BaseMemoryUnit>> entry : memories.entrySet()) {
            String memoryType = entry.getKey();
            if (!Objects.equals(memoryType, memType)) {
                continue;
            }
            for (BaseMemoryUnit memoryUnit : entry.getValue()) {
                if (!(memoryUnit instanceof SummaryUnit summaryUnit)) {
                    continue;
                }
                Map<String, Object> fields = new LinkedHashMap<>();
                fields.put("source_id", summaryUnit.getMessageMemId());
                fields.put("metadata", new LinkedHashMap<String, Object>());
                memoryDocs.add(new MemoryDoc(
                        summaryUnit.getMemId(),
                        summaryUnit.getSummary(),
                        memoryType,
                        parseTimestamp(summaryUnit.getTimestamp()),
                        fields
                ));
            }
        }
        return memoryDocs;
    }

    private static Map<String, Object> docToMap(MemoryDoc memoryDoc, Double score) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", memoryDoc.getId());
        result.put("mem", memoryDoc.getText());
        result.put("mem_type", memoryDoc.getType());
        result.put("timestamp", memoryDoc.getTimestamp());
        if (score != null) {
            result.put("score", score);
        }
        result.put("source_id", memoryDoc.getFields().get("source_id"));
        result.put("metadata", memoryDoc.getFields().get("metadata"));
        return result;
    }

    private static ZonedDateTime timestampFromResult(Map<String, Object> result) {
        Object timestamp = result.get("timestamp");
        return timestamp instanceof ZonedDateTime zonedDateTime ? zonedDateTime : null;
    }

    private <T> CompletionStage<T> callIndex(Supplier<CompletionStage<T>> supplier, StatusCode statusCode) {
        try {
            CompletionStage<T> stage = supplier.get();
            if (stage == null) {
                wrapException(new NullPointerException("memory index operation returned null"), statusCode, memType);
            }
            return stage.handle((result, failure) -> {
                if (failure == null) {
                    return result;
                }
                Throwable cause = unwrapCompletionFailure(failure);
                if (cause instanceof BaseError baseError) {
                    throw baseError;
                }
                wrapException(cause, statusCode, memType);
                return null;
            });
        } catch (BaseError baseError) {
            throw baseError;
        } catch (Throwable failure) {
            Throwable cause = unwrapCompletionFailure(failure);
            if (cause instanceof BaseError baseError) {
                throw baseError;
            }
            wrapException(cause, statusCode, memType);
            return completed(null);
        }
    }

    private static Throwable unwrapCompletionFailure(Throwable failure) {
        Throwable current = failure;
        while ((current instanceof CompletionException || current instanceof ExecutionException)
                && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    private static <T> CompletionStage<T> completed(T value) {
        return java.util.concurrent.CompletableFuture.completedFuture(value);
    }
}

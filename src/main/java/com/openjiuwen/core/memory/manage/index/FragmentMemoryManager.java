/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.manage.index;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.common.logging.events.LogEventType;
import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.store.BaseMemoryIndex;
import com.openjiuwen.core.foundation.store.MemoryDoc;
import com.openjiuwen.core.memory.manage.mem_model.BaseMemoryUnit;
import com.openjiuwen.core.memory.manage.mem_model.FragmentMemoryUnit;
import com.openjiuwen.core.memory.manage.mem_model.MemoryType;
import com.openjiuwen.core.memory.manage.mem_model.OperationType;
import com.openjiuwen.core.memory.manage.update.MemUpdateChecker;
import com.openjiuwen.core.memory.manage.update.MemoryActionItem;
import com.openjiuwen.core.memory.manage.update.MemoryStatus;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;

/**
 * Fragment-memory manager backed by the unified memory index.
 *
 * <p>Mirrors Python's {@code FragmentMemoryManager} in
 * {@code openjiuwen/core/memory/manage/index/fragment_memory_manager.py}.</p>
 */
public class FragmentMemoryManager extends BaseMemoryManager {
    public static final List<String> FRAGMENT_MEMORY_TYPE = List.of(
            MemoryType.USER_PROFILE.getValue(),
            MemoryType.SEMANTIC_MEMORY.getValue(),
            MemoryType.EPISODIC_MEMORY.getValue()
    );

    public static final int UPDATE_CHECK_OLD_MEMORY_NUM = 5;
    public static final double UPDATE_CHECK_OLD_MEMORY_RELEVANCE_THRESHOLD = 0.75d;

    private static final DateTimeFormatter DASH_TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH-mm-ss");
    private static final DateTimeFormatter COLON_TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String SOURCE_ID = "source_id";
    private static final String FRAGMENT = "fragment";

    private final BaseMemoryIndex memoryIndex;
    private final byte[] cryptoKey;
    private final String memType;

    public FragmentMemoryManager(BaseMemoryIndex memoryIndex, byte[] cryptoKey) {
        this.memoryIndex = memoryIndex;
        this.cryptoKey = cryptoKey == null ? null : cryptoKey.clone();
        this.memType = FRAGMENT;
    }

    public BaseMemoryIndex getMemoryIndex() {
        return memoryIndex;
    }

    public byte[] getCryptoKey() {
        return cryptoKey == null ? null : cryptoKey.clone();
    }

    public String getMemType() {
        return memType;
    }

    static void removeUpdateEntriesFromProcessResult(Set<String> deleteMemoryIdSet,
                                                     Map<String, FragmentMemoryUnit> processResultDict) {
        if (deleteMemoryIdSet == null || processResultDict == null) {
            return;
        }
        for (String memId : deleteMemoryIdSet) {
            FragmentMemoryUnit unit = processResultDict.get(memId);
            if (unit != null && unit.getOperationType() == OperationType.UPDATE) {
                processResultDict.remove(memId);
            }
        }
    }

    static void appendMemUnitListToDict(Map<String, FragmentMemoryUnit> memUnitDict,
                                        List<FragmentMemoryUnit> memUnitList) {
        if (memUnitDict == null || memUnitList == null) {
            return;
        }
        for (FragmentMemoryUnit memUnit : memUnitList) {
            if (memUnit == null) {
                continue;
            }
            if (memUnitDict.containsKey(memUnit.getMemId())) {
                MEMORY_LOGGER.warning(
                        "mem duplicate, old will be overwrite",
                        "event_type", LogEventType.MEMORY_STORE,
                        "memory_id", memUnit.getMemId()
                );
            }
            memUnitDict.put(memUnit.getMemId(), memUnit);
        }
    }

    static ZonedDateTime parseTimestamp(Object timestamp) {
        if (timestamp instanceof ZonedDateTime zonedDateTime) {
            return zonedDateTime;
        }
        if (timestamp instanceof OffsetDateTime offsetDateTime) {
            return offsetDateTime.toZonedDateTime();
        }
        if (timestamp == null) {
            return ZonedDateTime.now();
        }
        String text = String.valueOf(timestamp);
        if (text.isEmpty()) {
            return ZonedDateTime.now();
        }
        for (DateTimeFormatter formatter : List.of(DASH_TIMESTAMP_FORMAT, COLON_TIMESTAMP_FORMAT)) {
            try {
                return LocalDateTime.parse(text, formatter).atZone(ZoneOffset.UTC);
            } catch (DateTimeParseException ignored) {
                // Try the next Python-compatible timestamp format.
            }
        }
        try {
            return ZonedDateTime.parse(text);
        } catch (DateTimeParseException ignored) {
            // Fall through to OffsetDateTime/LocalDateTime parsing.
        }
        try {
            return OffsetDateTime.parse(text).toZonedDateTime();
        } catch (DateTimeParseException ignored) {
            // Fall through to a local ISO timestamp, matching Python fromisoformat for naive values.
        }
        try {
            return LocalDateTime.parse(text).atZone(ZoneId.systemDefault());
        } catch (DateTimeParseException ignored) {
            return ZonedDateTime.now();
        }
    }

    static List<Map<String, Object>> processConflictInfo(List<Map<String, Object>> conflictInfo,
                                                         Map<Integer, String> inputMemoryIdsMap) {
        List<Map<String, Object>> processConflictInfo = new ArrayList<>();
        if (conflictInfo == null) {
            return processConflictInfo;
        }
        for (Map<String, Object> conflict : conflictInfo) {
            int conflictId = Integer.parseInt(String.valueOf(conflict.get("id")));
            Object conflictMemory = conflict.get("text");
            Object conflictEvent = conflict.get("event");
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", conflictId == 0 ? "-1" : inputMemoryIdsMap.get(conflictId));
            item.put("text", conflictMemory);
            item.put("event", conflictEvent);
            processConflictInfo.add(item);
        }
        return processConflictInfo;
    }

    MemoryDoc convertToMemoryDoc(FragmentMemoryUnit memUnit) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put(SOURCE_ID, memUnit.getMessageMemId());
        return new MemoryDoc(
                memUnit.getMemId(),
                memUnit.getContent(),
                memoryTypeValue(memUnit.getMemType()),
                hasText(memUnit.getTimestamp()) ? parseTimestamp(memUnit.getTimestamp()) : ZonedDateTime.now(),
                fields
        );
    }

    Map<String, Object> docToDict(MemoryDoc doc) {
        return docToDict(doc, 0.0d);
    }

    Map<String, Object> docToDict(MemoryDoc doc, double score) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", doc.getId());
        result.put("mem", doc.getText());
        result.put("mem_type", doc.getType());
        result.put("timestamp", doc.getTimestamp());
        result.put("score", score);
        result.put(SOURCE_ID, doc.getFields() == null ? null : doc.getFields().get(SOURCE_ID));
        return result;
    }

    @Override
    public CompletionStage<List<BaseMemoryUnit>> addMemories(String userId,
                                                            String scopeId,
                                                            Map<String, List<BaseMemoryUnit>> memories,
                                                            Model llm,
                                                            Map<String, Object> kwargs) {
        validateRequiredParams(
                userId,
                scopeId,
                memoryIndex,
                StatusCode.MEMORY_ADD_MEMORY_EXECUTION_ERROR,
                memType
        );

        try {
            Set<String> deleteMemoryIdSet = new LinkedHashSet<>();
            Map<String, FragmentMemoryUnit> processResultDict = new LinkedHashMap<>();
            List<FragmentMemoryUnit> addMemoryUnitList = new ArrayList<>();

            Map<String, FragmentMemoryUnit> newMemUnits = getNewMemUnitsAndUpdateMemories(
                    userId,
                    scopeId,
                    memories,
                    deleteMemoryIdSet,
                    processResultDict
            );
            Map<String, String> newMemContent = new LinkedHashMap<>();
            for (Map.Entry<String, FragmentMemoryUnit> entry : newMemUnits.entrySet()) {
                newMemContent.put(entry.getKey(), entry.getValue().getContent());
            }

            if (newMemUnits.isEmpty()) {
                deleteAndCleanProcessResults(userId, scopeId, deleteMemoryIdSet, processResultDict);
                return CompletableFuture.completedFuture(new ArrayList<>(processResultDict.values()));
            }

            Map<String, String> oldMemories = getRelatedOldMemories(newMemContent, userId, scopeId);
            if (oldMemories.isEmpty() && newMemContent.size() == 1) {
                deleteAndCleanProcessResults(userId, scopeId, deleteMemoryIdSet, processResultDict);
                addMemoryUnitList.addAll(newMemUnits.values());
                addMemoryDocs(userId, scopeId, addMemoryUnitList);
                appendMemUnitListToDict(processResultDict, addMemoryUnitList);
                return CompletableFuture.completedFuture(new ArrayList<>(processResultDict.values()));
            }

            MemUpdateChecker checker = new MemUpdateChecker();
            List<MemoryActionItem> actionItems = join(checker.check(newMemContent, oldMemories, llm));
            MEMORY_LOGGER.info(
                    "Memory check completed, got {} action items",
                    actionItems.size(),
                    "event_type", LogEventType.MEMORY_PROCESS,
                    "metadata", Map.of("action_count", actionItems.size())
            );

            for (MemoryActionItem actionItem : actionItems) {
                if (actionItem.status() == MemoryStatus.ADD) {
                    FragmentMemoryUnit memUnit = newMemUnits.get(actionItem.id());
                    if (memUnit != null) {
                        addMemoryUnitList.add(memUnit);
                    }
                } else if (actionItem.status() == MemoryStatus.DELETE) {
                    deleteMemoryIdSet.add(actionItem.id());
                }
            }

            deleteAndCleanProcessResults(userId, scopeId, deleteMemoryIdSet, processResultDict);
            addMemoryDocs(userId, scopeId, addMemoryUnitList);
            appendMemUnitListToDict(processResultDict, addMemoryUnitList);
            return CompletableFuture.completedFuture(new ArrayList<>(processResultDict.values()));
        } catch (BaseError baseError) {
            throw baseError;
        } catch (RuntimeException exception) {
            wrapException(exception, StatusCode.MEMORY_ADD_MEMORY_EXECUTION_ERROR, memType);
            return CompletableFuture.failedFuture(exception);
        }
    }

    @Override
    public CompletionStage<Boolean> update(String userId,
                                           String scopeId,
                                           String memId,
                                           String newMemory,
                                           Map<String, Object> kwargs) {
        validateRequiredParams(
                userId,
                scopeId,
                memoryIndex,
                StatusCode.MEMORY_UPDATE_MEMORY_EXECUTION_ERROR,
                memType
        );

        try {
            MemoryDoc oldDoc = join(memoryIndex.getById(userId, scopeId, memId));
            if (oldDoc == null) {
                return CompletableFuture.completedFuture(Boolean.FALSE);
            }
            MemoryDoc updatedDoc = new MemoryDoc(
                    memId,
                    newMemory,
                    oldDoc.getType(),
                    ZonedDateTime.now(),
                    oldDoc.getFields()
            );
            join(memoryIndex.updateMemories(userId, scopeId, List.of(updatedDoc)));
            return CompletableFuture.completedFuture(Boolean.TRUE);
        } catch (BaseError baseError) {
            throw baseError;
        } catch (RuntimeException exception) {
            wrapException(exception, StatusCode.MEMORY_UPDATE_MEMORY_EXECUTION_ERROR, memType);
            return CompletableFuture.failedFuture(exception);
        }
    }

    @Override
    public CompletionStage<List<Map<String, Object>>> search(String userId,
                                                            String scopeId,
                                                            String query,
                                                            int topK,
                                                            Map<String, Object> kwargs) {
        validateRequiredParams(
                userId,
                scopeId,
                memoryIndex,
                StatusCode.MEMORY_GET_MEMORY_EXECUTION_ERROR,
                memType
        );

        try {
            List<String> memTypes = normalizeMemTypes(kwargs == null ? null : kwargs.get("mem_types"));
            List<BaseMemoryIndex.MemorySearchResult> searchResults = join(memoryIndex.search(
                    userId,
                    scopeId,
                    query,
                    memTypes == null || memTypes.isEmpty() ? FRAGMENT_MEMORY_TYPE : memTypes,
                    topK
            ));
            List<Map<String, Object>> result = new ArrayList<>();
            if (searchResults != null) {
                for (BaseMemoryIndex.MemorySearchResult searchResult : searchResults) {
                    result.add(docToDict(searchResult.document(), searchResult.score()));
                }
            }
            result.sort(Comparator.comparingDouble((Map<String, Object> item) -> numberValue(item.get("score")))
                    .reversed());
            return CompletableFuture.completedFuture(result.size() <= topK ? result : new ArrayList<>(result.subList(0, topK)));
        } catch (BaseError baseError) {
            throw baseError;
        } catch (RuntimeException exception) {
            wrapException(exception, StatusCode.MEMORY_GET_MEMORY_EXECUTION_ERROR, memType);
            return CompletableFuture.failedFuture(exception);
        }
    }

    @Override
    public CompletionStage<Map<String, Object>> get(String userId, String scopeId, String memId) {
        validateRequiredParams(
                userId,
                scopeId,
                memoryIndex,
                StatusCode.MEMORY_GET_MEMORY_EXECUTION_ERROR,
                memType
        );

        try {
            MemoryDoc memoryDoc = join(memoryIndex.getById(userId, scopeId, memId));
            return CompletableFuture.completedFuture(memoryDoc == null ? null : docToDict(memoryDoc));
        } catch (BaseError baseError) {
            throw baseError;
        } catch (RuntimeException exception) {
            wrapException(exception, StatusCode.MEMORY_GET_MEMORY_EXECUTION_ERROR, memType);
            return CompletableFuture.failedFuture(exception);
        }
    }

    @Override
    public CompletionStage<Boolean> delete(String userId,
                                           String scopeId,
                                           String memId,
                                           Map<String, Object> kwargs) {
        validateRequiredParams(
                userId,
                scopeId,
                memoryIndex,
                StatusCode.MEMORY_DELETE_MEMORY_EXECUTION_ERROR,
                memType
        );

        try {
            MemoryDoc doc = join(memoryIndex.getById(userId, scopeId, memId));
            if (doc == null) {
                MEMORY_LOGGER.error(
                        "Delete memory failed, memory not found",
                        "event_type", LogEventType.MEMORY_STORE,
                        "memory_id", List.of(memId),
                        "user_id", userId,
                        "scope_id", scopeId
                );
                return CompletableFuture.completedFuture(Boolean.FALSE);
            }
            join(memoryIndex.deleteMemories(userId, scopeId, List.of(memId)));
            return CompletableFuture.completedFuture(Boolean.TRUE);
        } catch (BaseError baseError) {
            throw baseError;
        } catch (RuntimeException exception) {
            wrapException(exception, StatusCode.MEMORY_DELETE_MEMORY_EXECUTION_ERROR, memType);
            return CompletableFuture.failedFuture(exception);
        }
    }

    @Override
    public CompletionStage<Boolean> deleteByUserId(String userId, String scopeId, Map<String, Object> kwargs) {
        validateRequiredParams(
                userId,
                scopeId,
                memoryIndex,
                StatusCode.MEMORY_DELETE_MEMORY_EXECUTION_ERROR,
                memType
        );

        try {
            join(memoryIndex.deleteByUserAndScope(userId, scopeId));
            return CompletableFuture.completedFuture(Boolean.TRUE);
        } catch (BaseError baseError) {
            throw baseError;
        } catch (RuntimeException exception) {
            wrapException(exception, StatusCode.MEMORY_DELETE_MEMORY_EXECUTION_ERROR, memType);
            return CompletableFuture.failedFuture(exception);
        }
    }

    public CompletionStage<List<Map<String, Object>>> listFragmentMemories(String userId,
                                                                           String scopeId,
                                                                           int offset,
                                                                           int batchSize,
                                                                           MemoryType memType) {
        validateRequiredParams(
                userId,
                scopeId,
                memoryIndex,
                StatusCode.MEMORY_GET_MEMORY_EXECUTION_ERROR,
                this.memType
        );

        try {
            List<String> memTypes;
            if (memType != null) {
                if (!FRAGMENT_MEMORY_TYPE.contains(memType.getValue())) {
                    MEMORY_LOGGER.error(
                            "{} is not a valid memory type",
                            memType.getValue(),
                            "event_type", LogEventType.MEMORY_STORE,
                            "user_id", userId,
                            "scope_id", scopeId
                    );
                    return CompletableFuture.completedFuture(List.of());
                }
                memTypes = List.of(memType.getValue());
            } else {
                memTypes = FRAGMENT_MEMORY_TYPE;
            }

            List<MemoryDoc> allMemories = join(memoryIndex.listMemories(userId, scopeId, offset, batchSize, memTypes));
            if (allMemories == null || allMemories.isEmpty()) {
                return CompletableFuture.completedFuture(List.of());
            }
            List<Map<String, Object>> result = new ArrayList<>();
            for (MemoryDoc doc : allMemories) {
                result.add(docToDict(doc));
            }
            result.sort(Comparator
                    .comparing((Map<String, Object> item) -> Objects.toString(item.get("mem"), ""))
                    .thenComparing(item -> Objects.toString(item.get("timestamp"), ""))
                    .reversed());
            return CompletableFuture.completedFuture(result);
        } catch (BaseError baseError) {
            throw baseError;
        } catch (RuntimeException exception) {
            wrapException(exception, StatusCode.MEMORY_GET_MEMORY_EXECUTION_ERROR, this.memType);
            return CompletableFuture.failedFuture(exception);
        }
    }

    public CompletionStage<List<Map<String, Object>>> listFragmentMemories(String userId, String scopeId) {
        return listFragmentMemories(userId, scopeId, 0, 100, null);
    }

    Map<String, FragmentMemoryUnit> getNewMemUnitsAndUpdateMemories(
            String userId,
            String scopeId,
            Map<String, List<BaseMemoryUnit>> memories,
            Set<String> deleteMemoryIdSet,
            Map<String, FragmentMemoryUnit> processResultDict
    ) {
        Map<String, FragmentMemoryUnit> newMemUnits = new LinkedHashMap<>();
        Map<String, FragmentMemoryUnit> updateMemUnits = new LinkedHashMap<>();
        if (memories == null) {
            return newMemUnits;
        }
        for (Map.Entry<String, List<BaseMemoryUnit>> entry : memories.entrySet()) {
            String memoryType = entry.getKey();
            if (!FRAGMENT_MEMORY_TYPE.contains(memoryType)) {
                continue;
            }
            List<BaseMemoryUnit> memoryList = entry.getValue();
            if (memoryList == null) {
                continue;
            }
            for (BaseMemoryUnit memUnit : memoryList) {
                if (!(memUnit instanceof FragmentMemoryUnit fragmentMemoryUnit)) {
                    MEMORY_LOGGER.warning(
                            "mem_unit is not a FragmentMemoryUnit",
                            "event_type", LogEventType.MEMORY_STORE,
                            "memory_type", memoryType,
                            "user_id", userId,
                            "scope_id", scopeId
                    );
                    continue;
                }

                String memContent = fragmentMemoryUnit.getContent();
                String memId = fragmentMemoryUnit.getMemId();
                OperationType operationType = fragmentMemoryUnit.getOperationType();
                if (operationType == OperationType.UPDATE && hasText(memContent)) {
                    if (updateMemUnits.containsKey(memId)) {
                        MEMORY_LOGGER.warning(
                                "update memory duplicate, old will be overwrite",
                                "event_type", LogEventType.MEMORY_STORE,
                                "memory_id", memId
                        );
                    }
                    updateMemUnits.put(memId, fragmentMemoryUnit);
                } else if (operationType == OperationType.DELETE) {
                    deleteMemoryIdSet.add(memId);
                    processResultDict.put(memId, fragmentMemoryUnit);
                } else if (hasText(memContent)) {
                    newMemUnits.put(memId, fragmentMemoryUnit);
                }
            }
        }

        if (!updateMemUnits.isEmpty()) {
            try {
                List<MemoryDoc> updateDocs = new ArrayList<>();
                for (FragmentMemoryUnit memUnit : updateMemUnits.values()) {
                    updateDocs.add(convertToMemoryDoc(memUnit));
                }
                join(memoryIndex.updateMemories(userId, scopeId, updateDocs));
                processResultDict.putAll(updateMemUnits);
            } catch (BaseError baseError) {
                throw baseError;
            } catch (RuntimeException exception) {
                wrapException(exception, StatusCode.MEMORY_UPDATE_MEMORY_EXECUTION_ERROR, memType);
            }
        }

        return newMemUnits;
    }

    Map<String, String> getRelatedOldMemories(Map<String, String> newMemContent, String userId, String scopeId) {
        Map<String, String> oldMemories = new LinkedHashMap<>();
        Set<String> oldMemIds = new HashSet<>();
        if (newMemContent == null) {
            return oldMemories;
        }
        for (String newMemory : newMemContent.values()) {
            List<Map<String, Object>> searchResults = join(search(
                    userId,
                    scopeId,
                    newMemory,
                    UPDATE_CHECK_OLD_MEMORY_NUM,
                    Map.of()
            ));
            if (searchResults == null) {
                continue;
            }
            for (Map<String, Object> result : searchResults) {
                String resultId = Objects.toString(result.get("id"), "");
                double resultScore = numberValue(result.get("score"));
                String resultContent = Objects.toString(result.get("mem"), "");
                if (!resultId.isEmpty()
                        && resultScore > UPDATE_CHECK_OLD_MEMORY_RELEVANCE_THRESHOLD
                        && !oldMemIds.contains(resultId)) {
                    oldMemories.put(resultId, resultContent);
                    oldMemIds.add(resultId);
                }
            }
        }
        return oldMemories;
    }

    CompletionStage<Void> addMemoryToStore(String userId, String scopeId, FragmentMemoryUnit memory) {
        if (!hasText(userId)) {
            throw ErrorHelper.buildError(
                    StatusCode.MEMORY_ADD_MEMORY_EXECUTION_ERROR,
                    "memory_type", memoryTypeValue(memory.getMemType()),
                    "error_msg", "user_id is required"
            );
        }
        if (!hasText(scopeId)) {
            throw ErrorHelper.buildError(
                    StatusCode.MEMORY_ADD_MEMORY_EXECUTION_ERROR,
                    "memory_type", memoryTypeValue(memory.getMemType()),
                    "error_msg", "scope_id is required"
            );
        }
        if (!hasText(memory.getContent())) {
            throw ErrorHelper.buildError(
                    StatusCode.MEMORY_ADD_MEMORY_EXECUTION_ERROR,
                    "memory_type", memoryTypeValue(memory.getMemType()),
                    "error_msg", "content is required"
            );
        }

        MEMORY_LOGGER.debug(
                "Add memory",
                "memory_type", memoryTypeValue(memory.getMemType()),
                "event_type", LogEventType.MEMORY_STORE,
                "user_id", userId,
                "scope_id", scopeId
        );
        MemoryDoc memoryDoc = convertToMemoryDoc(memory);
        return memoryIndex.addMemories(userId, scopeId, List.of(memoryDoc));
    }

    private void deleteAndCleanProcessResults(String userId,
                                              String scopeId,
                                              Set<String> deleteMemoryIdSet,
                                              Map<String, FragmentMemoryUnit> processResultDict) {
        if (deleteMemoryIdSet != null && !deleteMemoryIdSet.isEmpty()) {
            join(memoryIndex.deleteMemories(userId, scopeId, new ArrayList<>(deleteMemoryIdSet)));
            removeUpdateEntriesFromProcessResult(deleteMemoryIdSet, processResultDict);
        }
    }

    private void addMemoryDocs(String userId, String scopeId, List<FragmentMemoryUnit> addMemoryUnitList) {
        if (addMemoryUnitList == null || addMemoryUnitList.isEmpty()) {
            return;
        }
        List<MemoryDoc> addDocs = new ArrayList<>();
        for (FragmentMemoryUnit memUnit : addMemoryUnitList) {
            addDocs.add(convertToMemoryDoc(memUnit));
        }
        join(memoryIndex.addMemories(userId, scopeId, addDocs));
    }

    private static List<String> normalizeMemTypes(Object rawValue) {
        if (rawValue == null) {
            return null;
        }
        List<String> result = new ArrayList<>();
        if (rawValue instanceof MemoryType memoryType) {
            result.add(memoryType.getValue());
        } else if (rawValue instanceof String text) {
            result.add(text);
        } else if (rawValue instanceof Iterable<?> iterable) {
            for (Object item : iterable) {
                if (item instanceof MemoryType memoryType) {
                    result.add(memoryType.getValue());
                } else if (item != null) {
                    result.add(String.valueOf(item));
                }
            }
        } else {
            result.add(String.valueOf(rawValue));
        }
        return result;
    }

    private static String memoryTypeValue(MemoryType memoryType) {
        return memoryType == null ? MemoryType.UNKNOWN.getValue() : memoryType.getValue();
    }

    private static boolean hasText(String value) {
        return value != null && !value.isEmpty();
    }

    private static double numberValue(Object value) {
        return value instanceof Number number ? number.doubleValue() : 0.0d;
    }

    private static <T> T join(CompletionStage<T> stage) {
        try {
            return stage.toCompletableFuture().join();
        } catch (CompletionException exception) {
            if (exception.getCause() instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw exception;
        }
    }
}

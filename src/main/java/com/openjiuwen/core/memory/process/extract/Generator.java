/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.process.extract;

import com.openjiuwen.core.common.VirtualThreadSupport;
import com.openjiuwen.core.common.logging.LoggerProtocol;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.common.logging.events.LogEventType;
import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import com.openjiuwen.core.memory.config.AgentMemoryConfig;
import com.openjiuwen.core.memory.config.MemoryScopeConfig;
import com.openjiuwen.core.memory.manage.mem_model.BaseMemoryUnit;
import com.openjiuwen.core.memory.manage.mem_model.DataIdManager;
import com.openjiuwen.core.memory.manage.mem_model.FragmentMemoryUnit;
import com.openjiuwen.core.memory.manage.mem_model.MemoryType;
import com.openjiuwen.core.memory.manage.mem_model.OperationType;
import com.openjiuwen.core.memory.manage.mem_model.SummaryUnit;
import com.openjiuwen.core.memory.manage.mem_model.VariableUnit;
import com.openjiuwen.core.memory.manage.search.SearchManager;
import com.openjiuwen.core.memory.manage.search.SearchParams;
import com.openjiuwen.core.memory.prompts.PromptApplier;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Mirrors Python's {@code Generator} in
 * {@code openjiuwen/core/memory/process/extract/generation.py}.
 */
public class Generator {

    private static final LoggerProtocol MEMORY_LOGGER = Loggers.MEMORY;
    private static final Map<String, MemoryType> CATEGORY_TO_CLASS = Map.of(
            "user_profile", MemoryType.USER_PROFILE,
            "semantic_memory", MemoryType.SEMANTIC_MEMORY,
            "episodic_memory", MemoryType.EPISODIC_MEMORY
    );
    private static final Map<String, OperationType> OPERATION_STR_TO_ENUM = Map.of(
            OperationType.ADD.getValue(), OperationType.ADD,
            OperationType.UPDATE.getValue(), OperationType.UPDATE,
            OperationType.DELETE.getValue(), OperationType.DELETE
    );
    private static final java.util.concurrent.Executor IO_EXECUTOR =
            VirtualThreadSupport.newThreadPerTaskExecutor("memory-generator-io");

    private final DataIdManager dataIdGenerator;
    private final SearchManager searchManager;

    public Generator(DataIdManager dataIdGenerator) {
        this(dataIdGenerator, null);
    }

    public Generator(DataIdManager dataIdGenerator, SearchManager searchManager) {
        this.dataIdGenerator = dataIdGenerator;
        this.searchManager = searchManager;
    }

    public CompletionStage<Map<String, List<BaseMemoryUnit>>> genAllMemory(Map<String, Object> kwargs) {
        return CompletableFuture.supplyAsync(() -> genAllMemorySync(kwargs == null ? Map.of() : kwargs), IO_EXECUTOR);
    }

    @SuppressWarnings("unchecked")
    private Map<String, List<BaseMemoryUnit>> genAllMemorySync(Map<String, Object> kwargs) {
        List<BaseMessage> messages = (List<BaseMessage>) kwargs.get("messages");
        AgentMemoryConfig config = (AgentMemoryConfig) kwargs.get("config");
        Model model = (Model) kwargs.get("base_chat_model");
        String userId = (String) kwargs.get("user_id");
        String scopeId = (String) kwargs.get("scope_id");
        List<BaseMessage> historyMessages = (List<BaseMessage>) kwargs.get("history_messages");
        String forbiddenVariables = (String) kwargs.get("forbidden_variables");
        String messageMemId = (String) kwargs.get("message_mem_id");
        String timestamp = (String) kwargs.get("timestamp");
        Integer summaryMaxToken = (Integer) kwargs.get("summary_max_token");
        MemoryScopeConfig scopeConfig = (MemoryScopeConfig) kwargs.get("scope_config");
        Object semanticStore = kwargs.get("semantic_store");

        if (isEmpty(messages) || config == null || isEmpty(userId) || isEmpty(scopeId) || model == null) {
            MEMORY_LOGGER.error("[{}] Messages, config, user_id, scope_id, model are required parameters",
                    LogEventType.MEMORY_PROCESS.getValue());
            return Map.of();
        }

        ExtractMemoryParams extractMemoryParams = new ExtractMemoryParams(
                userId,
                scopeId,
                messages,
                historyMessages,
                model
        );
        Map<String, List<BaseMemoryUnit>> allMemoryResults = new LinkedHashMap<>();
        MemoryAnalyzerResult analyzeResult = MemoryAnalyzer.analyze(
                messages,
                historyMessages,
                model,
                config,
                summaryMaxToken,
                scopeConfig,
                forbiddenVariables == null ? "" : forbiddenVariables
        ).toCompletableFuture().join();
        if (analyzeResult == null) {
            return allMemoryResults;
        }

        for (VariableUnit unit : processExtractedData(analyzeResult.getVariables())) {
            addMemoryUnit(allMemoryResults, unit);
        }

        if (!config.isEnableLongTermMem()) {
            MEMORY_LOGGER.info("[{}] Not enable long term memory", LogEventType.MEMORY_PROCESS.getValue());
            return allMemoryResults;
        }

        if (config.isEnableSummaryMemory()) {
            SummaryUnit summaryUnit = processSummaryData(userId, messageMemId, analyzeResult.getSummary(), timestamp);
            addMemoryUnit(allMemoryResults, summaryUnit);
        }

        if (!analyzeResult.isHasKeyInformation()) {
            return allMemoryResults;
        }

        Map<String, Boolean> fragmentEnable = Map.of(
                MemoryType.USER_PROFILE.getValue(), config.isEnableUserProfile(),
                MemoryType.SEMANTIC_MEMORY.getValue(), config.isEnableSemanticMemory(),
                MemoryType.EPISODIC_MEMORY.getValue(), config.isEnableEpisodicMemory()
        );

        try {
            List<BaseMemoryUnit> mergedUnits = categoriesToMemoryUnit(
                    extractMemoryParams,
                    messageMemId,
                    timestamp,
                    scopeConfig,
                    semanticStore
            );
            for (BaseMemoryUnit unit : mergedUnits) {
                String memType = unit.getMemType() == null ? null : unit.getMemType().getValue();
                if (Boolean.TRUE.equals(fragmentEnable.get(memType))) {
                    addMemoryUnit(allMemoryResults, unit);
                }
            }
        } catch (NullPointerException exception) {
            MEMORY_LOGGER.debug("[{}] Get conflict info has attribute exception: {}",
                    LogEventType.MEMORY_PROCESS.getValue(), exception.toString());
            return allMemoryResults;
        } catch (IllegalArgumentException exception) {
            MEMORY_LOGGER.warning("[{}] Get conflict info has value exception: {}",
                    LogEventType.MEMORY_PROCESS.getValue(), exception.toString());
            return allMemoryResults;
        } catch (RuntimeException exception) {
            MEMORY_LOGGER.warning("[{}] Get conflict info has exception: {}",
                    LogEventType.MEMORY_PROCESS.getValue(), exception.toString());
            return allMemoryResults;
        }

        MEMORY_LOGGER.info("[{}] Memory units generated successfully", LogEventType.MEMORY_PROCESS.getValue());
        return allMemoryResults;
    }

    private List<BaseMemoryUnit> categoriesToMemoryUnit(
            ExtractMemoryParams extractMemoryParas,
            String messageMemId,
            String timestamp,
            MemoryScopeConfig scopeConfig,
            Object semanticStore
    ) {
        List<BaseMemoryUnit> memoryUnits = new ArrayList<>();
        Map<String, Object> memoryDict = LongTermMemoryExtractor.extractLongTermMemory(
                extractMemoryParas,
                timestamp,
                scopeConfig
        ).toCompletableFuture().join();

        if (booleanValue(memoryDict.get("has_explict_instruct"))) {
            List<Map<String, Object>> instructMemories = listOfMaps(memoryDict.get("instruct_memories"));
            MemoryOperationParams memoryOperationParams = new MemoryOperationParams(
                    extractMemoryParas.getUserId(),
                    extractMemoryParas.getScopeId(),
                    messageMemId,
                    timestamp,
                    extractMemoryParas.getBaseChatModel(),
                    semanticStore
            );
            memoryUnits.addAll(handleMemoryWithInstruct(memoryOperationParams, instructMemories));
        }

        memoryUnits.addAll(getFragmentMemoryUnit(
                extractMemoryParas.getUserId(),
                messageMemId,
                memoryDict,
                timestamp
        ));
        return memoryUnits;
    }

    static List<VariableUnit> processExtractedData(List<VariableResult> variableResults) {
        List<VariableUnit> variableUnits = new ArrayList<>();
        if (variableResults == null) {
            return variableUnits;
        }
        for (VariableResult tmpData : variableResults) {
            if (isEmpty(tmpData.getVariableValue())) {
                continue;
            }
            variableUnits.add(new VariableUnit(tmpData.getVariableKey(), tmpData.getVariableValue()));
        }
        return variableUnits;
    }

    private SummaryUnit processSummaryData(String userId, String messageMemId, String summary, String timestamp) {
        String memId = dataIdGenerator.generateNextId(userId).toCompletableFuture().join();
        return new SummaryUnit(memId, summary, messageMemId, timestamp);
    }

    private List<FragmentMemoryUnit> getFragmentMemoryUnit(
            String userId,
            String messageMemId,
            Map<String, Object> memoryDict,
            String timestamp
    ) {
        List<FragmentMemoryUnit> fragmentMemUnits = new ArrayList<>();
        for (Map.Entry<String, Object> entry : memoryDict.entrySet()) {
            MemoryType memType = CATEGORY_TO_CLASS.get(entry.getKey());
            if (memType == null) {
                continue;
            }
            for (Object rawContent : listValue(entry.getValue())) {
                Object memContent = rawContent;
                if (!(memContent instanceof String)) {
                    Object content = memContent instanceof Map<?, ?> map ? map.get("content") : null;
                    memContent = isEmpty(content) ? String.valueOf(memContent) : content;
                }
                String memId = dataIdGenerator.generateNextId(userId).toCompletableFuture().join();
                fragmentMemUnits.add(new FragmentMemoryUnit(
                        memType,
                        memId,
                        String.valueOf(memContent),
                        messageMemId,
                        timestamp,
                        OperationType.ADD
                ));
            }
        }
        return fragmentMemUnits;
    }

    private List<FragmentMemoryUnit> processProactiveMemoryData(
            String userId,
            String messageMemId,
            List<?> memoryList,
            String timestamp
    ) {
        List<FragmentMemoryUnit> fragmentMemUnits = new ArrayList<>();
        for (Object item : memoryList) {
            if (!(item instanceof Map<?, ?> memDict)) {
                continue;
            }
            String memInstruct = stringOrDefault(memDict.get("mem_instruct"), "").toLowerCase(Locale.ROOT);
            OperationType operationType = OPERATION_STR_TO_ENUM.get(memInstruct);
            if (operationType != OperationType.ADD) {
                continue;
            }
            MemoryType memType = CATEGORY_TO_CLASS.get(memDict.get("mem_type"));
            if (memType == null || isEmpty(memDict.get("mem_content"))) {
                continue;
            }
            Object memContent = memDict.get("mem_content");
            if (!(memContent instanceof String)) {
                Object content = memContent instanceof Map<?, ?> map ? map.get("mem_content") : null;
                memContent = isEmpty(content) ? String.valueOf(memContent) : content;
            }
            String memId = dataIdGenerator.generateNextId(userId).toCompletableFuture().join();
            fragmentMemUnits.add(new FragmentMemoryUnit(
                    memType,
                    memId,
                    String.valueOf(memContent),
                    messageMemId,
                    timestamp,
                    operationType
            ));
        }
        return fragmentMemUnits;
    }

    private List<FragmentMemoryUnit> handleMemoryWithInstruct(
            MemoryOperationParams memoryOperationParams,
            List<Map<String, Object>> memoryList
    ) {
        List<Map<String, Object>> updateMemories = new ArrayList<>();
        List<Map<String, Object>> deleteMemories = new ArrayList<>();
        for (Map<String, Object> memDict : memoryList) {
            String memInstruct = stringOrDefault(memDict.get("mem_instruct"), "").toLowerCase(Locale.ROOT);
            OperationType operationType = OPERATION_STR_TO_ENUM.get(memInstruct);
            if (operationType == OperationType.UPDATE) {
                updateMemories.add(memDict);
            } else if (operationType == OperationType.DELETE) {
                deleteMemories.add(memDict);
            }
        }

        List<FragmentMemoryUnit> retMemories = new ArrayList<>();
        retMemories.addAll(processMemoryOperations(memoryOperationParams, updateMemories, OperationType.UPDATE));
        retMemories.addAll(processMemoryOperations(memoryOperationParams, deleteMemories, OperationType.DELETE));
        return retMemories;
    }

    private List<FragmentMemoryUnit> processMemoryOperations(
            MemoryOperationParams memoryOperationParams,
            List<Map<String, Object>> memoryDicts,
            OperationType operationType
    ) {
        List<FragmentMemoryUnit> retMemories = new ArrayList<>();
        for (Map<String, Object> memDict : memoryDicts) {
            String oldMem = stringOrDefault(memDict.get("old_mem"), "");
            if (oldMem.isEmpty()) {
                continue;
            }

            SearchParams params = SearchParams.builder()
                    .userId(memoryOperationParams.getUserId())
                    .scopeId(memoryOperationParams.getScopeId())
                    .query(oldMem)
                    .topK(1)
                    .searchType(List.of(
                            MemoryType.USER_PROFILE.getValue(),
                            MemoryType.EPISODIC_MEMORY.getValue(),
                            MemoryType.SEMANTIC_MEMORY.getValue()
                    ))
                    .build();
            List<Map<String, Object>> searchData = searchManager.search(
                    params,
                    mapValue(memoryOperationParams.getSemanticStore())
            ).toCompletableFuture().join();
            searchData = searchData == null ? new ArrayList<>() : new ArrayList<>(searchData);
            searchData.sort(Comparator.comparingDouble(Generator::scoreValue).reversed());
            List<Map<String, Object>> obtainedMem = searchData.isEmpty()
                    ? List.of()
                    : List.of(searchData.get(0));
            if (obtainedMem.isEmpty()) {
                continue;
            }

            List<MemoryMatch> memIds = semanticValidation(
                    obtainedMem,
                    oldMem,
                    memoryOperationParams.getBaseChatModel()
            );
            for (MemoryMatch memId : memIds) {
                String memTypeStr = stringOrDefault(memDict.get("mem_type"), "").toLowerCase(Locale.ROOT);
                retMemories.add(new FragmentMemoryUnit(
                        CATEGORY_TO_CLASS.get(memTypeStr),
                        memId.id(),
                        stringOrDefault(memDict.get("mem_content"), ""),
                        memoryOperationParams.getMessageMemId(),
                        memoryOperationParams.getTimestamp(),
                        operationType
                ));
            }
        }
        return retMemories;
    }

    private List<MemoryMatch> semanticValidation(
            List<Map<String, Object>> obtainedMems,
            String oldMem,
            Model baseChatModel
    ) {
        List<MemoryMatch> retIds = new ArrayList<>();
        for (Map<String, Object> obtainedMem : obtainedMems) {
            String mem = stringOrDefault(obtainedMem.get("mem"), "");
            String promptContent = new PromptApplier().apply(
                    "semantic_validation",
                    Map.of("obtained_mem", mem, "old_mem", oldMem)
            );
            AssistantMessage response = baseChatModel.invoke(List.of(new UserMessage(promptContent)))
                    .toCompletableFuture().join();
            String responseContent = response.getContentAsString().toUpperCase(Locale.ROOT);
            if (responseContent.contains("CORRECT") && !responseContent.contains("WRONG")) {
                MEMORY_LOGGER.debug("[{}] semantic_validate_result: old_mem:{}, obtained_mem:{}, result: CORRECT",
                        LogEventType.MEMORY_PROCESS.getValue(), oldMem, mem);
                retIds.add(new MemoryMatch(stringOrDefault(obtainedMem.get("id"), ""), mem));
            } else {
                MEMORY_LOGGER.debug("[{}] semantic_validate_result: old_mem:{}, obtained_mem:{}, result: WRONG",
                        LogEventType.MEMORY_PROCESS.getValue(), oldMem, mem);
            }
        }
        return retIds;
    }

    private static void addMemoryUnit(Map<String, List<BaseMemoryUnit>> memoryResults, BaseMemoryUnit unit) {
        String memType = unit.getMemType() == null ? null : unit.getMemType().getValue();
        memoryResults.computeIfAbsent(memType, ignored -> new ArrayList<>()).add(unit);
    }

    private static List<Object> listValue(Object value) {
        if (value instanceof List<?> list) {
            return new ArrayList<>(list);
        }
        return List.of();
    }

    private static List<Map<String, Object>> listOfMaps(Object value) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (!(value instanceof List<?> list)) {
            return result;
        }
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                Map<String, Object> normalized = new LinkedHashMap<>();
                map.forEach((key, entryValue) -> normalized.put(String.valueOf(key), entryValue));
                result.add(normalized);
            }
        }
        return result;
    }

    private static Map<String, Object> mapValue(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return Map.of();
        }
        Map<String, Object> normalized = new LinkedHashMap<>();
        map.forEach((key, entryValue) -> normalized.put(String.valueOf(key), entryValue));
        return normalized;
    }

    private static boolean booleanValue(Object value) {
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        if (value instanceof String stringValue) {
            return "true".equals(stringValue.toLowerCase(Locale.ROOT));
        }
        return false;
    }

    private static double scoreValue(Map<String, Object> item) {
        Object score = item.get("score");
        return score instanceof Number number ? number.doubleValue() : 0.0d;
    }

    private static boolean isEmpty(List<?> value) {
        return value == null || value.isEmpty();
    }

    private static boolean isEmpty(String value) {
        return value == null || value.isEmpty();
    }

    private static boolean isEmpty(Object value) {
        if (value == null) {
            return true;
        }
        if (value instanceof String stringValue) {
            return stringValue.isEmpty();
        }
        if (value instanceof List<?> listValue) {
            return listValue.isEmpty();
        }
        return false;
    }

    private static String stringOrDefault(Object value, String defaultValue) {
        return value == null ? defaultValue : String.valueOf(value);
    }

    private record MemoryMatch(String id, String mem) {
    }
}

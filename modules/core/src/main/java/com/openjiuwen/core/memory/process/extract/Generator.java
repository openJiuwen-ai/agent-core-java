/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.process.extract;

import com.openjiuwen.core.common.logging.LoggerProtocol;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.common.logging.events.LogEventType;
import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.memory.config.AgentMemoryConfig;
import com.openjiuwen.core.memory.manage.mem_model.BaseMemoryUnit;
import com.openjiuwen.core.memory.manage.mem_model.DataIdManager;
import com.openjiuwen.core.memory.manage.mem_model.FragmentMemoryUnit;
import com.openjiuwen.core.memory.manage.mem_model.MemoryType;
import com.openjiuwen.core.memory.manage.mem_model.SummaryUnit;
import com.openjiuwen.core.memory.manage.mem_model.VariableUnit;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Generates all memory units (variables, summary, fragment) from conversation messages.
 * 
 * @since 0.1.7
 */
public class Generator {
    private static final LoggerProtocol MEMORY_LOGGER = Loggers.MEMORY;

    private final DataIdManager dataIdGenerator;

    /**
     * Generator.
     * 
     * @param dataIdGenerator dataIdGenerator
     * @since 0.1.7
     */
    public Generator(DataIdManager dataIdGenerator) {
        this.dataIdGenerator = dataIdGenerator;
    }

    /**
     * genAllMemory.
     * 
     * @param kwargs kwargs
     * @return the result
     * @since 0.1.7
     */
    public Map<String, List<BaseMemoryUnit>> genAllMemory(Map<String, Object> kwargs) {
        List<BaseMessage> messages = kwargs.get("messages") instanceof List<?> rawMessages
                ? rawMessages.stream().filter(BaseMessage.class::isInstance).map(BaseMessage.class::cast).toList()
                : List.of();
        AgentMemoryConfig config = kwargs.get("config") instanceof AgentMemoryConfig cfg ? cfg : null;
        @SuppressWarnings("unchecked")
        Map.Entry<String, Model> model =
            kwargs.get("base_chat_model") instanceof Map.Entry<?, ?> rawModel && rawModel.getKey() instanceof String
                    && rawModel.getValue() instanceof Model ? (Map.Entry<String, Model>) rawModel : null;
        String userId = kwargs.get("user_id") instanceof String value ? value : null;
        String scopeId = kwargs.get("scope_id") instanceof String value ? value : null;
        List<BaseMessage> historyMessages = kwargs.get("history_messages") instanceof List<?> rawHistory
                ? rawHistory.stream().filter(BaseMessage.class::isInstance).map(BaseMessage.class::cast).toList()
                : List.of();
        String messageMemId = kwargs.get("message_mem_id") instanceof String value ? value : null;
        String timestamp = kwargs.get("timestamp") instanceof String value ? value : null;
        Integer summaryMaxToken = kwargs.get("summary_max_token") instanceof Integer value ? value : null;
        String forbiddenVariables = kwargs.get("forbidden_variables") instanceof String value ? value : null;

        if (hasMissingRequiredParameters(messages, config, model, userId, scopeId)) {
            MEMORY_LOGGER.error("[{}] Messages, config, user_id, scope_id, model are required parameters",
                    LogEventType.MEMORY_PROCESS);
            return Map.of();
        }

        ExtractMemoryParams extractParams = ExtractMemoryParams.builder().userId(userId).scopeId(scopeId)
                .messages(messages).historyMessages(historyMessages).baseChatModel(model).build();

        Map<String, List<BaseMemoryUnit>> allMemoryResults = new HashMap<>();

        // Analyze memories
        MemoryAnalyzerResult analyzeRes = MemoryAnalyzer.analyze(messages, historyMessages, model, config,
                summaryMaxToken != null ? summaryMaxToken : 128, forbiddenVariables);

        if (analyzeRes == null) {
            return allMemoryResults;
        }

        // Process variable results
        List<VariableUnit> variableUnits = processExtractedData(analyzeRes.getVariables());
        for (VariableUnit unit : variableUnits) {
            String memType = unit.getMemType().getValue();
            allMemoryResults.computeIfAbsent(memType, k -> new ArrayList<>()).add(unit);
        }

        if (!config.isEnableLongTermMem()) {
            MEMORY_LOGGER.info("[{}] Not enable long term memory", LogEventType.MEMORY_PROCESS);
            return allMemoryResults;
        }

        // Process summary data
        if (config.isEnableSummaryMemory()) {
            SummaryUnit summaryUnit = processSummaryData(userId, messageMemId, analyzeRes.getSummary(), timestamp);
            String summaryType = summaryUnit.getMemType().getValue();
            allMemoryResults.computeIfAbsent(summaryType, k -> new ArrayList<>()).add(summaryUnit);
        }

        if (!analyzeRes.isHasKeyInformation() || !config.isEnableFragmentMemory()) {
            return allMemoryResults;
        }

        // Process fragment memories
        try {
            List<BaseMemoryUnit> mergedUnits = categoriesToMemoryUnit(extractParams, messageMemId, timestamp, config);
            for (BaseMemoryUnit unit : mergedUnits) {
                String memType = unit.getMemType().getValue();
                allMemoryResults.computeIfAbsent(memType, k -> new ArrayList<>()).add(unit);
            }
        } catch (Exception e) {
            MEMORY_LOGGER.warn("[{}] Get conflict info has exception: {}", LogEventType.MEMORY_PROCESS, e.getMessage());
            return allMemoryResults;
        }

        MEMORY_LOGGER.info("[{}] Memory units generated successfully", LogEventType.MEMORY_PROCESS);
        return allMemoryResults;
    }

    private static boolean hasMissingRequiredParameters(List<BaseMessage> messages, AgentMemoryConfig config,
            Map.Entry<String, Model> model, String userId, String scopeId) {
        if (messages.isEmpty() || config == null || model == null) {
            return true;
        }
        return isBlank(userId) || isBlank(scopeId);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /**
     * categoriesToMemoryUnit.
     * 
     * @param params params
     * @param messageMemId messageMemId
     * @param timestamp timestamp
     * @param config config
     * @return the result
     * @since 0.1.7
     */
    private List<BaseMemoryUnit> categoriesToMemoryUnit(ExtractMemoryParams params, String messageMemId,
            String timestamp, AgentMemoryConfig config) {
        List<BaseMemoryUnit> memoryUnits = new ArrayList<>();
        Map<String, List<Object>> memoryDict = LongTermMemoryExtractor.extractLongTermMemory(params, timestamp);
        memoryUnits.addAll(getFragmentMemoryUnits(params.getUserId(), messageMemId, memoryDict, timestamp, config));
        return memoryUnits;
    }

    /**
     * processExtractedData.
     * 
     * @param variableResults variableResults
     * @return the result
     * @since 0.1.7
     */
    private static List<VariableUnit> processExtractedData(List<VariableResult> variableResults) {
        List<VariableUnit> variableUnits = new ArrayList<>();
        if (variableResults == null) {
            return variableUnits;
        }
        for (VariableResult tmp : variableResults) {
            if (tmp.getVariableValue() == null || tmp.getVariableValue().isEmpty()) {
                continue;
            }
            variableUnits.add(VariableUnit.builder().variableName(tmp.getVariableKey())
                    .variableMem(tmp.getVariableValue()).build());
        }
        return variableUnits;
    }

    /**
     * processSummaryData.
     * 
     * @param userId userId
     * @param messageMemId messageMemId
     * @param summary summary
     * @param timestamp timestamp
     * @return the result
     * @since 0.1.7
     */
    private SummaryUnit processSummaryData(String userId, String messageMemId, String summary, String timestamp) {
        String memId = dataIdGenerator.generateNextId(userId);
        return SummaryUnit.builder().memId(memId).summary(summary).messageMemId(messageMemId).timestamp(timestamp)
                .build();
    }

    /**
     * getFragmentMemoryUnits.
     * 
     * @param userId userId
     * @param messageMemId messageMemId
     * @param memoryDict memoryDict
     * @param timestamp timestamp
     * @param config config
     * @return the result
     * @since 0.1.7
     */
    private List<FragmentMemoryUnit> getFragmentMemoryUnits(String userId, String messageMemId,
            Map<String, List<Object>> memoryDict, String timestamp, AgentMemoryConfig config) {
        List<FragmentMemoryUnit> fragmentUnits = new ArrayList<>();
        if (memoryDict == null) {
            return fragmentUnits;
        }
        for (Map.Entry<String, List<Object>> entry : memoryDict.entrySet()) {
            MemoryType memoryType = MemoryType.fromValue(entry.getKey());
            if (memoryType == MemoryType.UNKNOWN) {
                continue;
            }
            if (!config.isMemoryTypeEnabled(memoryType.getValue())) {
                continue;
            }
            for (Object item : entry.getValue()) {
                String memContent = normalizeFragmentContent(item);
                String memId = dataIdGenerator.generateNextId(userId);
                fragmentUnits.add(FragmentMemoryUnit.builder().memType(memoryType).content(memContent)
                        .messageMemId(messageMemId).timestamp(timestamp).memId(memId).build());
            }
        }
        return fragmentUnits;
    }

    /**
     * normalizeFragmentContent.
     * 
     * @param item item
     * @return the result
     * @since 0.1.7
     */
    private static String normalizeFragmentContent(Object item) {
        if (item instanceof String text) {
            return text;
        }
        if (item instanceof Map<?, ?> map) {
            Object content = map.get("content");
            if (content != null && !String.valueOf(content).isEmpty()) {
                return String.valueOf(content);
            }
        }
        return String.valueOf(item);
    }
}

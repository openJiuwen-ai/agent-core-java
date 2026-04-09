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
import com.openjiuwen.core.memory.manage.mem_model.DataIdManager;
import com.openjiuwen.core.memory.manage.mem_model.FragmentMemoryUnit;
import com.openjiuwen.core.memory.manage.mem_model.BaseMemoryUnit;
import com.openjiuwen.core.memory.manage.mem_model.MemoryType;
import com.openjiuwen.core.memory.manage.mem_model.SummaryUnit;
import com.openjiuwen.core.memory.manage.mem_model.VariableUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Generates all memory units (variables, summary, fragment) from conversation messages.
 */
public class Generator {

    private static final LoggerProtocol MEMORY_LOGGER = Loggers.MEMORY;

    private final DataIdManager dataIdGenerator;

    public Generator(DataIdManager dataIdGenerator) {
        this.dataIdGenerator = dataIdGenerator;
    }

    public Map<String, List<BaseMemoryUnit>> genAllMemory(Map<String, Object> kwargs) {
        @SuppressWarnings("unchecked")
        List<BaseMessage> messages = (List<BaseMessage>) kwargs.get("messages");
        AgentMemoryConfig config = (AgentMemoryConfig) kwargs.get("config");
        @SuppressWarnings("unchecked")
        Map.Entry<String, Model> model = (Map.Entry<String, Model>) kwargs.get("base_chat_model");
        String userId = (String) kwargs.get("user_id");
        String scopeId = (String) kwargs.get("scope_id");
        @SuppressWarnings("unchecked")
        List<BaseMessage> historyMessages = (List<BaseMessage>) kwargs.get("history_messages");
        String messageMemId = (String) kwargs.get("message_mem_id");
        String timestamp = (String) kwargs.get("timestamp");
        Integer summaryMaxToken = (Integer) kwargs.get("summary_max_token");

        if (messages == null || config == null || userId == null || scopeId == null || model == null) {
            MEMORY_LOGGER.error("[{}] Messages, config, user_id, scope_id, model are required parameters",
                    LogEventType.MEMORY_PROCESS);
            return Map.of();
        }

        ExtractMemoryParams extractParams = ExtractMemoryParams.builder()
                .userId(userId)
                .scopeId(scopeId)
                .messages(messages)
                .historyMessages(historyMessages)
                .baseChatModel(model)
                .build();

        Map<String, List<BaseMemoryUnit>> allMemoryResults = new HashMap<>();

        // Analyze memories
        MemoryAnalyzerResult analyzeRes = MemoryAnalyzer.analyze(
                messages, historyMessages, model, config,
                summaryMaxToken != null ? summaryMaxToken : 128);

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
            SummaryUnit summaryUnit = processSummaryData(
                    userId, messageMemId, analyzeRes.getSummary(), timestamp);
            String summaryType = summaryUnit.getMemType().getValue();
            allMemoryResults.computeIfAbsent(summaryType, k -> new ArrayList<>()).add(summaryUnit);
        }

        if (!analyzeRes.isHasKeyInformation() || !config.isEnableFragmentMemory()) {
            return allMemoryResults;
        }

        // Process fragment memories
        try {
            List<BaseMemoryUnit> mergedUnits = categoriesToMemoryUnit(extractParams, messageMemId, timestamp);
            for (BaseMemoryUnit unit : mergedUnits) {
                String memType = unit.getMemType().getValue();
                allMemoryResults.computeIfAbsent(memType, k -> new ArrayList<>()).add(unit);
            }
        } catch (Exception e) {
            MEMORY_LOGGER.warn("[{}] Get conflict info has exception: {}",
                    LogEventType.MEMORY_PROCESS, e.getMessage());
            return allMemoryResults;
        }

        MEMORY_LOGGER.info("[{}] Memory units generated successfully", LogEventType.MEMORY_PROCESS);
        return allMemoryResults;
    }

    private List<BaseMemoryUnit> categoriesToMemoryUnit(
            ExtractMemoryParams params, String messageMemId, String timestamp) {
        List<BaseMemoryUnit> memoryUnits = new ArrayList<>();
        Map<String, List<String>> memoryDict = LongTermMemoryExtractor.extractLongTermMemory(params, timestamp);
        memoryUnits.addAll(getFragmentMemoryUnits(params.getUserId(), messageMemId, memoryDict, timestamp));
        return memoryUnits;
    }

    private static List<VariableUnit> processExtractedData(List<VariableResult> variableResults) {
        List<VariableUnit> variableUnits = new ArrayList<>();
        if (variableResults == null) return variableUnits;
        for (VariableResult tmp : variableResults) {
            if (tmp.getVariableValue() == null || tmp.getVariableValue().isEmpty()) {
                continue;
            }
            variableUnits.add(VariableUnit.builder()
                    .variableName(tmp.getVariableKey())
                    .variableMem(tmp.getVariableValue())
                    .build());
        }
        return variableUnits;
    }

    private SummaryUnit processSummaryData(String userId, String messageMemId,
                                           String summary, String timestamp) {
        String memId = dataIdGenerator.generateNextId(userId);
        return SummaryUnit.builder()
                .memId(memId)
                .summary(summary)
                .messageMemId(messageMemId)
                .timestamp(timestamp)
                .build();
    }

    private List<FragmentMemoryUnit> getFragmentMemoryUnits(
            String userId, String messageMemId,
            Map<String, List<String>> memoryDict, String timestamp) {
        List<FragmentMemoryUnit> fragmentUnits = new ArrayList<>();
        if (memoryDict == null) return fragmentUnits;
        for (Map.Entry<String, List<String>> entry : memoryDict.entrySet()) {
            String fragmentType = entry.getKey();
            for (String memContent : entry.getValue()) {
                String memId = dataIdGenerator.generateNextId(userId);
                fragmentUnits.add(FragmentMemoryUnit.builder()
                        .fragmentType(fragmentType)
                        .content(memContent)
                        .messageMemId(messageMemId)
                        .timestamp(timestamp)
                        .memId(memId)
                        .build());
            }
        }
        return fragmentUnits;
    }
}

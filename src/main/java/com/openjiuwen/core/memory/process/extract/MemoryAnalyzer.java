/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.process.extract;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.common.VirtualThreadSupport;
import com.openjiuwen.core.common.logging.LoggerProtocol;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.common.logging.events.LogEventType;
import com.openjiuwen.core.common.schema.Param;
import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.output_parsers.JsonOutputParser;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import com.openjiuwen.core.memory.config.AgentMemoryConfig;
import com.openjiuwen.core.memory.config.MemoryScopeConfig;
import com.openjiuwen.core.memory.prompts.PromptApplier;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Mirrors Python's {@code MemoryAnalyzer} in
 * {@code openjiuwen/core/memory/process/extract/memory_analyzer.py}.
 */
public final class MemoryAnalyzer {

    private static final LoggerProtocol MEMORY_LOGGER = Loggers.MEMORY;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final int DEFAULT_RETRIES = 3;
    private static final java.util.concurrent.Executor IO_EXECUTOR =
            VirtualThreadSupport.newThreadPerTaskExecutor("memory-analyzer-io");

    private MemoryAnalyzer() {
    }

    public static CompletionStage<MemoryAnalyzerResult> analyze(
            List<BaseMessage> messages,
            List<BaseMessage> historyMessages,
            Model baseChatModel,
            AgentMemoryConfig memoryConfig,
            Integer summaryMaxToken
    ) {
        return analyze(messages, historyMessages, baseChatModel, memoryConfig, summaryMaxToken, null, "", DEFAULT_RETRIES);
    }

    public static CompletionStage<MemoryAnalyzerResult> analyze(
            List<BaseMessage> messages,
            List<BaseMessage> historyMessages,
            Model baseChatModel,
            AgentMemoryConfig memoryConfig,
            Integer summaryMaxToken,
            MemoryScopeConfig scopeConfig,
            String forbiddenVariables
    ) {
        return analyze(messages, historyMessages, baseChatModel, memoryConfig, summaryMaxToken,
                scopeConfig, forbiddenVariables, DEFAULT_RETRIES);
    }

    public static CompletionStage<MemoryAnalyzerResult> analyze(
            List<BaseMessage> messages,
            List<BaseMessage> historyMessages,
            Model baseChatModel,
            AgentMemoryConfig memoryConfig,
            Integer summaryMaxToken,
            MemoryScopeConfig scopeConfig,
            String forbiddenVariables,
            int retries
    ) {
        if (messages == null || messages.isEmpty()) {
            MEMORY_LOGGER.warning("[{}] No messages to analyze, messages_len={}",
                    LogEventType.MEMORY_PROCESS.getValue(), 0);
            return CompletableFuture.completedFuture(null);
        }

        String history = joinMessages(historyMessages);
        String conversation = joinMessages(messages);

        List<Map<String, String>> variablesDescription = new ArrayList<>();
        List<Map<String, String>> variablesOutputFormat = new ArrayList<>();
        if (memoryConfig != null && memoryConfig.getMemVariables() != null) {
            for (Param param : memoryConfig.getMemVariables()) {
                Map<String, String> description = new LinkedHashMap<>();
                description.put("variable_key", param.getName());
                description.put("variable_value", param.getDescription());
                variablesDescription.add(description);

                Map<String, String> output = new LinkedHashMap<>();
                output.put("variable_key", param.getName());
                output.put("variable_value", "");
                variablesOutputFormat.add(output);
            }
        }

        boolean hasVariable = memoryConfig != null
                && memoryConfig.getMemVariables() != null
                && !memoryConfig.getMemVariables().isEmpty();
        Map<String, String> promptVariables = new LinkedHashMap<>();
        promptVariables.put("history", history);
        promptVariables.put("conversation", conversation);
        promptVariables.put("has_variable", pythonBoolean(hasVariable));
        promptVariables.put("variables_define_template", toJson(variablesDescription));
        promptVariables.put("variables_output_template", toJson(variablesOutputFormat));
        promptVariables.put("forbidden_variables", forbiddenVariables == null || forbiddenVariables.isEmpty()
                ? "None" : forbiddenVariables);
        promptVariables.put("max_message_token", String.valueOf(summaryMaxToken));
        promptVariables.put("user_profile_definition",
                scopeConfig == null ? "" : nullToEmpty(scopeConfig.getUserProfileDefinition()));
        promptVariables.put("semantic_memory_definition",
                scopeConfig == null ? "" : nullToEmpty(scopeConfig.getSemanticMemoryDefinition()));
        promptVariables.put("episodic_memory_definition",
                scopeConfig == null ? "" : nullToEmpty(scopeConfig.getEpisodicMemoryDefinition()));

        String promptContent = new PromptApplier().apply("memory_analysis_prompt", promptVariables);
        List<BaseMessage> modelInput = List.of(new UserMessage(promptContent));
        return CompletableFuture.supplyAsync(
                () -> invokeAndParse(baseChatModel, modelInput, memoryConfig, retries),
                IO_EXECUTOR);
    }

    private static MemoryAnalyzerResult invokeAndParse(
            Model baseChatModel,
            List<BaseMessage> modelInput,
            AgentMemoryConfig memoryConfig,
            int retries
    ) {
        JsonOutputParser parser = new JsonOutputParser();
        int maxRetries = Math.max(1, retries);
        for (int attempt = 0; attempt < maxRetries; attempt++) {
            try {
                AssistantMessage response = baseChatModel.invoke(modelInput).toCompletableFuture().join();
                Object parsed = parser.parse(response.getContentAsString()).toCompletableFuture().join();
                if (parsed instanceof Map<?, ?> parsedMap) {
                    MemoryAnalyzerResult result = fromMap(parsedMap);
                    if (memoryConfig == null
                            || !memoryConfig.isEnableLongTermMem()
                            || !memoryConfig.isEnableSummaryMemory()) {
                        result.setSummary("");
                    }
                    return result;
                }
            } catch (RuntimeException exception) {
                if (attempt >= maxRetries - 1) {
                    MEMORY_LOGGER.error("[{}] Categories model output format error: {}",
                            LogEventType.MEMORY_PROCESS.getValue(), exception.toString());
                }
            }
        }
        return new MemoryAnalyzerResult();
    }

    private static MemoryAnalyzerResult fromMap(Map<?, ?> raw) {
        MemoryAnalyzerResult result = new MemoryAnalyzerResult();
        result.setHasKeyInformation(booleanValue(raw.get("has_key_information")));
        Object variables = raw.get("variables");
        if (variables instanceof List<?> variableList) {
            List<VariableResult> variableResults = new ArrayList<>();
            for (Object variable : variableList) {
                if (variable instanceof Map<?, ?> variableMap) {
                    variableResults.add(new VariableResult(
                            stringOrDefault(variableMap.get("variable_key"), ""),
                            stringOrDefault(variableMap.get("variable_value"), "")
                    ));
                }
            }
            result.setVariables(variableResults);
        }
        result.setSummary(stringOrDefault(raw.get("summary"), ""));
        return result;
    }

    private static String joinMessages(List<BaseMessage> messages) {
        StringBuilder builder = new StringBuilder();
        if (messages == null) {
            return "";
        }
        for (BaseMessage message : messages) {
            builder.append(message.getRole()).append(": ")
                    .append(message.getContentAsString()).append("\n");
        }
        return builder.toString();
    }

    private static String toJson(Object value) {
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            if (value instanceof List<?>) {
                return "[]";
            }
            return "{}";
        }
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

    private static String pythonBoolean(boolean value) {
        return value ? "True" : "False";
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String stringOrDefault(Object value, String defaultValue) {
        return value == null ? defaultValue : String.valueOf(value);
    }
}

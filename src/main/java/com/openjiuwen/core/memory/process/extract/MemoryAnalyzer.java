  /*
   * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
   */

package com.openjiuwen.core.memory.process.extract;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import com.openjiuwen.core.memory.prompt.PromptApplier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Analyzes conversation messages to determine key information, extract variables, and generate summary.
 */
public class MemoryAnalyzer {

    private static final LoggerProtocol MEMORY_LOGGER = Loggers.MEMORY;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private MemoryAnalyzer() {
    }

    @SuppressWarnings("unchecked")
    public static MemoryAnalyzerResult analyze(
            List<BaseMessage> messages,
            List<BaseMessage> historyMessages,
            Map.Entry<String, Model> baseChatModel,
            AgentMemoryConfig memoryConfig,
            int summaryMaxToken,
            int retries) {

        if (messages == null || messages.isEmpty()) {
            MEMORY_LOGGER.warn("[{}] No messages to analyze", LogEventType.MEMORY_PROCESS);
            return null;
        }

        StringBuilder history = new StringBuilder();
        StringBuilder conversation = new StringBuilder();

        if (historyMessages != null) {
            for (BaseMessage msg : historyMessages) {
                history.append(msg.getRole()).append(": ").append(msg.getContentAsString()).append("\n");
            }
        }
        for (BaseMessage msg : messages) {
            conversation.append(msg.getRole()).append(": ").append(msg.getContentAsString()).append("\n");
        }

        List<Map<String, String>> variablesDescription = new ArrayList<>();
        List<Map<String, String>> variablesOutputFormat = new ArrayList<>();
        if (memoryConfig.getMemVariables() != null) {
            for (Param param : memoryConfig.getMemVariables()) {
                Map<String, String> desc = new HashMap<>();
                desc.put("variable_key", param.getName());
                desc.put("variable_value", param.getDescription());
                variablesDescription.add(desc);

                Map<String, String> output = new HashMap<>();
                output.put("variable_key", param.getName());
                output.put("variable_value", "");
                variablesOutputFormat.add(output);
            }
        }

        String variablesDescJson;
        String variablesOutputJson;
        try {
            variablesDescJson = MAPPER.writeValueAsString(variablesDescription);
            variablesOutputJson = MAPPER.writeValueAsString(variablesOutputFormat);
        } catch (Exception e) {
            variablesDescJson = "[]";
            variablesOutputJson = "[]";
        }

        boolean hasVariable = memoryConfig.getMemVariables() != null && !memoryConfig.getMemVariables().isEmpty();

        Map<String, Object> variables = new HashMap<>();
        variables.put("history", history.toString());
        variables.put("conversation", conversation.toString());
        variables.put("has_variable", hasVariable);
        variables.put("variables_define_template", variablesDescJson);
        variables.put("variables_output_template", variablesOutputJson);
        variables.put("max_message_token", summaryMaxToken);

        String promptContent = PromptApplier.getInstance().apply("memory_analysis_prompt", variables);
        List<BaseMessage> modelInput = List.of(new UserMessage(promptContent));

        String modelName = baseChatModel.getKey();
        Model modelClient = baseChatModel.getValue();
        JsonOutputParser parser = new JsonOutputParser();

        for (int attempt = 0; attempt < retries; attempt++) {
            try {
                AssistantMessage response = modelClient.invoke(
                        modelInput, null, null, null, modelName, null, null, null, null, null);
                Object res = parser.parse(response.getContentAsString());
                if (res instanceof Map<?, ?> resMap) {
                    MemoryAnalyzerResult result = new MemoryAnalyzerResult();
                    result.setHasKeyInformation(Boolean.TRUE.equals(resMap.get("has_key_information")));

                    // Parse variables
                    Object varsObj = resMap.get("variables");
                    if (varsObj instanceof List<?> varsList) {
                        List<VariableResult> variableResults = new ArrayList<>();
                        for (Object item : varsList) {
                            if (item instanceof Map<?, ?> itemMap) {
                                VariableResult vr = new VariableResult();
                                Object variableKey = itemMap.containsKey("variable_key")
                                        ? itemMap.get("variable_key") : "";
                                Object variableValue = itemMap.containsKey("variable_value")
                                        ? itemMap.get("variable_value") : "";
                                vr.setVariableKey(String.valueOf(variableKey));
                                vr.setVariableValue(String.valueOf(variableValue));
                                variableResults.add(vr);
                            }
                        }
                        result.setVariables(variableResults);
                    }

                    // Parse summary
                    Object summaryObj = resMap.get("summary");
                    if (summaryObj != null) {
                        result.setSummary(String.valueOf(summaryObj));
                    }

                    // Clear summary if not enabled
                    if (!memoryConfig.isEnableLongTermMem() || !memoryConfig.isEnableSummaryMemory()) {
                        result.setSummary("");
                    }
                    return result;
                }
            } catch (Exception e) {
                if (attempt < retries - 1) {
                    continue;
                }
                MEMORY_LOGGER.error("[{}] Categories model output format error: {}",
                        LogEventType.MEMORY_PROCESS, e.getMessage());
            }
        }
        return new MemoryAnalyzerResult();
    }

    public static MemoryAnalyzerResult analyze(
            List<BaseMessage> messages,
            List<BaseMessage> historyMessages,
            Map.Entry<String, Model> baseChatModel,
            AgentMemoryConfig memoryConfig,
            int summaryMaxToken) {
        return analyze(messages, historyMessages, baseChatModel, memoryConfig, summaryMaxToken, 3);
    }
}

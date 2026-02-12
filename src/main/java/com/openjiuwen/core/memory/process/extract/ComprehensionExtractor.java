/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */

package com.openjiuwen.core.memory.process.extract;

import com.openjiuwen.core.common.logging.LoggerProtocol;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.common.schema.Param;
import com.openjiuwen.core.common.utils.Pair;
import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.outputparsers.JsonOutputParser;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.memory.config.AgentMemoryConfig;
import com.openjiuwen.core.memory.prompt.VariableExtractorPrompt;

import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Extractor for variables from conversation using LLM.
 * Corresponds to Python: process/extract/variable_extractor.py ComprehensionExtractor
 */
public class ComprehensionExtractor {

    private static final LoggerProtocol logger = Loggers.MEMORY;

    public ComprehensionExtractor() {
    }

    /**
     * Extract variables from the given messages using LLM.
     *
     * @param messages       The current messages to extract variables from
     * @param historySummary The summary of historical messages
     * @param baseChatModel  The chat model to use for extraction
     * @param config         Configuration for the extraction process
     * @return CompletableFuture of list of extracted data objects
     */
    public static CompletableFuture<List<ExtractedData>> extract(
            List<BaseMessage> messages,
            BaseMessage historySummary,
            Pair<String, Model> baseChatModel,
            AgentMemoryConfig config
    ) {
        return CompletableFuture.supplyAsync(() -> {
            List<Param> memVariables = config.getMemVariables();
            if (memVariables == null || memVariables.isEmpty()) {
                logger.info("Memory variables not set.");
                return List.of();
            }

            StringBuilder variablesDescription = new StringBuilder();
            Set<String> variablesUser = new HashSet<>();
            StringBuilder variablesOutputFormat = new StringBuilder("{");

            int cnt = 0;
            for (Param param : memVariables) {
                String key = param.getName();
                String description = param.getDescription();
                variablesUser.add(key);
                variablesDescription.append(key).append("(").append(description).append("),");
                if (cnt != 0) {
                    variablesOutputFormat.append(",");
                }
                variablesOutputFormat.append("\"").append(key).append("\": {\"value\": \"string\"}");
                cnt++;
            }
            variablesOutputFormat.append("}");

            String sysMessage = VariableExtractorPrompt.EXTRACT_VARIABLES_PROMPT
                    .replace("{variables}", variablesDescription.toString())
                    .replace("{variables_output_format}", variablesOutputFormat.toString());

            Object historyContent = historySummary.getContent();
            String history = historyContent instanceof String ? (String) historyContent : "";

            List<Map<String, Object>> modelInput = ExtractUtils.buildModelInput(
                    messages,
                    history,
                    sysMessage
            );

            logger.debug("Start to extract variables, input: {}", modelInput);

            Model modelClient = baseChatModel.getValue();

            try {
                BaseMessage response = modelClient.invoke(modelInput).join();
                Object content = response.getContent();
                String contentStr = content instanceof String ? (String) content : content.toString();

                logger.debug("Succeed to call llm, content: {}", contentStr);

                // Parse response
                List<ExtractedData> extractResult = new ArrayList<>();
                JsonOutputParser parser = new JsonOutputParser();
                Object parsed = parser.parse(contentStr).join();

                if (parsed == null) {
                    logger.error("Failed to extract variables, response None");
                    return List.of();
                }

                if (parsed instanceof Map<?, ?> parsedMap) {
                    for (Map.Entry<?, ?> entry : parsedMap.entrySet()) {
                        String key = String.valueOf(entry.getKey()).trim();
                        Object value = entry.getValue();

                        if (!checkValue(value)) {
                            continue;
                        }

                        @SuppressWarnings("unchecked")
                        Map<String, Object> valueMap = (Map<String, Object>) value;
                        Object rawValue = valueMap.get("value");
                        String valueStr = rawValue != null ? String.valueOf(rawValue).trim() : "";

                        if (!valueStr.isEmpty() && !valueStr.equalsIgnoreCase("null")) {
                            if (variablesUser.contains(key)) {
                                extractResult.add(new ExtractedData(
                                        ExtractedDataType.USER,
                                        key,
                                        valueStr
                                ));
                            }
                        }
                    }
                }

                logger.debug("Succeed to extract variables, result: {}", extractResult);
                return extractResult;

            } catch (Exception e) {
                logger.error("Failed to extract variables, with error: {}", e.getMessage());
                return List.of();
            }
        });
    }

    /**
     * Check if the value is valid for extraction.
     * Corresponds to Python: _check_value in variable_extractor.py
     *
     * @param value The value to check
     * @return true if valid, false otherwise
     */
    public static boolean checkValue(Object value) {
        if (value == null || !(value instanceof Map)) {
            return false;
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> valueMap = (Map<String, Object>) value;
        // Python: value.get("value", "") - use empty string as default if key doesn't exist
        Object innerValue = valueMap.getOrDefault("value", "");

        if (innerValue == null) {
            return false;
        }

        String valueStr = String.valueOf(innerValue);
        return !valueStr.equalsIgnoreCase("none");
    }
}

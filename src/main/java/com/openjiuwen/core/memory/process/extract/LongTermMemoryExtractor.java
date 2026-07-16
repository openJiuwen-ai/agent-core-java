/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.process.extract;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.common.logging.LoggerProtocol;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.common.logging.events.LogEventType;
import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.output_parsers.JsonOutputParser;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import com.openjiuwen.core.memory.prompt.PromptApplier;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Extracts long-term memory (fragment memories) from conversation using LLM.
 * 
 * @since 0.1.7
 */
public class LongTermMemoryExtractor {
    private static final LoggerProtocol MEMORY_LOGGER = Loggers.MEMORY;

    /**
     * ObjectMapper.
     * 
     * @since 0.1.7
     */
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * LongTermMemoryExtractor.
     * 
     * @since 0.1.7
     */
    private LongTermMemoryExtractor() {
    }

    /**
     * extractLongTermMemory.
     * 
     * @param params params
     * @param timestamp timestamp
     * @param retries retries
     * @return the result
     * @since 0.1.7
     */
    @SuppressWarnings("unchecked")
    public static Map<String, List<Object>> extractLongTermMemory(ExtractMemoryParams params, String timestamp,
            int retries) {
        StringBuilder referenceStr = new StringBuilder();
        StringBuilder inputMsgStr = new StringBuilder();

        if (params.getHistoryMessages() != null) {
            for (BaseMessage msg : params.getHistoryMessages()) {
                String name = msg.getName() != null ? msg.getName() : msg.getRole();
                referenceStr.append(name).append(": ").append(msg.getContentAsString()).append("\n");
            }
        }
        for (BaseMessage msg : params.getMessages()) {
            String name = msg.getName() != null ? msg.getName() : msg.getRole();
            referenceStr.append(name).append(": ").append(msg.getContentAsString()).append("\n");
            if ("user".equals(msg.getRole())) {
                inputMsgStr.append(name).append(": ").append(msg.getContentAsString()).append("\n");
            }
        }

        Map<String, Object> variables = new HashMap<>();
        variables.put("conversation_time", timestamp);
        variables.put("input_messages", inputMsgStr.toString());
        variables.put("reference_messages", referenceStr.toString());

        String promptContent = PromptApplier.getInstance().apply("fragment_memory_prompt", variables);

        List<BaseMessage> modelInput = List.of(new UserMessage(promptContent));
        String modelName = params.getBaseChatModel().getKey();
        Model modelClient = params.getBaseChatModel().getValue();
        JsonOutputParser parser = new JsonOutputParser();

        for (int attempt = 0; attempt < retries; attempt++) {
            try {
                AssistantMessage response =
                    modelClient.invoke(modelInput, null, null, null, modelName, null, null, null, null, null);
                Object result = parser.parse(response.getContentAsString());
                if (result instanceof Map) {
                    Map<String, List<Object>> normalized = new HashMap<>();
                    Map<?, ?> rawMap = (Map<?, ?>) result;
                    for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
                        if (entry.getKey() == null) {
                            continue;
                        }
                        Object value = entry.getValue();
                        if (value instanceof List<?> list) {
                            normalized.put(String.valueOf(entry.getKey()), new java.util.ArrayList<>(list));
                        }
                    }
                    return normalized;
                }
            } catch (Exception e) {
                if (attempt < retries - 1) {
                    continue;
                }
                MEMORY_LOGGER.error("[{}] Long term memory extractor model output format error: {}",
                        LogEventType.MEMORY_PROCESS, e.getMessage());
            }
        }
        return Collections.emptyMap();
    }

    /**
     * extractLongTermMemory.
     * 
     * @param params params
     * @param timestamp timestamp
     * @return the result
     * @since 0.1.7
     */
    public static Map<String, List<Object>> extractLongTermMemory(ExtractMemoryParams params, String timestamp) {
        return extractLongTermMemory(params, timestamp, 3);
    }
}

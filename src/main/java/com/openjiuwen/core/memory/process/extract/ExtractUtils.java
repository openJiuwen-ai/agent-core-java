/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */

package com.openjiuwen.core.memory.process.extract;

import com.openjiuwen.core.foundation.llm.schema.BaseMessage;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Utility methods for building LLM model input.
 * Corresponds to Python: process/extract/common.py
 */
public final class ExtractUtils {

    private ExtractUtils() {
        // Utility class, no instantiation
    }

    /**
     * Build model input from messages, history, and prompt.
     *
     * @param messages        Current messages to process
     * @param historyMessages History messages (can be List<BaseMessage> or String)
     * @param prompt          System prompt
     * @return List of message maps for LLM input
     */
    public static List<Map<String, Object>> buildModelInput(
            List<BaseMessage> messages,
            Object historyMessages,
            String prompt
    ) {
        String history = "";

        if (historyMessages instanceof String historyStr) {
            history = historyStr;
        } else if (historyMessages instanceof List<?> historyList) {
            if (!historyList.isEmpty()) {
                StringBuilder sb = new StringBuilder();
                for (Object obj : historyList) {
                    if (obj instanceof BaseMessage msg) {
                        sb.append(msg.getRole()).append(": ").append(msg.getContent()).append("\n");
                    }
                }
                history = sb.toString();
            }
        }

        StringBuilder conversation = new StringBuilder();
        for (BaseMessage msg : messages) {
            conversation.append(msg.getRole()).append(": ").append(msg.getContent()).append("\n");
        }

        List<Map<String, Object>> modelInput = new ArrayList<>();

        // System message
        Map<String, Object> systemMessage = new LinkedHashMap<>();
        systemMessage.put("role", "system");
        systemMessage.put("content", prompt);
        modelInput.add(systemMessage);

        // User message
        StringBuilder userInput = new StringBuilder();
        if (!history.isEmpty()) {
            userInput.append("如果当前输入与历史消息有关联，可参考历史消息，历史消息如下：\n");
            userInput.append("<historical_messages>").append(history).append("</historical_messages>\n");
        }
        userInput.append("现在开始：请根据设定的规则处理以下输入并生成出输出：\n");
        userInput.append("<current_messages>").append(conversation).append("</current_messages>\n");

        Map<String, Object> userMessage = new LinkedHashMap<>();
        userMessage.put("role", "user");
        userMessage.put("content", userInput.toString());
        modelInput.add(userMessage);

        return modelInput;
    }
}


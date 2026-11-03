/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.context.token;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.tool.schema.ToolInfo;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A simple token counter that estimates token count based on character length.
 * <p>
 * This is a fallback implementation when tiktoken is not available in Java.
 * It uses a heuristic of ~4 characters per token (similar to tiktoken's cl100k_base
 * encoding) plus overhead tokens for message framing.
 * <p>
 * Mirrors Python's {@code TiktokenCounter} from {@code context_engine/token/tiktoken_counter.py}.
 * <p>
 * Since Java does not have a native tiktoken binding, this implementation
 * approximates token counts. For production use, consider integrating
 * a Java tiktoken library (e.g., jtokkit).
 * 
 * @since 0.1.7
 */
public class SimpleTokenCounter implements TokenCounter {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** Approximate chars per token — tuned towards cl100k_base behaviour. */
    private static final int CHARS_PER_TOKEN = 4;

    /** Extra overhead tokens per message (role, framing, separators). */
    private static final int MESSAGE_OVERHEAD = 4;

    /** Extra overhead at the end of a message list. */
    private static final int REPLY_OVERHEAD = 3;

    private final String model;

    /**
     * SimpleTokenCounter.
     * 
     * @since 0.1.7
     */
    public SimpleTokenCounter() {
        this("gpt-4");
    }

    /**
     * SimpleTokenCounter.
     * 
     * @param model model
     * @since 0.1.7
     */
    public SimpleTokenCounter(String model) {
        this.model = model;
    }

    /**
     * count.
     * 
     * @param text text
     * @param model model
     * @return the result
     * @since 0.1.7
     */
    @Override
    public int count(String text, String model, Map<String, Object> kwargs) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        int chineseCount = 0;
        int otherCount = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.UnicodeBlock.of(c) == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS) {
                chineseCount++;
            } else {
                otherCount++;
            }
        }
        int weighted = chineseCount * 8 + otherCount;
        return Math.max(1, weighted / CHARS_PER_TOKEN);
    }

    /**
     * countMessages.
     *
     * @param messages messages
     * @param model model
     * @param kwargs kwargs
     * @return the result
     * @since 0.1.7
     */
    @Override
    public int countMessages(List<BaseMessage> messages, String model, Map<String, Object> kwargs) {
        if (messages == null || messages.isEmpty()) {
            return 0;
        }
        int total = 0;
        for (BaseMessage msg : messages) {
            String piece = "<|start|>" + msg.getRole() + "\n" + msg.getContentAsString() + "<|end|>";
            total += count(piece, model);

            if (msg instanceof AssistantMessage assistantMsg) {
                List<?> toolCalls = assistantMsg.getToolCalls();
                if (toolCalls != null && !toolCalls.isEmpty()) {
                    try {
                        String toolCallsJson = MAPPER.writeValueAsString(toolCalls);
                        total += count(toolCallsJson, model);
                    } catch (JsonProcessingException e) {
                        // fallback: estimate based on list size
                        total += toolCalls.size() * 20;
                    }
                }
            }
        }
        return total + REPLY_OVERHEAD;
    }

    /**
     * countTools.
     *
     * @param tools tools
     * @param model model
     * @param kwargs kwargs
     * @return the result
     * @since 0.1.7
     */
    @Override
    public int countTools(List<ToolInfo> tools, String model, Map<String, Object> kwargs) {
        if (tools == null || tools.isEmpty()) {
            return 0;
        }
        int total = 0;
        for (int idx = 0; idx < tools.size(); idx++) {
            ToolInfo tool = tools.get(idx);
            Map<String, Object> functionObj = new LinkedHashMap<>();
            functionObj.put("name", tool.getName());
            functionObj.put("description", tool.getDescription() != null ? tool.getDescription() : "");
            functionObj.put("parameters", tool.getParameters());
            try {
                String jsonStr = MAPPER.writeValueAsString(functionObj);
                String piece = "<|start|>functions." + tool.getName() + ":" + idx + "\n" + jsonStr + "<|end|>";
                total += count(piece, model);
            } catch (JsonProcessingException e) {
                total += 50; // fallback estimate
            }
        }
        return total + REPLY_OVERHEAD;
    }
}

/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.context.token;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.tool.schema.ToolInfo;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A fast token counter powered by tiktoken-compatible encoding.
 * <p>
 * This implementation mirrors Python's {@code TiktokenCounter} from
 * {@code context_engine/token/tiktoken_counter.py}.
 * <p>
 * Since the Java runtime may not have a tiktoken library available, this class
 * uses the same heuristic as {@link SimpleTokenCounter} ({@code len / 4}) as a
 * fallback. When {@code jtokkit} or a similar library is added to the classpath,
 * this class can be upgraded to use exact encoding.
 */
public class TiktokenCounter extends TokenCounter {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final int CHARS_PER_TOKEN = 4;
    private static final int REPLY_OVERHEAD = 3;

    private final String model;
    private volatile boolean fallbackWarningPrinted = false;

    public TiktokenCounter() {
        this("gpt-4");
    }

    public TiktokenCounter(String model) {
        this.model = model;
    }

    @Override
    public int count(String text, String model) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        // TODO: integrate jtokkit for exact tiktoken-based counting
        return Math.max(1, text.length() / CHARS_PER_TOKEN);
    }

    @Override
    public int countMessages(List<BaseMessage> messages, String model) {
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
                        String json = MAPPER.writeValueAsString(toolCalls);
                        total += count(json, model);
                    } catch (JsonProcessingException e) {
                        // fallback: count string representation
                        total += count(toolCalls.toString(), model);
                    }
                }
            }
        }
        return total + REPLY_OVERHEAD;
    }

    @Override
    public int countTools(List<ToolInfo> tools, String model) {
        if (tools == null || tools.isEmpty()) {
            return 0;
        }
        int total = 0;
        for (int i = 0; i < tools.size(); i++) {
            ToolInfo tool = tools.get(i);
            Map<String, Object> functionObj = new LinkedHashMap<>();
            functionObj.put("name", tool.getName());
            functionObj.put("description", tool.getDescription() != null ? tool.getDescription() : "");
            functionObj.put("parameters", tool.getParameters());

            try {
                String json = MAPPER.writeValueAsString(functionObj);
                String piece = "<|start|>functions." + tool.getName() + ":" + i + "\n" + json + "<|end|>";
                total += count(piece, model);
            } catch (JsonProcessingException e) {
                total += count(tool.toString(), model);
            }
        }
        return total + REPLY_OVERHEAD;
    }
}

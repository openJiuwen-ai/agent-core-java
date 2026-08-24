/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.context.token;

import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.foundation.tool.schema.ToolInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.ToIntFunction;

/**
 * A tiktoken-style token counter with Python-compatible fallback behavior.
 *
 * <p>Mirrors Python's {@code TiktokenCounter} in
 * {@code openjiuwen/core/context_engine/token/tiktoken_counter.py}.</p>
 */
public class TiktokenCounter implements TokenCounter {

    private static final Logger LOGGER = LoggerFactory.getLogger(TiktokenCounter.class);
    private static final String DEFAULT_MODEL = "gpt-4";
    private static final String DEFAULT_ENCODING = "cl100k_base";
    private static final Map<String, String> MODEL_TO_ENCODING = Map.ofEntries(
            Map.entry("gpt-3.5-turbo", "cl100k_base"),
            Map.entry("gpt-4", "cl100k_base"),
            Map.entry("gpt-4-turbo", "cl100k_base"),
            Map.entry("gpt-4o", "o200k_base"),
            Map.entry("gpt-4o-mini", "o200k_base"),
            Map.entry("text-embedding-ada-002", "cl100k_base"),
            Map.entry("text-embedding-3-small", "cl100k_base"),
            Map.entry("text-embedding-3-large", "cl100k_base")
    );

    private final ToIntFunction<String> encoder;
    private final String model;
    private final String encodingName;
    private final AtomicBoolean fallbackWarningPrinted = new AtomicBoolean(false);

    public TiktokenCounter() {
        this(DEFAULT_MODEL);
    }

    public TiktokenCounter(String model) {
        this(model, null);
    }

    TiktokenCounter(String model, ToIntFunction<String> encoder) {
        this.model = model;
        this.encodingName = resolveEncodingName(model);
        this.encoder = encoder;
    }

    @Override
    public int count(String text, String model, Map<String, Object> kwargs) {
        Objects.requireNonNull(text, "text");
        if (encoder != null) {
            try {
                return encoder.applyAsInt(text);
            } catch (IllegalArgumentException exception) {
                LOGGER.warn("Tiktoken encoding failed for text (len={}), using len//4 fallback.",
                        pythonLength(text));
                return fallbackCount(text);
            }
        }
        if (fallbackWarningPrinted.compareAndSet(false, true)) {
            LOGGER.warn("Tiktoken initialization failed, using len(text)//4 as fallback for token counting.");
        }
        return fallbackCount(text);
    }

    @Override
    public int countMessages(List<BaseMessage> messages, String model, Map<String, Object> kwargs) {
        if (messages == null || messages.isEmpty()) {
            return 0;
        }
        int total = 0;
        for (BaseMessage message : messages) {
            Objects.requireNonNull(message, "message");
            String piece = "<|start|>" + message.getRole() + "\n" + message.getContent() + "<|end|>";
            total += count(piece, model, kwargs);
            if (message instanceof AssistantMessage assistantMessage) {
                List<Map<String, Object>> toolCalls = toolCallsForCounting(assistantMessage);
                if (!toolCalls.isEmpty()) {
                    total += count(jsonDumps(toolCalls, false), model, kwargs);
                }
            }
        }
        return total + 3;
    }

    @Override
    public int countTools(List<ToolInfo> tools, String model, Map<String, Object> kwargs) {
        if (tools == null || tools.isEmpty()) {
            return 0;
        }
        int total = 0;
        for (int idx = 0; idx < tools.size(); idx++) {
            ToolInfo tool = Objects.requireNonNull(tools.get(idx), "tool");
            Map<String, Object> functionObject = new LinkedHashMap<>();
            functionObject.put("name", tool.getName());
            functionObject.put("description", normalizeDescription(tool.getDescription()));
            functionObject.put("parameters", tool.getParameters());
            String json = jsonDumps(functionObject, true);
            String piece = "<|start|>functions." + tool.getName() + ":" + idx + "\n" + json + "<|end|>";
            total += count(piece);
        }
        return total + 3;
    }

    String getModel() {
        return model;
    }

    String getEncodingName() {
        return encodingName;
    }

    private static String resolveEncodingName(String model) {
        if (model == null) {
            return DEFAULT_ENCODING;
        }
        return MODEL_TO_ENCODING.getOrDefault(model, DEFAULT_ENCODING);
    }

    private static int fallbackCount(String text) {
        return pythonLength(text) / 4;
    }

    private static int pythonLength(String text) {
        return text.codePointCount(0, text.length());
    }

    private static String normalizeDescription(String description) {
        return description == null || description.isEmpty() ? "" : description;
    }

    private static List<Map<String, Object>> toolCallsForCounting(AssistantMessage assistantMessage) {
        List<ToolCall> toolCalls = assistantMessage.getToolCalls();
        if (toolCalls == null || toolCalls.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> serializedCalls = new ArrayList<>();
        for (ToolCall call : toolCalls) {
            Map<String, Object> callMap = new LinkedHashMap<>();
            callMap.put("id", call.getId());
            callMap.put("type", call.getType());
            Map<String, Object> functionMap = new LinkedHashMap<>();
            functionMap.put("name", call.getName());
            functionMap.put("arguments", call.getArguments());
            callMap.put("function", functionMap);
            serializedCalls.add(callMap);
        }
        return serializedCalls;
    }

    private static String jsonDumps(Object value, boolean compact) {
        StringBuilder builder = new StringBuilder();
        appendJson(builder, value, compact);
        return builder.toString();
    }

    private static void appendJson(StringBuilder builder, Object value, boolean compact) {
        if (value == null) {
            builder.append("null");
            return;
        }
        if (value instanceof String stringValue) {
            appendJsonString(builder, stringValue);
            return;
        }
        if (value instanceof Number || value instanceof Boolean) {
            builder.append(value);
            return;
        }
        if (value instanceof Map<?, ?> mapValue) {
            appendJsonMap(builder, mapValue, compact);
            return;
        }
        if (value instanceof Iterable<?> iterableValue) {
            appendJsonIterable(builder, iterableValue, compact);
            return;
        }
        appendJsonString(builder, String.valueOf(value));
    }

    private static void appendJsonMap(StringBuilder builder, Map<?, ?> mapValue, boolean compact) {
        String itemSeparator = compact ? "," : ", ";
        String keyValueSeparator = compact ? ":" : ": ";
        builder.append('{');
        boolean first = true;
        for (Map.Entry<?, ?> entry : mapValue.entrySet()) {
            if (!first) {
                builder.append(itemSeparator);
            }
            appendJsonString(builder, String.valueOf(entry.getKey()));
            builder.append(keyValueSeparator);
            appendJson(builder, entry.getValue(), compact);
            first = false;
        }
        builder.append('}');
    }

    private static void appendJsonIterable(StringBuilder builder, Iterable<?> iterableValue, boolean compact) {
        String itemSeparator = compact ? "," : ", ";
        builder.append('[');
        boolean first = true;
        for (Object item : iterableValue) {
            if (!first) {
                builder.append(itemSeparator);
            }
            appendJson(builder, item, compact);
            first = false;
        }
        builder.append(']');
    }

    private static void appendJsonString(StringBuilder builder, String value) {
        builder.append('"');
        for (int i = 0; i < value.length(); i++) {
            char current = value.charAt(i);
            switch (current) {
                case '"' -> builder.append("\\\"");
                case '\\' -> builder.append("\\\\");
                case '\b' -> builder.append("\\b");
                case '\f' -> builder.append("\\f");
                case '\n' -> builder.append("\\n");
                case '\r' -> builder.append("\\r");
                case '\t' -> builder.append("\\t");
                default -> {
                    if (current < 0x20) {
                        builder.append(String.format("\\u%04x", (int) current));
                    } else {
                        builder.append(current);
                    }
                }
            }
        }
        builder.append('"');
    }
}

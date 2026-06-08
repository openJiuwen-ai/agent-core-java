/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.observability;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Per-task span tracking helpers for LLM, tool, and agent spans.
 * <p>
 * Mirrors Python's module functions in
 * {@code openjiuwen/agent_teams/observability/span_context.py}.
 */
public final class SpanContext {

    private static final InheritableThreadLocal<List<LlmSpanState>> LLM_SPAN_STACK =
            new InheritableThreadLocal<>() {
                @Override
                protected List<LlmSpanState> initialValue() {
                    return new ArrayList<>();
                }

                @Override
                protected List<LlmSpanState> childValue(List<LlmSpanState> parentValue) {
                    return parentValue == null ? new ArrayList<>() : new ArrayList<>(parentValue);
                }
            };

    private static final InheritableThreadLocal<Map<String, Deque<Object>>> TOOL_SPAN_MAP =
            new InheritableThreadLocal<>() {
                @Override
                protected Map<String, Deque<Object>> initialValue() {
                    return new LinkedHashMap<>();
                }

                @Override
                protected Map<String, Deque<Object>> childValue(Map<String, Deque<Object>> parentValue) {
                    return copyBuckets(parentValue);
                }
            };

    private static final InheritableThreadLocal<Map<String, Deque<Object>>> AGENT_SPAN_MAP =
            new InheritableThreadLocal<>() {
                @Override
                protected Map<String, Deque<Object>> initialValue() {
                    return new LinkedHashMap<>();
                }

                @Override
                protected Map<String, Deque<Object>> childValue(Map<String, Deque<Object>> parentValue) {
                    return copyBuckets(parentValue);
                }
            };

    private SpanContext() {
    }

    public static void pushLlmSpanState(LlmSpanState state) {
        List<LlmSpanState> stack = new ArrayList<>(LLM_SPAN_STACK.get());
        stack.add(state);
        LLM_SPAN_STACK.set(stack);
    }

    public static LlmSpanState popLlmSpanState() {
        return popLlmSpanState(false);
    }

    public static LlmSpanState popLlmSpanState(boolean peek) {
        List<LlmSpanState> stack = new ArrayList<>(LLM_SPAN_STACK.get());
        if (stack.isEmpty()) {
            return null;
        }
        if (peek) {
            return stack.getLast();
        }
        LlmSpanState state = stack.removeLast();
        LLM_SPAN_STACK.set(stack);
        return state;
    }

    public static void pushToolSpan(String toolName, Object span) {
        Map<String, Deque<Object>> mapping = copyBuckets(TOOL_SPAN_MAP.get());
        Deque<Object> bucket = mapping.computeIfAbsent(toolName, ignored -> new ArrayDeque<>());
        bucket.addLast(span);
        TOOL_SPAN_MAP.set(mapping);
    }

    public static <T> T popToolSpan(String toolName) {
        Map<String, Deque<Object>> mapping = copyBuckets(TOOL_SPAN_MAP.get());
        Deque<Object> bucket = mapping.get(toolName);
        if (bucket == null || bucket.isEmpty()) {
            return null;
        }
        @SuppressWarnings("unchecked")
        T span = (T) bucket.removeLast();
        if (bucket.isEmpty()) {
            mapping.remove(toolName);
        }
        TOOL_SPAN_MAP.set(mapping);
        return span;
    }

    public static void pushAgentSpan(String agentId, Object span) {
        Map<String, Deque<Object>> mapping = copyBuckets(AGENT_SPAN_MAP.get());
        Deque<Object> bucket = mapping.computeIfAbsent(agentId, ignored -> new ArrayDeque<>());
        bucket.addLast(span);
        AGENT_SPAN_MAP.set(mapping);
    }

    public static <T> T popAgentSpan(String agentId) {
        Map<String, Deque<Object>> mapping = copyBuckets(AGENT_SPAN_MAP.get());
        Deque<Object> bucket = mapping.get(agentId);
        if (bucket == null || bucket.isEmpty()) {
            return null;
        }
        @SuppressWarnings("unchecked")
        T span = (T) bucket.removeLast();
        if (bucket.isEmpty()) {
            mapping.remove(agentId);
        }
        AGENT_SPAN_MAP.set(mapping);
        return span;
    }

    public static void resetAll() {
        LLM_SPAN_STACK.set(new ArrayList<>());
        TOOL_SPAN_MAP.set(new LinkedHashMap<>());
        AGENT_SPAN_MAP.set(new LinkedHashMap<>());
    }

    private static Map<String, Deque<Object>> copyBuckets(Map<String, Deque<Object>> source) {
        Map<String, Deque<Object>> copy = new LinkedHashMap<>();
        if (source == null) {
            return copy;
        }
        for (Map.Entry<String, Deque<Object>> entry : source.entrySet()) {
            copy.put(entry.getKey(), new ArrayDeque<>(entry.getValue()));
        }
        return copy;
    }
}

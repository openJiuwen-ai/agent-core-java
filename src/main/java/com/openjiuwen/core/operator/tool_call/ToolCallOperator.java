/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.operator.tool_call;

import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.operator.Operator;
import com.openjiuwen.core.operator.OperatorStream;
import com.openjiuwen.core.operator.TunableSpec;
import com.openjiuwen.core.session.Session;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Tool invocation operator; tunables cover tool descriptions only.
 */
public class ToolCallOperator extends Operator {

    private final Tool tool;
    private final String toolCallId;
    private final ToolExecutor toolExecutor;
    private final ToolRegistry toolRegistry;
    private boolean enabled = true;
    private int maxRetries;

    public ToolCallOperator(Tool tool, String toolCallId, ToolExecutor toolExecutor, ToolRegistry toolRegistry) {
        this.tool = tool;
        this.toolCallId = toolCallId != null ? toolCallId : "tool_call";
        this.toolExecutor = toolExecutor;
        this.toolRegistry = toolRegistry;
    }

    public ToolCallOperator(Tool tool) {
        this(tool, "tool_call", null, null);
    }

    public ToolCallOperator(ToolExecutor toolExecutor) {
        this(null, "tool_call", toolExecutor, null);
    }

    public ToolCallOperator(Tool tool, ToolRegistry toolRegistry) {
        this(tool, "tool_call", null, toolRegistry);
    }

    public ToolCallOperator() {
        this(null, "tool_call", null, null);
    }

    @Override
    public String getOperatorId() {
        return toolCallId;
    }

    @Override
    public Map<String, TunableSpec> getTunables() {
        if (toolRegistry == null) {
            return Collections.emptyMap();
        }
        return Map.of("tool_description",
                new TunableSpec("tool_description", "text", "tool_description", Map.of("type", "dict")));
    }

    @Override
    public void setParameter(String target, Object value) {
        if (!"tool_description".equals(target) || toolRegistry == null || !(value instanceof Map<?, ?> descriptions)) {
            return;
        }
        for (Map.Entry<?, ?> entry : descriptions.entrySet()) {
            if (entry.getKey() != null) {
                toolRegistry.setToolDescription(String.valueOf(entry.getKey()), String.valueOf(entry.getValue()));
            }
        }
    }

    @Override
    public Map<String, Object> getState() {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("enabled", enabled);
        state.put("max_retries", maxRetries);
        return state;
    }

    @Override
    public void loadState(Map<String, Object> state) {
        if (state == null) {
            return;
        }
        if (state.containsKey("enabled")) {
            enabled = state.get("enabled") instanceof Boolean b
                    ? b : Boolean.parseBoolean(String.valueOf(state.get("enabled")));
        }
        if (state.containsKey("max_retries")) {
            maxRetries = clampRetries(state.get("max_retries"));
        }
    }

    @Override
    public Object invoke(Map<String, Object> inputs,
                         Session session,
                         Map<String, Object> kwargs) throws Exception {
        if (!enabled) {
            throw new IllegalStateException("ToolCallOperator disabled: " + toolCallId);
        }
        Map<String, Object> safeKwargs = kwargs != null ? kwargs : Collections.emptyMap();
        setOperatorContext(session, toolCallId);
        try {
            Object toolCalls = inputs != null ? inputs.get("tool_calls") : null;
            if (toolCalls instanceof List<?> list && toolExecutor != null) {
                List<ToolExecutionResult> results = new ArrayList<>();
                for (Object toolCall : list) {
                    ToolExecutionResult last = null;
                    for (int attempt = 0; attempt <= maxRetries; attempt++) {
                        last = toolExecutor.execute(toolCall, session);
                        if (last != null && last.result() != null) {
                            break;
                        }
                    }
                    if (last != null) {
                        results.add(last);
                    }
                }
                return results;
            }

            if (tool == null) {
                throw new IllegalStateException("ToolCallOperator has no tool configured");
            }

            Exception lastError = null;
            for (int attempt = 0; attempt <= maxRetries; attempt++) {
                try {
                    return tool.invoke(inputs, safeKwargs);
                } catch (Exception ex) {
                    lastError = ex;
                    if (attempt >= maxRetries) {
                        throw ex;
                    }
                }
            }
            throw lastError != null ? lastError : new IllegalStateException("tool invoke failed without exception");
        } finally {
            setOperatorContext(session, null);
        }
    }

    @Override
    public OperatorStream<Object> stream(Map<String, Object> inputs,
                                         Session session,
                                         Map<String, Object> kwargs) throws Exception {
        if (tool == null) {
            throw new UnsupportedOperationException("tool stream not implemented");
        }
        Map<String, Object> safeKwargs = kwargs != null ? kwargs : Collections.emptyMap();
        setOperatorContext(session, toolCallId);
        try {
            return OperatorStream.wrap(tool.stream(inputs, safeKwargs), () -> setOperatorContext(session, null));
        } catch (Exception ex) {
            setOperatorContext(session, null);
            throw ex;
        }
    }

    private static int clampRetries(Object value) {
        int retries = Integer.parseInt(String.valueOf(value));
        return Math.max(0, Math.min(5, retries));
    }

}

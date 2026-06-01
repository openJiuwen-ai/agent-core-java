/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
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
import java.util.function.BiConsumer;

/**
 * Tool description parameter handle for self-evolution.
 *
 * <p>Mirrors Python's {@code ToolCallOperator} in
 * {@code openjiuwen.core.operator.tool_call.base}.</p>
 *
 * <p>The Java class keeps the earlier invoke/stream compatibility path used by
 * ReActAgentEvolve, while the tunable/state surface mirrors Python: it manages
 * only {@code tool_description} values.</p>
 */
public class ToolCallOperator extends Operator {

    private final Tool tool;
    private final String toolCallId;
    private final ToolExecutor toolExecutor;
    private final ToolRegistry toolRegistry;
    private final BiConsumer<String, Object> onParameterUpdated;
    private final Map<String, String> descriptions;
    private int maxRetries;

    public ToolCallOperator(Tool tool, String toolCallId, ToolExecutor toolExecutor, ToolRegistry toolRegistry) {
        this(tool, toolCallId, toolExecutor, toolRegistry, null, null);
    }

    private ToolCallOperator(Tool tool, String toolCallId, ToolExecutor toolExecutor, ToolRegistry toolRegistry,
                             Map<String, String> descriptions,
                             BiConsumer<String, Object> onParameterUpdated) {
        this.tool = tool;
        this.toolCallId = toolCallId != null ? toolCallId : "tool_call";
        this.toolExecutor = toolExecutor;
        this.toolRegistry = toolRegistry;
        this.onParameterUpdated = onParameterUpdated;
        this.descriptions = new LinkedHashMap<>();
        if (descriptions != null) {
            this.descriptions.putAll(descriptions);
        } else {
            this.descriptions.putAll(descriptionsFromRegistry(toolRegistry));
        }
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

    public ToolCallOperator(String operatorId) {
        this(null, operatorId, null, null, null, null);
    }

    public ToolCallOperator(String operatorId, Map<String, String> descriptions) {
        this(null, operatorId, null, null, descriptions, null);
    }

    public ToolCallOperator(String operatorId, Map<String, String> descriptions,
                            BiConsumer<String, Object> onParameterUpdated) {
        this(null, operatorId, null, null, descriptions, onParameterUpdated);
    }

    @Override
    public String getOperatorId() {
        return toolCallId;
    }

    @Override
    public Map<String, TunableSpec> getTunables() {
        if (descriptions.isEmpty() && toolRegistry == null) {
            return Collections.emptyMap();
        }
        return Map.of("tool_description",
                new TunableSpec("tool_description", "text", "tool_description", Map.of("type", "dict")));
    }

    @Override
    public void setParameter(String target, Object value) {
        if (!"tool_description".equals(target) || !(value instanceof Map<?, ?> newDescriptions)) {
            return;
        }
        Map<String, String> copied = toDescriptionMap(newDescriptions);
        descriptions.clear();
        descriptions.putAll(copied);
        if (toolRegistry != null) {
            for (Map.Entry<String, String> entry : copied.entrySet()) {
                toolRegistry.setToolDescription(entry.getKey(), entry.getValue());
            }
        }
        if (onParameterUpdated != null) {
            onParameterUpdated.accept("tool_description", new LinkedHashMap<>(descriptions));
        }
    }

    private static Map<String, String> toDescriptionMap(Map<?, ?> rawDescriptions) {
        Map<String, String> copied = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : rawDescriptions.entrySet()) {
            if (entry.getKey() != null) {
                copied.put(String.valueOf(entry.getKey()), String.valueOf(entry.getValue()));
            }
        }
        return copied;
    }

    @Override
    public Map<String, Object> getState() {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("tool_description", new LinkedHashMap<>(descriptions));
        return state;
    }

    @Override
    public void loadState(Map<String, Object> state) {
        if (state == null) {
            return;
        }
        Object value = state.get("tool_description");
        if (value instanceof Map<?, ?> descriptionState) {
            setParameter("tool_description", descriptionState);
        }
    }

    public Object invoke(Map<String, Object> inputs,
                         Session session,
                         Map<String, Object> kwargs) throws Exception {
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

    public Object invoke(Map<String, Object> inputs, Session session) throws Exception {
        return invoke(inputs, session, Collections.emptyMap());
    }

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

    public OperatorStream<Object> stream(Map<String, Object> inputs, Session session) throws Exception {
        return stream(inputs, session, Collections.emptyMap());
    }

    private static int clampRetries(Object value) {
        int retries = Integer.parseInt(String.valueOf(value));
        return Math.max(0, Math.min(5, retries));
    }

    private static Map<String, String> descriptionsFromRegistry(ToolRegistry toolRegistry) {
        Map<String, String> result = new LinkedHashMap<>();
        if (toolRegistry == null) {
            return result;
        }
        try {
            for (Map<String, Object> toolDef : toolRegistry.getToolDefs()) {
                if (toolDef == null) {
                    continue;
                }
                Object name = toolDef.getOrDefault("name", toolDef.get("id"));
                Object description = toolDef.get("description");
                if (name != null && description != null) {
                    result.put(String.valueOf(name), String.valueOf(description));
                }
            }
        } catch (Exception ignored) {
            return new LinkedHashMap<>();
        }
        return result;
    }
}

/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.application.llm_agent.rails;

import com.openjiuwen.core.session.stream.OutputSchema;
import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.core.singleagent.rail.AgentRail;
import com.openjiuwen.core.singleagent.rail.InvokeInputs;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletionStage;

/**
 * AFTER_INVOKE rail that converts raw ReActAgent results to legacy LLMAgent output.
 *
 * <p>Mirrors Python's {@code InvokeResultAdapterRail} in
 * {@code openjiuwen/core/application/llm_agent/rails/invoke_result_adapter_rail.py}.</p>
 */
public class InvokeResultAdapterRail extends AgentRail {

    public static final String INVOKE_RESULT_KEY = "invoke_result";

    public InvokeResultAdapterRail() {
        setPriority(90);
    }

    @Override
    public CompletionStage<Void> afterInvoke(AgentCallbackContext context) {
        Object rawResult = readResult(context);
        if (rawResult instanceof Map<?, ?> result) {
            context.getExtra().put(INVOKE_RESULT_KEY, convertDictToSchema(result));
        }
        return completed();
    }

    private Object readResult(AgentCallbackContext context) {
        if (context == null) {
            return null;
        }
        Object inputs = context.getInputs();
        if (inputs instanceof InvokeInputs invokeInputs) {
            return invokeInputs.getResult();
        }
        if (inputs instanceof Map<?, ?> map) {
            return map.get("result");
        }
        return readAttribute(inputs, "result");
    }

    private Object convertDictToSchema(Map<?, ?> result) {
        String resultType = Objects.toString(result.get("result_type"), "");
        if ("interrupt".equals(resultType)) {
            return convertInterrupt(result);
        }
        Map<String, Object> adapted = new LinkedHashMap<>();
        adapted.put("output", result.containsKey("output") ? result.get("output") : "");
        adapted.put("result_type", resultType);
        return adapted;
    }

    private List<Object> convertInterrupt(Map<?, ?> result) {
        Object workflowState = result.get("workflow_execution_state");
        String pendingId = firstComponentId(result.get("component_ids"));
        Object schemas = readAttribute(workflowState, "result");
        if (!(schemas instanceof List<?> schemaList)) {
            return List.of();
        }

        List<Object> outputSchemas = new ArrayList<>();
        for (Object schema : schemaList) {
            if (pendingId == null || pendingId.equals(readPayloadId(schema))) {
                outputSchemas.add(schema);
            }
        }
        return outputSchemas;
    }

    private String firstComponentId(Object componentIds) {
        if (componentIds instanceof List<?> list && !list.isEmpty()) {
            Object first = list.get(0);
            return first == null ? null : String.valueOf(first);
        }
        return null;
    }

    private Object readPayloadId(Object schema) {
        Object payload = schema instanceof OutputSchema outputSchema
                ? outputSchema.getPayload()
                : readAttribute(schema, "payload");
        return readAttribute(payload, "id");
    }

    private Object readAttribute(Object source, String name) {
        if (source == null || name == null || name.isBlank()) {
            return null;
        }
        if (source instanceof Map<?, ?> map) {
            return map.get(name);
        }
        for (String methodName : List.of(
                "get" + Character.toUpperCase(name.charAt(0)) + name.substring(1),
                name
        )) {
            try {
                Method method = source.getClass().getMethod(methodName);
                if (method.getParameterCount() == 0) {
                    return method.invoke(source);
                }
            } catch (ReflectiveOperationException ignored) {
                // Try the next Java representation of Python attribute access.
            }
        }
        try {
            Field field = source.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return field.get(source);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }
}

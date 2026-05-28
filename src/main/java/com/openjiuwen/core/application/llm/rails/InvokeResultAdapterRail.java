/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.application.llm.rails;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * AFTER_INVOKE rail that converts ReActAgent raw result to legacy LLMAgent schema.
 *
 * <p>Mirrors Python's {@code InvokeResultAdapterRail} in {@code openjiuwen.core.application.llm_agent.rails.invoke_result_adapter_rail}.</p>
 */
public class InvokeResultAdapterRail {

    public static final String INVOKE_RESULT_KEY = "invoke_result";
    private int priority = 90;

    /**
     * Convert ReActAgent raw result dict to legacy LLMAgent output format.
     *
     * @param result the raw result map
     * @return converted output (list or map)
     */
    public static Object convertDictToSchema(Map<String, Object> result) {
        String resultType = (String) result.getOrDefault("result_type", "");
        if ("interrupt".equals(resultType)) {
            Object workflowState = result.get("workflow_execution_state");
            List<?> componentIds = (List<?>) result.get("component_ids");
            String pendingId = componentIds != null && !componentIds.isEmpty()
                    ? String.valueOf(componentIds.get(0))
                    : null;

            List<?> schemas = new ArrayList<>();
            if (workflowState != null) {
                // Get result from workflow state
                try {
                    Object wsResult = workflowState.getClass().getField("result").get(workflowState);
                    if (wsResult instanceof List) {
                        schemas = (List<?>) wsResult;
                    }
                } catch (Exception e) {
                    schemas = new ArrayList<>();
                }
            }

            List<Object> outputSchemas = new ArrayList<>();
            for (Object schema : schemas) {
                outputSchemas.add(schema);
            }
            return outputSchemas;
        } else {
            Map<String, Object> output = new HashMap<>();
            output.put("output", result.getOrDefault("output", ""));
            output.put("result_type", resultType);
            return output;
        }
    }

    /**
     * After invoke callback.
     * <p>
     * Mirrors Python's {@code after_invoke} method which:
     * <ul>
     *   <li>Gets raw_result from ctx.inputs.result</li>
     *   <li>Converts using _convert_dict_to_schema</li>
     *   <li>Stores converted result in ctx.extra["invoke_result"]</li>
     * </ul>
     *
     * @param ctx the agent callback context
     */
    public void afterInvoke(Object ctx) {
        if (ctx == null) {
            return;
        }

        try {
            // Get raw_result from ctx.inputs
            Map<String, Object> rawResult = null;

            // Try to get inputs attribute from context
            if (ctx instanceof Map) {
                rawResult = (Map<String, Object>) ((Map<?, ?>) ctx).get("inputs");
            } else {
                // Try reflection for AgentCallbackContext-like objects
                try {
                    Object inputs = ctx.getClass().getMethod("getInputs").invoke(ctx);
                    if (inputs instanceof Map) {
                        rawResult = (Map<String, Object>) inputs;
                    } else if (inputs != null) {
                        // Try to get result from inputs object
                        Object result = inputs.getClass().getMethod("getResult").invoke(inputs);
                        if (result instanceof Map) {
                            rawResult = (Map<String, Object>) result;
                        }
                    }
                } catch (Exception e) {
                    // Fallback: try direct result attribute
                    try {
                        Object result = ctx.getClass().getMethod("getResult").invoke(ctx);
                        if (result instanceof Map) {
                            rawResult = (Map<String, Object>) result;
                        }
                    } catch (Exception ignored) {
                    }
                }
            }

            if (rawResult == null) {
                return;
            }

            // Convert raw result to schema
            Object convertedResult = convertDictToSchema(rawResult);

            // Store in ctx.extra
            if (ctx instanceof Map) {
                Map<String, Object> ctxMap = (Map<String, Object>) ctx;
                Object extraObj = ctxMap.computeIfAbsent("extra", k -> new HashMap<String, Object>());
                if (extraObj instanceof Map<?, ?> extraMap) {
                    ((Map<String, Object>) extraMap).put(INVOKE_RESULT_KEY, convertedResult);
                }
            } else {
                // Try reflection for AgentCallbackContext-like objects
                try {
                    Object extra = ctx.getClass().getMethod("getExtra").invoke(ctx);
                    if (extra instanceof Map) {
                        ((Map<String, Object>) extra).put(INVOKE_RESULT_KEY, convertedResult);
                    } else if (extra == null) {
                        // Create new extra map if not exists
                        Map<String, Object> newExtra = new HashMap<>();
                        newExtra.put(INVOKE_RESULT_KEY, convertedResult);
                        try {
                            ctx.getClass().getMethod("setExtra", Map.class).invoke(ctx, newExtra);
                        } catch (Exception setEx) {
                            // Try direct field access
                            try {
                                java.lang.reflect.Field extraField = ctx.getClass().getDeclaredField("extra");
                                extraField.setAccessible(true);
                                extraField.set(ctx, newExtra);
                            } catch (Exception fieldEx) {
                                // Unable to set extra - log and continue
                            }
                        }
                    }
                } catch (Exception e) {
                    // Unable to get extra - log and continue
                }
            }

        } catch (Exception e) {
            // Handle any exceptions gracefully
        }
    }

    /**
     * Get the priority.
     *
     * @return the priority
     */
    public int getPriority() {
        return priority;
    }

    /**
     * Set the priority.
     *
     * @param priority the priority
     */
    public void setPriority(int priority) {
        this.priority = priority;
    }
}

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
     *
     * @param ctx the agent callback context
     */
    public void afterInvoke(Object ctx) {
        // TODO: Implement result conversion and storage in ctx.extra
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
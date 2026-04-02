// coding: utf-8
// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.

package com.openjiuwen.agent_evolving.optimizer.tool_call.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.common.logging.Loggers;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Simple API wrapper for tool execution.
 *
 * <p>Mirrors Python's {@code openjiuwen.agent_evolving.optimizer.tool_call.utils.customized_api.SimpleAPIWrapper}.
 */
public class SimpleApiWrapper {

    protected final Map<String, Object> functions = new HashMap<>();
    protected String fnCallName;
    protected Object module;

    /**
     * Create API wrapper with callable.
     *
     * @param toolCallable Tool callable
     * @param name         Function name
     * @param config       Configuration
     */
    public SimpleApiWrapper(Object toolCallable, String name, Map<String, Object> config) {
        this.fnCallName = name;
        this.functions.put(name, toolCallable);
    }

    /**
     * Execute tool call.
     *
     * @param tool      Tool definition
     * @param toolInput Tool input parameters
     * @return Array of [response, status_code]
     */
    public Object[] call(Map<String, Object> tool, Map<String, Object> toolInput) {
        String toolName = (String) tool.get("name");
        Loggers.AGENT.info("=== Trying to execute tool: {}, tool_input: {} ===", tool, toolInput);

        Map<String, Object> params = toolInput;
        Object fn = functions.get(fnCallName);

        if (fn == null) {
            Loggers.AGENT.error("request invalid, no function '{}' found", toolName);
            return new Object[]{
                    "{\"error\": \"request invalid, no function '" + toolName + "' found\", \"response\": \"\"}",
                    12
            };
        }

        try {
            // Execute function - simplified reflection call
            Object output = executeFunction(fn, params);
            ObjectMapper mapper = new ObjectMapper();
            return new Object[]{mapper.writeValueAsString(Map.of("response", output)), 0};
        } catch (Exception e) {
            Loggers.AGENT.error("request invalid, error: {}", e.getMessage());
            return new Object[]{
                    "{\"error\": \"request invalid, error: " + e.getMessage() + "\", \"response\": \"\"}",
                    12
            };
        }
    }

    /**
     * Execute function with parameters.
     *
     * @param fn     Function to execute
     * @param params Parameters
     * @return Execution result
     */
    @SuppressWarnings("unchecked")
    protected Object executeFunction(Object fn, Map<String, Object> params) throws Exception {
        if (fn instanceof java.util.function.Function) {
            return ((java.util.function.Function<Map<String, Object>, Object>) fn).apply(params);
        }
        // Use reflection for other callable types
        java.lang.reflect.Method method = fn.getClass().getMethod("apply", Object.class);
        return method.invoke(fn, params);
    }

    /**
     * Add function to wrapper.
     *
     * @param name Function name
     * @param func Function callable
     */
    public void addFunction(String name, Object func) {
        functions.put(name, func);
    }
}
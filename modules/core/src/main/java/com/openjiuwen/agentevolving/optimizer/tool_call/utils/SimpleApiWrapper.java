/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.optimizer.tool_call.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.common.logging.Loggers;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

/**
 * Simple API wrapper for tool execution.
 * <p>
 * Mirrors Python's {@code openjiuwen.agent_evolving.optimizer.tool_call.utils.customized_api.SimpleAPIWrapper}.
 * 
 * @since 0.1.7
 */
public class SimpleApiWrapper {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * functions.
     * 
     * @since 0.1.7
     */
    protected final Map<String, Object> functions = new HashMap<>();

    /**
     * fnCallName.
     * 
     * @since 0.1.7
     */
    protected String fnCallName;

    /**
     * module.
     * 
     * @since 0.1.7
     */
    protected Object module;

    /**
     * Create API wrapper with callable.
     * 
     * @param toolCallable Tool callable
     * @param name Function name
     * @param config Configuration
     * @since 0.1.7
     */
    public SimpleApiWrapper(Object toolCallable, String name, Map<String, Object> config) {
        this.fnCallName = name;
        this.functions.put(name, toolCallable);
    }

    /**
     * Create API wrapper from a custom-function map.
     * 
     * @param fnCallName Function name to invoke
     * @param customFunctions Custom function registry
     * @since 0.1.7
     */
    public SimpleApiWrapper(String fnCallName, Map<String, Object> customFunctions) {
        this.fnCallName = fnCallName;
        if (customFunctions != null) {
            this.functions.putAll(customFunctions);
        }
    }

    /**
     * Execute tool call.
     * 
     * @param tool Tool definition
     * @param toolInput Tool input parameters
     * @return Array of [response, status_code]
     * @since 0.1.7
     */
    public Object[] call(Map<String, Object> tool, Map<String, Object> toolInput) {
        String toolName = (String) tool.get("name");
        Loggers.AGENT.info("=== Trying to execute tool: {}, tool_input: {} ===", tool, toolInput);

        Map<String, Object> params = toolInput;
        Object fn = functions.get(fnCallName);

        if (fn == null) {
            Loggers.AGENT.error("request invalid, no function '{}' found", toolName);
            return buildErrorResponse("request invalid, no function '" + toolName + "' found");
        }

        try {
            Object output = executeFunction(fn, params);
            return new Object[]{OBJECT_MAPPER.writeValueAsString(Map.of("response", output)), 0};
        } catch (Exception e) {
            Loggers.AGENT.error("request invalid, error: {}", e.getMessage());
            return buildErrorResponse("request invalid, error: " + e.getMessage());
        }
    }

    /**
     * executeFunction.
     * 
     * @param fn fn
     * @param params params
     * @return the result
     * @throws Exception Exception
     * @since 0.1.7
     */
    @SuppressWarnings("unchecked")
    protected Object executeFunction(Object fn, Map<String, Object> params) throws Exception {
        if (fn instanceof Function<?, ?>) {
            return ((Function<Map<String, Object>, Object>) fn).apply(params);
        }
        for (String methodName : List.of("apply", "call", "invoke")) {
            for (java.lang.reflect.Method method : fn.getClass().getMethods()) {
                if (method.getName().equals(methodName) && method.getParameterCount() == 1) {
                    return method.invoke(fn, params);
                }
            }
        }
        throw new NoSuchMethodException("No single-argument callable method found on " + fn.getClass().getName());
    }

    /**
     * Add function to wrapper.
     * 
     * @param name Function name
     * @param func Function callable
     * @since 0.1.7
     */
    public void addFunction(String name, Object func) {
        functions.put(name, func);
    }

    /**
     * loadCustomData.
     * 
     * @param dataPath dataPath
     * @param apiWrapper apiWrapper
     * @return the result
     * @throws Exception Exception
     * @since 0.1.7
     */
    @SuppressWarnings("unchecked")
    public static List<Map<String, Object>> loadCustomData(String dataPath, SimpleApiWrapper apiWrapper)
            throws Exception {
        if (dataPath == null || dataPath.isBlank()) {
            return List.of();
        }

        Path path = Path.of(dataPath);
        if (!Files.exists(path)) {
            return List.of();
        }

        List<Map<String, Object>> tools = new java.util.ArrayList<>();
        String fileName = path.getFileName().toString().toLowerCase(Locale.ROOT);

        if (fileName.endsWith(".jsonl")) {
            for (String line : Files.readAllLines(path)) {
                if (line == null || line.isBlank()) {
                    continue;
                }
                Object parsed = OBJECT_MAPPER.readValue(line, Object.class);
                if (parsed instanceof Map<?, ?> data) {
                    Object functions = data.get("function");
                    if (functions instanceof List<?> list) {
                        for (Object function : list) {
                            addToolEntry(tools, function);
                        }
                    } else {
                        addToolEntry(tools, functions);
                    }
                }
            }
            return tools;
        }

        if (fileName.endsWith(".json")) {
            Object parsed = OBJECT_MAPPER.readValue(Files.readString(path), Object.class);
            if (parsed instanceof List<?> list) {
                for (Object item : list) {
                    if (item instanceof Map<?, ?> map && map.containsKey("function")) {
                        addToolEntry(tools, map.get("function"));
                    } else {
                        addToolEntry(tools, item);
                    }
                }
            } else if (parsed instanceof Map<?, ?> map && map.containsKey("functions")) {
                Object functions = map.get("functions");
                if (functions instanceof List<?> list) {
                    for (Object function : list) {
                        addToolEntry(tools, function);
                    }
                }
            } else {
                // no-op
            }
        }

        return tools;
    }

    /**
     * addToolEntry.
     * 
     * @param tools tools
     * @param functionDefinition functionDefinition
     * @since 0.1.7
     */
    private static void addToolEntry(List<Map<String, Object>> tools, Object functionDefinition) {
        if (!(functionDefinition instanceof Map<?, ?> map)) {
            return;
        }
        Map<String, Object> function = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (entry.getKey() != null) {
                function.put(String.valueOf(entry.getKey()), entry.getValue());
            }
        }
        tools.add(new LinkedHashMap<>(Map.of("type", "function", "function", Collections.unmodifiableMap(function))));
    }

    /**
     * buildErrorResponse.
     * 
     * @param errorMessage errorMessage
     * @return the result
     * @since 0.1.7
     */
    private Object[] buildErrorResponse(String errorMessage) {
        try {
            return new Object[]{OBJECT_MAPPER.writeValueAsString(Map.of("error", errorMessage, "response", "")), 12};
        } catch (JsonProcessingException ignored) {
            return new Object[]{"{\"error\":\"" + errorMessage + "\",\"response\":\"\"}", 12};
        }
    }
}

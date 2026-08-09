/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.optimizer.tool_call.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.common.logging.Loggers;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Simplified version of the custom BFCL-style API wrapper.
 *
 * <p>Mirrors Python's {@code SimpleAPIWrapper} in
 * {@code openjiuwen/agent_evolving/optimizer/tool_call/utils/customized_api.py}.</p>
 */
public class SimpleApiWrapper {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    protected final Map<String, Object> functions = new LinkedHashMap<>();
    protected String fnCallName;
    protected Object module;

    public SimpleApiWrapper(String fnCallName, Map<String, Object> customFunctions) {
        this(null, fnCallName, customFunctions, null);
    }

    public SimpleApiWrapper(
            String toolPath,
            String fnCallName,
            Map<String, Object> customFunctions,
            ToolModuleLoader moduleLoader
    ) {
        this.fnCallName = fnCallName;
        this.module = null;
        if (toolPath != null && !toolPath.isBlank() && Files.exists(Path.of(toolPath)) && moduleLoader != null) {
            LoadedToolModule loaded = moduleLoader.load(toolPath);
            if (loaded != null) {
                this.module = loaded.module();
                if (loaded.functions() != null) {
                    this.functions.putAll(loaded.functions());
                }
            }
        }
        if (customFunctions != null) {
            this.functions.putAll(customFunctions);
        }
    }

    public void addFunction(String name, Object func) {
        functions.put(name, func);
    }

    public void setFnCallName(String fnCallName) {
        this.fnCallName = fnCallName;
    }

    public Object[] call(Map<String, Object> tool, Map<String, Object> toolInput) {
        String toolName = String.valueOf(tool.get("name"));
        Loggers.AGENT.info("=== Trying to execute tool: " + tool + ", tool_input: " + toolInput + " ===");

        Object fn = functions.get(fnCallName);
        if (fn == null) {
            String error = "request invalid, no function '" + toolName + "' found";
            Loggers.AGENT.error(error);
            return buildErrorResponse(error);
        }

        try {
            Object output = executeFunction(fn, toolInput);
            return new Object[]{OBJECT_MAPPER.writeValueAsString(Map.of("response", output)), 0};
        } catch (Exception exception) {
            String error = "request invalid, error: " + exception.getMessage();
            Loggers.AGENT.error(error);
            return buildErrorResponse(error);
        }
    }

    @SuppressWarnings("unchecked")
    protected Object executeFunction(Object fn, Map<String, Object> params) throws Exception {
        if (fn instanceof Function<?, ?> function) {
            return ((Function<Map<String, Object>, Object>) function).apply(params);
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

    @SuppressWarnings("unchecked")
    public static List<Map<String, Object>> loadCustomData(String dataPath, SimpleApiWrapper apiWrapper) throws Exception {
        if (dataPath == null || dataPath.isBlank()) {
            return List.of();
        }

        Path path = Path.of(dataPath);
        if (!Files.exists(path)) {
            return List.of();
        }

        List<Map<String, Object>> tools = new ArrayList<>();
        String fileName = path.getFileName().toString().toLowerCase();

        if (fileName.endsWith(".jsonl")) {
            for (String line : Files.readAllLines(path)) {
                if (line == null || line.isBlank()) {
                    continue;
                }
                Object parsed = OBJECT_MAPPER.readValue(line, Object.class);
                if (parsed instanceof Map<?, ?> data && data.containsKey("function")) {
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
            }
        }

        return tools;
    }

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
        Map<String, Object> toolEntry = new LinkedHashMap<>();
        toolEntry.put("type", "function");
        toolEntry.put("function", function);
        tools.add(toolEntry);
    }

    private Object[] buildErrorResponse(String errorMessage) {
        try {
            return new Object[]{OBJECT_MAPPER.writeValueAsString(Map.of("error", errorMessage, "response", "")), 12};
        } catch (Exception ignored) {
            return new Object[]{"{\"error\":\"" + errorMessage + "\",\"response\":\"\"}", 12};
        }
    }

    /**
     * Mirrors the dynamic tool-module loading boundary in
     * {@code openjiuwen/agent_evolving/optimizer/tool_call/utils/customized_api.py}.
     */
    public interface ToolModuleLoader {

        LoadedToolModule load(String toolPath);
    }

    /**
     * Mirrors the loaded module payload in
     * {@code openjiuwen/agent_evolving/optimizer/tool_call/utils/customized_api.py}.
     */
    public record LoadedToolModule(Object module, Map<String, Object> functions) {
    }
}

/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.tool.mcp;

import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.common.utils.SchemaUtils;
import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.runner.callback.ToolCallEvents;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;

/**
 * MCP tool wrapper.
 *
 * <p>Mirrors Python's {@code MCPTool} in
 * {@code openjiuwen/core/foundation/tool/mcp/base.py}.</p>
 */
public class McpTool extends Tool {

    private final Object mcpClient;
    private final float operationTimeout;

    public McpTool(Object mcpClient, McpToolCard toolInfo) {
        this(mcpClient, toolInfo, McpBase.NO_TIMEOUT);
    }

    public McpTool(Object mcpClient, McpToolCard toolInfo, float operationTimeout) {
        super(toolInfo);
        if (mcpClient == null) {
            throw ErrorHelper.buildError(StatusCode.TOOL_MCP_CLIENT_NOT_SUPPORTED,
                    "card", String.valueOf(getCard()));
        }
        this.mcpClient = mcpClient;
        this.operationTimeout = operationTimeout;
    }

    public Object getMcpClient() {
        return mcpClient;
    }

    @Override
    protected Iterator<Object> streamInternal(Map<String, Object> inputs, Map<String, Object> kwargs) {
        throw ErrorHelper.buildError(StatusCode.TOOL_STREAM_NOT_SUPPORTED, "card", String.valueOf(getCard()));
    }

    @Override
    protected Object invokeInternal(Map<String, Object> inputs, Map<String, Object> kwargs) {
        try {
            Map<String, Object> arguments = inputs != null ? new LinkedHashMap<>(inputs) : new LinkedHashMap<>();
            Map<String, Object> inputParams = getCard().getInputParams();
            if (inputParams != null) {
                triggerCallback(ToolCallEvents.TOOL_PARSE_STARTED, parseStartedKwargs(inputs, inputParams));
                boolean skipNoneValue = !kwargsContains(kwargs, "skip_none_value")
                        || Boolean.TRUE.equals(kwargs.get("skip_none_value"));
                boolean skipValidate = Boolean.TRUE.equals(kwargs != null ? kwargs.get("skip_inputs_validate") : null);
                arguments = SchemaUtils.formatWithSchema(arguments, inputParams, false, skipValidate);
                if (skipNoneValue) {
                    Map<String, Object> cleaned = SchemaUtils.removeNoneValues(arguments);
                    arguments = cleaned != null ? cleaned : new LinkedHashMap<>();
                }
                triggerCallback(ToolCallEvents.TOOL_PARSE_FINISHED, parseFinishedKwargs(arguments));
            }
            Object result = awaitIfNeeded(callTool(arguments, operationTimeout(kwargs)));
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("result", result);
            return payload;
        } catch (Exception error) {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("reason", error.getMessage());
            params.put("method", "invoke");
            params.put("card", String.valueOf(getCard()));
            throw ErrorHelper.buildError(StatusCode.TOOL_MCP_EXECUTION_ERROR, null, null, error, params);
        }
    }

    private Object callTool(Map<String, Object> arguments, float timeout) throws Exception {
        String toolName = getCard().getName();
        Method twoArgumentMethod = null;
        for (Method method : mcpClient.getClass().getMethods()) {
            if (!("callTool".equals(method.getName()) || "call_tool".equals(method.getName()))) {
                continue;
            }
            if (method.getParameterCount() == 3) {
                return invokeCallToolMethod(method, toolName, arguments, timeoutArgument(method, timeout));
            }
            if (method.getParameterCount() == 2) {
                twoArgumentMethod = method;
            }
        }
        if (twoArgumentMethod != null) {
            return invokeCallToolMethod(twoArgumentMethod, toolName, arguments);
        }
        throw new NoSuchMethodException("callTool(String, Map) or call_tool(String, Map)");
    }

    private Object invokeCallToolMethod(Method method, String toolName, Map<String, Object> arguments,
                                        Object... extraArguments) throws Exception {
        Object[] args = new Object[2 + extraArguments.length];
        args[0] = toolName;
        args[1] = arguments;
        System.arraycopy(extraArguments, 0, args, 2, extraArguments.length);
        try {
            return method.invoke(mcpClient, args);
        } catch (InvocationTargetException error) {
            Throwable cause = error.getCause();
            if (cause instanceof Exception exception) {
                throw exception;
            }
            throw new RuntimeException(Objects.requireNonNullElse(cause, error));
        }
    }

    private float operationTimeout(Map<String, Object> kwargs) {
        Object value = first(kwargs, "operation_timeout", "operationTimeout", "timeout");
        if (value == null) {
            return operationTimeout;
        }
        if (value instanceof Number number) {
            return number.floatValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Float.parseFloat(text.trim());
            } catch (NumberFormatException ignored) {
                return operationTimeout;
            }
        }
        return operationTimeout;
    }

    private static Object first(Map<String, Object> values, String... keys) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        for (String key : keys) {
            if (values.containsKey(key)) {
                return values.get(key);
            }
        }
        return null;
    }

    private static Object timeoutArgument(Method method, float timeout) {
        Class<?> type = method.getParameterTypes()[2];
        if (type == double.class || type == Double.class) {
            return (double) timeout;
        }
        return timeout;
    }

    private Map<String, Object> parseStartedKwargs(Map<String, Object> inputs, Map<String, Object> inputParams) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("tool_name", getCard().getName());
        values.put("tool_id", getCard().getId());
        values.put("raw_inputs", inputs);
        values.put("schema", inputParams);
        return values;
    }

    private Map<String, Object> parseFinishedKwargs(Map<String, Object> arguments) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("tool_name", getCard().getName());
        values.put("tool_id", getCard().getId());
        values.put("formatted_inputs", arguments);
        return values;
    }

    private static boolean kwargsContains(Map<String, Object> kwargs, String key) {
        return kwargs != null && kwargs.containsKey(key);
    }

    private static Object awaitIfNeeded(Object value) throws Exception {
        if (!(value instanceof CompletionStage<?> stage)) {
            return value;
        }
        try {
            return stage.toCompletableFuture().get();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw interrupted;
        } catch (ExecutionException | CompletionException executionError) {
            Throwable cause = executionError.getCause();
            if (cause instanceof Exception exception) {
                throw exception;
            }
            throw new RuntimeException(Objects.requireNonNullElse(cause, executionError));
        }
    }
}

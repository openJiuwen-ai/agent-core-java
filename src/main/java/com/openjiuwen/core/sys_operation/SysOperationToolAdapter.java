/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sys_operation;

import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.foundation.tool.function.LocalFunction;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Adapter for converting {@link SysOperation} operations to local-function tools.
 *
 * <p>Mirrors Python's {@code SysOperationToolAdapter} in
 * {@code openjiuwen/core/sys_operation/tool_adapter.py}.</p>
 */
public final class SysOperationToolAdapter {

    private SysOperationToolAdapter() {
    }

    public static List<ToolBinding> extractTools(SysOperationCard card, SysOperation instance) {
        List<ToolBinding> tools = new ArrayList<>();
        for (String opType : OperationRegistry.getSupportedOperations(card.getMode())) {
            BaseOperation subOperation = instance.getOperation(opType);
            if (subOperation == null) {
                continue;
            }
            List<ToolCard> toolCards = subOperation.listTools();
            if (toolCards == null || toolCards.isEmpty()) {
                continue;
            }
            for (ToolCard toolCard : toolCards) {
                String toolId = SysOperationCard.generateToolId(card.getId(), opType, toolCard.getName());
                ToolCard newCard = ToolCard.builder()
                        .id(toolId)
                        .name(toolCard.getName())
                        .description(toolCard.getDescription())
                        .inputParams(toolCard.getInputParams())
                        .properties(toolCard.getProperties())
                        .build();
                Method method = subOperation.resolveMethod(toolCard.getName());
                if (method == null) {
                    continue;
                }
                LocalFunction localFunction = new LocalFunction(
                        newCard,
                        inputs -> invokeMethod(subOperation, method, inputs)
                );
                tools.add(new ToolBinding(toolId, localFunction));
            }
        }
        return tools;
    }

    public static String getToolIdPrefix(String sysOperationId) {
        return sysOperationId + ".";
    }

    public static List<String> getToolIdPrefix(List<String> sysOperationIds) {
        return sysOperationIds.stream().map(SysOperationToolAdapter::getToolIdPrefix).toList();
    }

    private static Object invokeMethod(BaseOperation target, Method method, Map<String, Object> inputs) {
        try {
            return method.invoke(target, resolveArguments(method, inputs));
        } catch (IllegalAccessException exception) {
            throw new IllegalStateException("Cannot access operation method " + method.getName(), exception);
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new IllegalStateException("Operation method failed: " + method.getName(), cause);
        }
    }

    private static Object[] resolveArguments(Method method, Map<String, Object> inputs) {
        Parameter[] parameters = method.getParameters();
        if (parameters.length == 0) {
            return new Object[0];
        }
        if (parameters.length == 1 && Map.class.isAssignableFrom(parameters[0].getType())) {
            return new Object[]{inputs};
        }
        Object[] args = new Object[parameters.length];
        for (int index = 0; index < parameters.length; index += 1) {
            Parameter parameter = parameters[index];
            Object value = inputs == null ? null : inputs.get(parameter.getName());
            if (value == null) {
                value = defaultValue(parameter.getType());
            }
            args[index] = value;
        }
        return args;
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) {
            return null;
        }
        if (type == boolean.class) {
            return false;
        }
        if (type == byte.class) {
            return (byte) 0;
        }
        if (type == short.class) {
            return (short) 0;
        }
        if (type == int.class) {
            return 0;
        }
        if (type == long.class) {
            return 0L;
        }
        if (type == float.class) {
            return 0.0f;
        }
        if (type == double.class) {
            return 0.0d;
        }
        if (type == char.class) {
            return '\0';
        }
        return null;
    }

    /**
     * Mirrors Python's tuple {@code (tool_id, LocalFunction)} from
     * {@code openjiuwen/core/sys_operation/tool_adapter.py}.
     */
    public record ToolBinding(String toolId, LocalFunction localFunction) {
    }
}

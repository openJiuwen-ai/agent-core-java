/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */
package com.openjiuwen.core.sysop;

import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.foundation.tool.function.LocalFunction;
import com.openjiuwen.core.sysop.registry.OperationRegistry;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Adapter for converting SysOperation to LocalFunction tools.
 * <p>
 * Mirrors Python's {@code SysOperationToolAdapter} in {@code sys_operation/tool_adapter.py}.
 */
public final class SysOperationToolAdapter {

    private SysOperationToolAdapter() {
    }

    /**
     * A tuple of (toolId, LocalFunction).
     */
    public record ToolEntry(String toolId, LocalFunction localFunction) {
    }

    /**
     * Extract all tools from SysOperation and wrap them as LocalFunction instances.
     *
     * @param card     SysOperationCard containing operation metadata
     * @param instance SysOperation instance to extract tools from
     * @return list of (toolId, LocalFunction) entries ready for registration
     */
    public static List<ToolEntry> extractTools(SysOperationCard card, SysOperation instance) {
        List<ToolEntry> tools = new ArrayList<>();

        for (String opType : OperationRegistry.getSupportedOperations(card.getMode())) {
            BaseOperation subOp = instance.getOperation(opType);
            if (subOp == null) {
                continue;
            }

            List<ToolCard> toolCards = subOp.listTools();
            if (toolCards == null || toolCards.isEmpty()) {
                continue;
            }

            for (ToolCard toolCard : toolCards) {
                Method method = findToolMethod(subOp.getClass(), toolCard.getName());
                if (method == null) {
                    continue;
                }

                String toolId = SysOperationCard.generateToolId(card.getId(), opType, toolCard.getName());
                ToolCard newCard = ToolCard.builder()
                        .id(toolId)
                        .name(toolCard.getName())
                        .description(toolCard.getDescription())
                        .inputParams(toolCard.getInputParams())
                        .build();

                LocalFunction localFunc = new LocalFunction(newCard,
                        inputs -> invokeToolMethod(subOp, method, inputs != null ? inputs : Map.of()));
                tools.add(new ToolEntry(toolId, localFunc));
            }
        }

        return tools;
    }

    /**
     * Get tool ID prefix for a sys operation.
     *
     * @param sysOperationId the sys operation card ID
     * @return prefix string ending with "."
     */
    public static String getToolIdPrefix(String sysOperationId) {
        return sysOperationId + ".";
    }

    /**
     * Get tool ID prefixes for multiple sys operations.
     * <p>
     * Mirrors Python's {@code get_tool_id_prefix(sys_operation_id: List[str])} overload.
     *
     * @param sysOperationIds list of sys operation card IDs
     * @return list of prefix strings, each ending with "."
     */
    public static List<String> getToolIdPrefix(List<String> sysOperationIds) {
        return sysOperationIds.stream()
                .map(id -> id + ".")
                .toList();
    }

    private static Method findToolMethod(Class<?> operationClass, String methodName) {
        for (Method method : operationClass.getMethods()) {
            if (method.getName().equals(methodName)) {
                return method;
            }
        }
        return null;
    }

    private static Object invokeToolMethod(BaseOperation operation, Method method, Map<String, Object> inputs) {
        Object[] args = buildArguments(method, inputs);
        try {
            return method.invoke(operation, args);
        } catch (InvocationTargetException e) {
            Throwable target = e.getTargetException();
            if (target instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (target instanceof Error error) {
                throw error;
            }
            throw new RuntimeException(target);
        } catch (IllegalAccessException e) {
            throw new RuntimeException("Failed to invoke sys operation method " + method.getName(), e);
        }
    }

    private static Object[] buildArguments(Method method, Map<String, Object> inputs) {
        Parameter[] parameters = method.getParameters();
        Object[] args = new Object[parameters.length];
        for (int i = 0; i < parameters.length; i++) {
            Parameter parameter = parameters[i];
            args[i] = convertValue(inputs.get(parameter.getName()), parameter.getType(), parameter.getParameterizedType());
        }
        return args;
    }

    @SuppressWarnings("unchecked")
    private static Object convertValue(Object rawValue, Class<?> targetType, Type genericType) {
        if (rawValue == null) {
            return defaultValue(targetType);
        }
        if (targetType.isInstance(rawValue)) {
            return rawValue;
        }
        if (targetType == String.class) {
            return String.valueOf(rawValue);
        }
        if (targetType == int.class || targetType == Integer.class) {
            return rawValue instanceof Number ? ((Number) rawValue).intValue() : Integer.parseInt(String.valueOf(rawValue));
        }
        if (targetType == long.class || targetType == Long.class) {
            return rawValue instanceof Number ? ((Number) rawValue).longValue() : Long.parseLong(String.valueOf(rawValue));
        }
        if (targetType == double.class || targetType == Double.class) {
            return rawValue instanceof Number ? ((Number) rawValue).doubleValue() : Double.parseDouble(String.valueOf(rawValue));
        }
        if (targetType == boolean.class || targetType == Boolean.class) {
            if (rawValue instanceof Boolean) {
                return rawValue;
            }
            return Boolean.parseBoolean(String.valueOf(rawValue));
        }
        if (targetType == int[].class) {
            if (rawValue instanceof List<?> list) {
                int[] result = new int[list.size()];
                for (int i = 0; i < list.size(); i++) {
                    Object item = list.get(i);
                    result[i] = item instanceof Number ? ((Number) item).intValue() : Integer.parseInt(String.valueOf(item));
                }
                return result;
            }
            return rawValue;
        }
        if (Map.class.isAssignableFrom(targetType) && rawValue instanceof Map<?, ?> mapValue) {
            return convertMap(mapValue, genericType);
        }
        if (List.class.isAssignableFrom(targetType) && rawValue instanceof List<?> listValue) {
            return convertList(listValue, genericType);
        }
        if (Iterator.class.isAssignableFrom(targetType)) {
            if (rawValue instanceof Iterator<?>) {
                return rawValue;
            }
            if (rawValue instanceof Iterable<?> iterable) {
                return iterable.iterator();
            }
        }
        return rawValue;
    }

    private static Object defaultValue(Class<?> targetType) {
        if (!targetType.isPrimitive()) {
            return null;
        }
        if (targetType == boolean.class) {
            return false;
        }
        if (targetType == int.class) {
            return 0;
        }
        if (targetType == long.class) {
            return 0L;
        }
        if (targetType == double.class) {
            return 0D;
        }
        if (targetType == float.class) {
            return 0F;
        }
        if (targetType == short.class) {
            return (short) 0;
        }
        if (targetType == byte.class) {
            return (byte) 0;
        }
        if (targetType == char.class) {
            return '\0';
        }
        return null;
    }

    private static Map<?, ?> convertMap(Map<?, ?> rawMap, Type genericType) {
        Type valueType = getGenericArgument(genericType, 1);
        if (valueType == String.class) {
            Map<String, String> converted = new LinkedHashMap<>();
            rawMap.forEach((key, value) -> converted.put(String.valueOf(key), value == null ? null : String.valueOf(value)));
            return converted;
        }
        Map<String, Object> converted = new LinkedHashMap<>();
        rawMap.forEach((key, value) -> converted.put(String.valueOf(key), value));
        return converted;
    }

    private static List<?> convertList(List<?> rawList, Type genericType) {
        Type itemType = getGenericArgument(genericType, 0);
        if (itemType == String.class) {
            return rawList.stream().map(String::valueOf).toList();
        }
        return rawList;
    }

    private static Type getGenericArgument(Type genericType, int index) {
        if (genericType instanceof ParameterizedType parameterizedType) {
            Type[] actualTypeArguments = parameterizedType.getActualTypeArguments();
            if (index >= 0 && index < actualTypeArguments.length) {
                return actualTypeArguments[index];
            }
        }
        return Object.class;
    }
}

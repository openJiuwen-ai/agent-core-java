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
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Flow;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicReference;

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
        for (String opType : OperationRegistry.getToolExtractionOperationNames(card.getMode())) {
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
                List<Method> methods = resolveMethods(subOperation, toolCard.getName());
                if (methods.isEmpty()) {
                    continue;
                }
                LocalFunction localFunction = new LocalFunction(
                        newCard,
                        inputs -> invokeMethod(subOperation, methods, inputs)
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

    private static Object invokeMethod(BaseOperation target, List<Method> methods, Map<String, Object> inputs) {
        Method method = selectMethod(methods, inputs);
        try {
            return adaptResult(method.invoke(target, resolveArguments(method, inputs)));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Cannot invoke operation method " + method.getName()
                    + " with inputs " + inputs, exception);
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

    private static List<Method> resolveMethods(BaseOperation operation, String methodName) {
        String javaName = BaseOperation.snakeToCamel(methodName);
        List<Method> methods = new ArrayList<>();
        for (Method method : operation.getClass().getMethods()) {
            if (method.getName().equals(javaName) || method.getName().equals(methodName)) {
                methods.add(method);
            }
        }
        return methods;
    }

    private static Method selectMethod(List<Method> methods, Map<String, Object> inputs) {
        Method bestMethod = null;
        int bestScore = Integer.MIN_VALUE;
        for (Method method : methods) {
            try {
                resolveArguments(method, inputs);
            } catch (IllegalArgumentException exception) {
                continue;
            }
            int score = compatibilityScore(method, inputs);
            if (score > bestScore) {
                bestScore = score;
                bestMethod = method;
            }
        }
        if (bestMethod == null) {
            throw new IllegalArgumentException("No compatible operation method for inputs " + inputs);
        }
        return bestMethod;
    }

    private static int compatibilityScore(Method method, Map<String, Object> inputs) {
        int score = 0;
        for (Parameter parameter : method.getParameters()) {
            if (inputs == null || !inputs.containsKey(parameter.getName())) {
                continue;
            }
            Object value = inputs.get(parameter.getName());
            Class<?> targetType = wrapPrimitive(parameter.getType());
            if (value != null && targetType.isInstance(value)) {
                score += 3;
            } else {
                score += 1;
            }
        }
        return score;
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
            } else {
                value = convertArgument(value, parameter.getType());
            }
            args[index] = value;
        }
        return args;
    }

    private static Object convertArgument(Object value, Class<?> targetType) {
        Class<?> expectedType = wrapPrimitive(targetType);
        if (value == null || expectedType.isInstance(value)) {
            return value;
        }
        if (targetType.isEnum() && value instanceof String text) {
            return convertEnumValue(targetType, text);
        }
        throw new IllegalArgumentException("Value " + value + " is not compatible with "
                + targetType.getSimpleName());
    }

    private static Object convertEnumValue(Class<?> targetType, String value) {
        try {
            Method fromValue = targetType.getMethod("fromValue", String.class);
            return fromValue.invoke(null, value);
        } catch (NoSuchMethodException exception) {
            // Fall through to enum-name matching for enum types without a Python literal helper.
        } catch (IllegalAccessException | InvocationTargetException exception) {
            throw new IllegalArgumentException("Cannot convert enum value " + value
                    + " to " + targetType.getSimpleName(), exception);
        }
        for (Object constant : targetType.getEnumConstants()) {
            Enum<?> enumValue = (Enum<?>) constant;
            if (enumValue.name().equalsIgnoreCase(value)) {
                return enumValue;
            }
        }
        throw new IllegalArgumentException("No enum constant " + targetType.getSimpleName() + " for value " + value);
    }

    private static Class<?> wrapPrimitive(Class<?> type) {
        if (!type.isPrimitive()) {
            return type;
        }
        if (type == boolean.class) {
            return Boolean.class;
        }
        if (type == byte.class) {
            return Byte.class;
        }
        if (type == short.class) {
            return Short.class;
        }
        if (type == int.class) {
            return Integer.class;
        }
        if (type == long.class) {
            return Long.class;
        }
        if (type == float.class) {
            return Float.class;
        }
        if (type == double.class) {
            return Double.class;
        }
        if (type == char.class) {
            return Character.class;
        }
        return type;
    }

    private static Object adaptResult(Object result) {
        if (result instanceof Flow.Publisher<?> publisher) {
            return publisherIterator(publisher);
        }
        return result;
    }

    private static Iterator<Object> publisherIterator(Flow.Publisher<?> publisher) {
        Object end = new Object();
        BlockingQueue<Object> queue = new LinkedBlockingQueue<>();
        AtomicReference<Throwable> error = new AtomicReference<>();
        @SuppressWarnings({"rawtypes", "unchecked"})
        Flow.Publisher<Object> typedPublisher = (Flow.Publisher) publisher;
        typedPublisher.subscribe(new Flow.Subscriber<>() {
            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                subscription.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(Object item) {
                queue.offer(item);
            }

            @Override
            public void onError(Throwable throwable) {
                error.set(throwable);
                queue.offer(end);
            }

            @Override
            public void onComplete() {
                queue.offer(end);
            }
        });
        return new Iterator<>() {
            private Object next;
            private boolean finished;

            @Override
            public boolean hasNext() {
                if (finished) {
                    return false;
                }
                if (next != null) {
                    return true;
                }
                next = takeNext();
                if (next != end) {
                    return true;
                }
                finished = true;
                Throwable throwable = error.get();
                if (throwable != null) {
                    throw new IllegalStateException("Stream publisher failed", throwable);
                }
                return false;
            }

            @Override
            public Object next() {
                if (!hasNext()) {
                    throw new NoSuchElementException();
                }
                Object value = next;
                next = null;
                return value;
            }

            private Object takeNext() {
                try {
                    return queue.take();
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Interrupted while waiting for stream publisher", exception);
                }
            }
        };
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

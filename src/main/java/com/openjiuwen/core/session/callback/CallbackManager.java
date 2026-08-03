/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session.callback;

import com.openjiuwen.core.common.logging.Loggers;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages callback handlers and triggers events.
 *
 * <p>Mirrors Python's {@code CallbackManager} in
 * {@code openjiuwen/core/session/callback/callback_manager.py}.</p>
 */
public class CallbackManager {

    private final Map<String, BaseHandler> handlers = new ConcurrentHashMap<>();
    private final Map<String, List<String>> triggerEvents = new ConcurrentHashMap<>();

    public void register(Map<String, BaseHandler> configs) {
        if (configs == null) {
            return;
        }
        for (Map.Entry<String, BaseHandler> entry : configs.entrySet()) {
            BaseHandler handler = entry.getValue();
            handlers.put(entry.getKey(), handler);
            triggerEvents.put(entry.getKey(), handler.getTriggerEvents());
        }
    }

    public void trigger(String handlerClassName, String eventName, Map<String, Object> kwargs) {
        BaseHandler handler = handlers.get(handlerClassName);
        if (handler == null) {
            Loggers.SESSION.error("Handler not found: {}", handlerClassName);
            return;
        }
        String resolvedEventName = resolveEventName(handlerClassName, eventName);
        if (resolvedEventName == null) {
            throw new IllegalArgumentException("event name not isExists: " + eventName);
        }
        try {
            Method method = findMethod(handler, resolvedEventName);
            if (method == null) {
                return;
            }
            if (method.getParameterCount() == 0) {
                method.invoke(handler);
            } else if (method.getParameterCount() == 1 && method.getParameterTypes()[0] == Map.class) {
                method.invoke(handler, kwargs);
            } else {
                method.invoke(handler, buildMethodArgs(method, kwargs));
            }
        } catch (java.lang.reflect.InvocationTargetException error) {
            Throwable cause = error.getTargetException();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new RuntimeException("Callback handler failed: " + eventName, cause);
        } catch (ReflectiveOperationException | SecurityException error) {
            throw new RuntimeException("Callback invocation failed: " + eventName, error);
        }
    }

    public BaseHandler getHandler(String handlerName) {
        return handlers.get(handlerName);
    }

    private Method findMethod(BaseHandler handler, String eventName) {
        for (Method method : handler.getClass().getMethods()) {
            if (method.getName().equals(eventName)) {
                return method;
            }
        }
        return null;
    }

    private String resolveEventName(String handlerClassName, String eventName) {
        List<String> events = triggerEvents.get(handlerClassName);
        if (events == null || events.isEmpty()) {
            return null;
        }
        if (events.contains(eventName)) {
            return eventName;
        }
        String candidate = snakeToCamel(eventName);
        return events.contains(candidate) ? candidate : null;
    }

    private Object[] buildMethodArgs(Method method, Map<String, Object> kwargs) {
        java.lang.reflect.Parameter[] parameters = method.getParameters();
        Object[] args = new Object[parameters.length];
        for (int index = 0; index < parameters.length; index++) {
            java.lang.reflect.Parameter parameter = parameters[index];
            Object value = null;
            if (kwargs != null) {
                value = kwargs.get(parameter.getName());
                if (value == null) {
                    value = kwargs.get(camelToSnake(parameter.getName()));
                }
            }
            args[index] = value == null && parameter.getType().isPrimitive()
                    ? defaultPrimitiveValue(parameter.getType())
                    : value;
        }
        return args;
    }

    private static Object defaultPrimitiveValue(Class<?> primitiveType) {
        if (primitiveType == boolean.class) {
            return false;
        }
        if (primitiveType == byte.class) {
            return (byte) 0;
        }
        if (primitiveType == short.class) {
            return (short) 0;
        }
        if (primitiveType == int.class) {
            return 0;
        }
        if (primitiveType == long.class) {
            return 0L;
        }
        if (primitiveType == float.class) {
            return 0F;
        }
        if (primitiveType == double.class) {
            return 0D;
        }
        if (primitiveType == char.class) {
            return '\0';
        }
        return null;
    }

    private static String snakeToCamel(String value) {
        if (value == null || value.isEmpty() || !value.contains("_")) {
            return value;
        }
        StringBuilder builder = new StringBuilder();
        boolean upperNext = false;
        for (char ch : value.toCharArray()) {
            if (ch == '_') {
                upperNext = true;
            } else {
                builder.append(upperNext ? Character.toUpperCase(ch) : ch);
                upperNext = false;
            }
        }
        return builder.toString();
    }

    private static String camelToSnake(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        StringBuilder builder = new StringBuilder();
        for (char ch : value.toCharArray()) {
            if (Character.isUpperCase(ch)) {
                builder.append('_').append(Character.toLowerCase(ch));
            } else {
                builder.append(ch);
            }
        }
        return builder.toString();
    }
}

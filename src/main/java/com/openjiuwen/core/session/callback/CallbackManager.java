/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.session.callback;

import com.openjiuwen.core.common.logging.Loggers;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages callback handlers and triggers events.
 * <p>
 * Mirrors Python's {@code openjiuwen.core.session.callback.callback_manager.CallbackManager}.
 */
public class CallbackManager {

    private final Map<String, BaseHandler> handlers = new ConcurrentHashMap<>();
    private final Map<String, List<String>> triggerEvents = new ConcurrentHashMap<>();

    public CallbackManager() {
    }

    /**
     * Register handlers from a config map.
     *
     * @param configs map of handler name -> handler instance
     */
    public void register(Map<String, BaseHandler> configs) {
        if (configs == null) {
            return;
        }
        for (Map.Entry<String, BaseHandler> entry : configs.entrySet()) {
            String handlerName = entry.getKey();
            BaseHandler handler = entry.getValue();
            handlers.put(handlerName, handler);
            triggerEvents.put(handlerName, handler.getTriggerEvents());
        }
    }

    /**
     * Trigger a specific event on a handler.
     *
     * @param handlerClassName the handler name
     * @param eventName        the event method name
     * @param kwargs           the event arguments
     */
    public void trigger(String handlerClassName, String eventName, Map<String, Object> kwargs) {
        BaseHandler handler = handlers.get(handlerClassName);
        if (handler == null) {
            Loggers.SESSION.error("Handler not found: {}", handlerClassName);
            return;
        }

        String resolvedEventName = resolveEventName(handlerClassName, eventName);
        if (resolvedEventName == null) {
            Loggers.SESSION.error("Event name not registered in callback manager: handler={}, event={}",
                    handlerClassName, eventName);
            throw new IllegalArgumentException("event name not exists: " + eventName);
        }

        try {
            Method method = findMethod(handler, resolvedEventName);
            if (method != null) {
                if (method.getParameterCount() == 0) {
                    method.invoke(handler);
                } else if (method.getParameterCount() == 1 && method.getParameterTypes()[0] == Map.class) {
                    method.invoke(handler, kwargs);
                } else {
                    method.invoke(handler, buildMethodArgs(method, kwargs));
                }
            }
        } catch (java.lang.reflect.InvocationTargetException e) {
            Loggers.SESSION.error("Failed to trigger event: handler={}, event={}", handlerClassName, eventName, e);
            // Propagate the target exception to match Python behavior where handler errors are not swallowed
            Throwable cause = e.getTargetException();
            if (cause instanceof RuntimeException runtimeEx) {
                throw runtimeEx;
            }
            throw new RuntimeException("Callback handler failed: " + eventName, cause);
        } catch (Exception e) {
            Loggers.SESSION.error("Failed to trigger event: handler={}, event={}", handlerClassName, eventName, e);
            throw new RuntimeException("Callback invocation failed: " + eventName, e);
        }
    }

    /**
     * Get a registered handler by name.
     *
     * @param handlerName the handler name
     * @return the handler or null
     */
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
        if (events.contains(candidate)) {
            return candidate;
        }
        return null;
    }

    private Object[] buildMethodArgs(Method method, Map<String, Object> kwargs) {
        java.lang.reflect.Parameter[] parameters = method.getParameters();
        Object[] args = new Object[parameters.length];
        for (int i = 0; i < parameters.length; i++) {
            java.lang.reflect.Parameter parameter = parameters[i];
            Object value = null;
            if (kwargs != null) {
                value = kwargs.get(parameter.getName());
                if (value == null) {
                    value = kwargs.get(camelToSnake(parameter.getName()));
                }
            }
            if (value == null && parameter.getType().isPrimitive()) {
                value = defaultPrimitiveValue(parameter.getType());
            }
            args[i] = value;
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
                continue;
            }
            builder.append(upperNext ? Character.toUpperCase(ch) : ch);
            upperNext = false;
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

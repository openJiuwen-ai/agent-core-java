/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session.tracer;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Decorator utilities for wrapping model, tool, and workflow invocations with tracer events.
 * <p>
 * Mirrors Python's {@code openjiuwen/core/session/tracer/decorator.py}.
 * </p>
 */
public final class TracerDecorator {

    private static final String TRACE_HANDLER = "tracer_agent";

    private TracerDecorator() {
    }

    @SuppressWarnings("unchecked")
    public static <T> T decorateModelWithTrace(T model, Object agentSession) {
        Object session = getInnerSession(agentSession);
        if (!shouldDecorate(model, session)) {
            return model;
        }
        Map<String, Object> instanceInfo = new LinkedHashMap<>();
        instanceInfo.put("class_name", resolveModelName(model));
        instanceInfo.put("type", "llm");
        return (T) createTracingProxy(model, session, InvokeType.LLM, instanceInfo);
    }

    @SuppressWarnings("unchecked")
    public static <T> T decorateToolWithTrace(T tool, Object agentSession) {
        Object session = getInnerSession(agentSession);
        if (!shouldDecorate(tool, session)) {
            return tool;
        }
        Map<String, Object> instanceInfo = new LinkedHashMap<>();
        instanceInfo.put("class_name", resolveCardName(tool));
        instanceInfo.put("type", "tool");
        return (T) createTracingProxy(tool, session, InvokeType.PLUGIN, instanceInfo);
    }

    @SuppressWarnings("unchecked")
    public static <T> T decorateWorkflowWithTrace(T workflow, Object agentSession) {
        Object session = getInnerSession(agentSession);
        if (!shouldDecorate(workflow, session)) {
            return workflow;
        }
        Map<String, Object> instanceInfo = new LinkedHashMap<>();
        instanceInfo.put("class_name", resolveCardName(workflow));
        instanceInfo.put("type", "workflow");
        Map<String, Object> metadata = resolveWorkflowMetadata(workflow);
        if (!metadata.isEmpty()) {
            instanceInfo.put("metadata", metadata);
        }
        return (T) createTracingProxy(workflow, session, InvokeType.WORKFLOW, instanceInfo);
    }

    private static boolean shouldDecorate(Object target, Object session) {
        return target != null && session != null && getTracer(session) != null && hasZeroArgMethod(session, "span");
    }

    private static Object getInnerSession(Object agentSession) {
        if (agentSession == null) {
            return null;
        }
        if (hasZeroArgMethod(agentSession, "tracer") && hasZeroArgMethod(agentSession, "span")) {
            return agentSession;
        }
        Object inner = readField(agentSession, "_inner");
        if (inner != null) {
            return inner;
        }
        Object getterInner = invokeZeroArg(agentSession, "getInner");
        if (getterInner != null) {
            return getterInner;
        }
        return null;
    }

    private static Object createTracingProxy(Object target, Object session, InvokeType invokeType,
                                             Map<String, Object> instanceInfo) {
        Class<?>[] interfaces = target.getClass().getInterfaces();
        if (interfaces.length == 0) {
            return target;
        }
        return Proxy.newProxyInstance(
                target.getClass().getClassLoader(),
                interfaces,
                new TracingInvocationHandler(target, session, invokeType, instanceInfo)
        );
    }

    private static String resolveModelName(Object model) {
        Object config = invokeZeroArg(model, "getConfig");
        if (config == null) {
            config = readField(model, "config");
        }
        Object modelConfig = config == null ? null : invokeZeroArg(config, "getModelConfig");
        if (modelConfig == null && config != null) {
            modelConfig = readField(config, "modelConfig");
        }
        Object modelName = modelConfig == null ? null : invokeZeroArg(modelConfig, "getModelName");
        if (modelName == null && modelConfig != null) {
            modelName = readField(modelConfig, "modelName");
        }
        return modelName == null ? model.getClass().getSimpleName() : String.valueOf(modelName);
    }

    private static String resolveCardName(Object target) {
        Object card = invokeZeroArg(target, "getCard");
        if (card == null) {
            card = readField(target, "card");
        }
        Object name = card == null ? null : invokeZeroArg(card, "getName");
        if (name == null && card != null) {
            name = readField(card, "name");
        }
        return name == null ? target.getClass().getSimpleName() : String.valueOf(name);
    }

    private static Map<String, Object> resolveWorkflowMetadata(Object workflow) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        Object card = invokeZeroArg(workflow, "getCard");
        if (card == null) {
            card = readField(workflow, "card");
        }
        if (card == null) {
            return metadata;
        }
        putIfPresent(metadata, "id", readProperty(card, "Id"));
        putIfPresent(metadata, "name", readProperty(card, "Name"));
        putIfPresent(metadata, "description", readProperty(card, "Description"));
        putIfPresent(metadata, "version", readProperty(card, "Version"));
        return metadata;
    }

    private static Object getTracer(Object session) {
        return invokeZeroArg(session, "tracer");
    }

    private static Object getSpan(Object session) {
        return invokeZeroArg(session, "span");
    }

    private static Object createAgentSpan(Object tracer, Object parentSpan) {
        if (tracer == null) {
            return null;
        }
        Object manager = invokeZeroArg(tracer, "getTracerAgentSpanManager");
        if (manager == null) {
            manager = readField(tracer, "tracerAgentSpanManager");
        }
        if (manager == null) {
            manager = readField(tracer, "tracer_agent_span_manager");
        }
        if (manager == null) {
            return null;
        }
        Method withParent = findMethod(manager.getClass(), "createAgentSpan", 1);
        if (withParent != null) {
            return invokeMethod(manager, withParent, parentSpan);
        }
        Method noArg = findMethod(manager.getClass(), "createAgentSpan", 0);
        if (noArg != null) {
            return invokeMethod(manager, noArg);
        }
        return null;
    }

    private static void trigger(Object tracer, String eventName, Map<String, Object> kwargs) {
        if (tracer == null) {
            return;
        }
        for (String methodName : List.of("trigger", "syncTrigger", "sync_trigger")) {
            Method method = findMethod(tracer.getClass(), methodName, 3);
            if (method != null) {
                invokeMethod(tracer, method, TRACE_HANDLER, eventName, kwargs);
                return;
            }
        }
    }

    private static Object invokeMethod(Object target, Method method, Object... args) {
        try {
            method.setAccessible(true);
            return method.invoke(target, args);
        } catch (IllegalAccessException | InvocationTargetException exception) {
            throw new IllegalStateException("Failed to invoke method: " + method.getName(), unwrap(exception));
        }
    }

    private static Method findMethod(Class<?> type, String name, int parameterCount) {
        for (Method method : type.getMethods()) {
            if (method.getName().equals(name) && method.getParameterCount() == parameterCount) {
                return method;
            }
        }
        for (Method method : type.getDeclaredMethods()) {
            if (method.getName().equals(name) && method.getParameterCount() == parameterCount) {
                return method;
            }
        }
        return null;
    }

    private static boolean hasZeroArgMethod(Object target, String name) {
        return target != null && findMethod(target.getClass(), name, 0) != null;
    }

    private static Object invokeZeroArg(Object target, String name) {
        if (target == null) {
            return null;
        }
        Method method = findMethod(target.getClass(), name, 0);
        if (method == null) {
            return null;
        }
        try {
            method.setAccessible(true);
            return method.invoke(target);
        } catch (IllegalAccessException | InvocationTargetException exception) {
            return null;
        }
    }

    private static Object readField(Object target, String fieldName) {
        if (target == null) {
            return null;
        }
        Class<?> current = target.getClass();
        while (current != null) {
            try {
                Field field = current.getDeclaredField(fieldName);
                field.setAccessible(true);
                return field.get(target);
            } catch (NoSuchFieldException exception) {
                current = current.getSuperclass();
            } catch (IllegalAccessException exception) {
                return null;
            }
        }
        return null;
    }

    private static Object readProperty(Object target, String suffix) {
        Object value = invokeZeroArg(target, "get" + suffix);
        if (value != null) {
            return value;
        }
        String fieldName = Character.toLowerCase(suffix.charAt(0)) + suffix.substring(1);
        return readField(target, fieldName);
    }

    private static void putIfPresent(Map<String, Object> target, String key, Object value) {
        if (value != null) {
            target.put(key, value);
        }
    }

    private static Throwable unwrap(Throwable throwable) {
        if (throwable instanceof InvocationTargetException invocationTargetException
                && invocationTargetException.getCause() != null) {
            return invocationTargetException.getCause();
        }
        return throwable;
    }

    private static final class TracingInvocationHandler implements InvocationHandler {

        private final Object target;
        private final Object session;
        private final InvokeType invokeType;
        private final Map<String, Object> instanceInfo;

        private TracingInvocationHandler(Object target, Object session, InvokeType invokeType,
                                         Map<String, Object> instanceInfo) {
            this.target = target;
            this.session = session;
            this.invokeType = invokeType;
            this.instanceInfo = new LinkedHashMap<>(instanceInfo);
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if (!Objects.equals(method.getName(), "invoke") && !Objects.equals(method.getName(), "stream")) {
                return method.invoke(target, args);
            }
            Object tracer = getTracer(session);
            if (tracer == null) {
                return method.invoke(target, args);
            }

            Object span = createAgentSpan(tracer, getSpan(session));
            trigger(tracer, "on_" + invokeType.getValue() + "_start", startPayload(span, args));
            Object[] invocationArgs = invokeType == InvokeType.LLM
                    ? withTracerRecordCallback(args, tracer, span)
                    : args;
            try {
                Object result = method.invoke(target, invocationArgs);
                if ("stream".equals(method.getName())) {
                    return wrapStreamResult(result, tracer, span);
                }
                trigger(tracer, "on_" + invokeType.getValue() + "_end", Map.of(
                        "span", span,
                        "outputs", Map.of("outputs", result)
                ));
                return result;
            } catch (InvocationTargetException exception) {
                Throwable cause = unwrap(exception);
                trigger(tracer, "on_" + invokeType.getValue() + "_error", Map.of(
                        "span", span,
                        "error", cause
                ));
                throw cause;
            }
        }

        private Map<String, Object> startPayload(Object span, Object[] args) {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("span", span);
            payload.put("inputs", Map.of("inputs", firstInput(args)));
            payload.put("instance_info", new LinkedHashMap<>(instanceInfo));
            return payload;
        }

        private Object firstInput(Object[] args) {
            if (args != null && args.length > 0) {
                return args[0];
            }
            return Map.of();
        }

        @SuppressWarnings("unchecked")
        private Object[] withTracerRecordCallback(Object[] args, Object tracer, Object span) {
            if (args == null || args.length == 0 || !(args[args.length - 1] instanceof Map<?, ?> rawMap)) {
                return args;
            }
            Map<String, Object> kwargs = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
                kwargs.put(String.valueOf(entry.getKey()), entry.getValue());
            }
            Object existing = kwargs.get("tracer_record_data");
            Consumer<Map<String, Object>> callback = payload -> {
                Map<String, Object> eventKwargs = new LinkedHashMap<>();
                eventKwargs.put("span", span);
                if (payload != null) {
                    eventKwargs.putAll(payload);
                }
                trigger(tracer, "on_" + invokeType.getValue() + "_request", eventKwargs);
                invokeExistingCallback(existing, payload);
            };
            kwargs.put("tracer_record_data", callback);
            Object[] updated = args.clone();
            updated[updated.length - 1] = kwargs;
            return updated;
        }

        @SuppressWarnings("unchecked")
        private void invokeExistingCallback(Object callback, Map<String, Object> payload) {
            if (callback instanceof Consumer<?> consumer) {
                ((Consumer<Map<String, Object>>) consumer).accept(payload);
            }
        }

        private Object wrapStreamResult(Object result, Object tracer, Object span) {
            if (result instanceof Iterator<?> iterator) {
                return new Iterator<>() {
                    private final List<Object> outputs = new ArrayList<>();
                    private boolean finished;

                    @Override
                    public boolean hasNext() {
                        boolean hasNext = iterator.hasNext();
                        if (!hasNext) {
                            finishOnce();
                        }
                        return hasNext;
                    }

                    @Override
                    public Object next() {
                        Object item = iterator.next();
                        outputs.add(item);
                        return item;
                    }

                    private void finishOnce() {
                        if (finished) {
                            return;
                        }
                        finished = true;
                        trigger(tracer, "on_" + invokeType.getValue() + "_end", Map.of(
                                "span", span,
                                "outputs", Map.of("outputs", List.copyOf(outputs))
                        ));
                    }
                };
            }
            if (result instanceof Iterable<?> iterable) {
                return wrapStreamResult(iterable.iterator(), tracer, span);
            }
            trigger(tracer, "on_" + invokeType.getValue() + "_end", Map.of(
                    "span", span,
                    "outputs", Map.of("outputs", result)
            ));
            return result;
        }
    }
}

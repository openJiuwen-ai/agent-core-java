/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.session.tracer;

import com.openjiuwen.core.session.internal.AgentSession;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;

/**
 * Tracer decorator utilities for wrapping model, tool, and workflow invocations with trace spans.
 * <p>
 * Mirrors Python's {@code openjiuwen.core.session.tracer.decorator} module.
 * In Java, dynamic proxying replaces Python's dynamic class wrapping.
 */
public final class TracerDecorator {

    private TracerDecorator() {
    }

    /**
     * Check if the object+session combination should be decorated with trace.
     */
    private static boolean shouldDecorate(Object obj, Object session) {
        if (obj == null || session == null) {
            return false;
        }
        try {
            Method tracerMethod = session.getClass().getMethod("tracer");
            Object tracer = tracerMethod.invoke(session);
            if (tracer == null) {
                return false;
            }
            Method spanMethod = session.getClass().getMethod("span");
            return spanMethod != null;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Decorate a model with trace. Wraps invoke/stream calls with trace span recording.
     *
     * @param model        the model object
     * @param agentSession the agent session API (expects an _inner field or getInner method)
     * @param <T>          the model type
     * @return the wrapped model, or original if tracing is not applicable
     */
    @SuppressWarnings("unchecked")
    public static <T> T decorateModelWithTrace(T model, Object agentSession) {
        Object innerSession = getInnerSession(agentSession);
        if (!shouldDecorate(model, innerSession)) {
            return model;
        }

        String modelName = getClassName(model);
        Map<String, Object> instanceInfo = new HashMap<>();
        instanceInfo.put("class_name", modelName);
        instanceInfo.put("type", "llm");

        return createTracingProxy(model, innerSession, InvokeType.LLM, instanceInfo);
    }

    /**
     * Decorate a tool with trace. Wraps invoke calls with trace span recording.
     *
     * @param tool         the tool object
     * @param agentSession the agent session API
     * @param <T>          the tool type
     * @return the wrapped tool, or original if tracing is not applicable
     */
    @SuppressWarnings("unchecked")
    public static <T> T decorateToolWithTrace(T tool, Object agentSession) {
        Object innerSession = getInnerSession(agentSession);
        if (!shouldDecorate(tool, innerSession)) {
            return tool;
        }

        String toolName = getCardName(tool);
        Map<String, Object> instanceInfo = new HashMap<>();
        instanceInfo.put("class_name", toolName);
        instanceInfo.put("type", "tool");

        return createTracingProxy(tool, innerSession, InvokeType.PLUGIN, instanceInfo);
    }

    /**
     * Decorate a workflow with trace. Wraps invoke/stream calls with trace span recording.
     *
     * @param workflow     the workflow object
     * @param agentSession the agent session API
     * @param <T>          the workflow type
     * @return the wrapped workflow, or original if tracing is not applicable
     */
    @SuppressWarnings("unchecked")
    public static <T> T decorateWorkflowWithTrace(T workflow, Object agentSession) {
        Object innerSession = getInnerSession(agentSession);
        if (!shouldDecorate(workflow, innerSession)) {
            return workflow;
        }

        String wfName = getCardName(workflow);
        Map<String, Object> instanceInfo = new HashMap<>();
        instanceInfo.put("class_name", wfName);
        instanceInfo.put("type", "workflow");

        // Try to collect metadata from card
        try {
            Method cardMethod = workflow.getClass().getMethod("getCard");
            Object card = cardMethod.invoke(workflow);
            if (card != null) {
                Map<String, Object> metadata = new HashMap<>();
                tryPutField(metadata, "id", card);
                tryPutField(metadata, "name", card);
                tryPutField(metadata, "description", card);
                tryPutField(metadata, "version", card);
                instanceInfo.put("metadata", metadata);
            }
        } catch (Exception ignored) {
            // no card — skip metadata
        }

        return createTracingProxy(workflow, innerSession, InvokeType.WORKFLOW, instanceInfo);
    }

    /**
     * Synchronous trace wrapper around a function-like call.
     * Mirrors Python's {@code trace()}.
     *
     * @param session      the agent session (must expose tracer() and span())
     * @param invokeType   the type of invocation
     * @param instanceInfo descriptive info about the invoked instance
     * @param callable     the callable to wrap (args -> result)
     * @param args         the input arguments
     * @param kwargs       the keyword-style arguments
     * @return the invocation result
     */
    public static Object trace(Object session, InvokeType invokeType,
                               Map<String, Object> instanceInfo,
                               BiFunction<Object[], Map<String, Object>, Object> callable,
                               Object[] args, Map<String, Object> kwargs) {
        Tracer tracer = getTracer(session);
        if (tracer == null) {
            return callable.apply(args, kwargs);
        }
        TraceAgentSpan span = null;
        try {
            Span agentSpan = getSpan(session);
            span = tracer.getTracerAgentSpanManager().createAgentSpan(agentSpan);

            Map<String, Object> triggerKwargs = new HashMap<>();
            triggerKwargs.put("span", span);
            triggerKwargs.put("inputs", Map.of("inputs", args != null && args.length > 0 ? args[0] : new HashMap<>()));
            triggerKwargs.put("instance_info", instanceInfo);
            tracer.trigger(TracerHandlerName.TRACE_AGENT.getValue(),
                    "on_" + invokeType.getValue() + "_start", triggerKwargs);

            Object result = callable.apply(args, kwargs);

            Map<String, Object> endKwargs = new HashMap<>();
            endKwargs.put("span", span);
            endKwargs.put("outputs", Map.of("outputs", result));
            tracer.trigger(TracerHandlerName.TRACE_AGENT.getValue(),
                    "on_" + invokeType.getValue() + "_end", endKwargs);

            return result;
        } catch (Exception error) {
            Map<String, Object> errorKwargs = new HashMap<>();
            errorKwargs.put("span", span);
            errorKwargs.put("error", error);
            tracer.trigger(TracerHandlerName.TRACE_AGENT.getValue(),
                    "on_" + invokeType.getValue() + "_error", errorKwargs);
            throw error;
        }
    }

    // ---- private helpers ----

    private static Object getInnerSession(Object agentSession) {
        if (agentSession == null) {
            return null;
        }
        if (agentSession instanceof AgentSession
                || (hasMethod(agentSession, "tracer") && hasMethod(agentSession, "span"))) {
            return agentSession;
        }
        try {
            Method getInner = agentSession.getClass().getMethod("getInner");
            return getInner.invoke(agentSession);
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean hasMethod(Object obj, String methodName) {
        try {
            obj.getClass().getMethod(methodName);
            return true;
        } catch (NoSuchMethodException e) {
            return false;
        }
    }

    private static Tracer getTracer(Object session) {
        try {
            Method tracerMethod = session.getClass().getMethod("tracer");
            Object result = tracerMethod.invoke(session);
            return result instanceof Tracer ? (Tracer) result : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static Span getSpan(Object session) {
        try {
            Method spanMethod = session.getClass().getMethod("span");
            Object result = spanMethod.invoke(session);
            return result instanceof Span ? (Span) result : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static String getClassName(Object obj) {
        try {
            Method configMethod = obj.getClass().getMethod("getConfig");
            Object config = configMethod.invoke(obj);
            if (config != null) {
                Method modelConfigMethod = config.getClass().getMethod("getModelConfig");
                Object modelConfig = modelConfigMethod.invoke(config);
                if (modelConfig != null) {
                    Method nameMethod = modelConfig.getClass().getMethod("getModelName");
                    Object name = nameMethod.invoke(modelConfig);
                    if (name != null) {
                        return name.toString();
                    }
                }
            }
        } catch (Exception ignored) {
            // fall through
        }
        return obj.getClass().getSimpleName();
    }

    private static String getCardName(Object obj) {
        try {
            Method cardMethod = obj.getClass().getMethod("getCard");
            Object card = cardMethod.invoke(obj);
            if (card != null) {
                Method nameMethod = card.getClass().getMethod("getName");
                Object name = nameMethod.invoke(card);
                if (name != null) {
                    return name.toString();
                }
            }
        } catch (Exception ignored) {
            // fall through
        }
        return obj.getClass().getSimpleName();
    }

    private static void tryPutField(Map<String, Object> map, String field, Object obj) {
        try {
            String getter = "get" + Character.toUpperCase(field.charAt(0)) + field.substring(1);
            Method method = obj.getClass().getMethod(getter);
            Object value = method.invoke(obj);
            if (value != null) {
                map.put(field, value);
            }
        } catch (Exception ignored) {
            // skip
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T createTracingProxy(T original, Object session,
                                             InvokeType invokeType,
                                             Map<String, Object> instanceInfo) {
        Class<?>[] interfaces = original.getClass().getInterfaces();
        if (interfaces.length == 0) {
            // Cannot proxy non-interface types; return original
            return original;
        }

        Tracer tracer = getTracer(session);
        if (tracer == null) {
            return original;
        }

        Object proxy = Proxy.newProxyInstance(
                original.getClass().getClassLoader(),
                interfaces,
                new TracingInvocationHandler(original, session, tracer, invokeType, instanceInfo));

        return (T) proxy;
    }

    /**
     * Dynamic invocation handler that wraps "invoke" and "stream" methods with tracing.
     */
    private static class TracingInvocationHandler implements InvocationHandler {
        private final Object target;
        private final Object session;
        private final Tracer tracer;
        private final InvokeType invokeType;
        private final Map<String, Object> instanceInfo;

        TracingInvocationHandler(Object target, Object session, Tracer tracer,
                                 InvokeType invokeType, Map<String, Object> instanceInfo) {
            this.target = target;
            this.session = session;
            this.tracer = tracer;
            this.invokeType = invokeType;
            this.instanceInfo = instanceInfo;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            String methodName = method.getName();

            // Only trace "invoke" and "stream" methods
            if ("invoke".equals(methodName) || "stream".equals(methodName)) {
                TraceAgentSpan span = null;
                try {
                    Span agentSpan = getSpan(session);
                    span = tracer.getTracerAgentSpanManager().createAgentSpan(agentSpan);

                    Map<String, Object> startKwargs = new HashMap<>();
                    startKwargs.put("span", span);
                    startKwargs.put("inputs", Map.of("inputs",
                            args != null && args.length > 0 ? args[0] : new HashMap<>()));
                    startKwargs.put("instance_info", instanceInfo);
                    tracer.trigger(TracerHandlerName.TRACE_AGENT.getValue(),
                            "on_" + invokeType.getValue() + "_start", startKwargs);

                    Object result = method.invoke(target, args);

                    Map<String, Object> endKwargs = new HashMap<>();
                    endKwargs.put("span", span);
                    endKwargs.put("outputs", Map.of("outputs", result));
                    tracer.trigger(TracerHandlerName.TRACE_AGENT.getValue(),
                            "on_" + invokeType.getValue() + "_end", endKwargs);

                    return result;
                } catch (Exception error) {
                    Throwable cause = error.getCause() != null ? error.getCause() : error;
                    Map<String, Object> errorKwargs = new HashMap<>();
                    errorKwargs.put("span", span);
                    errorKwargs.put("error", cause);
                    tracer.trigger(TracerHandlerName.TRACE_AGENT.getValue(),
                            "on_" + invokeType.getValue() + "_error", errorKwargs);
                    throw cause;
                }
            }

            // All other methods delegate directly
            return method.invoke(target, args);
        }
    }
}

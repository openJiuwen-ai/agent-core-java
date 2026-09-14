/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent.rail;

import com.openjiuwen.core.singleagent.BaseAgent;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Base class for class-based agent rails.
 *
 * <p>Hook methods are {@code void}, matching 930 / Python {@code AgentRail}.
 * {@link #getCallbacks()} registers only methods overridden by a subclass and
 * adapts them to the async {@link AgentCallback} contract used by the callback
 * framework.</p>
 */
public abstract class AgentRail {

    /**
     * Event to Java hook method name. Higher-priority rails still run first via
     * {@link #getPriority()}.
     */
    public static final Map<AgentCallbackEvent, String> EVENT_METHOD_MAP = new EnumMap<>(AgentCallbackEvent.class);

    static {
        EVENT_METHOD_MAP.put(AgentCallbackEvent.BEFORE_INVOKE, "beforeInvoke");
        EVENT_METHOD_MAP.put(AgentCallbackEvent.AFTER_INVOKE, "afterInvoke");
        EVENT_METHOD_MAP.put(AgentCallbackEvent.BEFORE_MODEL_CALL, "beforeModelCall");
        EVENT_METHOD_MAP.put(AgentCallbackEvent.AFTER_MODEL_CALL, "afterModelCall");
        EVENT_METHOD_MAP.put(AgentCallbackEvent.ON_MODEL_EXCEPTION, "onModelException");
        EVENT_METHOD_MAP.put(AgentCallbackEvent.BEFORE_TOOL_CALL, "beforeToolCall");
        EVENT_METHOD_MAP.put(AgentCallbackEvent.AFTER_TOOL_CALL, "afterToolCall");
        EVENT_METHOD_MAP.put(AgentCallbackEvent.ON_TOOL_EXCEPTION, "onToolException");
        EVENT_METHOD_MAP.put(AgentCallbackEvent.BEFORE_TASK_ITERATION, "beforeTaskIteration");
        EVENT_METHOD_MAP.put(AgentCallbackEvent.AFTER_TASK_ITERATION, "afterTaskIteration");
    }

    /** Execution priority; a higher value runs before lower-priority callbacks. */
    private int priority = 50;

    /**
     * Lifecycle hook invoked when the rail is registered.
     *
     * @param agent owning agent or host
     */
    public void init(Object agent) {
    }

    /**
     * Lifecycle hook invoked when the rail is unregistered.
     *
     * @param agent owning agent or host
     */
    public void uninit(Object agent) {
    }

    /**
     * Typed init entry used by {@link BaseAgent#registerRail(AgentRail)}.
     *
     * @param agent owning agent
     */
    public void init(BaseAgent agent) {
        init((Object) agent);
    }

    /**
     * Typed uninit entry used when a rail is removed from a {@link BaseAgent}.
     *
     * @param agent owning agent
     */
    public void uninit(BaseAgent agent) {
        uninit((Object) agent);
    }

    /**
     * Called before {@code agent.invoke()}.
     *
     * @param context callback context
     */
    public void beforeInvoke(AgentCallbackContext context) {
    }

    /**
     * Called after {@code agent.invoke()} completes.
     *
     * @param context callback context
     */
    public void afterInvoke(AgentCallbackContext context) {
    }

    /**
     * Called before an LLM request.
     *
     * @param context callback context
     */
    public void beforeModelCall(AgentCallbackContext context) {
    }

    /**
     * Called after an LLM response is received.
     *
     * @param context callback context
     */
    public void afterModelCall(AgentCallbackContext context) {
    }

    /**
     * Called when an LLM request raises.
     *
     * @param context callback context
     */
    public void onModelException(AgentCallbackContext context) {
    }

    /**
     * Called before a tool is executed.
     *
     * @param context callback context
     */
    public void beforeToolCall(AgentCallbackContext context) {
    }

    /**
     * Called after a tool execution completes.
     *
     * @param context callback context
     */
    public void afterToolCall(AgentCallbackContext context) {
    }

    /**
     * Called when tool execution raises.
     *
     * @param context callback context
     */
    public void onToolException(AgentCallbackContext context) {
    }

    /**
     * Called before an outer task-loop iteration.
     *
     * @param context callback context
     */
    public void beforeTaskIteration(AgentCallbackContext context) {
    }

    /**
     * Called after an outer task-loop iteration.
     *
     * @param context callback context
     */
    public void afterTaskIteration(AgentCallbackContext context) {
    }

    /**
     * Returns callbacks for hook methods actually overridden by the concrete class.
     *
     * @return event to async callback adapter
     */
    public Map<AgentCallbackEvent, AgentCallback> getCallbacks() {
        Map<AgentCallbackEvent, AgentCallback> callbacks = new EnumMap<>(AgentCallbackEvent.class);
        for (Map.Entry<AgentCallbackEvent, String> entry : EVENT_METHOD_MAP.entrySet()) {
            AgentCallback callback = callbackIfOverridden(entry.getValue());
            if (callback != null) {
                callbacks.put(entry.getKey(), callback);
            }
        }
        return callbacks;
    }

    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }

    protected static CompletionStage<Void> completed() {
        return CompletableFuture.completedFuture(null);
    }

    private AgentCallback callbackIfOverridden(String methodName) {
        if (isBaseMethod(methodName)) {
            return null;
        }
        return wrapVoidHook(methodName);
    }

    private boolean isBaseMethod(String methodName) {
        try {
            Method subclassMethod = this.getClass().getMethod(methodName, AgentCallbackContext.class);
            Method baseMethod = AgentRail.class.getMethod(methodName, AgentCallbackContext.class);
            return subclassMethod.equals(baseMethod);
        } catch (NoSuchMethodException ex) {
            return true;
        }
    }

    private AgentCallback wrapVoidHook(String methodName) {
        try {
            Method method = this.getClass().getMethod(methodName, AgentCallbackContext.class);
            method.setAccessible(true);
            return context -> invokeVoidHook(method, methodName, context);
        } catch (NoSuchMethodException ex) {
            return null;
        }
    }

    private CompletionStage<Void> invokeVoidHook(Method method, String methodName, AgentCallbackContext context) {
        try {
            method.invoke(this, context);
            return completed();
        } catch (InvocationTargetException ex) {
            throw unwrapInvocation(methodName, ex);
        } catch (IllegalAccessException ex) {
            throw new IllegalStateException("Error invoking rail callback: " + methodName, ex);
        }
    }

    private static RuntimeException unwrapInvocation(String methodName, InvocationTargetException ex) {
        Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
        if (cause instanceof RuntimeException runtimeException) {
            return runtimeException;
        }
        if (cause instanceof Error error) {
            throw error;
        }
        return new IllegalStateException("Error invoking rail callback: " + methodName, cause);
    }
}

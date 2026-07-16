/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent.rail;

import com.openjiuwen.core.foundation.tool.ToolCard;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Base class for agent rails.
 * <p>
 * Rails provide class-based lifecycle hooks with:
 * <ul>
 * <li>State management across callback invocations</li>
 * <li>Tools that are auto-registered on the agent</li>
 * <li>Priority-based execution ordering (lower = runs first)</li>
 * </ul>
 * <p>
 * Example:
 * 
 * <pre>
 * {
 *     &#64;code
 *     class LogRail extends AgentRail {
 *         &#64;Override
 *         public void beforeModelCall(AgentCallbackContext ctx) {
 *             System.out.println("calling LLM...");
 *         }
 * 
 *         @Override
 *         public void afterModelCall(AgentCallbackContext ctx) {
 *             System.out.println("LLM responded");
 *         }
 *     }
 *     agent.registerRail(new LogRail());
 * }
 * </pre>
 * 
 * @since 0.1.7
 */
public abstract class AgentRail {
    /**
     * EVENT_METHOD_MAP.
     * 
     * @since 0.1.7
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
    }

    private int priority = 50;
    private final List<ToolCard> tools;
    private final List<Object> skills;

    /**
     * AgentRail.
     * 
     * @since 0.1.7
     */
    protected AgentRail() {
        this(null, null);
    }

    /**
     * AgentRail.
     * 
     * @param tools tools
     * @since 0.1.7
     */
    protected AgentRail(List<ToolCard> tools) {
        this(tools, null);
    }

    /**
     * AgentRail.
     * 
     * @param tools tools
     * @param skills skills
     * @since 0.1.7
     */
    protected AgentRail(List<ToolCard> tools, List<Object> skills) {
        this.tools = tools != null ? tools : new ArrayList<>();
        this.skills = skills != null ? skills : new ArrayList<>();
    }

    /**
     * getPriority.
     * 
     * @return the result
     * @since 0.1.7
     */
    public int getPriority() {
        return priority;
    }

    /**
     * setPriority.
     * 
     * @param priority priority
     * @since 0.1.7
     */
    public void setPriority(int priority) {
        this.priority = priority;
    }

    /**
     * getTools.
     * 
     * @return the result
     * @since 0.1.7
     */
    public List<ToolCard> getTools() {
        return tools;
    }

    /**
     * Skills carried by this rail (reserved for future use).
     * 
     * @return list of skills
     * @since 0.1.7
     */
    public List<Object> getSkills() {
        return skills;
    }

    /**
     * Lifecycle hook invoked when the rail is registered on an agent.
     * 
     * @param agent owning agent
     * @since 0.1.7
     */
    public void init(Object agent) {
    }

    /**
     * Lifecycle hook invoked when the rail is unregistered from an agent.
     * 
     * @param agent owning agent
     * @since 0.1.7
     */
    public void uninit(Object agent) {
    }

    // -- 8 hook methods (override to activate) --

    /**
     * beforeInvoke.
     * 
     * @param ctx ctx
     * @since 0.1.7
     */
    public void beforeInvoke(AgentCallbackContext ctx) {
    }

    /**
     * afterInvoke.
     * 
     * @param ctx ctx
     * @since 0.1.7
     */
    public void afterInvoke(AgentCallbackContext ctx) {
    }

    /**
     * beforeModelCall.
     * 
     * @param ctx ctx
     * @since 0.1.7
     */
    public void beforeModelCall(AgentCallbackContext ctx) {
    }

    /**
     * afterModelCall.
     * 
     * @param ctx ctx
     * @since 0.1.7
     */
    public void afterModelCall(AgentCallbackContext ctx) {
    }

    /**
     * onModelException.
     * 
     * @param ctx ctx
     * @since 0.1.7
     */
    public void onModelException(AgentCallbackContext ctx) {
    }

    /**
     * beforeToolCall.
     * 
     * @param ctx ctx
     * @since 0.1.7
     */
    public void beforeToolCall(AgentCallbackContext ctx) {
    }

    /**
     * afterToolCall.
     * 
     * @param ctx ctx
     * @since 0.1.7
     */
    public void afterToolCall(AgentCallbackContext ctx) {
    }

    /**
     * onToolException.
     * 
     * @param ctx ctx
     * @since 0.1.7
     */
    public void onToolException(AgentCallbackContext ctx) {
    }

    /**
     * Extract overridden hook methods.
     * 
     * @return map of event to callback, only for methods overridden by the subclass
     * @since 0.1.7
     */
    public Map<AgentCallbackEvent, Consumer<AgentCallbackContext>> getCallbacks() {
        Map<AgentCallbackEvent, Consumer<AgentCallbackContext>> callbacks = new EnumMap<>(AgentCallbackEvent.class);
        for (Map.Entry<AgentCallbackEvent, String> entry : EVENT_METHOD_MAP.entrySet()) {
            if (!isBaseMethod(entry.getValue())) {
                Consumer<AgentCallbackContext> callback = buildCallback(entry.getValue());
                if (callback != null) {
                    callbacks.put(entry.getKey(), callback);
                }
            }
        }
        return callbacks;
    }

    /**
     * isBaseMethod.
     * 
     * @param methodName methodName
     * @return the result
     * @since 0.1.7
     */
    private boolean isBaseMethod(String methodName) {
        try {
            Method subclassMethod = this.getClass().getMethod(methodName, AgentCallbackContext.class);
            Method baseMethod = AgentRail.class.getMethod(methodName, AgentCallbackContext.class);
            return subclassMethod.equals(baseMethod);
        } catch (NoSuchMethodException e) {
            return true;
        }
    }

    /**
     * buildCallback.
     * 
     * @param methodName methodName
     * @return the result
     * @since 0.1.7
     */
    private Consumer<AgentCallbackContext> buildCallback(String methodName) {
        try {
            Method method = this.getClass().getMethod(methodName, AgentCallbackContext.class);
            method.setAccessible(true);
            return ctx -> {
                try {
                    method.invoke(this, ctx);
                } catch (Exception e) {
                    throw new RuntimeException("Error invoking rail callback: " + methodName, e);
                }
            };
        } catch (NoSuchMethodException e) {
            return null;
        }
    }
}

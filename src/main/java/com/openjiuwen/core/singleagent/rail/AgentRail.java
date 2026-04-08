/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
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
 *
 * <p>Rails provide class-based lifecycle hooks with:
 * <ul>
 *   <li>State management across callback invocations</li>
 *   <li>Tools that are auto-registered on the agent</li>
 *   <li>Priority-based execution ordering (lower = runs first)</li>
 * </ul>
 *
 * <p>Example:
 * <pre>{@code
 * class LogRail extends AgentRail {
 *     @Override
 *     public void beforeModelCall(AgentCallbackContext ctx) {
 *         System.out.println("calling LLM...");
 *     }
 *
 *     @Override
 *     public void afterModelCall(AgentCallbackContext ctx) {
 *         System.out.println("LLM responded");
 *     }
 * }
 *
 * agent.registerRail(new LogRail());
 * }</pre>
 */
public abstract class AgentRail {

    /**
     * Public mapping from callback event to hook method name.
     *
     * <p>Mirrors Python's {@code EVENT_METHOD_MAP} exported in
     * {@code rail.__all__}.</p>
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

    protected AgentRail() {
        this(null, null);
    }

    protected AgentRail(List<ToolCard> tools) {
        this(tools, null);
    }

    protected AgentRail(List<ToolCard> tools, List<Object> skills) {
        this.tools = tools != null ? tools : new ArrayList<>();
        this.skills = skills != null ? skills : new ArrayList<>();
    }

    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }

    public List<ToolCard> getTools() {
        return tools;
    }

    /**
     * Skills carried by this rail (reserved for future use).
     *
     * @return list of skills
     */
    public List<Object> getSkills() {
        return skills;
    }

    // -- 8 hook methods (override to activate) --

    public void beforeInvoke(AgentCallbackContext ctx) {}

    public void afterInvoke(AgentCallbackContext ctx) {}

    public void beforeModelCall(AgentCallbackContext ctx) {}

    public void afterModelCall(AgentCallbackContext ctx) {}

    public void onModelException(AgentCallbackContext ctx) {}

    public void beforeToolCall(AgentCallbackContext ctx) {}

    public void afterToolCall(AgentCallbackContext ctx) {}

    public void onToolException(AgentCallbackContext ctx) {}

    /**
     * Extract overridden hook methods.
     *
     * @return map of event to callback, only for methods overridden by the subclass
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

    private boolean isBaseMethod(String methodName) {
        try {
            Method subclassMethod = this.getClass().getMethod(methodName, AgentCallbackContext.class);
            Method baseMethod = AgentRail.class.getMethod(methodName, AgentCallbackContext.class);
            return subclassMethod.equals(baseMethod);
        } catch (NoSuchMethodException e) {
            return true;
        }
    }

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

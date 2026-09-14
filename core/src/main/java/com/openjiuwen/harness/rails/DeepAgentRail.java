/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.rails;

import com.openjiuwen.core.singleagent.rail.AgentCallback;
import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.core.singleagent.rail.AgentCallbackEvent;
import com.openjiuwen.core.singleagent.rail.AgentRail;
import com.openjiuwen.core.singleagent.rail.ModelCallInputs;
import com.openjiuwen.core.singleagent.rail.ToolCallInputs;
import com.openjiuwen.harness.deep_agent.DeepAgent;

import java.lang.reflect.Method;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;

/**
 * DeepAgent rail base. Extends {@link AgentRail} like 930
 * {@code openjiuwen/harness/rails/DeepAgentRail.java}.
 *
 * <p>Subclass hooks may override {@link AgentRail}'s {@code void} methods, or
 * keep the existing {@link CallbackContext} methods. Inner ReAct only receives
 * model/tool adapters so outer invoke / task-iteration are not double-fired.</p>
 */
public abstract class DeepAgentRail extends AgentRail {

    private static final Set<String> INPUT_KEYS = Set.of(
            "tool_call", "tool_name", "tool_args", "tool_result", "tool_msg",
            "messages", "tools", "model_context", "response"
    );

    private static final Set<AgentCallbackEvent> INNER_CALLBACK_EVENTS = Set.of(
            AgentCallbackEvent.BEFORE_MODEL_CALL,
            AgentCallbackEvent.AFTER_MODEL_CALL,
            AgentCallbackEvent.ON_MODEL_EXCEPTION,
            AgentCallbackEvent.BEFORE_TOOL_CALL,
            AgentCallbackEvent.AFTER_TOOL_CALL,
            AgentCallbackEvent.ON_TOOL_EXCEPTION
    );

    private DeepAgent owner;
    private Object workspace;
    private Object sysOperation;

    protected DeepAgentRail() {
        setPriority(100);
    }

    /**
     * Default priority used when a subclass does not call {@link #setPriority(int)}.
     *
     * @return priority value
     */
    public int priority() {
        return super.getPriority();
    }

    @Override
    public int getPriority() {
        return priority();
    }

    @Override
    public void init(Object agent) {
        if (agent instanceof DeepAgent deepAgent) {
            init(deepAgent);
        }
    }

    @Override
    public void uninit(Object agent) {
        if (agent instanceof DeepAgent deepAgent) {
            uninit(deepAgent);
        }
    }

    public void init(DeepAgent agent) {
        this.owner = agent;
        if (agent == null) {
            return;
        }
        if (agent.getWorkspace() != null) {
            setWorkspace(agent.getWorkspace());
        }
        Object operation = agent.getSysOperation();
        if (operation != null) {
            setSysOperation(operation);
        }
    }

    public void uninit(DeepAgent agent) {
    }

    public Object getWorkspace() {
        return workspace;
    }

    public void setWorkspace(Object workspace) {
        this.workspace = workspace;
    }

    public Object getSysOperation() {
        return sysOperation;
    }

    public void setSysOperation(Object sysOperation) {
        this.sysOperation = sysOperation;
    }

    public void beforeInvoke(CallbackContext ctx) {
    }

    public void afterInvoke(CallbackContext ctx) {
    }

    public void beforeModelCall(CallbackContext ctx) {
    }

    public void afterModelCall(CallbackContext ctx) {
    }

    public void beforeToolCall(CallbackContext ctx) {
    }

    public void afterToolCall(CallbackContext ctx) {
    }

    public void beforeTaskIteration(CallbackContext ctx) {
    }

    public void afterTaskIteration(CallbackContext ctx) {
    }

    /**
     * Inner ReAct only gets model/tool hooks, matching Python {@code _BRIDGE_EVENTS}.
     */
    @Override
    public Map<AgentCallbackEvent, AgentCallback> getCallbacks() {
        Map<AgentCallbackEvent, AgentCallback> callbacks = new EnumMap<>(super.getCallbacks());
        addCallbackContextAdapter(callbacks, AgentCallbackEvent.BEFORE_MODEL_CALL, "beforeModelCall",
                this::beforeModelCall);
        addCallbackContextAdapter(callbacks, AgentCallbackEvent.AFTER_MODEL_CALL, "afterModelCall",
                this::afterModelCall);
        addCallbackContextAdapter(callbacks, AgentCallbackEvent.BEFORE_TOOL_CALL, "beforeToolCall",
                this::beforeToolCall);
        addCallbackContextAdapter(callbacks, AgentCallbackEvent.AFTER_TOOL_CALL, "afterToolCall",
                this::afterToolCall);
        callbacks.keySet().retainAll(INNER_CALLBACK_EVENTS);
        return callbacks;
    }

    private void addCallbackContextAdapter(Map<AgentCallbackEvent, AgentCallback> callbacks,
                                           AgentCallbackEvent event,
                                           String methodName,
                                           Consumer<CallbackContext> hook) {
        if (callbacks.containsKey(event) || !isCallbackContextOverridden(methodName)) {
            return;
        }
        callbacks.put(event, context -> forward(context, hook));
    }

    private boolean isCallbackContextOverridden(String methodName) {
        try {
            Method method = this.getClass().getMethod(methodName, CallbackContext.class);
            return method.getDeclaringClass() != DeepAgentRail.class;
        } catch (NoSuchMethodException ex) {
            return false;
        }
    }

    private CompletionStage<Void> forward(AgentCallbackContext context, Consumer<CallbackContext> hook) {
        CallbackContext callback = toCallbackContext(context);
        hook.accept(callback);
        applyCallbackContext(context, callback);
        return completed();
    }

    private CallbackContext toCallbackContext(AgentCallbackContext context) {
        Map<String, Object> values = new LinkedHashMap<>();
        if (context != null) {
            putInputs(values, context.getInputs());
            if (context.getExtra() != null) {
                values.putAll(context.getExtra());
            }
        }
        return new CallbackContext(owner, values);
    }

    private static void putInputs(Map<String, Object> values, Object inputs) {
        if (inputs instanceof Map<?, ?> map) {
            putMapInputs(values, map);
            return;
        }
        if (inputs instanceof ToolCallInputs toolInputs) {
            putToolCallInputs(values, toolInputs);
            return;
        }
        if (inputs instanceof ModelCallInputs modelInputs) {
            putModelCallInputs(values, modelInputs);
        }
    }

    private static void putMapInputs(Map<String, Object> values, Map<?, ?> map) {
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (entry.getKey() != null) {
                values.put(String.valueOf(entry.getKey()), entry.getValue());
            }
        }
    }

    private static void putToolCallInputs(Map<String, Object> values, ToolCallInputs toolInputs) {
        values.put("tool_call", toolInputs.getToolCall());
        values.put("tool_name", toolInputs.getToolName());
        values.put("tool_args", toolInputs.getToolArgs());
        values.put("tool_result", toolInputs.getToolResult());
        values.put("tool_msg", toolInputs.getToolMsg());
    }

    private static void putModelCallInputs(Map<String, Object> values, ModelCallInputs modelInputs) {
        values.put("messages", modelInputs.getMessages());
        values.put("tools", modelInputs.getTools());
        values.put("model_context", modelInputs.getModelContext());
        values.put("response", modelInputs.getResponse());
    }

    private static void applyCallbackContext(AgentCallbackContext context, CallbackContext callback) {
        if (context == null || callback == null) {
            return;
        }
        Map<String, Object> extra = ensureExtra(context);
        for (Map.Entry<String, Object> entry : callback.getValues().entrySet()) {
            if (entry.getValue() == null || INPUT_KEYS.contains(entry.getKey())) {
                continue;
            }
            extra.put(entry.getKey(), entry.getValue());
        }
        applyRejection(extra, callback);
        applyToolCallRewrites(context.getInputs(), callback);
        applyForceFinish(context, callback);
    }

    private static Map<String, Object> ensureExtra(AgentCallbackContext context) {
        Map<String, Object> extra = context.getExtra();
        if (extra != null) {
            return extra;
        }
        extra = new LinkedHashMap<>();
        context.setExtra(extra);
        return context.getExtra();
    }

    private static void applyRejection(Map<String, Object> extra, CallbackContext callback) {
        if (!callback.isRejected()) {
            return;
        }
        extra.put("_skip_tool", Boolean.TRUE);
        extra.put("rejected", Boolean.TRUE);
        if (callback.getRejectionMessage() != null) {
            extra.put("error", callback.getRejectionMessage());
        }
    }

    private static void applyToolCallRewrites(Object inputs, CallbackContext callback) {
        if (!(inputs instanceof ToolCallInputs toolInputs)) {
            return;
        }
        Object toolName = callback.get("tool_name");
        if (toolName instanceof String name) {
            toolInputs.setToolName(name);
        }
        if (callback.get("tool_args") != null) {
            toolInputs.setToolArgs(callback.get("tool_args"));
        }
        if (callback.get("tool_result") != null) {
            toolInputs.setToolResult(callback.get("tool_result"));
        }
        if (callback.get("tool_msg") != null) {
            toolInputs.setToolMsg(callback.get("tool_msg"));
        }
        if (callback.get("tool_call") != null) {
            toolInputs.setToolCall(callback.get("tool_call"));
        }
    }

    private static void applyForceFinish(AgentCallbackContext context, CallbackContext callback) {
        if (context.hasForceFinishRequest() || !Boolean.TRUE.equals(callback.get("force_finish"))) {
            return;
        }
        Object result = callback.get("force_finish_result");
        if (result instanceof Map<?, ?> map) {
            Map<String, Object> copied = new LinkedHashMap<>();
            map.forEach((key, value) -> copied.put(String.valueOf(key), value));
            context.requestForceFinish(copied);
            return;
        }
        context.requestForceFinish(Map.of());
    }
}

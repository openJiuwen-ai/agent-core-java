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

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;

/**
 * DeepAgent rail base. Extends {@link AgentRail} like Python
 * {@code openjiuwen/harness/rails/base.py} and 730.
 *
 * <p>Subclass hooks stay on {@link CallbackContext} for the existing Java
 * rails. Inner ReAct events adapt {@link AgentCallbackContext} onto those
 * hooks. Outer invoke / task-iteration still call the {@code CallbackContext}
 * methods directly so they are not double-fired on the inner agent.</p>
 */
public class DeepAgentRail extends AgentRail {

    private static final Set<String> INPUT_KEYS = Set.of(
            "tool_call", "tool_name", "tool_args", "tool_result", "tool_msg",
            "messages", "tools", "model_context", "response"
    );

    private DeepAgent owner;
    private Object workspace;
    private Object sysOperation;

    public DeepAgentRail() {
        setPriority(100);
    }

    public void init(DeepAgent agent) {
        this.owner = agent;
        if (agent == null) {
            return;
        }
        if (agent.getWorkspace() != null) {
            setWorkspace(agent.getWorkspace());
        }
        Object sysOperation = agent.getSysOperation();
        if (sysOperation != null) {
            setSysOperation(sysOperation);
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

    @Override
    public CompletionStage<Void> beforeModelCall(AgentCallbackContext context) {
        return forward(context, this::beforeModelCall);
    }

    @Override
    public CompletionStage<Void> afterModelCall(AgentCallbackContext context) {
        return forward(context, this::afterModelCall);
    }

    @Override
    public CompletionStage<Void> beforeToolCall(AgentCallbackContext context) {
        return forward(context, this::beforeToolCall);
    }

    @Override
    public CompletionStage<Void> afterToolCall(AgentCallbackContext context) {
        return forward(context, this::afterToolCall);
    }

    /**
     * Inner ReAct only gets model/tool hooks, matching Python {@code _BRIDGE_EVENTS}.
     */
    @Override
    public Map<AgentCallbackEvent, AgentCallback> getCallbacks() {
        Map<AgentCallbackEvent, AgentCallback> callbacks = new EnumMap<>(AgentCallbackEvent.class);
        callbacks.put(AgentCallbackEvent.BEFORE_MODEL_CALL, this::beforeModelCall);
        callbacks.put(AgentCallbackEvent.AFTER_MODEL_CALL, this::afterModelCall);
        callbacks.put(AgentCallbackEvent.ON_MODEL_EXCEPTION, this::onModelException);
        callbacks.put(AgentCallbackEvent.BEFORE_TOOL_CALL, this::beforeToolCall);
        callbacks.put(AgentCallbackEvent.AFTER_TOOL_CALL, this::afterToolCall);
        callbacks.put(AgentCallbackEvent.ON_TOOL_EXCEPTION, this::onToolException);
        return callbacks;
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
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() != null) {
                    values.put(String.valueOf(entry.getKey()), entry.getValue());
                }
            }
            return;
        }
        if (inputs instanceof ToolCallInputs toolInputs) {
            values.put("tool_call", toolInputs.getToolCall());
            values.put("tool_name", toolInputs.getToolName());
            values.put("tool_args", toolInputs.getToolArgs());
            values.put("tool_result", toolInputs.getToolResult());
            values.put("tool_msg", toolInputs.getToolMsg());
            return;
        }
        if (inputs instanceof ModelCallInputs modelInputs) {
            values.put("messages", modelInputs.getMessages());
            values.put("tools", modelInputs.getTools());
            values.put("model_context", modelInputs.getModelContext());
            values.put("response", modelInputs.getResponse());
        }
    }

    private static void applyCallbackContext(AgentCallbackContext context, CallbackContext callback) {
        if (context == null || callback == null) {
            return;
        }
        Map<String, Object> extra = context.getExtra();
        if (extra == null) {
            extra = new LinkedHashMap<>();
            context.setExtra(extra);
            extra = context.getExtra();
        }
        for (Map.Entry<String, Object> entry : callback.getValues().entrySet()) {
            if (entry.getValue() == null || INPUT_KEYS.contains(entry.getKey())) {
                continue;
            }
            extra.put(entry.getKey(), entry.getValue());
        }
        if (callback.isRejected()) {
            extra.put("_skip_tool", Boolean.TRUE);
            extra.put("rejected", Boolean.TRUE);
            if (callback.getRejectionMessage() != null) {
                extra.put("error", callback.getRejectionMessage());
            }
        }
        applyToolCallRewrites(context.getInputs(), callback);
        applyForceFinish(context, callback);
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

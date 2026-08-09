/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.rails;

import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.core.singleagent.rail.AgentRail;
import com.openjiuwen.core.singleagent.rail.ModelCallInputs;
import com.openjiuwen.core.singleagent.rail.ToolCallInputs;
import com.openjiuwen.harness.deep_agent.DeepAgent;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;

/**
 * Forwards ReAct {@code AgentRail} model/tool hooks onto a {@link DeepAgentRail}.
 *
 * <p>Python {@code DeepAgentRail} extends {@code AgentRail}, so {@code before_model_call}
 * is registered on the agent automatically. Java {@link DeepAgentRail} is a separate type;
 * {@link com.openjiuwen.harness.deep_agent.DeepAgent#invokeWithLifecycle} only fires
 * {@code beforeInvoke}/{@code afterInvoke}. This bridge restores the Python hook timing
 * without double-firing invoke hooks.</p>
 */
public final class DeepAgentRailAgentBridge extends AgentRail {

    private static final Set<String> INPUT_KEYS = Set.of(
            "tool_call", "tool_name", "tool_args", "tool_result", "tool_msg",
            "messages", "tools", "model_context", "response"
    );

    private final DeepAgent owner;
    private final DeepAgentRail delegate;

    public DeepAgentRailAgentBridge(DeepAgent owner, DeepAgentRail delegate) {
        this.owner = owner;
        this.delegate = delegate;
        if (delegate != null) {
            setPriority(delegate.getPriority());
        }
    }

    @Override
    public CompletionStage<Void> beforeModelCall(AgentCallbackContext context) {
        return forward(context, ctx -> delegate.beforeModelCall(ctx));
    }

    @Override
    public CompletionStage<Void> afterModelCall(AgentCallbackContext context) {
        return forward(context, ctx -> delegate.afterModelCall(ctx));
    }

    @Override
    public CompletionStage<Void> beforeToolCall(AgentCallbackContext context) {
        return forward(context, ctx -> delegate.beforeToolCall(ctx));
    }

    @Override
    public CompletionStage<Void> afterToolCall(AgentCallbackContext context) {
        return forward(context, ctx -> delegate.afterToolCall(ctx));
    }

    private CompletionStage<Void> forward(AgentCallbackContext context, Consumer<CallbackContext> hook) {
        if (delegate != null) {
            CallbackContext callback = toCallbackContext(context);
            hook.accept(callback);
            applyCallbackContext(context, callback);
        }
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

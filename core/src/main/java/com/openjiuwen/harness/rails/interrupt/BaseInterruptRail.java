/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.rails.interrupt;

import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.foundation.llm.schema.ToolMessage;
import com.openjiuwen.core.singleagent.interrupt.InterruptRequest;
import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.core.singleagent.rail.ToolCallInputs;
import com.openjiuwen.harness.rails.CallbackContext;
import com.openjiuwen.harness.rails.DeepAgentRail;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Base interrupt rail for tool-call confirmation flows.
 *
 * <p>Mirrors Python's {@code BaseInterruptRail} in
 * {@code openjiuwen/harness/rails/interrupt/interrupt_base.py}.</p>
 */
public class BaseInterruptRail extends DeepAgentRail {

    private final Map<String, Object> pendingInterrupts = new LinkedHashMap<>();
    private final Set<String> toolNames = new LinkedHashSet<>();

    public BaseInterruptRail() {
    }

    public BaseInterruptRail(Collection<String> toolNames) {
        addTools(toolNames);
    }

    /**
     * Return the stable identity used to persist and replay this rail's settled decisions.
     * Defaults to the concrete class name; override it when multiple instances of the same rail
     * class are registered on one agent, or when a changed policy must invalidate previous decisions.
     *
     * @return stable identity of this rail
     * @since 0.1.16
     */
    protected String railId() {
        return getClass().getName();
    }

    @Override
    public void beforeToolCall(AgentCallbackContext context) {
        if (!(context.getInputs() instanceof ToolCallInputs inputs)
                || !(inputs.getToolCall() instanceof ToolCall toolCall)
                || !toolNames.contains(toolCall.getName())) {
            return;
        }
        InterruptRailSupport.evaluate(context, toolCall, railId(),
                () -> resolveInterrupt(context, toolCall, getUserInput(context, toolCall)), true);
    }

    /**
     * Pause a native tool call. Subclasses override this method to resolve user responses.
     *
     * @param context current callback context
     * @param toolCall call being evaluated
     * @param userInput resumed response, or null
     * @return decision to approve, reject, or pause this call
     * @since 0.1.17
     */
    protected InterruptDecision resolveInterrupt(AgentCallbackContext context, ToolCall toolCall, Object userInput) {
        return interrupt(new InterruptRequest("Please approve or reject?", Map.of(), toolCall.getName()));
    }

    /**
     * Read the native resume response for a tool call.
     *
     * @param context current callback context
     * @param toolCall call being evaluated
     * @return response for this call, or null
     * @since 0.1.17
     */
    protected Object getUserInput(AgentCallbackContext context, ToolCall toolCall) {
        return InterruptRailSupport.userInput(context, toolCall, railId())
                .orElse(null);
    }

    /**
     * Approve without replacing tool arguments.
     *
     * @return approval decision
     * @since 0.1.17
     */
    public ApproveResult approve() {
        return new ApproveResult();
    }

    /**
     * Approve with replacement tool arguments.
     *
     * @param newArgs replacement arguments, or null to retain them
     * @return approval decision
     * @since 0.1.17
     */
    public ApproveResult approve(String newArgs) {
        return new ApproveResult(newArgs);
    }

    /**
     * Reject and surface a tool result.
     *
     * @param toolResult rejection result
     * @return rejection decision
     * @since 0.1.17
     */
    public RejectResult reject(Object toolResult) {
        return new RejectResult(toolResult);
    }

    /**
     * Reject and surface a tool result and message.
     *
     * @param toolResult rejection result
     * @param toolMessage optional rejection message
     * @return rejection decision
     * @since 0.1.17
     */
    public RejectResult reject(Object toolResult, ToolMessage toolMessage) {
        return new RejectResult(toolResult, toolMessage);
    }

    /**
     * Pause this call until a user response is supplied.
     *
     * @param request confirmation request
     * @return pending decision
     * @since 0.1.17
     */
    public InterruptResult interrupt(InterruptRequest request) {
        return new InterruptResult(request);
    }

    @Override
    public void beforeToolCall(CallbackContext ctx) {
        if (ctx == null) {
            return;
        }
        Object toolName = ctx.get("tool_name");
        if (toolName != null && toolNames.contains(String.valueOf(toolName))) {
            ctx.put("interrupt_required", true);
        }
        if (Boolean.TRUE.equals(ctx.get("interrupt_required"))) {
            pendingInterrupts.put(String.valueOf(ctx.get("tool_name")), new LinkedHashMap<>(ctx.getValues()));
        }
    }

    public void addTool(String toolName) {
        if (toolName != null && !toolName.isBlank()) {
            toolNames.add(toolName);
        }
    }

    public void addTools(Collection<String> names) {
        if (names == null) {
            return;
        }
        for (String name : names) {
            addTool(name);
        }
    }

    public void addPolicy(String toolName, Object ignoredPolicy) {
        addTool(toolName);
    }

    public Set<String> getTools() {
        return new LinkedHashSet<>(toolNames);
    }

    public Map<String, Object> getPendingInterrupts() {
        return new LinkedHashMap<>(pendingInterrupts);
    }

    public void clearPendingInterrupts() {
        pendingInterrupts.clear();
    }
}

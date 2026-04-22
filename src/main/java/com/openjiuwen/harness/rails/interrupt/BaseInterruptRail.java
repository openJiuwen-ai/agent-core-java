/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.rails.interrupt;

import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.foundation.llm.schema.ToolMessage;
import com.openjiuwen.core.session.interaction.InteractiveInput;
import com.openjiuwen.core.singleagent.interrupt.InterruptRequest;
import com.openjiuwen.core.singleagent.interrupt.ToolInterruptException;
import com.openjiuwen.core.singleagent.interrupt.ToolInterruptionState;
import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.core.singleagent.rail.AgentRail;
import com.openjiuwen.core.singleagent.rail.ToolCallInputs;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Harness-level base rail for interrupt and resume handling.
 *
 * Mirrors Python's openjiuwen.harness.rails.interrupt.interrupt_base.
 *
 * @since 0.1.7
 */
public abstract class BaseInterruptRail extends AgentRail {
    private final Set<String> toolNames = new LinkedHashSet<String>();

    /**
     * Create an interrupt rail for a set of tool names.
     *
     * @param toolNames intercepted tool names
     */
    protected BaseInterruptRail(Iterable<String> toolNames) {
        if (toolNames != null) {
            for (String toolName : toolNames) {
                if (toolName != null && !toolName.isEmpty()) {
                    this.toolNames.add(toolName);
                }
            }
        }
        setPriority(90);
    }

    /**
     * Approve tool execution without overriding arguments.
     *
     * @return approve decision
     */
    public ApproveResult approve() {
        return new ApproveResult(null);
    }

    /**
     * Approve tool execution and override the tool arguments.
     *
     * @param newArgs rewritten tool arguments
     * @return approve decision
     */
    public ApproveResult approve(String newArgs) {
        return new ApproveResult(newArgs);
    }

    /**
     * Reject tool execution with a synthetic tool result.
     *
     * @param toolResult synthetic tool result
     * @return reject decision
     */
    public RejectResult reject(Object toolResult) {
        return new RejectResult(toolResult, null);
    }

    /**
     * Reject tool execution with a synthetic tool result and message.
     *
     * @param toolResult synthetic tool result
     * @param toolMessage synthetic tool message
     * @return reject decision
     */
    public RejectResult reject(Object toolResult, ToolMessage toolMessage) {
        return new RejectResult(toolResult, toolMessage);
    }

    /**
     * Interrupt tool execution and wait for user input.
     *
     * @param request interruption payload
     * @return interrupt decision
     */
    public InterruptResult interrupt(InterruptRequest request) {
        return new InterruptResult(request);
    }

    /**
     * Register a tool name intercepted by this rail.
     *
     * @param toolName tool name
     */
    public void addTool(String toolName) {
        if (toolName != null && !toolName.isEmpty()) {
            toolNames.add(toolName);
        }
    }

    /**
     * Register multiple tool names intercepted by this rail.
     *
     * @param toolNames tool names
     */
    public void addTools(Iterable<String> toolNames) {
        if (toolNames == null) {
            return;
        }
        for (String toolName : toolNames) {
            addTool(toolName);
        }
    }

    /**
     * Backward-compatible alias of {@link #addTool(String)}.
     *
     * @param toolName tool name
     * @param ignoredPolicy ignored compatibility parameter
     */
    public void addPolicy(String toolName, Object ignoredPolicy) {
        addTool(toolName);
    }

    /**
     * Return all intercepted tool names.
     *
     * @return intercepted tool names
     */
    public Set<String> getToolNames() {
        return new LinkedHashSet<String>(toolNames);
    }

    /**
     * Intercept tool execution before the tool is invoked.
     *
     * @param ctx callback context
     */
    @Override
    public void beforeToolCall(AgentCallbackContext ctx) {
        if (!(ctx.getInputs() instanceof ToolCallInputs)) {
            return;
        }
        ToolCallInputs inputs = (ToolCallInputs) ctx.getInputs();
        String toolName = inputs.getToolName();
        if (!toolNames.contains(toolName)) {
            return;
        }

        ToolCall toolCall = inputs.getToolCall();
        String toolCallId = toolCall != null ? toolCall.getId() : "";
        Object userInput = getUserInput(ctx, toolCallId);
        InterruptDecision decision = resolveInterrupt(ctx, toolCall, userInput);
        applyDecision(ctx, toolCall, decision);
    }

    /**
     * Resolve the decision for the current tool invocation.
     *
     * @param ctx callback context
     * @param toolCall current tool call
     * @param userInput resume input, when present
     * @return rail decision
     */
    protected abstract InterruptDecision resolveInterrupt(
            AgentCallbackContext ctx,
            ToolCall toolCall,
            Object userInput
    );

    /**
     * Extract resume input for the current tool call from callback context.
     *
     * @param ctx callback context
     * @param toolCallId current tool call id
     * @return matched user input or null-equivalent object
     */
    protected Object getUserInput(AgentCallbackContext ctx, String toolCallId) {
        Object rawInput = ctx.getExtra().get(ToolInterruptionState.RESUME_USER_INPUT_KEY);
        Object resolvedInput = rawInput;
        if (rawInput == null) {
            return resolvedInput;
        }
        if (rawInput instanceof InteractiveInput) {
            InteractiveInput interactiveInput = (InteractiveInput) rawInput;
            Map<String, Object> userInputs = interactiveInput.getUserInputs();
            if (toolCallId != null && !toolCallId.isEmpty() && userInputs.containsKey(toolCallId)) {
                return userInputs.get(toolCallId);
            }
            return interactiveInput.getRawInputs();
        }
        if (rawInput instanceof Map<?, ?>) {
            Map<?, ?> map = (Map<?, ?>) rawInput;
            if (toolCallId != null && !toolCallId.isEmpty() && map.containsKey(toolCallId)) {
                return map.get(toolCallId);
            }
            return rawInput;
        }
        return rawInput;
    }

    private void applyDecision(AgentCallbackContext ctx, ToolCall toolCall, InterruptDecision decision) {
        ToolCallInputs inputs = null;
        if (ctx.getInputs() instanceof ToolCallInputs) {
            inputs = (ToolCallInputs) ctx.getInputs();
        }
        if (inputs == null) {
            return;
        }
        if (decision instanceof ApproveResult) {
            ApproveResult approveResult = (ApproveResult) decision;
            if (approveResult.getNewArgs() != null) {
                inputs.setToolArgs(approveResult.getNewArgs());
            }
            return;
        }
        if (decision instanceof RejectResult) {
            RejectResult rejectResult = (RejectResult) decision;
            ctx.getExtra().put("_skip_tool", Boolean.TRUE);
            inputs.setToolResult(rejectResult.getToolResult());
            ToolMessage toolMessage = rejectResult.getToolMessage();
            if (toolMessage == null) {
                String toolCallId = toolCall != null ? toolCall.getId() : "";
                toolMessage = ToolMessage.builder()
                        .content(String.valueOf(rejectResult.getToolResult()))
                        .toolCallId(toolCallId)
                        .build();
            }
            inputs.setToolMsg(toolMessage);
            return;
        }
        if (decision instanceof InterruptResult) {
            InterruptResult interruptResult = (InterruptResult) decision;
            throw new ToolInterruptException(interruptResult.getRequest(), toolCall);
        }
    }
}

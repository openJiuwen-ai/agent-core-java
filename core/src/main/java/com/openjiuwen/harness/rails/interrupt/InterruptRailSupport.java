/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.rails.interrupt;

import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.foundation.llm.schema.ToolMessage;
import com.openjiuwen.core.session.interaction.InteractiveInput;
import com.openjiuwen.core.singleagent.interrupt.InterruptConstants;
import com.openjiuwen.core.singleagent.interrupt.ToolInterruptException;
import com.openjiuwen.core.singleagent.interrupt.ToolInterruptionState.SettledDecision;
import com.openjiuwen.core.singleagent.interrupt.ToolInterruptionState;
import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.core.singleagent.rail.AgentRail;
import com.openjiuwen.core.singleagent.rail.ToolCallInputs;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Shared native tool interruption and terminal-decision replay for interrupt and security rails.
 *
 * @since 2026-10-10
 */
public final class InterruptRailSupport {
    private InterruptRailSupport() {
    }

    /**
     * Identify a registered rail independently of other instances of its class.
     *
     * @param context current callback context
     * @param rail evaluating rail
     * @return stable registration identity, or class name for a direct hook invocation
     * @since 0.1.17
     */
    public static String railId(AgentCallbackContext context, AgentRail rail) {
        return context.getAgent() == null ? rail.getClass().getName()
                : context.getAgent().getAgentCallbackManager().getRailIdentity(rail);
    }

    /**
     * Resolve a rail once per tool call and replay its terminal decision after a later interruption.
     *
     * @param context current callback context
     * @param toolCall current tool call
     * @param railId stable identity of the evaluating rail
     * @param resolver evaluates a decision when none has been settled
     * @since 0.1.17
     */
    public static void evaluate(AgentCallbackContext context, ToolCall toolCall, String railId,
                                Supplier<InterruptDecision> resolver) {
        Map<String, SettledDecision> settled = ToolInterruptionState.railState(context).getRailSettledDecisions();
        String key = toolCall.getId() + "\u0000" + railId;
        SettledDecision decision = settled.get(key);
        if (decision != null) {
            applySettled(context, toolCall, decision);
            return;
        }
        InterruptDecision resolved = resolver.get();
        if (resolved instanceof InterruptResult pending) {
            ToolInterruptionState.railState(context).getPendingRailIds().put(toolCall.getId(), railId);
            throw new ToolInterruptException(pending.request(), toolCall);
        }
        if (resolved instanceof RejectResult rejected) {
            decision = new SettledDecision(false, null, rejected.toolResult(), rejected.toolMessage());
        } else {
            String args = null;
            if (resolved instanceof ApproveResult approved) {
                args = approved.newArgs();
            }
            decision = new SettledDecision(true, args, null, null);
        }
        settled.put(key, decision);
        applySettled(context, toolCall, decision);
    }

    /**
     * Read the native resume input for this tool call.
     *
     * @param context current callback context
     * @param toolCall tool whose approval is being resolved
     * @param railId identity of the evaluating rail
     * @return input for this call, empty while awaiting a response
     * @since 0.1.17
     */
    public static Optional<Object> userInput(AgentCallbackContext context, ToolCall toolCall, String railId) {
        String pending = ToolInterruptionState.railState(context).getPendingRailIds().get(toolCall.getId());
        if (pending != null && !pending.equals(railId)) {
            return Optional.empty();
        }
        Object input = context.getExtra().get(InterruptConstants.RESUME_USER_INPUT_KEY);
        if (input instanceof InteractiveInput interactive) {
            return Optional.ofNullable(interactive.getUserInputs().get(toolCall.getId()));
        }
        if (input instanceof Map<?, ?> map && map.containsKey(toolCall.getId())) {
            return Optional.ofNullable(map.get(toolCall.getId()));
        }
        return Optional.ofNullable(input);
    }

    /**
     * Read the session's native auto-confirm configuration.
     *
     * @param context current callback context
     * @return copied configuration, empty when absent
     * @since 0.1.17
     */
    public static Map<String, Object> autoConfirmConfig(AgentCallbackContext context) {
        Map<String, Object> config = new LinkedHashMap<>();
        if (context.getSession() == null) {
            return config;
        }
        Object saved = context.getSession().getState(InterruptConstants.INTERRUPT_AUTO_CONFIRM_KEY);
        if (saved instanceof Map<?, ?> map) {
            map.forEach((key, value) -> config.put(String.valueOf(key), value));
        }
        return config;
    }

    private static void applySettled(AgentCallbackContext context, ToolCall toolCall, SettledDecision decision) {
        if (!(context.getInputs() instanceof ToolCallInputs inputs)) {
            return;
        }
        if (decision.isApproved()) {
            if (decision.newArgs() != null) {
                toolCall.setArguments(decision.newArgs());
                inputs.setToolArgs(decision.newArgs());
            }
            return;
        }
        context.getExtra().put("_skip_tool", true);
        inputs.setToolResult(decision.toolResult());
        ToolMessage message = decision.toolMessage();
        if (message == null) {
            message = new ToolMessage(String.valueOf(decision.toolResult()), toolCall.getId(), toolCall.getName());
        }
        inputs.setToolMsg(message);
    }
}

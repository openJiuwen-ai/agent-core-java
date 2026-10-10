/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent.interrupt;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.openjiuwen.core.foundation.llm.schema.ToolMessage;
import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;

import java.io.Serial;
import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tool interruption state for resume support.
 *
 * <p>Mirrors Python's {@code ToolInterruptionState} in
 * {@code openjiuwen/core/single_agent/interrupt/state.py}.</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ToolInterruptionState extends BaseInterruptionState {
    private static final String RAIL_STATE_KEY = "_tool_rail_interruption_state";

    @Serial
    private static final long serialVersionUID = 1L;

    @JsonProperty("interrupted_tools")
    private Map<String, ToolInterruptEntry> interruptedTools = new LinkedHashMap<>();

    @JsonProperty("auto_confirm_mapping")
    private Map<String, String> autoConfirmMapping = new LinkedHashMap<>();

    @JsonProperty("rail_settled_decisions")
    private Map<String, SettledDecision> railSettledDecisions = new ConcurrentHashMap<>();

    @JsonProperty("pending_rail_ids")
    private Map<String, String> pendingRailIds = new ConcurrentHashMap<>();

    /**
     * Return the rail awaiting a reply for each interrupted call.
     *
     * @return pending rail identities
     * @since 0.1.17
     */
    public Map<String, String> getPendingRailIds() {
        if (pendingRailIds == null) {
            pendingRailIds = new ConcurrentHashMap<>();
        }
        return pendingRailIds;
    }

    /**
     * Copy rail decisions and pending identities from an invocation or checkpoint.
     *
     * @param state source rail state
     * @since 0.1.17
     */
    public void copyRailStateFrom(ToolInterruptionState state) {
        setRailSettledDecisions(state.getRailSettledDecisions());
        pendingRailIds = new ConcurrentHashMap<>(state.getPendingRailIds());
    }

    /**
     * Obtain the shared rail state for the current invocation or resumed checkpoint.
     *
     * @param context current invocation context
     * @return state shared by this invocation's tool callbacks
     * @since 0.1.17
     */
    public static ToolInterruptionState railState(AgentCallbackContext context) {
        Object current = context.getExtra().get(RAIL_STATE_KEY);
        if (current instanceof ToolInterruptionState state) {
            return state;
        }
        Object saved = null;
        if (context.getSession() != null) {
            saved = context.getSession().getState(InterruptConstants.INTERRUPTION_KEY);
        }
        if (saved instanceof ToolInterruptionState interruptionState) {
            context.getExtra().put(RAIL_STATE_KEY, interruptionState);
            return interruptionState;
        }
        ToolInterruptionState state = new ToolInterruptionState();
        context.getExtra().put(RAIL_STATE_KEY, state);
        return state;
    }

    /**
     * Return terminal rail decisions; pending approvals are never stored here.
     *
     * @return thread-safe decision map
     * @since 0.1.17
     */
    public Map<String, SettledDecision> getRailSettledDecisions() {
        if (railSettledDecisions == null) {
            railSettledDecisions = new ConcurrentHashMap<>();
        }
        return railSettledDecisions;
    }

    /**
     * Copy terminal decisions into a checkpoint.
     *
     * @param decisions decisions collected before a later rail interrupted
     * @since 0.1.17
     */
    public void setRailSettledDecisions(Map<String, SettledDecision> decisions) {
        railSettledDecisions = new ConcurrentHashMap<>();
        if (decisions != null) {
            railSettledDecisions.putAll(decisions);
        }
    }

    public Map<String, ToolInterruptEntry> getInterruptedTools() {
        return interruptedTools;
    }

    public void setInterruptedTools(Map<String, ToolInterruptEntry> interruptedTools) {
        this.interruptedTools = interruptedTools == null
                ? new LinkedHashMap<>()
                : new LinkedHashMap<>(interruptedTools);
    }

    public Map<String, String> getAutoConfirmMapping() {
        return autoConfirmMapping;
    }

    public void setAutoConfirmMapping(Map<String, String> autoConfirmMapping) {
        this.autoConfirmMapping = autoConfirmMapping == null
                ? new LinkedHashMap<>()
                : new LinkedHashMap<>(autoConfirmMapping);
    }

    /**
     * A terminal approval or rejection to replay after another rail interrupts the same call.
     *
     * @param isApproved whether this rail approved the call
     * @param newArgs replacement arguments, if present
     * @param toolResult rejection result, if present
     * @param toolMessage rejection message, if present
     * @since 0.1.17
     */
    public record SettledDecision(boolean isApproved, String newArgs, Object toolResult, ToolMessage toolMessage)
            implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
    }
}

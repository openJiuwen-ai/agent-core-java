/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentteams.agent.coordination.handlers;

import com.openjiuwen.agentteams.agent.coordination.DispatcherHost;
import com.openjiuwen.agentteams.agent.coordination.InnerEventMessage;
import com.openjiuwen.agentteams.agent.coordination.InnerEventType;
import com.openjiuwen.agentteams.agent.coordination.PollController;
import com.openjiuwen.agentteams.agent.coordination.TeamAgentBlueprint;
import com.openjiuwen.agentteams.agent.coordination.TeamInfra;
import com.openjiuwen.agentteams.schema.events.CoordinationEvent;
import com.openjiuwen.agentteams.schema.events.EventMessage;
import com.openjiuwen.agentteams.schema.events.TeamEvent;
import com.openjiuwen.agentteams.schema.team.TeamRole;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.session.interaction.InteractiveInput;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Handle USER_INPUT / STANDBY / CLEANED / TOOL_APPROVAL_RESULT / TASK_PLAN_RESPONSE.
 *
 * <p>Mirrors Python {@code handlers/agent_lifecycle.py}. These events drive the
 * local agent's lifecycle: bootstrap user input, pause polling on team standby,
 * tear down on team cleanup (non-leader only), resume HITL interrupt on
 * tool-approval result, resume HITL interrupt on task-plan decision. Stateless
 * — defers to {@link DispatcherHost} for all behavior.
 *
 * @since 2026/7/9
 */
public class AgentLifecycleHandler extends BaseCoordinationHandler {
    /**
     * Construct and register event bindings.
     *
     * @param host the owning TeamAgent
     * @param blueprint static config
     * @param infra per-process services
     * @param pollCtrl poll control surface
     */
    public AgentLifecycleHandler(DispatcherHost host, TeamAgentBlueprint blueprint,
                                 TeamInfra infra, PollController pollCtrl) {
        super(host, blueprint, infra, pollCtrl);
        callbacks.put(InnerEventType.USER_INPUT.getValue(), this::onUserInput);
        callbacks.put(TeamEvent.STANDBY, this::onStandby);
        callbacks.put(TeamEvent.CLEANED, this::onCleaned);
        callbacks.put(TeamEvent.TOOL_APPROVAL_RESULT, this::onToolApprovalResult);
        callbacks.put(TeamEvent.TASK_PLAN_RESPONSE, this::onTaskPlanResponse);
    }

    /**
     * Forward {@code coordination bootstrap} user input to the agent.
     *
     * <p>Routing decisions ({@code @<member> body} etc.) happen at the runtime
     * dispatch boundary, not here. By the time input reaches the inner event
     * bus it is already aimed at this agent — this handler just delivers it.
     *
     * @param event the coordination event carrying user input
     */
    public void onUserInput(CoordinationEvent event) {
        String content = "";
        if (event instanceof InnerEventMessage inner) {
            Object c = inner.getPayload() != null
                    ? inner.getPayload().getOrDefault("content", "") : "";
            content = c != null ? String.valueOf(c) : "";
        }
        Loggers.AGENT.info("user_input -> deliver_input");
        // USER_INPUT reflects explicit user intent: even when the team is
        // terminated (isTeamTerminated=true), fresh user input signals intent
        // to start a new team or continue the conversation, so it must be
        // forwarded to the leader LLM. Stale POLL_MAILBOX / TASK_BOARD
        // events are still filtered out by the deliverInput guard.
        // Without this pass-through, post-clean_team user input would be
        // silently dropped (no feedback = stuck).
        if (round instanceof com.openjiuwen.agentteams.agent.TeamAgent teamAgent
                && teamAgent.getStreamController() != null
                && teamAgent.getStreamController().isTeamTerminated()) {
            Loggers.AGENT.info("onUserInput: team terminated, but USER_INPUT is user intent"
                    + " — resetting latch and delivering for member={}",
                    teamAgent.resolveLocalMemberName());
            teamAgent.getStreamController().resetTeamTerminated();
        }
        round.deliverInput(content);
    }

    /**
     * Pause periodic polling on TEAM_STANDBY.
     *
     * @param event the team standby event
     */
    public void onStandby(CoordinationEvent event) {
        String memberName = blueprint.memberName().orElse(null);
        Loggers.AGENT.info("[{}] received TEAM_STANDBY, pausing polls", memberName);
        poll.pausePolls();
    }

    /**
     * Tear down on TEAM_CLEANED for non-leader members.
     *
     * <p>The leader must never {@code shutdownSelf} from its own CLEANED event:
     * persistent leaders have to survive {@code clean_team} to accept the next
     * interaction. The leader branch is a no-op; teammates and human-agent
     * avatars abandon their loop here.
     *
     * @param event the team cleaned event
     */
    public void onCleaned(CoordinationEvent event) {
        String memberName = blueprint.memberName().orElse(null);
        if (blueprint.role().orElse(null) == TeamRole.LEADER) {
            Loggers.AGENT.debug("[{}] ignoring TEAM_CLEANED on leader path", memberName);
            return;
        }
        Loggers.AGENT.info("[{}] received TEAM_CLEANED, shutting down coordination", memberName);
        lifecycle.shutdownSelf();
    }

    /**
     * Resume a teammate HITL interrupt from a structured approval event.
     *
     * @param event the tool approval result event
     */
    public void onToolApprovalResult(CoordinationEvent event) {
        if (!(event instanceof EventMessage msg)) {
            return;
        }
        String memberName = blueprint.memberName().orElse(null);
        Map<String, Object> payload = msg.getPayload() != null ? msg.getPayload() : Map.of();
        String targetId = str(payload, "member_name");
        if (targetId == null || !targetId.equals(memberName)) {
            return;
        }
        String toolCallId = str(payload, "tool_call_id");
        if (toolCallId == null || toolCallId.isBlank()) {
            return;
        }
        Map<String, Object> decision = new LinkedHashMap<>();
        decision.put("approved", bool(payload, "approved"));
        decision.put("feedback", str(payload, "feedback"));
        decision.put("auto_confirm", bool(payload, "auto_confirm"));
        InteractiveInput input = new InteractiveInput();
        input.update(toolCallId, decision);
        Loggers.AGENT.debug(
                "[{}] received tool approval result for tool_call_id={}, approved={}",
                memberName, toolCallId, payload.get("approved"));
        round.resumeInterrupt(input);
    }

    /**
     * Handle a leader decision for a submitted member plan.
     *
     * @param event the task plan response event
     */
    public void onTaskPlanResponse(CoordinationEvent event) {
        if (!(event instanceof EventMessage msg)) {
            return;
        }
        String memberName = blueprint.memberName().orElse(null);
        Map<String, Object> payload = msg.getPayload() != null ? msg.getPayload() : Map.of();
        String targetId = str(payload, "member_name");
        if (targetId == null || !targetId.equals(memberName)) {
            return;
        }
        String toolCallId = str(payload, "tool_call_id");
        if (toolCallId == null || toolCallId.isBlank()) {
            return;
        }
        Map<String, Object> decision = new LinkedHashMap<>();
        decision.put("approved", bool(payload, "approved"));
        decision.put("feedback", str(payload, "feedback"));
        Object planId = payload.getOrDefault("plan_id", "");
        decision.put("plan_id", planId != null ? String.valueOf(planId) : "");
        InteractiveInput input = new InteractiveInput();
        input.update(toolCallId, decision);
        Loggers.AGENT.debug(
                "[{}] received task plan response for tool_call_id={}, approved={}",
                memberName, toolCallId, payload.get("approved"));
        round.resumeInterrupt(input);
    }
}

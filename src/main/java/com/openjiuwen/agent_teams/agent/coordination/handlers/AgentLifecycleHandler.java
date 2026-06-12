/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.agent.coordination.handlers;

import com.openjiuwen.agent_teams.agent.AgentConfigurator.TeamInfra;
import com.openjiuwen.agent_teams.agent.AgentConfigurator.TeamRole;
import com.openjiuwen.agent_teams.agent.TeamAgentBlueprint;
import com.openjiuwen.agent_teams.agent.coordination.EventDispatcher.CoordinationEvent;
import com.openjiuwen.agent_teams.agent.coordination.EventDispatcher.DispatcherHost;
import com.openjiuwen.agent_teams.agent.coordination.EventDispatcher.EventCallback;
import com.openjiuwen.agent_teams.agent.coordination.EventDispatcher.InnerEventMessage;
import com.openjiuwen.agent_teams.agent.coordination.EventDispatcher.InnerEventType;
import com.openjiuwen.agent_teams.agent.coordination.EventDispatcher.PollController;
import com.openjiuwen.agent_teams.agent.coordination.EventDispatcher.TransportEvent;
import com.openjiuwen.agent_teams.schema.BaseEventMessage;
import com.openjiuwen.agent_teams.schema.TaskPlanResponseEvent;
import com.openjiuwen.agent_teams.schema.TeamEvent;
import com.openjiuwen.agent_teams.schema.ToolApprovalResultEvent;
import com.openjiuwen.agent_teams.schema.events.EventMessage;
import com.openjiuwen.core.session.interaction.InteractiveInput;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handles user input, standby, cleanup, and approval response lifecycle events.
 *
 * <p>Mirrors Python's {@code AgentLifecycleHandler} in
 * {@code openjiuwen/agent_teams/agent/coordination/handlers/agent_lifecycle.py}.</p>
 */
public class AgentLifecycleHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(AgentLifecycleHandler.class);

    private final DispatcherHost host;
    private final TeamAgentBlueprint blueprint;
    private final TeamInfra infra;
    private final PollController pollController;

    public AgentLifecycleHandler(
            DispatcherHost host,
            TeamAgentBlueprint blueprint,
            TeamInfra infra,
            PollController pollController
    ) {
        this.host = Objects.requireNonNull(host, "host");
        this.blueprint = Objects.requireNonNull(blueprint, "blueprint");
        this.infra = Objects.requireNonNull(infra, "infra");
        this.pollController = Objects.requireNonNull(pollController, "pollController");
    }

    public Map<String, EventCallback> getCallbacks() {
        Map<String, EventCallback> callbacks = new LinkedHashMap<>();
        callbacks.put(InnerEventType.USER_INPUT.value(), this::onUserInput);
        callbacks.put(TeamEvent.STANDBY, this::onStandby);
        callbacks.put(TeamEvent.CLEANED, this::onCleaned);
        callbacks.put(TeamEvent.TOOL_APPROVAL_RESULT, this::onToolApprovalResult);
        callbacks.put(TeamEvent.TASK_PLAN_RESPONSE, this::onTaskPlanResponse);
        return callbacks;
    }

    public CompletionStage<Void> onUserInput(CoordinationEvent event) {
        InnerEventMessage innerEvent = (InnerEventMessage) event;
        Object content = innerEvent.getPayload().getOrDefault("content", "");
        LOGGER.info("user_input -> deliver_input");
        return host.deliverInput(content, true);
    }

    public CompletionStage<Void> onStandby(CoordinationEvent event) {
        LOGGER.info("[{}] received TEAM_STANDBY, pausing polls", blueprint.getMemberName());
        return pollController.pausePolls();
    }

    public CompletionStage<Void> onCleaned(CoordinationEvent event) {
        String memberName = blueprint.getMemberName();
        if (blueprint.getRole() == TeamRole.LEADER) {
            LOGGER.debug("[{}] ignoring TEAM_CLEANED on leader path", memberName);
            return CompletableFuture.completedFuture(null);
        }
        LOGGER.info("[{}] received TEAM_CLEANED, shutting down coordination", memberName);
        return host.shutdownSelf();
    }

    public CompletionStage<Void> onToolApprovalResult(CoordinationEvent event) {
        BaseEventMessage payload = payloadOf(event);
        if (!(payload instanceof ToolApprovalResultEvent approval)) {
            return CompletableFuture.completedFuture(null);
        }
        String memberName = blueprint.getMemberName();
        String targetId = approval.getMemberName();
        if (targetId == null || !targetId.equals(memberName)) {
            return CompletableFuture.completedFuture(null);
        }

        InteractiveInput interactiveInput = new InteractiveInput();
        Map<String, Object> approvalPayload = new LinkedHashMap<>();
        approvalPayload.put("approved", approval.isApproved());
        approvalPayload.put("feedback", approval.getFeedback());
        approvalPayload.put("auto_confirm", approval.isAutoConfirm());
        interactiveInput.update(approval.getToolCallId(), approvalPayload);
        LOGGER.debug(
                "[{}] received tool approval result for tool_call_id={}, approved={}",
                memberName,
                approval.getToolCallId(),
                approval.isApproved()
        );
        return host.resumeInterrupt(interactiveInput);
    }

    public CompletionStage<Void> onTaskPlanResponse(CoordinationEvent event) {
        BaseEventMessage payload = payloadOf(event);
        if (!(payload instanceof TaskPlanResponseEvent response)) {
            return CompletableFuture.completedFuture(null);
        }
        String memberName = blueprint.getMemberName();
        String targetId = response.getMemberName();
        if (targetId == null || !targetId.equals(memberName)) {
            return CompletableFuture.completedFuture(null);
        }
        if (response.getToolCallId() == null || response.getToolCallId().isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }

        InteractiveInput interactiveInput = new InteractiveInput();
        Map<String, Object> planPayload = new LinkedHashMap<>();
        planPayload.put("approved", response.isApproved());
        planPayload.put("feedback", response.getFeedback());
        planPayload.put("plan_id", response.getPlanId() == null ? "" : response.getPlanId());
        interactiveInput.update(response.getToolCallId(), planPayload);
        LOGGER.debug(
                "[{}] received task plan response for tool_call_id={}, approved={}",
                memberName,
                response.getToolCallId(),
                response.isApproved()
        );
        return host.resumeInterrupt(interactiveInput);
    }

    public TeamInfra getInfra() {
        return infra;
    }

    private static BaseEventMessage payloadOf(CoordinationEvent event) {
        EventMessage eventMessage = ((TransportEvent) event).getMessage();
        return eventMessage.getPayload();
    }
}

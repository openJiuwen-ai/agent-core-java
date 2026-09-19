/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentteams.agent.coordination.handlers;

import com.openjiuwen.agentteams.I18n;
import com.openjiuwen.agentteams.agent.coordination.DispatcherHost;
import com.openjiuwen.agentteams.agent.coordination.PollController;
import com.openjiuwen.agentteams.agent.coordination.TeamAgentBlueprint;
import com.openjiuwen.agentteams.agent.coordination.TeamInfra;
import com.openjiuwen.agentteams.schema.events.CoordinationEvent;
import com.openjiuwen.agentteams.schema.events.EventMessage;
import com.openjiuwen.agentteams.schema.events.TeamEvent;
import com.openjiuwen.agentteams.schema.team.TeamRole;

import java.util.Map;
import java.util.Optional;

/**
 * Narrate swarmflow phase/lifecycle milestones to the spectator leader.
 *
 * <p>Mirrors Python {@code handlers/workflow.py}. Consumes WORKFLOW_PROGRESS
 * events and feeds narration lines to the leader via {@code deliver_input}
 * with {@code useSteer=true}. Per-agent progress is intentionally NOT narrated
 * (too chatty); it lives in the 4-layer {@code WorkflowRun} the observer
 * accumulates. Completion / failure results are fed back by the NativeHarness
 * async-tool framework, so only mid-run milestones remain here.
 *
 * @since 2026/7/9
 */
public class WorkflowHandler extends BaseCoordinationHandler {
    private static final String KIND_WORKFLOW_STARTED = "workflow_started";
    private static final String KIND_PHASE = "phase";

    /**
     * Construct and register event bindings.
     *
     * @param host the owning TeamAgent
     * @param blueprint static config
     * @param infra per-process services
     * @param pollCtrl poll control surface
     */
    public WorkflowHandler(DispatcherHost host, TeamAgentBlueprint blueprint,
                           TeamInfra infra, PollController pollCtrl) {
        super(host, blueprint, infra, pollCtrl);
        callbacks.put(TeamEvent.WORKFLOW_PROGRESS, this::onWorkflowProgress);
    }

    /**
     * Render a phase/lifecycle milestone and deliver it to the leader.
     *
     * @param event the workflow progress event
     */
    public void onWorkflowProgress(CoordinationEvent event) {
        if (blueprint.role().orElse(null) != TeamRole.LEADER) {
            return;
        }
        if (!(event instanceof EventMessage msg)) {
            return;
        }
        Map<String, Object> payload = msg.getPayload() != null ? msg.getPayload() : Map.of();
        String kind = str(payload, "kind");
        String workflowName = str(payload, "workflow_name");
        String phase = str(payload, "phase");
        Optional<String> line = render(kind, workflowName, phase);
        if (line.isEmpty()) {
            return;
        }
        round.deliverInput(line.get(), true);
    }

    private static Optional<String> render(String kind, String workflowName, String phase) {
        String name = (workflowName != null && !workflowName.isBlank()) ? workflowName : "workflow";
        if (KIND_WORKFLOW_STARTED.equals(kind)) {
            return Optional.of(I18n.t("workflow.started", name));
        }
        if (KIND_PHASE.equals(kind)) {
            return Optional.of(I18n.t("workflow.phase", phase != null ? phase : "?"));
        }
        return Optional.empty();
    }
}

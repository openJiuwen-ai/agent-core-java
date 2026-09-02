/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentteams.agent.coordination.handlers;

import com.openjiuwen.agentteams.agent.coordination.DispatcherHost;
import com.openjiuwen.agentteams.agent.coordination.InnerEventType;
import com.openjiuwen.agentteams.agent.coordination.PollController;
import com.openjiuwen.agentteams.agent.coordination.TeamAgentBlueprint;
import com.openjiuwen.agentteams.agent.coordination.TeamInfra;
import com.openjiuwen.agentteams.messager.Messager;
import com.openjiuwen.agentteams.schema.events.CoordinationEvent;
import com.openjiuwen.agentteams.schema.events.EventMessage;
import com.openjiuwen.agentteams.schema.events.TeamEvent;
import com.openjiuwen.agentteams.schema.events.TeamTopic;
import com.openjiuwen.agentteams.schema.team.TeamCompletionSnapshot;
import com.openjiuwen.agentteams.schema.team.TeamRole;
import com.openjiuwen.agentteams.tools.TeamBackend;
import com.openjiuwen.core.common.logging.Loggers;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletionException;

/**
 * Drive + consume the team-completion lifecycle.
 *
 * <p>Mirrors Python {@code handlers/team_completion.py}. Two responsibilities:
 * <ul>
 *   <li>On {@code POLL_TASK} ticks (leader only, when the leader is idle):
 *       evaluate the three team-completion conditions via
 *       {@code TeamBackend.isTeamCompleted()} and emit {@code TEAM_COMPLETED}
 *       once per rising edge.</li>
 *   <li>On {@code TASK_LIST_DRAINED}: log it and fire every registered
 *       completion callback. {@code TeamAgent} wires
 *       {@code TeamSkillRail.notifyTeamCompleted} here once after the
 *       DeepAgent is built, so a drained board triggers team-skill evolution
 *       without waiting for a {@code view_task} call.</li>
 *   <li>On {@code TEAM_COMPLETED}: consume the event with a structured log.</li>
 * </ul>
 *
 * <p>Evaluation runs on the poll tick rather than reacting to member-status
 * events because the kernel self-filter drops the leader's own
 * {@code MemberStatusChangedEvent} — the leader cannot observe its own settle,
 * so the periodic idle tick is the reliable leader-idle hook.
 *
 * @since 2026/7/9
 */
public class TeamCompletionHandler extends BaseCoordinationHandler {
    /** Rising-edge guard: emit TEAM_COMPLETED once per completion cycle. */
    private boolean isTeamCompletedEmitted = false;

    /** Callbacks fired on TASK_LIST_DRAINED (e.g. TeamSkillRail.notifyTeamCompleted). */
    private final List<Runnable> completionCallbacks = new ArrayList<>();

    /**
     * Construct and register event bindings.
     *
     * @param host the owning TeamAgent
     * @param blueprint static config
     * @param infra per-process services
     * @param pollCtrl poll control surface
     */
    public TeamCompletionHandler(DispatcherHost host, TeamAgentBlueprint blueprint,
                                 TeamInfra infra, PollController pollCtrl) {
        super(host, blueprint, infra, pollCtrl);
        callbacks.put(InnerEventType.POLL_TASK.getValue(), this::onPollTask);
        callbacks.put(TeamEvent.TASK_LIST_DRAINED, this::onTaskListDrained);
        callbacks.put(TeamEvent.TEAM_COMPLETED, this::onTeamCompleted);
    }

    /**
     * Register a callback fired whenever the task board drains.
     *
     * <p>Called at construction wiring time, not per event. Each callback runs
     * on every {@code TASK_LIST_DRAINED} this handler receives.
     *
     * @param callback the callback to register
     */
    public void registerCompletionCallback(Runnable callback) {
        if (callback != null) {
            completionCallbacks.add(callback);
        }
    }

    /**
     * Reset the rising-edge guard so the next completion re-emits.
     *
     * <p>Called by the kernel on every {@code start} (cold start / resume /
     * recover) so each run cycle evaluates team completion independently.
     */
    public void rearm() {
        this.isTeamCompletedEmitted = false;
    }

    /**
     * Leader-idle tick: evaluate the three completion conditions.
     *
     * <p>Gated to the leader and to a genuinely idle leader — a mid-round
     * leader's own status is BUSY, which fails condition 1 anyway, so the
     * early return just skips a wasted DB scan.
     *
     * @param event the poll task event
     */
    public void onPollTask(CoordinationEvent event) {
        if (blueprint.role().orElse(null) != TeamRole.LEADER) {
            return;
        }
        Object backend = infra.teamBackend();
        if (!(backend instanceof TeamBackend tb)) {
            return;
        }
        if (round.hasInFlightRound() || round.isAgentRunning()) {
            return;
        }
        Optional<TeamCompletionSnapshot> snapshotOpt = tb.isTeamCompleted();
        if (snapshotOpt.isEmpty()) {
            // Falling edge: re-arm so the next rising edge emits again.
            isTeamCompletedEmitted = false;
            return;
        }
        if (isTeamCompletedEmitted) {
            return;
        }
        TeamCompletionSnapshot snapshot = snapshotOpt.get();
        publishTeamCompleted(tb, snapshot);
        isTeamCompletedEmitted = true;

        // Persistent teams auto-pause on completion: close the leader stream
        // so the Runner finally drives finalize -> pause. Temporary teams are
        // torn down by their leader via clean_team, not here.
        // For teamDeleted snapshots the team DB row is already gone — there is
        // no persistent lifecycle to conclude; just mark the stream terminated
        // via the TEAM_COMPLETED payload consumer.
        if ("persistent".equalsIgnoreCase(blueprint.lifecycle().orElse(null))
                && !snapshot.isTeamDeleted()) {
            lifecycle.concludeCompletedRound(snapshot.memberCount(), snapshot.taskCount());
        }
    }

    private void publishTeamCompleted(TeamBackend tb, TeamCompletionSnapshot snapshot) {
        Messager messager = infra.messager();
        if (messager == null) {
            return;
        }
        String teamName = tb.getTeamName();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("team_name", teamName);
        payload.put("member_count", snapshot.memberCount());
        payload.put("task_count", snapshot.taskCount());
        if (snapshot.isTeamDeleted()) {
            payload.put("team_deleted", true);
        }
        EventMessage event = EventMessage.builder()
                .eventType(TeamEvent.TEAM_COMPLETED)
                .payload(payload)
                .build();
        try {
            Optional<String> sessionId = resolveSessionId();
            if (sessionId.isEmpty()) {
                Loggers.AGENT.warn("Cannot publish TEAM_COMPLETED: session id unavailable");
                return;
            }
            String topic = TeamTopic.TEAM.build(sessionId.get(), teamName);
            messager.publish(topic, event).join();
            Loggers.AGENT.info("[leader] team {} completed: {} members, {} tasks",
                    teamName, snapshot.memberCount(), snapshot.taskCount());
        } catch (CompletionException e) {
            Loggers.AGENT.error("Failed to publish TEAM_COMPLETED for team {}: {}",
                    teamName, e.getMessage(), e);
        }
    }

    private Optional<String> resolveSessionId() {
        if (blueprint.ctx() != null) {
            return Optional.ofNullable(blueprint.ctx().getSessionId());
        }
        return Optional.empty();
    }

    /**
     * Consume TASK_LIST_DRAINED — log it and fire registered callbacks.
     *
     * <p>Callbacks are wired once after construction; the registry being
     * non-empty only on the leader (where {@code TeamSkillRail} is mounted)
     * is what scopes the fan-out. Each callback is isolated so one failure
     * does not skip the rest.
     *
     * @param event the task list drained event
     */
    public void onTaskListDrained(CoordinationEvent event) {
        Map<String, Object> payload = extractPayload(event);
        String teamName = str(payload, "team_name");
        Object count = payload.get("task_count");
        Loggers.AGENT.info("task list drained for team {}: {} terminal task(s)",
                teamName, count != null ? count : 0);
        for (Runnable callback : completionCallbacks) {
            try {
                callback.run();
            } catch (IllegalStateException | NullPointerException
                    | IllegalArgumentException | UnsupportedOperationException e) {
                Loggers.AGENT.error("task list drained callback failed: {}", e.getMessage(), e);
            }
        }
    }

    /**
     * Consume TEAM_COMPLETED — structured log of team completion.
     *
     * <p>The kernel self-filter drops the emitting leader's own copy, so this
     * runs on teammates; the leader already logged at emit time.
     *
     * @param event the team completed event
     */
    public void onTeamCompleted(CoordinationEvent event) {
        Map<String, Object> payload = extractPayload(event);
        String teamName = str(payload, "team_name");
        Object memberCount = payload.get("member_count");
        Object taskCount = payload.get("task_count");
        Loggers.AGENT.info("team {} reported completed: {} members, {} tasks",
                teamName, memberCount != null ? memberCount : 0, taskCount != null ? taskCount : 0);
    }

    private static Map<String, Object> extractPayload(CoordinationEvent event) {
        if (event instanceof EventMessage msg && msg.getPayload() != null) {
            return msg.getPayload();
        }
        return Map.of();
    }
}

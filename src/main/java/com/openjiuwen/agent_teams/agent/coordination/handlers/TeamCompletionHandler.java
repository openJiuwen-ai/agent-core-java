/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.agent.coordination.handlers;

import com.openjiuwen.agent_teams.AgentTeamsContext;
import com.openjiuwen.agent_teams.agent.AgentConfigurator.TeamInfra;
import com.openjiuwen.agent_teams.agent.AgentConfigurator.TeamRole;
import com.openjiuwen.agent_teams.agent.TeamAgentBlueprint;
import com.openjiuwen.agent_teams.agent.coordination.EventDispatcher.CoordinationEvent;
import com.openjiuwen.agent_teams.agent.coordination.EventDispatcher.DispatcherHost;
import com.openjiuwen.agent_teams.agent.coordination.EventDispatcher.InnerEventType;
import com.openjiuwen.agent_teams.agent.coordination.EventDispatcher.PollController;
import com.openjiuwen.agent_teams.agent.coordination.EventDispatcher.TransportEvent;
import com.openjiuwen.agent_teams.messager.Messager;
import com.openjiuwen.agent_teams.schema.BaseEventMessage;
import com.openjiuwen.agent_teams.schema.TaskListDrainedEvent;
import com.openjiuwen.agent_teams.schema.TeamCompletedEvent;
import com.openjiuwen.agent_teams.schema.TeamEvent;
import com.openjiuwen.agent_teams.schema.TeamTopic;
import com.openjiuwen.agent_teams.schema.events.EventMessage;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Drives and consumes team-completion coordination events.
 *
 * <p>Mirrors Python's {@code TeamCompletionHandler} in
 * {@code openjiuwen/agent_teams/agent/coordination/handlers/team_completion.py}.</p>
 */
public class TeamCompletionHandler extends BaseCoordinationHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(TeamCompletionHandler.class);

    private boolean teamCompletedEmitted;
    private final List<CompletionCallback> completionCallbacks = new ArrayList<>();

    public TeamCompletionHandler(
            DispatcherHost host,
            TeamAgentBlueprint blueprint,
            TeamInfra infra,
            PollController pollController
    ) {
        super(host, blueprint, infra, pollController);
    }

    @Override
    public Map<String, String> getEventMethodMap() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put(InnerEventType.POLL_TASK.value(), "onPollTask");
        map.put(TeamEvent.TASK_LIST_DRAINED, "onTaskListDrained");
        map.put(TeamEvent.TEAM_COMPLETED, "onTeamCompleted");
        return map;
    }

    @Override
    protected EventCallback resolveCallback(String methodName) {
        return switch (methodName) {
            case "onPollTask" -> this::onPollTask;
            case "onTaskListDrained" -> this::onTaskListDrained;
            case "onTeamCompleted" -> this::onTeamCompleted;
            default -> throw new IllegalArgumentException("Unknown method: " + methodName);
        };
    }

    public void registerCompletionCallback(CompletionCallback callback) {
        completionCallbacks.add(callback);
    }

    public void rearm() {
        teamCompletedEmitted = false;
    }

    public CompletionStage<Void> onPollTask(CoordinationEvent event) {
        if (blueprint.getRole() != TeamRole.LEADER || !(infra.getTeamBackend() instanceof TeamBackendView backend)) {
            return CompletableFuture.completedFuture(null);
        }
        if (round.hasInFlightRound() || round.isAgentRunning()) {
            return CompletableFuture.completedFuture(null);
        }

        return backend.isTeamCompleted().thenCompose(snapshot -> {
            if (snapshot.isEmpty()) {
                teamCompletedEmitted = false;
                return CompletableFuture.completedFuture(null);
            }
            if (teamCompletedEmitted) {
                return CompletableFuture.completedFuture(null);
            }
            TeamCompletionSnapshot completed = snapshot.get();
            return publishTeamCompleted(backend.teamName(), completed)
                    .thenCompose(ignored -> {
                        teamCompletedEmitted = true;
                        if ("persistent".equals(blueprint.getLifecycle())) {
                            return lifecycle.concludeCompletedRound(completed.memberCount(), completed.taskCount());
                        }
                        return CompletableFuture.completedFuture(null);
                    });
        });
    }

    public CompletionStage<Void> publishTeamCompleted(String teamName, TeamCompletionSnapshot snapshot) {
        Messager messager = infra.getMessager();
        if (messager == null) {
            return CompletableFuture.completedFuture(null);
        }
        TeamCompletedEvent payload = new TeamCompletedEvent();
        payload.setTeamName(teamName);
        payload.setMemberCount(snapshot.memberCount());
        payload.setTaskCount(snapshot.taskCount());
        String topic = TeamTopic.TEAM.build(AgentTeamsContext.getSessionId(), teamName);
        return messager.publish(topic, EventMessage.fromEvent(payload))
                .thenRun(() -> LOGGER.info(
                        "[leader] team {} completed: {} members, {} tasks",
                        teamName,
                        snapshot.memberCount(),
                        snapshot.taskCount()
                ))
                .exceptionally(exception -> {
                    LOGGER.error("Failed to publish TEAM_COMPLETED for team {}: {}", teamName, exception.toString());
                    return (Void) null;
                });
    }

    public CompletionStage<Void> onTaskListDrained(CoordinationEvent event) {
        BaseEventMessage payload = messageOf(event).getPayload();
        if (payload instanceof TaskListDrainedEvent drained) {
            LOGGER.info(
                    "task list drained for team {}: {} terminal task(s)",
                    drained.getTeamName(),
                    drained.getTaskCount()
            );
        }
        CompletionStage<Void> chain = CompletableFuture.completedFuture(null);
        for (CompletionCallback callback : completionCallbacks) {
            chain = chain.thenCompose(ignored -> callback.onCompleted()
                    .exceptionally(exception -> {
                        LOGGER.error("task list drained callback failed: {}", exception.toString());
                        return (Void) null;
                    }));
        }
        return chain;
    }

    public CompletionStage<Void> onTeamCompleted(CoordinationEvent event) {
        BaseEventMessage payload = messageOf(event).getPayload();
        if (payload instanceof TeamCompletedEvent completed) {
            LOGGER.info(
                    "team {} reported completed: {} members, {} tasks",
                    completed.getTeamName(),
                    completed.getMemberCount(),
                    completed.getTaskCount()
            );
        }
        return CompletableFuture.completedFuture(null);
    }

    public boolean isTeamCompletedEmitted() {
        return teamCompletedEmitted;
    }

    public List<CompletionCallback> getCompletionCallbacks() {
        return List.copyOf(completionCallbacks);
    }

    private static EventMessage messageOf(CoordinationEvent event) {
        return ((TransportEvent) event).getMessage();
    }

    /**
     * Completion callback fired when the task list drains.
     *
     * <p>Mirrors Python's completion callback callable in
     * {@code openjiuwen/agent_teams/agent/coordination/handlers/team_completion.py}.</p>
     */
    @FunctionalInterface
    public interface CompletionCallback {
        CompletionStage<Void> onCompleted();
    }

    /**
     * Team backend surface used to evaluate team completion.
     *
     * <p>Mirrors Python's backend calls in
     * {@code openjiuwen/agent_teams/agent/coordination/handlers/team_completion.py}.</p>
     */
    public interface TeamBackendView {
        CompletionStage<Optional<TeamCompletionSnapshot>> isTeamCompleted();

        String teamName();
    }

    /**
     * Completion counts returned by the team backend.
     *
     * <p>Mirrors Python's {@code TeamCompletionSnapshot} use in
     * {@code openjiuwen/agent_teams/agent/coordination/handlers/team_completion.py}.</p>
     */
    public record TeamCompletionSnapshot(int memberCount, int taskCount) {
    }
}

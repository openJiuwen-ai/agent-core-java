/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.agent.coordination.handlers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.openjiuwen.agent_teams.AgentTeamsContext;
import com.openjiuwen.agent_teams.agent.AgentConfigurator.AgentCard;
import com.openjiuwen.agent_teams.agent.AgentConfigurator.ConfiguredTeamBackend;
import com.openjiuwen.agent_teams.agent.AgentConfigurator.TeamAgentSpec;
import com.openjiuwen.agent_teams.agent.AgentConfigurator.TeamInfra;
import com.openjiuwen.agent_teams.agent.AgentConfigurator.TeamRole;
import com.openjiuwen.agent_teams.agent.AgentConfigurator.TeamRuntimeContext;
import com.openjiuwen.agent_teams.agent.TeamAgentBlueprint;
import com.openjiuwen.agent_teams.agent.coordination.EventDispatcher.DispatcherHost;
import com.openjiuwen.agent_teams.agent.coordination.EventDispatcher.InnerEventMessage;
import com.openjiuwen.agent_teams.agent.coordination.EventDispatcher.InnerEventType;
import com.openjiuwen.agent_teams.agent.coordination.EventDispatcher.PollController;
import com.openjiuwen.agent_teams.agent.coordination.EventDispatcher.TransportEvent;
import com.openjiuwen.agent_teams.messager.Messager;
import com.openjiuwen.agent_teams.messager.MessagerHandler;
import com.openjiuwen.agent_teams.schema.TaskListDrainedEvent;
import com.openjiuwen.agent_teams.schema.TeamCompletedEvent;
import com.openjiuwen.agent_teams.schema.TeamEvent;
import com.openjiuwen.agent_teams.schema.events.EventMessage;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.junit.jupiter.api.Test;

/**
 * Focused parity tests for {@link TeamCompletionHandler}.
 *
 * <p>Mirrors Python's team-completion handler behavior in
 * {@code openjiuwen/agent_teams/agent/coordination/handlers/team_completion.py}.</p>
 */
class TeamCompletionHandlerTest {

    @Test
    void callbackMapPreservesPythonTeamCompletionOrder() {
        TeamCompletionHandler handler = newHandler(
                new RecordingHost(),
                TeamRole.LEADER,
                "leader",
                "temporary",
                new TeamInfra(),
                new RecordingPoll()
        );

        assertEquals(
                List.of(
                        "coordination_poll_task",
                        TeamEvent.TASK_LIST_DRAINED,
                        TeamEvent.TEAM_COMPLETED
                ),
                handler.getCallbacks().keySet().stream().toList()
        );
    }

    @Test
    void leaderPollPublishesOncePerRisingEdgeAndPersistentConcludesRound() {
        RecordingHost host = new RecordingHost();
        RecordingBackend backend = new RecordingBackend();
        backend.snapshots.add(Optional.of(new TeamCompletionHandler.TeamCompletionSnapshot(3, 5)));
        backend.snapshots.add(Optional.of(new TeamCompletionHandler.TeamCompletionSnapshot(3, 5)));
        backend.snapshots.add(Optional.empty());
        backend.snapshots.add(Optional.of(new TeamCompletionHandler.TeamCompletionSnapshot(3, 5)));
        RecordingMessager messager = new RecordingMessager();
        TeamInfra infra = new TeamInfra();
        infra.setTeamBackend(backend);
        infra.setMessager(messager);
        TeamCompletionHandler handler = newHandler(host, TeamRole.LEADER, "leader", "persistent", infra, new RecordingPoll());
        AgentTeamsContext.SessionIdToken token = AgentTeamsContext.setSessionId("sid-1");
        try {
            handler.onPollTask(new InnerEventMessage(InnerEventType.POLL_TASK)).toCompletableFuture().join();
            handler.onPollTask(new InnerEventMessage(InnerEventType.POLL_TASK)).toCompletableFuture().join();
            handler.onPollTask(new InnerEventMessage(InnerEventType.POLL_TASK)).toCompletableFuture().join();
            handler.onPollTask(new InnerEventMessage(InnerEventType.POLL_TASK)).toCompletableFuture().join();
        } finally {
            AgentTeamsContext.resetSessionId(token);
        }

        assertEquals(2, messager.topics.size());
        assertEquals("session:sid-1:team:team:team", messager.topics.getFirst());
        assertEquals(TeamEvent.TEAM_COMPLETED, messager.messages.getFirst().getEventType());
        assertEquals(2, host.concludedRounds.size());
        assertEquals("3:5", host.concludedRounds.getFirst());
        assertTrue(handler.isTeamCompletedEmitted());
    }

    @Test
    void nonLeaderBusyOrRunningLeaderSkipsCompletionEvaluation() {
        RecordingBackend backend = new RecordingBackend();
        backend.snapshots.add(Optional.of(new TeamCompletionHandler.TeamCompletionSnapshot(1, 1)));
        TeamInfra infra = new TeamInfra();
        infra.setTeamBackend(backend);
        infra.setMessager(new RecordingMessager());
        RecordingHost host = new RecordingHost();
        TeamCompletionHandler teammate = newHandler(host, TeamRole.TEAMMATE, "dev", "persistent", infra, new RecordingPoll());
        TeamCompletionHandler leader = newHandler(host, TeamRole.LEADER, "leader", "persistent", infra, new RecordingPoll());

        teammate.onPollTask(new InnerEventMessage(InnerEventType.POLL_TASK)).toCompletableFuture().join();
        host.agentRunning = true;
        leader.onPollTask(new InnerEventMessage(InnerEventType.POLL_TASK)).toCompletableFuture().join();
        host.agentRunning = false;
        host.inFlightRound = true;
        leader.onPollTask(new InnerEventMessage(InnerEventType.POLL_TASK)).toCompletableFuture().join();

        assertEquals(0, backend.evaluationCount);
    }

    @Test
    void temporaryCompletionPublishesWithoutConcludingRound() {
        RecordingBackend backend = new RecordingBackend();
        backend.snapshots.add(Optional.of(new TeamCompletionHandler.TeamCompletionSnapshot(2, 4)));
        RecordingMessager messager = new RecordingMessager();
        TeamInfra infra = new TeamInfra();
        infra.setTeamBackend(backend);
        infra.setMessager(messager);
        RecordingHost host = new RecordingHost();
        TeamCompletionHandler handler = newHandler(host, TeamRole.LEADER, "leader", "temporary", infra, new RecordingPoll());

        handler.onPollTask(new InnerEventMessage(InnerEventType.POLL_TASK)).toCompletableFuture().join();

        assertEquals(1, messager.messages.size());
        assertTrue(host.concludedRounds.isEmpty());
    }

    @Test
    void taskListDrainedFiresCallbacksAndIsolatesFailures() {
        TeamCompletionHandler handler = newHandler(
                new RecordingHost(),
                TeamRole.LEADER,
                "leader",
                "temporary",
                new TeamInfra(),
                new RecordingPoll()
        );
        List<String> calls = new ArrayList<>();
        handler.registerCompletionCallback(() -> {
            calls.add("first");
            return CompletableFuture.failedFuture(new IllegalStateException("boom"));
        });
        handler.registerCompletionCallback(() -> {
            calls.add("second");
            return CompletableFuture.completedFuture(null);
        });

        handler.onTaskListDrained(transport(EventMessage.fromEvent(taskListDrained("team", 2))))
                .toCompletableFuture()
                .join();

        assertEquals(List.of("first", "second"), calls);
        assertEquals(2, handler.getCompletionCallbacks().size());
    }

    @Test
    void rearmAllowsNextCompletedPollToEmitAgain() {
        RecordingBackend backend = new RecordingBackend();
        backend.snapshots.add(Optional.of(new TeamCompletionHandler.TeamCompletionSnapshot(1, 1)));
        backend.snapshots.add(Optional.of(new TeamCompletionHandler.TeamCompletionSnapshot(1, 1)));
        RecordingMessager messager = new RecordingMessager();
        TeamInfra infra = new TeamInfra();
        infra.setTeamBackend(backend);
        infra.setMessager(messager);
        TeamCompletionHandler handler = newHandler(
                new RecordingHost(),
                TeamRole.LEADER,
                "leader",
                "temporary",
                infra,
                new RecordingPoll()
        );

        handler.onPollTask(new InnerEventMessage(InnerEventType.POLL_TASK)).toCompletableFuture().join();
        handler.rearm();
        handler.onPollTask(new InnerEventMessage(InnerEventType.POLL_TASK)).toCompletableFuture().join();
        handler.onTeamCompleted(transport(EventMessage.fromEvent(teamCompleted("team", 1, 1))))
                .toCompletableFuture()
                .join();

        assertEquals(2, messager.messages.size());
        assertTrue(handler.isTeamCompletedEmitted());
    }

    private static TeamCompletionHandler newHandler(
            RecordingHost host,
            TeamRole role,
            String memberName,
            String lifecycle,
            TeamInfra infra,
            RecordingPoll poll
    ) {
        return new TeamCompletionHandler(host, blueprint(role, memberName, lifecycle), infra, poll);
    }

    private static TeamAgentBlueprint blueprint(TeamRole role, String memberName, String lifecycle) {
        TeamRuntimeContext ctx = new TeamRuntimeContext();
        ctx.setRole(role);
        ctx.setMemberName(memberName);
        TeamAgentSpec spec = new TeamAgentSpec();
        spec.setLifecycle(lifecycle);
        return new TeamAgentBlueprint(
                new AgentCard("agent", "Agent", "description"),
                spec,
                ctx,
                "",
                "cn"
        );
    }

    private static TransportEvent transport(EventMessage message) {
        return new TransportEvent(message);
    }

    private static TaskListDrainedEvent taskListDrained(String teamName, int taskCount) {
        TaskListDrainedEvent event = new TaskListDrainedEvent();
        event.setTeamName(teamName);
        event.setTaskCount(taskCount);
        return event;
    }

    private static TeamCompletedEvent teamCompleted(String teamName, int memberCount, int taskCount) {
        TeamCompletedEvent event = new TeamCompletedEvent();
        event.setTeamName(teamName);
        event.setMemberCount(memberCount);
        event.setTaskCount(taskCount);
        return event;
    }

    private static final class RecordingHost implements DispatcherHost {
        private final List<String> concludedRounds = new ArrayList<>();
        private boolean agentRunning;
        private boolean inFlightRound;

        @Override
        public boolean isAgentReady() {
            return true;
        }

        @Override
        public boolean isAgentRunning() {
            return agentRunning;
        }

        @Override
        public boolean hasInFlightRound() {
            return inFlightRound;
        }

        @Override
        public boolean hasPendingInterrupt() {
            return false;
        }

        @Override
        public CompletionStage<Void> cancelAgent() {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Void> deliverInput(Object content, boolean useSteer) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Void> resumeInterrupt(Object userInput) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Void> shutdownSelf() {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Void> concludeCompletedRound(int memberCount, int taskCount) {
            concludedRounds.add(memberCount + ":" + taskCount);
            return CompletableFuture.completedFuture(null);
        }
    }

    private static final class RecordingPoll implements PollController {
        @Override
        public CompletionStage<Void> pausePolls() {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Void> resumePolls() {
            return CompletableFuture.completedFuture(null);
        }
    }

    private static final class RecordingBackend extends ConfiguredTeamBackend implements TeamCompletionHandler.TeamBackendView {
        private final Deque<Optional<TeamCompletionHandler.TeamCompletionSnapshot>> snapshots = new ArrayDeque<>();
        private int evaluationCount;

        private RecordingBackend() {
            super("team", "leader", true, Map.of(), null, "", List.of(), null, null, true, false, List.of(), null, null, "leader");
        }

        @Override
        public CompletionStage<Optional<TeamCompletionHandler.TeamCompletionSnapshot>> isTeamCompleted() {
            evaluationCount++;
            return CompletableFuture.completedFuture(snapshots.isEmpty() ? Optional.empty() : snapshots.removeFirst());
        }

        @Override
        public String teamName() {
            return getTeamName();
        }
    }

    private static final class RecordingMessager implements Messager {
        private final List<String> topics = new ArrayList<>();
        private final List<EventMessage> messages = new ArrayList<>();

        @Override
        public CompletionStage<Void> start() {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Void> stop() {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Void> publish(String topicId, EventMessage message) {
            topics.add(topicId);
            messages.add(message);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Void> subscribe(String topicId, MessagerHandler handler) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Void> unsubscribe(String topicId) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Void> send(String agentId, EventMessage message) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Void> registerDirectMessageHandler(MessagerHandler handler) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Void> unregisterDirectMessageHandler() {
            return CompletableFuture.completedFuture(null);
        }
    }
}

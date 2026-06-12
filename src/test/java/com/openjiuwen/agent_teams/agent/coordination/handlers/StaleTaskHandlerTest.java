/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.agent.coordination.handlers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.openjiuwen.agent_teams.agent.AgentConfigurator.AgentCard;
import com.openjiuwen.agent_teams.agent.AgentConfigurator.TeamAgentSpec;
import com.openjiuwen.agent_teams.agent.AgentConfigurator.TeamInfra;
import com.openjiuwen.agent_teams.agent.AgentConfigurator.TeamRole;
import com.openjiuwen.agent_teams.agent.AgentConfigurator.TeamRuntimeContext;
import com.openjiuwen.agent_teams.agent.TeamAgentBlueprint;
import com.openjiuwen.agent_teams.agent.coordination.EventDispatcher.DispatcherHost;
import com.openjiuwen.agent_teams.agent.coordination.EventDispatcher.InnerEventMessage;
import com.openjiuwen.agent_teams.agent.coordination.EventDispatcher.InnerEventType;
import com.openjiuwen.agent_teams.agent.coordination.EventDispatcher.PollController;
import com.openjiuwen.agent_teams.schema.status.TaskStatus;
import com.openjiuwen.agent_teams.tools.TeamTask;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.junit.jupiter.api.Test;

/**
 * Focused parity tests for {@link StaleTaskHandler}.
 *
 * <p>Mirrors Python's stale-task sweep behavior in
 * {@code openjiuwen/agent_teams/agent/coordination/handlers/stale_task.py}.</p>
 */
class StaleTaskHandlerTest {

    @Test
    void callbackMapPreservesPythonPollTaskOrder() {
        StaleTaskHandler handler = newHandler(
                new RecordingHost(),
                TeamRole.TEAMMATE,
                "dev",
                new TeamInfra(),
                new LinkedHashMap<>()
        );

        assertEquals(List.of("coordination_poll_task"), handler.getCallbacks().keySet().stream().toList());
    }

    @Test
    void teammateSelfNudgesOnlyOwnStaleClaimedTasks() {
        RecordingHost host = new RecordingHost();
        RecordingTaskManager taskManager = new RecordingTaskManager();
        long now = System.currentTimeMillis();
        TeamTask ownStale = task("T1", TaskStatus.CLAIMED.value(), "dev", now - 11 * 60 * 1000L);
        TeamTask otherStale = task("T2", TaskStatus.CLAIMED.value(), "other", now - 11 * 60 * 1000L);
        TeamTask ownFresh = task("T3", TaskStatus.CLAIMED.value(), "dev", now - 60 * 1000L);
        taskManager.byStatus.put(TaskStatus.CLAIMED.value(), List.of(ownStale, otherStale, ownFresh));
        TeamInfra infra = new TeamInfra();
        infra.setTaskManager(taskManager);
        Map<String, Double> throttle = new LinkedHashMap<>();
        StaleTaskHandler handler = newHandler(host, TeamRole.TEAMMATE, "dev", infra, throttle);

        handler.onPollTask(new InnerEventMessage(InnerEventType.POLL_TASK)).toCompletableFuture().join();

        assertEquals(List.of(TaskStatus.CLAIMED.value()), taskManager.statusCalls);
        assertEquals(1, host.delivered.size());
        assertTrue(host.delivered.getFirst().contains("[T1]"));
        assertFalse(host.delivered.getFirst().contains("[T2]"));
        assertFalse(host.useSteerValues.getFirst());
        assertTrue(throttle.containsKey("T1"));
        assertSame(throttle, handler.getLastStaleNudge());
    }

    @Test
    void leaderNudgesOtherAssigneeByMessageAndSelfByInput() {
        RecordingHost host = new RecordingHost();
        RecordingTaskManager taskManager = new RecordingTaskManager();
        RecordingMessageManager messageManager = new RecordingMessageManager();
        long now = System.currentTimeMillis();
        taskManager.byStatus.put(TaskStatus.CLAIMED.value(), List.of(
                task("T1", TaskStatus.CLAIMED.value(), "dev", now - 11 * 60 * 1000L),
                task("T2", TaskStatus.CLAIMED.value(), "leader", now - 11 * 60 * 1000L)
        ));
        TeamInfra infra = new TeamInfra();
        infra.setTaskManager(taskManager);
        infra.setMessageManager(messageManager);
        StaleTaskHandler handler = newHandler(host, TeamRole.LEADER, "leader", infra, new LinkedHashMap<>());

        handler.checkStaleClaimedTasks().toCompletableFuture().join();

        assertEquals(List.of("dev"), messageManager.targets);
        assertTrue(messageManager.sent.getFirst().contains("[T1]"));
        assertEquals(1, host.delivered.size());
        assertTrue(host.delivered.getFirst().contains("[T2]"));
    }

    @Test
    void claimedThrottlePreventsRepeatAndGcRemovesCompletedTask() {
        RecordingHost host = new RecordingHost();
        RecordingTaskManager taskManager = new RecordingTaskManager();
        long now = System.currentTimeMillis();
        taskManager.byStatus.put(TaskStatus.CLAIMED.value(), List.of(
                task("T1", TaskStatus.CLAIMED.value(), "dev", now - 11 * 60 * 1000L)
        ));
        TeamInfra infra = new TeamInfra();
        infra.setTaskManager(taskManager);
        Map<String, Double> throttle = new LinkedHashMap<>();
        StaleTaskHandler handler = newHandler(host, TeamRole.TEAMMATE, "dev", infra, throttle);

        handler.checkStaleClaimedTasks().toCompletableFuture().join();
        handler.checkStaleClaimedTasks().toCompletableFuture().join();
        taskManager.byStatus.put(TaskStatus.CLAIMED.value(), List.of());
        handler.checkStaleClaimedTasks().toCompletableFuture().join();

        assertEquals(1, host.delivered.size());
        assertFalse(throttle.containsKey("T1"));
    }

    @Test
    void leaderSelfPromptsForFreshStalePendingTasksOnly() {
        RecordingHost host = new RecordingHost();
        RecordingTaskManager taskManager = new RecordingTaskManager();
        long now = System.currentTimeMillis();
        taskManager.byStatus.put(TaskStatus.PENDING.value(), List.of(
                task("P1", TaskStatus.PENDING.value(), null, now - 11 * 60 * 1000L),
                task("P2", TaskStatus.PENDING.value(), null, now - 60 * 1000L),
                task("P3", TaskStatus.PENDING.value(), null, null)
        ));
        TeamInfra infra = new TeamInfra();
        infra.setTaskManager(taskManager);
        StaleTaskHandler handler = newHandler(host, TeamRole.LEADER, "leader", infra, new LinkedHashMap<>());

        handler.checkStalePendingTasks().toCompletableFuture().join();
        handler.checkStalePendingTasks().toCompletableFuture().join();

        assertEquals(1, host.delivered.size());
        assertTrue(host.delivered.getFirst().contains("[P1]"));
        assertFalse(host.delivered.getFirst().contains("[P2]"));
        assertFalse(host.delivered.getFirst().contains("[P3]"));
        assertTrue(handler.getLastPendingNudge().containsKey("P1"));
    }

    @Test
    void teammateDoesNotSweepPendingTasks() {
        RecordingHost host = new RecordingHost();
        RecordingTaskManager taskManager = new RecordingTaskManager();
        taskManager.byStatus.put(TaskStatus.PENDING.value(), List.of(
                task("P1", TaskStatus.PENDING.value(), null, System.currentTimeMillis() - 11 * 60 * 1000L)
        ));
        TeamInfra infra = new TeamInfra();
        infra.setTaskManager(taskManager);
        StaleTaskHandler handler = newHandler(host, TeamRole.TEAMMATE, "dev", infra, new LinkedHashMap<>());

        handler.checkStalePendingTasks().toCompletableFuture().join();

        assertTrue(host.delivered.isEmpty());
        assertTrue(taskManager.statusCalls.isEmpty());
    }

    private static StaleTaskHandler newHandler(
            RecordingHost host,
            TeamRole role,
            String memberName,
            TeamInfra infra,
            Map<String, Double> throttle
    ) {
        return new StaleTaskHandler(host, blueprint(role, memberName), infra, new RecordingPoll(), throttle);
    }

    private static TeamAgentBlueprint blueprint(TeamRole role, String memberName) {
        TeamRuntimeContext ctx = new TeamRuntimeContext();
        ctx.setRole(role);
        ctx.setMemberName(memberName);
        return new TeamAgentBlueprint(
                new AgentCard("agent", "Agent", "description"),
                new TeamAgentSpec(),
                ctx,
                "",
                "cn"
        );
    }

    private static TeamTask task(String taskId, String status, String assignee, Long updatedAt) {
        return new TeamTask(taskId, "team", "Title " + taskId, "Content " + taskId, status, assignee, updatedAt);
    }

    private static final class RecordingHost implements DispatcherHost {
        private final List<String> delivered = new ArrayList<>();
        private final List<Boolean> useSteerValues = new ArrayList<>();

        @Override
        public boolean isAgentReady() {
            return true;
        }

        @Override
        public boolean isAgentRunning() {
            return false;
        }

        @Override
        public boolean hasInFlightRound() {
            return false;
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
            delivered.add(String.valueOf(content));
            useSteerValues.add(useSteer);
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

    private static final class RecordingTaskManager implements StaleTaskHandler.TaskManager {
        private final Map<String, List<TeamTask>> byStatus = new LinkedHashMap<>();
        private final List<String> statusCalls = new ArrayList<>();

        @Override
        public CompletionStage<List<TeamTask>> listTasks(String status) {
            statusCalls.add(status);
            return CompletableFuture.completedFuture(byStatus.getOrDefault(status, List.of()));
        }
    }

    private static final class RecordingMessageManager implements StaleTaskHandler.MessageManager {
        private final List<String> sent = new ArrayList<>();
        private final List<String> targets = new ArrayList<>();

        @Override
        public CompletionStage<Void> sendMessage(String content, String targetId) {
            sent.add(content);
            targets.add(targetId);
            return CompletableFuture.completedFuture(null);
        }
    }
}

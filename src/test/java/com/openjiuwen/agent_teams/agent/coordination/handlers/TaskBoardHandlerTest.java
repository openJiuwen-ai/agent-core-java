/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.agent.coordination.handlers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.openjiuwen.agent_teams.AgentTeamI18n;
import com.openjiuwen.agent_teams.agent.AgentConfigurator.AgentCard;
import com.openjiuwen.agent_teams.agent.AgentConfigurator.ConfiguredTeamBackend;
import com.openjiuwen.agent_teams.agent.AgentConfigurator.TeamAgentSpec;
import com.openjiuwen.agent_teams.agent.AgentConfigurator.TeamInfra;
import com.openjiuwen.agent_teams.agent.AgentConfigurator.TeamRole;
import com.openjiuwen.agent_teams.agent.AgentConfigurator.TeamRuntimeContext;
import com.openjiuwen.agent_teams.agent.TeamAgentBlueprint;
import com.openjiuwen.agent_teams.agent.coordination.EventDispatcher.DispatcherHost;
import com.openjiuwen.agent_teams.agent.coordination.EventDispatcher.PollController;
import com.openjiuwen.agent_teams.agent.coordination.EventDispatcher.TransportEvent;
import com.openjiuwen.agent_teams.schema.TaskClaimedEvent;
import com.openjiuwen.agent_teams.schema.TaskCreatedEvent;
import com.openjiuwen.agent_teams.schema.TaskPlanResponseEvent;
import com.openjiuwen.agent_teams.schema.TeamEvent;
import com.openjiuwen.agent_teams.schema.events.EventMessage;
import com.openjiuwen.agent_teams.schema.status.TaskStatus;
import com.openjiuwen.agent_teams.tools.TeamTask;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Focused parity tests for {@link TaskBoardHandler}.
 *
 * <p>Mirrors Python's task-board handler behavior in
 * {@code openjiuwen/agent_teams/agent/coordination/handlers/task_board.py}.</p>
 */
class TaskBoardHandlerTest {

    @BeforeEach
    void pinChineseI18n() {
        AgentTeamI18n.setLanguage("cn");
    }

    @AfterEach
    void restoreI18n() {
        AgentTeamI18n.setLanguage(AgentTeamI18n.DEFAULT_LANGUAGE);
    }

    @Test
    void callbackMapPreservesPythonTaskBoardOrder() {
        TaskBoardHandler handler = newHandler(
                new RecordingHost(),
                TeamRole.TEAMMATE,
                "dev",
                "temporary",
                new TeamInfra(),
                new RecordingPoll()
        );

        assertEquals(
                List.of(
                        TeamEvent.TASK_CLAIMED,
                        TeamEvent.TASK_CREATED,
                        TeamEvent.TASK_PLAN_REQUEST,
                        TeamEvent.TASK_PLAN_RESPONSE,
                        TeamEvent.TASK_UPDATED,
                        TeamEvent.TASK_COMPLETED,
                        TeamEvent.TASK_CANCELLED,
                        TeamEvent.TASK_UNBLOCKED
                ),
                handler.getCallbacks().keySet().stream().toList()
        );
    }

    @Test
    void taskClaimedForSelfResumesPollsAndDeliversAssignment() {
        RecordingHost host = new RecordingHost();
        RecordingPoll poll = new RecordingPoll();
        RecordingTaskManager taskManager = new RecordingTaskManager();
        TeamInfra infra = new TeamInfra();
        infra.setTaskManager(taskManager);
        TaskBoardHandler handler = newHandler(host, TeamRole.TEAMMATE, "dev", "temporary", infra, poll);

        handler.onTaskClaimed(transport(EventMessage.fromEvent(taskClaimed("dev", "T1"))))
                .toCompletableFuture()
                .join();

        assertEquals(1, poll.resumeCount);
        assertEquals(1, host.delivered.size());
        assertTrue(host.delivered.get(0).contains("[T1]"));
        assertTrue(taskManager.listCalls == 0);
    }

    @Test
    void claimForOtherMemberFallsBackToBoardNudgeForNonHumanAgent() {
        RecordingHost host = new RecordingHost();
        RecordingPoll poll = new RecordingPoll();
        RecordingTaskManager taskManager = new RecordingTaskManager();
        taskManager.tasks = List.of(
                task("T1", TaskStatus.PENDING.value(), null),
                task("T2", TaskStatus.CLAIMED.value(), "other"),
                task("T3", TaskStatus.COMPLETED.value(), "dev")
        );
        TeamInfra infra = new TeamInfra();
        infra.setTaskManager(taskManager);
        TaskBoardHandler handler = newHandler(host, TeamRole.TEAMMATE, "dev", "temporary", infra, poll);

        handler.onTaskClaimed(transport(EventMessage.fromEvent(taskClaimed("other", "T2"))))
                .toCompletableFuture()
                .join();

        assertEquals(1, poll.resumeCount);
        assertEquals(1, taskManager.listCalls);
        assertEquals(1, host.delivered.size());
        assertTrue(host.delivered.get(0).contains("[T1]"));
        assertTrue(host.delivered.get(0).contains("[T2]"));
        assertFalse(host.delivered.get(0).contains("[T3]"));
    }

    @Test
    void humanAgentGetsSelfClaimNotificationAndIgnoresOtherClaims() {
        RecordingHost host = new RecordingHost();
        RecordingPoll poll = new RecordingPoll();
        RecordingTaskManager taskManager = new RecordingTaskManager();
        taskManager.byId.put("T1", task("T1", TaskStatus.CLAIMED.value(), "human"));
        RecordingBackend backend = new RecordingBackend();
        backend.humanAgents = List.of("human");
        TeamInfra infra = new TeamInfra();
        infra.setTaskManager(taskManager);
        infra.setTeamBackend(backend);
        TaskBoardHandler handler = newHandler(host, TeamRole.HUMAN_AGENT, "human", "temporary", infra, poll);

        handler.onTaskClaimed(transport(EventMessage.fromEvent(taskClaimed("other", "T2"))))
                .toCompletableFuture()
                .join();
        handler.onTaskClaimed(transport(EventMessage.fromEvent(taskClaimed("human", "T1"))))
                .toCompletableFuture()
                .join();

        assertEquals(1, poll.resumeCount);
        assertEquals(1, host.delivered.size());
        assertTrue(host.delivered.get(0).contains("[T1]"));
        assertTrue(host.delivered.get(0).contains("Title T1"));
        assertEquals(1, taskManager.getCalls);
        assertEquals(0, taskManager.listCalls);
    }

    @Test
    void taskPlanDecisionTargetsSelfAndToolCallResponseSkipsExtraInput() {
        RecordingHost host = new RecordingHost();
        RecordingPoll poll = new RecordingPoll();
        TeamInfra infra = new TeamInfra();
        infra.setTaskManager(new RecordingTaskManager());
        TaskBoardHandler handler = newHandler(host, TeamRole.TEAMMATE, "dev", "temporary", infra, poll);

        handler.onTaskPlanDecision(transport(EventMessage.fromEvent(planResponse("dev", "T1", true, "", ""))))
                .toCompletableFuture()
                .join();
        handler.onTaskPlanDecision(transport(EventMessage.fromEvent(planResponse("dev", "T2", false, "revise", "call-1"))))
                .toCompletableFuture()
                .join();

        assertEquals(2, poll.resumeCount);
        assertEquals(1, host.delivered.size());
        assertTrue(host.delivered.get(0).contains("[T1]"));
        assertFalse(host.delivered.get(0).contains("[T2]"));
    }

    @Test
    void leaderNudgeRendersBoardAndAllDonePromptsRespectPollFlag() {
        RecordingHost host = new RecordingHost();
        RecordingTaskManager taskManager = new RecordingTaskManager();
        taskManager.tasks = List.of(task("T1", TaskStatus.PENDING.value(), null));
        TeamInfra infra = new TeamInfra();
        infra.setTaskManager(taskManager);
        TaskBoardHandler handler = newHandler(host, TeamRole.LEADER, "leader", "persistent", infra, new RecordingPoll());

        handler.onTaskBoardEvent(transport(EventMessage.fromEvent(taskCreated("T1"))))
                .toCompletableFuture()
                .join();
        taskManager.tasks = List.of(task("T2", TaskStatus.COMPLETED.value(), "dev"));
        handler.nudgeIdleAgent("leader", true).toCompletableFuture().join();
        handler.nudgeIdleAgent("leader", false).toCompletableFuture().join();

        assertEquals(2, host.delivered.size());
        assertTrue(host.delivered.get(0).contains("[T1]"));
        assertTrue(host.delivered.get(1).contains("所有任务已完成"));
    }

    private static TaskBoardHandler newHandler(
            RecordingHost host,
            TeamRole role,
            String memberName,
            String lifecycle,
            TeamInfra infra,
            RecordingPoll poll
    ) {
        return new TaskBoardHandler(host, blueprint(role, memberName, lifecycle), infra, poll);
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

    private static TaskClaimedEvent taskClaimed(String memberName, String taskId) {
        TaskClaimedEvent event = new TaskClaimedEvent();
        event.setMemberName(memberName);
        event.setTaskId(taskId);
        return event;
    }

    private static TaskCreatedEvent taskCreated(String taskId) {
        TaskCreatedEvent event = new TaskCreatedEvent();
        event.setTaskId(taskId);
        return event;
    }

    private static TaskPlanResponseEvent planResponse(
            String memberName,
            String taskId,
            boolean approved,
            String feedback,
            String toolCallId
    ) {
        TaskPlanResponseEvent event = new TaskPlanResponseEvent();
        event.setMemberName(memberName);
        event.setTaskId(taskId);
        event.setApproved(approved);
        event.setFeedback(feedback);
        event.setToolCallId(toolCallId);
        return event;
    }

    private static TeamTask task(String taskId, String status, String assignee) {
        return new TeamTask(taskId, "team", "Title " + taskId, "Content " + taskId, status, assignee, 123L);
    }

    private static final class RecordingHost implements DispatcherHost {
        private final List<String> delivered = new ArrayList<>();

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
        private int resumeCount;

        @Override
        public CompletionStage<Void> pausePolls() {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Void> resumePolls() {
            resumeCount++;
            return CompletableFuture.completedFuture(null);
        }
    }

    private static final class RecordingTaskManager implements TaskBoardHandler.TaskManager {
        private List<TeamTask> tasks = List.of();
        private final Map<String, TeamTask> byId = new LinkedHashMap<>();
        private int listCalls;
        private int getCalls;

        @Override
        public CompletionStage<List<TeamTask>> listTasks() {
            listCalls++;
            return CompletableFuture.completedFuture(tasks);
        }

        @Override
        public CompletionStage<Optional<TeamTask>> getTask(String taskId) {
            getCalls++;
            return CompletableFuture.completedFuture(Optional.ofNullable(byId.get(taskId)));
        }
    }

    private static final class RecordingBackend extends ConfiguredTeamBackend implements TaskBoardHandler.TeamBackendView {
        private List<String> humanAgents = List.of();

        private RecordingBackend() {
            super("team", "leader", true, Map.of(), null, "", List.of(), null, null, true, false, List.of(), null, null, "leader");
        }

        @Override
        public CompletionStage<Boolean> isHumanAgent(String memberName) {
            return CompletableFuture.completedFuture(humanAgents.contains(memberName));
        }
    }
}

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
import com.openjiuwen.agent_teams.agent.coordination.EventDispatcher.PollController;
import com.openjiuwen.agent_teams.agent.coordination.EventDispatcher.TransportEvent;
import com.openjiuwen.agent_teams.schema.MemberCanceledEvent;
import com.openjiuwen.agent_teams.schema.MemberShutdownEvent;
import com.openjiuwen.agent_teams.schema.MemberStatusChangedEvent;
import com.openjiuwen.agent_teams.schema.TeamEvent;
import com.openjiuwen.agent_teams.schema.events.EventMessage;
import com.openjiuwen.agent_teams.schema.status.MemberStatus;
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
 * Focused parity tests for {@link MemberHandler}.
 *
 * <p>Mirrors Python's member lifecycle handler behavior in
 * {@code openjiuwen/agent_teams/agent/coordination/handlers/member.py}.</p>
 */
class MemberHandlerTest {

    @Test
    void callbackMapPreservesPythonMemberEventOrder() {
        MemberHandler handler = newHandler(new RecordingHost(), TeamRole.LEADER, "leader", new TeamInfra(), new LinkedHashMap<>());

        assertEquals(
                List.of(
                        TeamEvent.MEMBER_SPAWNED,
                        TeamEvent.MEMBER_RESTARTED,
                        TeamEvent.MEMBER_STATUS_CHANGED,
                        TeamEvent.MEMBER_EXECUTION_CHANGED,
                        TeamEvent.MEMBER_SHUTDOWN,
                        TeamEvent.MEMBER_CANCELED
                ),
                handler.getCallbacks().keySet().stream().toList()
        );
    }

    @Test
    void teammateIgnoresMemberEventsForOtherMembersAndCancelsSelf() {
        RecordingHost host = new RecordingHost();
        MemberHandler handler = newHandler(host, TeamRole.TEAMMATE, "dev", new TeamInfra(), new LinkedHashMap<>());

        handler.onMemberEvent(transport(EventMessage.fromEvent(memberCanceled("other")))).toCompletableFuture().join();
        handler.onMemberEvent(transport(EventMessage.fromEvent(memberCanceled("dev")))).toCompletableFuture().join();

        assertEquals(1, host.cancelCount);
    }

    @Test
    void humanShutdownCollapsesWhenIdleOrForcedButNotDuringInFlightRound() {
        RecordingHost host = new RecordingHost();
        host.inFlightRound = true;
        MemberHandler handler = newHandler(host, TeamRole.HUMAN_AGENT, "human", new TeamInfra(), new LinkedHashMap<>());

        handler.onMemberEvent(transport(EventMessage.fromEvent(memberShutdown("human", false))))
                .toCompletableFuture()
                .join();
        handler.onMemberEvent(transport(EventMessage.fromEvent(memberShutdown("human", true))))
                .toCompletableFuture()
                .join();
        host.inFlightRound = false;
        handler.onMemberEvent(transport(EventMessage.fromEvent(memberShutdown("human", false))))
                .toCompletableFuture()
                .join();

        assertEquals(2, host.shutdownCount);
    }

    @Test
    void leaderStatusChangeNudgesOnlyStaleClaimedTasksAndUpdatesThrottle() {
        TeamInfra infra = new TeamInfra();
        RecordingTaskManager taskManager = new RecordingTaskManager();
        RecordingMessageManager messageManager = new RecordingMessageManager();
        long now = System.currentTimeMillis();
        TeamTask stale = new TeamTask("T1", "team", "Old", "finish old", TaskStatus.CLAIMED.value(), "dev", now - 11 * 60 * 1000L);
        TeamTask fresh = new TeamTask("T2", "team", "Fresh", "finish fresh", TaskStatus.CLAIMED.value(), "dev", now - 60 * 1000L);
        TeamTask missingTime = new TeamTask("T3", "team", "NoTime", "finish no-time", TaskStatus.CLAIMED.value(), "dev", null);
        taskManager.tasks = List.of(stale, fresh, missingTime);
        infra.setTaskManager(taskManager);
        infra.setMessageManager(messageManager);
        Map<String, Double> throttle = new LinkedHashMap<>();
        MemberHandler handler = newHandler(new RecordingHost(), TeamRole.LEADER, "leader", infra, throttle);

        handler.onMemberEvent(transport(EventMessage.fromEvent(statusChanged("dev", "busy", MemberStatus.READY.value()))))
                .toCompletableFuture()
                .join();

        assertEquals("dev", taskManager.targetId);
        assertEquals(TaskStatus.CLAIMED.value(), taskManager.status);
        assertEquals(1, messageManager.sent.size());
        assertTrue(messageManager.sent.getFirst().contains("[T1] Old: finish old"));
        assertFalse(messageManager.sent.getFirst().contains("[T2]"));
        assertTrue(throttle.containsKey("T1"));
    }

    @Test
    void leaderNudgeSkipsNonIdleOrUnchangedStatus() {
        TeamInfra infra = new TeamInfra();
        RecordingTaskManager taskManager = new RecordingTaskManager();
        RecordingMessageManager messageManager = new RecordingMessageManager();
        taskManager.tasks = List.of(new TeamTask(
                "T1",
                "team",
                "Old",
                "finish old",
                TaskStatus.CLAIMED.value(),
                "dev",
                System.currentTimeMillis() - 11 * 60 * 1000L
        ));
        infra.setTaskManager(taskManager);
        infra.setMessageManager(messageManager);
        MemberHandler handler = newHandler(new RecordingHost(), TeamRole.LEADER, "leader", infra, new LinkedHashMap<>());

        handler.onMemberEvent(transport(EventMessage.fromEvent(statusChanged("dev", "busy", "busy"))))
                .toCompletableFuture()
                .join();
        handler.onMemberEvent(transport(EventMessage.fromEvent(statusChanged("dev", "busy", "paused"))))
                .toCompletableFuture()
                .join();

        assertEquals(0, messageManager.sent.size());
    }

    @Test
    void staleNudgeThrottleIsSharedByReference() {
        TeamInfra infra = new TeamInfra();
        RecordingTaskManager taskManager = new RecordingTaskManager();
        RecordingMessageManager messageManager = new RecordingMessageManager();
        taskManager.tasks = List.of(new TeamTask(
                "T1",
                "team",
                "Old",
                "finish old",
                TaskStatus.CLAIMED.value(),
                "dev",
                System.currentTimeMillis() - 11 * 60 * 1000L
        ));
        infra.setTaskManager(taskManager);
        infra.setMessageManager(messageManager);
        Map<String, Double> throttle = new LinkedHashMap<>();
        MemberHandler handler = newHandler(new RecordingHost(), TeamRole.LEADER, "leader", infra, throttle);

        handler.nudgeIdleMemberWithStaleClaims("dev", "busy", MemberStatus.READY.value())
                .toCompletableFuture()
                .join();
        handler.nudgeIdleMemberWithStaleClaims("dev", "busy", MemberStatus.READY.value())
                .toCompletableFuture()
                .join();

        assertSame(throttle, handler.getLastStaleNudge());
        assertEquals(1, messageManager.sent.size());
    }

    private static MemberHandler newHandler(
            RecordingHost host,
            TeamRole role,
            String memberName,
            TeamInfra infra,
            Map<String, Double> throttle
    ) {
        return new MemberHandler(host, blueprint(role, memberName), infra, new RecordingPoll(), throttle);
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

    private static TransportEvent transport(EventMessage message) {
        return new TransportEvent(message);
    }

    private static MemberCanceledEvent memberCanceled(String memberName) {
        MemberCanceledEvent event = new MemberCanceledEvent();
        event.setMemberName(memberName);
        return event;
    }

    private static MemberShutdownEvent memberShutdown(String memberName, boolean force) {
        MemberShutdownEvent event = new MemberShutdownEvent();
        event.setMemberName(memberName);
        event.setForce(force);
        return event;
    }

    private static MemberStatusChangedEvent statusChanged(String memberName, String oldStatus, String newStatus) {
        MemberStatusChangedEvent event = new MemberStatusChangedEvent();
        event.setMemberName(memberName);
        event.setOldStatus(oldStatus);
        event.setNewStatus(newStatus);
        return event;
    }

    private static final class RecordingHost implements DispatcherHost {
        private int cancelCount;
        private int shutdownCount;
        private boolean inFlightRound;

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
            return inFlightRound;
        }

        @Override
        public boolean hasPendingInterrupt() {
            return false;
        }

        @Override
        public CompletionStage<Void> cancelAgent() {
            cancelCount++;
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
            shutdownCount++;
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

    private static final class RecordingTaskManager implements MemberHandler.TaskManager {
        private List<TeamTask> tasks = List.of();
        private String targetId;
        private String status;

        @Override
        public CompletionStage<List<TeamTask>> getTasksByAssignee(String targetId, String status) {
            this.targetId = targetId;
            this.status = status;
            return CompletableFuture.completedFuture(tasks);
        }
    }

    private static final class RecordingMessageManager implements MemberHandler.MessageManager {
        private final List<String> sent = new ArrayList<>();

        @Override
        public CompletionStage<Void> sendMessage(String content, String targetId) {
            sent.add(content);
            return CompletableFuture.completedFuture(null);
        }
    }
}

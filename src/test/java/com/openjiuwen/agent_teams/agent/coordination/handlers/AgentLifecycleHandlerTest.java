/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.agent.coordination.handlers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.openjiuwen.agent_teams.agent.AgentConfigurator.AgentCard;
import com.openjiuwen.agent_teams.agent.AgentConfigurator.TeamAgentSpec;
import com.openjiuwen.agent_teams.agent.AgentConfigurator.TeamInfra;
import com.openjiuwen.agent_teams.agent.AgentConfigurator.TeamRole;
import com.openjiuwen.agent_teams.agent.AgentConfigurator.TeamRuntimeContext;
import com.openjiuwen.agent_teams.agent.TeamAgentBlueprint;
import com.openjiuwen.agent_teams.agent.coordination.EventDispatcher.CoordinationEvent;
import com.openjiuwen.agent_teams.agent.coordination.EventDispatcher.DispatcherHost;
import com.openjiuwen.agent_teams.agent.coordination.EventDispatcher.InnerEventMessage;
import com.openjiuwen.agent_teams.agent.coordination.EventDispatcher.InnerEventType;
import com.openjiuwen.agent_teams.agent.coordination.EventDispatcher.PollController;
import com.openjiuwen.agent_teams.agent.coordination.EventDispatcher.TransportEvent;
import com.openjiuwen.agent_teams.schema.TaskPlanResponseEvent;
import com.openjiuwen.agent_teams.schema.TeamEvent;
import com.openjiuwen.agent_teams.schema.ToolApprovalResultEvent;
import com.openjiuwen.agent_teams.schema.events.EventMessage;
import com.openjiuwen.core.session.interaction.InteractiveInput;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.junit.jupiter.api.Test;

/**
 * Focused parity tests for {@link AgentLifecycleHandler}.
 *
 * <p>Mirrors Python's lifecycle handler behavior in
 * {@code openjiuwen/agent_teams/agent/coordination/handlers/agent_lifecycle.py}.</p>
 */
class AgentLifecycleHandlerTest {

    @Test
    void callbackMapPreservesPythonEventOrder() {
        AgentLifecycleHandler handler = newHandler(new RecordingHost(), TeamRole.TEAMMATE, "dev", new RecordingPoll());

        assertEquals(
                List.of(
                        "user_input",
                        TeamEvent.STANDBY,
                        TeamEvent.CLEANED,
                        TeamEvent.TOOL_APPROVAL_RESULT,
                        TeamEvent.TASK_PLAN_RESPONSE
                ),
                handler.getCallbacks().keySet().stream().toList()
        );
    }

    @Test
    void userInputDeliversPayloadContentWithSteerDefault() {
        RecordingHost host = new RecordingHost();
        AgentLifecycleHandler handler = newHandler(host, TeamRole.TEAMMATE, "dev", new RecordingPoll());

        handler.onUserInput(new InnerEventMessage(InnerEventType.USER_INPUT, Map.of("content", "hello")))
                .toCompletableFuture()
                .join();

        assertEquals("hello", host.deliveredContent);
        assertTrue(host.deliveredUseSteer);
    }

    @Test
    void userInputDefaultsMissingContentToEmptyString() {
        RecordingHost host = new RecordingHost();
        AgentLifecycleHandler handler = newHandler(host, TeamRole.TEAMMATE, "dev", new RecordingPoll());

        handler.onUserInput(new InnerEventMessage(InnerEventType.USER_INPUT)).toCompletableFuture().join();

        assertEquals("", host.deliveredContent);
    }

    @Test
    void standbyPausesPolls() {
        RecordingPoll poll = new RecordingPoll();
        AgentLifecycleHandler handler = newHandler(new RecordingHost(), TeamRole.TEAMMATE, "dev", poll);

        handler.onStandby(transport(new EventMessage(TeamEvent.STANDBY, Map.of(), "sender")))
                .toCompletableFuture()
                .join();

        assertEquals(1, poll.pauseCount);
    }

    @Test
    void cleanedIsNoOpForLeaderAndShutdownForNonLeader() {
        RecordingHost leaderHost = new RecordingHost();
        AgentLifecycleHandler leaderHandler = newHandler(leaderHost, TeamRole.LEADER, "leader", new RecordingPoll());
        leaderHandler.onCleaned(transport(new EventMessage(TeamEvent.CLEANED, Map.of(), "sender")))
                .toCompletableFuture()
                .join();

        RecordingHost teammateHost = new RecordingHost();
        AgentLifecycleHandler teammateHandler = newHandler(teammateHost, TeamRole.TEAMMATE, "dev", new RecordingPoll());
        teammateHandler.onCleaned(transport(new EventMessage(TeamEvent.CLEANED, Map.of(), "sender")))
                .toCompletableFuture()
                .join();

        assertEquals(0, leaderHost.shutdownCount);
        assertEquals(1, teammateHost.shutdownCount);
    }

    @Test
    void toolApprovalResultIgnoresNonTargetAndResumesTargetInterrupt() {
        RecordingHost host = new RecordingHost();
        AgentLifecycleHandler handler = newHandler(host, TeamRole.TEAMMATE, "dev", new RecordingPoll());

        handler.onToolApprovalResult(transport(EventMessage.fromEvent(toolApproval("other", "call-1", true))))
                .toCompletableFuture()
                .join();
        handler.onToolApprovalResult(transport(EventMessage.fromEvent(toolApproval("dev", "call-2", false))))
                .toCompletableFuture()
                .join();

        assertEquals(1, host.resumeCount);
        InteractiveInput input = assertInstanceOf(InteractiveInput.class, host.resumedInput);
        assertTrue(input.getUserInputs().containsKey("call-2"));
        Map<?, ?> approval = assertInstanceOf(Map.class, input.getUserInputs().get("call-2"));
        assertEquals(false, approval.get("approved"));
        assertEquals("feedback", approval.get("feedback"));
        assertEquals(true, approval.get("auto_confirm"));
    }

    @Test
    void taskPlanResponseRequiresTargetAndToolCallBeforeResume() {
        RecordingHost host = new RecordingHost();
        AgentLifecycleHandler handler = newHandler(host, TeamRole.TEAMMATE, "dev", new RecordingPoll());

        handler.onTaskPlanResponse(transport(EventMessage.fromEvent(taskPlanResponse("dev", "", "plan-a", true))))
                .toCompletableFuture()
                .join();
        handler.onTaskPlanResponse(transport(EventMessage.fromEvent(taskPlanResponse("other", "call-x", "plan-b", true))))
                .toCompletableFuture()
                .join();
        handler.onTaskPlanResponse(transport(EventMessage.fromEvent(taskPlanResponse("dev", "call-y", null, false))))
                .toCompletableFuture()
                .join();

        assertEquals(1, host.resumeCount);
        InteractiveInput input = assertInstanceOf(InteractiveInput.class, host.resumedInput);
        Map<?, ?> plan = assertInstanceOf(Map.class, input.getUserInputs().get("call-y"));
        assertEquals(false, plan.get("approved"));
        assertEquals("plan feedback", plan.get("feedback"));
        assertEquals("", plan.get("plan_id"));
    }

    @Test
    void constructorKeepsInfraReference() {
        TeamInfra infra = new TeamInfra();
        AgentLifecycleHandler handler = new AgentLifecycleHandler(
                new RecordingHost(),
                blueprint(TeamRole.TEAMMATE, "dev"),
                infra,
                new RecordingPoll()
        );

        assertSame(infra, handler.getInfra());
    }

    private static AgentLifecycleHandler newHandler(
            RecordingHost host,
            TeamRole role,
            String memberName,
            RecordingPoll poll
    ) {
        return new AgentLifecycleHandler(host, blueprint(role, memberName), new TeamInfra(), poll);
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

    private static CoordinationEvent transport(EventMessage message) {
        return new TransportEvent(message);
    }

    private static ToolApprovalResultEvent toolApproval(String memberName, String toolCallId, boolean approved) {
        ToolApprovalResultEvent event = new ToolApprovalResultEvent();
        event.setMemberName(memberName);
        event.setToolCallId(toolCallId);
        event.setApproved(approved);
        event.setFeedback("feedback");
        event.setAutoConfirm(true);
        return event;
    }

    private static TaskPlanResponseEvent taskPlanResponse(
            String memberName,
            String toolCallId,
            String planId,
            boolean approved
    ) {
        TaskPlanResponseEvent event = new TaskPlanResponseEvent();
        event.setMemberName(memberName);
        event.setToolCallId(toolCallId);
        event.setPlanId(planId);
        event.setApproved(approved);
        event.setFeedback("plan feedback");
        return event;
    }

    private static final class RecordingHost implements DispatcherHost {
        private Object deliveredContent;
        private boolean deliveredUseSteer;
        private int shutdownCount;
        private int resumeCount;
        private Object resumedInput;

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
            deliveredContent = content;
            deliveredUseSteer = useSteer;
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Void> resumeInterrupt(Object userInput) {
            resumeCount++;
            resumedInput = userInput;
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
        private int pauseCount;

        @Override
        public CompletionStage<Void> pausePolls() {
            pauseCount++;
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Void> resumePolls() {
            return CompletableFuture.completedFuture(null);
        }
    }
}

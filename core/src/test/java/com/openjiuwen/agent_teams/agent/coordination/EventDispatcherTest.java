/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.agent.coordination;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import com.openjiuwen.agent_teams.schema.TeamEvent;
import com.openjiuwen.agent_teams.schema.events.EventMessage;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.junit.jupiter.api.Test;

/**
 * Focused parity tests for {@link EventDispatcher}.
 *
 * <p>Mirrors Python's dispatcher trigger rules and stable callback fan-out in
 * {@code openjiuwen/agent_teams/agent/coordination/dispatcher.py}.</p>
 */
class EventDispatcherTest {

    @Test
    void notReadyHostSkipsAllCallbacks() {
        RecordingHost host = new RecordingHost(false);
        EventDispatcher dispatcher = newDispatcher(host, TeamRole.TEAMMATE, "dev");

        dispatcher.dispatch(new InnerEventMessage(InnerEventType.USER_INPUT, Map.of("content", "hello")))
                .toCompletableFuture()
                .join();

        assertTrue(dispatcher.getCallbackTrace().isEmpty());
        assertTrue(dispatcher.getLifecycle().getHandledEvents().isEmpty());
    }

    @Test
    void innerUserInputTriggersLifecycleCallback() {
        EventDispatcher dispatcher = newDispatcher(new RecordingHost(true), TeamRole.LEADER, "leader");

        dispatcher.dispatch(new InnerEventMessage(InnerEventType.USER_INPUT, Map.of("content", "hello")))
                .toCompletableFuture()
                .join();

        assertEquals(List.of("AgentLifecycleHandler.on_user_input"), dispatcher.getCallbackTrace());
        assertEquals("user_input", dispatcher.getLifecycle().getHandledEvents().get(0).eventKey());
    }

    @Test
    void humanAgentMutesInnerPollEvents() {
        EventDispatcher dispatcher = newDispatcher(new RecordingHost(true), TeamRole.HUMAN_AGENT, "human");

        dispatcher.dispatch(new InnerEventMessage(InnerEventType.POLL_TASK)).toCompletableFuture().join();
        dispatcher.dispatch(new InnerEventMessage(InnerEventType.POLL_MAILBOX)).toCompletableFuture().join();

        assertTrue(dispatcher.getCallbackTrace().isEmpty());
        assertTrue(dispatcher.getStaleTask().getHandledEvents().isEmpty());
        assertTrue(dispatcher.getMessage().getHandledEvents().isEmpty());
    }

    @Test
    void transportEventWithoutMemberNameIsSkipped() {
        EventDispatcher dispatcher = newDispatcher(new RecordingHost(true), TeamRole.TEAMMATE, null);

        dispatcher.dispatch(eventMessage(TeamEvent.MESSAGE)).toCompletableFuture().join();

        assertTrue(dispatcher.getCallbackTrace().isEmpty());
    }

    @Test
    void humanTransportWhitelistAllowsOnlyAvatarRelevantEvents() {
        EventDispatcher dispatcher = newDispatcher(new RecordingHost(true), TeamRole.HUMAN_AGENT, "human");

        dispatcher.dispatch(eventMessage(TeamEvent.TASK_CLAIMED)).toCompletableFuture().join();
        dispatcher.dispatch(eventMessage(TeamEvent.TASK_CREATED)).toCompletableFuture().join();
        dispatcher.dispatch(eventMessage(TeamEvent.MESSAGE)).toCompletableFuture().join();

        assertEquals(
                List.of(
                        "TaskBoardHandler.on_task_claimed",
                        "MessageHandler.on_message_or_broadcast"
                ),
                dispatcher.getCallbackTrace()
        );
    }

    @Test
    void sharedMemberShutdownKeyKeepsMemberBeforeMessageOrder() {
        EventDispatcher dispatcher = newDispatcher(new RecordingHost(true), TeamRole.TEAMMATE, "dev");

        dispatcher.dispatch(eventMessage(TeamEvent.MEMBER_SHUTDOWN)).toCompletableFuture().join();

        assertEquals(
                List.of(
                        "MemberHandler.on_member_event",
                        "MessageHandler.on_member_shutdown_drain"
                ),
                dispatcher.getCallbackTrace()
        );
    }

    @Test
    void sharedPollTaskKeyKeepsStaleSweepBeforeTeamCompletionOrder() {
        EventDispatcher dispatcher = newDispatcher(new RecordingHost(true), TeamRole.TEAMMATE, "dev");

        dispatcher.dispatch(new InnerEventMessage(InnerEventType.POLL_TASK)).toCompletableFuture().join();

        assertEquals(
                List.of(
                        "StaleTaskHandler.on_poll_task",
                        "TeamCompletionHandler.on_poll_task"
                ),
                dispatcher.getCallbackTrace()
        );
    }

    @Test
    void staleClaimThrottleIsSharedByMemberAndStaleHandlers() {
        EventDispatcher dispatcher = newDispatcher(new RecordingHost(true), TeamRole.TEAMMATE, "dev");

        assertSame(dispatcher.getMember().getStaleClaimThrottle(), dispatcher.getStaleTask().getStaleClaimThrottle());
    }

    private static EventDispatcher newDispatcher(RecordingHost host, TeamRole role, String memberName) {
        return new EventDispatcher(
                host,
                blueprint(role, memberName),
                new TeamInfra(),
                new RecordingPollController()
        );
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

    private static EventMessage eventMessage(String eventType) {
        return new EventMessage(eventType, new LinkedHashMap<>(), "sender");
    }

    private static final class RecordingHost implements DispatcherHost {
        private final boolean ready;

        private RecordingHost(boolean ready) {
            this.ready = ready;
        }

        @Override
        public boolean isAgentReady() {
            return ready;
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

    private static final class RecordingPollController implements PollController {
        @Override
        public CompletionStage<Void> pausePolls() {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Void> resumePolls() {
            return CompletableFuture.completedFuture(null);
        }
    }
}

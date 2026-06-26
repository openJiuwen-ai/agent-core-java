/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.agent.coordination.handlers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.openjiuwen.agent_teams.agent.AgentConfigurator.AgentCard;
import com.openjiuwen.agent_teams.agent.AgentConfigurator.TeamAgentSpec;
import com.openjiuwen.agent_teams.agent.AgentConfigurator.TeamInfra;
import com.openjiuwen.agent_teams.agent.AgentConfigurator.TeamRuntimeContext;
import com.openjiuwen.agent_teams.agent.TeamAgentBlueprint;
import com.openjiuwen.agent_teams.agent.coordination.EventDispatcher.CoordinationEvent;
import com.openjiuwen.agent_teams.agent.coordination.EventDispatcher.DispatcherHost;
import com.openjiuwen.agent_teams.agent.coordination.EventDispatcher.InnerEventMessage;
import com.openjiuwen.agent_teams.agent.coordination.EventDispatcher.InnerEventType;
import com.openjiuwen.agent_teams.agent.coordination.EventDispatcher.PollController;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.junit.jupiter.api.Test;

/**
 * Focused parity tests for {@link BaseCoordinationHandler}.
 *
 * <p>Mirrors Python's base handler callback map behavior in
 * {@code openjiuwen/agent_teams/agent/coordination/handlers/base.py}.</p>
 */
class BaseCoordinationHandlerTest {

    @Test
    void constructorAliasesHostAndStoresSharedDependencies() {
        RecordingHost host = new RecordingHost();
        TeamAgentBlueprint blueprint = blueprint();
        TeamInfra infra = new TeamInfra();
        RecordingPoll poll = new RecordingPoll();

        TestHandler handler = new TestHandler(host, blueprint, infra, poll);

        assertSame(host, handler.getRound());
        assertSame(host, handler.getLifecycle());
        assertSame(blueprint, handler.getBlueprint());
        assertSame(infra, handler.getInfra());
        assertSame(poll, handler.getPoll());
    }

    @Test
    void getCallbacksPreservesEventMethodMapOrderAndBindsMethods() {
        TestHandler handler = new TestHandler(new RecordingHost(), blueprint(), new TeamInfra(), new RecordingPoll());
        Map<String, EventCallback> callbacks = handler.getCallbacks();

        assertEquals(List.of("first", "second"), callbacks.keySet().stream().toList());
        callbacks.get("first").handle(new InnerEventMessage(InnerEventType.USER_INPUT)).toCompletableFuture().join();
        callbacks.get("second").handle(new InnerEventMessage(InnerEventType.POLL_TASK)).toCompletableFuture().join();

        assertEquals(List.of("on_first:user_input", "on_second:coordination_poll_task"), handler.invocations);
    }

    private static TeamAgentBlueprint blueprint() {
        return new TeamAgentBlueprint(
                new AgentCard("agent", "Agent", "description"),
                new TeamAgentSpec(),
                new TeamRuntimeContext(),
                "",
                "cn"
        );
    }

    private static final class TestHandler extends BaseCoordinationHandler {
        private final List<String> invocations = new java.util.ArrayList<>();

        private TestHandler(
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
            map.put("first", "onFirst");
            map.put("second", "onSecond");
            return map;
        }

        @Override
        protected EventCallback resolveCallback(String methodName) {
            return switch (methodName) {
                case "onFirst" -> this::onFirst;
                case "onSecond" -> this::onSecond;
                default -> throw new IllegalArgumentException("Unknown method: " + methodName);
            };
        }

        private CompletionStage<Void> onFirst(CoordinationEvent event) {
            invocations.add("on_first:" + event.eventKey());
            return CompletableFuture.completedFuture(null);
        }

        private CompletionStage<Void> onSecond(CoordinationEvent event) {
            invocations.add("on_second:" + event.eventKey());
            return CompletableFuture.completedFuture(null);
        }
    }

    private static final class RecordingHost implements DispatcherHost {
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
}

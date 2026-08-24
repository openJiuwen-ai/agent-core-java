/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.multiagent.teams.handoff;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.multiagent.schema.TeamCard;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.session.AgentTeamSession;
import com.openjiuwen.core.session.stream.StreamMode;
import com.openjiuwen.core.singleagent.BaseAgent;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Focused parity tests for the event-driven handoff team.
 *
 * <p>Mirrors Python's {@code HandoffTeam} in
 * {@code openjiuwen/core/multi_agent/teams/handoff/handoff_team.py}.</p>
 */
class HandoffTeamTest {

    @Test
    void invokeRegistersInternalEndpointAndReturnsFinalResult() {
        HandoffTeam team = new HandoffTeam(teamCard());
        StubAgent agent = new StubAgent(agentCard("a"), Map.of("answer", "done"));
        team.addAgent(agentCard("a"), ignored -> agent);

        Object result = team.invoke("hello").toCompletableFuture().join();

        assertThat(result).isEqualTo(Map.of("answer", "done"));
        assertThat(agent.getLastInput()).isEqualTo("hello");
        assertThat(team.getRuntime().hasAgent("__handoff_ep_team-id_a")).isTrue();
        assertThat(team.getRuntime().listSubscriptions("__handoff_ep_team-id_a"))
                .containsEntry("agent_id", "__handoff_ep_team-id_a");
        assertThat(team.lookupCoordinator(agent.getLastSessionId())).isNull();
    }

    @Test
    void invokeUsesConfiguredStartAgentAndFiltersInterruptedResumeHistory() {
        AgentCard startAgent = agentCard("b");
        HandoffConfig handoffConfig = new HandoffConfig();
        handoffConfig.setStartAgent(startAgent);
        HandoffTeam team = new HandoffTeam(teamCard(), new HandoffTeamConfig(handoffConfig));
        StubAgent skipped = new StubAgent(agentCard("a"), Map.of("unexpected", true));
        StubAgent target = new StubAgent(startAgent, Map.of("answer", "resumed"));
        team.addAgent(agentCard("a"), ignored -> skipped);
        team.addAgent(startAgent, ignored -> target);

        AgentTeamSession session = new AgentTeamSession("resume-session", null, "team-id");
        Map<String, Object> keepHistory = new LinkedHashMap<>();
        keepHistory.put("agent", "a");
        keepHistory.put("output", Map.of("content", "keep"));
        session.updateState(Map.of(
                HandoffOrchestrator.COORDINATOR_STATE_KEY,
                Map.of("current_agent_id", "b", "handoff_count", 1),
                HandoffOrchestrator.HANDOFF_HISTORY_KEY,
                List.of(
                        Map.of("agent", "a", "output", Map.of("result_type", "interrupt")),
                        keepHistory
                )
        ));

        Object result = team.invoke("resume", session).toCompletableFuture().join();

        assertThat(result).isEqualTo(Map.of("answer", "resumed"));
        assertThat(skipped.getLastInput()).isNull();
        assertThat(target.getLastInput()).isInstanceOf(Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> input = (Map<String, Object>) target.getLastInput();
        assertThat(input).containsEntry("query", "resume");
        assertThat(input.get("handoff_history")).isEqualTo(List.of(keepHistory));
    }

    @Test
    void streamYieldsTargetResultWrittenToTeamSession() {
        HandoffTeam team = new HandoffTeam(teamCard());
        team.addAgent(agentCard("a"), ignored -> new StubAgent(agentCard("a"), Map.of("stream", "chunk")));

        Stream<Object> stream = team.stream("hello");

        assertThat(stream.toList()).hasSize(1);
    }

    @Test
    @Timeout(10)
    void timeoutRaisesStructuredTeamExecutionError() {
        HandoffTeamConfig config = new HandoffTeamConfig();
        config.setMessageTimeout(0.01);
        HandoffTeam team = new HandoffTeam(teamCard(), config);
        team.addAgent(agentCard("a"), ignored -> new HangingAgent(agentCard("a")));

        assertThatThrownBy(() -> team.invoke("hello").toCompletableFuture().join())
                .isInstanceOf(CompletionException.class)
                .hasCauseInstanceOf(BaseError.class)
                .hasMessageContaining("handoff chain timeout");
    }

    private static TeamCard teamCard() {
        return new TeamCard("team-id", "team-name", "team");
    }

    private static AgentCard agentCard(String id) {
        return new AgentCard(id, id, "agent " + id);
    }

    private static final class StubAgent extends BaseAgent {
        private final Object result;
        private Object lastInput;
        private String lastSessionId;

        private StubAgent(AgentCard card, Object result) {
            super(card);
            this.result = result;
        }

        @Override
        public BaseAgent configure(Object config) {
            return this;
        }

        @Override
        public CompletionStage<Object> invoke(Object inputs, AgentSessionApi session) {
            this.lastInput = inputs;
            this.lastSessionId = session == null ? "" : session.getSessionId();
            return CompletableFuture.completedFuture(result);
        }

        @Override
        public Iterator<Object> stream(Object inputs, AgentSessionApi session, List<StreamMode> streamModes) {
            return List.of(result).iterator();
        }

        private Object getLastInput() {
            return lastInput;
        }

        private String getLastSessionId() {
            return lastSessionId;
        }
    }

    private static final class HangingAgent extends BaseAgent {
        private HangingAgent(AgentCard card) {
            super(card);
        }

        @Override
        public BaseAgent configure(Object config) {
            return this;
        }

        @Override
        public CompletionStage<Object> invoke(Object inputs, AgentSessionApi session) {
            return new CompletableFuture<>();
        }

        @Override
        public Iterator<Object> stream(Object inputs, AgentSessionApi session, List<StreamMode> streamModes) {
            return List.of().iterator();
        }
    }
}

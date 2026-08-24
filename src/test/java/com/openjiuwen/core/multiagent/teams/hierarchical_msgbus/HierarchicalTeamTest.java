/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.multiagent.teams.hierarchical_msgbus;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.multiagent.schema.TeamCard;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.session.stream.StreamMode;
import com.openjiuwen.core.singleagent.BaseAgent;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import org.junit.jupiter.api.Test;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Focused parity tests for the message-bus hierarchical team.
 *
 * <p>Mirrors Python's {@code HierarchicalTeam} in
 * {@code openjiuwen/core/multi_agent/teams/hierarchical_msgbus/hierarchical_team.py}.</p>
 */
class HierarchicalTeamTest {

    private static final AgentCard SUPERVISOR_CARD = new AgentCard(
            "hierarchical-msgbus-supervisor-test",
            "supervisor",
            "Supervisor agent"
    );

    @Test
    void invokeRejectsMissingSupervisorConfig() {
        HierarchicalTeam team = new HierarchicalTeam(
                new TeamCard("team-id", "team-name", ""),
                new HierarchicalTeamConfig()
        );

        assertThatThrownBy(() -> team.invoke("hello").toCompletableFuture().join())
                .isInstanceOf(BaseError.class)
                .hasMessageContaining("No supervisor configured in HierarchicalTeamConfig");
    }

    @Test
    void addAgentRegistersSupervisorAndAppliesP2pTimeout() {
        RecordingAgent supervisor = new RecordingAgent(SUPERVISOR_CARD);
        HierarchicalTeam team = newTeam(supervisor, 2.5);

        assertThat(team.getSupervisorId()).isEqualTo(SUPERVISOR_CARD.getId());
        assertThat(team.getRuntime().getP2pTimeout()).isEqualTo(2.5);
        assertThat(team.invoke("payload").toCompletableFuture().join()).isEqualTo("supervised:payload");
        assertThat(supervisor.lastInputs.get()).isEqualTo("payload");
    }

    @Test
    void streamWritesFinalSupervisorResult() {
        RecordingAgent supervisor = new RecordingAgent(SUPERVISOR_CARD);
        HierarchicalTeam team = newTeam(supervisor, 1800.0);

        List<Object> chunks = team.stream(Map.of("query", "hello")).toList();

        assertThat(chunks).isNotEmpty();
        assertThat(supervisor.lastInputs.get()).isEqualTo(Map.of("query", "hello"));
    }

    private HierarchicalTeam newTeam(RecordingAgent supervisor, Double timeout) {
        HierarchicalTeam team = new HierarchicalTeam(
                new TeamCard("team-id", "team-name", ""),
                new HierarchicalTeamConfig(SUPERVISOR_CARD, timeout)
        );
        team.addAgent(SUPERVISOR_CARD, ignored -> supervisor);
        return team;
    }

    private static final class RecordingAgent extends BaseAgent {
        private final AtomicReference<Object> lastInputs = new AtomicReference<>();

        private RecordingAgent(AgentCard card) {
            super(card);
        }

        @Override
        public BaseAgent configure(Object config) {
            return this;
        }

        @Override
        public CompletionStage<Object> invoke(Object inputs, AgentSessionApi session) {
            lastInputs.set(inputs);
            return CompletableFuture.completedFuture("supervised:" + inputs);
        }

        @Override
        public Iterator<Object> stream(Object inputs, AgentSessionApi session, List<StreamMode> streamModes) {
            lastInputs.set(inputs);
            return List.<Object>of(Map.of("output", "stream:" + inputs, "result_type", "answer")).iterator();
        }
    }
}

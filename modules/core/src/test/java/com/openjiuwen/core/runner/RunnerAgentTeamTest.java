/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner;

import static org.assertj.core.api.Assertions.assertThat;

import com.openjiuwen.agentteams.TeamConstants;
import com.openjiuwen.agentteams.agent.TeamAgent;
import com.openjiuwen.agentteams.messager.InProcessMessager;
import com.openjiuwen.agentteams.schema.blueprint.TeamAgentSpec;
import com.openjiuwen.agentteams.schema.team.TeamLifecycle;
import com.openjiuwen.agentteams.schema.team.TeamMemberSpec;
import com.openjiuwen.agentteams.schema.team.TeamRole;
import com.openjiuwen.agentteams.schema.team.TeamRuntimeContext;
import com.openjiuwen.agentteams.tools.TeamBackend;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

class RunnerAgentTeamTest {
    private final List<TeamAgent> configuredAgents = new ArrayList<>();

    @AfterEach
    void cleanup() {
        configuredAgents.forEach(TeamAgent::close);
        InProcessMessager.cleanupInprocessBus();
        TeamBackend.resetSharedDbCache();
    }

    @Test
    void temporaryTeamShouldStreamAndFinalize() {
        RunnerImpl runner = new RunnerImpl("temporary-team-runner", RunnerConfig.DEFAULT);
        TeamAgent agent = createStreamAgent("temporary-team", "temporary");
        Map<String, Object> inputs = Map.of("query", "coordinate");

        Iterator<Object> stream = runner.runAgentTeamStreaming(agent, inputs, "session-1", null, null);

        assertThat(stream.next()).isEqualTo("coordinate");
        assertThat(stream.hasNext()).isFalse();
        assertThat(agent.getContext().getLifecycle()).isEqualTo(TeamLifecycle.COMPLETED);
    }

    @Test
    void persistentTeamShouldResumeByRegisteredName() {
        RunnerImpl runner = new RunnerImpl("persistent-team-runner", RunnerConfig.DEFAULT);
        TeamAgent agent = createStreamAgent("persistent-team", "persistent");
        Map<String, Object> firstInputs = Map.of("query", "first");
        Map<String, Object> secondInputs = Map.of("query", "continue");

        Iterator<Object> first = runner.runAgentTeamStreaming(
                agent, firstInputs, "persistent-session", null, null);
        assertThat(first.next()).isEqualTo("first");
        assertThat(first.hasNext()).isFalse();
        assertThat(agent.getContext().getLifecycle()).isEqualTo(TeamLifecycle.PAUSED);

        Iterator<Object> second = runner.runAgentTeamStreaming(
                "persistent-team", secondInputs, "persistent-session", null, null);
        assertThat(second.next()).isEqualTo("continue");
        assertThat(second.hasNext()).isFalse();
        assertThat(agent.getContext().getLifecycle()).isEqualTo(TeamLifecycle.PAUSED);

        assertThat(runner.destroyAgentTeam("persistent-team", true)).isTrue();
    }

    private TeamAgent createStreamAgent(String name, String lifecycle) {
        TeamAgentSpec spec = TeamAgentSpec.builder()
                .name(name)
                .lifecycle(lifecycle)
                .spawnMode("inprocess")
                .transport("inprocess")
                .storage("memory")
                .members(List.of(TeamMemberSpec.builder()
                        .name(TeamConstants.DEFAULT_LEADER_MEMBER_NAME)
                        .role(TeamRole.LEADER)
                        .build()))
                .build();
        TeamRuntimeContext context = TeamRuntimeContext.builder()
                .teamId(name)
                .memberName(TeamConstants.DEFAULT_LEADER_MEMBER_NAME)
                .role(TeamRole.LEADER)
                .build();
        TeamAgent agent = new StaticStreamTeamAgent().configure(spec, context);
        configuredAgents.add(agent);
        return agent;
    }

    private static final class StaticStreamTeamAgent extends TeamAgent {
        @Override
        public Iterator<Object> stream(Map<String, Object> inputs, Object session) {
            return List.of(inputs.get("query")).iterator();
        }
    }
}

/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentteams;

import static org.assertj.core.api.Assertions.assertThat;

import com.openjiuwen.agentteams.agent.TeamAgent;
import com.openjiuwen.agentteams.schema.blueprint.TeamAgentSpec;
import com.openjiuwen.agentteams.schema.team.ModelPoolEntry;
import com.openjiuwen.agentteams.schema.team.TeamMemberSpec;
import com.openjiuwen.agentteams.schema.team.TeamRole;

import org.junit.jupiter.api.Test;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

class LeaderTeammateAgentTeamTest {
    private int factoryInvocationCount;

    @Test
    void builderShouldPropagateModelPoolConfiguration() {
        ModelPoolEntry model = ModelPoolEntry.builder().modelId("leader-model").provider("openai")
                .modelName("mock-model").build();
        ModelPoolEntry replacement = ModelPoolEntry.builder().modelId("replacement-model").provider("openai")
                .modelName("replacement-model").build();
        LeaderTeammateAgentTeam.Builder builder = LeaderTeammateAgentTeam.builder().teamName("configured-team")
                .modelPool(List.of(model)).modelPoolStrategy("by_model_name");

        LeaderTeammateAgentTeam team = builder.build();
        builder.modelPool(List.of(replacement));

        assertThat(team.spec().getModelPool()).containsExactly(model);
        assertThat(team.spec().getModelPoolStrategy()).isEqualTo("by_model_name");
    }

    @Test
    void streamShouldLazilyBuildAndReuseRuntimeAgent() {
        TeamAgentSpec spec = TeamAgentSpec.builder().name("lazy-team")
                .members(List.of(TeamMemberSpec.builder().name("leader").role(TeamRole.LEADER).build())).build();
        Iterator<Object> chunks = List.<Object>of("chunk").iterator();
        RecordingTeamAgent runtimeAgent = new RecordingTeamAgent(chunks);
        LeaderTeammateAgentTeam team = new LeaderTeammateAgentTeam(spec, ignored -> {
            factoryInvocationCount++;
            return runtimeAgent;
        });
        Map<String, Object> inputs = Map.of("query", "coordinate");
        Object session = new Object();

        assertThat(team.isBuilt()).isFalse();
        assertThat(team.stream(inputs, session)).isSameAs(chunks);
        assertThat(team.isBuilt()).isTrue();
        assertThat(team.agent()).isSameAs(runtimeAgent);

        team.build();

        assertThat(factoryInvocationCount).isOne();
        assertThat(runtimeAgent.streamInvocationCount).isOne();
        assertThat(runtimeAgent.receivedInputs).isSameAs(inputs);
        assertThat(runtimeAgent.receivedSession).isSameAs(session);
    }

    private static final class RecordingTeamAgent extends TeamAgent {
        private final Iterator<Object> chunks;
        private Map<String, Object> receivedInputs;
        private Object receivedSession;
        private int streamInvocationCount;

        private RecordingTeamAgent(Iterator<Object> chunks) {
            this.chunks = chunks;
        }

        @Override
        public Iterator<Object> stream(Map<String, Object> streamInputs, Object streamSession) {
            receivedInputs = streamInputs;
            receivedSession = streamSession;
            streamInvocationCount++;
            return chunks;
        }
    }
}

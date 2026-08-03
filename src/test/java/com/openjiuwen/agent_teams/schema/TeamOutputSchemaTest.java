/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.schema;

import static org.assertj.core.api.Assertions.assertThat;

import com.openjiuwen.agent_teams.agent.AgentConfigurator.TeamRole;
import com.openjiuwen.core.session.stream.OutputSchema;

import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Focused parity tests for team-layer output stream chunks.
 *
 * <p>Mirrors Python's {@code TeamOutputSchema} tests for
 * {@code openjiuwen/agent_teams/schema/stream.py}.</p>
 */
class TeamOutputSchemaTest {

    @Test
    void fromOutputCopiesBaseFieldsAndAddsTeamSource() {
        OutputSchema base = new OutputSchema("message", 3, Map.of("text", "hello"));

        TeamOutputSchema team = TeamOutputSchema.fromOutput(base, "leader", TeamRole.LEADER);

        assertThat(team).isNotSameAs(base);
        assertThat(team.getType()).isEqualTo("message");
        assertThat(team.getIndex()).isEqualTo(3);
        assertThat(team.getPayload()).isEqualTo(Map.of("text", "hello"));
        assertThat(team.getSourceMember()).isEqualTo("leader");
        assertThat(team.getRole()).isEqualTo(TeamRole.LEADER);
    }

    @Test
    void fromOutputDoesNotMutateOriginalChunk() {
        OutputSchema base = new OutputSchema("message", 1, Map.of("text", "plain"));

        TeamOutputSchema.fromOutput(base, "worker", TeamRole.TEAMMATE);

        assertThat(base).isNotInstanceOf(TeamOutputSchema.class);
        assertThat(base.getType()).isEqualTo("message");
        assertThat(base.getIndex()).isEqualTo(1);
        assertThat(base.getPayload()).isEqualTo(Map.of("text", "plain"));
    }

    @Test
    void sourceAndRoleMayRemainNullForPlainUpstreamProducers() {
        TeamOutputSchema team = TeamOutputSchema.fromOutput(
                new OutputSchema("llm_output", 0, "chunk"),
                null,
                null
        );

        assertThat(team.getSourceMember()).isNull();
        assertThat(team.getRole()).isNull();
    }
}

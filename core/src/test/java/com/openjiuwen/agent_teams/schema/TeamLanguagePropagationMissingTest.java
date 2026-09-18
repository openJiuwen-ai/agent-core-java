/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.schema;

import static org.assertj.core.api.Assertions.assertThat;

import com.openjiuwen.agent_teams.agent.TeamAgent;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Supplemental parity tests for language propagation through {@link TeamAgentSpec#build()}.
 *
 * <p>Mirrors Python's {@code tests.unit_tests.agent_teams.test_language_propagation} in
 * {@code tests/unit_tests/agent_teams/test_language_propagation.py}.</p>
 */
class TeamLanguagePropagationMissingTest {

    @Test
    void languageEnPropagatesToTeamSpec() {
        TeamAgentSpec spec = specWithLanguage("en", new DeepAgentSpec());

        TeamAgent agent = spec.build();

        assertThat(agent.getRuntimeContext().getTeamSpec().getLanguage()).isEqualTo("en");
    }

    @Test
    void languageNoneFallsBackToCn() {
        TeamAgentSpec spec = specWithLanguage(null, new DeepAgentSpec());

        TeamAgent agent = spec.build();

        assertThat(agent.getRuntimeContext().getTeamSpec().getLanguage()).isEqualTo("cn");
    }

    @Test
    void languageZhNormalizesToCn() {
        TeamAgentSpec spec = specWithLanguage("zh", new DeepAgentSpec());

        TeamAgent agent = spec.build();

        assertThat(agent.getRuntimeContext().getTeamSpec().getLanguage()).isEqualTo("cn");
    }

    @Test
    void languagePropagatesToDeepAgentSpec() {
        DeepAgentSpec leader = new DeepAgentSpec();
        TeamAgentSpec spec = specWithLanguage("en", leader);

        spec.build();

        assertThat(leader.getLanguage()).isEqualTo("en");
        assertThat(spec.getAgents().get("leader").getLanguage()).isEqualTo("en");
    }

    @Test
    void perRoleLanguageOverridePreserved() {
        DeepAgentSpec leader = new DeepAgentSpec();
        leader.setLanguage("cn");
        TeamAgentSpec spec = specWithLanguage("en", leader);

        spec.build();

        assertThat(leader.getLanguage()).isEqualTo("cn");
        assertThat(spec.getAgents().get("leader").getLanguage()).isEqualTo("cn");
    }

    private static TeamAgentSpec specWithLanguage(String language, DeepAgentSpec leader) {
        TeamAgentSpec spec = new TeamAgentSpec();
        spec.setTeamName("agent_team");
        spec.setAgents(Map.of("leader", leader));
        spec.setLanguage(language);
        return spec;
    }
}

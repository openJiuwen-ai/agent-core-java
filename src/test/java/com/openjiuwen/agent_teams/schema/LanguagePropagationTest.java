package com.openjiuwen.agent_teams.schema;

import com.openjiuwen.agent_teams.agent.TeamAgent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Mirrors Python's {@code tests.unit_tests.agent_teams.test_language_propagation}.
 */
class LanguagePropagationTest {

    @Test
    void testLanguageEnPropagatesToTeamSpec() {
        TeamAgentSpec spec = specWithLeader();
        spec.setLanguage("en");

        TeamAgent agent = spec.build();

        assertEquals("en", agent.getRuntimeContext().getTeamSpec().getLanguage());
    }

    @Test
    void testLanguageNoneFallsBackToCn() {
        TeamAgentSpec spec = specWithLeader();

        TeamAgent agent = spec.build();

        assertEquals("cn", agent.getRuntimeContext().getTeamSpec().getLanguage());
    }

    @Test
    void testLanguageZhNormalizesToCn() {
        TeamAgentSpec spec = specWithLeader();
        spec.setLanguage("zh");

        TeamAgent agent = spec.build();

        assertEquals("cn", agent.getRuntimeContext().getTeamSpec().getLanguage());
    }

    @Test
    void testLanguagePropagatesToDeepAgentSpec() {
        TeamAgentSpec spec = specWithLeader();
        spec.setLanguage("en");

        spec.build();

        assertEquals("en", spec.getAgents().get("leader").getLanguage());
    }

    @Test
    void testPerRoleLanguageOverridePreserved() {
        TeamAgentSpec spec = specWithLeader();
        spec.setLanguage("en");
        spec.getAgents().get("leader").setLanguage("cn");

        spec.build();

        assertEquals("cn", spec.getAgents().get("leader").getLanguage());
    }

    private static TeamAgentSpec specWithLeader() {
        TeamAgentSpec spec = new TeamAgentSpec();
        spec.getAgents().put("leader", new DeepAgentSpec());
        return spec;
    }
}

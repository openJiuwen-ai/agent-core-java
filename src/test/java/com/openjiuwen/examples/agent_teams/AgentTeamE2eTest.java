/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.examples.agent_teams;

import com.openjiuwen.agent_teams.schema.TeamAgentSpec;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AgentTeamE2eTest {
    @Test
    void loadConfigSeparatesRuntimeFromTeamSpecConfig() throws Exception {
        Path config = Files.createTempFile("agent-team", ".yaml");
        Files.writeString(config, """
                team_name: demo_team
                teammate_mode: build_mode
                spawn_mode: inprocess
                runtime:
                  session_id: demo_session
                  initial_query: hello
                transport:
                  type: inprocess
                """, StandardCharsets.UTF_8);

        AgentTeamE2e.LoadedConfig loaded = AgentTeamE2e.loadConfig(config);

        assertThat(loaded.teamConfig()).containsEntry("team_name", "demo_team");
        assertThat(loaded.teamConfig()).doesNotContainKey("runtime");
        assertThat(loaded.runtimeConfig()).containsEntry("session_id", "demo_session");
    }

    @Test
    void buildSpecUsesSnakeCaseAndIgnoresInfrastructureKeys() {
        TeamAgentSpec spec = AgentTeamE2e.buildSpec(Map.of(
                "team_name", "demo_team",
                "teammate_mode", "build_mode",
                "spawn_mode", "inprocess",
                "transport", Map.of("type", "inprocess"),
                "storage", Map.of("type", "sqlite")
        ));

        assertThat(spec.getTeamName()).isEqualTo("demo_team");
        assertThat(spec.getTeammateMode()).isEqualTo("build_mode");
        assertThat(spec.getSpawnMode()).isEqualTo("inprocess");
    }

    @Test
    void printBannerMatchesInteractiveScriptShape() {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();

        AgentTeamE2e.printBanner(new PrintStream(bytes, true, StandardCharsets.UTF_8));

        String output = bytes.toString(StandardCharsets.UTF_8);
        assertThat(output).contains("Agent Team E2E - Interactive CLI");
        assertThat(output).contains("Type 'exit' or 'quit' to stop.");
    }

    @Test
    void applyDefaultEnvironmentSetsExpectedSystemProperties() {
        String oldSsl = System.getProperty("LLM_SSL_VERIFY");
        String oldSensitive = System.getProperty("IS_SENSITIVE");
        try {
            System.clearProperty("LLM_SSL_VERIFY");
            System.clearProperty("IS_SENSITIVE");

            AgentTeamE2e.applyDefaultEnvironment();

            assertThat(System.getProperty("LLM_SSL_VERIFY")).isEqualTo("false");
            assertThat(System.getProperty("IS_SENSITIVE")).isEqualTo("false");
        } finally {
            restoreProperty("LLM_SSL_VERIFY", oldSsl);
            restoreProperty("IS_SENSITIVE", oldSensitive);
        }
    }

    private static void restoreProperty(String key, String value) {
        if (value == null) {
            System.clearProperty(key);
        } else {
            System.setProperty(key, value);
        }
    }
}

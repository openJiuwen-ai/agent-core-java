/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.examples.agent_teams;

import com.openjiuwen.agent_teams.agent.ModelPoolEntry;
import com.openjiuwen.agent_teams.schema.TeamAgentSpec;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AgentTeamModelPoolE2eTest {
    @Test
    void teamConfigPathUsesModelPoolConfigFile() {
        assertThat(AgentTeamModelPoolE2e.teamConfigPath(Path.of("examples", "agent_teams")).toString())
                .endsWith("examples" + java.io.File.separator + "agent_teams" + java.io.File.separator
                        + "config_model_pool.yaml");
    }

    @Test
    void printPoolSummaryHandlesEmptyPool() {
        TeamAgentSpec spec = new TeamAgentSpec();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();

        AgentTeamModelPoolE2e.printPoolSummary(spec, new PrintStream(bytes, true, StandardCharsets.UTF_8));

        assertThat(bytes.toString(StandardCharsets.UTF_8))
                .contains("(no model pool configured - members use per-agent model)");
    }

    @Test
    void printPoolSummaryShowsStrategyEntriesAndMaskedApiKey() {
        TeamAgentSpec spec = new TeamAgentSpec();
        spec.setModelPoolStrategy("round_robin");
        ModelPoolEntry entry = new ModelPoolEntry();
        entry.setModelName("glm-5.1");
        entry.setApiProvider("OpenAI");
        entry.setApiBaseUrl("https://example.test/v1");
        entry.setApiKey("sk-1234567890");
        entry.setDescription("primary");
        spec.setModelPool(List.of(entry));
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();

        AgentTeamModelPoolE2e.printPoolSummary(spec, new PrintStream(bytes, true, StandardCharsets.UTF_8));

        String output = bytes.toString(StandardCharsets.UTF_8);
        assertThat(output).contains("strategy : round_robin");
        assertThat(output).contains("entries  : 1");
        assertThat(output).contains("glm-5.1");
        assertThat(output).contains("api_key: sk-12345...");
    }

    @Test
    void buildSpecMapsModelPoolEntriesFromSnakeCaseConfig() {
        TeamAgentSpec spec = AgentTeamE2e.buildSpec(Map.of(
                "team_name", "pool_team",
                "model_pool_strategy", "by_model_name",
                "model_pool", List.of(Map.of(
                        "model_name", "glm-5",
                        "api_base_url", "https://example.test/v1",
                        "api_key", "sk-test",
                        "api_provider", "OpenAI",
                        "description", "direct"
                ))
        ));

        assertThat(spec.getTeamName()).isEqualTo("pool_team");
        assertThat(spec.getModelPoolStrategy()).isEqualTo("by_model_name");
        assertThat(spec.getModelPool()).hasSize(1);
        assertThat(spec.getModelPool().getFirst().getModelName()).isEqualTo("glm-5");
    }
}

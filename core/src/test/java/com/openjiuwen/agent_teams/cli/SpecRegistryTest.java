/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.openjiuwen.agent_teams.schema.TeamAgentSpec;
import com.openjiuwen.core.common.exception.BaseError;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Focused tests for the CLI spec loader and registry.
 *
 * <p>Mirrors Python's {@code SpecEntry}, {@code load_spec_yaml}, and
 * {@code SpecRegistry} in {@code openjiuwen/agent_teams/cli/spec_loader.py}.</p>
 */
class SpecRegistryTest {

    @TempDir
    Path tempDir;

    @Test
    void loadSpecYamlExpandsEnvironmentAndStripsRuntimeBlock() throws IOException {
        Path yaml = writeYaml("team.yaml", """
                team_name: team-${SUFFIX}
                spawn_mode: inprocess
                agents:
                  leader:
                    system_prompt: "hello ${SUFFIX} ${MISSING}"
                runtime:
                  session_id: "session-${SUFFIX}"
                  hints:
                    - "${SUFFIX}"
                metadata:
                  nested:
                    owner: "${SUFFIX}"
                """);

        SpecLoader.LoadedSpec loaded = SpecLoader.loadSpecYaml(
                yaml,
                name -> "SUFFIX".equals(name) ? "alpha" : null
        );

        assertThat(loaded.spec().getTeamName()).isEqualTo("team-alpha");
        assertThat(loaded.spec().getSpawnMode()).isEqualTo("inprocess");
        assertThat(loaded.spec().getAgents().get("leader").getSystemPrompt())
                .isEqualTo("hello alpha ${MISSING}");
        assertThat(loaded.runtimeOverrides())
                .containsEntry("session_id", "session-alpha")
                .containsEntry("hints", List.of("alpha"));
        assertThat(loaded.spec().getMetadata()).doesNotContainKey("runtime");
        assertThat(loaded.spec().getMetadata()).containsKey("nested");
    }

    @Test
    void registryKeepsInMemorySpecAheadOfYaml() throws IOException {
        Path yaml = writeYaml("team.yaml", """
                team_name: team-a
                agents:
                  leader:
                    system_prompt: yaml
                runtime:
                  session_id: yaml-session
                """);
        SpecRegistry registry = new SpecRegistry();

        SpecEntry yamlEntry = registry.addYaml(yaml);
        TeamAgentSpec inMemory = spec("team-a", "memory");
        SpecEntry memoryEntry = registry.addInmemory(inMemory);
        SpecEntry shadowed = registry.addYaml(yaml);

        assertThat(yamlEntry.source()).endsWith("team.yaml");
        assertThat(memoryEntry.source()).isEqualTo("in-memory");
        assertThat(shadowed).isSameAs(memoryEntry);
        assertThat(registry.get("team-a")).isSameAs(memoryEntry);
        assertThat(registry.names()).containsExactly("team-a");
    }

    @Test
    void yamlReloadReplacesYamlEntryAndKeepsInsertionOrder() throws IOException {
        Path first = writeYaml("first.yaml", """
                team_name: team-a
                agents:
                  leader:
                    system_prompt: first
                """);
        Path second = writeYaml("second.yaml", """
                team_name: team-a
                agents:
                  leader:
                    system_prompt: second
                runtime:
                  initial_query: hi
                """);
        SpecRegistry registry = new SpecRegistry();

        registry.addYaml(first);
        SpecEntry reloaded = registry.addYaml(second);

        assertThat(registry.names()).containsExactly("team-a");
        assertThat(registry.get("team-a")).isSameAs(reloaded);
        assertThat(reloaded.source()).endsWith("second.yaml");
        assertThat(reloaded.runtimeOverrides()).containsEntry("initial_query", "hi");
    }

    @Test
    void bulkRegisterUsesSpecTeamNameInsteadOfDeclaredKey() {
        SpecRegistry registry = new SpecRegistry();

        registry.bulkRegister(Map.of("declared", spec("actual", "memory")));

        assertThat(registry.get("declared")).isNull();
        assertThat(registry.get("actual")).isNotNull();
        assertThat(registry.names()).containsExactly("actual");
    }

    @Test
    void missingYamlRaisesConfigInvalid() {
        assertThrows(BaseError.class, () -> SpecLoader.loadSpecYaml(tempDir.resolve("missing.yaml")));
    }

    private Path writeYaml(String name, String content) throws IOException {
        Path path = tempDir.resolve(name);
        Files.writeString(path, content);
        return path;
    }

    private static TeamAgentSpec spec(String teamName, String prompt) {
        TeamAgentSpec spec = new TeamAgentSpec();
        spec.setTeamName(teamName);
        com.openjiuwen.agent_teams.agent.AgentConfigurator.DeepAgentSpec leader =
                new com.openjiuwen.agent_teams.agent.AgentConfigurator.DeepAgentSpec();
        leader.setSystemPrompt(prompt);
        spec.setAgents(Map.of("leader", leader));
        return spec;
    }
}

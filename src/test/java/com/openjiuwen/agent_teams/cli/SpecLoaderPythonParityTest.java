/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
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
 * Missing-test parity coverage for CLI spec loader and registry behavior.
 *
 * <p>Mirrors Python's {@code test_spec_loader} in
 * {@code tests/unit_tests/agent_teams/cli/test_spec_loader.py}.</p>
 */
class SpecLoaderPythonParityTest {

    @TempDir
    Path tempDir;

    @Test
    void expandEnvVarsHandlesStrDictListRecursively() {
        Map<String, String> env = Map.of("FOO", "bar", "API_KEY", "secret");

        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) SpecLoader.expandEnvVars(
                Map.of(
                        "key", "${FOO}",
                        "nested", Map.of("k", "value-${API_KEY}"),
                        "items", List.of("${FOO}", "${MISSING}", "literal")
                ),
                env::get
        );

        assertThat(result).containsEntry("key", "bar");
        @SuppressWarnings("unchecked")
        Map<String, Object> nested = (Map<String, Object>) result.get("nested");
        assertThat(nested).containsEntry("k", "value-secret");
        assertThat(result.get("items")).isEqualTo(List.of("bar", "${MISSING}", "literal"));
    }

    @Test
    void loadSpecYamlStripsRuntimeBlock() throws IOException {
        Path yaml = writeYaml("team.yaml", """
                agents:
                  leader: {}
                team_name: yaml_team
                runtime:
                  session_id: s1
                  initial_query: hi
                """);

        SpecLoader.LoadedSpec loaded = SpecLoader.loadSpecYaml(yaml);

        assertThat(loaded.spec()).isInstanceOf(TeamAgentSpec.class);
        assertThat(loaded.spec().getTeamName()).isEqualTo("yaml_team");
        assertThat(loaded.runtimeOverrides()).containsExactlyInAnyOrderEntriesOf(Map.of(
                "session_id", "s1",
                "initial_query", "hi"
        ));
        assertThat(loaded.spec().getMetadata()).doesNotContainKey("runtime");
    }

    @Test
    void loadSpecYamlReturnsEmptyRuntimeWhenAbsent() throws IOException {
        Path yaml = writeYaml("team.yaml", """
                agents:
                  leader: {}
                team_name: no_runtime
                """);

        SpecLoader.LoadedSpec loaded = SpecLoader.loadSpecYaml(yaml);

        assertThat(loaded.spec().getTeamName()).isEqualTo("no_runtime");
        assertThat(loaded.runtimeOverrides()).isEmpty();
    }

    @Test
    void loadSpecYamlRaisesOnMissingFile() {
        assertThrows(BaseError.class, () -> SpecLoader.loadSpecYaml(tempDir.resolve("does_not_exist.yaml")));
    }

    @Test
    void specRegistryInmemoryTakesPriorityOverYaml() throws IOException {
        Path yaml = writeYaml("team.yaml", """
                agents:
                  leader: {}
                team_name: shared
                """);
        SpecRegistry registry = new SpecRegistry();
        registry.addInmemory(makeSpec("shared"));

        SpecEntry yamlEntry = registry.addYaml(yaml);

        assertThat(yamlEntry.source()).isEqualTo("in-memory");
        assertThat(registry.get("shared").source()).isEqualTo("in-memory");
    }

    @Test
    void specRegistryBulkRegisterUsesSpecTeamName() {
        SpecRegistry registry = new SpecRegistry();

        registry.bulkRegister(Map.of("declared_name", makeSpec("real_name")));

        assertThat(registry.get("real_name")).isNotNull();
        assertThat(registry.get("declared_name")).isNull();
    }

    @Test
    void specRegistryListsEntriesInInsertionOrder() {
        SpecRegistry registry = new SpecRegistry();

        registry.addInmemory(makeSpec("a"));
        registry.addInmemory(makeSpec("b"));
        registry.addInmemory(makeSpec("c"));

        assertThat(registry.names()).containsExactly("a", "b", "c");
    }

    private Path writeYaml(String name, String content) throws IOException {
        Path path = tempDir.resolve(name);
        Files.writeString(path, content);
        return path;
    }

    private static TeamAgentSpec makeSpec(String teamName) {
        TeamAgentSpec spec = new TeamAgentSpec();
        spec.setAgents(Map.of("leader", new com.openjiuwen.agent_teams.agent.AgentConfigurator.DeepAgentSpec()));
        spec.setTeamName(teamName);
        return spec;
    }
}

package com.openjiuwen.harness;

import com.openjiuwen.harness.harness_config.HarnessConfigBuilder;
import com.openjiuwen.harness.harness_config.HarnessConfigLoader;
import com.openjiuwen.harness.schema.DeepAgentConfig;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("system-test")
class HarnessConfigSystemTest {

    @TempDir
    Path tempDir;

    @Test
    void configBuildShouldMaterializeWorkspaceArtifactsAndRuntimeState() throws Exception {
        Path configPath = tempDir.resolve("system.yaml");
        Files.writeString(configPath, """
                schema_version: harness_config.v0.1
                name: System Agent
                workspace:
                  root_path: repo
                prompts:
                  sections:
                    - name: identity
                      content:
                        en: system agent
                    - name: handbook
                      file: HANDBOOK.md
                      content:
                        en: system workspace
                resources:
                  tools:
                    - type: builtin
                      names: [filesystem, shell]
                  rails:
                    - type: builtin
                      name: task_planning
                language: en
                """);

        var resolvedConfig = HarnessConfigLoader.load(configPath);
        DeepAgentConfig config = HarnessConfigBuilder.build(resolvedConfig, null, tempDir);

        assertThat(config.getLanguage()).isEqualTo("en");
        assertThat(config.getWorkspace()).isNotNull();
        assertThat(Files.readString(tempDir.resolve("repo/HANDBOOK.md"))).isEqualTo("system workspace");
    }
}

package com.openjiuwen.harness.harness_config;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HarnessConfigLoaderTest {

    @Test
    void loadResolvesIdentityInlineAndFileSections() throws Exception {
        Path tempDir = Files.createTempDirectory("harness-config-loader");
        Path workspaceRoot = tempDir.resolve("workspace");
        Path configPath = tempDir.resolve("harness_config.yaml");
        Files.writeString(configPath, """
                language: en
                prompts:
                  sections:
                    - name: identity
                      content:
                        cn: "你好 {{workspace_root}}"
                        en: "Hello {{workspace_root}}"
                    - name: extra
                      priority: 17
                      content: "Shared {{name}}"
                    - name: prompt-file
                      file: "AGENT.md"
                      content:
                        en: "Agent {{name}}"
                        cn: "智能体 {{name}}"
                resources:
                  tools:
                    - type: builtin
                      name: demo
                """);

        ResolvedHarnessConfig resolved = HarnessConfigLoader.load(
                configPath,
                Map.of("name", "demo"),
                workspaceRoot
        );

        assertThat(resolved.getConfig().getLanguage()).isEqualTo("en");
        assertThat(resolved.getSystemPrompt()).isEqualTo("Hello " + workspaceRoot);
        assertThat(resolved.getExtraSections()).hasSize(1);
        assertThat(resolved.getExtraSections().get(0).getName()).isEqualTo("extra");
        assertThat(resolved.getExtraSections().get(0).getPriority()).isEqualTo(17);
        assertThat(resolved.getExtraSections().get(0).getContent())
                .containsEntry("cn", "Shared demo")
                .containsEntry("en", "Shared demo");
        assertThat(resolved.getFileSections()).hasSize(1);
        assertThat(resolved.getFileSections().get(0).getFilename()).isEqualTo("AGENT.md");
        assertThat(resolved.getFileSections().get(0).getContent())
                .containsEntry("cn", "智能体 demo")
                .containsEntry("en", "Agent demo");
        assertThat(resolved.getSourcePath()).isEqualTo(configPath.toAbsolutePath().normalize());
    }

    @Test
    void loadRejectsMissingConfigFile() {
        Path missingPath = Path.of("missing-harness", "harness_config.yaml");

        assertThatThrownBy(() -> HarnessConfigLoader.load(missingPath))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("HarnessConfig file not found");
    }

    @Test
    void loadRejectsPromptSectionsWithoutName() throws Exception {
        Path tempDir = Files.createTempDirectory("harness-config-invalid");
        Path configPath = tempDir.resolve("harness_config.yaml");
        Files.writeString(configPath, """
                prompts:
                  sections:
                    - content: "missing name"
                """);

        assertThatThrownBy(() -> HarnessConfigLoader.load(configPath))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("prompts.sections[0].name is required");
    }
}

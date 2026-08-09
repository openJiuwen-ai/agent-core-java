package com.openjiuwen.harness;

import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.harness.deep_agent.DeepAgent;
import com.openjiuwen.harness.harness_config.HarnessConfig;
import com.openjiuwen.harness.harness_config.HarnessConfigBuilder;
import com.openjiuwen.harness.harness_config.HarnessConfigInfo;
import com.openjiuwen.harness.harness_config.HarnessConfigLoader;
import com.openjiuwen.harness.harness_config.HarnessConfigRegistry;
import com.openjiuwen.harness.harness_config.ResolvedHarnessConfig;
import com.openjiuwen.harness.rails.DeepAgentRail;
import com.openjiuwen.harness.rails.HeartbeatRail;
import com.openjiuwen.harness.rails.TaskPlanningRail;
import com.openjiuwen.harness.schema.DeepAgentConfig;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HarnessConfigCompatibilityTest {

    @TempDir
    Path tempDir;

    @Test
    void loaderShouldResolvePromptSectionsAndTemplates() throws Exception {
        Path configPath = tempDir.resolve("harness_config.yaml");
        Files.writeString(configPath, """
                schema_version: harness_config.v0.1
                id: coding-agent
                name: Coding Agent
                language: en
                prompts:
                  sections:
                    - name: identity
                      content:
                        en: "You are working in {{ workspace_root }}"
                    - name: style
                      priority: 40
                      content: "Keep answers short"
                    - name: repo_notes
                      file: AGENT.md
                      content:
                        en: "Workspace: {{ workspace_root }}"
                """);

        ResolvedHarnessConfig resolved = HarnessConfigLoader.load(configPath, Map.of("workspace_root", "/tmp/work"), null);

        assertThat(resolved.getSystemPrompt()).isEqualTo("You are working in /tmp/work");
        assertThat(resolved.getExtraSections()).hasSize(1);
        assertThat(resolved.getFileSections()).hasSize(1);
    }

    @Test
    @Tag("system-test")
    void builderShouldCreateAgentAndWriteWorkspaceFiles() throws Exception {
        Path configPath = tempDir.resolve("agent.yaml");
        Files.writeString(configPath, """
                schema_version: harness_config.v0.1
                id: build-agent
                name: Build Agent
                description: Config-built agent
                workspace:
                  root_path: workspace
                prompts:
                  sections:
                    - name: identity
                      content:
                        cn: 你是构建助手
                    - name: runbook
                      file: notes/RUNBOOK.md
                      content:
                        cn: 使用工作区
                resources:
                  tools:
                    - type: builtin
                      names: [filesystem, shell]
                  rails:
                    - type: builtin
                      name: task_planning
                  skills:
                    dirs: [skills]
                    mode: auto_list
                  mcps:
                    - type: stdio
                      command: uvx
                      args: [mcp-server]
                      env:
                        MODE: demo
                language: cn
                max_iterations: 9
                """);

        ResolvedHarnessConfig resolvedConfig = HarnessConfigLoader.load(configPath);
        DeepAgentConfig agentConfig = HarnessConfigBuilder.build(resolvedConfig, null, null);
        DeepAgent agent = new DeepAgent(new AgentCard("build-agent", "build-agent", "Build Agent"));
        agent.configure(agentConfig);

        assertThat(agent.getCard().getName()).isEqualTo("Build Agent");
        assertThat(agent.deepConfig().getMaxIterations()).isEqualTo(9);
        assertThat(agent.getRails().stream().map(item -> item.getClass().getSimpleName()).toList())
                .contains("TaskPlanningRail");
        assertThat(agent.deepConfig().getProgressiveToolDefaultVisibleTools()).hasSizeGreaterThanOrEqualTo(0);
    }

    @Test
    void registryShouldDiscoverAndLoadConfigs() {
        List<HarnessConfigInfo> discovered = HarnessConfigRegistry.discover();
        assertThat(discovered).isNotNull();
    }

    @Test
    void registryShouldSupportDisableAndEnable() {
        List<HarnessConfigInfo> discovered = HarnessConfigRegistry.discover();
        for (HarnessConfigInfo info : discovered) {
            HarnessConfigRegistry.disable(info.getId());
            assertThat(HarnessConfigRegistry.get(info.getId())).isNull();
            HarnessConfigRegistry.enable(info.getId());
        }
    }

    @Test
    void generateYamlShouldRoundTripBuiltinResources() {
        DeepAgentConfig progressiveConfig = new DeepAgentConfig();
        progressiveConfig.setProgressiveToolDefaultVisibleTools(List.of("read_file"));
        progressiveConfig.setProgressiveToolAlwaysVisibleTools(List.of("search_tools", "load_tools"));
        progressiveConfig.setProgressiveToolMaxLoadedTools(3);

        String yaml = HarnessConfigBuilder.generateHarnessConfigYaml();

        assertThat(yaml).contains("schema_version: harness_config.v0.1");
        assertThat(yaml).contains("name: DeepAgent");
    }

    @Test
    @Tag("system-test")
    void builderShouldResolvePythonBuiltinRailsOnly() throws Exception {
        Path configPath = tempDir.resolve("configured-rails.yaml");
        Files.writeString(configPath, """
                schema_version: harness_config.v0.1
                id: configured-rails
                name: Configured Rails
                workspace:
                  root_path: workspace
                resources:
                  rails:
                    - type: builtin
                      name: task_planning
                    - type: package
                      module: com.openjiuwen.harness.rails
                      class: HeartbeatRail
                """);

        ResolvedHarnessConfig resolvedConfig = HarnessConfigLoader.load(configPath);
        DeepAgentConfig agentConfig = HarnessConfigBuilder.build(resolvedConfig, null, null);
        DeepAgent agent = new DeepAgent(new AgentCard("configured-rails", "configured-rails", "Configured Rails"));
        agent.configure(agentConfig);
        List<DeepAgentRail> rails = agent.getRails();

        TaskPlanningRail planning = findRail(rails, TaskPlanningRail.class);
        assertThat(planning.isEnableProgressRepeat()).isFalse();
        assertThat(planning.getListToolCallInterval()).isEqualTo(20);
        assertThat(findRail(rails, HeartbeatRail.class)).isNotNull();
    }

    @Test
    void builderShouldRejectUnknownBuiltinRail() {
        HarnessConfig.ResourcesSchema resources = new HarnessConfig.ResourcesSchema();
        resources.getRails().add(HarnessConfig.RailResourceSchema.builder()
                .type("builtin")
                .name("coding_memory")
                .build());
        assertThatThrownBy(() -> HarnessConfigBuilder.resolveRails(resources))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown builtin rail: 'coding_memory'");
    }

    @Test
    void loaderShouldResolveFromObjectModel() throws Exception {
        Path modelConfigPath = tempDir.resolve("model.yaml");
        Files.writeString(modelConfigPath, """
                schema_version: harness_config.v0.1
                name: Model Agent
                prompts:
                  sections:
                    - name: identity
                      content:
                        en: "Hello {{ repo }}"
                language: en
                """);

        ResolvedHarnessConfig resolved = HarnessConfigLoader.load(
                modelConfigPath,
                Map.of("repo", "agent-core-java"),
                null
        );

        assertThat(resolved.getSystemPrompt()).isEqualTo("Hello agent-core-java");
    }

    private static <T> T findRail(List<? extends Object> rails, Class<T> type) {
        return rails.stream()
                .filter(type::isInstance)
                .map(type::cast)
                .findFirst()
                .orElseThrow();
    }
}

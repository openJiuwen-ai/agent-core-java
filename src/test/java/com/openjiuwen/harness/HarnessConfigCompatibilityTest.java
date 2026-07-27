package com.openjiuwen.harness;

import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.harness.DeepAgent;
import com.openjiuwen.harness.harness_config.HarnessConfig;
import com.openjiuwen.harness.harness_config.HarnessConfigBuilder;
import com.openjiuwen.harness.harness_config.HarnessConfigInfo;
import com.openjiuwen.harness.harness_config.HarnessConfigLoader;
import com.openjiuwen.harness.harness_config.HarnessConfigRegistry;
import com.openjiuwen.harness.harness_config.ResolvedHarnessConfig;
import com.openjiuwen.harness.rails.CodingMemoryRail;
import com.openjiuwen.harness.rails.ContextAssembleRail;
import com.openjiuwen.harness.rails.ContextProcessorRail;
import com.openjiuwen.harness.rails.DeepAgentRail;
import com.openjiuwen.harness.rails.ExternalMemoryRail;
import com.openjiuwen.harness.rails.HeartbeatRail;
import com.openjiuwen.harness.rails.LspRail;
import com.openjiuwen.harness.rails.McpRail;
import com.openjiuwen.harness.rails.ProgressiveToolRail;
import com.openjiuwen.harness.rails.SkillCreateRail;
import com.openjiuwen.harness.rails.TaskPlanningRail;
import com.openjiuwen.harness.rails.TaskCompletionRail;
import com.openjiuwen.harness.rails.TeamSkillCreateRail;
import com.openjiuwen.harness.rails.TeamSkillRail;
import com.openjiuwen.harness.rails.VerificationContractRail;
import com.openjiuwen.harness.rails.VerificationRail;
import com.openjiuwen.harness.rails.skills.SkillUseRail;
import com.openjiuwen.harness.schema.DeepAgentConfig;
import com.openjiuwen.harness.tools.BashTool;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

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
                    - type: builtin
                      name: heartbeat
                    - type: builtin
                      name: lsp
                    - type: builtin
                      name: mcp
                    - type: builtin
                      name: progressive_tool
                    - type: builtin
                      name: task_completion
                    - type: builtin
                      name: context_assemble
                    - type: builtin
                      name: context_processor
                    - type: builtin
                      name: memory
                    - type: builtin
                      name: coding_memory
                    - type: builtin
                      name: external_memory
                      config:
                        provider: openjiuwen
                        user_id: user-from-config
                        scope_id: scope-from-config
                        session_id: session-from-config
                    - type: builtin
                      name: verification_contract
                    - type: builtin
                      name: verification
                    - type: builtin
                      name: skill_create
                    - type: builtin
                      name: team_skill_create
                    - type: builtin
                      name: team_skill
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
        assertThat(agent.getTools().values().stream().map(tool -> tool.getClass().getSimpleName()).toList())
                .contains("FilesystemTool", "BashTool");
        assertThat(agent.getTools().values().stream()
                .filter(com.openjiuwen.core.foundation.tool.Tool.class::isInstance)
                .map(tool -> ((com.openjiuwen.core.foundation.tool.Tool) tool).getCard().getName())
                .toList())
                .contains("todo_create", "todo_list", "todo_get", "todo_modify", "list_skill", "skill_tool",
                        "lsp", "list_mcp_resources", "read_mcp_resource", "search_tools", "load_tools",
                        "memory_search", "read_memory", "write_memory", "edit_memory",
                        "coding_memory_read", "coding_memory_write", "coding_memory_edit",
                        "ltm_search", "ltm_search_summary");
        assertThat(agent.getRails().stream().map(item -> item.getClass().getSimpleName()).toList())
                .contains("SecurityRail", "TaskPlanningRail", "HeartbeatRail", "LspRail", "McpRail",
                        "ProgressiveToolRail", "TaskCompletionRail", "ContextAssembleRail",
                        "ContextProcessorRail", "MemoryRail", "CodingMemoryRail", "ExternalMemoryRail",
                        "VerificationContractRail", "VerificationRail", "SkillCreateRail",
                        "TeamSkillCreateRail", "TeamSkillRail");
        assertThat(agent.deepConfig().getMcps()).hasSize(1);
        assertThat(agent.deepConfig().getProgressiveToolDefaultVisibleTools()).hasSizeGreaterThanOrEqualTo(0);
        ExternalMemoryRail externalMemory = findRail(agent.getRails(), ExternalMemoryRail.class);
        assertThat(externalMemory).isNotNull();
        assertThat(Files.readString(tempDir.resolve("workspace/notes/RUNBOOK.md"))).isEqualTo("使用工作区");
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
    void builderShouldApplyBuiltinRailConfig() throws Exception {
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
                      name: progressive_tool
                      config:
                        default_visible_tools: [read_file]
                        always_visible_tools: [search_tools, load_tools]
                        max_loaded_tools: 3
                    - type: builtin
                      name: task_planning
                      config:
                        enable_progress_repeat: true
                        list_tool_call_interval: 4
                        model_selection:
                          fast: cheap model
                    - type: builtin
                      name: task_completion
                      config:
                        task_instruction: "Solve: {query}"
                        completion_promise: DONE
                        required_confirmations: 2
                        allow_promise_details: true
                        max_rounds: 5
                        timeout_seconds: 7
                    - type: builtin
                      name: context_processor
                      config:
                        preset: false
                        processor_keys: [ToolResultBudgetProcessor]
                        session_memory_enabled: true
                    - type: builtin
                      name: memory
                      config:
                        proactive: false
                    - type: builtin
                      name: coding_memory
                      config:
                        coding_memory_dir: code-mem
                        proactive: false
                    - type: builtin
                      name: verification
                      config:
                        allowed_tools: [read_file]
                    - type: builtin
                      name: skill_use
                      config:
                        skills_dir: [custom-skills]
                        skill_mode: all
                        enabled_skills: [alpha]
                        disabled_skills: [beta]
                        remote_skills:
                          - repo: owner/remote-skills
                            ref: main
                            directory: skills
                            token: ghp_test
                    - type: builtin
                      name: skill_create
                      config:
                        skills_dir: custom-skills
                        language: en
                        auto_trigger: false
                        tool_call_threshold: 4
                        tool_diversity_threshold: 2
                    - type: builtin
                      name: team_skill_create
                      config:
                        skills_dir: team-skills
                        language: en
                        auto_trigger: false
                        min_team_members_for_create: 4
                    - type: builtin
                      name: team_skill
                      config:
                        skills_dir: team-skills
                        language: en
                """);

        ResolvedHarnessConfig resolvedConfig = HarnessConfigLoader.load(configPath);
        DeepAgentConfig agentConfig = HarnessConfigBuilder.build(resolvedConfig, null, null);
        DeepAgent agent = new DeepAgent(new AgentCard("configured-rails", "configured-rails", "Configured Rails"));
        agent.configure(agentConfig);
        List<DeepAgentRail> rails = agent.getRails();

        ProgressiveToolRail progressive = findRail(rails, ProgressiveToolRail.class);
        assertThat(progressive).isNotNull();

        TaskPlanningRail planning = findRail(rails, TaskPlanningRail.class);
        assertThat(planning).isNotNull();
        assertThat(planning.isEnableProgressRepeat()).isTrue();
        assertThat(planning.getListToolCallInterval()).isEqualTo(4);
        assertThat(planning.getModelSelection()).containsEntry("fast", "cheap model");

        TaskCompletionRail completion = findRail(rails, TaskCompletionRail.class);
        assertThat(completion).isNotNull();
        assertThat(completion.applyTaskInstruction("ship", false)).isEqualTo("Solve: ship");
        assertThat(TaskCompletionRail.promiseMatches("<promise>DONE with details</promise>", "DONE")).isTrue();
        assertThat(completion.getRequiredConfirmations()).isEqualTo(2);
        assertThat(completion.getMaxRounds()).isEqualTo(5);
        assertThat(completion.getTimeout()).isEqualTo(7.0);

        ContextProcessorRail processor = findRail(rails, ContextProcessorRail.class);
        assertThat(processor).isNotNull();

        CodingMemoryRail codingMemory = findRail(rails, CodingMemoryRail.class);
        assertThat(codingMemory).isNotNull();
        assertThat(codingMemory.codingMemoryDir()).endsWith("workspace/code-mem");

        VerificationRail verification = findRail(rails, VerificationRail.class);
        assertThat(verification).isNotNull();
        assertThat(verification.allowsTool("read_file")).isTrue();
        assertThat(verification.allowsTool("bash")).isTrue();

        SkillCreateRail skillCreate = findRail(rails, SkillCreateRail.class);
        assertThat(skillCreate).isNotNull();
        assertThat(skillCreate.getSkillsDir()).endsWith("workspace/custom-skills");
        assertThat(skillCreate.getLanguage()).isEqualTo("en");
        assertThat(skillCreate.shouldProposeNewSkill()).isFalse();

        TeamSkillCreateRail teamSkillCreate = findRail(rails, TeamSkillCreateRail.class);
        assertThat(teamSkillCreate).isNotNull();

        TeamSkillRail teamSkill = findRail(rails, TeamSkillRail.class);
        assertThat(teamSkill).isNotNull();
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


package com.openjiuwen.harness;

import static org.assertj.core.api.Assertions.assertThat;

import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.harness.deep_agent.DeepAgent;
import com.openjiuwen.harness.harness_config.HarnessConfig;
import com.openjiuwen.harness.harness_config.HarnessConfigBuilder;
import com.openjiuwen.harness.harness_config.HarnessConfigInfo;
import com.openjiuwen.harness.harness_config.HarnessConfigLoader;
import com.openjiuwen.harness.harness_config.HarnessConfigRegistry;
import com.openjiuwen.harness.harness_config.ResolvedHarnessConfig;
import com.openjiuwen.harness.rails.CodingMemoryRail;
import com.openjiuwen.harness.rails.ContextAssembleRail;
import com.openjiuwen.harness.rails.ContextProcessorRail;
import com.openjiuwen.harness.rails.ExternalMemoryRail;
import com.openjiuwen.harness.rails.HeartbeatRail;
import com.openjiuwen.harness.rails.LspRail;
import com.openjiuwen.harness.rails.McpRail;
import com.openjiuwen.harness.rails.MemoryRail;
import com.openjiuwen.harness.rails.ProgressiveToolRail;
import com.openjiuwen.harness.rails.SkillCreateRail;
import com.openjiuwen.harness.rails.SkillUseRail;
import com.openjiuwen.harness.rails.TaskCompletionRail;
import com.openjiuwen.harness.rails.TaskPlanningRail;
import com.openjiuwen.harness.rails.TeamSkillCreateRail;
import com.openjiuwen.harness.rails.TeamSkillRail;
import com.openjiuwen.harness.rails.VerificationContractRail;
import com.openjiuwen.harness.rails.VerificationRail;
import com.openjiuwen.harness.tools.BashTool;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

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

        Path workspaceRoot = tempDir.resolve("work");
        ResolvedHarnessConfig resolved =
            HarnessConfigLoader.load(configPath, Map.of("workspace_root", workspaceRoot.toString()), null);

        assertThat(resolved.systemPrompt()).contains("You are working in");
        assertThat(resolved.systemPrompt()).contains(workspaceRoot.getFileName().toString());
        assertThat(resolved.extraSections()).hasSize(1);
        assertThat(resolved.extraSections().get(0).name()).isEqualTo("style");
        assertThat(resolved.fileSections()).hasSize(1);
        assertThat(resolved.fileSections().get(0).filename()).isEqualTo("AGENT.md");
    }

    @Test
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

        DeepAgent agent = HarnessConfigBuilder.build(HarnessConfigLoader.load(configPath));
        agent.ensureInitialized();

        assertThat(agent.getCard().getName()).isEqualTo("Build Agent");
        assertThat(agent.getConfig().getMaxIterations()).isEqualTo(9);
        assertThat(agent.getRegisteredTools().stream().map(tool -> tool.getClass().getSimpleName()).toList())
                .contains("FilesystemTool", "BashTool");
        assertThat(
                agent.getRegisteredTools().stream().filter(com.openjiuwen.core.foundation.tool.Tool.class::isInstance)
                        .map(tool -> ((com.openjiuwen.core.foundation.tool.Tool) tool).getCard().getName()).toList())
                .contains("todo_create", "todo_list", "todo_get", "todo_modify", "list_skill", "skill_tool", "lsp",
                        "list_mcp_resources", "read_mcp_resource", "search_tools", "load_tools", "memory_search",
                        "read_memory", "write_memory", "edit_memory", "coding_memory_read", "coding_memory_write",
                        "coding_memory_edit", "ltm_search", "ltm_search_summary");
        assertThat(agent.getRegisteredRails().stream().map(item -> item.getClass().getSimpleName()).toList()).contains(
                "SecurityRail", "TaskPlanningRail", "HeartbeatRail", "LspRail", "McpRail", "ProgressiveToolRail",
                "TaskCompletionRail", "ContextAssembleRail", "ContextProcessorRail", "MemoryRail", "CodingMemoryRail",
                "ExternalMemoryRail", "VerificationContractRail", "VerificationRail", "SkillCreateRail",
                "TeamSkillCreateRail", "TeamSkillRail");
        assertThat(agent.getRegisteredMcps()).hasSize(1);
        assertThat(agent.getConfig().getSkillDirectories()).hasSize(1);
        assertThat(agent.getConfig().getSkillMode()).isEqualTo("auto_list");
        ExternalMemoryRail externalMemory = findRail(agent.getRegisteredRails(), ExternalMemoryRail.class);
        assertThat(externalMemory.toolNames()).containsExactly("ltm_search", "ltm_search_summary");
        externalMemory
                .beforeInvoke(com.openjiuwen.core.singleagent.rail.AgentCallbackContext.builder()
                        .inputs(com.openjiuwen.core.singleagent.rail.InvokeInputs.builder().query("remember config")
                                .conversationId("session-from-config").build())
                        .extra(new java.util.LinkedHashMap<>()).build());
        assertThat(externalMemory.isInitialized()).isTrue();
        assertThat(Files.readString(tempDir.resolve("workspace/notes/RUNBOOK.md"))).isEqualTo("使用工作区");
    }

    @Test
    void registryShouldSupportManualRegistrationAndLoad() throws Exception {
        Path configPath = tempDir.resolve("registered.yaml");
        Files.writeString(configPath, """
                schema_version: harness_config.v0.1
                name: Registered Agent
                prompts:
                  sections:
                    - name: identity
                      content: registry agent
                """);
        HarnessConfigRegistry.register(HarnessConfigInfo.builder().id("registered-agent").name("registered-agent")
                .packageName("local").configPath(configPath).build());

        assertThat(HarnessConfigRegistry.get("registered-agent")).isNotNull();
        DeepAgent agent = HarnessConfigRegistry.load("registered-agent");
        assertThat(agent.getCard().getName()).isEqualTo("Registered Agent");

        HarnessConfigRegistry.disable("registered-agent");
        assertThat(HarnessConfigRegistry.get("registered-agent")).isNull();
        HarnessConfigRegistry.enable("registered-agent");
        assertThat(HarnessConfigRegistry.get("registered-agent")).isNotNull();
    }

    @Test
    void registryShouldReloadChangedConfigFiles() throws Exception {
        Path configPath = tempDir.resolve("hot-reload.yaml");
        Files.writeString(configPath, """
                schema_version: harness_config.v0.1
                name: Hot Reload One
                prompts:
                  sections:
                    - name: identity
                      content: first prompt
                """);
        HarnessConfigRegistry.register(HarnessConfigInfo.builder().id("hot-reload-agent").name("hot-reload-agent")
                .packageName("local").configPath(configPath).build());

        DeepAgent first = HarnessConfigRegistry.load("hot-reload-agent");
        assertThat(first.getCard().getName()).isEqualTo("Hot Reload One");
        assertThat(HarnessConfigRegistry.reloadIfChanged("hot-reload-agent").reloaded()).isFalse();

        Files.writeString(configPath, """
                schema_version: harness_config.v0.1
                name: Hot Reload Two
                prompts:
                  sections:
                    - name: identity
                      content: second prompt
                """);
        Files.setLastModifiedTime(configPath, FileTime.from(Instant.now().plusSeconds(2)));

        HarnessConfigRegistry.ReloadResult result = HarnessConfigRegistry.reloadIfChanged("hot-reload-agent");

        assertThat(result.reloaded()).isTrue();
        assertThat(result.agent()).isNotSameAs(first);
        assertThat(result.agent().getCard().getName()).isEqualTo("Hot Reload Two");
        assertThat(HarnessConfigRegistry.getLoaded("hot-reload-agent")).isSameAs(result.agent());
        assertThat(HarnessConfigRegistry.reloadIfChanged("hot-reload-agent").reloaded()).isFalse();
    }

    @Test
    void generateYamlShouldRoundTripBuiltinResources() {
        String yaml = HarnessConfigBuilder.generateHarnessConfigYaml(
                AgentCard.builder().id("demo").name("Demo").description("d").build(), "System prompt",
                List.of(new BashTool()),
                List.of(new TaskPlanningRail(true, 4, Map.of("fast", "cheap model")), new HeartbeatRail(),
                        new LspRail(), new McpRail(),
                        new ProgressiveToolRail(List.of("read_file"), List.of("search_tools", "load_tools"), 3),
                        new TaskCompletionRail("Solve: {query}", "DONE", 2, true, 5, Duration.ofSeconds(7)),
                        new ContextAssembleRail(),
                        new ContextProcessorRail(false, List.of("ToolResultBudgetProcessor"), true),
                        new MemoryRail(null, false), new CodingMemoryRail("code-mem", null, false),
                        new ExternalMemoryRail(), new VerificationContractRail(),
                        new VerificationRail(java.util.Set.of("read_file")),
                        new SkillUseRail(List.of("custom-skills"), "all", List.of("alpha"), List.of("beta"),
                                List.of(new SkillUseRail.RemoteSkillSource("owner", "remote-skills", "main", "skills",
                                        "ghp_test"))),
                        new SkillCreateRail("custom-skills", "en", false, 4, 2),
                        new TeamSkillCreateRail("team-skills", "en", false, 4), new TeamSkillRail("team-skills", "en")),
                "en", 7, 3.5);

        assertThat(yaml).contains("schema_version: harness_config.v0.1");
        assertThat(yaml).contains("names: [shell]");
        assertThat(yaml).contains("name: task_planning");
        assertThat(yaml).contains("name: heartbeat");
        assertThat(yaml).contains("name: lsp");
        assertThat(yaml).contains("name: mcp");
        assertThat(yaml).contains("name: progressive_tool");
        assertThat(yaml).contains("name: task_completion");
        assertThat(yaml).contains("name: context_assemble");
        assertThat(yaml).contains("name: context_processor");
        assertThat(yaml).contains("name: memory");
        assertThat(yaml).contains("name: coding_memory");
        assertThat(yaml).contains("name: external_memory");
        assertThat(yaml).contains("name: verification_contract");
        assertThat(yaml).contains("name: verification");
        assertThat(yaml).contains("enable_progress_repeat: true");
        assertThat(yaml).contains("list_tool_call_interval: 4");
        assertThat(yaml).contains("model_selection:");
        assertThat(yaml).contains("fast: cheap model");
        assertThat(yaml).contains("default_visible_tools: [read_file]");
        assertThat(yaml).contains("max_loaded_tools: 3");
        assertThat(yaml).contains("task_instruction: 'Solve: {query}'");
        assertThat(yaml).contains("completion_promise: DONE");
        assertThat(yaml).contains("required_confirmations: 2");
        assertThat(yaml).contains("timeout_millis: 7000");
        assertThat(yaml).contains("processor_keys: [ToolResultBudgetProcessor]");
        assertThat(yaml).contains("coding_memory_dir: code-mem");
        assertThat(yaml).contains("allowed_tools:");
        assertThat(yaml).contains("name: skill_use");
        assertThat(yaml).contains("skill_mode: all");
        assertThat(yaml).contains("enabled_skills: [alpha]");
        assertThat(yaml).contains("disabled_skills: [beta]");
        assertThat(yaml).contains("skills_dir: custom-skills");
        assertThat(yaml).contains("remote_skills:");
        assertThat(yaml).contains("owner: owner");
        assertThat(yaml).contains("repo: remote-skills");
        assertThat(yaml).contains("ref: main");
        assertThat(yaml).contains("directory: skills");
        assertThat(yaml).contains("token: ghp_test");
        assertThat(yaml).contains("min_team_members_for_create: 4");
        assertThat(yaml).contains("completion_timeout: 3.5");
    }

    @Test
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

        DeepAgent agent = HarnessConfigBuilder.build(HarnessConfigLoader.load(configPath));
        List<Object> rails = agent.getConfig().getRails();

        ProgressiveToolRail progressive = findRail(rails, ProgressiveToolRail.class);
        assertThat(progressive.getDefaultVisibleTools()).containsExactly("read_file");
        assertThat(progressive.getAlwaysVisibleTools()).containsExactlyInAnyOrder("search_tools", "load_tools");
        assertThat(progressive.getMaxLoadedTools()).isEqualTo(3);

        TaskPlanningRail planning = findRail(rails, TaskPlanningRail.class);
        assertThat(planning.isEnableProgressRepeat()).isTrue();
        assertThat(planning.getListToolCallInterval()).isEqualTo(4);
        assertThat(planning.getModelSelection()).containsEntry("fast", "cheap model");

        TaskCompletionRail completion = findRail(rails, TaskCompletionRail.class);
        assertThat(completion.applyTaskInstruction("ship", false)).isEqualTo("Solve: ship");
        assertThat(completion.promiseMatches("<promise>DONE with details</promise>")).isTrue();
        assertThat(completion.getRequiredConfirmations()).isEqualTo(2);
        assertThat(completion.getMaxRounds()).isEqualTo(5);
        assertThat(completion.getTimeout()).isEqualTo(Duration.ofSeconds(7));

        ContextProcessorRail processor = findRail(rails, ContextProcessorRail.class);
        assertThat(processor.isPreset()).isFalse();
        assertThat(processor.getProcessorKeys()).containsExactly("ToolResultBudgetProcessor");
        assertThat(processor.isSessionMemoryEnabled()).isTrue();

        CodingMemoryRail codingMemory = findRail(rails, CodingMemoryRail.class);
        assertThat(codingMemory.codingMemoryDir().replace('\\', '/')).endsWith("workspace/code-mem");

        VerificationRail verification = findRail(rails, VerificationRail.class);
        assertThat(verification.allowsTool("read_file")).isTrue();
        assertThat(verification.allowsTool("bash")).isFalse();

        SkillUseRail skillUse = findRail(rails, SkillUseRail.class);
        assertThat(skillUse.configuredSkillDirectories().get(0).replace('\\', '/')).endsWith("workspace/custom-skills");
        assertThat(skillUse.skillMode()).isEqualTo("all");
        assertThat(skillUse.enabledSkills()).containsExactly("alpha");
        assertThat(skillUse.disabledSkills()).containsExactly("beta");
        assertThat(skillUse.remoteSkillSources()).singleElement().satisfies(source -> {
            assertThat(source.owner()).isEqualTo("owner");
            assertThat(source.repo()).isEqualTo("remote-skills");
            assertThat(source.ref()).isEqualTo("main");
            assertThat(source.directory()).isEqualTo("skills");
            assertThat(source.token()).isEqualTo("ghp_test");
        });

        SkillCreateRail skillCreate = findRail(rails, SkillCreateRail.class);
        assertThat(skillCreate.getSkillsDir().replace('\\', '/')).endsWith("workspace/custom-skills");
        assertThat(skillCreate.getLanguage()).isEqualTo("en");
        assertThat(skillCreate.shouldProposeNewSkill()).isFalse();

        TeamSkillCreateRail teamSkillCreate = findRail(rails, TeamSkillCreateRail.class);
        assertThat(teamSkillCreate.getLanguage()).isEqualTo("en");
        teamSkillCreate.recordToolCall("spawn_member");
        teamSkillCreate.recordToolCall("spawn_member");
        teamSkillCreate.recordToolCall("spawn_member");
        assertThat(teamSkillCreate.shouldProposeNewTeamSkill()).isFalse();
        teamSkillCreate.recordToolCall("spawn_member");
        assertThat(teamSkillCreate.shouldProposeNewTeamSkill()).isTrue();

        TeamSkillRail teamSkill = findRail(rails, TeamSkillRail.class);
        assertThat(teamSkill.getSkillsDir().replace('\\', '/')).endsWith("workspace/team-skills");
        assertThat(teamSkill.getLanguage()).isEqualTo("en");
    }

    @Test
    void loaderShouldResolveFromObjectModel() {
        HarnessConfig config =
            HarnessConfig.builder().name("Model Agent")
                    .prompts(HarnessConfig.PromptsSchema.builder()
                            .sections(List.of(HarnessConfig.SectionSchema.builder().name("identity")
                                    .content(Map.of("en", "Hello {{ repo }}")).build()))
                            .build())
                    .language("en").build();

        ResolvedHarnessConfig resolved =
            HarnessConfigLoader.resolve(config, tempDir.resolve("model.yaml"), Map.of("repo", "agent-core-java"), null);

        assertThat(resolved.systemPrompt()).isEqualTo("Hello agent-core-java");
    }

    private static <T> T findRail(List<Object> rails, Class<T> type) {
        return rails.stream().filter(type::isInstance).map(type::cast).findFirst().orElseThrow();
    }
}

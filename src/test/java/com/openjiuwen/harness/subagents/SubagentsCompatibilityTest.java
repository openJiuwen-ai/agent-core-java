
package com.openjiuwen.harness.subagents;

import static org.assertj.core.api.Assertions.assertThat;

import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.foundation.store.base_embedding.EmbeddingConfig;
import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.foundation.tool.mcp.McpServerConfig;
import com.openjiuwen.core.singleagent.agents.ReActAgentConfig;
import com.openjiuwen.core.sysop.SysOperation;
import com.openjiuwen.core.sysop.SysOperationCard;
import com.openjiuwen.harness.deep_agent.DeepAgent;
import com.openjiuwen.harness.rails.AgentModeRail;
import com.openjiuwen.harness.rails.CodingMemoryRail;
import com.openjiuwen.harness.rails.MemoryRail;
import com.openjiuwen.harness.rails.SecurityRail;
import com.openjiuwen.harness.rails.SkillUseRail;
import com.openjiuwen.harness.rails.SysOperationRail;
import com.openjiuwen.harness.rails.interrupt.AskUserRail;
import com.openjiuwen.harness.rails.interrupt.ConfirmInterruptRail;
import com.openjiuwen.harness.schema.config.DeepAgentConfig;
import com.openjiuwen.harness.workspace.Workspace;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

class SubagentsCompatibilityTest {
    @Test
    void buildPlanAgentConfigShouldExposeExpectedDefaults() {
        SubAgentConfig config = PlanAgentFactory.buildPlanAgentConfig("en");
        assertThat(config.getAgentCard().getName()).isEqualTo("plan_agent");
        assertThat(config.getAgentCard().getDescription()).contains("Architecture design specialist");
        assertThat(config.getSystemPrompt()).contains("software architect and planning specialist");
        assertThat(config.getSystemPrompt()).contains("CRITICAL: READ-ONLY MODE");
        assertThat(config.getSystemPrompt()).contains("Critical Files for Implementation");
        assertThat(config.getSystemPrompt()).contains("git status", "git diff");
        assertThat(config.getMaxIterations()).isEqualTo(25);
        assertThat(config.hasRail(SysOperationRail.class)).isTrue();
        assertThat(config.hasRail(SecurityRail.class)).isTrue();
        assertThat(config.getRails().stream().filter(SecurityRail.class::isInstance).map(SecurityRail.class::cast)
                .allMatch(SecurityRail::isReadOnly)).isTrue();
        assertThat(config.getMetadata()).containsEntry("readonly", true).containsEntry("write_tools_forbidden", true)
                .containsEntry("requires_critical_files", true).containsEntry("critical_files_min", 3)
                .containsEntry("critical_files_max", 5);
        assertThat((List<String>) config.getMetadata().get("forbidden_operations")).contains("write_file", "edit_file",
                "git commit");
        assertThat(config.isRestrictToWorkDir()).isFalse();
    }

    @Test
    void buildExploreAgentConfigShouldExposeExpectedDefaults() {
        SubAgentConfig config = ExploreAgentFactory.buildExploreAgentConfig("en");
        assertThat(config.getAgentCard().getName()).isEqualTo("explore_agent");
        assertThat(config.getAgentCard().getDescription()).contains("Codebase navigation agent");
        assertThat(config.getSystemPrompt()).contains("codebase navigation specialist");
        assertThat(config.getSystemPrompt()).contains("IMPORTANT: READ-ONLY OPERATION");
        assertThat(config.getSystemPrompt()).contains("glob", "grep", "read_file", "list_files");
        assertThat(config.getSystemPrompt()).contains("Issue independent grep and read operations in parallel");
        assertThat(config.hasRail(SysOperationRail.class)).isTrue();
        assertThat(config.hasRail(SecurityRail.class)).isTrue();
        assertThat(config.getRails().stream().filter(SecurityRail.class::isInstance).map(SecurityRail.class::cast)
                .allMatch(SecurityRail::isReadOnly)).isTrue();
        assertThat(config.getMetadata()).containsEntry("readonly", true).containsEntry("write_tools_forbidden", true)
                .containsEntry("allowed_shell_intent", "read_only");
        assertThat((List<String>) config.getMetadata().get("recommended_tools")).contains("glob", "grep", "read_file",
                "list_files", "bash");
    }

    @Test
    void createCodeResearchAndVerificationAgentsShouldReturnDeepAgents() {
        Workspace workspace = Workspace.builder().rootPath("./repo").language("en").build();

        DeepAgent code = CodeAgentFactory.createCodeAgent("en", workspace);
        DeepAgent research = ResearchAgentFactory.createResearchAgent("en", workspace);
        DeepAgent verification = VerificationAgentFactory.createVerificationAgent("en", workspace);

        assertThat(code.getCard().getName()).isEqualTo("code_agent");
        assertThat(research.getCard().getName()).isEqualTo("research_agent");
        assertThat(verification.getCard().getName()).isEqualTo("verification_agent");
        assertThat(code.getConfig().getSystemPrompt()).contains("AI Coding Agent", "don't guess file contents");
        assertThat(research.getConfig().getSystemPrompt()).contains("research assistant",
                "Only return the final research results");
        assertThat(verification.getConfig().getSystemPrompt()).contains("adversarial verification specialist")
                .contains("Command run").contains("VERDICT: PASS");
        assertThat(verification.getConfig().getMaxIterations()).isEqualTo(40);
        assertThat(code.getConfig().getRails().stream().map(Object::getClass).toList()).contains(SysOperationRail.class,
                AgentModeRail.class, AskUserRail.class, ConfirmInterruptRail.class, CodingMemoryRail.class);
        assertThat(code.getConfig().getSubagents()).hasSize(2);
    }

    @Test
    void buildCodeAgentConfigShouldExposeBuiltInPlanningAndExplorationSubagents() {
        SubAgentConfig config = CodeAgentFactory.buildCodeAgentConfig("en");

        assertThat(config.getAgentCard().getDescription()).contains("senior software engineer");
        assertThat(config.getSystemPrompt()).contains("Use tools whenever possible");
        assertThat(config.getMaxIterations()).isEqualTo(15);
        assertThat(config.getMetadata()).containsEntry("requires_ask_user", true)
                .containsEntry("requires_confirm_interrupt", true).containsEntry("supports_coding_memory", true)
                .containsEntry("enable_task_planning", true);
        assertThat(config.hasRail(SysOperationRail.class)).isTrue();
        assertThat(config.hasRail(AgentModeRail.class)).isTrue();
        assertThat(config.hasRail(AskUserRail.class)).isTrue();
        assertThat(config.hasRail(ConfirmInterruptRail.class)).isTrue();
        assertThat(config.hasRail(CodingMemoryRail.class)).isTrue();
        assertThat(config.getSubagents()).hasSize(2)
                .allSatisfy(item -> assertThat(item).isInstanceOf(SubAgentConfig.class));
        assertThat(config.getSubagents().stream().map(SubAgentConfig.class::cast).map(SubAgentConfig::getFactoryName))
                .containsExactly("explore_agent", "plan_agent");
    }

    @Test
    void buildCodeAgentConfigShouldApplyEmbeddingConfigToCodingMemoryRail() {
        EmbeddingConfig embeddingConfig =
            new EmbeddingConfig("embedding-test", "https://example.invalid/embeddings", "test-key");

        SubAgentConfig config = CodeAgentFactory.buildCodeAgentConfig("en", Map.of("embedding_config", embeddingConfig,
                "coding_memory_dir", "/tmp/code-memory", "coding_memory_proactive", false));

        CodingMemoryRail rail = config.getRails().stream().filter(CodingMemoryRail.class::isInstance)
                .map(CodingMemoryRail.class::cast).findFirst().orElseThrow();
        assertThat(rail.embeddingConfig()).isSameAs(embeddingConfig);
        assertThat(rail.isProactive()).isFalse();
        assertThat(rail.codingMemoryDir()).isEqualTo("/tmp/code-memory");
        assertThat(config.getFactoryKwargs()).containsEntry("embedding_config", embeddingConfig);
    }

    @Test
    void buildCodeAgentConfigShouldMergeCustomRailsWithRequiredRailsByDefault() {
        MemoryRail memoryRail = new MemoryRail();
        SysOperationRail customSysOperationRail = new SysOperationRail();

        SubAgentConfig config = CodeAgentFactory.buildCodeAgentConfig("en",
                Map.of("custom_rails", List.of(memoryRail, customSysOperationRail)));

        assertThat(config.getRails()).contains(memoryRail, customSysOperationRail);
        assertThat(config.getRails().get(0)).isSameAs(memoryRail);
        assertThat(config.getRails().get(1)).isSameAs(customSysOperationRail);
        assertThat(config.getRails().stream().filter(SysOperationRail.class::isInstance)).hasSize(1);
        assertThat(config.hasRail(AgentModeRail.class)).isTrue();
        assertThat(config.hasRail(AskUserRail.class)).isTrue();
        assertThat(config.hasRail(ConfirmInterruptRail.class)).isTrue();
        assertThat(config.hasRail(CodingMemoryRail.class)).isTrue();
    }

    @Test
    void buildCodeAgentConfigShouldSupportAppendAndReplaceRailMergeModes() {
        MemoryRail appended = new MemoryRail();
        SubAgentConfig appendConfig = CodeAgentFactory.buildCodeAgentConfig("en",
                Map.of("custom_rails", List.of(appended), "rails_merge_mode", "append"));

        assertThat(appendConfig.getRails().get(0)).isInstanceOf(SysOperationRail.class);
        assertThat(appendConfig.getRails().get(appendConfig.getRails().size() - 1)).isSameAs(appended);

        SkillUseRail replacement = new SkillUseRail();
        SubAgentConfig replaceConfig = CodeAgentFactory.buildCodeAgentConfig("en",
                Map.of("custom_rails", List.of(replacement), "rails_merge_mode", "replace"));

        assertThat(replaceConfig.getRails()).containsExactly(replacement);
        assertThat(replaceConfig.hasRail(SysOperationRail.class)).isFalse();
        assertThat(replaceConfig.hasRail(CodingMemoryRail.class)).isFalse();
    }

    @Test
    void builtInSubagentFactoriesShouldSupportCustomRailMergeKwargs() {
        MemoryRail planMemory = new MemoryRail();
        MemoryRail exploreMemory = new MemoryRail();
        MemoryRail researchMemory = new MemoryRail();
        MemoryRail verificationMemory = new MemoryRail();

        SubAgentConfig plan = PlanAgentFactory.buildPlanAgentConfig("en", Map.of("custom_rails", List.of(planMemory)));
        SubAgentConfig explore =
            ExploreAgentFactory.buildExploreAgentConfig("en", Map.of("custom_rails", List.of(exploreMemory)));
        SubAgentConfig research =
            ResearchAgentFactory.buildResearchAgentConfig("en", Map.of("custom_rails", List.of(researchMemory)));
        SubAgentConfig verification = VerificationAgentFactory.buildVerificationAgentConfig("en",
                Map.of("custom_rails", List.of(verificationMemory)));

        assertThat(plan.getRails()).contains(planMemory);
        assertThat(plan.hasRail(SysOperationRail.class)).isTrue();
        assertThat(plan.hasRail(SecurityRail.class)).isTrue();
        assertThat(explore.getRails()).contains(exploreMemory);
        assertThat(explore.hasRail(SysOperationRail.class)).isTrue();
        assertThat(explore.hasRail(SecurityRail.class)).isTrue();
        assertThat(research.getRails()).contains(researchMemory);
        assertThat(research.hasRail(SysOperationRail.class)).isTrue();
        assertThat(verification.getRails()).contains(verificationMemory);
        assertThat(verification.hasRail(SysOperationRail.class)).isTrue();
        assertThat(verification.hasRail(com.openjiuwen.harness.rails.VerificationRail.class)).isTrue();
        assertThat(verification.getFactoryKwargs()).containsKey("custom_rails");
    }

    @Test
    void builtInSubagentFactoriesShouldApplyCommonFactoryKwargsOverrides() {
        ToolCard toolCard = ToolCard.builder().name("inspect").description("inspect").build();
        McpServerConfig mcp = McpServerConfig.builder().serverId("mcp-a").serverName("MCP A").clientType("stdio")
                .serverPath("stdio://fixture").build();
        Object model = new Object();
        Object backend = new Object();
        SysOperation sysOperation = new SysOperation(SysOperationCard.builder().id("sys-a").name("sys-a").build());

        Map<String, Object> kwargs = new LinkedHashMap<>();
        kwargs.put("name", "custom_research");
        kwargs.put("description", "custom desc");
        kwargs.put("system_prompt", "custom prompt");
        kwargs.put("max_iterations", 7);
        kwargs.put("enable_task_loop", true);
        kwargs.put("workspace_path", "/tmp/custom-work");
        kwargs.put("restrict_to_work_dir", false);
        kwargs.put("prompt_mode", "minimal");
        kwargs.put("skill_mode", "auto_list");
        kwargs.put("skills", List.of("java", "research"));
        kwargs.put("skill_directories", List.of("/tmp/skills"));
        kwargs.put("tools", List.of(toolCard));
        kwargs.put("mcps", List.of(mcp));
        kwargs.put("model", model);
        kwargs.put("backend", backend);
        kwargs.put("sys_operation", sysOperation);
        kwargs.put("metadata", Map.of("priority", "high"));

        SubAgentConfig config = ResearchAgentFactory.buildResearchAgentConfig("en", kwargs);

        DeepAgentConfig deepConfig = config.toDeepAgentConfig();

        assertThat(config.getAgentCard().getName()).isEqualTo("custom_research");
        assertThat(config.getAgentCard().getDescription()).isEqualTo("custom desc");
        assertThat(config.getSystemPrompt()).isEqualTo("custom prompt");
        assertThat(config.getMaxIterations()).isEqualTo(7);
        assertThat(config.isEnableTaskLoop()).isTrue();
        assertThat(config.getWorkspacePath()).isEqualTo("/tmp/custom-work");
        assertThat(config.isRestrictToWorkDir()).isFalse();
        assertThat(config.getPromptMode()).isEqualTo("minimal");
        assertThat(config.getSkillMode()).isEqualTo("auto_list");
        assertThat(config.getSkills()).containsExactly("java", "research");
        assertThat(config.getSkillDirectories()).containsExactly("/tmp/skills");
        assertThat(config.getTools()).containsExactly(toolCard);
        assertThat(config.getMcps()).containsExactly(mcp);
        assertThat(config.getModel()).isSameAs(model);
        assertThat(config.getBackend()).isSameAs(backend);
        assertThat(config.getSysOperation()).isSameAs(sysOperation);
        assertThat(config.getMetadata()).containsEntry("priority", "high");
        assertThat(deepConfig.getSysOperation()).isSameAs(sysOperation);
        assertThat(deepConfig.getSkillMode()).isEqualTo("auto_list");
    }

    @Test
    void codingMemoryRailShouldKeepEmbeddingConfigInRuntimeToolContext() {
        EmbeddingConfig embeddingConfig =
            new EmbeddingConfig("embedding-test", "https://example.invalid/embeddings", "test-key");
        SubAgentConfig config = CodeAgentFactory.buildCodeAgentConfig("en",
                Map.of("embedding_config", embeddingConfig, "coding_memory_dir", "/tmp/code-memory"));
        CodingMemoryRail rail = config.getRails().stream().filter(CodingMemoryRail.class::isInstance)
                .map(CodingMemoryRail.class::cast).findFirst().orElseThrow();
        DeepAgent agent = com.openjiuwen.harness.factory.HarnessFactory.createDeepAgent(config.getAgentCard(),
                config.toDeepAgentConfig(), Workspace.builder().rootPath("./repo").language("en").build());

        agent.ensureInitialized();

        assertThat(rail.embeddingConfig()).isSameAs(embeddingConfig);
        assertThat(java.nio.file.Path.of(rail.codingMemoryDir()).toAbsolutePath().normalize())
                .isEqualTo(java.nio.file.Path.of("/tmp/code-memory").toAbsolutePath().normalize());
    }

    @Test
    void buildBrowserAgentConfigShouldExposePythonDirectControlPromptDefaults() {
        com.openjiuwen.harness.tools.browser.BrowserRuntimeSettings settings =
            com.openjiuwen.harness.tools.browser.BrowserRuntimeSettings.builder().build();

        SubAgentConfig config = BrowserAgentFactory.buildBrowserAgentConfig(settings, "en");

        assertThat(config.getAgentCard().getName()).isEqualTo("browser_agent");
        assertThat(config.getAgentCard().getDescription()).contains("Playwright MCP tools");
        assertThat(config.getSystemPrompt()).contains("browser automation agent")
                .contains("Plan and decide at this agent level")
                .contains("Do not assume a nested browser worker or browser_run_task wrapper exists");
        assertThat(config.getFactoryKwargs()).containsEntry("settings", settings);
    }

    @Test
    void subAgentConfigShouldPreserveRuntimeWiringFields() {
        Object model = new Object();
        Object backend = new Object();
        SubAgentConfig config = SubAgentConfig.builder()
                .agentCard(com.openjiuwen.core.singleagent.schema.AgentCard.builder().name("worker").description("d")
                        .build())
                .model(model).backend(backend).promptMode("compact").skills(List.of("java", "testing"))
                .skillDirectories(List.of("/repo/skills")).factoryKwargs(Map.of("embedding_config", "local")).build();

        DeepAgentConfig deepConfig = config.toDeepAgentConfig();

        assertThat(deepConfig.getModel()).isSameAs(model);
        assertThat(deepConfig.getBackend()).isSameAs(backend);
        assertThat(deepConfig.getPromptMode()).isEqualTo("compact");
        assertThat(deepConfig.getSkills()).containsExactly("java", "testing");
        assertThat(deepConfig.getSkillDirectories()).containsExactly("/repo/skills");
        assertThat(deepConfig.getFactoryKwargs()).containsEntry("embedding_config", "local");
    }

    @Test
    void createConfiguredSubagentShouldRetainRuntimeWiringFields() {
        Object model = new Object();
        Object backend = new Object();
        SubAgentConfig worker = SubAgentConfig.builder()
                .agentCard(com.openjiuwen.core.singleagent.schema.AgentCard.builder().name("worker").description("d")
                        .build())
                .model(model).backend(backend).promptMode("compact").skills(List.of("java"))
                .factoryKwargs(Map.of("backend_mode", "isolated")).build();
        DeepAgent parent = com.openjiuwen.harness.factory.HarnessFactory
                .createDeepAgent(DeepAgentConfig.builder().workspacePath("./repo").subagents(List.of(worker)).build());

        DeepAgent child = parent.createSubagent("worker", "session-a");

        assertThat(child.getConfig().getModel()).isSameAs(model);
        assertThat(child.getConfig().getBackend()).isSameAs(backend);
        assertThat(child.getConfig().getPromptMode()).isEqualTo("compact");
        assertThat(child.getConfig().getSkills()).containsExactly("java");
        assertThat(child.getConfig().getFactoryKwargs()).containsEntry("backend_mode", "isolated");
    }

    @Test
    void createConfiguredSubagentShouldApplyConcreteModelToRuntimeAgent() {
        Model model = org.mockito.Mockito.mock(Model.class);
        SubAgentConfig worker = SubAgentConfig.builder().agentCard(
                com.openjiuwen.core.singleagent.schema.AgentCard.builder().name("worker").description("d").build())
                .model(model).build();
        DeepAgent parent = com.openjiuwen.harness.factory.HarnessFactory
                .createDeepAgent(DeepAgentConfig.builder().workspacePath("./repo").subagents(List.of(worker)).build());

        DeepAgent child = parent.createSubagent("worker", "session-a");

        assertThat(child.getAgent().peekLlm()).isSameAs(model);
    }

    @Test
    void createConfiguredSubagentShouldApplyTypedModelAndBackendConfigs() {
        ModelRequestConfig modelConfig =
            ModelRequestConfig.builder().modelName("gpt-test").temperature(0.2).topP(0.9).maxTokens(128).build();
        ModelClientConfig backendConfig = ModelClientConfig.builder().clientProvider("openai").apiKey("test-key")
                .apiBase("https://example.invalid/v1").verifySsl(false).headers(Map.of("x-test", "1")).build();
        SubAgentConfig worker = SubAgentConfig.builder().agentCard(
                com.openjiuwen.core.singleagent.schema.AgentCard.builder().name("worker").description("d").build())
                .model(modelConfig).backend(backendConfig).build();
        DeepAgent parent = com.openjiuwen.harness.factory.HarnessFactory
                .createDeepAgent(DeepAgentConfig.builder().workspacePath("./repo").subagents(List.of(worker)).build());

        DeepAgent child = parent.createSubagent("worker", "session-a");

        ReActAgentConfig runtimeConfig = (ReActAgentConfig) child.getAgent().getConfig();
        assertThat(runtimeConfig.getModelConfigObj()).isSameAs(modelConfig);
        assertThat(runtimeConfig.getModelName()).isEqualTo("gpt-test");
        assertThat(runtimeConfig.getModelClientConfig()).isSameAs(backendConfig);
        assertThat(runtimeConfig.getModelProvider()).isEqualTo("openai");
        assertThat(runtimeConfig.getApiBase()).isEqualTo("https://example.invalid/v1");
    }

    @Test
    void createConfiguredSubagentShouldApplyPromptModeToRuntimePromptBuilder() {
        SubAgentConfig worker = SubAgentConfig.builder().agentCard(
                com.openjiuwen.core.singleagent.schema.AgentCard.builder().name("worker").description("d").build())
                .systemPrompt("base identity").promptMode("none").build();
        DeepAgent parent = com.openjiuwen.harness.factory.HarnessFactory
                .createDeepAgent(DeepAgentConfig.builder().workspacePath("./repo").subagents(List.of(worker)).build());

        DeepAgent child = parent.createSubagent("worker", "session-a");
        child.getAgent().addPromptBuilderSection("tools", "tool guidance", 20);

        ReActAgentConfig runtimeConfig = (ReActAgentConfig) child.getAgent().getConfig();
        assertThat(runtimeConfig.getPromptMode()).isEqualTo("none");
        assertThat(child.getAgent().getPromptBuilder().getMode()).isEqualTo("none");
        assertThat(child.getAgent().getPromptBuilder().build()).isEqualTo("base identity");
    }

    @Test
    void createConfiguredSubagentShouldInheritParentRuntimeFallbacks() {
        Model model = org.mockito.Mockito.mock(Model.class);
        ModelClientConfig backendConfig = ModelClientConfig.builder().clientProvider("openai").apiKey("test-key")
                .apiBase("https://example.invalid/v1").build();
        SubAgentConfig worker = SubAgentConfig.builder().agentCard(
                com.openjiuwen.core.singleagent.schema.AgentCard.builder().name("worker").description("d").build())
                .systemPrompt("base identity").build();
        DeepAgent parent = com.openjiuwen.harness.factory.HarnessFactory
                .createDeepAgent(DeepAgentConfig.builder().workspacePath("./repo").model(model).backend(backendConfig)
                        .promptMode("minimal").subagents(List.of(worker)).build());

        DeepAgent child = parent.createSubagent("worker", "session-a");

        ReActAgentConfig runtimeConfig = (ReActAgentConfig) child.getAgent().getConfig();
        assertThat(child.getConfig().getModel()).isSameAs(model);
        assertThat(child.getConfig().getBackend()).isSameAs(backendConfig);
        assertThat(child.getConfig().getPromptMode()).isEqualTo("minimal");
        assertThat(child.getAgent().peekLlm()).isSameAs(model);
        assertThat(runtimeConfig.getModelClientConfig()).isSameAs(backendConfig);
        assertThat(runtimeConfig.getPromptMode()).isEqualTo("minimal");
        assertThat(child.getAgent().getPromptBuilder().getMode()).isEqualTo("minimal");
    }
}

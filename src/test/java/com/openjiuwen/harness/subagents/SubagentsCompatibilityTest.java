package com.openjiuwen.harness.subagents;

import com.openjiuwen.core.foundation.store.EmbeddingConfig;
import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.foundation.tool.mcp.McpServerConfig;
import com.openjiuwen.core.singleagent.agents.ReActAgentConfig;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.core.sysop.SysOperation;
import com.openjiuwen.core.sysop.SysOperationCard;
import com.openjiuwen.harness.DeepAgent;
import com.openjiuwen.harness.rails.AgentModeRail;
import com.openjiuwen.harness.rails.CodingMemoryRail;
import com.openjiuwen.harness.rails.DeepAgentRail;
import com.openjiuwen.harness.rails.MemoryRail;
import com.openjiuwen.harness.rails.SecurityRail;
import com.openjiuwen.harness.rails.SkillUseRail;
import com.openjiuwen.harness.rails.SysOperationRail;
import com.openjiuwen.harness.rails.interrupt.AskUserRail;
import com.openjiuwen.harness.rails.interrupt.ConfirmRail;
import com.openjiuwen.harness.rails.subagent.VerificationRail;
import com.openjiuwen.harness.schema.DeepAgentConfig;
import com.openjiuwen.harness.tools.browser_move.playwright_runtime.RuntimeSettings;
import com.openjiuwen.harness.workspace.Workspace;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

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
        assertThat(config.getRails().stream()
                .filter(SecurityRail.class::isInstance)
                .map(SecurityRail.class::cast)
                .allMatch(SecurityRail::isReadOnly)).isTrue();
        assertThat(config.getMetadata())
                .containsEntry("readonly", true)
                .containsEntry("write_tools_forbidden", true)
                .containsEntry("requires_critical_files", true)
                .containsEntry("critical_files_min", 3)
                .containsEntry("critical_files_max", 5);
        assertThat((List<String>) config.getMetadata().get("forbidden_operations"))
                .contains("write_file", "edit_file", "git commit");
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
        assertThat(config.getRails().stream()
                .filter(SecurityRail.class::isInstance)
                .map(SecurityRail.class::cast)
                .allMatch(SecurityRail::isReadOnly)).isTrue();
        assertThat(config.getMetadata())
                .containsEntry("readonly", true)
                .containsEntry("write_tools_forbidden", true)
                .containsEntry("allowed_shell_intent", "read_only");
        assertThat((List<String>) config.getMetadata().get("recommended_tools"))
                .contains("glob", "grep", "read_file", "list_files", "bash");
    }

    @Test
    void createCodeResearchAndVerificationAgentsShouldReturnDeepAgents() {
        com.openjiuwen.harness.DeepAgent code = CodeAgentFactory.createCodeAgent((Object) null);
        com.openjiuwen.harness.DeepAgent research = ResearchAgentFactory.createResearchAgent((Object) null);
        com.openjiuwen.harness.DeepAgent verification = VerificationAgentFactory.createVerificationAgent((Object) null);

        assertThat(code.getCard().getName()).isEqualTo("code_agent");
        assertThat(research.getCard().getName()).isEqualTo("research_agent");
        assertThat(verification.getCard().getName()).isEqualTo("verification_agent");
        assertThat(code.deepConfig().getSystemPrompt()).contains("AI Coding Agent", "don't guess file contents");
        assertThat(research.deepConfig().getSystemPrompt()).contains("research assistant", "Only return the final research results");
        assertThat(verification.deepConfig().getSystemPrompt())
                .contains("adversarial verification specialist")
                .contains("Command run")
                .contains("VERDICT: PASS");
        assertThat(verification.deepConfig().getMaxIterations()).isEqualTo(40);
        assertThat(code.getRails().stream().map(Object::getClass).map(Class::getSimpleName).toList())
                .contains("SysOperationRail", "AgentModeRail", "AskUserRail", "ConfirmRail");
    }

    @Test
    void buildCodeAgentConfigShouldExposeBuiltInPlanningAndExplorationSubagents() {
        DeepAgentConfig.SubAgentConfig config = CodeAgentFactory.buildCodeAgentConfig((Object) null);

        assertThat(config.getDescription()).contains("senior software engineer");
        assertThat(config.getSystemPrompt()).contains("Use tools whenever possible");
        assertThat(config.getMaxIterations()).isEqualTo(15);
        assertThat(config.getFactoryName()).isEqualTo("code_agent");
        assertThat(config.getRails().stream().anyMatch(SysOperationRail.class::isInstance)).isTrue();
    }

    @Test
    void buildCodeAgentConfigShouldApplyEmbeddingConfigToCodingMemoryRail() {
        EmbeddingConfig embeddingConfig = new EmbeddingConfig(
                "embedding-test",
                "https://example.invalid/embeddings",
                "test-key"
        );

        DeepAgentConfig.SubAgentConfig config = CodeAgentFactory.buildCodeAgentConfig(
                null, null, null, null, null, null, false, 15,
                null, null, null, null, "en", null, embeddingConfig
        );

        CodingMemoryRail rail = config.getRails().stream()
                .filter(CodingMemoryRail.class::isInstance)
                .map(CodingMemoryRail.class::cast)
                .findFirst()
                .orElse(null);
        assertThat(rail).isNotNull();
        assertThat(rail.codingMemoryDir()).isNotNull();
        assertThat(config.getFactoryKwargs()).containsEntry("embedding_config", embeddingConfig);
    }

    @Test
    void buildCodeAgentConfigShouldMergeCustomRailsWithRequiredRailsByDefault() {
        MemoryRail memoryRail = new MemoryRail();
        SysOperationRail customSysOperationRail = new SysOperationRail();
        List<DeepAgentRail> customRails = List.of(memoryRail, customSysOperationRail);

        DeepAgentConfig.SubAgentConfig config = CodeAgentFactory.buildCodeAgentConfig(
                null, null, null, null, null, customRails, false, 15,
                null, null, null, null, "en", null, null
        );

        assertThat(config.getRails()).contains(memoryRail, customSysOperationRail);
        assertThat(config.getRails().stream().filter(SysOperationRail.class::isInstance)).hasSize(1);
    }

    @Test
    void buildCodeAgentConfigShouldSupportAppendAndReplaceRailMergeModes() {
        MemoryRail appended = new MemoryRail();
        List<DeepAgentRail> appendRails = List.of(appended);

        DeepAgentConfig.SubAgentConfig appendConfig = CodeAgentFactory.buildCodeAgentConfig(
                null, null, null, null, null, appendRails, false, 15,
                null, null, null, null, "en", null, null
        );

        assertThat(appendConfig.getRails().stream().anyMatch(SysOperationRail.class::isInstance)).isTrue();

        SkillUseRail replacement = new SkillUseRail();
        List<DeepAgentRail> replaceRails = List.of(replacement);

        DeepAgentConfig.SubAgentConfig replaceConfig = CodeAgentFactory.buildCodeAgentConfig(
                null, null, null, null, null, replaceRails, false, 15,
                null, null, null, null, "en", null, null
        );

        assertThat(replaceConfig.getRails()).contains(replacement);
    }

    @Test
    void builtInSubagentFactoriesShouldSupportCustomRailMergeKwargs() {
        MemoryRail planMemory = new MemoryRail();
        MemoryRail exploreMemory = new MemoryRail();

        SubAgentConfig plan = PlanAgentFactory.buildPlanAgentConfig("en", Map.of("custom_rails", List.of(planMemory)));
        SubAgentConfig explore = ExploreAgentFactory.buildExploreAgentConfig("en", Map.of("custom_rails", List.of(exploreMemory)));

        assertThat(plan.getRails()).contains(planMemory);
        assertThat(plan.hasRail(SysOperationRail.class)).isTrue();
        assertThat(plan.hasRail(SecurityRail.class)).isTrue();
        assertThat(explore.getRails()).contains(exploreMemory);
        assertThat(explore.hasRail(SysOperationRail.class)).isTrue();
        assertThat(explore.hasRail(SecurityRail.class)).isTrue();
    }

    @Test
    void builtInSubagentFactoriesShouldApplyCommonFactoryKwargsOverrides() {
        ToolCard toolCard = ToolCard.builder().name("inspect").description("inspect").build();
        McpServerConfig mcp = McpServerConfig.builder()
                .serverId("mcp-a")
                .serverName("MCP A")
                .clientType("stdio")
                .serverPath("stdio://fixture")
                .build();
        Object model = new Object();
        Object backend = new Object();
        SysOperation sysOperation = new SysOperation(SysOperationCard.builder().id("sys-a").name("sys-a").build());

        DeepAgentConfig.SubAgentConfig config = ResearchAgentFactory.buildResearchAgentConfig(
                model,
                AgentCard.builder().name("custom_research").description("custom desc").build(),
                "custom prompt",
                null,
                List.of(mcp),
                null,
                false,
                7,
                null,
                null,
                backend,
                sysOperation,
                "en",
                "minimal"
        );

        assertThat(config.getName()).isEqualTo("custom_research");
        assertThat(config.getDescription()).isEqualTo("custom desc");
        assertThat(config.getSystemPrompt()).isEqualTo("custom prompt");
        assertThat(config.getMaxIterations()).isEqualTo(7);
        assertThat(config.isEnableTaskLoop()).isFalse();
        assertThat(config.getModel()).isSameAs(model);
        assertThat(config.getBackend()).isSameAs(backend);
        assertThat(config.getSysOperation()).isSameAs(sysOperation);
        assertThat(config.getFactoryName()).isEqualTo("research_agent");
    }

    @Test
    void codingMemoryRailShouldKeepEmbeddingConfigInRuntimeToolContext() {
        EmbeddingConfig embeddingConfig = new EmbeddingConfig(
                "embedding-test",
                "https://example.invalid/embeddings",
                "test-key"
        );
        DeepAgentConfig.SubAgentConfig config = CodeAgentFactory.buildCodeAgentConfig(
                null, null, null, null, null, null, false, 15,
                null, null, null, null, "en", null, embeddingConfig
        );
        CodingMemoryRail rail = config.getRails().stream()
                .filter(CodingMemoryRail.class::isInstance)
                .map(CodingMemoryRail.class::cast)
                .findFirst()
                .orElseThrow();
        com.openjiuwen.harness.DeepAgent agent = new com.openjiuwen.harness.DeepAgent(config.getCard());
        agent.configure(config.getConfig());

        assertThat(rail.codingMemoryDir()).isNotNull();
    }

    @Test
    void buildBrowserAgentConfigShouldExposePythonDirectControlPromptDefaults() {
        RuntimeSettings settings = new RuntimeSettings();

        DeepAgentConfig.SubAgentConfig config = BrowserAgentFactory.buildBrowserAgentConfig(null, null, null, null, null, null, settings, "en", false, 25);

        assertThat(config.getName()).isEqualTo("browser_agent");
        assertThat(config.getDescription()).contains("Playwright MCP tools");
        assertThat(config.getSystemPrompt())
                .contains("browser automation agent");
        assertThat(config.getFactoryKwargs()).containsEntry("settings", settings);
    }

    @Test
    void subAgentConfigShouldPreserveRuntimeWiringFields() {
        Object model = new Object();
        Object backend = new Object();
        SubAgentConfig config = SubAgentConfig.builder()
                .agentCard(AgentCard.builder().name("worker").description("d").build())
                .model(model)
                .backend(backend)
                .promptMode("compact")
                .skills(List.of("java", "testing"))
                .skillDirectories(List.of("/repo/skills"))
                .factoryKwargs(Map.of("embedding_config", "local"))
                .build();

        com.openjiuwen.harness.schema.config.DeepAgentConfig deepConfig = config.toDeepAgentConfig();

        assertThat(deepConfig.getModel()).isSameAs(model);
        assertThat(deepConfig.getBackend()).isSameAs(backend);
        assertThat(deepConfig.getPromptMode()).isEqualTo("compact");
        assertThat(deepConfig.getSkills()).containsExactly("java", "testing");
        assertThat(deepConfig.getSkillDirectories()).containsExactly("/repo/skills");
        assertThat(deepConfig.getFactoryKwargs()).containsEntry("embedding_config", "local");
    }

    @Test
    @Tag("system-test")
    void createConfiguredSubagentShouldRetainRuntimeWiringFields() {
        Object model = new Object();
        Object backend = new Object();
        SubAgentConfig worker = SubAgentConfig.builder()
                .agentCard(AgentCard.builder().name("worker").description("d").build())
                .model(model)
                .backend(backend)
                .promptMode("compact")
                .skills(List.of("java"))
                .factoryKwargs(Map.of("backend_mode", "isolated"))
                .build();
        com.openjiuwen.harness.deep_agent.DeepAgent parent = com.openjiuwen.harness.factory.HarnessFactory.createDeepAgent(
                com.openjiuwen.harness.schema.config.DeepAgentConfig.builder()
                        .workspacePath("./repo")
                        .subagents(List.of(worker))
                        .build()
        );

        com.openjiuwen.harness.deep_agent.DeepAgent child = parent.createSubagent("worker", "session-a");

        assertThat(child.getConfig().getModel()).isSameAs(model);
        assertThat(child.getConfig().getBackend()).isSameAs(backend);
        assertThat(child.getConfig().getPromptMode()).isEqualTo("compact");
        assertThat(child.getConfig().getSkills()).containsExactly("java");
        assertThat(child.getConfig().getFactoryKwargs()).containsEntry("backend_mode", "isolated");
    }

    @Test
    @Tag("system-test")
    void createConfiguredSubagentShouldApplyConcreteModelToRuntimeAgent() {
        Model model = org.mockito.Mockito.mock(Model.class);
        SubAgentConfig worker = SubAgentConfig.builder()
                .agentCard(AgentCard.builder().name("worker").description("d").build())
                .model(model)
                .build();
        com.openjiuwen.harness.deep_agent.DeepAgent parent = com.openjiuwen.harness.factory.HarnessFactory.createDeepAgent(
                com.openjiuwen.harness.schema.config.DeepAgentConfig.builder()
                        .workspacePath("./repo")
                        .subagents(List.of(worker))
                        .build()
        );

        com.openjiuwen.harness.deep_agent.DeepAgent child = parent.createSubagent("worker", "session-a");

        assertThat(child.getAgent().getLlm()).isSameAs(model);
    }

    @Test
    @Tag("system-test")
    void createConfiguredSubagentShouldApplyTypedModelAndBackendConfigs() {
        ModelRequestConfig modelConfig = ModelRequestConfig.builder()
                .modelName("gpt-test")
                .temperature(0.2)
                .topP(0.9)
                .maxTokens(128)
                .build();
        ModelClientConfig backendConfig = ModelClientConfig.builder()
                .clientProvider("openai")
                .apiKey("test-key")
                .apiBase("https://example.invalid/v1")
                .verifySsl(false)
                .headers(Map.of("x-test", "1"))
                .build();
        SubAgentConfig worker = SubAgentConfig.builder()
                .agentCard(AgentCard.builder().name("worker").description("d").build())
                .model(modelConfig)
                .backend(backendConfig)
                .build();
        com.openjiuwen.harness.deep_agent.DeepAgent parent = com.openjiuwen.harness.factory.HarnessFactory.createDeepAgent(
                com.openjiuwen.harness.schema.config.DeepAgentConfig.builder()
                        .workspacePath("./repo")
                        .subagents(List.of(worker))
                        .build()
        );

        com.openjiuwen.harness.deep_agent.DeepAgent child = parent.createSubagent("worker", "session-a");

        ReActAgentConfig runtimeConfig = (ReActAgentConfig) child.getAgent().getConfig();
        assertThat(runtimeConfig.getModelConfigObj()).isSameAs(modelConfig);
        assertThat(runtimeConfig.getModelName()).isEqualTo("gpt-test");
        assertThat(runtimeConfig.getModelClientConfig()).isSameAs(backendConfig);
        assertThat(runtimeConfig.getModelProvider()).isEqualTo("openai");
        assertThat(runtimeConfig.getApiBase()).isEqualTo("https://example.invalid/v1");
    }

    @Test
    @Tag("system-test")
    void createConfiguredSubagentShouldApplyPromptModeToRuntimePromptBuilder() {
        SubAgentConfig worker = SubAgentConfig.builder()
                .agentCard(AgentCard.builder().name("worker").description("d").build())
                .systemPrompt("base identity")
                .promptMode("none")
                .build();
        com.openjiuwen.harness.deep_agent.DeepAgent parent = com.openjiuwen.harness.factory.HarnessFactory.createDeepAgent(
                com.openjiuwen.harness.schema.config.DeepAgentConfig.builder()
                        .workspacePath("./repo")
                        .subagents(List.of(worker))
                        .build()
        );

        com.openjiuwen.harness.deep_agent.DeepAgent child = parent.createSubagent("worker", "session-a");
        child.getAgent().addPromptBuilderSection("tools", "tool guidance", 20);

        ReActAgentConfig runtimeConfig = (ReActAgentConfig) child.getAgent().getConfig();
        assertThat(runtimeConfig.getPromptTemplateName()).isEqualTo("none");
        assertThat(child.getAgent().getPromptBuilder().build()).isNotNull();
    }

    @Test
    @Tag("system-test")
    void createConfiguredSubagentShouldInheritParentRuntimeFallbacks() {
        Model model = org.mockito.Mockito.mock(Model.class);
        ModelClientConfig backendConfig = ModelClientConfig.builder()
                .clientProvider("openai")
                .apiKey("test-key")
                .apiBase("https://example.invalid/v1")
                .build();
        SubAgentConfig worker = SubAgentConfig.builder()
                .agentCard(AgentCard.builder().name("worker").description("d").build())
                .systemPrompt("base identity")
                .build();
        com.openjiuwen.harness.deep_agent.DeepAgent parent = com.openjiuwen.harness.factory.HarnessFactory.createDeepAgent(
                com.openjiuwen.harness.schema.config.DeepAgentConfig.builder()
                        .workspacePath("./repo")
                        .model(model)
                        .backend(backendConfig)
                        .promptMode("minimal")
                        .subagents(List.of(worker))
                        .build()
        );

        com.openjiuwen.harness.deep_agent.DeepAgent child = parent.createSubagent("worker", "session-a");

        ReActAgentConfig runtimeConfig = (ReActAgentConfig) child.getAgent().getConfig();
        assertThat(child.getConfig().getModel()).isSameAs(model);
        assertThat(child.getConfig().getBackend()).isSameAs(backendConfig);
        assertThat(child.getConfig().getPromptMode()).isEqualTo("minimal");
        assertThat(child.getAgent().getLlm()).isSameAs(model);
        assertThat(runtimeConfig.getModelClientConfig()).isSameAs(backendConfig);
        assertThat(runtimeConfig.getPromptTemplateName()).isEqualTo("minimal");
    }
}

/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.core.singleagent.legacy;

import com.openjiuwen.core.foundation.llm.schema.BaseModelInfo;
import com.openjiuwen.core.foundation.llm.schema.ModelConfig;
import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.foundation.tool.function.LocalFunction;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.runner.base.TagMatchStrategy;
import com.openjiuwen.core.singleagent.legacy.config.AgentConfig;
import com.openjiuwen.core.singleagent.legacy.config.ConstrainConfig;
import com.openjiuwen.core.singleagent.legacy.config.LLMCallConfig;
import com.openjiuwen.core.singleagent.legacy.config.LegacyReActAgentConfig;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.core.workflow.Workflow;
import com.openjiuwen.core.workflow.WorkflowCard;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

/**
 * Backward compatibility tests for legacy imports.
 *
 * <p>Mirrors Python's {@code tests/unit_tests/agent/test_backward_compatibility.py}.
 */
@DisplayName("Backward Compatibility")
class BackwardCompatibilityTest {

    @BeforeEach
    void setUp() {
        Runner.start();
    }

    @AfterEach
    void tearDown() {
        Runner.stop();
    }

    @Test
    @DisplayName("test_old_imports_issue_warnings")
    void testOldImportsIssueWarnings() {
        AgentConfig agentConfig = new AgentConfig();
        LLMCallConfig llmCallConfig = new LLMCallConfig();
        ConstrainConfig constrainConfig = new ConstrainConfig();

        assertThat(agentConfig).isNotNull();
        assertThat(llmCallConfig).isNotNull();
        assertThat(constrainConfig.getMaxIteration()).isEqualTo(ConstrainConfig.DEFAULT_MAX_ITERATION);
        assertThat(LegacyApi.class.isAnnotationPresent(Deprecated.class)).isTrue();
        assertThat(LegacyApi.class.getAnnotation(Deprecated.class).forRemoval()).isTrue();
    }

    @Test
    @DisplayName("test_new_imports_no_warning")
    void testNewImportsNoWarning() {
        AgentCard card = AgentCard.builder()
                .name("test")
                .description("test")
                .build();
        assertThat(card).isNotNull();
        assertThat(card.getName()).isEqualTo("test");
        assertThat(AgentCard.class.isAnnotationPresent(Deprecated.class)).isFalse();
    }

    @Test
    @DisplayName("test_legacy_module_imports_issue_warnings")
    void testLegacyModuleImportsIssueWarnings() {
        LegacyReActAgentConfig config = new LegacyReActAgentConfig();
        assertThat(config).isNotNull();
        assertThat(LegacyApi.class.isAnnotationPresent(Deprecated.class)).isTrue();
        assertThat(deprecatedMethod("createReActAgentConfig", String.class, String.class, String.class,
                ModelConfig.class, List.class)).isNotNull();
    }

    @Test
    @DisplayName("test_react_agent_old_style_construction")
    void testReactAgentOldStyleConstruction() {
        LegacyReActAgentConfig config = LegacyReActAgentConfig.builder()
                .id("test_agent")
                .version("1.0")
                .description("Test Agent")
                .model(modelConfig())
                .build();

        LegacyReActAgent agent = new LegacyReActAgent(config);

        assertThat(agent.getAgentConfig().getId()).isEqualTo("test_agent");
        assertThat(agent.getAgentConfig().getVersion()).isEqualTo("1.0");
    }

    @Test
    @DisplayName("test_react_agent_with_tools_parameter")
    void testReactAgentWithToolsParameter() {
        LegacyReActAgentConfig config = LegacyReActAgentConfig.builder()
                .id("test_agent")
                .version("1.0")
                .model(modelConfig())
                .build();

        LegacyReActAgent agent = new LegacyReActAgent(config, List.of(), List.of());

        assertThat(agent).isNotNull();
        assertThat(agent.getAgentConfig().getId()).isEqualTo("test_agent");
    }

    @Test
    @DisplayName("test_add_tools_method_works")
    void testAddToolsMethodWorks() {
        LegacyReActAgent agent = new LegacyReActAgent(legacyConfig("tool_agent"));
        Tool addTool = addTool();

        assertThatNoException().isThrownBy(() -> agent.addTools(List.of(addTool)));

        assertThat(agent.getAgentConfig().getTools()).contains("add");
        assertThat(Runner.resourceMgr().getTool("add", "tool_agent", TagMatchStrategy.ALL)).isSameAs(addTool);
    }

    @Test
    @DisplayName("test_add_workflows_method_works")
    void testAddWorkflowsMethodWorks() {
        LegacyReActAgent agent = new LegacyReActAgent(legacyConfig("workflow_agent"));
        Workflow workflow = new Workflow(WorkflowCard.builder()
                .id("legacy_workflow")
                .name("legacy_workflow")
                .version("1.0")
                .build());

        assertThatNoException().isThrownBy(() -> agent.addWorkflows(List.of(workflow)));

        assertThat(Runner.resourceMgr().getWorkflow("legacy_workflow", "workflow_agent", TagMatchStrategy.ALL))
                .isNotNull();
    }

    @Test
    @DisplayName("test_deprecation_warning_contains_migration_info")
    void testDeprecationWarningContainsMigrationInfo() {
        Deprecated deprecated = LegacyApi.class.getAnnotation(Deprecated.class);

        assertThat(deprecated).isNotNull();
        assertThat(deprecated.since()).isEqualTo("0.1.7");
        assertThat(deprecated.forRemoval()).isTrue();
        assertThatNoException().isThrownBy(() ->
                LegacyApi.emitDeprecationWarning("LegacyReActAgentConfig", "ReActAgent"));
    }

    @Test
    @DisplayName("test_create_react_agent_config_issues_warning")
    void testCreateReactAgentConfigIssuesWarning() {
        Method factory = deprecatedMethod("createReActAgentConfig", String.class, String.class, String.class,
                ModelConfig.class, List.class);

        assertThat(factory.getAnnotation(Deprecated.class)).isNotNull();
        assertThatNoException().isThrownBy(() -> LegacyApi.createReActAgentConfig(
                "test",
                "1.0",
                "test",
                modelConfig(),
                List.of()
        ));
    }

    @Test
    @DisplayName("test_create_react_agent_config_works")
    void testCreateReactAgentConfigWorks() {
        LegacyReActAgentConfig config = LegacyApi.createReActAgentConfig(
                "test",
                "1.0",
                "test",
                modelConfig(),
                List.of()
        );

        assertThat(config.getId()).isEqualTo("test");
        assertThat(config.getVersion()).isEqualTo("1.0");
        assertThat(config.getDescription()).isEqualTo("test");
    }

    @Test
    @DisplayName("test_old_and_new_apis_coexist")
    void testOldAndNewApisCoexist() {
        LegacyReActAgent oldAgent = new LegacyReActAgent(legacyConfig("old_agent"));
        com.openjiuwen.core.singleagent.agents.ReActAgent newAgent =
                new com.openjiuwen.core.singleagent.agents.ReActAgent(
                        AgentCard.builder().id("new_agent").name("new_agent").build());

        assertThat(oldAgent).isInstanceOf(BaseAgent.class);
        assertThat(newAgent.getCard().getId()).isEqualTo("new_agent");
    }

    private static LegacyReActAgentConfig legacyConfig(String id) {
        return LegacyReActAgentConfig.builder()
                .id(id)
                .version("1.0")
                .description("Legacy agent test")
                .model(modelConfig())
                .build();
    }

    private static ModelConfig modelConfig() {
        return new ModelConfig(
                "OpenAI",
                BaseModelInfo.builder()
                        .modelName("gpt-4")
                        .apiKey("test-key")
                        .apiBase("https://api.openai.com/v1")
                        .build()
        );
    }

    private static Tool addTool() {
        return new LocalFunction(
                ToolCard.builder()
                        .id("add")
                        .name("add")
                        .description("addition")
                        .inputParams(Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "a", Map.of("type", "number"),
                                        "b", Map.of("type", "number")
                                ),
                                "required", List.of("a", "b")
                        ))
                        .build(),
                inputs -> ((Number) inputs.get("a")).intValue() + ((Number) inputs.get("b")).intValue()
        );
    }

    private static Method deprecatedMethod(String name, Class<?>... parameterTypes) {
        try {
            Method method = LegacyApi.class.getMethod(name, parameterTypes);
            assertThat(method.isAnnotationPresent(Deprecated.class)).isTrue();
            return method;
        } catch (NoSuchMethodException e) {
            throw new AssertionError("Expected deprecated method not found: " + name, e);
        }
    }
}

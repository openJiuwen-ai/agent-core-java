/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.schema;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.openjiuwen.agent_teams.agent.AgentConfigurator.AgentCard;
import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.harness.workspace.Workspace;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Focused parity tests for serializable DeepAgent specs.
 *
 * <p>Mirrors Python's {@code DeepAgentSpec} tests for
 * {@code openjiuwen/agent_teams/schema/deep_agent_spec.py}.</p>
 */
class DeepAgentSpecTest {

    @Test
    void teamModelConfigBuildConstructsModelWithProvidedConfigs() {
        ModelClientConfig client = new ModelClientConfig();
        client.setClientProvider("openai");
        client.setApiKey("test-key");
        client.setApiBase("https://example.invalid/v1");
        ModelRequestConfig request = new ModelRequestConfig();
        request.setModelName("gpt-test");

        TeamModelConfig config = new TeamModelConfig(client, request);

        Model model = config.build();

        assertThat(model.getModelClientConfig()).isSameAs(client);
        assertThat(model.getModelConfig()).isSameAs(request);
    }

    @Test
    void railSpecInjectsLanguageAndSkillDirectories() {
        WorkspaceSpec workspaceSpec = new WorkspaceSpec();
        workspaceSpec.setRootPath(".");
        Workspace workspace = workspaceSpec.build();

        @SuppressWarnings("unchecked")
        Map<String, Object> built = (Map<String, Object>) new RailSpec("skill_use", Map.of())
                .build("en", workspace);

        assertThat(built).containsEntry("language", "en");
        assertThat((List<String>) built.get("skills_dir"))
                .contains("~/.openjiuwen/workspace/skills", "~/.claude/skills");
    }

    @Test
    void builtinToolSpecInjectsLanguageAndToolId() {
        DeepAgentSpecPackage.registerToolType("custom", values -> Map.copyOf(values));

        @SuppressWarnings("unchecked")
        Map<String, Object> built = (Map<String, Object>) new BuiltinToolSpec("custom", Map.of("x", 1))
                .build("cn", "agent.custom");

        assertThat(built).containsEntry("x", 1);
        assertThat(built).containsEntry("language", "cn");
        assertThat(built).containsEntry("tool_id", "agent.custom");
    }

    @Test
    void subAgentBuildResolvesToolsRailsAndWorkspace() {
        DeepAgentSpecPackage.registerToolType("subtool", values -> Map.copyOf(values));
        SubAgentSpec subAgent = new SubAgentSpec();
        subAgent.setAgentCard(new AgentCard("sub", "Sub", "desc"));
        subAgent.setSystemPrompt("help");
        subAgent.setTools(List.of(new BuiltinToolSpec("subtool", Map.of())));
        subAgent.setRails(List.of(new RailSpec("task_planning", Map.of())));
        WorkspaceSpec workspace = new WorkspaceSpec();
        workspace.setRootPath(".");
        subAgent.setWorkspaceSpec(workspace);

        SubAgentSpec.SubAgentBuildConfig built = subAgent.build(null, "en");

        assertThat(built.agentCard().getId()).isEqualTo("sub");
        assertThat(built.tools()).hasSize(1);
        assertThat(built.rails()).hasSize(1);
        assertThat(built.workspace()).isNotNull();
    }

    @Test
    void deepAgentBuildResolvesProgressiveToolArguments() {
        DeepAgentSpec spec = new DeepAgentSpec();
        spec.setCard(new AgentCard("agent", "Agent", "desc"));
        spec.setLanguage("en");
        spec.setSystemPrompt("system");
        spec.setTools(List.of(new BuiltinToolSpec("web_search", Map.of())));
        ProgressiveToolSpec progressive = new ProgressiveToolSpec();
        progressive.setAlwaysVisibleTools(List.of("read"));
        progressive.setDefaultVisibleTools(List.of("search"));
        progressive.setMaxLoadedTools(7);
        spec.setProgressiveTool(progressive);

        DeepAgentSpec.DeepAgentBuildConfig built = spec.build();

        assertThat(built.enableTaskLoop()).isTrue();
        assertThat(built.card().getId()).isEqualTo("agent");
        assertThat(built.tools()).hasSize(1);
        assertThat(built.progressiveToolKwargs())
                .containsEntry("progressive_tool_enabled", true)
                .containsEntry("progressive_tool_max_loaded_tools", 7);
    }

    @Test
    void unknownRailAndToolTypesFailFast() {
        assertThatThrownBy(() -> new RailSpec("missing", Map.of()).build("en", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown rail type");
        assertThatThrownBy(() -> new BuiltinToolSpec("missing", Map.of()).build("en", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown tool type");
    }
}

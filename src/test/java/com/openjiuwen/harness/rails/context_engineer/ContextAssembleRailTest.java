/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.rails.context_engineer;

import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.singleagent.agents.ReActAgent;
import com.openjiuwen.core.singleagent.prompts.PromptSection;
import com.openjiuwen.core.singleagent.rail.RunKind;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.harness.DeepAgent;
import com.openjiuwen.harness.prompts.sections.SectionName;
import com.openjiuwen.harness.rails.CallbackContext;
import com.openjiuwen.harness.schema.DeepAgentConfig;
import com.openjiuwen.harness.workspace.Workspace;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Focused parity tests for context assembly rail behavior.
 *
 * <p>Mirrors Python's {@code ContextAssembleRail} in
 * {@code openjiuwen/harness/rails/context_engineer/context_assemble_rail.py}.</p>
 */
class ContextAssembleRailTest {

    @Test
    void initResolvesBuilderAndAbilityManagerFromReactAgent() {
        DeepAgent deepAgent = deepAgentWithWorkspace(new Workspace("/repo/project", "en"));
        ReActAgent reactAgent = reactAgent("en");
        deepAgent.setReactAgent(reactAgent, true);
        ContextAssembleRail rail = new ContextAssembleRail();

        rail.init(deepAgent);

        assertThat(rail.getPriority()).isEqualTo(85);
        assertThat(rail.getSystemPromptBuilder()).isSameAs(reactAgent.getSystemPromptBuilder());
        assertThat(rail.getAbilityManager()).isSameAs(reactAgent.getAbilityManager());
    }

    @Test
    void beforeModelCallInjectsWorkspaceToolsAndContextSections() {
        DeepAgent deepAgent = deepAgentWithWorkspace(new Workspace("/repo/project", "en"));
        ReActAgent reactAgent = reactAgent("en");
        reactAgent.getAbilityManager().add(ToolCard.builder()
                .name("read_file")
                .description("Read a file")
                .build());
        deepAgent.setReactAgent(reactAgent, true);
        ContextAssembleRail rail = new ContextAssembleRail();
        rail.init(deepAgent);

        rail.beforeModelCall(new CallbackContext(deepAgent, Map.of("run_kind", "normal")));

        assertThat(reactAgent.getSystemPromptBuilder().getSection(SectionName.WORKSPACE))
                .map(section -> section.render("en"))
                .hasValueSatisfying(content -> assertThat(content).contains("Your working directory is: `/repo/project`"));
        assertThat(reactAgent.getSystemPromptBuilder().getSection(SectionName.TOOLS))
                .map(section -> section.render("en"))
                .hasValueSatisfying(content -> assertThat(content).contains("- read_file: Read a file"));
        assertThat(reactAgent.getSystemPromptBuilder().getSection(SectionName.CONTEXT))
                .map(PromptSection::getPriority)
                .hasValue(80);
    }

    @Test
    void missingWorkspaceRemovesWorkspaceAndContextButLeavesTools() {
        DeepAgent deepAgent = deepAgentWithWorkspace(null);
        ReActAgent reactAgent = reactAgent("cn");
        reactAgent.getSystemPromptBuilder().addSection(new PromptSection(
                SectionName.WORKSPACE,
                Map.of("cn", "workspace"),
                70));
        reactAgent.getSystemPromptBuilder().addSection(new PromptSection(
                SectionName.TOOLS,
                Map.of("cn", "tools"),
                30));
        reactAgent.getSystemPromptBuilder().addSection(new PromptSection(
                SectionName.CONTEXT,
                Map.of("cn", "context"),
                80));
        deepAgent.setReactAgent(reactAgent, true);
        ContextAssembleRail rail = new ContextAssembleRail();
        rail.init(deepAgent);

        rail.beforeModelCall(new CallbackContext(deepAgent, Map.of()));

        assertThat(reactAgent.getSystemPromptBuilder().hasSection(SectionName.WORKSPACE)).isFalse();
        assertThat(reactAgent.getSystemPromptBuilder().hasSection(SectionName.CONTEXT)).isFalse();
        assertThat(reactAgent.getSystemPromptBuilder().hasSection(SectionName.TOOLS)).isTrue();
    }

    @Test
    void heartbeatRunKindDisablesDailyMemory() {
        DeepAgent deepAgent = new DeepAgent();

        assertThat(ContextAssembleRail.includeDailyMemory(new CallbackContext(
                deepAgent,
                Map.of("run_kind", RunKind.HEARTBEAT)))).isFalse();
        assertThat(ContextAssembleRail.includeDailyMemory(new CallbackContext(
                deepAgent,
                Map.of("extra", Map.of("run_kind", "heartbeat"))))).isFalse();
        assertThat(ContextAssembleRail.includeDailyMemory(new CallbackContext(
                deepAgent,
                Map.of("run_kind", "normal")))).isTrue();
    }

    @Test
    void uninitRemovesWorkspaceAndContextSections() {
        DeepAgent deepAgent = deepAgentWithWorkspace(new Workspace("/repo/project", "cn"));
        ReActAgent reactAgent = reactAgent("cn");
        reactAgent.getSystemPromptBuilder().addSection(new PromptSection(
                SectionName.WORKSPACE,
                Map.of("cn", "workspace"),
                70));
        reactAgent.getSystemPromptBuilder().addSection(new PromptSection(
                SectionName.CONTEXT,
                Map.of("cn", "context"),
                80));
        deepAgent.setReactAgent(reactAgent, true);
        ContextAssembleRail rail = new ContextAssembleRail();
        rail.init(deepAgent);

        rail.uninit(deepAgent);

        assertThat(reactAgent.getSystemPromptBuilder().hasSection(SectionName.WORKSPACE)).isFalse();
        assertThat(reactAgent.getSystemPromptBuilder().hasSection(SectionName.CONTEXT)).isFalse();
        assertThat(rail.getSystemPromptBuilder()).isNull();
        assertThat(rail.getAbilityManager()).isNull();
    }

    private static DeepAgent deepAgentWithWorkspace(Object workspace) {
        DeepAgent agent = new DeepAgent();
        DeepAgentConfig config = new DeepAgentConfig();
        config.setWorkspace(workspace);
        agent.configure(config);
        return agent;
    }

    private static ReActAgent reactAgent(String language) {
        ReActAgent agent = new ReActAgent(new AgentCard("agent", "agent", "test"));
        agent.getSystemPromptBuilder().setLanguage(language);
        return agent;
    }
}

/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness;

import com.openjiuwen.core.context.schema.ContextEngineConfig;
import com.openjiuwen.core.singleagent.agents.ReActAgentConfig;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.harness.deep_agent.DeepAgent;
import com.openjiuwen.harness.schema.config.DeepAgentConfig;
import com.openjiuwen.harness.subagents.SubAgentConfig;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for subagent inheritance of the parent's context engine config and
 * per-subagent overrides.
 */
class SubagentContextInheritTest {

    private static final String SUBAGENT_TYPE = "research_agent";

    @Test
    void childInheritsParentContextConfig() {
        ContextEngineConfig parentContext = ContextEngineConfig.builder()
                .maxContextMessageNum(187)
                .defaultWindowRoundNum(7)
                .build();
        DeepAgent parent = parentWithSubagent(parentContext);
        DeepAgent child = parent.createSubagent(SUBAGENT_TYPE, "sub_session_ctx");

        ReActAgentConfig childRuntimeConfig = (ReActAgentConfig) child.getAgent().getConfig();

        assertThat(childRuntimeConfig.getContextEngineConfig())
                .as("the child runtime config carries the parent's context config")
                .isSameAs(parentContext);
        assertThat(childRuntimeConfig.getContextEngineConfig().getMaxContextMessageNum())
                .isEqualTo(187);
        assertThat(childRuntimeConfig.getContextEngineConfig().getDefaultWindowRoundNum())
                .isEqualTo(7);
    }

    @Test
    void explicitChildContextConfigWinsOverInheritance() {
        ContextEngineConfig parentContext = ContextEngineConfig.builder()
                .maxContextMessageNum(187)
                .build();
        ContextEngineConfig childContext = ContextEngineConfig.builder()
                .maxContextMessageNum(64)
                .build();
        DeepAgent parent = parentWithSubagent(parentContext);
        SubAgentConfig spec = SubAgentConfig.builder()
                .agentCard(new AgentCard(SUBAGENT_TYPE, SUBAGENT_TYPE, "override"))
                .systemPrompt("override prompt")
                .contextEngineConfig(childContext)
                .build();
        parent.getConfig().getSubagents().clear();
        parent.getConfig().getSubagents().add(spec);

        DeepAgent child = parent.createSubagent(SUBAGENT_TYPE, "sub_session_override");

        ReActAgentConfig childRuntimeConfig = (ReActAgentConfig) child.getAgent().getConfig();
        assertThat(childRuntimeConfig.getContextEngineConfig())
                .as("an explicit child config is not replaced by the parent's")
                .isSameAs(childContext);
        assertThat(childRuntimeConfig.getContextEngineConfig().getMaxContextMessageNum())
                .isEqualTo(64);
    }

    @Test
    void subAgentConfigCarriesContextFields() {
        ContextEngineConfig context = ContextEngineConfig.builder().maxContextMessageNum(64).build();
        SubAgentConfig spec = SubAgentConfig.builder()
                .contextEngineConfig(context)
                .build();

        assertThat(spec.getContextEngineConfig()).isSameAs(context);

        DeepAgentConfig converted = spec.toDeepAgentConfig();
        assertThat(converted.getContextEngineConfig()).isSameAs(context);
    }

    private static DeepAgent parentWithSubagent(ContextEngineConfig parentContextConfig) {
        com.openjiuwen.harness.schema.DeepAgentConfig legacy = new com.openjiuwen.harness.schema.DeepAgentConfig();
        legacy.setEnableTaskLoop(false);
        legacy.setPromptMode("default");
        legacy.setContextEngineConfig(parentContextConfig);

        com.openjiuwen.harness.schema.DeepAgentConfig.SubAgentConfig subSpec =
                new com.openjiuwen.harness.schema.DeepAgentConfig.SubAgentConfig(
                        SUBAGENT_TYPE, SUBAGENT_TYPE + " description", "research prompt");
        legacy.getSubagents().put(SUBAGENT_TYPE, subSpec);

        DeepAgent parent = new DeepAgent(new AgentCard("deep", "deep", "test"));
        parent.configure(legacy);
        return parent;
    }
}

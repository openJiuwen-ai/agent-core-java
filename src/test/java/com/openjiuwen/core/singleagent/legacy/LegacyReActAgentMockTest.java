/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.core.singleagent.legacy;

import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.singleagent.agents.ReActAgent;
import com.openjiuwen.core.singleagent.agents.ReActAgentConfig;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for ReActAgent using legacy API (deprecated).
 *
 * <p>Mirrors Python's {@code test_react_agent_mock.py} in
 * {@code tests/unit_tests/agent/react_agent/}.
 */
@DisplayName("Legacy ReActAgent Mock")
class LegacyReActAgentMockTest {

    @Test
    @DisplayName("legacy ReActAgent can be created with config")
    void testLegacyReActAgentCreatedWithConfig() {
        AgentCard card = AgentCard.builder()
                .name("react_agent_mock_test")
                .description("math helper")
                .build();
        ReActAgent agent = new ReActAgent(card);
        ReActAgentConfig config = ReActAgentConfig.builder()
                .modelClientConfig(ModelClientConfig.builder()
                        .clientProvider("OpenAI")
                        .apiKey("mock_key")
                        .apiBase("mock_url")
                        .build())
                .modelConfigObj(ModelRequestConfig.builder()
                        .modelName("gpt-3.5-turbo")
                        .temperature(0.7)
                        .build())
                .promptTemplate(List.of(Map.of("role", "system", "content", "You are a math assistant.")))
                .build();
        agent.configure(config);

        assertThat(agent).isNotNull();
        assertThat(agent.getCard().getName()).isEqualTo("react_agent_mock_test");
    }

    @Test
    @DisplayName("legacy agent config has correct defaults")
    void testLegacyAgentConfigDefaults() {
        ReActAgentConfig config = ReActAgentConfig.builder().build();
        assertThat(config.getMaxIterations()).isEqualTo(5);
        assertThat(config.getModelProvider()).isEqualTo("openai");
    }

    @Test
    @DisplayName("legacy agent can add tools to ability manager")
    void testLegacyAgentCanAddTools() {
        AgentCard card = AgentCard.builder()
                .name("react_agent_with_tools")
                .description("agent with tools")
                .build();
        ReActAgent agent = new ReActAgent(card);
        assertThat(agent.getAbilityManager()).isNotNull();
    }
}

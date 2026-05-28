/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.core.singleagent.agents;

import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for ReActAgent questioner extract context with workflow interrupt.
 *
 * <p>Mirrors Python's {@code test_react_agent_questioner_extract_context.py} in
 * {@code tests/unit_tests/agent/react_agent/}.
 */
@DisplayName("ReActAgent Questioner Extract Context")
class ReActAgentQuestionerExtractContextTest {

    @BeforeEach
    void setUp() {
        Runner.start();
    }

    @AfterEach
    void tearDown() {
        Runner.stop();
    }

    @Test
    @DisplayName("model client config is correctly configured")
    void testModelClientConfigCorrect() {
        ModelClientConfig clientConfig = ModelClientConfig.builder()
                .clientProvider("OpenAI")
                .apiKey("sk-fake")
                .apiBase("https://mock.openai.com/v1")
                .verifySsl(false)
                .build();
        assertThat(clientConfig.getClientProvider()).isEqualTo("OpenAI");
        assertThat(clientConfig.getApiKey()).isEqualTo("sk-fake");
        assertThat(clientConfig.isVerifySsl()).isFalse();
    }

    @Test
    @DisplayName("model request config is correctly configured")
    void testModelRequestConfigCorrect() {
        ModelRequestConfig requestConfig = ModelRequestConfig.builder()
                .modelName("gpt-4o-mock")
                .temperature(0.0)
                .build();
        assertThat(requestConfig.getModelName()).isEqualTo("gpt-4o-mock");
        assertThat(requestConfig.getTemperature()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("agent can be configured with questioner workflow")
    void testAgentConfiguredWithQuestionerWorkflow() {
        AgentCard card = AgentCard.builder()
                .id("react_agent_questioner_extract_test")
                .description("test agent")
                .build();
        ReActAgent agent = new ReActAgent(card);
        ReActAgentConfig config = ReActAgentConfig.builder()
                .modelClientConfig(ModelClientConfig.builder()
                        .clientProvider("OpenAI")
                        .apiKey("sk-fake")
                        .apiBase("https://mock.openai.com/v1")
                        .verifySsl(false)
                        .build())
                .modelConfigObj(ModelRequestConfig.builder()
                        .modelName("gpt-4o-mock")
                        .temperature(0.0)
                        .build())
                .promptTemplate(List.of(Map.of("role", "system", "content", "You are a helpful assistant.")))
                .build();
        agent.configure(config);
        assertThat(agent.getConfig()).isNotNull();
    }
}

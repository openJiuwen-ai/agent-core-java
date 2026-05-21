/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.core.singleagent.agents;

import com.openjiuwen.core.context.schema.ContextEngineConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for ReActAgent using new Card + Config pattern.
 *
 * <p>Mirrors Python's {@code test_new_react_agent_mock.py} in
 * {@code tests/unit_tests/agent/react_agent/}.
 */
@DisplayName("New ReActAgent Config")
class NewReActAgentMockTest {

    @Nested
    @DisplayName("ReActAgentConfig tests")
    class ConfigTests {

        @Test
        @DisplayName("config default values")
        void testConfigDefaultValues() {
            ReActAgentConfig config = ReActAgentConfig.builder().build();
            assertThat(config.getMemScopeId()).isEmpty();
            assertThat(config.getModelName()).isEmpty();
            assertThat(config.getModelProvider()).isEqualTo("openai");
            assertThat(config.getApiKey()).isEmpty();
            assertThat(config.getApiBase()).isEmpty();
            assertThat(config.getPromptTemplateName()).isEmpty();
            assertThat(config.getPromptTemplate()).isEmpty();
            assertThat(config.getMaxIterations()).isEqualTo(5);
        }

        @Test
        @DisplayName("chained configuration")
        void testConfigChainedConfiguration() {
            ReActAgentConfig config = ReActAgentConfig.builder().build();
            config.configureModel("gpt-4");
            config.configureModelProvider("openai", "test_key", "https://api.test.com");
            config.configurePromptTemplate(List.of(Map.of("role", "system", "content", "You are a helpful assistant.")));

            assertThat(config.getModelName()).isEqualTo("gpt-4");
            assertThat(config.getApiKey()).isEqualTo("test_key");
            assertThat(config.getApiBase()).isEqualTo("https://api.test.com");
            assertThat(config.getPromptTemplate()).hasSize(1);
        }

        @Test
        @DisplayName("configure model client")
        void testConfigureModelClient() {
            ReActAgentConfig config = ReActAgentConfig.builder().build();
            ModelClientConfig clientConfig = ModelClientConfig.builder()
                    .clientProvider("OpenAI")
                    .apiKey("sk-test")
                    .apiBase("https://api.openai.com/v1")
                    .verifySsl(false)
                    .build();
            ModelRequestConfig requestConfig = ModelRequestConfig.builder()
                    .modelName("gpt-4")
                    .temperature(0.7)
                    .build();

            config.setModelClientConfig(clientConfig);
            config.setModelConfigObj(requestConfig);

            assertThat(config.getModelClientConfig()).isNotNull();
            assertThat(config.getModelConfigObj()).isNotNull();
            assertThat(config.getModelClientConfig().getApiKey()).isEqualTo("sk-test");
            assertThat(config.getModelConfigObj().getModelName()).isEqualTo("gpt-4");
        }
    }

    @Nested
    @DisplayName("ReActAgent creation tests")
    class CreationTests {

        @Test
        @DisplayName("create agent with card")
        void testCreateAgentWithCard() {
            AgentCard card = AgentCard.builder()
                    .name("test_agent")
                    .description("Test agent")
                    .build();
            ReActAgent agent = new ReActAgent(card);
            assertThat(agent.getCard().getName()).isEqualTo("test_agent");
            assertThat(agent.getCard().getDescription()).isEqualTo("Test agent");
        }

        @Test
        @DisplayName("create agent with card and configure")
        void testCreateAgentWithCardAndConfigure() {
            AgentCard card = AgentCard.builder()
                    .name("test_agent")
                    .description("Test agent")
                    .build();
            ReActAgent agent = new ReActAgent(card);
            ReActAgentConfig config = ReActAgentConfig.builder()
                    .promptTemplate(List.of(Map.of("role", "system", "content", "You are a helpful assistant.")))
                    .maxIterations(10)
                    .build();
            agent.configure(config);
            assertThat(agent.getConfig()).isInstanceOf(ReActAgentConfig.class);
            ReActAgentConfig actualConfig = (ReActAgentConfig) agent.getConfig();
            assertThat(actualConfig.getMaxIterations()).isEqualTo(10);
        }
    }
}

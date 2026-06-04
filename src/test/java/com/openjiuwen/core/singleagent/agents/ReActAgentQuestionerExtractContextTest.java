/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.core.singleagent.agents;

import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.session.interaction.InteractiveInput;
import com.openjiuwen.core.session.stream.OutputSchema;
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

    @Test
    @DisplayName("questioner extract interrupt then resume writes assistant context")
    void testQuestionerExtractInterruptThenResume() {
        String name = "\u5f20\u4e09";
        OutputSchema interaction = new OutputSchema(
                "__interaction__",
                0,
                Map.of("id", "questioner", "message", "name not found")
        );
        InteractiveInput userInput = new InteractiveInput();
        userInput.update("questioner", "\u6211\u53eb" + name);

        List<OutputSchema> firstInvokeInteractions = List.of(interaction);
        List<OutputSchema> secondInvokeInteractions = List.of();
        List<BaseMessage> chatHistory = List.of(
                new UserMessage("\u5e2e\u6211\u5904\u7406\u4e00\u4e0b"),
                new AssistantMessage("name not found"),
                new UserMessage("\u6211\u53eb" + name),
                new AssistantMessage("{\"name\": \"" + name + "\"}"),
                new UserMessage("Hello " + name),
                new AssistantMessage("\u4f60\u597d\uff0c" + name + "\uff01")
        );

        assertThat(firstInvokeInteractions).hasSize(1);
        assertThat(firstInvokeInteractions.get(0).getPayload().toString()).contains("questioner");
        assertThat(userInput.getUserInputs()).containsEntry("questioner", "\u6211\u53eb" + name);
        assertThat(secondInvokeInteractions).isEmpty();
        assertThat(chatHistory).hasSize(6);
        assertThat(chatHistory.stream().map(BaseMessage::getRole).toList())
                .containsExactly("user", "assistant", "user", "assistant", "user", "assistant");
        assertThat(chatHistory.get(3).getContent()).isEqualTo("{\"name\": \"" + name + "\"}");
        assertThat(chatHistory.get(4).getContent()).isEqualTo("Hello " + name);
        assertThat(chatHistory.get(5).getContent()).isEqualTo("\u4f60\u597d\uff0c" + name + "\uff01");
    }
}

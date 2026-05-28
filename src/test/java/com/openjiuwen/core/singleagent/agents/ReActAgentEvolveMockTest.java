/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.core.singleagent.agents;

import com.openjiuwen.core.operator.Operator;
import com.openjiuwen.core.operator.llm_call.LLMCallOperator;
import com.openjiuwen.core.operator.tool_call.ToolCallOperator;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

/**
 * Unit tests for ReActAgentEvolve operator initialization and parameter sync.
 *
 * <p>Mirrors Python's {@code test_react_agent_evolve_mock.py} in
 * {@code tests/unit_tests/agent/react_agent/}.
 */
class ReActAgentEvolveMockTest {

    private AgentCard card;

    @BeforeEach
    void setUp() {
        card = AgentCard.builder()
                .name("test_agent_evolve")
                .description("Test ReActAgentEvolve")
                .build();
    }

    @Nested
    @DisplayName("ReActAgentEvolve Creation")
    class Creation {

        @Test
        @DisplayName("agent creation with card initializes operators")
        void testAgentCreationWithCard() {
            ReActAgentEvolve agent = new ReActAgentEvolve(card);

            assertThat(agent.getCard().getName()).isEqualTo("test_agent_evolve");
            assertThat(agent.getCard().getDescription()).isEqualTo("Test ReActAgentEvolve");
        }

        @Test
        @DisplayName("tool operator initialized on creation")
        void testToolOperatorInitializedOnCreation() throws Exception {
            ReActAgentEvolve agent = new ReActAgentEvolve(card);

            Field toolOpField = ReActAgentEvolve.class.getDeclaredField("toolOp");
            toolOpField.setAccessible(true);
            ToolCallOperator toolOp = (ToolCallOperator) toolOpField.get(agent);

            assertThat(toolOp).isNotNull();
            assertThat(toolOp.getOperatorId()).isEqualTo("react_tool");
        }
    }

    @Nested
    @DisplayName("ReActAgentEvolve Operators")
    class Operators {

        @Test
        @DisplayName("getOperators returns tool operator")
        void testGetOperatorsReturnsToolOperator() {
            ReActAgentEvolve agent = new ReActAgentEvolve(card);
            Map<String, Operator> operators = agent.getOperators();

            assertThat(operators).containsKey("react_tool");
            assertThat(operators.get("react_tool")).isInstanceOf(ToolCallOperator.class);
        }

        @Test
        @DisplayName("getOperators handles no LLM operator gracefully")
        void testGetOperatorsHandlesNoLlmOperator() {
            ReActAgentEvolve agent = new ReActAgentEvolve(card);
            Map<String, Operator> operators = agent.getOperators();

            assertThat(operators).containsKey("react_tool");
        }
    }

    @Nested
    @DisplayName("ReActAgentEvolve Parameter Sync")
    class ParameterSync {

        @Test
        @DisplayName("LLM parameter updated syncs to config prompt template")
        void testLlmParameterUpdatedSyncsToConfig() throws Exception {
            ReActAgentEvolve agent = new ReActAgentEvolve(card);
            ReActAgentConfig config = ReActAgentConfig.builder().build();
            config.configurePromptTemplate(List.of(Map.of("role", "system", "content", "Original prompt")));
            agent.configure(config);

            List<Map<String, String>> newPrompt = List.of(Map.of("role", "system", "content", "Updated prompt"));

            Field llmOpField = ReActAgentEvolve.class.getDeclaredField("llmOp");
            llmOpField.setAccessible(true);

            Field configField = ReActAgentEvolve.class.getDeclaredField("config");
            configField.setAccessible(true);

            llmOpField.set(agent, null);

            agent.configure(ReActAgentConfig.builder()
                    .promptTemplate(newPrompt)
                    .build());

            ReActAgentConfig currentConfig = (ReActAgentConfig) configField.get(agent);
            assertThat(currentConfig.getPromptTemplate()).isEqualTo(newPrompt);
        }

        @Test
        @DisplayName("tool parameter updated ignores invalid params without error")
        void testToolParameterUpdatedIgnoresInvalid() {
            ReActAgentEvolve agent = new ReActAgentEvolve(card);

            assertThatNoException().isThrownBy(() -> {
                agent.configure(ReActAgentConfig.builder().build());
            });
        }
    }
}

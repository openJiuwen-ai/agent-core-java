/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.core.singleagent.agents;

import com.openjiuwen.core.foundation.tool.ToolCard;
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
 * {@code tests/unit_tests/agent/react_agent/}.</p>
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
        void testAgentCreationWithCard() throws Exception {
            ReActAgentEvolve agent = new ReActAgentEvolve(card);

            assertThat(agent.getCard().getName()).isEqualTo("test_agent_evolve");
            assertThat(agent.getCard().getDescription()).isEqualTo("Test ReActAgentEvolve");
            assertThat(readField(agent, "llmOp")).isInstanceOf(LLMCallOperator.class);
            assertThat(readField(agent, "toolOp")).isInstanceOf(ToolCallOperator.class);
        }

        @Test
        @DisplayName("operators initialized on creation")
        void testOperatorsInitializedOnCreation() throws Exception {
            ReActAgentEvolve agent = new ReActAgentEvolve(card);

            LLMCallOperator llmOp = (LLMCallOperator) readField(agent, "llmOp");
            ToolCallOperator toolOp = (ToolCallOperator) readField(agent, "toolOp");

            assertThat(llmOp).isNotNull();
            assertThat(llmOp.getOperatorId()).isEqualTo("react_llm");
            assertThat(toolOp).isNotNull();
            assertThat(toolOp.getOperatorId()).isEqualTo("react_tool");
        }
    }

    @Nested
    @DisplayName("ReActAgentEvolve Operators")
    class Operators {

        @Test
        @DisplayName("getOperators returns both operators")
        void testGetOperatorsReturnsOperators() {
            ReActAgentEvolve agent = new ReActAgentEvolve(card);
            Map<String, Operator> operators = agent.getOperators();

            assertThat(operators).containsKeys("react_llm", "react_tool");
            assertThat(operators.get("react_llm")).isInstanceOf(LLMCallOperator.class);
            assertThat(operators.get("react_tool")).isInstanceOf(ToolCallOperator.class);
        }

        @Test
        @DisplayName("getOperators returns empty when operator fields are null")
        void testGetOperatorsReturnsEmptyWhenOperatorsNone() throws Exception {
            ReActAgentEvolve agent = new ReActAgentEvolve(card);
            writeField(agent, "llmOp", null);
            writeField(agent, "toolOp", null);

            assertThat(agent.getOperators()).isEmpty();
        }
    }

    @Nested
    @DisplayName("ReActAgentEvolve Parameter Sync")
    class ParameterSync {

        @Test
        @DisplayName("LLM parameter updated syncs list prompt to config")
        void testLlmParameterUpdatedSyncsToConfig() {
            ReActAgentEvolve agent = new ReActAgentEvolve(card);
            List<Map<String, String>> newPrompt = List.of(Map.of("role", "system", "content", "Updated prompt"));

            agent.getOperators().get("react_llm").setParameter("system_prompt", newPrompt);

            ReActAgentConfig currentConfig = (ReActAgentConfig) agent.getConfig();
            assertThat(currentConfig.getPromptTemplate()).isEqualTo(newPrompt);
        }

        @Test
        @DisplayName("LLM parameter updated converts string prompt to message list")
        void testLlmParameterUpdatedWithString() {
            ReActAgentEvolve agent = new ReActAgentEvolve(card);

            agent.getOperators().get("react_llm").setParameter("system_prompt", "String prompt");

            ReActAgentConfig currentConfig = (ReActAgentConfig) agent.getConfig();
            assertThat(currentConfig.getPromptTemplate())
                    .isEqualTo(List.of(Map.of("role", "system", "content", "String prompt")));
        }

        @Test
        @DisplayName("LLM parameter updated ignores non-system prompt params")
        void testLlmParameterUpdatedIgnoresOtherParams() {
            ReActAgentEvolve agent = new ReActAgentEvolve(card);
            List<Map<String, String>> originalPrompt = List.of(Map.of("role", "system", "content", "Original"));
            ((ReActAgentConfig) agent.getConfig()).setPromptTemplate(originalPrompt);

            agent.getOperators().get("react_llm").setParameter("user_prompt", "Some value");

            assertThat(((ReActAgentConfig) agent.getConfig()).getPromptTemplate()).isEqualTo(originalPrompt);
        }

        @Test
        @DisplayName("tool parameter updated syncs to ability manager")
        void testToolParameterUpdatedSyncsToAbilityManager() {
            ReActAgentEvolve agent = new ReActAgentEvolve(card);
            ToolCard toolCard = ToolCard.builder()
                    .id("tool1")
                    .name("tool1")
                    .description("Original desc")
                    .build();
            agent.getAbilityManager().add(toolCard);

            agent.getOperators().get("react_tool")
                    .setParameter("tool_description", Map.of("tool1", "Updated desc"));

            ToolCard updated = (ToolCard) agent.getAbilityManager().get("tool1");
            assertThat(updated.getDescription()).isEqualTo("Updated desc");
        }

        @Test
        @DisplayName("tool parameter updated ignores invalid params without error")
        void testToolParameterUpdatedIgnoresInvalid() {
            ReActAgentEvolve agent = new ReActAgentEvolve(card);

            assertThatNoException().isThrownBy(() -> {
                agent.getOperators().get("react_tool").setParameter("wrong_param", Map.of("tool1", "desc"));
                agent.getOperators().get("react_tool").setParameter("tool_description", "not a dict");
            });
        }
    }

    private static Object readField(Object target, String fieldName) throws Exception {
        Field field = ReActAgentEvolve.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(target);
    }

    private static void writeField(Object target, String fieldName, Object value) throws Exception {
        Field field = ReActAgentEvolve.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}

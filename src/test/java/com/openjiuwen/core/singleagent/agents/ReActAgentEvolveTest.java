/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent.agents;

import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.operator.Operator;
import com.openjiuwen.core.operator.llm_call.LLMCallOperator;
import com.openjiuwen.core.operator.tool_call.ToolCallOperator;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Focused missing-test parity coverage for {@link ReActAgentEvolve}.
 *
 * <p>Mirrors Python's {@code tests/unit_tests/agent/react_agent/test_react_agent_evolve_mock.py}.</p>
 */
class ReActAgentEvolveTest {

    @Test
    void agentCreationWithCardStoresCardFields() {
        ReActAgentEvolve agent = newAgent("test_agent_evolve", "Test ReActAgentEvolve");

        assertEquals("test_agent_evolve", agent.getCard().getName());
        assertEquals("Test ReActAgentEvolve", agent.getCard().getDescription());
        assertNotNull(agent.getLlmOperator());
        assertNotNull(agent.getToolOperator());
    }

    @Test
    void operatorsInitializedOnCreation() {
        ReActAgentEvolve agent = newAgent("test_agent", "Test agent");

        assertNotNull(agent.getLlmOperator());
        assertNotNull(agent.getToolOperator());
        assertInstanceOf(LLMCallOperator.class, agent.getLlmOperator());
        assertInstanceOf(ToolCallOperator.class, agent.getToolOperator());
        assertEquals("react_llm", agent.getLlmOperator().getOperatorId());
        assertEquals("react_tool", agent.getToolOperator().getOperatorId());
    }

    @Test
    void getOperatorsReturnsBothOperatorsInPythonOrder() {
        ReActAgentEvolve agent = newAgent("test_agent_evolve", "Test agent");

        Map<String, Operator> operators = agent.getOperators();

        assertEquals(List.of("react_tool", "react_llm"), List.copyOf(operators.keySet()));
        assertInstanceOf(ToolCallOperator.class, operators.get("react_tool"));
        assertInstanceOf(LLMCallOperator.class, operators.get("react_llm"));
    }

    @Test
    void getOperatorsReturnsEmptyWhenOperatorsAreNull() {
        ReActAgentEvolve agent = newAgent("test_agent_evolve", "Test agent");

        agent.setLlmOperatorForTest(null);
        agent.setToolOperatorForTest(null);

        assertTrue(agent.getOperators().isEmpty());
    }

    @Test
    void llmParameterUpdateSyncsListSystemPromptToConfig() {
        ReActAgentEvolve agent = newAgent("test_agent", "Test agent");
        List<Map<String, Object>> prompt = List.of(Map.of("role", "system", "content", "Updated prompt"));

        agent.onLlmParameterUpdated("system_prompt", prompt);

        assertEquals(prompt, agent.getConfig().getPromptTemplate());
    }

    @Test
    void llmParameterUpdateHandlesStringPrompt() {
        ReActAgentEvolve agent = newAgent("test_agent", "Test agent");

        agent.onLlmParameterUpdated("system_prompt", "String prompt");

        assertEquals(List.of(Map.of("role", "system", "content", "String prompt")),
                agent.getConfig().getPromptTemplate());
    }

    @Test
    void llmParameterUpdateIgnoresNonSystemPromptTarget() {
        ReActAgentEvolve agent = newAgent("test_agent", "Test agent");
        List<Map<String, Object>> prompt = List.of(Map.of("role", "system", "content", "Original"));
        agent.getConfig().setPromptTemplate(prompt);

        agent.onLlmParameterUpdated("user_prompt", "ignored");

        assertEquals(prompt, agent.getConfig().getPromptTemplate());
    }

    @Test
    void toolParameterUpdateSyncsDescriptionsToAbilityManager() {
        ReActAgentEvolve agent = newAgent("test_agent", "Test agent");
        ToolCard card = ToolCard.builder().name("tool1").description("Original desc").build();
        agent.getAbilityManager().add(card);

        agent.onToolParameterUpdated("tool_description", Map.of("tool1", "Updated desc"));

        assertEquals("Updated desc", card.getDescription());
    }

    @Test
    void toolParameterUpdateIgnoresInvalidInputs() {
        ReActAgentEvolve agent = newAgent("test_agent", "Test agent");

        agent.onToolParameterUpdated("wrong_param", Map.of("tool1", "desc"));
        agent.onToolParameterUpdated("tool_description", "not a dict");

        assertTrue(agent.getOperators().containsKey("react_tool"));
    }

    private static ReActAgentEvolve newAgent(String name, String description) {
        return new ReActAgentEvolve(new AgentCard(null, name, description));
    }
}

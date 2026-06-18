/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.single_agent.agents;

import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.operator.Operator;
import com.openjiuwen.core.operator.llm_call.LLMCallOperator;
import com.openjiuwen.core.operator.tool_call.ToolCallOperator;
import com.openjiuwen.core.single_agent.schema.AgentCard;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Focused parity tests for {@link ReActAgentEvolve}.
 *
 * <p>Mirrors Python's tests for
 * {@code openjiuwen/core/single_agent/agents/react_agent_evolve.py}.</p>
 */
class ReActAgentEvolveTest {

    @Test
    void creationInitializesOperators() {
        ReActAgentEvolve agent = new ReActAgentEvolve(new AgentCard(null, "test_agent_evolve",
                "Test ReActAgentEvolve"));

        assertEquals("test_agent_evolve", agent.getCard().getName());
        assertEquals("Test ReActAgentEvolve", agent.getCard().getDescription());
        assertNotNull(agent.getLlmOperator());
        assertNotNull(agent.getToolOperator());
        assertInstanceOf(LLMCallOperator.class, agent.getLlmOperator());
        assertInstanceOf(ToolCallOperator.class, agent.getToolOperator());
        assertEquals("react_llm", agent.getLlmOperator().getOperatorId());
        assertEquals("react_tool", agent.getToolOperator().getOperatorId());
    }

    @Test
    void getOperatorsReturnsBothOperatorsInPythonOrder() {
        ReActAgentEvolve agent = new ReActAgentEvolve(new AgentCard(null, "agent", "desc"));

        Map<String, Operator> operators = agent.getOperators();

        assertEquals(List.of("react_tool", "react_llm"), List.copyOf(operators.keySet()));
        assertInstanceOf(ToolCallOperator.class, operators.get("react_tool"));
        assertInstanceOf(LLMCallOperator.class, operators.get("react_llm"));
    }

    @Test
    void getOperatorsReturnsEmptyWhenOperatorsAreNull() {
        ReActAgentEvolve agent = new ReActAgentEvolve(new AgentCard(null, "agent", "desc"));

        agent.setLlmOperatorForTest(null);
        agent.setToolOperatorForTest(null);

        assertTrue(agent.getOperators().isEmpty());
    }

    @Test
    void llmParameterUpdateSyncsListAndStringSystemPromptToConfig() {
        ReActAgentEvolve agent = new ReActAgentEvolve(new AgentCard(null, "agent", "desc"));
        List<Map<String, Object>> prompt = List.of(Map.of("role", "system", "content", "Updated prompt"));

        agent.onLlmParameterUpdated("system_prompt", prompt);

        assertEquals(prompt, agent.getConfig().getPromptTemplate());

        agent.onLlmParameterUpdated("system_prompt", "String prompt");

        assertEquals(List.of(Map.of("role", "system", "content", "String prompt")),
                agent.getConfig().getPromptTemplate());
    }

    @Test
    void llmParameterUpdateIgnoresNonSystemPromptTarget() {
        ReActAgentEvolve agent = new ReActAgentEvolve(new AgentCard(null, "agent", "desc"));
        List<Map<String, Object>> prompt = List.of(Map.of("role", "system", "content", "Original"));
        agent.getConfig().setPromptTemplate(prompt);

        agent.onLlmParameterUpdated("user_prompt", "ignored");

        assertEquals(prompt, agent.getConfig().getPromptTemplate());
    }

    @Test
    void toolParameterUpdateSyncsDescriptionsToAbilityManager() {
        ReActAgentEvolve agent = new ReActAgentEvolve(new AgentCard(null, "agent", "desc"));
        ToolCard card = ToolCard.builder().name("tool1").description("Original desc").build();
        agent.getAbilityManager().add(card);

        agent.onToolParameterUpdated("tool_description", Map.of("tool1", "Updated desc"));

        assertEquals("Updated desc", card.getDescription());
    }

    @Test
    void toolParameterUpdateIgnoresInvalidInputs() {
        ReActAgentEvolve agent = new ReActAgentEvolve(new AgentCard(null, "agent", "desc"));

        agent.onToolParameterUpdated("wrong_param", Map.of("tool1", "desc"));
        agent.onToolParameterUpdated("tool_description", "not a dict");

        assertTrue(agent.getOperators().containsKey("react_tool"));
    }
}

/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.core.singleagent.agents;

import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.core.workflow.WorkflowCard;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for ReActAgent workflow interrupt/resume redesign.
 *
 * <p>Mirrors Python's {@code test_react_agent_workflow_interrupt_mock.py} in
 * {@code tests/unit_tests/agent/react_agent/}.
 */
@DisplayName("ReActAgent Workflow Interrupt Mock")
class ReActAgentWorkflowInterruptMockTest {

    @BeforeEach
    void setUp() {
        Runner.start();
    }

    @AfterEach
    void tearDown() {
        Runner.stop();
    }

    @Test
    @DisplayName("workflow card can be created for interrupt test")
    void testWorkflowCardCreatedForInterruptTest() {
        WorkflowCard card = WorkflowCard.builder()
                .id("wf_interrupt_test")
                .name("wf_interrupt_test")
                .version("1.0")
                .build();
        assertThat(card.getId()).isEqualTo("wf_interrupt_test");
    }

    @Test
    @DisplayName("agent can be configured with workflow abilities")
    void testAgentConfiguredWithWorkflowAbilities() {
        AgentCard card = AgentCard.builder()
                .id("react_agent_interrupt_test")
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
        assertThat(agent.getAbilityManager()).isNotNull();
    }
}

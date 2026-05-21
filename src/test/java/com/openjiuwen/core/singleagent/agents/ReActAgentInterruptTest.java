/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.core.singleagent.agents;

import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.core.workflow.WorkflowExecutionState;
import com.openjiuwen.core.workflow.WorkflowOutput;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for ReActAgent interruption/resume logic.
 *
 * <p>Mirrors Python's {@code test_react_agent_interrupt.py} in
 * {@code tests/unit_tests/agent/react_agent/}.
 */
@DisplayName("ReActAgent Interrupt")
class ReActAgentInterruptTest {

    @Nested
    @DisplayName("_isInterrupted")
    class IsInterruptedTests {

        @Test
        @DisplayName("detects INPUT_REQUIRED state")
        void testDetectsInputRequiredState() {
            WorkflowOutput wf = new WorkflowOutput(null, WorkflowExecutionState.INPUT_REQUIRED);
            assertThat(wf.getState()).isEqualTo(WorkflowExecutionState.INPUT_REQUIRED);
        }

        @Test
        @DisplayName("COMPLETED state is not interrupted")
        void testCompletedStateIsNotInterrupted() {
            WorkflowOutput wf = new WorkflowOutput(null, WorkflowExecutionState.COMPLETED);
            assertThat(wf.getState()).isEqualTo(WorkflowExecutionState.COMPLETED);
        }
    }

    @Nested
    @DisplayName("Agent construction for interrupt")
    class AgentConstructionTests {

        @Test
        @DisplayName("agent can be constructed with interrupt config")
        void testAgentConstructedWithInterruptConfig() {
            AgentCard card = AgentCard.builder().id("test_interrupt_agent").build();
            ReActAgent agent = new ReActAgent(card);
            ReActAgentConfig config = ReActAgentConfig.builder()
                    .modelClientConfig(ModelClientConfig.builder()
                            .clientProvider("OpenAI")
                            .apiKey("sk-fake")
                            .apiBase("https://api.openai.com/v1")
                            .verifySsl(false)
                            .build())
                    .modelConfigObj(ModelRequestConfig.builder()
                            .modelName("gpt-3.5-turbo")
                            .build())
                    .promptTemplate(List.of(Map.of("role", "system", "content", "You are a helpful assistant.")))
                    .build();
            agent.configure(config);
            assertThat(agent.getConfig()).isNotNull();
        }
    }
}

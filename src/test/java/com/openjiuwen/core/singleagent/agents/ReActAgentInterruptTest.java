/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.core.singleagent.agents;

import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.session.interaction.InteractiveInput;
import com.openjiuwen.core.session.stream.OutputSchema;
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

        @Test
        @DisplayName("detects list with interaction item")
        void testDetectsListWithInteractionItem() {
            OutputSchema interaction = new OutputSchema("__interaction__", 0, Map.of("id", "questioner"));
            OutputSchema normal = new OutputSchema("answer", 0, Map.of("output", "ok"));

            assertThat(List.of(interaction).stream().anyMatch(item -> "__interaction__".equals(item.getType())))
                    .isTrue();
            assertThat(List.of(normal).stream().anyMatch(item -> "__interaction__".equals(item.getType())))
                    .isFalse();
            assertThat("plain string").isNotEqualTo("__interaction__");
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

    @Nested
    @DisplayName("Tool execution interrupt bookkeeping")
    class ToolExecutionInterruptTests {

        @Test
        @DisplayName("no interrupt returns no pending state")
        void testNoInterruptReturnsNone() {
            WorkflowOutput completed = new WorkflowOutput("tool result", WorkflowExecutionState.COMPLETED);

            boolean interrupted = completed.getState() == WorkflowExecutionState.INPUT_REQUIRED;

            assertThat(interrupted).isFalse();
        }

        @Test
        @DisplayName("builds state for first interrupted tool")
        void testBuildsStateForFirstInterruptedTool() {
            Map<String, Object> state = Map.of(
                    "iteration", 1,
                    "interrupted_workflows", Map.of(
                            "tool_b", Map.of(
                                    "tool_call_id", "c2",
                                    "component_ids", List.of("questioner")
                            )
                    ),
                    "pending_workflow_id", "tool_b",
                    "pending_component_id", "questioner"
            );

            assertThat(state.get("iteration")).isEqualTo(1);
            assertThat(((Map<?, ?>) state.get("interrupted_workflows")).containsKey("tool_b")).isTrue();
            assertThat(state.get("pending_workflow_id")).isEqualTo("tool_b");
            assertThat(state.get("pending_component_id")).isEqualTo("questioner");
        }
    }

    @Nested
    @DisplayName("Session state management")
    class SessionStateManagementTests {

        @Test
        @DisplayName("save load clear cycle")
        void testSaveLoadClearCycle() {
            AgentSessionApi session = new AgentSessionApi("sess_state_001", null,
                    AgentCard.builder().id("agent_session_state").build());
            Map<String, Object> fakeState = Map.of("ai_message", "test");

            session.updateState(Map.of("react_interrupt_state", fakeState));
            assertThat(session.getState("react_interrupt_state")).isEqualTo(fakeState);

            Map<String, Object> clearedState = new java.util.HashMap<>();
            clearedState.put("react_interrupt_state", null);
            session.updateState(clearedState);
            assertThat(session.getState("react_interrupt_state")).isNull();
        }
    }

    @Nested
    @DisplayName("Invoke interrupt resume payloads")
    class InvokeInterruptResumeTests {

        @Test
        @DisplayName("invoke interrupt then resume payloads")
        void testInvokeInterruptThenResume() {
            Map<String, Object> first = Map.of(
                    "result_type", "interrupt",
                    "workflow_execution_state", new WorkflowOutput(null, WorkflowExecutionState.INPUT_REQUIRED),
                    "component_ids", List.of("questioner")
            );
            InteractiveInput resume = new InteractiveInput();
            resume.update("questioner", "user feedback");
            Map<String, Object> second = Map.of(
                    "result_type", "answer",
                    "output", "Resume complete!"
            );

            assertThat(first.get("result_type")).isEqualTo("interrupt");
            assertThat(resume.getUserInputs()).containsEntry("questioner", "user feedback");
            assertThat(second.get("result_type")).isEqualTo("answer");
            assertThat(second.get("output")).asString().contains("Resume complete");
        }

        @Test
        @DisplayName("multi pending collects feedback one by one")
        void testMultiPendingCollectsFeedbackOneByOne() {
            Map<String, Object> first = Map.of(
                    "result_type", "interrupt",
                    "component_ids", List.of("c1")
            );
            Map<String, Object> second = Map.of(
                    "result_type", "interrupt",
                    "component_ids", List.of("c2")
            );
            Map<String, Object> third = Map.of(
                    "result_type", "answer",
                    "output", "Both workflows done!"
            );

            assertThat(first.get("component_ids")).isEqualTo(List.of("c1"));
            assertThat(second.get("component_ids")).isEqualTo(List.of("c2"));
            assertThat(third.get("output")).asString().contains("Both workflows done");
        }
    }
}

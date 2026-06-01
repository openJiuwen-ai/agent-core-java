/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.core.application.workflow;

import com.openjiuwen.core.application.schema.WorkflowAgentConfig;
import com.openjiuwen.core.controller.schema.ControllerOutput;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.session.Session;
import com.openjiuwen.core.session.interaction.InteractionOutput;
import com.openjiuwen.core.session.interaction.InteractiveInput;
import com.openjiuwen.core.session.stream.OutputSchema;
import com.openjiuwen.core.session.stream.StreamMode;
import com.openjiuwen.core.workflow.Workflow;
import com.openjiuwen.core.workflow.WorkflowExecutionState;
import com.openjiuwen.core.workflow.WorkflowOutput;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * WorkflowAgent multi-workflow UT.
 *
 * <p>Mirrors Python's {@code test_mock_workflow_agent_multi_workflow.py} in
 * {@code tests/unit_tests/agent/workflow_agent/}.
 */
@DisplayName("WorkflowAgent Multi Workflow")
class MockWorkflowAgentMultiWorkflowTest {

    /**
     * Minimal mock session implementation for testing.
     */
    static class MockSession implements Session {
        private final String sessionId;
        private final Map<String, Object> state = new HashMap<>();

        MockSession(String sessionId) {
            this.sessionId = sessionId;
        }

        @Override
        public String getSessionId() {
            return sessionId;
        }

        @Override
        public Object getState(String key) {
            return state.get(key);
        }

        @Override
        public void updateState(Map<String, Object> newState) {
            state.putAll(newState);
        }
    }

    @BeforeEach
    void setUp() {
        Runner.start();
    }

    @AfterEach
    void tearDown() {
        Runner.stop();
    }

    @Test
    @DisplayName("invoke with single workflow returns answer")
    void testInvokeSingleWorkflow() {
        Workflow workflow = WorkflowTestHelper.buildSimpleWorkflow("test_multi_wf_1", "multi_wf_1");

        WorkflowAgentConfig config = WorkflowAgentConfig.builder()
                .id("test_multi_wf_agent")
                .version("1.0")
                .description("multi workflow test agent")
                .build();
        WorkflowAgent agent = new WorkflowAgent(config);
        agent.addWorkflows(List.of(workflow));

        String conversationId = UUID.randomUUID().toString();
        Session mockSession = new MockSession(conversationId);
        ControllerOutput output = agent.invoke(Map.of(
                "query", "hello",
                "conversation_id", conversationId
        ), mockSession);

        assertThat(output).isNotNull();
        assertAnswerResult(output, "hello");
    }

    @Test
    @DisplayName("invoke with multiple workflows returns answer")
    void testInvokeMultipleWorkflows() {
        Workflow workflow1 = WorkflowTestHelper.buildSimpleWorkflow("test_multi_wf_1", "multi_wf_1");
        Workflow workflow2 = WorkflowTestHelper.buildSimpleWorkflow("test_multi_wf_2", "multi_wf_2");

        WorkflowAgentConfig config = WorkflowAgentConfig.builder()
                .id("test_multi_wf_agent_2")
                .version("1.0")
                .description("multi workflow test agent 2")
                .build();
        WorkflowAgent agent = new WorkflowAgent(config);
        agent.addWorkflows(List.of(workflow1, workflow2));

        String conversationId = UUID.randomUUID().toString();
        Session mockSession = new MockSession(conversationId);
        ControllerOutput output = agent.invoke(Map.of(
                "query", "hello",
                "conversation_id", conversationId
        ), mockSession);

        assertThat(output).isNotNull();
        assertAnswerResult(output, "hello");
    }

    @Test
    @DisplayName("intent detection routes invoke to selected workflow")
    void testMultiWorkflowRouting() {
        MockWorkflowAgent.setMockResponses(MockWorkflowAgent.textResponse("{\"result\": 2}"));
        Workflow weather = MockWorkflowAgent.prefixedWorkflow(
                "weather_flow", "weather_query", "weather lookup", "weather:");
        Workflow stock = MockWorkflowAgent.prefixedWorkflow(
                "stock_flow", "stock_query", "stock lookup", "stock:");
        WorkflowAgent agent = MockWorkflowAgent.createAgentWithModel("test_multi_wf_routing_agent", weather, stock);

        ControllerOutput output = agent.invoke(Map.of(
                "query", "show stock trend",
                "conversation_id", UUID.randomUUID().toString()
        ), null);

        WorkflowOutput workflowOutput = MockWorkflowAgent.workflowOutput(output);
        assertThat(workflowOutput).isNotNull();
        assertThat(MockWorkflowAgent.responseFrom(workflowOutput)).contains("stock:");
        assertThat(MockWorkflowAgent.responseFrom(workflowOutput)).contains("show stock trend");
    }

    @Test
    @DisplayName("stream can jump between interrupted workflows and recover each one")
    void testMultiWorkflowJumpAndRecovery() {
        MockWorkflowAgent.setMockResponses(
                MockWorkflowAgent.textResponse("{\"result\": 1}"),
                MockWorkflowAgent.textResponse("{\"result\": 2}"),
                MockWorkflowAgent.textResponse("{\"result\": 1}"),
                MockWorkflowAgent.textResponse("{\"result\": 2}"));
        Workflow weather = MockWorkflowAgent.questionerWorkflow(
                "weather_flow_jump", "weather_query", "weather lookup", "Please provide location", "weather:");
        Workflow stock = MockWorkflowAgent.questionerWorkflow(
                "stock_flow_jump", "stock_query", "stock lookup", "Please provide stock code", "stock:");
        WorkflowAgent agent = MockWorkflowAgent.createAgentWithModel("test_jump_agent", weather, stock);
        String conversationId = "test_jump_recovery";

        List<Object> chunks1 = stream(agent, "weather please", conversationId);
        assertThat(MockWorkflowAgent.chunksOfType(chunks1, "__interaction__")).hasSize(1);

        List<Object> chunks2 = stream(agent, "stock please", conversationId);
        assertThat(MockWorkflowAgent.chunksOfType(chunks2, "__interaction__")).hasSize(1);

        List<Object> chunks3 = stream(agent, "Beijing weather", conversationId);
        assertFinalResponseContains(chunks3, "Beijing weather");

        List<Object> chunks4 = stream(agent, "AAPL stock", conversationId);
        assertFinalResponseContains(chunks4, "AAPL stock");
    }

    @Test
    @DisplayName("InteractiveInput resumes interrupted workflow without another intent detection")
    void testInteractiveInputFastPath() {
        MockWorkflowAgent.setMockResponses(MockWorkflowAgent.textResponse("{\"result\": 1}"));
        Workflow weather = MockWorkflowAgent.questionerWorkflow(
                "weather_flow_skip", "weather_query", "weather lookup", "Please provide location", "weather:");
        Workflow stock = MockWorkflowAgent.questionerWorkflow(
                "stock_flow_skip", "stock_query", "stock lookup", "Please provide stock code", "stock:");
        WorkflowAgent agent = MockWorkflowAgent.createAgentWithModel("test_interactive_skip", weather, stock);
        String conversationId = "test_interactive_fast_path";

        List<Object> chunks1 = stream(agent, "weather please", conversationId);
        List<OutputSchema> interactions = MockWorkflowAgent.chunksOfType(chunks1, "__interaction__");
        assertThat(interactions).hasSize(1);
        String nodeId = ((InteractionOutput) interactions.get(0).getPayload()).getId();

        InteractiveInput input = new InteractiveInput();
        input.update(nodeId, "Beijing");
        List<Object> chunks2 = MockWorkflowAgent.collect(agent.stream(
                Map.of("query", input, "conversation_id", conversationId),
                null,
                List.of(StreamMode.OUTPUT)));

        assertFinalResponseContains(chunks2, "Beijing");
    }

    @Test
    @DisplayName("InteractiveInput node id resumes the matching workflow")
    void testInteractiveInputTargetsCorrectWorkflow() {
        MockWorkflowAgent.setMockResponses(
                MockWorkflowAgent.textResponse("{\"result\": 1}"),
                MockWorkflowAgent.textResponse("{\"result\": 2}"));
        Workflow weather = MockWorkflowAgent.questionerWorkflow(
                "weather_flow_resume", "weather_query", "weather lookup",
                "weather_questioner", "Please provide location", "weather:");
        Workflow stock = MockWorkflowAgent.questionerWorkflow(
                "stock_flow_resume", "stock_query", "stock lookup",
                "stock_questioner", "Please provide stock code", "stock:");
        WorkflowAgent agent = MockWorkflowAgent.createAgentWithModel("test_precise_resume", weather, stock);
        String conversationId = "test_precise_resume";

        List<OutputSchema> firstInteractions = MockWorkflowAgent.interactionData(agent.invoke(
                Map.of("query", "weather please", "conversation_id", conversationId), null));
        List<OutputSchema> secondInteractions = MockWorkflowAgent.interactionData(agent.invoke(
                Map.of("query", "stock please", "conversation_id", conversationId), null));

        String weatherNode = ((InteractionOutput) firstInteractions.get(0).getPayload()).getId();
        String stockNode = ((InteractionOutput) secondInteractions.get(0).getPayload()).getId();
        assertThat(weatherNode).isEqualTo("weather_questioner");
        assertThat(stockNode).isEqualTo("stock_questioner");

        ControllerOutput weatherResume = agent.invoke(
                Map.of("query", interactiveInput(weatherNode, "Beijing"), "conversation_id", conversationId), null);
        ControllerOutput stockResume = agent.invoke(
                Map.of("query", interactiveInput(stockNode, "AAPL"), "conversation_id", conversationId), null);

        assertThat(MockWorkflowAgent.responseFrom(MockWorkflowAgent.workflowOutput(weatherResume))).contains("Beijing");
        assertThat(MockWorkflowAgent.responseFrom(MockWorkflowAgent.workflowOutput(stockResume))).contains("AAPL");
    }

    private static void assertAnswerResult(ControllerOutput output, String expectedQuery) {
        assertThat(output.getType()).isEqualTo("task_completion");
        Map<String, Object> result = output.getDataAsMap();
        assertThat(result).isNotNull();
        assertThat(result.get("result_type")).isEqualTo("answer");
        assertThat(result.get("output")).isInstanceOf(WorkflowOutput.class);

        WorkflowOutput workflowOutput = (WorkflowOutput) result.get("output");
        assertThat(workflowOutput.getState()).isEqualTo(WorkflowExecutionState.COMPLETED);
        assertThat(String.valueOf(workflowOutput.getResult())).contains(expectedQuery);
    }

    private static List<Object> stream(WorkflowAgent agent, String query, String conversationId) {
        return MockWorkflowAgent.collect(agent.stream(
                Map.of("query", query, "conversation_id", conversationId),
                null,
                List.of(StreamMode.OUTPUT)));
    }

    @SuppressWarnings("unchecked")
    private static void assertFinalResponseContains(List<Object> chunks, String expected) {
        List<OutputSchema> finals = MockWorkflowAgent.chunksOfType(chunks, "workflow_final");
        assertThat(finals).isNotEmpty();
        Object payload = finals.get(finals.size() - 1).getPayload();
        assertThat(payload).isInstanceOf(Map.class);
        Object response = ((Map<String, Object>) payload).get("response");
        assertThat(String.valueOf(response)).contains(expected);
    }

    private static InteractiveInput interactiveInput(String nodeId, String value) {
        InteractiveInput input = new InteractiveInput();
        input.update(nodeId, value);
        return input;
    }
}

/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.core.application.workflow;

import com.openjiuwen.core.application.schema.WorkflowAgentConfig;
import com.openjiuwen.core.controller.schema.ControllerOutput;
import com.openjiuwen.core.context.ModelContext;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.session.stream.StreamMode;
import com.openjiuwen.core.workflow.WorkflowOutput;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * WorkflowAgent multi-workflow default response UT.
 * 
 * <p>Mirrors Python's test_mock_workflow_agent_multi_workflow_default_response.py
 * from {@code tests/unit_tests/agent/workflow_agent/test_mock_workflow_agent_multi_workflow_default_response.py}
 * 
 * <p>Python test coverage:
 * <ul>
 *   <li>Case #15: invoke + agent direct + DefaultResponse configured</li>
 *   <li>Case #16: invoke + Runner + no DefaultResponse</li>
 *   <li>Case #17: stream + Runner + DefaultResponse configured</li>
 * </ul>
 * 
 * <p>Python test logic (lines 82-250):
 * <pre>
 * class TestMultiWorkflowDefaultResponse(unittest.IsolatedAsyncioTestCase):
 *     """Multi-workflow default response tests."""
 *     
 *     @staticmethod
 *     def _build_prefixed_workflow(workflow_id, workflow_name, prefix):
 *         """Build simple workflow: start -> end"""
 *         card = WorkflowCard(name=workflow_name, id=workflow_id, version="1.0")
 *         flow = Workflow(card=card)
 *         flow.set_start_comp("start", Start(), inputs_schema={"query": "${query}"})
 *         flow.set_end_comp("end", End({"responseTemplate": f"{prefix}{{{{output}}}}}"}),
 *                           inputs_schema={"output": "${start.query}"})
 *         flow.add_connection("start", "end")
 *         return flow
 *     
 *     async def test_default_response_with_config(self, mock_detect):
 *         """Invoke + agent direct + multi-workflow + DefaultResponse configured.
 *         When LLM intent detection returns None and default_response is configured,
 *         agent returns the configured default_response.text instead of falling back.
 *         """
 *         weather_wf, stock_wf = self._build_two_workflows()
 *         default_text = "Sorry, I cannot understand your question"
 *         config = WorkflowAgentConfig(
 *             id="test_default_resp_agent",
 *             default_response=DefaultResponse(type="text", text=default_text),
 *         )
 *         agent = WorkflowAgent(config)
 *         agent.add_workflows([weather_wf, stock_wf])
 *         
 *         result = await agent.invoke({"query": "blahblah random xyz", "conversation_id": conv_id})
 *         
 *         self.assertEqual(result["status"], "default_response")
 *         self.assertEqual(result["result_type"], "answer")
 *         self.assertEqual(result["output"]["answer"], default_text)
 * </pre>
 * 
 */
@DisplayName("WorkflowAgent Multi Workflow Default Response")
class MockWorkflowAgentMultiWorkflowDefaultResponseTest {

    private static final String DEFAULT_TEXT = "Sorry, I cannot understand your question";

    @BeforeEach
    void setUp() {
        Runner.start();
    }

    @AfterEach
    void tearDown() {
        Runner.stop();
    }

    @Test
    @DisplayName("default_response is returned when intent detection finds no workflow")
    void testDefaultResponseWithConfig() {
        MockWorkflowAgent.setMockResponses(MockWorkflowAgent.textResponse("{\"result\": 0}"));
        WorkflowAgent agent = createDefaultResponseAgent("test_default_resp_agent");

        String conversationId = UUID.randomUUID().toString();
        ControllerOutput output = agent.invoke(Map.of(
                "query", "blahblah random xyz",
                "conversation_id", conversationId
        ), null);

        Map<String, Object> result = MockWorkflowAgent.dataMap(output);
        assertThat(result).isNotNull();
        assertThat(result.get("status")).isEqualTo("default_response");
        assertThat(result.get("result_type")).isEqualTo("answer");
        @SuppressWarnings("unchecked")
        Map<String, Object> outputMap = (Map<String, Object>) result.get("output");
        assertThat(outputMap).containsEntry("answer", DEFAULT_TEXT);
        assertAgentContextRecorded(agent, conversationId, 2);
    }

    @Test
    @DisplayName("fallback to first workflow when no default_response is configured")
    void testFallbackToFirstWorkflow() {
        MockWorkflowAgent.setMockResponses(MockWorkflowAgent.textResponse("{\"result\": 0}"));
        WorkflowAgent agent = createFallbackAgent("test_no_default_resp_agent");

        ControllerOutput output = (ControllerOutput) Runner.runAgent(agent, Map.of(
                "query", "blahblah random xyz",
                "conversation_id", UUID.randomUUID().toString()
        ), null, null);

        Map<String, Object> result = MockWorkflowAgent.dataMap(output);
        assertThat(result).isNotNull();
        assertThat(result.get("result_type")).isEqualTo("answer");
        WorkflowOutput workflowOutput = (WorkflowOutput) result.get("output");
        assertThat(MockWorkflowAgent.responseFrom(workflowOutput)).contains("weather:");
    }

    @Test
    @DisplayName("stream emits workflow_final with configured default_response text")
    void testDefaultResponseStream() {
        MockWorkflowAgent.setMockResponses(MockWorkflowAgent.textResponse("{\"result\": 0}"));
        WorkflowAgent agent = createDefaultResponseAgent("test_default_resp_stream_agent");

        var chunks = MockWorkflowAgent.collect(Runner.runAgentStreaming(
                agent,
                Map.of("query", "blahblah random xyz", "conversation_id", UUID.randomUUID().toString()),
                null,
                null,
                List.of(StreamMode.OUTPUT)));

        assertThat(chunks).isNotEmpty();
        var finalChunks = MockWorkflowAgent.chunksOfType(chunks, "workflow_final");
        assertThat(finalChunks).hasSize(1);
        assertThat(finalChunks.get(0).getPayload()).isInstanceOf(Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) finalChunks.get(0).getPayload();
        assertThat(payload).containsEntry("response", DEFAULT_TEXT);
    }

    private static WorkflowAgent createDefaultResponseAgent(String agentId) {
        WorkflowAgentConfig config = MockWorkflowAgent.configWithDefaultResponse(agentId, DEFAULT_TEXT);
        MockWorkflowAgent mock = MockWorkflowAgent.of(config);
        mock.addWorkflows(buildTwoWorkflows());
        return mock.unwrap();
    }

    private static WorkflowAgent createFallbackAgent(String agentId) {
        MockWorkflowAgent mock = MockWorkflowAgent.of(MockWorkflowAgent.configWithModel(agentId));
        mock.addWorkflows(buildTwoWorkflows());
        return mock.unwrap();
    }

    private static List<com.openjiuwen.core.workflow.Workflow> buildTwoWorkflows() {
        return List.of(
                MockWorkflowAgent.prefixedWorkflow(
                        "weather_flow", "weather_query", "Query weather, temperature, forecast", "weather:"),
                MockWorkflowAgent.prefixedWorkflow(
                        "stock_flow", "stock_query", "Query stock price, market trends", "stock:")
        );
    }

    private static void assertAgentContextRecorded(WorkflowAgent agent, String conversationId, int expectedMessages) {
        ModelContext context = agent.getContextEngine().getContext(null, conversationId);
        assertThat(context).isNotNull();
        assertThat(context.getMessages()).hasSize(expectedMessages);
        assertThat(context.getMessages().get(0).getRole()).isEqualTo("user");
        assertThat(context.getMessages().get(1).getRole()).isEqualTo("assistant");
    }
}

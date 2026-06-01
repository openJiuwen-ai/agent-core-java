/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.core.application.workflow;

import com.openjiuwen.core.application.schema.WorkflowAgentConfig;
import com.openjiuwen.core.context.ModelContext;
import com.openjiuwen.core.controller.schema.ControllerOutput;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.session.NodeSessionApi;
import com.openjiuwen.core.session.Session;
import com.openjiuwen.core.session.interaction.InteractionOutput;
import com.openjiuwen.core.session.interaction.InteractiveInput;
import com.openjiuwen.core.session.stream.OutputSchema;
import com.openjiuwen.core.session.stream.StreamMode;
import com.openjiuwen.core.workflow.Workflow;
import com.openjiuwen.core.workflow.WorkflowCard;
import com.openjiuwen.core.workflow.WorkflowComponent;
import com.openjiuwen.core.workflow.WorkflowOutput;
import com.openjiuwen.core.workflow.component.End;
import com.openjiuwen.core.workflow.component.Start;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * WorkflowAgent concurrent and realtime interrupt UT.
 *
 * <p>Mirrors Python's {@code test_mock_workflow_agent_concurrent.py} in
 * {@code tests/unit_tests/agent/workflow_agent/}.
 */
@DisplayName("WorkflowAgent Concurrent")
class MockWorkflowAgentConcurrentTest {

    @BeforeEach
    void setUp() {
        Runner.start();
    }

    @AfterEach
    void tearDown() {
        Runner.stop();
    }

    @Test
    @DisplayName("concurrent invocations with separate agents maintain isolation")
    void testConcurrentInvocationsMaintainIsolation() throws Exception {
        int numConversations = 3;
        ExecutorService executor = Executors.newFixedThreadPool(numConversations);
        List<Future<Map<String, Object>>> futures = new ArrayList<>();

        for (int i = 0; i < numConversations; i++) {
            final int idx = i;
            futures.add(executor.submit(() -> {
                Workflow workflow = WorkflowTestHelper.buildSimpleWorkflow(
                        "concurrent_wf_" + idx, "concurrent_" + idx);

                WorkflowAgentConfig config = WorkflowAgentConfig.builder()
                        .id("concurrent_agent_" + idx)
                        .version("1.0")
                        .description("concurrent test agent " + idx)
                        .build();
                WorkflowAgent agent = new WorkflowAgent(config);
                agent.addWorkflows(List.of(workflow));

                String conversationId = UUID.randomUUID().toString();
                Session session = new Session() {
                    private final Map<String, Object> state = new ConcurrentHashMap<>();

                    @Override
                    public String getSessionId() {
                        return conversationId;
                    }
                    @Override
                    public Object getState(String key) {
                        return state.get(key);
                    }
                    @Override
                    public void updateState(Map<String, Object> state) {
                        if (state != null) {
                            this.state.putAll(state);
                        }
                    }
                };
                ControllerOutput output = agent.invoke(Map.of(
                        "query", "hello_" + idx,
                        "conversation_id", conversationId
                ), session);
                assertThat(output).isNotNull();
                assertThat(output.getType()).isEqualTo("task_completion");
                Map<String, Object> result = output.getDataAsMap();
                assertThat(result).isNotNull();
                return result;
            }));
        }

        for (Future<Map<String, Object>> future : futures) {
            Map<String, Object> result = future.get(30, TimeUnit.SECONDS);
            assertThat(result).isInstanceOf(Map.class);
            assertThat(result.get("result_type")).isEqualTo("answer");
        }

        executor.shutdown();
        assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    @DisplayName("streaming interrupt switches workflow and resumes interrupted questioner")
    void testRealtimeInterruptCancellation() throws Exception {
        MockWorkflowAgent.setMockResponses(
                MockWorkflowAgent.textResponse("{\"result\": 1}"),
                MockWorkflowAgent.textResponse("{\"result\": 2}"));
        Workflow weather = buildSlowWorkflow("weather_slow_wf", "weather_slow");
        Workflow stock = MockWorkflowAgent.questionerWorkflow(
                "stock_interrupt_wf",
                "stock_interrupt",
                "Query stock price, market trends",
                "stock_questioner",
                "What is the stock code?",
                "stock:");
        WorkflowAgent agent = MockWorkflowAgent.createAgentWithModel("realtime_interrupt_agent", weather, stock);
        String conversationId = "test-realtime-interrupt";

        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<List<Object>> slowRun = executor.submit(() -> MockWorkflowAgent.collect(Runner.runAgentStreaming(
                agent,
                Map.of("query", "check weather", "conversation_id", conversationId),
                null,
                null,
                List.of(StreamMode.OUTPUT))));

        Thread.sleep(100);
        slowRun.cancel(true);

        List<Object> stockChunks = MockWorkflowAgent.collect(Runner.runAgentStreaming(
                agent,
                Map.of("query", "check stock", "conversation_id", conversationId),
                null,
                null,
                List.of(StreamMode.OUTPUT)));
        List<OutputSchema> interactions = MockWorkflowAgent.chunksOfType(stockChunks, "__interaction__");
        assertThat(interactions).hasSize(1);
        InteractionOutput interaction = (InteractionOutput) interactions.get(0).getPayload();
        assertThat(interaction.getId()).isEqualTo("stock_questioner");

        InteractiveInput answer = new InteractiveInput();
        answer.update(interaction.getId(), "AAPL");
        List<Object> resumedChunks = MockWorkflowAgent.collect(Runner.runAgentStreaming(
                agent,
                Map.of("query", answer, "conversation_id", conversationId),
                null,
                null,
                List.of(StreamMode.OUTPUT)));

        assertFinalResponseContains(resumedChunks, "AAPL");
        executor.shutdownNow();
    }

    @Test
    @DisplayName("questioner state resets on second invocation")
    void testComponentStateReset() {
        Workflow workflow = MockWorkflowAgent.questionerWorkflow(
                "state_reset_wf",
                "state_reset_test",
                "Questioner state reset workflow",
                "What is your location?",
                "");
        WorkflowAgent agent = MockWorkflowAgent.createAgent("state_reset_agent", workflow);
        String conversationId = "test-state-reset";

        ControllerOutput first = agent.invoke(
                Map.of("query", "collect info", "conversation_id", conversationId), null);
        assertInteraction(first, "questioner");

        ControllerOutput firstResume = agent.invoke(
                Map.of("query", interactiveInput("questioner", "shanghai"), "conversation_id", conversationId), null);
        String firstResponse = MockWorkflowAgent.responseFrom((WorkflowOutput) MockWorkflowAgent.dataMap(firstResume).get("output"));
        assertThat(firstResponse).contains("shanghai");

        ControllerOutput second = agent.invoke(
                Map.of("query", "collect info again", "conversation_id", conversationId), null);
        assertInteraction(second, "questioner");

        ControllerOutput secondResume = agent.invoke(
                Map.of("query", interactiveInput("questioner", "beijing"), "conversation_id", conversationId), null);
        String secondResponse = MockWorkflowAgent.responseFrom(
                (WorkflowOutput) MockWorkflowAgent.dataMap(secondResume).get("output"));
        assertThat(secondResponse).contains("beijing");
        assertThat(secondResponse).doesNotContain("shanghai");
    }

    private static void assertInteraction(ControllerOutput output, String expectedNodeId) {
        List<OutputSchema> interactions = MockWorkflowAgent.interactionData(output);
        assertThat(interactions).hasSize(1);
        assertThat(interactions.get(0).getType()).isEqualTo("__interaction__");
        assertThat(interactions.get(0).getPayload()).isInstanceOf(InteractionOutput.class);
        assertThat(((InteractionOutput) interactions.get(0).getPayload()).getId()).isEqualTo(expectedNodeId);
    }

    @SuppressWarnings("unchecked")
    private static void assertFinalResponseContains(List<Object> chunks, String expected) {
        List<OutputSchema> finals = MockWorkflowAgent.chunksOfType(chunks, "workflow_final");
        assertThat(finals).hasSize(1);
        Object payload = finals.get(0).getPayload();
        assertThat(payload).isInstanceOf(Map.class);
        assertThat(String.valueOf(((Map<String, Object>) payload).get("response"))).contains(expected);
    }

    private static InteractiveInput interactiveInput(String nodeId, String value) {
        InteractiveInput input = new InteractiveInput();
        input.update(nodeId, value);
        return input;
    }

    private static Workflow buildSlowWorkflow(String workflowId, String workflowName) {
        Workflow flow = new Workflow(WorkflowCard.builder()
                .id(workflowId)
                .version("1.0")
                .name(workflowName)
                .description("Query weather, temperature, forecast")
                .build());
        flow.setStartComp("start", new Start(), Map.of("query", "${query}"));
        flow.addWorkflowComp("slow_node", new SlowNode(), Map.of("output", "${start.query}"));
        flow.setEndComp("end", new End(Map.of("responseTemplate", "weather:{{output}}")),
                Map.of("output", "${slow_node.output}"));
        flow.addConnection("start", "slow_node");
        flow.addConnection("slow_node", "end");
        return flow;
    }

    private static final class SlowNode extends WorkflowComponent {
        @Override
        @SuppressWarnings("unchecked")
        public Object invoke(Object inputs, NodeSessionApi session, ModelContext context) {
            try {
                Thread.sleep(300);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            Map<String, Object> inputMap = (Map<String, Object>) inputs;
            return Map.of("output", inputMap.get("output"));
        }
    }
}

/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.application.workflow;

import com.openjiuwen.core.context.ModelContext;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.session.stream.OutputSchema;
import com.openjiuwen.core.session.stream.StreamMode;
import com.openjiuwen.core.workflow.Workflow;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * WorkflowAgent stream tests.
 * <p>
 * Mirrors Python's {@code test_mock_workflow_agent_stream.py} in
 * {@code tests/unit_tests/agent/workflow_agent/}.
 */
@DisplayName("WorkflowAgent Stream")
class MockWorkflowAgentStreamTest {

    @BeforeEach
    void setUp() {
        Runner.start();
    }

    @AfterEach
    void tearDown() {
        Runner.stop();
    }

    @Test
    @DisplayName("agent.stream completes a simple workflow end to end")
    void testStreamDirect() {
        Workflow workflow = MockWorkflowAgent.simpleWorkflow("test_stream_workflow", "stream_test");
        WorkflowAgent agent = MockWorkflowAgent.createAgent("test_stream_agent", workflow);

        String conversationId = java.util.UUID.randomUUID().toString();
        var chunks = MockWorkflowAgent.collect(agent.stream(
                java.util.Map.of("query", "hello", "conversation_id", conversationId),
                null,
                java.util.List.of(StreamMode.OUTPUT)));

        org.assertj.core.api.Assertions.assertThat(chunks).isNotEmpty();
        var workflowFinalChunks = MockWorkflowAgent.chunksOfType(chunks, "workflow_final");
        org.assertj.core.api.Assertions.assertThat(workflowFinalChunks).hasSize(1);
        assertWorkflowFinalPayload(workflowFinalChunks.get(0), "hello");
        assertAgentContextRecorded(agent, conversationId, 2);
    }

    @Test
    @DisplayName("Runner.runAgentStreaming streams a simple workflow")
    void testStreamViaRunner() {
        Workflow workflow = MockWorkflowAgent.simpleWorkflow("test_stream_runner_workflow", "stream_runner_test");
        WorkflowAgent agent = MockWorkflowAgent.createAgent("test_stream_runner_agent", workflow);

        String conversationId = java.util.UUID.randomUUID().toString();
        var chunks = MockWorkflowAgent.collect(Runner.runAgentStreaming(
                agent,
                java.util.Map.of("query", "hello", "conversation_id", conversationId),
                null,
                null,
                java.util.List.of(StreamMode.OUTPUT)));

        org.assertj.core.api.Assertions.assertThat(chunks).isNotEmpty();
        var workflowFinalChunks = MockWorkflowAgent.chunksOfType(chunks, "workflow_final");
        org.assertj.core.api.Assertions.assertThat(workflowFinalChunks).hasSize(1);
        assertWorkflowFinalPayload(workflowFinalChunks.get(0), "hello");
        assertAgentContextRecorded(agent, conversationId, 2);
    }

    @SuppressWarnings("unchecked")
    private static void assertWorkflowFinalPayload(OutputSchema chunk, String expectedResponse) {
        org.assertj.core.api.Assertions.assertThat(chunk.getPayload()).isInstanceOf(java.util.Map.class);
        java.util.Map<String, Object> payload = (java.util.Map<String, Object>) chunk.getPayload();
        org.assertj.core.api.Assertions.assertThat(payload.get("response")).isEqualTo(expectedResponse);
    }

    private static void assertAgentContextRecorded(WorkflowAgent agent, String conversationId, int expectedMessages) {
        ModelContext context = agent.getContextEngine().getContext(null, conversationId);
        org.assertj.core.api.Assertions.assertThat(context).isNotNull();
        org.assertj.core.api.Assertions.assertThat(context.getMessages()).hasSize(expectedMessages);
        org.assertj.core.api.Assertions.assertThat(context.getMessages().get(0).getRole()).isEqualTo("user");
        org.assertj.core.api.Assertions.assertThat(context.getMessages().get(1).getRole()).isEqualTo("assistant");
    }
}

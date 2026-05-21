/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.core.application.workflow;

import com.openjiuwen.core.context.ModelContext;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.session.NodeSessionApi;
import com.openjiuwen.core.session.stream.OutputSchema;
import com.openjiuwen.core.workflow.Workflow;
import com.openjiuwen.core.workflow.WorkflowCard;
import com.openjiuwen.core.workflow.WorkflowComponent;
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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * WorkflowAgent basic stream UT (mock-based).
 *
 * <p>Mirrors Python's {@code test_mock_workflow_agent_stream.py} in
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

    private static Workflow buildSimpleWorkflow() {
        WorkflowCard card = WorkflowCard.builder()
                .id("test_stream_workflow")
                .version("1.0")
                .name("stream_test")
                .description("Simple workflow for stream test")
                .build();
        Workflow flow = new Workflow(card);

        flow.setStartComp("start", new Start(),
                Map.of("query", "${query}"), null);
        flow.addWorkflowComp("node_a", new IdentityNode(),
                Map.of("output", "${start.query}"), null);
        flow.setEndComp("end", new PassThroughEndNode(),
                Map.of("result", "${node_a.output}"), null);

        flow.addConnection("start", "node_a");
        flow.addConnection("node_a", "end");
        return flow;
    }

    @Test
    @DisplayName("agent.stream() completes a simple workflow end-to-end")
    void testStreamDirect() {
        Workflow workflow = buildSimpleWorkflow();

        com.openjiuwen.core.application.schema.WorkflowAgentConfig config =
                com.openjiuwen.core.application.schema.WorkflowAgentConfig.builder()
                        .id("test_stream_agent")
                        .version("1.0")
                        .description("stream test agent")
                        .build();
        WorkflowAgent agent = new WorkflowAgent(config);
        agent.addWorkflows(List.of(workflow));

        String convId = UUID.randomUUID().toString();
        List<Object> chunks = new ArrayList<>();
        agent.stream(Map.of(
                "query", "hello",
                "conversation_id", convId
        )).forEachRemaining(chunks::add);

        assertThat(chunks).isNotEmpty();

        List<OutputSchema> workflowFinalChunks = new ArrayList<>();
        for (Object c : chunks) {
            if (c instanceof OutputSchema schema && "workflow_final".equals(schema.getType())) {
                workflowFinalChunks.add(schema);
            }
        }

        assertThat(workflowFinalChunks).hasSize(1);
    }

    private static class IdentityNode extends WorkflowComponent {
        @Override
        public Object invoke(Object inputs, NodeSessionApi session, ModelContext context) {
            return inputs;
        }
    }

    private static class PassThroughEndNode extends End {
        public PassThroughEndNode() {
            super(Map.of("responseTemplate", "hello:{{end_input}}"));
        }

        @Override
        public Object invoke(Object inputs, NodeSessionApi session, ModelContext context) {
            return inputs;
        }
    }
}

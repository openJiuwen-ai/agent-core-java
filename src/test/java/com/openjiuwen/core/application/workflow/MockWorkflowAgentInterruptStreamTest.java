/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.core.application.workflow;

import com.openjiuwen.core.application.schema.WorkflowAgentConfig;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.session.stream.OutputSchema;
import com.openjiuwen.core.workflow.Workflow;
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
 * WorkflowAgent interrupt stream UT.
 *
 * <p>Mirrors Python's {@code test_mock_workflow_agent_interrupt_stream.py} in
 * {@code tests/unit_tests/agent/workflow_agent/}.
 */
@DisplayName("WorkflowAgent Interrupt Stream")
class MockWorkflowAgentInterruptStreamTest {

    @BeforeEach
    void setUp() {
        Runner.start();
    }

    @AfterEach
    void tearDown() {
        Runner.stop();
    }

    @Test
    @DisplayName("stream direct with simple workflow yields chunks")
    void testStreamDirectSimpleWorkflow() {
        Workflow workflow = WorkflowTestHelper.buildSimpleWorkflow("test_interrupt_stream", "interrupt_stream_test");

        WorkflowAgentConfig config = WorkflowAgentConfig.builder()
                .id("test_interrupt_stream_agent")
                .version("1.0")
                .description("interrupt stream test agent")
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
}

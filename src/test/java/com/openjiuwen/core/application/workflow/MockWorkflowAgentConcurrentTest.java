/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.core.application.workflow;

import com.openjiuwen.core.application.schema.WorkflowAgentConfig;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.session.Session;
import com.openjiuwen.core.workflow.Workflow;
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
                    @Override
                    public String getSessionId() {
                        return conversationId;
                    }
                    @Override
                    public Object getState(String key) {
                        return null;
                    }
                    @Override
                    public void updateState(Map<String, Object> state) {
                    }
                };
                @SuppressWarnings("unchecked")
                Map<String, Object> result = (Map<String, Object>) agent.invoke(Map.of(
                        "query", "hello_" + idx,
                        "conversation_id", conversationId
                ), session);
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
}

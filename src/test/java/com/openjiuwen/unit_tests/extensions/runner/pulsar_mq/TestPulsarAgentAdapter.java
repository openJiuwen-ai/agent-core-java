/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.unit_tests.extensions.runner.pulsar_mq;

import com.openjiuwen.core.application.schema.WorkflowAgentConfig;
import com.openjiuwen.core.application.schema.WorkflowSchema;
import com.openjiuwen.core.application.workflow.WorkflowAgent;
import com.openjiuwen.core.common.constants.ControllerType;
import com.openjiuwen.core.foundation.llm.schema.BaseModelInfo;
import com.openjiuwen.core.foundation.llm.schema.ModelConfig;
import com.openjiuwen.core.runner.DistributedConfig;
import com.openjiuwen.core.runner.MessageQueueConfig;
import com.openjiuwen.core.runner.MessageQueueType;
import com.openjiuwen.core.runner.PulsarConfig;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.runner.RunnerConfig;
import com.openjiuwen.core.runner.drunner.remote_client.RemoteAgent;
import com.openjiuwen.core.session.stream.OutputSchema;
import com.openjiuwen.core.session.stream.TraceSchema;
import com.openjiuwen.core.singleagent.legacy.LegacyApi;
import com.openjiuwen.core.singleagent.legacy.LegacyReActAgent;
import com.openjiuwen.core.singleagent.legacy.config.LegacyReActAgentConfig;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.core.workflow.Workflow;
import com.openjiuwen.core.workflow.WorkflowCard;
import com.openjiuwen.tests.unit_tests.core.workflow.MockNodes;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's {@code tests/unit_tests/extensions/runner/pulsar_mq/test_pulsar_agent_adapter.py}.
 */
@Tag("integration-test")
@Disabled("Requires real uv sync --extra pulsar and llm")
class TestPulsarAgentAdapter {

    private static final String API_BASE = System.getenv("API_BASE");
    private static final String API_KEY = System.getenv("API_KEY");
    private static final String MODEL_NAME = System.getenv("MODEL_NAME");
    private static final String MODEL_PROVIDER = System.getenv().getOrDefault("MODEL_PROVIDER", "openai");

    private String workflowResourceId;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        System.setProperty("LLM_SSL_VERIFY", "false");
        System.setProperty("RESTFUL_SSL_VERIFY", "false");
        Runner.setConfig(RunnerConfig.builder()
                .distributedMode(true)
                .distributedConfig(DistributedConfig.builder()
                        .requestTimeout(15.0)
                        .messageQueueConfig(MessageQueueConfig.builder()
                                .type(MessageQueueType.PULSAR.getValue())
                                .pulsarConfig(PulsarConfig.builder()
                                        .maxWorkers(8)
                                        .url("pulsar://localhost:6650")
                                        .build())
                                .build())
                        .build())
                .build());
    }

    @AfterEach
    void tearDown() {
        Runner.stop();
        Runner.setConfig(RunnerConfig.DEFAULT);
    }

    private LegacyReActAgent createAndRegisterAgent(String agentId) {
        LegacyReActAgentConfig reactAgentConfig = LegacyApi.createReActAgentConfig(
                agentId,
                "0.0.1",
                "AI助手",
                createModel(),
                List.of()
        );
        LegacyReActAgent reactAgent = new LegacyReActAgent(reactAgentConfig);
        Runner.resourceMgr().addAgent(AgentCard.builder().id(agentId).build(), () -> reactAgent, null);
        return reactAgent;
    }

    private static ModelConfig createModel() {
        return new ModelConfig(
                MODEL_PROVIDER,
                BaseModelInfo.builder()
                        .modelName(MODEL_NAME)
                        .apiBase(API_BASE)
                        .apiKey(API_KEY)
                        .temperature(0.7)
                        .topP(0.9)
                        .timeout(30)
                        .build()
        );
    }

    private Workflow buildWorkflow(String name, String workflowId, String version) {
        WorkflowCard workflowCard = WorkflowCard.builder()
                .id(workflowId)
                .version(version)
                .name(name)
                .build();
        Workflow flow = new Workflow(workflowCard);
        flow.setStartComp("start", new MockNodes.MockStartNode("start"), Map.of("query", "${query}"));
        flow.addWorkflowComp("node_a", new MockNodes.Node1("node_a"), Map.of("output", "${start.query}"));
        flow.setEndComp("end", new MockNodes.MockEndNode("end"), Map.of("result", "${node_a.output}"));
        flow.addConnection("start", "node_a");
        flow.addConnection("node_a", "end");
        return flow;
    }

    @Test
    @DisplayName("adapter invoke returns output from real remote agent")
    void testAdapterInvoke() throws Exception {
        Runner.start();
        try {
            createAndRegisterAgent("weather-single_agent");
            RemoteAgent client = new RemoteAgent("weather-single_agent");
            Object result = client.invoke(Map.of("query", "你好"));
            assertTrue(result instanceof Map);
            assertNotNull(((Map<?, ?>) result).get("output"));
        } finally {
            Runner.stop();
        }
    }

    @Test
    @DisplayName("adapter stream yields output and trace chunks")
    void testAdapterStream() throws Exception {
        Runner.start();
        try {
            createAndRegisterAgent("weather-single_agent-stream");
            RemoteAgent client = new RemoteAgent("weather-single_agent-stream");
            List<Object> chunks = new ArrayList<>();
            Iterator<Object> iterator = client.stream(Map.of("query", "你好"));
            while (iterator.hasNext()) {
                Object chunk = iterator.next();
                chunks.add(chunk);
                assertTrue(chunk instanceof OutputSchema || chunk instanceof TraceSchema,
                        "Chunk must be OutputSchema or TraceSchema, got " + chunk.getClass());
            }
            assertFalse(chunks.isEmpty());
        } finally {
            Runner.stop();
        }
    }

    @Test
    @DisplayName("workflow agent invoke with adapter returns completed answer")
    void testReactAgentInvokeWithAdapter() {
        try {
            Runner.start();
            String workflowId = "test_workflow";
            workflowResourceId = workflowId + "_1";
            String name = "test_workflow";
            String version = "1";
            Workflow workflow = buildWorkflow(name, workflowId, version);

            WorkflowSchema workflowSchema = WorkflowSchema.builder()
                    .id(workflowId)
                    .version(version)
                    .name(name)
                    .description("test_workflow")
                    .inputParams(Map.of("query", Map.of("type", "string")))
                    .build();
            WorkflowAgentConfig workflowConfig = WorkflowAgentConfig.builder()
                    .workflows(List.of(workflowSchema))
                    .controllerType(ControllerType.WORKFLOW_CONTROLLER)
                    .build();

            WorkflowAgent agent = new WorkflowAgent(workflowConfig);
            agent.addWorkflows(List.of(workflow));
            Runner.resourceMgr().addWorkflow(
                    WorkflowCard.builder().id(workflowResourceId).name(name).build(),
                    () -> workflow,
                    null
            );
            Runner.resourceMgr().addAgent(AgentCard.builder().id("workflow-single_agent").build(), () -> agent, null);

            RemoteAgent client = new RemoteAgent("workflow-single_agent");
            Runner.resourceMgr().addAgent(
                    AgentCard.builder().id("remote-workflow-single_agent").build(),
                    () -> client,
                    null
            );

            Object response = Runner.runAgent("remote-workflow-single_agent", Map.of("query", "London"), null, null);
            assertTrue(response instanceof Map);
            Map<?, ?> resultMap = (Map<?, ?>) response;
            assertEquals("answer", resultMap.get("result_type"));

            Object output = resultMap.get("output");
            assertNotNull(output);
            assertTrue(String.valueOf(output).contains("London"));
            assertTrue(String.valueOf(output).contains("COMPLETED"));
        } finally {
            Runner.resourceMgr().removeAgent("remote-workflow-single_agent", null, null, true);
            Runner.resourceMgr().removeAgent("workflow-single_agent", null, null, true);
            if (workflowResourceId != null) {
                Runner.resourceMgr().removeWorkflow(workflowResourceId, null, null, true);
            }
            Runner.stop();
        }
    }
}

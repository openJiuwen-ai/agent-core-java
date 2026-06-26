/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.drunner.remote_client;

import static org.assertj.core.api.Assertions.assertThat;

import com.openjiuwen.core.application.workflow_agent.WorkflowAgent;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.runner.RunnerConfig;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.single_agent.legacy.agent.BaseAgent;
import com.openjiuwen.core.single_agent.legacy.config.WorkflowAgentConfig;
import com.openjiuwen.core.single_agent.schema.AgentCard;
import com.openjiuwen.core.singleagent.legacy.schema.WorkflowSchema;
import com.openjiuwen.core.workflow.Workflow;
import com.openjiuwen.core.workflow.WorkflowCard;
import com.openjiuwen.core.workflow.WorkflowExecutionState;
import com.openjiuwen.core.workflow.WorkflowOutput;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * <p>Mirrors Python's {@code TestRunnerIntegration.test_react_agent_invoke_with_adapter} in
 * {@code tests/unit_tests/core/runner/dunner/test_agent_adapter.py}.</p>
 */
class WorkflowRemoteAgentAdapterMissingTest {
    private static final String WORKFLOW_ID = "test_workflow";
    private static final String WORKFLOW_VERSION = "1";
    private static final String LOCAL_AGENT_ID = "workflow-single_agent";
    private static final String REMOTE_AGENT_ID = "remote-workflow-single_agent";

    @BeforeEach
    void startRunner() {
        Runner.setConfig(RunnerConfig.DEFAULT_RUNNER_CONFIG.copy());
        RemoteClientFactory.registerRemoteClient(
                ProtocolEnum.A2A.name(),
                ignored -> new LoopbackWorkflowRemoteClient(LOCAL_AGENT_ID)
        );
        Runner.start().toCompletableFuture().join();
    }

    @AfterEach
    void cleanup() {
        Runner.resourceMgr().removeAgent(REMOTE_AGENT_ID);
        Runner.resourceMgr().removeAgent(LOCAL_AGENT_ID);
        Runner.resourceMgr().removeWorkflow(WORKFLOW_ID + "_" + WORKFLOW_VERSION);
        Runner.stop().toCompletableFuture().join();
        RemoteClientFactory.clearCustomRemoteClientsForTest();
        Runner.setConfig(RunnerConfig.DEFAULT_RUNNER_CONFIG.copy());
    }

    @Test
    void reactAgentInvokeWithAdapterReturnsWorkflowAnswerAndCleansResources() {
        RecordingWorkflowController controller = new RecordingWorkflowController();
        WorkflowAgent agent = workflowAgent(controller);
        agent.bindWorkflows(List.of(buildWorkflow()));
        Runner.resourceMgr().addAgent(new AgentCard(LOCAL_AGENT_ID, LOCAL_AGENT_ID, "workflow agent"), () -> agent);
        Runner.resourceMgr().addAgent(
                new AgentCard(REMOTE_AGENT_ID, REMOTE_AGENT_ID, "remote workflow agent"),
                new RemoteAgent(LOCAL_AGENT_ID, "", null, null, ProtocolEnum.A2A, null)
        );

        Map<String, Object> response = map(Runner.runAgent(REMOTE_AGENT_ID, mutableInputs("London"))
                .toCompletableFuture()
                .join());

        assertThat(response).containsEntry("result_type", "answer");
        WorkflowOutput output = (WorkflowOutput) response.get("output");
        assertThat(output.getResult()).isEqualTo(Map.of("result", "London"));
        assertThat(output.getState()).isEqualTo(WorkflowExecutionState.COMPLETED);
        assertThat(controller.lastInputs).containsEntry("query", "London");
        assertThat(controller.lastSession).isNotNull();
    }

    private static WorkflowAgent workflowAgent(RecordingWorkflowController controller) {
        WorkflowAgentConfig config = new WorkflowAgentConfig();
        config.setId(LOCAL_AGENT_ID);
        config.setVersion(WORKFLOW_VERSION);
        config.setDescription("test_workflow");
        config.setWorkflows(List.of(WorkflowSchema.builder()
                .id(WORKFLOW_ID)
                .version(WORKFLOW_VERSION)
                .name("test_workflow")
                .description("test_workflow")
                .inputs(Map.of("query", Map.of("type", "string")))
                .build()));
        WorkflowAgent agent = new WorkflowAgent(config);
        agent.setController(controller);
        return agent;
    }

    private static Workflow buildWorkflow() {
        WorkflowCard workflowCard = new WorkflowCard(
                WORKFLOW_ID,
                "test_workflow",
                "test_workflow",
                WORKFLOW_VERSION,
                Map.of("query", Map.of("type", "string"))
        );
        return new Workflow(workflowCard);
    }

    private static Map<String, Object> mutableInputs(String query) {
        Map<String, Object> inputs = new LinkedHashMap<>();
        inputs.put("query", query);
        return inputs;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value) {
        assertThat(value).isInstanceOf(Map.class);
        Map<String, Object> result = new LinkedHashMap<>();
        ((Map<?, ?>) value).forEach((key, mapValue) -> result.put(String.valueOf(key), mapValue));
        return result;
    }

    public static final class RecordingWorkflowController {
        private Map<String, Object> lastInputs;
        private AgentSessionApi lastSession;

        public CompletionStage<Object> invoke(Map<String, Object> inputs, AgentSessionApi session) {
            this.lastInputs = new LinkedHashMap<>(inputs);
            this.lastSession = session;
            return CompletableFuture.completedFuture(new LinkedHashMap<>(Map.of(
                    "result_type", "answer",
                    "output", new WorkflowOutput(Map.of("result", inputs.get("query")), WorkflowExecutionState.COMPLETED)
            )));
        }
    }

    private static final class LoopbackWorkflowRemoteClient implements RemoteClient {
        private final String targetAgentId;
        private boolean started;

        private LoopbackWorkflowRemoteClient(String targetAgentId) {
            this.targetAgentId = targetAgentId;
        }

        @Override
        public CompletionStage<Void> start() {
            this.started = true;
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Void> stop() {
            this.started = false;
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public boolean isStarted() {
            return started;
        }

        @Override
        public CompletionStage<Map<String, Object>> invoke(Map<String, Object> inputs, Double timeoutSeconds) {
            Object target = Runner.resourceMgr().getAgent(targetAgentId).toCompletableFuture().join();
            if (!(target instanceof BaseAgent agent)) {
                return CompletableFuture.failedFuture(
                        new IllegalStateException("expected local WorkflowAgent for " + targetAgentId)
                );
            }
            return agent.invoke(new LinkedHashMap<>(inputs), null).thenApply(WorkflowRemoteAgentAdapterMissingTest::map);
        }

        @Override
        public Iterator<Object> stream(Map<String, Object> inputs, Double timeoutSeconds) {
            Object response = invoke(inputs, timeoutSeconds).toCompletableFuture().join();
            return List.of(response).iterator();
        }
    }
}

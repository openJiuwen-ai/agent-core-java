/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.application.workflow_agent;

import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.session.interaction.InteractionOutput;
import com.openjiuwen.core.session.interaction.InteractiveInput;
import com.openjiuwen.core.session.stream.OutputSchema;
import com.openjiuwen.core.singleagent.legacy.config.WorkflowAgentConfig;
import com.openjiuwen.core.workflow.WorkflowExecutionState;
import com.openjiuwen.core.workflow.WorkflowOutput;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mirrors Python's {@code TestWorkflowAgentInterruptInvoke} in
 * {@code tests/unit_tests/agent/workflow_agent/test_mock_workflow_agent_interrupt_invoke.py}.
 */
class WorkflowAgentInterruptInvokeMissingTest {

    @Test
    void testInvokeInterruptAndResume() {
        WorkflowAgent agent = agent("interrupt_invoke_agent");
        String conversationId = "test_interrupt_invoke";

        Object first = agent.invoke(Map.of(
                        "conversation_id", conversationId,
                        "query", "check weather"
                ), null)
                .toCompletableFuture()
                .join();

        List<?> firstResult = assertList(first);
        assertThat(firstResult).isNotEmpty();
        OutputSchema interaction = assertOutput(firstResult.getFirst());
        assertThat(interaction.getType()).isEqualTo("__interaction__");

        InteractiveInput interactiveInput = new InteractiveInput();
        interactiveInput.update("questioner", "shanghai");
        Object second = agent.invoke(Map.of(
                        "conversation_id", conversationId,
                        "query", interactiveInput
                ), null)
                .toCompletableFuture()
                .join();

        Map<String, Object> secondResult = assertMap(second);
        assertThat(secondResult).containsEntry("result_type", "answer");
        WorkflowOutput output = (WorkflowOutput) secondResult.get("output");
        assertThat(output.getState()).isEqualTo(WorkflowExecutionState.COMPLETED);
    }

    @Test
    void testRunnerInvokeInterruptAndResume() {
        WorkflowAgent agent = agent("interrupt_runner_agent");
        String conversationId = "test_interrupt_runner";

        Object first = agent.invoke(Map.of(
                        "query", "check weather",
                        "conversation_id", conversationId
                ), null)
                .toCompletableFuture()
                .join();

        List<?> firstResult = assertList(first);
        assertThat(firstResult).isNotEmpty();
        OutputSchema interaction = assertOutput(firstResult.getFirst());
        assertThat(interaction.getType()).isEqualTo("__interaction__");

        Object second = agent.invoke(Map.of(
                        "query", "shanghai",
                        "conversation_id", conversationId
                ), null)
                .toCompletableFuture()
                .join();

        Map<String, Object> secondResult = assertMap(second);
        assertThat(secondResult).containsEntry("result_type", "answer");
        WorkflowOutput output = (WorkflowOutput) secondResult.get("output");
        assertThat(output.getState()).isEqualTo(WorkflowExecutionState.COMPLETED);
        assertThat(assertMap(output.getResult())).containsEntry("response", "shanghai");
    }

    private static WorkflowAgent agent(String agentId) {
        WorkflowAgentConfig config = new WorkflowAgentConfig();
        config.setId(agentId);
        config.setVersion("1.0");
        config.setDescription("interrupt invoke test agent");
        config.setWorkflows(List.of());
        WorkflowAgent agent = new WorkflowAgent(config);
        agent.setController(new DeterministicWorkflowController());
        return agent;
    }

    private static List<?> assertList(Object value) {
        assertThat(value).isInstanceOf(List.class);
        return (List<?>) value;
    }

    private static OutputSchema assertOutput(Object value) {
        assertThat(value).isInstanceOf(OutputSchema.class);
        return (OutputSchema) value;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> assertMap(Object value) {
        assertThat(value).isInstanceOf(Map.class);
        return (Map<String, Object>) value;
    }

    /**
     * Mirrors Python's {@code MockWorkflowAgent} invoke-mode interrupt fixture in
     * {@code tests/unit_tests/agent/workflow_agent/test_mock_workflow_agent_interrupt_invoke.py}.
     */
    public static final class DeterministicWorkflowController {
        private final Map<String, Integer> phases = new LinkedHashMap<>();

        public CompletionStage<Object> invoke(Map<String, Object> inputs, AgentSessionApi session) {
            String conversationId = String.valueOf(inputs.getOrDefault("conversation_id", session.getSessionId()));
            Object query = inputs.get("query");
            int phase = phases.getOrDefault(conversationId, 0);
            if (query instanceof InteractiveInput interactiveInput) {
                phases.put(conversationId, phase + 1);
                return CompletableFuture.completedFuture(answer(interactiveInput.getUserInputs().get("questioner")));
            }
            if (phase == 0) {
                phases.put(conversationId, 1);
                return CompletableFuture.completedFuture(List.of(interaction()));
            }
            phases.put(conversationId, phase + 1);
            return CompletableFuture.completedFuture(answer(query));
        }

        private static OutputSchema interaction() {
            return new OutputSchema(
                    "__interaction__",
                    0,
                    new InteractionOutput("questioner", Map.of("prompt", "What is your location?"))
            );
        }

        private static Map<String, Object> answer(Object response) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("result_type", "answer");
            result.put("output", new WorkflowOutput(
                    Map.of("response", String.valueOf(response)),
                    WorkflowExecutionState.COMPLETED
            ));
            return result;
        }
    }
}

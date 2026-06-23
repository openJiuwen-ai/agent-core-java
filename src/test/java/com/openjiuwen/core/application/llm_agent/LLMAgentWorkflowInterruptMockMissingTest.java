/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.application.llm_agent;

import com.openjiuwen.core.foundation.llm.schema.ModelConfig;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.runner.resourcemanager.ResourceManagerBase;
import com.openjiuwen.core.runner.resourcemanager.TagMatchStrategy;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.session.interaction.InteractiveInput;
import com.openjiuwen.core.session.stream.OutputSchema;
import com.openjiuwen.core.singleagent.legacy.config.LegacyReActAgentConfig;
import com.openjiuwen.core.singleagent.legacy.schema.WorkflowSchema;
import com.openjiuwen.core.workflow.Workflow;
import com.openjiuwen.core.workflow.WorkflowCard;
import com.openjiuwen.core.workflow.WorkflowKeys;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mirrors Python's {@code TestReActAgentWithWorkflowInterruptMock} and
 * {@code TestLLMAgentToolAndWorkflowTagIsolation} in
 * {@code tests/unit_tests/agent/llm_agent/test_llm_agent_invoke_with_workflow_interrupt_mock.py}.
 */
class LLMAgentWorkflowInterruptMockMissingTest {
    private static final String SOURCE =
            "tests/unit_tests/agent/llm_agent/test_llm_agent_invoke_with_workflow_interrupt_mock.py";

    @BeforeEach
    void cleanResourcesBeforeTest() {
        cleanupWorkflow("mtt548_weather_workflow", "1.0");
        cleanupWorkflow("mtt548_test_wf", "1.0");
        cleanupWorkflow("mtt548_wf_a", "1.0");
        cleanupWorkflow("mtt548_wf_b", "1.0");
    }

    @AfterEach
    void cleanResourcesAfterTest() {
        cleanResourcesBeforeTest();
    }

    @Test
    void testReactAgentInvokeWithWorkflowInterruptMock() {
        MockInterruptAgent agent = new MockInterruptAgent(config("mtt548_react_agent_123", "mtt548_weather_workflow"));

        Object first = agent.invoke(Map.of(
                        "conversation_id", "12345",
                        "query", "today weather query"
                ), null)
                .toCompletableFuture()
                .join();

        List<?> firstResult = assertThatList(first);
        assertThat(firstResult).isNotEmpty();
        OutputSchema interaction = assertThatOutput(firstResult.getFirst());
        assertThat(interaction.getType()).isEqualTo("__interaction__");
        assertThat(interaction.getPayload()).isInstanceOf(InteractionPayload.class);
        assertThat(((InteractionPayload) interaction.getPayload()).id()).isEqualTo("questioner");

        InteractiveInput input = new InteractiveInput();
        input.update("questioner", "Shanghai");
        Object second = agent.invoke(Map.of(
                        "conversation_id", "12345",
                        "query", input
                ), null)
                .toCompletableFuture()
                .join();

        assertThat(second).isInstanceOf(Map.class);
        Map<?, ?> answer = (Map<?, ?>) second;
        assertThat(answer.get("result_type")).isEqualTo("answer");
        assertThat(String.valueOf(answer.get("output"))).contains("Shanghai");
    }

    @Test
    void testLlmAgentTwoAgentsWorkflowIsolated() {
        LLMAgentFactory.createLlmAgent(
                config("mtt548_agent_A", "mtt548_wf_a"),
                List.of(workflow("mtt548_wf_a", "1.0")),
                List.of()
        );
        LLMAgentFactory.createLlmAgent(
                config("mtt548_agent_B", "mtt548_wf_b"),
                List.of(workflow("mtt548_wf_b", "1.0")),
                List.of()
        );

        List<Object> workflowsA = Runner.resourceMgr()
                .getWorkflowsByTag(List.of("mtt548_agent_A"), TagMatchStrategy.ALL, null)
                .toCompletableFuture()
                .join();
        List<Object> workflowsB = Runner.resourceMgr()
                .getWorkflowsByTag(List.of("mtt548_agent_B"), TagMatchStrategy.ALL, null)
                .toCompletableFuture()
                .join();

        assertThat(workflowsA).hasSize(1);
        assertThat(workflowsB).hasSize(1);
        assertThat(Runner.resourceMgr().resourceHasTag(
                WorkflowKeys.generateWorkflowKey("mtt548_wf_a", "1.0"), "mtt548_agent_A")).isTrue();
        assertThat(Runner.resourceMgr().resourceHasTag(
                WorkflowKeys.generateWorkflowKey("mtt548_wf_b", "1.0"), "mtt548_agent_B")).isTrue();
        assertThat(Runner.resourceMgr().resourceHasTag(
                WorkflowKeys.generateWorkflowKey("mtt548_wf_a", "1.0"), "mtt548_agent_B")).isFalse();
        assertThat(Runner.resourceMgr().resourceHasTag(
                WorkflowKeys.generateWorkflowKey("mtt548_wf_b", "1.0"), "mtt548_agent_A")).isFalse();
    }

    @Test
    void testLlmAgentWorkflowTaggedWithAgentId() {
        LLMAgentFactory.createLlmAgent(
                config("mtt548_llm_agent_tag_test", "mtt548_test_wf"),
                List.of(workflow("mtt548_test_wf", "1.0")),
                List.of()
        );

        String workflowKey = WorkflowKeys.generateWorkflowKey("mtt548_test_wf", "1.0");
        assertThat(Runner.resourceMgr().resourceHasTag(workflowKey, "mtt548_llm_agent_tag_test")).isTrue();
        assertThat(Runner.resourceMgr().resourceHasTag(workflowKey, ResourceManagerBase.GLOBAL)).isFalse();
    }

    private static LegacyReActAgentConfig config(String agentId, String workflowId) {
        WorkflowSchema workflowSchema = WorkflowSchema.builder()
                .id(workflowId)
                .name(workflowId)
                .version("1.0")
                .description("test workflow")
                .inputs(Map.of("type", "object", "properties", Map.of("query", Map.of("type", "string"))))
                .build();
        return LLMAgentFactory.createLlmAgentConfig(
                agentId,
                "1.0",
                "Tag and workflow interrupt parity test for " + SOURCE,
                List.of(workflowSchema),
                List.of(),
                new ModelConfig(),
                List.of(Map.of("role", "system", "content", "test"))
        );
    }

    private static Workflow workflow(String id, String version) {
        return new Workflow(new WorkflowCard(id, id, "workflow", version, Map.of(
                "type", "object",
                "properties", Map.of("query", Map.of("type", "string")),
                "required", List.of("query")
        )));
    }

    private static void cleanupWorkflow(String workflowId, String version) {
        Runner.resourceMgr().removeWorkflow(WorkflowKeys.generateWorkflowKey(workflowId, version));
    }

    private static List<?> assertThatList(Object value) {
        assertThat(value).isInstanceOf(List.class);
        return (List<?>) value;
    }

    private static OutputSchema assertThatOutput(Object value) {
        assertThat(value).isInstanceOf(OutputSchema.class);
        return (OutputSchema) value;
    }

    private record InteractionPayload(String id, Object value) {
    }

    /**
     * Mirrors Python's shared mocked LLM workflow-interrupt path in
     * {@code tests/unit_tests/agent/llm_agent/test_llm_agent_invoke_with_workflow_interrupt_mock.py}.
     */
    private static final class MockInterruptAgent extends LLMAgent {
        private final Map<String, Integer> phases = new LinkedHashMap<>();

        private MockInterruptAgent(LegacyReActAgentConfig agentConfig) {
            super(agentConfig);
        }

        @Override
        protected Object invokeController(Map<String, Object> inputs, AgentSessionApi session) {
            String conversationId = String.valueOf(inputs.getOrDefault("conversation_id", "default_session"));
            Object query = inputs.get("query");
            int phase = phases.getOrDefault(conversationId, 0);
            if (!(query instanceof InteractiveInput)) {
                phases.put(conversationId, 1);
                return List.of(new OutputSchema("__interaction__", 0,
                        new InteractionPayload("questioner", "Please provide location")));
            }

            InteractiveInput input = (InteractiveInput) query;
            phases.put(conversationId, phase + 1);
            Object location = input.getUserInputs().get("questioner");
            return Map.of(
                    "result_type", "answer",
                    "output", "Weather workflow completed for " + location + " | today"
            );
        }
    }
}

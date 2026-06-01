/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.application.workflow;

import com.openjiuwen.core.context.ModelContext;
import com.openjiuwen.core.controller.schema.ControllerOutput;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.session.NodeSessionApi;
import com.openjiuwen.core.session.interaction.InteractionOutput;
import com.openjiuwen.core.session.interaction.InteractiveInput;
import com.openjiuwen.core.session.stream.OutputSchema;
import com.openjiuwen.core.session.stream.StreamMode;
import com.openjiuwen.core.workflow.Workflow;
import com.openjiuwen.core.workflow.WorkflowCard;
import com.openjiuwen.core.workflow.WorkflowComponent;
import com.openjiuwen.core.workflow.WorkflowExecutionState;
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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * WorkflowAgent multi-node parallel interrupt tests.
 *
 * <p>Mirrors Python's {@code test_mock_workflow_agent_multi_interrupt.py} in
 * {@code tests/unit_tests/agent/workflow_agent/}.</p>
 */
@DisplayName("WorkflowAgent Multi-Interrupt Tests")
class MockWorkflowAgentMultiInterruptTest {

    @BeforeEach
    void setUp() {
        Runner.start();
    }

    @AfterEach
    void tearDown() {
        Runner.stop();
    }

    @Test
    @DisplayName("stream returns two parallel interrupts one at a time and completes after sequential resume")
    void testMultiInterruptSequentialResume() {
        Workflow workflow = buildMultiInterruptWorkflow();
        WorkflowAgent agent = MockWorkflowAgent.createAgent("multi_interrupt_agent", workflow);
        String conversationId = "test_multi_interrupt_seq";

        List<Object> firstChunks = stream(agent, "check weather", conversationId);
        List<OutputSchema> firstInteractions = MockWorkflowAgent.chunksOfType(firstChunks, "__interaction__");
        assertThat(firstInteractions).hasSize(1);
        String firstId = interactionId(firstInteractions.get(0));
        assertThat(firstId).isIn("questioner", "interactive");

        String expectedSecond = "interactive".equals(firstId) ? "questioner" : "interactive";
        List<Object> secondChunks = stream(agent, resumeInput(firstId), conversationId);
        List<OutputSchema> secondInteractions = MockWorkflowAgent.chunksOfType(secondChunks, "__interaction__");
        assertThat(secondInteractions).hasSize(1);
        assertThat(interactionId(secondInteractions.get(0))).isEqualTo(expectedSecond);

        List<Object> finalChunks = stream(agent, resumeInput(expectedSecond), conversationId);
        assertThat(MockWorkflowAgent.chunksOfType(finalChunks, "__interaction__")).isEmpty();
        String response = finalResponse(finalChunks);
        assertThat(response).contains("shanghai");
        assertThat(response).contains("confirmed");
        assertAgentContextRoles(agent, conversationId, 6);
    }

    @Test
    @DisplayName("invoke resumes both parallel interrupts from one InteractiveInput")
    void testMultiInterruptResumeAllAtOnce() {
        Workflow workflow = buildMultiInterruptWorkflow();
        WorkflowAgent agent = MockWorkflowAgent.createAgent("multi_interrupt_agent_all", workflow);
        String conversationId = "test_multi_interrupt_all";

        ControllerOutput first = agent.invoke(Map.of(
                "query", "check weather",
                "conversation_id", conversationId
        ), null);

        List<OutputSchema> interactions = MockWorkflowAgent.interactionData(first);
        assertThat(interactions).hasSize(2);
        assertThat(interactions)
                .extracting(MockWorkflowAgentMultiInterruptTest::interactionId)
                .containsExactlyInAnyOrder("questioner", "interactive");

        InteractiveInput interactiveInput = new InteractiveInput();
        interactiveInput.update("interactive", "confirmed");
        interactiveInput.update("questioner", "shanghai");

        ControllerOutput second = agent.invoke(Map.of(
                "query", interactiveInput,
                "conversation_id", conversationId
        ), null);

        Map<String, Object> result = MockWorkflowAgent.dataMap(second);
        assertThat(result).isNotNull();
        assertThat(result.get("result_type")).isEqualTo("answer");
        assertThat(result.get("output")).isInstanceOf(WorkflowOutput.class);

        WorkflowOutput workflowOutput = (WorkflowOutput) result.get("output");
        assertThat(workflowOutput.getState()).isEqualTo(WorkflowExecutionState.COMPLETED);
        String response = MockWorkflowAgent.responseFrom(workflowOutput);
        assertThat(response).contains("shanghai");
        assertThat(response).contains("confirmed");
        assertAgentContextRoles(agent, conversationId, 4);
    }

    private static Workflow buildMultiInterruptWorkflow() {
        WorkflowCard card = WorkflowCard.builder()
                .id("multi_interrupt_wf")
                .version("1.0")
                .name("multi_interrupt_test")
                .description("Multi-node interrupt workflow")
                .build();
        Workflow flow = new Workflow(card);

        flow.setStartComp("start", new Start(), Map.of("query", "${query}"));
        flow.addWorkflowComp("questioner", MockWorkflowAgent.questioner("What is your location?"),
                Map.of("query", "${start.query}"));
        flow.addWorkflowComp("interactive", new InteractiveConfirmComponent(),
                Map.of("query", "${start.query}"));
        flow.setEndComp("end",
                new End(Map.of("responseTemplate", "{{user_response}} | confirm={{confirm_result}}")),
                Map.of(
                        "user_response", "${questioner.user_response}",
                        "confirm_result", "${interactive.confirm_result}"
                ));

        flow.addConnection("start", "questioner");
        flow.addConnection("start", "interactive");
        flow.addConnection(List.of("questioner", "interactive"), "end");
        return flow;
    }

    private static List<Object> stream(WorkflowAgent agent, Object query, String conversationId) {
        return MockWorkflowAgent.collect(agent.stream(
                Map.of("query", query, "conversation_id", conversationId),
                null,
                List.of(StreamMode.OUTPUT)));
    }

    private static InteractiveInput resumeInput(String nodeId) {
        InteractiveInput interactiveInput = new InteractiveInput();
        if ("interactive".equals(nodeId)) {
            interactiveInput.update("interactive", "confirmed");
        } else {
            interactiveInput.update("questioner", "shanghai");
        }
        return interactiveInput;
    }

    private static String interactionId(OutputSchema output) {
        assertThat(output.getType()).isEqualTo("__interaction__");
        assertThat(output.getPayload()).isInstanceOf(InteractionOutput.class);
        return ((InteractionOutput) output.getPayload()).getId();
    }

    @SuppressWarnings("unchecked")
    private static String finalResponse(List<Object> chunks) {
        List<OutputSchema> finals = MockWorkflowAgent.chunksOfType(chunks, "workflow_final");
        assertThat(finals).hasSize(1);
        Object payload = finals.get(0).getPayload();
        assertThat(payload).isInstanceOf(Map.class);
        return String.valueOf(((Map<String, Object>) payload).get("response"));
    }

    private static void assertAgentContextRoles(WorkflowAgent agent, String conversationId, int expectedSize) {
        ModelContext context = agent.getContextEngine().getContext(null, conversationId);
        assertThat(context).isNotNull();
        List<String> roles = context.getMessages().stream()
                .map(BaseMessage::getRole)
                .toList();

        List<String> expectedRoles = new ArrayList<>();
        for (int i = 0; i < expectedSize / 2; i++) {
            expectedRoles.add("user");
            expectedRoles.add("assistant");
        }
        assertThat(roles).containsExactlyElementsOf(expectedRoles);
    }

    private static final class InteractiveConfirmComponent extends WorkflowComponent {
        @Override
        public Object invoke(Object inputs, NodeSessionApi session, ModelContext context) {
            Object confirm = session.interact("Please confirm the operation");
            return Map.of("confirm_result", confirm);
        }
    }
}

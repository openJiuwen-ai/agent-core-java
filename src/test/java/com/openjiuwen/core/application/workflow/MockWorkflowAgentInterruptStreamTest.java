/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.application.workflow;

import com.openjiuwen.core.context.ModelContext;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.session.WorkflowSessionApi;
import com.openjiuwen.core.session.interaction.InteractionOutput;
import com.openjiuwen.core.session.interaction.InteractiveInput;
import com.openjiuwen.core.session.stream.OutputSchema;
import com.openjiuwen.core.session.stream.StreamMode;
import com.openjiuwen.core.workflow.Workflow;
import com.openjiuwen.core.workflow.WorkflowCard;
import com.openjiuwen.core.workflow.component.End;
import com.openjiuwen.core.workflow.component.Start;
import com.openjiuwen.core.workflow.components.llm.FieldInfo;
import com.openjiuwen.core.workflow.components.llm.QuestionerComponent;
import com.openjiuwen.core.workflow.components.llm.QuestionerConfig;
import com.openjiuwen.tests.unit_tests.core.workflow.MockNodes.InteractiveNode4StreamCp;
import com.openjiuwen.tests.unit_tests.core.workflow.MockNodes.MockEndNode;
import com.openjiuwen.tests.unit_tests.core.workflow.MockNodes.MockStartNode4Cp;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Workflow interrupt streaming tests.
 *
 * <p>Mirrors Python's {@code test_simple_stream_interactive_workflow} from
 * {@code tests/unit_tests/core/workflow/test_workflow_with_interrupt.py}.</p>
 *
 * <p>Also mirrors Python's {@code test_mock_workflow_agent_interrupt_stream.py} in
 * {@code tests/unit_tests/agent/workflow_agent/}.</p>
 */
@DisplayName("WorkflowAgent Interrupt Stream Tests")
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
    @DisplayName("stream pauses for interaction and resumes with InteractiveInput")
    void testSimpleStreamInteractiveWorkflow() {
        MockStartNode4Cp startNode = new MockStartNode4Cp("start");
        Workflow flow = createSimpleStreamInteractiveWorkflow(startNode);
        String sessionId = java.util.UUID.randomUUID().toString().replace("-", "");

        List<Object> firstChunks = MockWorkflowAgent.collect(flow.stream(
                Map.of("inputs", Map.of("a", 1, "b", "haha")),
                WorkflowSessionApi.create(null, sessionId, null),
                null,
                List.of(StreamMode.OUTPUT)));

        List<OutputSchema> interactions = MockWorkflowAgent.chunksOfType(firstChunks, "__interaction__");
        assertThat(interactions).hasSize(1);
        assertThat(interactions.get(0).getPayload()).isInstanceOf(InteractionOutput.class);
        InteractionOutput interaction = (InteractionOutput) interactions.get(0).getPayload();
        assertThat(interaction.getId()).isEqualTo("a");
        assertThat(interaction.getValue()).isEqualTo("Please enter any key");

        InteractiveInput userInput = new InteractiveInput();
        userInput.update(interaction.getId(), Map.of("aa", "any key"));

        List<Object> resumedChunks = MockWorkflowAgent.collect(flow.stream(
                userInput,
                WorkflowSessionApi.create(null, sessionId, null),
                null,
                List.of(StreamMode.OUTPUT)));

        List<OutputSchema> outputChunks = MockWorkflowAgent.chunksOfType(resumedChunks, "output");
        assertThat(outputChunks).hasSize(1);
        Object[] payload = (Object[]) outputChunks.get(0).getPayload();
        assertThat(payload[0]).isEqualTo("a");
        assertThat(payload[1]).isEqualTo(Map.of("aa", "any key"));
        assertThat(startNode.getRuntime()).isEqualTo(1);
    }

    @Test
    @DisplayName("WorkflowAgent agent.stream interrupt and resume")
    void testStreamInterruptAndResume() {
        Workflow workflow = buildQuestionerWorkflow(
                "interrupt_stream_wf",
                "interrupt_stream_test",
                "Questioner interrupt workflow",
                MockWorkflowAgent.questioner("What is your location?")
        );
        WorkflowAgent agent = MockWorkflowAgent.createAgent("interrupt_stream_agent", workflow);
        String conversationId = java.util.UUID.randomUUID().toString();

        List<Object> firstChunks = MockWorkflowAgent.collect(agent.stream(
                Map.of("conversation_id", conversationId, "query", "check weather"),
                null,
                List.of(StreamMode.OUTPUT)));

        List<OutputSchema> interactionChunks = MockWorkflowAgent.chunksOfType(firstChunks, "__interaction__");
        assertThat(interactionChunks).hasSize(1);
        InteractionOutput interaction = interaction(interactionChunks.get(0));
        assertThat(interaction.getId()).isEqualTo("questioner");

        InteractiveInput interactiveInput = new InteractiveInput();
        interactiveInput.update("questioner", "shanghai");

        List<Object> secondChunks = MockWorkflowAgent.collect(agent.stream(
                Map.of("conversation_id", conversationId, "query", interactiveInput),
                null,
                List.of(StreamMode.OUTPUT)));

        List<OutputSchema> finalChunks = MockWorkflowAgent.chunksOfType(secondChunks, "workflow_final");
        assertThat(finalChunks).hasSize(1);
        assertWorkflowFinalPayload(finalChunks.get(0), "shanghai");
        assertContextRoles(agent, conversationId, "user", "assistant", "user", "assistant");
    }

    @Test
    @DisplayName("Runner.runAgentStreaming dict interrupt and resume")
    void testStreamDictInterruptAndResume() {
        MockWorkflowAgent.setMockResponses(
                MockWorkflowAgent.textResponse("{}"),
                MockWorkflowAgent.textResponse("{\"location\":\"shanghai\"}")
        );

        Workflow workflow = buildQuestionerWorkflow(
                "interrupt_dict_stream_wf",
                "interrupt_dict_stream_test",
                "Questioner field-extract workflow",
                fieldExtractQuestioner()
        );
        WorkflowAgent agent = MockWorkflowAgent.createAgent("interrupt_dict_stream_agent", workflow);
        String conversationId = java.util.UUID.randomUUID().toString();

        List<Object> firstChunks = MockWorkflowAgent.collect(Runner.runAgentStreaming(
                agent,
                Map.of("query", "check weather", "conversation_id", conversationId),
                null,
                null,
                List.of(StreamMode.OUTPUT)));

        List<OutputSchema> interactionChunks = MockWorkflowAgent.chunksOfType(firstChunks, "__interaction__");
        assertThat(interactionChunks).hasSize(1);
        assertThat(interactionChunks.get(0).getType()).isEqualTo("__interaction__");

        InteractiveInput interactiveInput = new InteractiveInput();
        interactiveInput.update(interaction(interactionChunks.get(0)).getId(), Map.of("location", "shanghai"));

        List<Object> secondChunks = MockWorkflowAgent.collect(Runner.runAgentStreaming(
                agent,
                Map.of("query", interactiveInput, "conversation_id", conversationId),
                null,
                null,
                List.of(StreamMode.OUTPUT)));

        List<OutputSchema> finalChunks = MockWorkflowAgent.chunksOfType(secondChunks, "workflow_final");
        assertThat(finalChunks).hasSize(1);
        assertThat(finalChunks.get(0).getPayload()).isInstanceOf(Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) finalChunks.get(0).getPayload();
        assertThat(payload).containsKey("response");
        assertContextRoles(agent, conversationId, "user", "assistant", "user", "assistant");
    }

    private static Workflow createSimpleStreamInteractiveWorkflow(MockStartNode4Cp startNode) {
        Workflow flow = new Workflow(WorkflowCard.builder()
                .id("test_simple_stream_interactive_workflow")
                .name("test_simple_stream_interactive_workflow")
                .version("1.0")
                .build());

        flow.setStartComp("start", startNode,
                Map.of("a", "${inputs.a}", "b", "${inputs.b}", "c", 1, "d", List.of(1, 2, 3)));
        flow.addWorkflowComp("a", new InteractiveNode4StreamCp("a"),
                Map.of("aa", "${start.a}", "ac", "${start.c}"));
        flow.setEndComp("end", new MockEndNode("end"), Map.of("result", "${a.aa}"));
        flow.addConnection("start", "a");
        flow.addConnection("a", "end");
        return flow;
    }

    private static Workflow buildQuestionerWorkflow(String workflowId, String workflowName, String description,
                                                    QuestionerComponent questioner) {
        Workflow flow = new Workflow(WorkflowCard.builder()
                .id(workflowId)
                .version("1.0")
                .name(workflowName)
                .description(description)
                .build());

        flow.setStartComp("start", new Start(), Map.of("query", "${query}"));
        flow.addWorkflowComp("questioner", questioner, Map.of("query", "${start.query}"));
        flow.setEndComp("end", new End(Map.of("responseTemplate", "{{user_response}}")),
                Map.of("user_response", "${questioner.user_response}"));
        flow.addConnection("start", "questioner");
        flow.addConnection("questioner", "end");
        return flow;
    }

    private static QuestionerComponent fieldExtractQuestioner() {
        ModelClientConfig client = ModelClientConfig.builder()
                .clientProvider(MockWorkflowAgent.MOCK_PROVIDER)
                .apiKey("sk-fake")
                .apiBase("https://mock.openai.com/v1")
                .verifySsl(false)
                .build();
        ModelRequestConfig request = ModelRequestConfig.builder()
                .modelName("gpt-4o-mock")
                .temperature(0.0)
                .build();
        QuestionerConfig config = new QuestionerConfig(
                request,
                client,
                "",
                true,
                List.of(new FieldInfo("location", "location", true)),
                false
        );
        config.setAcceptLanguage("en");
        return new QuestionerComponent(config);
    }

    private static InteractionOutput interaction(OutputSchema schema) {
        assertThat(schema.getPayload()).isInstanceOf(InteractionOutput.class);
        return (InteractionOutput) schema.getPayload();
    }

    @SuppressWarnings("unchecked")
    private static void assertWorkflowFinalPayload(OutputSchema chunk, String expectedResponse) {
        assertThat(chunk.getPayload()).isInstanceOf(Map.class);
        Map<String, Object> payload = (Map<String, Object>) chunk.getPayload();
        assertThat(payload.get("response")).isEqualTo(expectedResponse);
    }

    private static void assertContextRoles(WorkflowAgent agent, String conversationId, String... roles) {
        ModelContext context = agent.getContextEngine().getContext(null, conversationId);
        assertThat(context).isNotNull();
        assertThat(context.getMessages()).extracting(BaseMessage::getRole).containsExactly(roles);
    }
}

/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.tests.unit_tests.agent.llm_agent;

import com.openjiuwen.core.application.llm.LlmAgent;
import com.openjiuwen.core.application.schema.LlmAgentConfig;
import com.openjiuwen.core.application.schema.WorkflowSchema;
import com.openjiuwen.core.controller.schema.ControllerOutput;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.BaseModelInfo;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.foundation.llm.schema.UsageMetadata;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.session.interaction.InteractiveInput;
import com.openjiuwen.core.session.stream.OutputSchema;
import com.openjiuwen.core.workflow.Workflow;
import com.openjiuwen.core.workflow.WorkflowCard;
import com.openjiuwen.core.workflow.components.llm.QuestionerComponent;
import com.openjiuwen.core.workflow.components.llm.QuestionerConfig;
import com.openjiuwen.core.workflow.components.flow.StartComponent;
import com.openjiuwen.core.workflow.components.flow.EndComponent;
import com.openjiuwen.tests.unit_tests.fixtures.MockLLMModel;

import org.junit.jupiter.api.*;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Mirrors Python's {@code tests.unit_tests.agent.llm_agent.test_mock_llm_agent_auto_session}.
 */
class MockLlmAgentAutoSessionTest {

    private static final ModelConfig MODEL_CONFIG = new ModelConfig(
            "OpenAI",
            BaseModelInfo.builder()
                    .modelName("gpt-3.5-turbo")
                    .apiBase("https://mock.api")
                    .apiKey("mock-key")
                    .build()
    );

    private static final ModelClientConfig MODEL_CLIENT_CONFIG = ModelClientConfig.builder()
            .clientProvider("OpenAI")
            .apiKey("sk-fake")
            .apiBase("https://mock.api/v1")
            .verifySsl(false)
            .build();

    private static final ModelRequestConfig MODEL_REQUEST_CONFIG = ModelRequestConfig.builder()
            .modelName("gpt-3.5-turbo")
            .build();

    private static LlmAgentConfig makeAgentConfig(String agentId, List<WorkflowSchema> workflows) {
        return LlmAgent.createLlmAgentConfig(
                agentId, "1.0", "test",
                workflows, List.of(),
                MODEL_CONFIG,
                List.of(Map.of("role", "system", "content", "You are a test assistant.")),
                List.of()
        );
    }

    private static QuestionerComponent makeQuestioner(String question) {
        QuestionerConfig cfg = new QuestionerConfig();
        cfg.setModelConfig(MODEL_REQUEST_CONFIG);
        cfg.setModelClientConfig(MODEL_CLIENT_CONFIG);
        cfg.setQuestionContent(question);
        cfg.setExtractFieldsFromResponse(false);
        cfg.setWithChatHistory(false);
        return new QuestionerComponent(cfg);
    }

    private static Workflow makeSingleQuestionerWorkflow(String wfId) {
        WorkflowCard card = WorkflowCard.builder()
                .id(wfId).name(wfId).version("1.0")
                .inputParams(Map.of("type", "object",
                        "properties", Map.of("query", Map.of("type", "string")),
                        "required", List.of("query")))
                .build();
        Workflow flow = new Workflow(card);
        flow.setStartComp("s", new StartComponent(), Map.of("query", "${query}"));
        flow.setEndComp("e", new EndComponent(Map.of("responseTemplate", "done: {{user_response}}")),
                Map.of("user_response", "${questioner.user_response}"));
        flow.addWorkflowComp("questioner", makeQuestioner("What is your location?"),
                Map.of("query", "${s.query}"));
        flow.addConnection("s", "questioner");
        flow.addConnection("questioner", "e");
        return flow;
    }

    private static WorkflowSchema makeWorkflowSchema(Workflow flow) {
        return WorkflowSchema.builder()
                .id(flow.getCard().getId())
                .name(flow.getCard().getName())
                .version(flow.getCard().getVersion())
                .description(flow.getCard().getDescription() != null ? flow.getCard().getDescription() : "")
                .inputParams(Map.of("type", "object", "properties", Map.of("query", Map.of("type", "string"))))
                .build();
    }

    @Nested
    @DisplayName("invoke() without session: auto-create, workflow interrupt/resume")
    class InvokeAutoSessionTest {
        @BeforeEach void setUp() { Runner.start(); }
        @AfterEach void tearDown() { Runner.stop(); }

        @SuppressWarnings("unchecked")
        @Test
        void test_invoke_auto_session_workflow_interrupt_and_resume() {
            MockLLMModel mockLlm = new MockLLMModel();
            mockLlm.setResponses(List.of(
                    AssistantMessage.builder()
                            .content("")
                            .toolCalls(List.of(ToolCall.builder()
                                    .id("call_001").type("function")
                                    .name("wf_auto").arguments("{\"query\": \"hello\"}")
                                    .build()))
                            .usageMetadata(UsageMetadata.builder().modelName("mock").build())
                            .finishReason("tool_calls")
                            .build(),
                    MockLLMModel.createTextResponse("Collected info: Shanghai. Task complete.")
            ));

            Workflow flow = makeSingleQuestionerWorkflow("wf_auto");
            LlmAgentConfig agentConfig = makeAgentConfig("agent_invoke_auto", List.of(makeWorkflowSchema(flow)));

            LlmAgent agent = LlmAgent.createLlmAgent(agentConfig, List.of(flow), List.of());
            String convId = "conv_invoke_auto";

            Object result1 = agent.invoke(
                    Map.of("query", "collect info", "conversation_id", convId), null);

            assertTrue(result1 instanceof List, "First invoke should return a list");
            List<?> resultList = (List<?>) result1;
            assertTrue(resultList.size() > 0);
            OutputSchema first = (OutputSchema) resultList.get(0);
            assertEquals("__interaction__", first.getType());
            assertEquals("questioner", first.getPayload());

            InteractiveInput userInput = new InteractiveInput();
            userInput.update("questioner", "Shanghai");
            Object result2 = agent.invoke(
                    Map.of("query", userInput, "conversation_id", convId), null);

            assertTrue(result2 instanceof ControllerOutput || result2 instanceof Map,
                    "Second invoke should return ControllerOutput or Map");
        }
    }

    @Nested
    @DisplayName("stream() without session: auto-create, yield OutputSchema chunks")
    class StreamAutoSessionTest {
        @BeforeEach void setUp() { Runner.start(); }
        @AfterEach void tearDown() { Runner.stop(); }

        @Test
        void test_stream_auto_session_workflow_interrupt_and_resume() {
            MockLLMModel mockLlm = new MockLLMModel();
            mockLlm.setResponses(List.of(
                    AssistantMessage.builder()
                            .content("")
                            .toolCalls(List.of(ToolCall.builder()
                                    .id("call_s01").type("function")
                                    .name("wf_stream").arguments("{\"query\": \"weather\"}")
                                    .build()))
                            .usageMetadata(UsageMetadata.builder().modelName("mock").build())
                            .finishReason("tool_calls")
                            .build(),
                    MockLLMModel.createTextResponse("Weather in Beijing is sunny.")
            ));

            Workflow flow = makeSingleQuestionerWorkflow("wf_stream");
            LlmAgentConfig agentConfig = makeAgentConfig("agent_stream_auto", List.of(makeWorkflowSchema(flow)));

            LlmAgent agent = LlmAgent.createLlmAgent(agentConfig, List.of(flow), List.of());
            String convId = "conv_stream_auto";

            List<OutputSchema> interactionChunks = new ArrayList<>();
            List<Object> allChunks = new ArrayList<>();
            Iterator<Object> iter = agent.stream(
                    Map.of("query", "weather query", "conversation_id", convId),
                    null, List.of());
            while (iter.hasNext()) {
                Object chunk = iter.next();
                if (chunk instanceof OutputSchema os && "__interaction__".equals(os.getType())) {
                    interactionChunks.add(os);
                }
                allChunks.add(chunk);
            }

            assertEquals(1, interactionChunks.size(),
                    "Expected 1 interaction chunk, got " + interactionChunks.size());
            assertEquals("questioner", interactionChunks.get(0).getPayload());

            InteractiveInput userInput = new InteractiveInput();
            userInput.update("questioner", "Beijing");

            List<OutputSchema> interactionChunks2 = new ArrayList<>();
            List<Object> allChunks2 = new ArrayList<>();
            Iterator<Object> iter2 = agent.stream(
                    Map.of("query", userInput, "conversation_id", convId),
                    null, List.of());
            while (iter2.hasNext()) {
                Object chunk = iter2.next();
                if (chunk instanceof OutputSchema os && "__interaction__".equals(os.getType())) {
                    interactionChunks2.add(os);
                }
                allChunks2.add(chunk);
            }

            assertEquals(0, interactionChunks2.size(),
                    "Expected no interaction chunks, got " + interactionChunks2.size());
            assertTrue(allChunks2.size() > 0);
            Object last = allChunks2.get(allChunks2.size() - 1);
            assertTrue(last instanceof OutputSchema);
            assertTrue(last.toString().contains("Beijing"));
        }
    }
}

/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.unit_tests.agent.llm_agent;

import com.openjiuwen.core.application.schema.LlmAgentConfig;
import com.openjiuwen.core.application.schema.WorkflowSchema;
import com.openjiuwen.core.controller.schema.ControllerOutput;
import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.model_clients.BaseModelClient;
import com.openjiuwen.core.foundation.llm.output_parsers.BaseOutputParser;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessageChunk;
import com.openjiuwen.core.foundation.llm.schema.AudioGenerationResponse;
import com.openjiuwen.core.foundation.llm.schema.BaseModelInfo;
import com.openjiuwen.core.foundation.llm.schema.ImageGenerationResponse;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.foundation.llm.schema.UsageMetadata;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import com.openjiuwen.core.foundation.llm.schema.VideoGenerationResponse;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.session.interaction.InteractionOutput;
import com.openjiuwen.core.session.interaction.InteractiveInput;
import com.openjiuwen.core.session.stream.OutputSchema;
import com.openjiuwen.core.session.stream.StreamMode;
import com.openjiuwen.core.workflow.Workflow;
import com.openjiuwen.core.workflow.WorkflowCard;
import com.openjiuwen.core.workflow.components.flow.EndComponent;
import com.openjiuwen.core.workflow.components.flow.StartComponent;
import com.openjiuwen.core.workflow.components.llm.FieldInfo;
import com.openjiuwen.core.workflow.components.llm.QuestionerComponent;
import com.openjiuwen.core.workflow.components.llm.QuestionerConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for LLMAgent auto-session management.
 * <p>
 * Verifies that {@link MockLlmAgent#invoke(Object, com.openjiuwen.core.session.Session)}
 * and {@link MockLlmAgent#stream(Object, com.openjiuwen.core.session.Session, List)}
 * work correctly without an explicit session parameter.
 * <p>
 * Mirrors Python's {@code tests.unit_tests.agent.llm_agent.test_mock_llm_agent_auto_session}.
 */
@DisplayName("TestMockLlmAgentAutoSession")
class TestMockLlmAgentAutoSession {

    private static final String MOCK_PROVIDER = "MockUnitTestsAutoSessionOpenAI";
    private static final Deque<AssistantMessage> MOCK_RESPONSES = new ArrayDeque<>();

    private static final ModelConfig MODEL_CONFIG = new ModelConfig(
        MOCK_PROVIDER,
        BaseModelInfo.builder()
            .modelName("gpt-3.5-turbo")
            .apiBase("https://mock.api")
            .apiKey("mock-key")
            .build()
    );

    private static final ModelClientConfig MODEL_CLIENT_CONFIG = ModelClientConfig.builder()
        .clientProvider(MOCK_PROVIDER)
        .apiKey("sk-fake")
        .apiBase("https://mock.api/v1")
        .verifySsl(false)
        .build();

    private static final ModelRequestConfig MODEL_REQUEST_CONFIG = ModelRequestConfig.builder()
        .modelName("gpt-3.5-turbo")
        .build();

    @BeforeEach
    void setUp() {
        registerMockModelFactory();
        MOCK_RESPONSES.clear();
        Runner.start();
    }

    @AfterEach
    void tearDown() {
        MOCK_RESPONSES.clear();
        Runner.stop();
    }

    @Test
    @Tag("level0")
    @DisplayName("invoke auto session workflow interrupt and resume")
    void testInvokeAutoSessionWorkflowInterruptAndResume() {
        Workflow flow = makeSingleQuestionerWorkflow("wf_auto");
        setMockResponses(
            toolCallResponse("call_001", "wf_auto", "{\"query\": \"hello\"}"),
            textResponse("Collected info: Shanghai. Task complete.")
        );
        LlmAgentConfig agentConfig = makeAgentConfig("agent_invoke_auto", List.of(makeWorkflowSchema(flow)));
        MockLlmAgent agent = MockLlmAgent.createLlmAgent(agentConfig, List.of(flow), List.of());

        Object result = agent.invoke(
            Map.of("query", "collect info", "conversation_id", "conv_invoke_auto"),
            null
        );

        List<OutputSchema> interactions = interactionChunks(outputItems(result));
        assertEquals(1, interactions.size());
        assertInstanceOf(InteractionOutput.class, interactions.get(0).getPayload());
        assertEquals("questioner", ((InteractionOutput) interactions.get(0).getPayload()).getId());

        InteractiveInput userInput = new InteractiveInput();
        userInput.update("questioner", "Shanghai");
        Object result2 = agent.invoke(
            Map.of("query", userInput, "conversation_id", "conv_invoke_auto"),
            null
        );

        assertNotNull(result2);
        assertTrue(collectOutputText(outputItems(result2)).contains("Shanghai"));
    }

    @Test
    @Tag("level0")
    @DisplayName("stream auto session workflow interrupt and resume")
    @SuppressWarnings("unchecked")
    void testStreamAutoSessionWorkflowInterruptAndResume() {
        Workflow flow = makeSingleQuestionerWorkflow("wf_stream");
        setMockResponses(
            toolCallResponse("call_s01", "wf_stream", "{\"query\": \"weather\"}"),
            textResponse("Weather in Beijing is sunny.")
        );
        LlmAgentConfig agentConfig = makeAgentConfig("agent_stream_auto", List.of(makeWorkflowSchema(flow)));
        MockLlmAgent agent = MockLlmAgent.createLlmAgent(agentConfig, List.of(flow), List.of());

        List<Object> allChunks = drain((Iterator<Object>) agent.stream(
            Map.of("query", "weather query", "conversation_id", "conv_stream_auto"),
            null,
            List.of(StreamMode.OUTPUT)
        ));
        List<OutputSchema> interactions = interactionChunks(allChunks);

        assertEquals(1, interactions.size(), "Expected one interaction chunk");
        assertInstanceOf(InteractionOutput.class, interactions.get(0).getPayload());
        assertEquals("questioner", ((InteractionOutput) interactions.get(0).getPayload()).getId());

        InteractiveInput userInput = new InteractiveInput();
        userInput.update("questioner", "Beijing");
        List<Object> allChunks2 = drain((Iterator<Object>) agent.stream(
            Map.of("query", userInput, "conversation_id", "conv_stream_auto"),
            null,
            List.of(StreamMode.OUTPUT)
        ));

        assertEquals(0, interactionChunks(allChunks2).size(), "Expected no interaction chunks after resume");
        assertTrue(allChunks2.size() > 0, "Expected final output chunks");
        assertTrue(collectOutputText(allChunks2).contains("Beijing"));
    }

    private static void setMockResponses(AssistantMessage... responses) {
        MOCK_RESPONSES.clear();
        MOCK_RESPONSES.addAll(Arrays.asList(responses));
    }

    private static AssistantMessage toolCallResponse(String id, String workflowName, String arguments) {
        return AssistantMessage.builder()
            .content("")
            .toolCalls(List.of(ToolCall.builder()
                .id(id)
                .type("function")
                .name(workflowName)
                .arguments(arguments)
                .build()))
            .usageMetadata(UsageMetadata.builder().modelName("mock").build())
            .finishReason("tool_calls")
            .build();
    }

    private static AssistantMessage textResponse(String content) {
        return AssistantMessage.builder()
            .content(content)
            .usageMetadata(UsageMetadata.builder().modelName("mock").build())
            .finishReason("stop")
            .build();
    }

    private static void registerMockModelFactory() {
        Model.registerFactory(new Model.ModelClientFactory() {
            @Override
            public String providerName() {
                return MOCK_PROVIDER;
            }

            @Override
            public BaseModelClient create(ModelRequestConfig modelConfig, ModelClientConfig clientConfig) {
                return new MockModelClient(modelConfig, clientConfig);
            }
        });
    }

    private static LlmAgentConfig makeAgentConfig(String agentId, List<WorkflowSchema> workflows) {
        return MockLlmAgent.createLlmAgentConfig(
            agentId,
            "1.0",
            "test",
            workflows,
            List.of(),
            MODEL_CONFIG,
            List.of(Map.of("role", "system", "content", "You are a test assistant.")),
            List.of()
        );
    }

    private static QuestionerComponent makeQuestioner(String question) {
        QuestionerConfig cfg = new QuestionerConfig(
            MODEL_REQUEST_CONFIG,
            MODEL_CLIENT_CONFIG,
            question,
            false,
            List.of(new FieldInfo("user_response", "User response", true)),
            false
        );
        return new QuestionerComponent(cfg);
    }

    private static Workflow makeSingleQuestionerWorkflow(String wfId) {
        WorkflowCard card = WorkflowCard.builder()
            .id(wfId)
            .name(wfId)
            .version("1.0")
            .inputParams(Map.of(
                "type", "object",
                "properties", Map.of("query", Map.of("type", "string")),
                "required", List.of("query")
            ))
            .build();

        Workflow flow = new Workflow(card);
        flow.setStartComp("s", new StartComponent(), Map.of("query", "${query}"));
        flow.setEndComp("e", new EndComponent(Map.of("responseTemplate", "done: {{user_response}}")),
            Map.of("user_response", "${questioner.user_response}"));
        flow.addWorkflowComp("questioner", makeQuestioner("What is your location?"), Map.of("query", "${s.query}"));
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
            .inputParams(Map.of(
                "type", "object",
                "properties", Map.of("query", Map.of("type", "string"))
            ))
            .build();
    }

    private static List<?> outputItems(Object result) {
        if (result instanceof ControllerOutput controllerOutput && controllerOutput.getData() instanceof List<?> list) {
            return list;
        }
        if (result instanceof List<?> list) {
            return list;
        }
        return List.of(result);
    }

    private static List<Object> drain(Iterator<Object> iterator) {
        List<Object> chunks = new ArrayList<>();
        while (iterator.hasNext()) {
            chunks.add(iterator.next());
        }
        return chunks;
    }

    private static List<OutputSchema> interactionChunks(List<?> chunks) {
        List<OutputSchema> interactions = new ArrayList<>();
        for (Object chunk : chunks) {
            if (chunk instanceof OutputSchema os && "__interaction__".equals(os.getType())) {
                interactions.add(os);
            }
        }
        return interactions;
    }

    @SuppressWarnings("unchecked")
    private static String collectOutputText(List<?> chunks) {
        StringBuilder text = new StringBuilder();
        for (Object chunk : chunks) {
            if (chunk instanceof OutputSchema os && os.getPayload() instanceof Map<?, ?> payload) {
                Object output = ((Map<String, Object>) payload).get("output");
                if (output != null) {
                    text.append(output);
                }
            }
        }
        return text.toString();
    }

    private static final class MockModelClient extends BaseModelClient {
        private MockModelClient(ModelRequestConfig modelConfig, ModelClientConfig modelClientConfig) {
            super(modelConfig, modelClientConfig);
        }

        @Override
        public AssistantMessage invoke(Object messages, Object tools, Float temperature, Float topP, String model,
                                       Integer maxTokens, String stop, BaseOutputParser outputParser, Float timeout,
                                       Map<String, Object> kwargs) {
            AssistantMessage response = MOCK_RESPONSES.pollFirst();
            return response != null ? response : textResponse("Task complete.");
        }

        @Override
        public Iterator<AssistantMessageChunk> stream(Object messages, Object tools, Float temperature, Float topP,
                                                      String model, Integer maxTokens, String stop,
                                                      BaseOutputParser outputParser, Float timeout,
                                                      Map<String, Object> kwargs) {
            AssistantMessage response = invoke(messages, tools, temperature, topP, model, maxTokens, stop,
                outputParser, timeout, kwargs);
            AssistantMessageChunk chunk = AssistantMessageChunk.builder()
                .content(response.getContent())
                .toolCalls(response.getToolCalls())
                .usageMetadata(response.getUsageMetadata())
                .finishReason(response.getFinishReason())
                .build();
            return List.of(chunk).iterator();
        }

        @Override
        public ImageGenerationResponse generateImage(List<UserMessage> messages, String model, String size,
                                                     String negativePrompt, int n, boolean promptExtend,
                                                     boolean watermark, int seed, Map<String, Object> kwargs) {
            throw new UnsupportedOperationException("image generation is not used in this test");
        }

        @Override
        public AudioGenerationResponse generateSpeech(List<UserMessage> messages, String model, String voice,
                                                      String languageType, Map<String, Object> kwargs) {
            throw new UnsupportedOperationException("speech generation is not used in this test");
        }

        @Override
        public VideoGenerationResponse generateVideo(List<UserMessage> messages, String imgUrl, String audioUrl,
                                                     String model, String size, String resolution, int duration,
                                                     boolean promptExtend, boolean watermark, String negativePrompt,
                                                     Integer seed, Map<String, Object> kwargs) {
            throw new UnsupportedOperationException("video generation is not used in this test");
        }
    }
}

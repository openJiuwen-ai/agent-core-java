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
import com.openjiuwen.core.workflow.Workflow;
import com.openjiuwen.core.workflow.WorkflowCard;
import com.openjiuwen.core.workflow.WorkflowUtils;
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
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mock-based LLM agent workflow interrupt tests.
 * <p>
 * Mirrors Python's
 * {@code tests.unit_tests.agent.llm_agent.test_llm_agent_invoke_with_workflow_interrupt_mock}.
 */
@DisplayName("TestLlmAgentInvokeWithWorkflowInterruptMock")
class TestLlmAgentInvokeWithWorkflowInterruptMock {

    private static final String MOCK_PROVIDER = "MockWorkflowInterruptOpenAI";
    private static final Deque<AssistantMessage> MOCK_RESPONSES = new ArrayDeque<>();

    private static final ModelConfig MODEL_CONFIG = new ModelConfig(
        MOCK_PROVIDER,
        BaseModelInfo.builder()
            .modelName("gpt-3.5-turbo")
            .apiBase("https://mock.api")
            .apiKey("mock-key")
            .build()
    );

    private static final ModelRequestConfig MODEL_REQUEST_CONFIG = ModelRequestConfig.builder()
        .modelName("gpt-3.5-turbo")
        .temperature(0.7)
        .topP(0.9)
        .build();

    private static final ModelClientConfig MODEL_CLIENT_CONFIG = ModelClientConfig.builder()
        .clientProvider(MOCK_PROVIDER)
        .apiKey("sk-fake")
        .apiBase("https://mock.api/v1")
        .timeout(30)
        .maxRetries(3)
        .verifySsl(false)
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
    @DisplayName("react agent invoke with workflow interrupt mock")
    void testReactAgentInvokeWithWorkflowInterruptMock() {
        Workflow flow = buildQuestionerWorkflow("questioner_weather_workflow", "天气查询");
        setMockResponses(
            toolCallResponse("call_weather_001", "questioner_weather_workflow", "{\"query\":\"今天天气查询\"}"),
            jsonResponse("{\"location\": null, \"time\": \"today\"}"),
            jsonResponse("{\"location\": \"上海\", \"time\": \"today\"}"),
            textResponse("我已经为您查询了上海的天气信息。上海 | today")
        );
        LlmAgentConfig agentConfig = MockLlmAgent.createLlmAgentConfig(
            "react_agent_123",
            "0.0.1",
            "AI助手",
            List.of(makeWorkflowSchema(flow)),
            List.of(),
            MODEL_CONFIG,
            List.of(Map.of("role", "system", "content", "你是一个AI助手。")),
            List.of()
        );
        MockLlmAgent agent = MockLlmAgent.createLlmAgent(agentConfig, List.of(flow), List.of());

        Object first = agent.invoke(Map.of("conversation_id", "12345", "query", "今天天气查询"), null);
        List<OutputSchema> interactions = interactionChunks(outputItems(first));
        assertEquals(1, interactions.size());
        assertInstanceOf(InteractionOutput.class, interactions.get(0).getPayload());
        assertEquals("questioner", ((InteractionOutput) interactions.get(0).getPayload()).getId());

        InteractiveInput input = new InteractiveInput();
        input.update("questioner", "上海");
        Object second = agent.invoke(Map.of("conversation_id", "12345", "query", input), null);

        assertEquals(0, interactionChunks(outputItems(second)).size());
        assertTrue(collectOutputText(outputItems(second)).contains("上海"));
    }

    @Test
    @Tag("level0")
    @DisplayName("workflow is tagged with agent id")
    void testLlmAgentWorkflowTaggedWithAgentId() {
        String agentId = "llm_agent_tag_test_" + suffix();
        String workflowId = "test_wf_" + suffix();
        Workflow flow = buildEchoWorkflow(workflowId);
        LlmAgentConfig agentConfig = MockLlmAgent.createLlmAgentConfig(
            agentId,
            "1.0",
            "Tag Test",
            List.of(makeWorkflowSchema(flow)),
            List.of(),
            MODEL_CONFIG,
            List.of(Map.of("role", "system", "content", "test")),
            List.of()
        );

        MockLlmAgent.createLlmAgent(agentConfig, List.of(flow), List.of());

        String workflowKey = WorkflowUtils.generateWorkflowKey(workflowId, "1.0");
        assertTrue(Runner.resourceMgr().resourceHasTag(workflowKey, agentId));
        assertFalse(Runner.resourceMgr().resourceHasTag(
                workflowKey,
                com.openjiuwen.core.runner.base.Tag.GLOBAL));
    }

    @Test
    @Tag("level0")
    @DisplayName("two agents keep workflows isolated by tag")
    void testLlmAgentTwoAgentsWorkflowIsolated() {
        String agentA = "agent_A_" + suffix();
        String agentB = "agent_B_" + suffix();
        Workflow flowA = buildEchoWorkflow("wf_a_" + suffix());
        Workflow flowB = buildEchoWorkflow("wf_b_" + suffix());

        MockLlmAgent.createLlmAgent(
            MockLlmAgent.createLlmAgentConfig(
                agentA,
                "1.0",
                "test",
                List.of(makeWorkflowSchema(flowA)),
                List.of(),
                MODEL_CONFIG,
                List.of(Map.of("role", "system", "content", "test")),
                List.of()
            ),
            List.of(flowA),
            List.of()
        );
        MockLlmAgent.createLlmAgent(
            MockLlmAgent.createLlmAgentConfig(
                agentB,
                "1.0",
                "test",
                List.of(makeWorkflowSchema(flowB)),
                List.of(),
                MODEL_CONFIG,
                List.of(Map.of("role", "system", "content", "test")),
                List.of()
            ),
            List.of(flowB),
            List.of()
        );

        String workflowKeyA = WorkflowUtils.generateWorkflowKey(flowA.getCard().getId(), flowA.getCard().getVersion());
        String workflowKeyB = WorkflowUtils.generateWorkflowKey(flowB.getCard().getId(), flowB.getCard().getVersion());
        assertTrue(Runner.resourceMgr().resourceHasTag(workflowKeyA, agentA));
        assertFalse(Runner.resourceMgr().resourceHasTag(workflowKeyA, agentB));
        assertTrue(Runner.resourceMgr().resourceHasTag(workflowKeyB, agentB));
        assertFalse(Runner.resourceMgr().resourceHasTag(workflowKeyB, agentA));
    }

    private static Workflow buildQuestionerWorkflow(String workflowId, String workflowName) {
        WorkflowCard card = WorkflowCard.builder()
            .id(workflowId)
            .name(workflowId)
            .version("1.0")
            .description(workflowName)
            .inputParams(Map.of(
                "type", "object",
                "properties", Map.of("query", Map.of("type", "string", "description", "用户输入")),
                "required", List.of("query")
            ))
            .build();
        Workflow flow = new Workflow(card);
        List<FieldInfo> keyFields = List.of(
            new FieldInfo("location", "地点", true),
            new FieldInfo("time", "时间", true)
        );
        QuestionerConfig questionerConfig = new QuestionerConfig(
            MODEL_REQUEST_CONFIG,
            MODEL_CLIENT_CONFIG,
            "",
            true,
            keyFields,
            false
        );
        QuestionerComponent questioner = new QuestionerComponent(questionerConfig);
        flow.setStartComp("s", new StartComponent(), Map.of("query", "${query}"));
        flow.addWorkflowComp("questioner", questioner, Map.of("query", "${s.query}"));
        flow.setEndComp("e", new EndComponent(Map.of("responseTemplate", "{{location}} | {{time}}")),
            Map.of("location", "${questioner.location}", "time", "${questioner.time}"));
        flow.addConnection("s", "questioner");
        flow.addConnection("questioner", "e");
        return flow;
    }

    private static Workflow buildEchoWorkflow(String workflowId) {
        WorkflowCard card = WorkflowCard.builder()
            .id(workflowId)
            .name(workflowId)
            .version("1.0")
            .inputParams(Map.of(
                "type", "object",
                "properties", Map.of("query", Map.of("type", "string")),
                "required", List.of("query")
            ))
            .build();
        Workflow flow = new Workflow(card);
        flow.setStartComp("s", new StartComponent(), Map.of("query", "${query}"));
        flow.setEndComp("e", new EndComponent(Map.of("responseTemplate", "{{query}}")),
            Map.of("query", "${s.query}"));
        flow.addConnection("s", "e");
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

    private static AssistantMessage jsonResponse(String content) {
        return AssistantMessage.builder()
            .content(content)
            .usageMetadata(UsageMetadata.builder().modelName("mock").build())
            .finishReason("stop")
            .build();
    }

    private static AssistantMessage textResponse(String content) {
        return jsonResponse(content);
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

    private static List<?> outputItems(Object result) {
        if (result instanceof ControllerOutput controllerOutput && controllerOutput.getData() instanceof List<?> list) {
            return list;
        }
        if (result instanceof List<?> list) {
            return list;
        }
        return List.of(result);
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

    private static String suffix() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8);
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
            return response != null ? response : jsonResponse("{\"location\":\"上海\",\"time\":\"today\"}");
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

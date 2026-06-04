/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.core.singleagent.legacy;

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
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import com.openjiuwen.core.foundation.llm.schema.VideoGenerationResponse;
import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.foundation.tool.function.LocalFunction;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.singleagent.legacy.config.LegacyReActAgentConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for ReActAgent using legacy API (deprecated).
 *
 * <p>Mirrors Python's
 * {@code tests/unit_tests/agent/react_agent/test_react_agent_mock.py}.
 */
@DisplayName("Legacy ReActAgent Mock")
class LegacyReActAgentMockTest {

    private static final String MOCK_PROVIDER = "MockLegacyReActOpenAI";
    private static final ArrayDeque<AssistantMessage> MOCK_RESPONSES = new ArrayDeque<>();
    private static final AtomicInteger CALL_COUNT = new AtomicInteger();

    @BeforeEach
    void setUp() {
        registerMockModelFactory();
        resetMockModel();
        Runner.start();
    }

    @AfterEach
    void tearDown() {
        resetMockModel();
        Runner.stop();
    }

    @Test
    @DisplayName("test_react_agent_invoke_with_mock_llm")
    @SuppressWarnings("unchecked")
    void testReactAgentInvokeWithMockLlm() {
        setMockResponses(
                toolCallResponse("add", "{\"a\": 1, \"b\": 2}"),
                textResponse("According to the calculation, 1+2=3")
        );
        LegacyReActAgent reactAgent = new LegacyReActAgent(createReactAgentConfig("react_agent_mock_test"));
        reactAgent.addTools(List.of(addTool()));

        Object result = reactAgent.invoke(
                Map.of("conversation_id", "test_session", "query", "calculate 1+2"),
                null
        );

        assertThat(result).isInstanceOf(Map.class);
        Map<String, Object> resultMap = (Map<String, Object>) result;
        assertThat(resultMap)
                .containsEntry("result_type", "answer")
                .containsKey("output");
        assertThat(String.valueOf(resultMap.get("output"))).contains("3");
        assertThat(CALL_COUNT.get()).isEqualTo(2);
    }

    @Test
    @DisplayName("test_react_agent_multi_turn_tool_calls")
    @SuppressWarnings("unchecked")
    void testReactAgentMultiTurnToolCalls() {
        setMockResponses(
                toolCallResponse("add", "{\"a\": 1, \"b\": 2}"),
                toolCallResponse("multiply", "{\"a\": 3, \"b\": 3}"),
                textResponse("Calculation result: (1+2) * 3 = 9")
        );
        LegacyReActAgent reactAgent = new LegacyReActAgent(createReactAgentConfig("react_agent_multi_turn"));
        reactAgent.addTools(List.of(addTool(), multiplyTool()));

        Object result = reactAgent.invoke(
                Map.of("conversation_id", "test_multi_turn", "query", "calculate (1+2) * 3"),
                null
        );

        assertThat(result).isInstanceOf(Map.class);
        Map<String, Object> resultMap = (Map<String, Object>) result;
        assertThat(resultMap).containsEntry("result_type", "answer");
        assertThat(String.valueOf(resultMap.get("output"))).contains("9");
        assertThat(CALL_COUNT.get()).isEqualTo(3);
    }

    @Test
    @DisplayName("test_react_agent_pure_conversation")
    @SuppressWarnings("unchecked")
    void testReactAgentPureConversation() {
        setMockResponses(textResponse("Hello! I am a math assistant."));
        LegacyReActAgent reactAgent = new LegacyReActAgent(createReactAgentConfig("react_agent_conversation"));
        reactAgent.addTools(List.of(addTool()));

        Object result = reactAgent.invoke(
                Map.of("conversation_id", "test_conversation", "query", "hello"),
                null
        );

        assertThat(result).isInstanceOf(Map.class);
        Map<String, Object> resultMap = (Map<String, Object>) result;
        assertThat(resultMap).containsEntry("result_type", "answer");
        assertThat(String.valueOf(resultMap.get("output"))).contains("Hello");
        assertThat(CALL_COUNT.get()).isEqualTo(1);
    }

    private static LegacyReActAgentConfig createReactAgentConfig(String agentId) {
        return LegacyApi.createReActAgentConfig(
                agentId,
                "0.0.1",
                "math helper",
                modelConfig(),
                List.of(Map.of("role", "system", "content", "You are a math assistant."))
        );
    }

    private static ModelConfig modelConfig() {
        return new ModelConfig(
                MOCK_PROVIDER,
                BaseModelInfo.builder()
                        .modelName("gpt-3.5-turbo")
                        .apiBase("https://mock.api")
                        .apiKey("mock_key")
                        .temperature(0.7)
                        .topP(0.9)
                        .timeout(30)
                        .build()
        );
    }

    private static Tool addTool() {
        return new LocalFunction(
                ToolCard.builder()
                        .id("add")
                        .name("add")
                        .description("addition")
                        .inputParams(numberToolSchema())
                        .build(),
                inputs -> ((Number) inputs.get("a")).intValue() + ((Number) inputs.get("b")).intValue()
        );
    }

    private static Tool multiplyTool() {
        return new LocalFunction(
                ToolCard.builder()
                        .id("multiply")
                        .name("multiply")
                        .description("multiplication")
                        .inputParams(numberToolSchema())
                        .build(),
                inputs -> ((Number) inputs.get("a")).intValue() * ((Number) inputs.get("b")).intValue()
        );
    }

    private static Map<String, Object> numberToolSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "a", Map.of("type", "number", "description", "first number"),
                        "b", Map.of("type", "number", "description", "second number")
                ),
                "required", List.of("a", "b")
        );
    }

    private static void setMockResponses(AssistantMessage... responses) {
        MOCK_RESPONSES.clear();
        MOCK_RESPONSES.addAll(Arrays.asList(responses));
    }

    private static void resetMockModel() {
        MOCK_RESPONSES.clear();
        CALL_COUNT.set(0);
    }

    private static AssistantMessage toolCallResponse(String toolName, String arguments) {
        return AssistantMessage.builder()
                .content("")
                .toolCalls(List.of(ToolCall.builder()
                        .id("call_" + toolName)
                        .type("function")
                        .name(toolName)
                        .arguments(arguments)
                        .build()))
                .finishReason("tool_calls")
                .build();
    }

    private static AssistantMessage textResponse(String content) {
        return AssistantMessage.builder()
                .content(content)
                .finishReason("stop")
                .build();
    }

    private static AssistantMessage pollResponse() {
        AssistantMessage response = MOCK_RESPONSES.pollFirst();
        return response != null ? response : textResponse("Task complete.");
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

    private static final class MockModelClient extends BaseModelClient {
        private MockModelClient(ModelRequestConfig modelConfig, ModelClientConfig modelClientConfig) {
            super(modelConfig, modelClientConfig);
        }

        @Override
        public AssistantMessage invoke(Object messages, Object tools, Float temperature, Float topP, String model,
                                       Integer maxTokens, String stop, BaseOutputParser outputParser, Float timeout,
                                       Map<String, Object> kwargs) {
            CALL_COUNT.incrementAndGet();
            return pollResponse();
        }

        @Override
        public Iterator<AssistantMessageChunk> stream(Object messages, Object tools, Float temperature, Float topP,
                                                      String model, Integer maxTokens, String stop,
                                                      BaseOutputParser outputParser, Float timeout,
                                                      Map<String, Object> kwargs) {
            AssistantMessage response = invoke(messages, tools, temperature, topP, model, maxTokens,
                    stop, outputParser, timeout, kwargs);
            AssistantMessageChunk chunk = AssistantMessageChunk.builder()
                    .content(response.getContent())
                    .toolCalls(response.getToolCalls())
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

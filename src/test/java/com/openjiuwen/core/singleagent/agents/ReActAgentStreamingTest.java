/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.core.singleagent.agents;

import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.model_clients.BaseModelClient;
import com.openjiuwen.core.foundation.llm.output_parsers.BaseOutputParser;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessageChunk;
import com.openjiuwen.core.foundation.llm.schema.AudioGenerationResponse;
import com.openjiuwen.core.foundation.llm.schema.ImageGenerationResponse;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import com.openjiuwen.core.foundation.llm.schema.VideoGenerationResponse;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.session.stream.OutputSchema;
import com.openjiuwen.core.session.stream.StreamMode;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for ReActAgent streaming (_railed_model_call streaming path).
 *
 * <p>Mirrors Python's
 * {@code tests/unit_tests/agent/react_agent/test_react_agent_streaming.py}.
 */
@DisplayName("ReActAgent Streaming")
class ReActAgentStreamingTest {

    private static final String MOCK_PROVIDER = "MockReActStreamingOpenAI";
    private static final ArrayDeque<AssistantMessage> MOCK_RESPONSES = new ArrayDeque<>();
    private static final AtomicInteger INVOKE_CALL_COUNT = new AtomicInteger();
    private static final AtomicInteger STREAM_CALL_COUNT = new AtomicInteger();

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
    @DisplayName("test_streaming_writes_llm_output_chunks_to_session")
    void testStreamingWritesLlmOutputChunksToSession() {
        setMockResponses(textResponse("Hello from streaming!"));

        ReActAgent agent = makeAgent("agent_stream_llm_output");
        AgentSessionApi session = AgentSessionApi.create(
                "sess_stream_001",
                null,
                AgentCard.builder().id("agent_stream_llm_output").build(),
                List.of(StreamMode.OUTPUT)
        );

        List<Object> chunks = collect(agent.stream(Map.of("query", "hi"), session, List.of(StreamMode.OUTPUT)));

        assertThat(STREAM_CALL_COUNT.get()).isEqualTo(1);
        assertThat(INVOKE_CALL_COUNT.get()).isZero();

        List<OutputSchema> llmOutputFrames = outputChunksOfType(chunks, "llm_output");
        assertThat(llmOutputFrames).isNotEmpty();
        String streamedText = llmOutputFrames.stream()
                .map(OutputSchema::getPayload)
                .filter(Map.class::isInstance)
                .map(Map.class::cast)
                .map(payload -> String.valueOf(payload.get("content") != null ? payload.get("content") : ""))
                .reduce("", String::concat);
        assertThat(streamedText).contains("Hello from streaming!");

        List<OutputSchema> terminalFrames = outputChunksOfType(chunks, "answer");
        OutputSchema terminal = terminalFrames.get(terminalFrames.size() - 1);
        assertThat(terminal.getPayload()).isInstanceOf(Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> terminalPayload = (Map<String, Object>) terminal.getPayload();
        assertThat(terminalPayload)
                .containsEntry("result_type", "answer")
                .containsEntry("output", "Hello from streaming!");
    }

    @Test
    @DisplayName("test_no_session_falls_back_to_invoke")
    @SuppressWarnings("unchecked")
    void testNoSessionFallsBackToInvoke() {
        setMockResponses(textResponse("Fallback answer."));

        ReActAgent agent = makeAgent("agent_no_session");
        Object result = agent.invoke(Map.of("query", "hello"), null);

        assertThat(result).isInstanceOf(Map.class);
        Map<String, Object> resultMap = (Map<String, Object>) result;
        assertThat(resultMap)
                .containsEntry("result_type", "answer")
                .containsEntry("output", "Fallback answer.");
        assertThat(STREAM_CALL_COUNT.get()).isZero();
        assertThat(INVOKE_CALL_COUNT.get()).isEqualTo(1);
    }

    private static ReActAgent makeAgent(String agentId) {
        AgentCard card = AgentCard.builder().id(agentId).build();
        ReActAgent agent = new ReActAgent(card);
        ReActAgentConfig config = ReActAgentConfig.builder()
                .modelClientConfig(modelClientConfig())
                .modelConfigObj(ModelRequestConfig.builder()
                        .modelName("gpt-3.5-turbo")
                        .build())
                .promptTemplate(List.of(Map.of("role", "system", "content", "You are a helpful assistant.")))
                .build();
        agent.configure(config);
        return agent;
    }

    private static void setMockResponses(AssistantMessage... responses) {
        MOCK_RESPONSES.clear();
        MOCK_RESPONSES.addAll(Arrays.asList(responses));
    }

    private static void resetMockModel() {
        MOCK_RESPONSES.clear();
        INVOKE_CALL_COUNT.set(0);
        STREAM_CALL_COUNT.set(0);
    }

    private static AssistantMessage textResponse(String content) {
        return AssistantMessage.builder()
                .content(content)
                .finishReason("stop")
                .build();
    }

    private static List<Object> collect(Iterator<Object> iterator) {
        List<Object> chunks = new ArrayList<>();
        while (iterator.hasNext()) {
            chunks.add(iterator.next());
        }
        return chunks;
    }

    private static List<OutputSchema> outputChunksOfType(List<Object> chunks, String type) {
        List<OutputSchema> frames = new ArrayList<>();
        for (Object chunk : chunks) {
            if (chunk instanceof OutputSchema schema && type.equals(schema.getType())) {
                frames.add(schema);
            }
        }
        return frames;
    }

    private static ModelClientConfig modelClientConfig() {
        return ModelClientConfig.builder()
                .clientProvider(MOCK_PROVIDER)
                .apiKey("sk-fake")
                .apiBase("https://mock.openai.local/v1")
                .verifySsl(false)
                .build();
    }

    private static AssistantMessage pollResponse() {
        AssistantMessage response = MOCK_RESPONSES.pollFirst();
        return response != null ? response : textResponse("default mock answer");
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
            INVOKE_CALL_COUNT.incrementAndGet();
            return pollResponse();
        }

        @Override
        public Iterator<AssistantMessageChunk> stream(Object messages, Object tools, Float temperature, Float topP,
                                                      String model, Integer maxTokens, String stop,
                                                      BaseOutputParser outputParser, Float timeout,
                                                      Map<String, Object> kwargs) {
            STREAM_CALL_COUNT.incrementAndGet();
            AssistantMessage response = pollResponse();
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

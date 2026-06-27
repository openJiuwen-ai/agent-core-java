/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.llm.model_clients;

import com.openjiuwen.core.foundation.llm.output_parsers.BaseOutputParser;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessageChunk;
import com.openjiuwen.core.foundation.llm.schema.AudioGenerationResponse;
import com.openjiuwen.core.foundation.llm.schema.ImageGenerationResponse;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelHttpVersion;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.foundation.llm.schema.ProviderType;
import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import com.openjiuwen.core.foundation.llm.schema.VideoGenerationResponse;
import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Focused parity tests for {@link SiliconFlowModelClient}.
 *
 * <p>Mirrors Python's {@code SiliconFlowModelClient} in
 * {@code openjiuwen/core/foundation/llm/model_clients/siliconflow_model_client.py}.</p>
 */
class SiliconFlowModelClientTest {

    @Test
    void buildAndSanitizeParamsCleansAssistantToolCalls() {
        RecordingSiliconFlowClient client = new RecordingSiliconFlowClient();
        Map<String, Object> rawFunction = new LinkedHashMap<>();
        rawFunction.put("name", "lookup");
        rawFunction.put("arguments", "{\"q\":\"java\"}");
        Map<String, Object> rawToolCall = new LinkedHashMap<>();
        rawToolCall.put("id", "call-1");
        rawToolCall.put("type", "legacy");
        rawToolCall.put("index", 3);
        rawToolCall.put("function", rawFunction);
        Map<String, Object> assistantMessage = new LinkedHashMap<>();
        assistantMessage.put("role", "assistant");
        assistantMessage.put("content", "tool result");
        assistantMessage.put("tool_calls", List.of(rawToolCall));

        Map<String, Object> params = client.buildAndSanitizeParams(
                List.of(assistantMessage),
                null,
                null,
                null,
                null,
                null,
                null,
                false,
                Map.of("custom", "value")
        );

        assertThat(params)
                .containsEntry("model", "test-model")
                .containsEntry("stream", false)
                .containsEntry("custom", "value");
        List<?> messages = (List<?>) params.get("messages");
        Map<?, ?> message = (Map<?, ?>) messages.get(0);
        List<?> toolCalls = (List<?>) message.get("tool_calls");
        Map<?, ?> toolCall = (Map<?, ?>) toolCalls.get(0);
        Map<?, ?> function = (Map<?, ?>) toolCall.get("function");
        assertThat(toolCall.get("id")).isEqualTo("call-1");
        assertThat(toolCall.get("type")).isEqualTo("function");
        assertThat(toolCall.get("index")).isEqualTo(3);
        assertThat(function.get("name")).isEqualTo("lookup");
        assertThat(function.get("arguments")).isEqualTo("{\"q\":\"java\"}");
    }

    @Test
    void resolveApiUrlAppendsChatCompletionsOnlyWhenNeeded() {
        RecordingSiliconFlowClient baseClient = new RecordingSiliconFlowClient(
                "https://api.siliconflow.cn/v1/");
        RecordingSiliconFlowClient completionClient = new RecordingSiliconFlowClient(
                "https://api.siliconflow.cn/v1/chat/completions/");

        assertThat(baseClient.resolveApiUrl())
                .isEqualTo("https://api.siliconflow.cn/v1/chat/completions");
        assertThat(completionClient.resolveApiUrl())
                .isEqualTo("https://api.siliconflow.cn/v1/chat/completions");
    }

    @Test
    void invokePostsCompletionAndParsesUsageToolsParserAndTracer() throws Exception {
        RecordingSiliconFlowClient client = new RecordingSiliconFlowClient();
        client.nextJson = """
                {
                  "choices": [
                    {
                      "message": {
                        "content": "answer",
                        "reasoning_content": "because",
                        "tool_calls": [
                          {
                            "id": "call-1",
                            "index": 2,
                            "function": {
                              "name": "lookup",
                              "arguments": "{\\"q\\":\\"java\\"}"
                            }
                          }
                        ]
                      }
                    }
                  ],
                  "usage": {
                    "prompt_tokens": 11,
                    "completion_tokens": 7,
                    "total_tokens": 18,
                    "prompt_tokens_details": {
                      "cached_tokens": 5
                    },
                    "cost": {
                      "input_cost": 0.1,
                      "completion_cost": 0.2
                    }
                  }
                }
                """;
        RecordingTracer tracer = new RecordingTracer();
        Map<String, Object> kwargs = new LinkedHashMap<>();
        kwargs.put("tracer_record_data", tracer);
        kwargs.put("request_id", "r1");

        AssistantMessage result = client.invoke(
                "hello",
                null,
                null,
                null,
                null,
                null,
                null,
                new PrefixParser("parsed:"),
                12.0F,
                kwargs
        );

        assertThat(client.lastTimeout).isEqualTo(12.0F);
        assertThat(client.lastPayload)
                .containsEntry("stream", false)
                .containsEntry("request_id", "r1");
        assertThat(client.lastPayload).doesNotContainKey("tracer_record_data");
        assertThat(result.getContent()).isEqualTo("answer");
        assertThat(result.getReasoningContent()).isEqualTo("because");
        assertThat(result.getParserContent()).isEqualTo("parsed:answer");
        assertThat(result.getUsageMetadata().getInputTokens()).isEqualTo(11);
        assertThat(result.getUsageMetadata().getCacheTokens()).isEqualTo(5);
        assertThat(result.getUsageMetadata().getInputCost()).isEqualTo(0.1D);
        assertThat(result.getUsageMetadata().getOutputCost()).isEqualTo(0.2D);
        assertThat(result.getUsageMetadata().getTotalCost()).isEqualTo(0.30000000000000004D);
        assertThat(result.getToolCalls()).singleElement().satisfies(toolCall -> {
            assertThat(toolCall.getId()).isEqualTo("call-1");
            assertThat(toolCall.getType()).isEqualTo("function");
            assertThat(toolCall.getName()).isEqualTo("lookup");
            assertThat(toolCall.getIndex()).isEqualTo(2);
        });
        assertThat(result.getFinishReason()).isEqualTo("tool_calls");
        assertThat(tracer.records).hasSize(2);
        assertThat(tracer.records.get(0)).containsKey("llm_params");
        assertThat(tracer.records.get(1)).containsEntry("llm_response", result);
    }

    @Test
    void streamParsesSseChunksAppliesParserAndRecordsFinalMessage() throws Exception {
        RecordingSiliconFlowClient client = new RecordingSiliconFlowClient();
        client.nextStreamLines = List.of(
                "data: {\"choices\":[{\"delta\":{\"content\":\"Hello\"}}]}",
                "data: {\"choices\":[{\"delta\":{\"content\":\" world\"},\"finish_reason\":\"stop\"}],"
                        + "\"usage\":{\"prompt_tokens\":1,\"completion_tokens\":2,\"total_tokens\":3}}",
                "data: [DONE]"
        );
        RecordingTracer tracer = new RecordingTracer();

        Iterator<AssistantMessageChunk> iterator = client.stream(
                "hello",
                null,
                null,
                null,
                "override-model",
                32,
                null,
                new PrefixParser("parsed:"),
                null,
                Map.of("tracer_record_data", tracer)
        );
        List<AssistantMessageChunk> chunks = iteratorToList(iterator);

        assertThat(client.lastPayload)
                .containsEntry("model", "override-model")
                .containsEntry("max_tokens", 32)
                .containsEntry("stream", true);
        assertThat(chunks).hasSize(2);
        assertThat(chunks.get(0).getContent()).isEqualTo("Hello");
        assertThat(chunks.get(0).getParserContent()).isEqualTo("parsed:Hello");
        assertThat(chunks.get(1).getContent()).isEqualTo(" world");
        assertThat(chunks.get(1).getFinishReason()).isEqualTo("stop");
        assertThat(chunks.get(1).getUsageMetadata().getTotalTokens()).isEqualTo(3);
        assertThat(tracer.records).hasSize(2);
        AssistantMessageChunk finalMessage = (AssistantMessageChunk) tracer.records.get(1).get("llm_response");
        assertThat(finalMessage.getContent()).isEqualTo("Hello world");
    }

    @Test
    void generationMethodsMirrorPythonPassByReturningNull() {
        RecordingSiliconFlowClient client = new RecordingSiliconFlowClient();

        ImageGenerationResponse image = client.generateImage(List.of(new UserMessage("image")), null, "1664*928",
                null, 1, true, false, 0, Map.of());
        AudioGenerationResponse audio = client.generateSpeech(List.of(new UserMessage("speech")), null, "Cherry",
                "Auto", Map.of());
        VideoGenerationResponse video = client.generateVideo(List.of(new UserMessage("video")), null, null, null,
                null, null, 5, true, false, null, null, Map.of());

        assertThat(image).isNull();
        assertThat(audio).isNull();
        assertThat(video).isNull();
    }

    @Test
    void modelClientsFacadeInstantiatesSiliconFlowClient() {
        Object client = ModelClients.createModelClient(clientConfig("https://api.siliconflow.cn/v1"), requestConfig());

        assertThat(client).isInstanceOf(SiliconFlowModelClient.class);
    }

    @Test
    void appliesConfiguredHttpVersionToHttpClient() {
        SiliconFlowModelClient client = new SiliconFlowModelClient(
                requestConfig(),
                ModelClientConfig.builder()
                        .clientProvider(ProviderType.SILICON_FLOW)
                        .apiKey("sk-test")
                        .apiBase("https://api.siliconflow.cn/v1")
                        .verifySsl(false)
                        .httpVersion(ModelHttpVersion.HTTP_2)
                        .build());

        assertThat(client.createHttpClient("https://api.siliconflow.cn/v1/chat/completions").version())
                .isEqualTo(HttpClient.Version.HTTP_2);
    }

    private static List<AssistantMessageChunk> iteratorToList(Iterator<AssistantMessageChunk> iterator) {
        List<AssistantMessageChunk> result = new ArrayList<>();
        while (iterator.hasNext()) {
            result.add(iterator.next());
        }
        return result;
    }

    private static ModelRequestConfig requestConfig() {
        return ModelRequestConfig.builder()
                .modelName("test-model")
                .build();
    }

    private static ModelClientConfig clientConfig(String apiBase) {
        return ModelClientConfig.builder()
                .clientProvider(ProviderType.SILICON_FLOW)
                .apiKey("sk-test")
                .apiBase(apiBase)
                .verifySsl(false)
                .build();
    }

    /**
     * Test double exposing the HTTP request boundary.
     *
     * <p>Mirrors Python's patched {@code aiohttp.ClientSession.post} collaborator in
     * {@code openjiuwen/core/foundation/llm/model_clients/siliconflow_model_client.py}.</p>
     */
    private static final class RecordingSiliconFlowClient extends SiliconFlowModelClient {
        private Map<String, Object> lastPayload;
        private Float lastTimeout;
        private int nextStatus = 200;
        private String nextJson = "{}";
        private List<String> nextStreamLines = List.of();

        private RecordingSiliconFlowClient() {
            this("https://api.siliconflow.cn/v1");
        }

        private RecordingSiliconFlowClient(String apiBase) {
            super(requestConfig(), clientConfig(apiBase));
        }

        @Override
        protected HttpResult postJson(Map<String, Object> payload, Float timeout) {
            this.lastPayload = new LinkedHashMap<>(payload);
            this.lastTimeout = timeout;
            return new HttpResult(nextStatus, nextJson);
        }

        @Override
        protected HttpStreamResult postStream(Map<String, Object> payload, Float timeout) {
            this.lastPayload = new LinkedHashMap<>(payload);
            this.lastTimeout = timeout;
            return new HttpStreamResult(nextStatus, nextStreamLines, String.join("\n", nextStreamLines));
        }
    }

    /**
     * Deterministic output parser for invoke and stream tests.
     *
     * <p>Mirrors Python's {@code BaseOutputParser.parse(...)} collaborator in
     * {@code openjiuwen/core/foundation/llm/model_clients/siliconflow_model_client.py}.</p>
     */
    private static final class PrefixParser extends BaseOutputParser {
        private final String prefix;

        private PrefixParser(String prefix) {
            this.prefix = prefix;
        }

        @Override
        public CompletableFuture<Object> parse(Object inputs) {
            return CompletableFuture.completedFuture(prefix + inputs);
        }

        @Override
        public Iterator<Object> streamParse(Iterator<?> streamingInputs) {
            return List.of().iterator();
        }
    }

    /**
     * Recording tracer for the Python {@code tracer_record_data} callback.
     *
     * <p>Mirrors Python's callback payloads in
     * {@code openjiuwen/core/foundation/llm/model_clients/siliconflow_model_client.py}.</p>
     */
    private static final class RecordingTracer implements Consumer<Map<String, Object>> {
        private final List<Map<String, Object>> records = new ArrayList<>();

        @Override
        public void accept(Map<String, Object> payload) {
            records.add(payload);
        }
    }
}

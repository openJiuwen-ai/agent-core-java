/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.llm.model_clients;

import com.openjiuwen.core.foundation.llm.output_parsers.BaseOutputParser;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessageChunk;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.foundation.llm.schema.ProviderType;
import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.foundation.tool.schema.ToolInfo;
import org.junit.jupiter.api.Test;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Focused parity tests for {@link InferenceAffinityModelClient}.
 *
 * <p>Mirrors Python's {@code InferenceAffinityModelClient} in
 * {@code openjiuwen/core/foundation/llm/model_clients/inference_affinity_model_client.py}.</p>
 */
class InferenceAffinityModelClientTest {

    @Test
    void buildAndSanitizeParamsAddsCacheSharingAndCleansToolCalls() {
        RecordingInferenceAffinityClient client = new RecordingInferenceAffinityClient();
        AssistantMessage assistantMessage = AssistantMessage.builder()
                .content("tool result")
                .toolCalls(List.of(ToolCall.builder()
                        .id("call-1")
                        .type("legacy")
                        .name("lookup")
                        .arguments("{\"q\":\"java\"}")
                        .index(3)
                        .build()))
                .build();

        Map<String, Object> params = client.buildAndSanitizeParams(
                List.of(assistantMessage),
                null,
                null,
                null,
                null,
                null,
                null,
                false,
                "session-1",
                true,
                Map.of("custom", "value")
        );

        assertThat(params)
                .containsEntry("model", "test-model")
                .containsEntry("cache_sharing", true)
                .containsEntry("cache_salt", "session-1")
                .containsEntry("custom", "value");

        List<?> messages = (List<?>) params.get("messages");
        Map<?, ?> message = (Map<?, ?>) messages.get(0);
        List<?> toolCalls = (List<?>) message.get("tool_calls");
        Map<?, ?> toolCall = (Map<?, ?>) toolCalls.get(0);
        Map<?, ?> function = (Map<?, ?>) toolCall.get("function");
        assertThat(toolCall.get("id")).isEqualTo("call-1");
        assertThat(toolCall.get("type")).isEqualTo("function");
        assertThat(toolCall.containsKey("index")).isTrue();
        assertThat(function.get("name")).isEqualTo("lookup");
        assertThat(function.get("arguments")).isEqualTo("{\"q\":\"java\"}");
    }

    @Test
    void invokePostsChatCompletionAndParsesUsageToolsAndParserContent() throws Exception {
        RecordingInferenceAffinityClient client = new RecordingInferenceAffinityClient();
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
                    }
                  }
                }
                """;
        Map<String, Object> kwargs = new LinkedHashMap<>();
        kwargs.put("session_id", "session-1");
        kwargs.put("enable_cache_sharing", true);
        kwargs.put("tracer_record_data", "not-sent");

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

        assertThat(client.lastPath).isEqualTo("/v1/chat/completions");
        assertThat(client.lastTimeout).isEqualTo(12.0F);
        assertThat(client.lastPayload)
                .containsEntry("stream", false)
                .containsEntry("cache_sharing", true)
                .containsEntry("cache_salt", "session-1");
        assertThat(client.lastPayload).doesNotContainKeys("session_id", "enable_cache_sharing", "tracer_record_data");
        assertThat(result.getContent()).isEqualTo("answer");
        assertThat(result.getReasoningContent()).isEqualTo("because");
        assertThat(result.getParserContent()).isEqualTo("parsed:answer");
        assertThat(result.getUsageMetadata().getInputTokens()).isEqualTo(11);
        assertThat(result.getUsageMetadata().getCacheTokens()).isEqualTo(5);
        assertThat(result.getToolCalls()).singleElement().satisfies(toolCall -> {
            assertThat(toolCall.getId()).isEqualTo("call-1");
            assertThat(toolCall.getType()).isEqualTo("function");
            assertThat(toolCall.getName()).isEqualTo("lookup");
            assertThat(toolCall.getIndex()).isEqualTo(2);
        });
        assertThat(result.getFinishReason()).isEqualTo("tool_calls");
    }

    @Test
    void streamParsesSseChunksAndAppliesIncrementalParser() throws Exception {
        RecordingInferenceAffinityClient client = new RecordingInferenceAffinityClient();
        client.nextStreamLines = List.of(
                "data: {\"choices\":[{\"delta\":{\"content\":\"Hello\"}}]}",
                "data: {\"choices\":[{\"delta\":{\"content\":\" world\"},\"finish_reason\":\"stop\"}],"
                        + "\"usage\":{\"prompt_tokens\":1,\"completion_tokens\":2,\"total_tokens\":3}}",
                "data: [DONE]"
        );

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
                Map.of()
        );
        List<AssistantMessageChunk> chunks = iteratorToList(iterator);

        assertThat(client.lastPath).isEqualTo("/v1/chat/completions");
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
    }

    @Test
    void releasePostsKvCachePayloadAndReturnsFalseForNonSuccessStatus() throws Exception {
        RecordingInferenceAffinityClient client = new RecordingInferenceAffinityClient();
        client.nextJson = "{\"ok\":true}";
        AssistantMessage assistantMessage = AssistantMessage.builder()
                .content("answer")
                .toolCalls(List.of(ToolCall.builder()
                        .id("call-1")
                        .type("legacy")
                        .name("lookup")
                        .arguments("{}")
                        .build()))
                .build();
        ToolInfo toolInfo = ToolInfo.builder()
                .name("lookup")
                .description("Lookup")
                .parameters(Map.of("type", "object"))
                .build();

        Boolean released = client.release("session-1", List.of(assistantMessage), 1, List.of(toolInfo), 0, null);

        assertThat(released).isTrue();
        assertThat(client.lastPath).isEqualTo("/release_kv_cache");
        assertThat(client.lastPayload)
                .containsEntry("model", "test-model")
                .containsEntry("cache_salt", "session-1")
                .containsEntry("cache_sharing", true)
                .containsEntry("messages_released_index", 1)
                .containsEntry("tools_released_index", 0);
        List<?> messages = (List<?>) client.lastPayload.get("messages");
        Map<?, ?> firstMessage = (Map<?, ?>) messages.get(0);
        Map<?, ?> firstToolCall = (Map<?, ?>) ((List<?>) firstMessage.get("tool_calls")).get(0);
        assertThat(firstToolCall.get("type")).isEqualTo("function");
        assertThat((List<?>) client.lastPayload.get("tools")).hasSize(1);

        client.nextStatus = 500;
        client.nextJson = "error";
        assertThat(client.release("session-1", List.of(assistantMessage), 1, null, null, null)).isFalse();
    }

    private static List<AssistantMessageChunk> iteratorToList(Iterator<AssistantMessageChunk> iterator) {
        List<AssistantMessageChunk> result = new java.util.ArrayList<>();
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

    private static ModelClientConfig clientConfig() {
        return ModelClientConfig.builder()
                .clientProvider(ProviderType.INFERENCE_AFFINITY)
                .apiKey("sk-test")
                .apiBase("http://localhost:8000")
                .verifySsl(false)
                .build();
    }

    /**
     * Test double exposing the HTTP seam for deterministic focused tests.
     *
     * <p>Mirrors Python's patched aiohttp session in
     * {@code openjiuwen/core/foundation/llm/model_clients/inference_affinity_model_client.py}.</p>
     */
    private static final class RecordingInferenceAffinityClient extends InferenceAffinityModelClient {
        private String lastPath;
        private Map<String, Object> lastPayload;
        private Float lastTimeout;
        private int nextStatus = 200;
        private String nextJson = "{}";
        private List<String> nextStreamLines = List.of();

        private RecordingInferenceAffinityClient() {
            super(requestConfig(), clientConfig());
        }

        @Override
        protected HttpResult postJson(String path, Map<String, Object> payload, Float timeout) {
            this.lastPath = path;
            this.lastPayload = new LinkedHashMap<>(payload);
            this.lastTimeout = timeout;
            return new HttpResult(nextStatus, nextJson);
        }

        @Override
        protected HttpStreamResult postStream(String path, Map<String, Object> payload, Float timeout) {
            this.lastPath = path;
            this.lastPayload = new LinkedHashMap<>(payload);
            this.lastTimeout = timeout;
            return new HttpStreamResult(nextStatus, nextStreamLines, String.join("\n", nextStreamLines));
        }

        @Override
        protected void sleepBeforeRetry(int attempt) {
        }
    }

    /**
     * Deterministic output parser for invoke and stream tests.
     *
     * <p>Mirrors Python's {@code BaseOutputParser.parse(...)} collaborator in
     * {@code openjiuwen/core/foundation/llm/model_clients/inference_affinity_model_client.py}.</p>
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
}

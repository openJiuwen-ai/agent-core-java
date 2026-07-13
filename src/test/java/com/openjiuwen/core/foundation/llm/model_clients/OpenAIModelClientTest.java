/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.llm.model_clients;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.foundation.llm.ModelInvokeOptions;
import com.openjiuwen.core.foundation.llm.ModelRetryEvent;
import com.openjiuwen.core.foundation.llm.output_parsers.BaseOutputParser;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessageChunk;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelHttpVersion;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.foundation.llm.schema.ProviderType;
import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Focused parity tests for {@link OpenAIModelClient}.
 *
 * <p>Mirrors Python's {@code OpenAIModelClient} in
 * {@code openjiuwen/core/foundation/llm/model_clients/openai_model_client.py}.</p>
 */
class OpenAIModelClientTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void buildRequestParamsDropsTopPOnlyForOpenAiApiBase() {
        OpenAIModelClient openAiClient = client("https://api.openai.com/v1");
        OpenAIModelClient compatibleClient = client("https://compatible.example.test/v1");

        Map<String, Object> openAiParams = openAiClient.buildPreparedParams(
                "hello",
                null,
                null,
                null,
                null,
                null,
                null,
                false,
                null);
        Map<String, Object> compatibleParams = compatibleClient.buildPreparedParams(
                "hello",
                null,
                null,
                null,
                null,
                null,
                null,
                false,
                null);

        assertThat(openAiParams).containsEntry("temperature", 0.95D);
        assertThat(openAiParams).doesNotContainKey("top_p");
        assertThat(compatibleParams).containsEntry("temperature", 0.95D)
                .containsEntry("top_p", 0.1D);
    }

    @Test
    void invokeMergesHeadersMovesReturnTokenIdsAndParsesResponse() throws Exception {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("prompt_token_ids", List.of(10, 11));
        response.put("usage", Map.of(
                "prompt_tokens", 3,
                "completion_tokens", 4,
                "total_tokens", 7,
                "prompt_tokens_details", Map.of("cached_tokens", 2),
                "usage_cost", Map.of("input_cost", 0.1D, "output_cost", 0.2D, "total_cost", 0.3D)));
        response.put("choices", List.of(Map.of(
                "token_ids", List.of(20, 21),
                "logprobs", Map.of("content", List.of("lp")),
                "message", Map.of(
                        "content", "answer",
                        "reasoning_content", "because",
                        "tool_calls", List.of(Map.of(
                                "id", "call-1",
                                "function", Map.of("name", "lookup", "arguments", "{\"q\":1}")))))));

        try (MockOpenAiServer server = new MockOpenAiServer(json(response))) {
            OpenAIModelClient client = client(server.baseUrl());
            Map<String, Object> extraBody = new LinkedHashMap<>();
            extraBody.put("guided_choice", "A");
            Map<String, Object> kwargs = new LinkedHashMap<>();
            kwargs.put("return_token_ids", true);
            kwargs.put("extra_body", extraBody);
            kwargs.put("custom_headers", Map.of("X-Trace", "request", "Authorization", "Custom token-1"));

            AssistantMessage message = client.invoke(
                    "hello",
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    new PrefixParser(),
                    null,
                    kwargs);

            assertThat(header(server.lastHeaders, "Authorization")).isEqualTo("Custom token-1");
            assertThat(header(server.lastHeaders, "X-Base")).isEqualTo("base");
            assertThat(header(server.lastHeaders, "X-Trace")).isEqualTo("request");
            assertThat(server.lastBody).containsEntry("return_token_ids", true)
                    .containsEntry("guided_choice", "A");
            assertThat(server.lastBody).doesNotContainKey("extra_body");
            assertThat(message.getContent()).isEqualTo("answer");
            assertThat(message.getReasoningContent()).isEqualTo("because");
            assertThat(message.getParserContent()).isEqualTo("parsed:answer");
            assertThat(message.getPromptTokenIds()).containsExactly(10, 11);
            assertThat(message.getCompletionTokenIds()).containsExactly(20, 21);
            assertThat(message.getLogprobs()).isEqualTo(Map.of("content", List.of("lp")));
            assertThat(message.getUsageMetadata().getCacheTokens()).isEqualTo(2);
            assertThat(message.getUsageMetadata().getTotalCost()).isEqualTo(0.3D);
            ToolCall toolCall = message.getToolCalls().get(0);
            assertThat(toolCall.getId()).isEqualTo("call-1");
            assertThat(toolCall.getName()).isEqualTo("lookup");
            assertThat(toolCall.getIndex()).isEqualTo(0);
        }
    }

    @Test
    void invokeRetriesServerErrorWithEquivalentRebuiltRequest() throws Exception {
        String success = json(Map.of("choices", List.of(Map.of("message", Map.of("content", "ok")))));
        try (MockOpenAiServer server = new MockOpenAiServer(
                response(500, "{\"error\":\"retry\"}"),
                response(200, success))) {
            OpenAIModelClient client = client(server.baseUrl(), 1, Map.of("X-Base", "base"));
            Map<String, Object> kwargs = new LinkedHashMap<>();
            kwargs.put("extra_body", Map.of("guided_choice", "A"));
            kwargs.put("custom_headers", Map.of("X-Trace", "trace-1"));

            AssistantMessage message = client.invoke(
                    "hello", null, null, null, null, null, null, null, null, kwargs);

            assertThat(message.getContent()).isEqualTo("ok");
            assertThat(server.requests).hasSize(2);
            assertThat(server.requests).extracting(request -> request.uri).containsOnly("/chat/completions");
            assertThat(server.requests.get(0).body).isEqualTo(server.requests.get(1).body)
                    .containsEntry("guided_choice", "A");
            assertThat(server.requests).allSatisfy(request -> {
                assertThat(header(request.headers, "Authorization")).isEqualTo("Bearer sk-test");
                assertThat(header(request.headers, "X-Base")).isEqualTo("base");
                assertThat(header(request.headers, "X-Trace")).isEqualTo("trace-1");
            });
            assertThat(header(server.requests.get(0).headers, "X-Stainless-Retry-Count")).isEqualTo("0");
            assertThat(header(server.requests.get(1).headers, "X-Stainless-Retry-Count")).isEqualTo("1");
        }
    }

    @Test
    void streamRetriesRateLimitBeforeReturningSseIterator() throws Exception {
        String sse = "data: " + json(Map.of("choices", List.of(Map.of(
                "delta", Map.of("content", "ok"), "finish_reason", "stop")))) + "\n\ndata: [DONE]\n\n";
        try (MockOpenAiServer server = new MockOpenAiServer(
                response(429, "{\"error\":\"retry\"}"),
                response(200, sse, "text/event-stream"))) {
            List<AssistantMessageChunk> chunks = iteratorToList(client(server.baseUrl(), 1, Map.of()).stream(
                    "hello", null, null, null, null, null, null, null, null, new LinkedHashMap<>()));

            assertThat(chunks).singleElement().extracting(AssistantMessageChunk::getContent).isEqualTo("ok");
            assertThat(server.requests).hasSize(2);
            assertThat(header(server.requests.get(0).headers, "X-Stainless-Retry-Count")).isEqualTo("0");
            assertThat(header(server.requests.get(1).headers, "X-Stainless-Retry-Count")).isEqualTo("1");
        }
    }

    @Test
    void invokeOptionListenerReceivesRetryWithoutPollutingRequestBody() throws Exception {
        String success = json(Map.of("choices", List.of(Map.of("message", Map.of("content", "ok")))));
        try (MockOpenAiServer server = new MockOpenAiServer(
                response(500, "{\"error\":\"retry\"}"), response(200, success))) {
            List<ModelRetryEvent> events = new ArrayList<>();
            ModelInvokeOptions options = ModelInvokeOptions.builder()
                    .retryListener(events::add)
                    .extraFields(new LinkedHashMap<>(Map.of("request_tag", "visible")))
                    .build();

            AssistantMessage message = client(server.baseUrl(), 1, Map.of())
                    .invoke(List.of(new UserMessage("hello")), options)
                    .toCompletableFuture()
                    .get(5, TimeUnit.SECONDS);

            assertThat(message.getContent()).isEqualTo("ok");
            assertThat(events).singleElement().satisfies(event -> {
                assertThat(event.retryCount()).isEqualTo(1);
                assertThat(event.statusCode()).isEqualTo(500);
            });
            assertThat(server.requests).hasSize(2).allSatisfy(request -> assertThat(request.body)
                    .containsEntry("request_tag", "visible")
                    .doesNotContainKeys("__openjiuwen_retry_listener", "retry_listener", "retryListener"));
        }
    }

    @Test
    void streamOptionListenerReceivesRetryWithoutPollutingRequestBody() throws Exception {
        String sse = "data: " + json(Map.of("choices", List.of(Map.of(
                "delta", Map.of("content", "ok"), "finish_reason", "stop")))) + "\n\ndata: [DONE]\n\n";
        try (MockOpenAiServer server = new MockOpenAiServer(
                response(429, "{\"error\":\"retry\"}"), response(200, sse, "text/event-stream"))) {
            List<ModelRetryEvent> events = new ArrayList<>();
            ModelInvokeOptions options = ModelInvokeOptions.builder().retryListener(events::add).build();

            List<AssistantMessageChunk> chunks = iteratorToList(client(server.baseUrl(), 1, Map.of())
                    .stream(List.of(new UserMessage("hello")), options));

            assertThat(chunks).singleElement().extracting(AssistantMessageChunk::getContent).isEqualTo("ok");
            assertThat(events).singleElement().satisfies(event -> {
                assertThat(event.retryCount()).isEqualTo(1);
                assertThat(event.statusCode()).isEqualTo(429);
            });
            assertThat(server.requests).hasSize(2).allSatisfy(request -> assertThat(request.body)
                    .doesNotContainKeys("__openjiuwen_retry_listener", "retry_listener", "retryListener"));
        }
    }

    @Test
    void typedInvokeWithoutListenerKeepsExistingBehavior() throws Exception {
        String success = json(Map.of("choices", List.of(Map.of("message", Map.of("content", "ok")))));
        try (MockOpenAiServer server = new MockOpenAiServer(success)) {
            AssistantMessage message = client(server.baseUrl())
                    .invoke(List.of(new UserMessage("hello")), ModelInvokeOptions.builder().build())
                    .toCompletableFuture()
                    .get(5, TimeUnit.SECONDS);

            assertThat(message.getContent()).isEqualTo("ok");
            assertThat(server.requests).singleElement().satisfies(request -> assertThat(request.body)
                    .doesNotContainKeys("__openjiuwen_retry_listener", "retry_listener", "retryListener"));
        }
    }

    @Test
    void maxRetriesZeroKeepsFinalNonSuccessExceptionMapping() throws Exception {
        String success = json(Map.of("choices", List.of(Map.of("message", Map.of("content", "unused")))));
        try (MockOpenAiServer server = new MockOpenAiServer(
                response(500, "{\"error\":\"final\"}"), response(200, success))) {
            OpenAIModelClient client = client(server.baseUrl(), 0, Map.of());

            assertThatThrownBy(() -> client.invoke(
                    "hello", null, null, null, null, null, null, null, null, new LinkedHashMap<>()))
                    .isInstanceOf(BaseError.class)
                    .satisfies(error -> assertThat(((BaseError) error).getStatus())
                            .isEqualTo(StatusCode.MODEL_CALL_FAILED))
                    .hasMessageContaining("HTTP 500")
                    .hasMessageContaining("final");
            assertThat(server.requests).hasSize(1);
        }
    }

    @Test
    void customRetryCountHeaderIsPreservedCaseInsensitively() throws Exception {
        String success = json(Map.of("choices", List.of(Map.of("message", Map.of("content", "ok")))));
        try (MockOpenAiServer server = new MockOpenAiServer(
                response(500, "{\"error\":\"retry\"}"), response(200, success))) {
            OpenAIModelClient client = client(server.baseUrl(), 1,
                    Map.of("x-sTaInLeSs-rEtRy-CoUnT", "caller-value"));

            client.invoke("hello", null, null, null, null, null, null, null, null, new LinkedHashMap<>());

            assertThat(server.requests).hasSize(2).allSatisfy(request ->
                    assertThat(header(request.headers, "X-Stainless-Retry-Count")).isEqualTo("caller-value"));
        }
    }

    @Test
    void successfulSseResponseIsNotReplayedAfterEarlyEof() throws Exception {
        String partialSse = "data: " + json(Map.of("choices", List.of(Map.of(
                "delta", Map.of("content", "partial"), "finish_reason", "null")))) + "\n\n";
        try (MockOpenAiServer server = new MockOpenAiServer(response(200, partialSse, "text/event-stream"))) {
            List<AssistantMessageChunk> chunks = iteratorToList(client(server.baseUrl(), 1, Map.of()).stream(
                    "hello", null, null, null, null, null, null, null, null, new LinkedHashMap<>()));

            assertThat(chunks).singleElement().extracting(AssistantMessageChunk::getContent).isEqualTo("partial");
            assertThat(server.requests).hasSize(1);
        }
    }

    @Test
    void localFixtureFallbackStaysInsideOneRetryAttempt() throws Exception {
        String success = json(Map.of("choices", List.of(Map.of("message", Map.of("content", "ok")))));
        try (MockOpenAiServer primary = new MockOpenAiServer(8088,
                response(500, "{\"error\":\"primary\"}"), response(500, "{\"error\":\"primary\"}"));
             MockOpenAiServer fallback = new MockOpenAiServer(8090,
                     response(500, "{\"error\":\"fallback\"}"), response(200, success))) {
            AssistantMessage message = client(primary.baseUrl(), 1, Map.of()).invoke(
                    "hello", null, null, null, null, null, null, null, null, new LinkedHashMap<>());

            assertThat(message.getContent()).isEqualTo("ok");
            assertThat(primary.requests).hasSize(2);
            assertThat(fallback.requests).hasSize(2);
            assertThat(header(fallback.requests.get(0).headers, "X-Stainless-Retry-Count")).isEqualTo("0");
            assertThat(header(fallback.requests.get(1).headers, "X-Stainless-Retry-Count")).isEqualTo("1");
        }
    }

    @Test
    void streamAddsIncludeUsageAndKeepsUsageOnlyTokenIdChunk() throws Exception {
        String streamBody = String.join("\n",
                "data: " + json(Map.of(
                        "prompt_token_ids", List.of(1, 2),
                        "choices", List.of(Map.of(
                                "delta", Map.of(
                                        "content", "hel",
                                        "token_ids", List.of(30),
                                        "reasoning_content", "r1"),
                                "logprobs", Map.of("a", 1),
                                "finish_reason", "null")))),
                "data: " + json(Map.of(
                        "choices", List.of(Map.of(
                                "delta", Map.of("content", "lo"),
                                "token_ids", List.of(31),
                                "finish_reason", "stop")))),
                "data: " + json(Map.of(
                        "usage", Map.of("prompt_tokens", 2, "completion_tokens", 2, "total_tokens", 4),
                        "choices", List.of())),
                "data: [DONE]");

        try (MockOpenAiServer server = new MockOpenAiServer(streamBody)) {
            OpenAIModelClient client = client(server.baseUrl());
            Iterator<AssistantMessageChunk> iterator = client.stream(
                    "hello",
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    new PrefixParser(),
                    null,
                    new LinkedHashMap<>());
            List<AssistantMessageChunk> chunks = iteratorToList(iterator);

            assertThat(server.lastBody).containsEntry("stream", true);
            assertThat(server.lastBody.get("stream_options"))
                    .isEqualTo(Map.of("include_usage", true));
            assertThat(chunks).hasSize(3);
            assertThat(chunks.get(0).getContent()).isEqualTo("hel");
            assertThat(chunks.get(0).getPromptTokenIds()).containsExactly(1, 2);
            assertThat(chunks.get(0).getCompletionTokenIds()).containsExactly(30);
            assertThat(chunks.get(0).getParserContent()).isEqualTo("parsed:hel");
            assertThat(chunks.get(1).getContent()).isEqualTo("lo");
            assertThat(chunks.get(2).getContent()).isEqualTo("");
            assertThat(chunks.get(2).getUsageMetadata().getTotalTokens()).isEqualTo(4);
        }
    }

    @Test
    void streamReturnsFirstChunkBeforeResponseCompletes() throws Exception {
        String firstChunk = "data: " + json(Map.of(
                "choices", List.of(Map.of(
                        "delta", Map.of("content", "first"),
                        "finish_reason", "null"))))
                + "\n\n";

        try (DelayedSseServer server = new DelayedSseServer(firstChunk)) {
            OpenAIModelClient client = client(server.baseUrl());
            CompletableFuture<AssistantMessageChunk> firstChunkFuture = CompletableFuture.supplyAsync(() -> {
                try {
                    Iterator<AssistantMessageChunk> iterator = client.stream(
                            "hello",
                            null,
                            null,
                            null,
                            null,
                            null,
                            null,
                            null,
                            null,
                            new LinkedHashMap<>());
                    return iterator.next();
                } catch (Exception exception) {
                    throw new RuntimeException(exception);
                }
            });

            try {
                assertTrue(server.awaitFirstChunk(Duration.ofSeconds(1)), "server did not send first SSE chunk");
                AssistantMessageChunk first = firstChunkFuture.get(1, TimeUnit.SECONDS);

                assertThat(first.getContent()).isEqualTo("first");
            } catch (TimeoutException exception) {
                throw new AssertionError("stream did not return first chunk before response completed", exception);
            } finally {
                server.completeResponse();
            }
        }
    }

    @Test
    void streamMergesSplitToolCallArgumentsByIndex() throws Exception {
        String streamBody = String.join("\n",
                "data: " + json(Map.of(
                        "choices", List.of(Map.of(
                                "delta", Map.of("tool_calls", List.of(Map.of(
                                        "index", 0,
                                        "id", "call-1",
                                        "type", "function",
                                        "function", Map.of(
                                                "name", "lookup",
                                                "arguments", "{\"q\"")))),
                                "finish_reason", "null")))),
                "data: " + json(Map.of(
                        "choices", List.of(Map.of(
                                "delta", Map.of("tool_calls", List.of(Map.of(
                                        "index", 0,
                                        "function", Map.of("arguments", ":\"java\"")))),
                                "finish_reason", "null")))),
                "data: " + json(Map.of(
                        "choices", List.of(Map.of(
                                "delta", Map.of("tool_calls", List.of(Map.of(
                                        "index", 0,
                                        "function", Map.of("arguments", "}")))),
                                "finish_reason", "tool_calls")))),
                "data: [DONE]");

        try (MockOpenAiServer server = new MockOpenAiServer(streamBody)) {
            OpenAIModelClient client = client(server.baseUrl());
            List<AssistantMessageChunk> chunks = iteratorToList(client.stream(
                    "hello",
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    new LinkedHashMap<>()));

            AssistantMessageChunk merged = chunks.get(0);
            for (int index = 1; index < chunks.size(); index++) {
                merged = merged.merge(chunks.get(index));
            }

            assertThat(merged.getToolCalls()).singleElement().satisfies(toolCall -> {
                assertThat(toolCall.getId()).isEqualTo("call-1");
                assertThat(toolCall.getType()).isEqualTo("function");
                assertThat(toolCall.getName()).isEqualTo("lookup");
                assertThat(toolCall.getArguments()).isEqualTo("{\"q\":\"java\"}");
                assertThat(toolCall.getIndex()).isZero();
            });
        }
    }

    @Test
    void streamMergesInterleavedParallelToolCallsByIndex() throws Exception {
        String streamBody = String.join("\n",
                "data: " + json(Map.of(
                        "choices", List.of(Map.of(
                                "delta", Map.of("tool_calls", List.of(
                                        Map.of(
                                                "index", 0,
                                                "id", "call-0",
                                                "type", "function",
                                                "function", Map.of(
                                                        "name", "lookupUser",
                                                        "arguments", "{\"user\"")),
                                        Map.of(
                                                "index", 1,
                                                "id", "call-1",
                                                "type", "function",
                                                "function", Map.of(
                                                        "name", "lookupOrder",
                                                        "arguments", "{\"order\"")))),
                                "finish_reason", "null")))),
                "data: " + json(Map.of(
                        "choices", List.of(Map.of(
                                "delta", Map.of("tool_calls", List.of(
                                        Map.of(
                                                "index", 0,
                                                "function", Map.of("arguments", ":\"alice\"}")),
                                        Map.of(
                                                "index", 1,
                                                "function", Map.of("arguments", ":\"A-1\"}")))),
                                "finish_reason", "tool_calls")))),
                "data: [DONE]");

        try (MockOpenAiServer server = new MockOpenAiServer(streamBody)) {
            OpenAIModelClient client = client(server.baseUrl());
            List<AssistantMessageChunk> chunks = iteratorToList(client.stream(
                    "hello",
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    new LinkedHashMap<>()));

            AssistantMessageChunk merged = mergeChunks(chunks);

            assertThat(merged.getToolCalls()).hasSize(2);
            assertThat(merged.getToolCalls().get(0)).satisfies(toolCall -> {
                assertThat(toolCall.getId()).isEqualTo("call-0");
                assertThat(toolCall.getType()).isEqualTo("function");
                assertThat(toolCall.getName()).isEqualTo("lookupUser");
                assertThat(toolCall.getArguments()).isEqualTo("{\"user\":\"alice\"}");
                assertThat(toolCall.getIndex()).isZero();
            });
            assertThat(merged.getToolCalls().get(1)).satisfies(toolCall -> {
                assertThat(toolCall.getId()).isEqualTo("call-1");
                assertThat(toolCall.getType()).isEqualTo("function");
                assertThat(toolCall.getName()).isEqualTo("lookupOrder");
                assertThat(toolCall.getArguments()).isEqualTo("{\"order\":\"A-1\"}");
                assertThat(toolCall.getIndex()).isEqualTo(1);
            });
        }
    }

    @Test
    void streamKeepsReasoningAndTokenOnlyChunks() throws Exception {
        String streamBody = String.join("\n",
                "data: " + json(Map.of(
                        "prompt_token_ids", List.of(101, 102),
                        "choices", List.of())),
                "data: " + json(Map.of(
                        "choices", List.of(Map.of(
                                "delta", Map.of("reasoning_content", "thinking"),
                                "finish_reason", "null")))),
                "data: " + json(Map.of(
                        "choices", List.of(Map.of(
                                "delta", Map.of("token_ids", List.of(201)),
                                "finish_reason", "null")))),
                "data: [DONE]");

        try (MockOpenAiServer server = new MockOpenAiServer(streamBody)) {
            OpenAIModelClient client = client(server.baseUrl());
            List<AssistantMessageChunk> chunks = iteratorToList(client.stream(
                    "hello",
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    new LinkedHashMap<>()));

            assertThat(chunks).hasSize(3);
            assertThat(chunks.get(0).getContent()).isEqualTo("");
            assertThat(chunks.get(0).getPromptTokenIds()).containsExactly(101, 102);
            assertThat(chunks.get(1).getContent()).isEqualTo("");
            assertThat(chunks.get(1).getReasoningContent()).isEqualTo("thinking");
            assertThat(chunks.get(2).getContent()).isEqualTo("");
            assertThat(chunks.get(2).getCompletionTokenIds()).containsExactly(201);
        }
    }

    @Test
    void streamRecordsTracerAfterTerminalChunkEvenWithoutFinalHasNext() throws Exception {
        String streamBody = String.join("\n",
                "data: " + json(Map.of(
                        "choices", List.of(Map.of(
                                "delta", Map.of("content", "done"),
                                "finish_reason", "stop")))),
                "data: [DONE]");

        try (MockOpenAiServer server = new MockOpenAiServer(streamBody)) {
            OpenAIModelClient client = client(server.baseUrl());
            RecordingTracer tracer = new RecordingTracer();
            Iterator<AssistantMessageChunk> iterator = client.stream(
                    "hello",
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    Map.of("tracer_record_data", tracer));

            AssistantMessageChunk terminal = iterator.next();

            assertThat(terminal.getContent()).isEqualTo("done");
            assertThat(terminal.getFinishReason()).isEqualTo("stop");
            ((AutoCloseable) iterator).close();
            assertThat(tracer.records).hasSize(2);
            AssistantMessageChunk traced = (AssistantMessageChunk) tracer.records.get(1).get("llm_response");
            assertThat(traced.getContent()).isEqualTo("done");
            assertThat(traced.getFinishReason()).isEqualTo("stop");
        }
    }

    @Test
    void streamRecordsSingleFinalTraceWithUsageAfterTerminalChunk() throws Exception {
        String streamBody = String.join("\n",
                "data: " + json(Map.of(
                        "choices", List.of(Map.of(
                                "delta", Map.of("content", "done"),
                                "finish_reason", "stop")))),
                "data: " + json(Map.of(
                        "usage", Map.of("prompt_tokens", 1, "completion_tokens", 1, "total_tokens", 2),
                        "choices", List.of())),
                "data: [DONE]");

        try (MockOpenAiServer server = new MockOpenAiServer(streamBody)) {
            OpenAIModelClient client = client(server.baseUrl());
            RecordingTracer tracer = new RecordingTracer();
            Iterator<AssistantMessageChunk> iterator = client.stream(
                    "hello",
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    Map.of("tracer_record_data", tracer));

            List<AssistantMessageChunk> chunks = iteratorToList(iterator);

            assertThat(chunks).hasSize(2);
            assertThat(tracer.records).hasSize(2);
            AssistantMessageChunk traced = (AssistantMessageChunk) tracer.records.get(1).get("llm_response");
            assertThat(traced.getContent()).isEqualTo("done");
            assertThat(traced.getUsageMetadata().getTotalTokens()).isEqualTo(2);
        }
    }

    @Test
    void streamIteratorWithoutTracerIsCloseableAndReleasesBlockedResponse() throws Exception {
        assertStreamIteratorClosesBlockedResponse(false);
    }

    @Test
    void streamIteratorWithTracerIsCloseableAndReleasesBlockedResponse() throws Exception {
        assertStreamIteratorClosesBlockedResponse(true);
    }

    @Test
    void modelClientsFacadeInstantiatesOpenAiProvider() {
        Object created = ModelClients.createModelClient(
                ModelClientConfig.builder()
                        .clientProvider(ProviderType.OPEN_AI)
                        .apiKey("sk-test")
                        .apiBase("https://compatible.example.test/v1")
                        .verifySsl(false)
                        .customHeaders(Map.of("X-Base", "base"))
                        .build(),
                ModelRequestConfig.builder().modelName("gpt-test").build());

        assertInstanceOf(OpenAIModelClient.class, created);
    }

    @Test
    void appliesConfiguredHttpVersionToHttpClient() {
        OpenAIModelClient client = new OpenAIModelClient(
                ModelRequestConfig.builder().modelName("gpt-test").build(),
                ModelClientConfig.builder()
                        .clientProvider(ProviderType.OPEN_AI)
                        .apiKey("sk-test")
                        .apiBase("https://compatible.example.test/v1")
                        .verifySsl(false)
                        .httpVersion(ModelHttpVersion.HTTP_1_1)
                        .build());

        assertThat(client.httpClientForTesting().version()).isEqualTo(HttpClient.Version.HTTP_1_1);
    }

    private static OpenAIModelClient client(String apiBase) {
        return client(apiBase, 3, Map.of("X-Base", "base"));
    }

    private static OpenAIModelClient client(String apiBase, int maxRetries, Map<String, Object> customHeaders) {
        return new OpenAIModelClient(
                ModelRequestConfig.builder().modelName("gpt-test").build(),
                ModelClientConfig.builder()
                        .clientProvider(ProviderType.OPEN_AI)
                        .apiKey("sk-test")
                        .apiBase(apiBase)
                        .verifySsl(false)
                        .maxRetries(maxRetries)
                        .customHeaders(customHeaders)
                        .build());
    }

    private static void assertStreamIteratorClosesBlockedResponse(boolean withTracer) throws Exception {
        String firstChunk = "data: " + json(Map.of(
                "choices", List.of(Map.of(
                        "delta", Map.of("content", "first"),
                        "finish_reason", "null"))))
                + "\n\n";
        try (BlockingSseServer server = new BlockingSseServer(firstChunk)) {
            OpenAIModelClient client = client(server.baseUrl());
            Map<String, Object> kwargs = new LinkedHashMap<>();
            if (withTracer) {
                kwargs.put("tracer_record_data", new RecordingTracer());
            }

            Iterator<AssistantMessageChunk> iterator = client.stream(
                    "hello",
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    kwargs);
            AssistantMessageChunk first = iterator.next();
            assertThat(first.getContent()).isEqualTo("first");
            assertThat(server.awaitFirstChunk(Duration.ofSeconds(1))).isTrue();
            assertThat(iterator).isInstanceOf(AutoCloseable.class);

            ((AutoCloseable) iterator).close();

            assertThat(server.awaitResponseClosed(Duration.ofSeconds(2))).isTrue();
        }
    }

    private static String json(Map<String, Object> payload) throws IOException {
        return OBJECT_MAPPER.writeValueAsString(payload);
    }

    private static List<AssistantMessageChunk> iteratorToList(Iterator<AssistantMessageChunk> iterator) {
        List<AssistantMessageChunk> chunks = new java.util.ArrayList<>();
        while (iterator.hasNext()) {
            chunks.add(iterator.next());
        }
        return chunks;
    }

    private static AssistantMessageChunk mergeChunks(List<AssistantMessageChunk> chunks) {
        AssistantMessageChunk merged = null;
        for (AssistantMessageChunk chunk : chunks) {
            merged = merged == null ? chunk : merged.merge(chunk);
        }
        return merged;
    }

    private static String header(Map<String, String> headers, String name) {
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(name)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private static PlannedResponse response(int status, String body) {
        return response(status, body, "application/json");
    }

    private static PlannedResponse response(int status, String body, String contentType) {
        return new PlannedResponse(status, body, contentType);
    }

    private static final class RecordingTracer implements Consumer<Map<String, Object>> {
        private final List<Map<String, Object>> records = new ArrayList<>();

        @Override
        public void accept(Map<String, Object> payload) {
            records.add(payload);
        }
    }

    /**
     * Mirrors Python's asynchronous output parser callback in
     * {@code openjiuwen/core/foundation/llm/model_clients/openai_model_client.py}.
     */
    private static final class PrefixParser extends BaseOutputParser {
        @Override
        public CompletableFuture<Object> parse(Object inputs) {
            return CompletableFuture.completedFuture("parsed:" + inputs);
        }

        @Override
        public Iterator<Object> streamParse(Iterator<?> streamingInputs) {
            return List.of().iterator();
        }
    }

    /**
     * Mirrors Python's patched OpenAI API server in
     * {@code openjiuwen/core/foundation/llm/model_clients/openai_model_client.py}.
     */
    private static final class MockOpenAiServer implements AutoCloseable {
        private final HttpServer server;
        private final List<PlannedResponse> responses;
        private final AtomicInteger responseIndex = new AtomicInteger();
        private final List<RecordedRequest> requests = new ArrayList<>();
        private Map<String, Object> lastBody;
        private Map<String, String> lastHeaders;

        private MockOpenAiServer(String responseBody) throws IOException {
            this(response(200, responseBody));
        }

        private MockOpenAiServer(PlannedResponse... responses) throws IOException {
            this(0, responses);
        }

        private MockOpenAiServer(int port, PlannedResponse... responses) throws IOException {
            this.responses = List.of(responses);
            this.server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
            this.server.createContext("/chat/completions", this::handle);
            this.server.start();
        }

        private String baseUrl() {
            return "http://127.0.0.1:" + server.getAddress().getPort();
        }

        private void handle(HttpExchange exchange) throws IOException {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            lastBody = OBJECT_MAPPER.readValue(body, new TypeReference<LinkedHashMap<String, Object>>() {
            });
            lastHeaders = new LinkedHashMap<>();
            exchange.getRequestHeaders().forEach((name, values) -> {
                if (!values.isEmpty()) {
                    lastHeaders.put(name, values.get(0));
                }
            });
            requests.add(new RecordedRequest(exchange.getRequestURI().toString(), lastBody, lastHeaders));
            int index = Math.min(responseIndex.getAndIncrement(), responses.size() - 1);
            PlannedResponse response = responses.get(index);
            byte[] bytes = response.body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", response.contentType);
            if (response.status == 429 || response.status >= 500) {
                exchange.getResponseHeaders().add("retry-after-ms", "1");
            }
            exchange.sendResponseHeaders(response.status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        }

        @Override
        public void close() {
            server.stop(0);
        }
    }

    private record PlannedResponse(int status, String body, String contentType) {
    }

    private record RecordedRequest(String uri, Map<String, Object> body, Map<String, String> headers) {
    }

    private static final class DelayedSseServer implements AutoCloseable {
        private final HttpServer server;
        private final String firstChunk;
        private final CountDownLatch firstChunkSent = new CountDownLatch(1);
        private final CountDownLatch completeResponse = new CountDownLatch(1);
        private Map<String, Object> lastBody;

        private DelayedSseServer(String firstChunk) throws IOException {
            this.firstChunk = firstChunk;
            this.server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            this.server.createContext("/chat/completions", this::handle);
            this.server.start();
        }

        private String baseUrl() {
            return "http://127.0.0.1:" + server.getAddress().getPort();
        }

        private boolean awaitFirstChunk(Duration timeout) throws InterruptedException {
            return firstChunkSent.await(timeout.toMillis(), TimeUnit.MILLISECONDS);
        }

        private void completeResponse() {
            completeResponse.countDown();
        }

        private void handle(HttpExchange exchange) throws IOException {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            lastBody = OBJECT_MAPPER.readValue(body, new TypeReference<LinkedHashMap<String, Object>>() {
            });
            exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
            exchange.sendResponseHeaders(200, 0);
            exchange.getResponseBody().write(firstChunk.getBytes(StandardCharsets.UTF_8));
            exchange.getResponseBody().flush();
            firstChunkSent.countDown();
            try {
                completeResponse.await(5, TimeUnit.SECONDS);
                exchange.getResponseBody().write("data: [DONE]\n\n".getBytes(StandardCharsets.UTF_8));
                exchange.getResponseBody().flush();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            } finally {
                exchange.close();
            }
        }

        @Override
        public void close() {
            completeResponse();
            server.stop(0);
        }
    }

    private static final class BlockingSseServer implements AutoCloseable {
        private final HttpServer server;
        private final String firstChunk;
        private final CountDownLatch firstChunkSent = new CountDownLatch(1);
        private final CountDownLatch responseClosed = new CountDownLatch(1);
        private volatile boolean stopping;

        private BlockingSseServer(String firstChunk) throws IOException {
            this.firstChunk = firstChunk;
            this.server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            this.server.createContext("/chat/completions", this::handle);
            this.server.start();
        }

        private String baseUrl() {
            return "http://127.0.0.1:" + server.getAddress().getPort();
        }

        private boolean awaitFirstChunk(Duration timeout) throws InterruptedException {
            return firstChunkSent.await(timeout.toMillis(), TimeUnit.MILLISECONDS);
        }

        private boolean awaitResponseClosed(Duration timeout) throws InterruptedException {
            return responseClosed.await(timeout.toMillis(), TimeUnit.MILLISECONDS);
        }

        private void handle(HttpExchange exchange) throws IOException {
            exchange.getRequestBody().readAllBytes();
            exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
            exchange.sendResponseHeaders(200, 0);
            OutputStream responseBody = exchange.getResponseBody();
            try {
                responseBody.write(firstChunk.getBytes(StandardCharsets.UTF_8));
                responseBody.flush();
                firstChunkSent.countDown();
                while (!stopping) {
                    Thread.sleep(50);
                    responseBody.write(": keepalive\n\n".getBytes(StandardCharsets.UTF_8));
                    responseBody.flush();
                }
            } catch (IOException exception) {
                responseClosed.countDown();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            } finally {
                responseClosed.countDown();
                exchange.close();
            }
        }

        @Override
        public void close() {
            stopping = true;
            server.stop(0);
        }
    }
}

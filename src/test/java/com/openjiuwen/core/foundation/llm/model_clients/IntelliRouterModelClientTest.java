/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.llm.model_clients;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.foundation.llm.output_parsers.BaseOutputParser;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessageChunk;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.foundation.llm.schema.ProviderType;
import com.openjiuwen.core.foundation.tool.schema.ToolInfo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Focused parity tests for IntelliRouter model client translation.
 *
 * <p>Mirrors Python's {@code IntelliRouterModelClient} in
 * {@code openjiuwen/core/foundation/llm/model_clients/intelli_router_model_client.py}.</p>
 */
class IntelliRouterModelClientTest {

    @AfterEach
    void cleanupRouterFactory() {
        IntelliRouterModelClient.setRouterFactoryForTesting(null);
        IntelliRouterModelClient.clearRouterCacheForTesting();
    }

    @Test
    void extractsTypedConfigFromModelClientExtraFields() {
        Map<String, Object> deployment = new LinkedHashMap<>();
        deployment.put("model_name", "qwen");
        deployment.put("id", "dep-1");
        Map<String, Object> strategyKwargs = new LinkedHashMap<>();
        strategyKwargs.put("beta", 2);
        strategyKwargs.put("alpha", 1);
        Map<String, Object> extraFields = new LinkedHashMap<>();
        extraFields.put("intelli_router_deployments", List.of(deployment));
        extraFields.put("intelli_router_strategy", "lowest-latency");
        extraFields.put("intelli_router_num_retries", 5);
        extraFields.put("intelli_router_timeout", 18.5D);
        extraFields.put("intelli_router_strategy_kwargs", strategyKwargs);
        extraFields.put("intelli_router_enable_health_check", true);
        extraFields.put("intelli_router_health_check_interval", 42.0D);
        ModelClientConfig clientConfig = ModelClientConfig.builder()
                .clientProvider(ProviderType.INTELLI_ROUTER)
                .verifySsl(false)
                .extraFields(extraFields)
                .build();

        IntelliRouterClientConfig routerConfig = IntelliRouterClientConfig.fromModelClientConfig(clientConfig);

        assertThat(routerConfig.getDeployments()).containsExactly(deployment);
        assertThat(routerConfig.getStrategy()).isEqualTo("lowest-latency");
        assertThat(routerConfig.getNumRetries()).isEqualTo(5);
        assertThat(routerConfig.getTimeout()).isEqualTo(18.5D);
        assertThat(routerConfig.getStrategyKwargs()).containsEntry("alpha", 1).containsEntry("beta", 2);
        assertThat(routerConfig.isEnableHealthCheck()).isTrue();
        assertThat(routerConfig.getHealthCheckInterval()).isEqualTo(42.0D);
        assertThat(routerConfig.isVerifySsl()).isFalse();
    }

    @Test
    void extractsDefaultConfigWhenExtraFieldsAreMissing() {
        ModelClientConfig clientConfig = ModelClientConfig.builder()
                .clientProvider(ProviderType.INTELLI_ROUTER)
                .build();

        IntelliRouterClientConfig routerConfig = IntelliRouterClientConfig.fromModelClientConfig(clientConfig);

        assertThat(routerConfig.getDeployments()).isEmpty();
        assertThat(routerConfig.getStrategy()).isEqualTo("simple-shuffle");
        assertThat(routerConfig.getNumRetries()).isEqualTo(3);
        assertThat(routerConfig.getTimeout()).isEqualTo(30.0D);
        assertThat(routerConfig.getStrategyKwargs()).isEmpty();
        assertThat(routerConfig.isEnableHealthCheck()).isFalse();
        assertThat(routerConfig.getHealthCheckInterval()).isEqualTo(300.0D);
        assertThat(routerConfig.isVerifySsl()).isTrue();
    }

    @Test
    void routerKeyIsStableForEquivalentMapOrdering() {
        IntelliRouterClientConfig first = new IntelliRouterClientConfig(
                List.of(orderedMap("id", "dep-1", "model_name", "qwen")),
                "simple-shuffle",
                3,
                30.0D,
                orderedMap("b", 2, "a", 1),
                false,
                300.0D,
                true);
        IntelliRouterClientConfig second = new IntelliRouterClientConfig(
                List.of(orderedMap("model_name", "qwen", "id", "dep-1")),
                "simple-shuffle",
                3,
                30.0D,
                orderedMap("a", 1, "b", 2),
                false,
                300.0D,
                true);

        assertThat(IntelliRouterModelClient.makeRouterKey(first))
                .isEqualTo(IntelliRouterModelClient.makeRouterKey(second));
    }

    @Test
    void routerKeyChangesWhenConfigChanges() {
        IntelliRouterClientConfig first = new IntelliRouterClientConfig(
                List.of(orderedMap("id", "dep-1", "model_name", "qwen")),
                "simple-shuffle",
                3,
                30.0D,
                Map.of(),
                false,
                300.0D,
                true);
        IntelliRouterClientConfig second = new IntelliRouterClientConfig(
                List.of(orderedMap("id", "dep-2", "model_name", "qwen-plus")),
                "simple-shuffle",
                3,
                30.0D,
                Map.of(),
                false,
                300.0D,
                true);

        assertThat(IntelliRouterModelClient.makeRouterKey(first))
                .isNotEqualTo(IntelliRouterModelClient.makeRouterKey(second));
    }

    @Test
    void getOrCreateRouterReturnsSameInstanceForSameConfig() {
        AtomicInteger created = new AtomicInteger();
        IntelliRouterModelClient.setRouterFactoryForTesting(config ->
                RecordingRouter.withResponse(response("router-" + created.incrementAndGet(), null, null)));
        IntelliRouterClientConfig routerConfig = routerConfig("dep-1", "qwen");

        IntelliRouterModelClient.ReliableRouter first =
                IntelliRouterModelClient.getOrCreateRouterForTesting(routerConfig);
        IntelliRouterModelClient.ReliableRouter second =
                IntelliRouterModelClient.getOrCreateRouterForTesting(routerConfig);

        assertSame(first, second);
        assertThat(created).hasValue(1);
    }

    @Test
    void getOrCreateRouterReturnsDifferentInstancesForDifferentConfigs() {
        AtomicInteger created = new AtomicInteger();
        IntelliRouterModelClient.setRouterFactoryForTesting(config ->
                RecordingRouter.withResponse(response("router-" + created.incrementAndGet(), null, null)));

        IntelliRouterModelClient.ReliableRouter first =
                IntelliRouterModelClient.getOrCreateRouterForTesting(routerConfig("dep-1", "qwen"));
        IntelliRouterModelClient.ReliableRouter second =
                IntelliRouterModelClient.getOrCreateRouterForTesting(routerConfig("dep-2", "qwen-plus"));

        assertThat(first).isNotSameAs(second);
        assertThat(created).hasValue(2);
    }

    @Test
    void createRouterRaisesConfigErrorWhenFactoryIsUnavailable() {
        assertThatThrownBy(() -> IntelliRouterModelClient.createRouterForTesting(routerConfig("dep-1", "qwen")))
                .isInstanceOf(BaseError.class)
                .extracting(error -> (BaseError) error)
                .satisfies(error -> {
                    assertThat(error.getStatus()).isEqualTo(StatusCode.MODEL_SERVICE_CONFIG_ERROR);
                    assertThat(error.getParams()).containsEntry(
                            "error_msg",
                            "intelli_router package is not installed. Please install it with: pip install intelli-router");
                });
    }

    @Test
    void injectedRouterSkipsApiKeyAndApiBaseValidation() throws Exception {
        RecordingRouter router = RecordingRouter.withResponse(response("ok", null, null));
        ModelClientConfig clientConfig = ModelClientConfig.builder()
                .clientProvider(ProviderType.INTELLI_ROUTER)
                .verifySsl(true)
                .build();

        IntelliRouterModelClient client = new IntelliRouterModelClient(requestConfig(), clientConfig, router);
        AssistantMessage result = client.invoke("hello", null, null, null, null, null, null, null, null, null);

        assertThat(result.getContent()).isEqualTo("ok");
        assertThat(router.completionCalls).isEqualTo(1);
    }

    @Test
    void constructorCreatesRouterFromConfigFactory() {
        IntelliRouterModelClient.setRouterFactoryForTesting(config ->
                RecordingRouter.withResponse(response(config.getStrategy(), null, null)));

        IntelliRouterModelClient client = new IntelliRouterModelClient(requestConfig(), intelliRouterConfig());

        assertThat(client.routerForTesting()).isInstanceOf(RecordingRouter.class);
    }

    @Test
    void constructorUsesExternalRouterDirectly() {
        RecordingRouter router = RecordingRouter.withResponse(response("external", null, null));

        IntelliRouterModelClient client = new IntelliRouterModelClient(requestConfig(), intelliRouterConfig(), router);

        assertSame(router, client.routerForTesting());
    }

    @Test
    void constructorDoesNotRequireApiKeyWhenNoApiBaseIsPresent() throws Exception {
        RecordingRouter router = RecordingRouter.withResponse(response("ok", null, null));
        ModelClientConfig clientConfig = ModelClientConfig.builder()
                .clientProvider(ProviderType.INTELLI_ROUTER)
                .verifySsl(false)
                .build();

        IntelliRouterModelClient client = new IntelliRouterModelClient(requestConfig(), clientConfig, router);

        assertThat(client.invoke("hello", null, null, null, null, null, null, null, null, null).getContent())
                .isEqualTo("ok");
    }

    @Test
    void invokeBasicReturnsAssistantContent() throws Exception {
        RecordingRouter router = RecordingRouter.withResponse(response("Hello world!", null, null));
        IntelliRouterModelClient client = new IntelliRouterModelClient(requestConfig(), intelliRouterConfig(), router);

        AssistantMessage result = client.invoke(List.of(Map.of("role", "user", "content", "Hi")),
                null, null, null, null, null, null, null, null, null);

        assertThat(result.getContent()).isEqualTo("Hello world!");
        assertThat(result.getFinishReason()).isEqualTo("stop");
    }

    @Test
    void invokeBuildsPythonRouterParamsAndConvertsResponse() throws Exception {
        List<Map<String, Object>> toolCalls = List.of(Map.of(
                "id", "call-1",
                "function", Map.of("name", "lookup", "arguments", "{\"q\":\"java\"}")
        ));
        RecordingRouter router = RecordingRouter.withResponse(response("raw", "think", toolCalls));
        IntelliRouterModelClient client = new IntelliRouterModelClient(
                requestConfig(),
                intelliRouterConfig(),
                router);
        ToolInfo toolInfo = ToolInfo.builder()
                .name("search")
                .description("Search docs")
                .parameters(Map.of("type", "object"))
                .build();
        Map<String, Object> kwargs = new LinkedHashMap<>();
        kwargs.put("top_p", 0.33D);
        kwargs.put("custom", "value");

        AssistantMessage result = client.invoke(
                "hello",
                List.of(toolInfo),
                null,
                null,
                "",
                null,
                "STOP",
                new PrefixParser(),
                12.5F,
                kwargs);

        assertThat(router.model).isEqualTo("configured-model");
        assertThat(router.messages).containsExactly(Map.of("role", "user", "content", "hello"));
        assertThat(router.requestParams)
                .containsEntry("temperature", 0.2D)
                .containsEntry("top_p", 0.33D)
                .containsEntry("max_tokens", 99)
                .containsEntry("stop", "STOP")
                .containsEntry("timeout", 12.5D)
                .containsEntry("custom", "value");
        assertThat(router.requestParams).containsKey("tools");
        assertThat(router.requestParams).doesNotContainKeys("model", "messages", "stream", "tool_choice");

        assertThat(result.getContent()).isEqualTo("parsed:raw");
        assertThat(result.getReasoningContent()).isEqualTo("think");
        assertThat(result.getFinishReason()).isEqualTo("tool_calls");
        assertThat(result.getToolCalls()).hasSize(1);
        assertThat(result.getToolCalls().getFirst().getId()).isEqualTo("call-1");
        assertThat(result.getToolCalls().getFirst().getName()).isEqualTo("lookup");
        assertThat(result.getToolCalls().getFirst().getArguments()).isEqualTo("{\"q\":\"java\"}");
        assertThat(result.getToolCalls().getFirst().getIndex()).isZero();
    }

    @Test
    void invokeModelOverrideIsPassedToRouter() throws Exception {
        RecordingRouter router = RecordingRouter.withResponse(response("ok", null, null));
        IntelliRouterModelClient client = new IntelliRouterModelClient(requestConfig(), intelliRouterConfig(), router);

        client.invoke("hello", null, null, null, "override-model", null, null, null, null, null);

        assertThat(router.model).isEqualTo("override-model");
    }

    @Test
    void invokeEmptyChoicesReturnsEmptyContent() throws Exception {
        RecordingRouter router = RecordingRouter.withResponse(Map.of("choices", List.of()));
        IntelliRouterModelClient client = new IntelliRouterModelClient(requestConfig(), intelliRouterConfig(), router);

        AssistantMessage result = client.invoke("hello", null, null, null, null, null, null, null, null, null);

        assertThat(result.getContent()).isEqualTo("");
        assertThat(result.getFinishReason()).isEqualTo("stop");
    }

    @Test
    void streamBasicReturnsOneChunk() throws Exception {
        RecordingRouter router = RecordingRouter.withChunks(List.of(
                Map.of("choices", List.of(Map.of("delta", Map.of("content", "Hello"))))
        ));
        IntelliRouterModelClient client = new IntelliRouterModelClient(requestConfig(), intelliRouterConfig(), router);

        Iterator<AssistantMessageChunk> iterator = client.stream("Hi", null, null, null, null,
                null, null, null, null, null);

        assertThat(iterator.next().getContent()).isEqualTo("Hello");
        assertThat(iterator.hasNext()).isFalse();
    }

    @Test
    void streamMultipleChunksAreReturnedInOrder() throws Exception {
        RecordingRouter router = RecordingRouter.withChunks(List.of(
                Map.of("choices", List.of(Map.of("delta", Map.of("content", "Hello ")))),
                Map.of("choices", List.of(Map.of("delta", Map.of("content", "world")))),
                Map.of("choices", List.of(Map.of("delta", Map.of("content", "!"))))
        ));
        IntelliRouterModelClient client = new IntelliRouterModelClient(requestConfig(), intelliRouterConfig(), router);

        List<AssistantMessageChunk> chunks = new ArrayList<>();
        client.stream("Hi", null, null, null, null, null, null, null, null, null).forEachRemaining(chunks::add);

        assertThat(chunks).extracting(AssistantMessageChunk::getContent).containsExactly("Hello ", "world", "!");
    }

    @Test
    void streamEmptyChoicesReturnsEmptyChunkContent() throws Exception {
        RecordingRouter router = RecordingRouter.withChunks(List.of(Map.of("choices", List.of())));
        IntelliRouterModelClient client = new IntelliRouterModelClient(requestConfig(), intelliRouterConfig(), router);

        AssistantMessageChunk chunk = client.stream("Hi", null, null, null, null,
                null, null, null, null, null).next();

        assertThat(chunk.getContent()).isEqualTo("");
    }

    @Test
    void streamUsesRouterStreamCompletionAndConvertsChunks() throws Exception {
        RecordingRouter router = RecordingRouter.withChunks(List.of(
                Map.of("choices", List.of(Map.of("delta", Map.of("content", "a")))),
                Map.of("choices", List.of(Map.of("delta", Map.of("content", "b")))),
                Map.of("choices", List.of())
        ));
        IntelliRouterModelClient client = new IntelliRouterModelClient(
                requestConfig(),
                intelliRouterConfig(),
                router);

        Iterator<AssistantMessageChunk> iterator = client.stream(
                List.of(Map.of("role", "user", "content", "hi")),
                null,
                0.4F,
                0.6F,
                null,
                64,
                null,
                null,
                null,
                Map.of("request_id", "r1"));
        List<AssistantMessageChunk> chunks = new ArrayList<>();
        iterator.forEachRemaining(chunks::add);

        assertThat(router.streamCalls).isEqualTo(1);
        assertThat(router.requestParams)
                .containsEntry("temperature", 0.4000000059604645D)
                .containsEntry("top_p", 0.6000000238418579D)
                .containsEntry("max_tokens", 64)
                .containsEntry("request_id", "r1");
        assertThat(router.requestParams).doesNotContainKeys("model", "messages", "stream");
        assertThat(chunks).extracting(AssistantMessageChunk::getContent).containsExactly("a", "b", "");
    }

    @Test
    void generateImageRaisesError() {
        IntelliRouterModelClient client = new IntelliRouterModelClient(
                requestConfig(),
                intelliRouterConfig(),
                RecordingRouter.withResponse(response("", null, null)));

        assertUnsupportedMessage(() -> client.generateImage(List.of(), null, "1664*928", null, 1,
                true, false, 0, Map.of()), "IntelliRouter does not support image generation");
    }

    @Test
    void generateSpeechRaisesError() {
        IntelliRouterModelClient client = new IntelliRouterModelClient(
                requestConfig(),
                intelliRouterConfig(),
                RecordingRouter.withResponse(response("", null, null)));

        assertUnsupportedMessage(() -> client.generateSpeech(List.of(), null, "Cherry", "Auto", Map.of()),
                "IntelliRouter does not support speech generation");
    }

    @Test
    void generateVideoRaisesError() {
        IntelliRouterModelClient client = new IntelliRouterModelClient(
                requestConfig(),
                intelliRouterConfig(),
                RecordingRouter.withResponse(response("", null, null)));

        assertUnsupportedMessage(() -> client.generateVideo(List.of(), null, null, null, null, null, 5,
                true, false, null, null, Map.of()), "IntelliRouter does not support video generation");
    }

    @Test
    void routerCacheReusesFactoryResultForSameConfig() {
        AtomicInteger created = new AtomicInteger();
        IntelliRouterModelClient.setRouterFactoryForTesting(config ->
                RecordingRouter.withResponse(response("router-" + created.incrementAndGet(), null, null)));
        ModelClientConfig config = intelliRouterConfig();

        IntelliRouterModelClient first = new IntelliRouterModelClient(requestConfig(), config);
        IntelliRouterModelClient second = new IntelliRouterModelClient(requestConfig(), config);

        assertThat(created).hasValue(1);
        assertSame(first.routerForTesting(), second.routerForTesting());
    }

    @Test
    void convertResponseWithContentReturnsAssistantMessage() throws Exception {
        IntelliRouterModelClient client = new IntelliRouterModelClient(
                requestConfig(),
                intelliRouterConfig(),
                RecordingRouter.withResponse(response("", null, null)));

        AssistantMessage result = client.convertResponseForTesting(response("Hello", null, null));

        assertThat(result.getContent()).isEqualTo("Hello");
        assertThat(result.getToolCalls()).isNull();
    }

    @Test
    void convertResponseEmptyChoicesReturnsEmptyContent() throws Exception {
        IntelliRouterModelClient client = new IntelliRouterModelClient(
                requestConfig(),
                intelliRouterConfig(),
                RecordingRouter.withResponse(response("", null, null)));

        AssistantMessage result = client.convertResponseForTesting(Map.of("choices", List.of()));

        assertThat(result.getContent()).isEqualTo("");
    }

    @Test
    void convertChunkWithContentReturnsAssistantMessageChunk() {
        IntelliRouterModelClient client = new IntelliRouterModelClient(
                requestConfig(),
                intelliRouterConfig(),
                RecordingRouter.withResponse(response("", null, null)));

        AssistantMessageChunk result = client.convertChunkForTesting(
                Map.of("choices", List.of(Map.of("delta", Map.of("content", "Hello chunk")))));

        assertThat(result.getContent()).isEqualTo("Hello chunk");
    }

    @Test
    void convertChunkEmptyChoicesReturnsEmptyContent() {
        IntelliRouterModelClient client = new IntelliRouterModelClient(
                requestConfig(),
                intelliRouterConfig(),
                RecordingRouter.withResponse(response("", null, null)));

        AssistantMessageChunk result = client.convertChunkForTesting(Map.of("choices", List.of()));

        assertThat(result.getContent()).isEqualTo("");
    }

    @Test
    void buildRequestParamsBasicUsesExplicitValues() {
        IntelliRouterModelClient client = new IntelliRouterModelClient(
                requestConfig(),
                intelliRouterConfig(),
                RecordingRouter.withResponse(response("", null, null)));

        Map<String, Object> params = client.buildRequestParamsForTesting(
                List.of(Map.of("role", "user", "content", "Hi")),
                null,
                0.5F,
                0.8F,
                512,
                null,
                false,
                null,
                null);

        assertThat(params)
                .containsEntry("temperature", 0.5D)
                .containsEntry("top_p", 0.800000011920929D)
                .containsEntry("max_tokens", 512);
    }

    @Test
    void buildRequestParamsWithToolsConvertsToolInfo() {
        IntelliRouterModelClient client = new IntelliRouterModelClient(
                requestConfig(),
                intelliRouterConfig(),
                RecordingRouter.withResponse(response("", null, null)));
        ToolInfo toolInfo = ToolInfo.builder()
                .name("test_tool")
                .description("A test tool")
                .parameters(Map.of("type", "object"))
                .build();

        Map<String, Object> params = client.buildRequestParamsForTesting(
                List.of(Map.of("role", "user", "content", "Hi")),
                List.of(toolInfo),
                null,
                null,
                null,
                null,
                false,
                null,
                null);

        assertThat(params).containsKey("tools");
        List<?> tools = (List<?>) params.get("tools");
        Map<?, ?> function = (Map<?, ?>) ((Map<?, ?>) tools.getFirst()).get("function");
        assertThat(function.get("name")).isEqualTo("test_tool");
    }

    @Test
    void buildRequestParamsOmitsNoneValuesExceptModelDefaults() {
        IntelliRouterModelClient client = new IntelliRouterModelClient(
                ModelRequestConfig.builder().modelName("test-model").build(),
                intelliRouterConfig(),
                RecordingRouter.withResponse(response("", null, null)));

        Map<String, Object> params = client.buildRequestParamsForTesting(
                List.of(Map.of("role", "user", "content", "Hi")),
                null,
                null,
                null,
                null,
                null,
                false,
                null,
                null);

        assertThat(params).containsKeys("temperature", "top_p");
        assertThat(params).doesNotContainKeys("max_tokens", "stop");
    }

    @Test
    void unsupportedModalitiesRaisePythonModelCallFailedErrors() {
        IntelliRouterModelClient client = new IntelliRouterModelClient(
                requestConfig(),
                intelliRouterConfig(),
                RecordingRouter.withResponse(response("", null, null)));

        assertUnsupportedMessage(() -> client.generateImage(List.of(), null, "1664*928", null, 1,
                true, false, 0, Map.of()), "IntelliRouter does not support image generation");
        assertUnsupportedMessage(() -> client.generateSpeech(List.of(), null, "Cherry", "Auto", Map.of()),
                "IntelliRouter does not support speech generation");
        assertUnsupportedMessage(() -> client.generateVideo(List.of(), null, null, null, null, null, 5,
                true, false, null, null, Map.of()), "IntelliRouter does not support video generation");
    }

    private static void assertUnsupportedMessage(ThrowingRunnable runnable, String expectedMessage) {
        assertThatThrownBy(runnable::run)
                .isInstanceOf(BaseError.class)
                .extracting(error -> (BaseError) error)
                .satisfies(error -> {
                    assertThat(error.getStatus()).isEqualTo(StatusCode.MODEL_CALL_FAILED);
                    assertThat(error.getParams()).containsEntry("error_msg", expectedMessage);
                });
    }

    private static ModelRequestConfig requestConfig() {
        return ModelRequestConfig.builder()
                .modelName("configured-model")
                .temperature(0.2D)
                .topP(0.8D)
                .maxTokens(99)
                .build();
    }

    private static ModelClientConfig intelliRouterConfig() {
        return ModelClientConfig.builder()
                .clientProvider(ProviderType.INTELLI_ROUTER)
                .verifySsl(false)
                .extraFields(Map.of(
                        "intelli_router_deployments", List.of(orderedMap("id", "dep-1", "model_name", "qwen")),
                        "intelli_router_strategy", "simple-shuffle"
                ))
                .build();
    }

    private static IntelliRouterClientConfig routerConfig(String id, String modelName) {
        return new IntelliRouterClientConfig(
                List.of(orderedMap("id", id, "model_name", modelName, "api_key", "k", "api_base", "b")),
                "simple-shuffle",
                3,
                30.0D,
                Map.of(),
                false,
                300.0D,
                true);
    }

    private static Map<String, Object> response(String content, String reasoning, List<Map<String, Object>> toolCalls) {
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("content", content);
        if (reasoning != null) {
            message.put("reasoning_content", reasoning);
        }
        if (toolCalls != null) {
            message.put("tool_calls", toolCalls);
        }
        return Map.of("choices", List.of(Map.of("message", message)));
    }

    private static Map<String, Object> orderedMap(Object... pairs) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            result.put(String.valueOf(pairs[i]), pairs[i + 1]);
        }
        return result;
    }

    /**
     * Test router for the IntelliRouter third-party boundary.
     *
     * <p>Mirrors Python's injected {@code router} argument in
     * {@code openjiuwen/core/foundation/llm/model_clients/intelli_router_model_client.py}.</p>
     */
    private static final class RecordingRouter implements IntelliRouterModelClient.ReliableRouter {
        private final Map<String, Object> response;
        private final List<Map<String, Object>> chunks;
        private int completionCalls;
        private int streamCalls;
        private String model;
        private List<Map<String, Object>> messages;
        private Map<String, Object> requestParams;

        private RecordingRouter(Map<String, Object> response, List<Map<String, Object>> chunks) {
            this.response = response;
            this.chunks = chunks;
        }

        private static RecordingRouter withResponse(Map<String, Object> response) {
            return new RecordingRouter(response, List.of());
        }

        private static RecordingRouter withChunks(List<Map<String, Object>> chunks) {
            return new RecordingRouter(Map.of(), chunks);
        }

        @Override
        public Map<String, Object> completion(
                String model,
                List<Map<String, Object>> messages,
                Map<String, Object> requestParams) {
            completionCalls++;
            this.model = model;
            this.messages = messages;
            this.requestParams = requestParams;
            return response;
        }

        @Override
        public Iterator<Map<String, Object>> streamCompletion(
                String model,
                List<Map<String, Object>> messages,
                Map<String, Object> requestParams) {
            streamCalls++;
            this.model = model;
            this.messages = messages;
            this.requestParams = requestParams;
            return chunks.iterator();
        }
    }

    /**
     * Parser used to verify invoke parser fallback behavior.
     *
     * <p>Mirrors Python's {@code output_parser.parse} call in
     * {@code openjiuwen/core/foundation/llm/model_clients/intelli_router_model_client.py}.</p>
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
     * Functional test exception bridge.
     *
     * <p>Mirrors Python's raised {@code build_error} paths in
     * {@code openjiuwen/core/foundation/llm/model_clients/intelli_router_model_client.py}.</p>
     */
    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }
}

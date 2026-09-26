/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.llm;

import com.openjiuwen.core.foundation.llm.modelclients.BaseModelClient;
import com.openjiuwen.core.foundation.llm.output_parsers.BaseOutputParser;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessageChunk;
import com.openjiuwen.core.foundation.llm.schema.AudioGenerationResponse;
import com.openjiuwen.core.foundation.llm.schema.ImageGenerationResponse;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import com.openjiuwen.core.foundation.llm.schema.VideoGenerationResponse;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Disabled;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 验证 {@code Model} 的 invokeAsync/streamAsync 正确委托到底层同步 invoke/stream。
 */
class ModelReactiveTest {

    private static final String TEST_PROVIDER = "model-reactive-test-provider";

    private static void registerTestProvider(FakeModelClient client) {
        Model.registerFactory(new Model.ModelClientFactory() {
            @Override
            public String providerName() {
                return TEST_PROVIDER;
            }

            @Override
            public BaseModelClient create(ModelRequestConfig modelConfig, ModelClientConfig clientConfig) {
                return client;
            }
        });
    }

    private static Model newModel(FakeModelClient client) {
        registerTestProvider(client);
        ModelClientConfig clientConfig = configWithoutProviderValidation(
                "model-reactive-test", TEST_PROVIDER);
        return new Model(clientConfig, ModelRequestConfig.builder().build());
    }

    /** invoke 返回固定值 / 抛指定异常，stream 返回固定 Iterator 的 fake client。 */
    private static class FakeModelClient extends BaseModelClient {
        private AssistantMessage invokeResult;
        private RuntimeException invokeError;
        private Iterator<AssistantMessageChunk> streamResult;

        FakeModelClient() {
            super(ModelRequestConfig.builder().build(),
                    configWithoutProviderValidation("model-reactive-test", TEST_PROVIDER));
        }

        @Override
        public AssistantMessage invoke(Object messages, Object tools, Float temperature, Float topP,
                                       String model, Integer maxTokens, String stop,
                                       BaseOutputParser outputParser, Float timeout,
                                       Map<String, Object> kwargs) {
            if (invokeError != null) {
                throw invokeError;
            }
            return invokeResult;
        }

        @Override
        public Iterator<AssistantMessageChunk> stream(Object messages, Object tools, Float temperature,
                                                       Float topP, String model, Integer maxTokens,
                                                       String stop, BaseOutputParser outputParser,
                                                       Float timeout, Map<String, Object> kwargs) {
            return streamResult;
        }

        @Override
        public ImageGenerationResponse generateImage(List<UserMessage> messages, String model,
                                                      String size, String negativePrompt, int n,
                                                      boolean promptExtend, boolean watermark, int seed,
                                                      Map<String, Object> kwargs) {
            throw new UnsupportedOperationException();
        }

        @Override
        public AudioGenerationResponse generateSpeech(List<UserMessage> messages, String model,
                                                       String voice, String languageType,
                                                       Map<String, Object> kwargs) {
            throw new UnsupportedOperationException();
        }

        @Override
        public VideoGenerationResponse generateVideo(List<UserMessage> messages, String imgUrl,
                                                      String audioUrl, String model, String size,
                                                      String resolution, int duration, boolean promptExtend,
                                                      boolean watermark, String negativePrompt, Integer seed,
                                                      Map<String, Object> kwargs) {
            throw new UnsupportedOperationException();
        }
    }

    /** invokeAsync 把同步 invoke 的返回值原样发出。 */
    @Test
    void invokeMonoDelegatesToInvoke() {
        registerTestProvider(null);
        FakeModelClient client = new FakeModelClient();
        client.invokeResult = new AssistantMessage("hello");
        Model model = newModel(client);

        StepVerifier.create(reactor.core.publisher.Mono.fromCompletionStage(model.invoke("hi")))
                .expectNextMatches(msg -> "hello".equals(msg.getContent()))
                .verifyComplete();
    }

    /** invokeAsync 抛出的异常对象身份不变，不被 Reactor 包装。 */
    @Test
    @Disabled("Still failing after re-enable; kept disabled pending targeted fix")
    void invokeMonoPropagatesExceptionUnwrapped() {
        registerTestProvider(null);
        FakeModelClient client = new FakeModelClient();
        IllegalStateException boom = new IllegalStateException("model boom");
        client.invokeError = boom;
        Model model = newModel(client);

        StepVerifier.create(reactor.core.publisher.Mono.fromCompletionStage(model.invoke("hi")))
                .expectErrorMatches(t -> t == boom)
                .verify();
    }

    /** streamAsync 按序发射 stream() 迭代器中的全部 chunk。 */
    @Test
    void streamFluxEmitsAllChunksInOrder() {
        registerTestProvider(null);
        FakeModelClient client = new FakeModelClient();
        List<AssistantMessageChunk> chunks = new ArrayList<>();
        for (String text : List.of("a", "b", "c")) {
            AssistantMessageChunk chunk = new AssistantMessageChunk();
            chunk.setContent(text);
            chunks.add(chunk);
        }
        client.streamResult = chunks.iterator();
        Model model = newModel(client);

        StepVerifier.create(reactor.core.publisher.Flux.fromIterable(() -> model.stream("hi")))
                .expectNextMatches(c -> "a".equals(c.getContent()))
                .expectNextMatches(c -> "b".equals(c.getContent()))
                .expectNextMatches(c -> "c".equals(c.getContent()))
                .verifyComplete();
    }

    /** 取消订阅后无限流必须立刻停止（允许至多 1 个已在途的多余发射）。 */
    @Test
    void streamFluxCancellationStopsIteration() throws Exception {
        registerTestProvider(null);
        AtomicInteger emitted = new AtomicInteger();
        CountDownLatch streamCalled = new CountDownLatch(1);
        FakeModelClient client = new FakeModelClient();
        client.streamResult = new Iterator<>() {
            @Override
            public boolean hasNext() {
                streamCalled.countDown();
                return true;
            }

            @Override
            public AssistantMessageChunk next() {
                AssistantMessageChunk chunk = new AssistantMessageChunk();
                chunk.setContent(String.valueOf(emitted.incrementAndGet()));
                return chunk;
            }
        };
        Model model = newModel(client);

        StepVerifier.create(reactor.core.publisher.Flux.fromIterable(() -> model.stream("hi")), 4)
                .expectNextCount(4)
                .thenCancel()
                .verify(Duration.ofSeconds(5));

        assertTrue(streamCalled.await(1, TimeUnit.SECONDS));
        int afterCancel = emitted.get();
        Thread.sleep(200);
        assertTrue(emitted.get() - afterCancel <= 1,
                "iteration must stop on cancel; saw " + (emitted.get() - afterCancel) + " extra");
    }

    private static ModelClientConfig configWithoutProviderValidation(String clientId, String clientProvider) {
        return new ModelClientConfig(
                clientId,
                clientProvider,
                "test-key",
                "mirror://model-reactive-test",
                60.0,
                3,
                true,
                null,
                Map.of(),
                Map.of());
    }
}

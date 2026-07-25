/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.operator.legacy.llm_call;

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

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证 Legacy LLMCall.invokeAsync / streamAsync 正确委托到同步 invoke / stream。
 */
class LegacyLLMCallReactiveTest {

    private static final String TEST_PROVIDER = "legacy-llmcall-reactive-test-provider";

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

    private static LLMCall newLLMCall(FakeModelClient client) {
        registerTestProvider(client);
        ModelClientConfig clientConfig = ModelClientConfig.builder()
                .clientId("legacy-llmcall-reactive-test")
                .clientProvider(TEST_PROVIDER)
                .apiKey("test-key")
                .apiBase("mirror://legacy-llmcall-reactive-test")
                .build();
        Model model = new Model(clientConfig, ModelRequestConfig.builder().modelName("test-model").build());
        return new LLMCall("test-model", model, "system", "{{query}}");
    }

    /** invoke 返回固定值 / 抛指定异常，stream 返回固定 Iterator 的 fake client。 */
    private static class FakeModelClient extends BaseModelClient {
        private AssistantMessage invokeResult;
        private Exception invokeError;
        private Iterator<AssistantMessageChunk> streamResult;

        FakeModelClient() {
            super(ModelRequestConfig.builder().modelName("test-model").build(),
                    ModelClientConfig.builder()
                            .clientId("legacy-llmcall-reactive-test")
                            .clientProvider(TEST_PROVIDER)
                            .apiKey("test-key")
                            .apiBase("mirror://legacy-llmcall-reactive-test")
                            .build());
        }

        @Override
        public AssistantMessage invoke(Object messages, Object tools, Float temperature, Float topP,
                                       String model, Integer maxTokens, String stop,
                                       BaseOutputParser outputParser, Float timeout,
                                       Map<String, Object> kwargs) throws Exception {
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

    /** invoke returns CompletionStage that completes with the AssistantMessage. */
    @Test
    void invokeDelegatesToInvoke() throws Exception {
        registerTestProvider(null);
        FakeModelClient client = new FakeModelClient();
        client.invokeResult = new AssistantMessage("hello");
        LLMCall llmCall = newLLMCall(client);

        AssistantMessage result = llmCall.invoke(Map.of("query", "hi"), null, null, null)
                .toCompletableFuture().get();
        assertThat(result).isNotNull();
        assertTrue("hello".equals(result.getContent()));
    }

    /** invoke propagates exception unwrapped. */
    @Test
    void invokePropagatesExceptionUnwrapped() throws Exception {
        registerTestProvider(null);
        FakeModelClient client = new FakeModelClient();
        IllegalStateException boom = new IllegalStateException("llmcall boom");
        client.invokeError = boom;
        LLMCall llmCall = newLLMCall(client);

        try {
            llmCall.invoke(Map.of("query", "hi"), null, null, null)
                    .toCompletableFuture().get();
            assertTrue(false, "Should have thrown");
        } catch (java.util.concurrent.ExecutionException e) {
            assertTrue(e.getCause() instanceof IllegalStateException);
        }
    }

    /** stream returns Iterator that emits all chunks in order. */
    @Test
    void streamEmitsAllChunksInOrder() {
        registerTestProvider(null);
        FakeModelClient client = new FakeModelClient();
        List<AssistantMessageChunk> chunks = new ArrayList<>();
        for (String text : List.of("a", "b", "c")) {
            AssistantMessageChunk chunk = new AssistantMessageChunk();
            chunk.setContent(text);
            chunks.add(chunk);
        }
        client.streamResult = chunks.iterator();
        LLMCall llmCall = newLLMCall(client);

        Iterator<AssistantMessageChunk> it = llmCall.stream(Map.of("query", "hi"), null, null, null);
        assertTrue(it.hasNext());
        assertTrue("a".equals(it.next().getContent()));
        assertTrue(it.hasNext());
        assertTrue("b".equals(it.next().getContent()));
        assertTrue(it.hasNext());
        assertTrue("c".equals(it.next().getContent()));
    }

    /** stream iterator stops when underlying iterator is exhausted. */
    @Test
    void streamIteratorStopsWhenExhausted() {
        registerTestProvider(null);
        FakeModelClient client = new FakeModelClient();
        List<AssistantMessageChunk> chunks = new ArrayList<>();
        AssistantMessageChunk chunk = new AssistantMessageChunk();
        chunk.setContent("only");
        chunks.add(chunk);
        client.streamResult = chunks.iterator();
        LLMCall llmCall = newLLMCall(client);

        Iterator<AssistantMessageChunk> it = llmCall.stream(Map.of("query", "hi"), null, null, null);
        assertTrue(it.hasNext());
        assertTrue("only".equals(it.next().getContent()));
        assertTrue(!it.hasNext());
    }
}

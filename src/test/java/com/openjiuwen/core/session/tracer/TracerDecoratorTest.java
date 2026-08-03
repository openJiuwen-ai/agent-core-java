/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session.tracer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.session.config.Config;
import com.openjiuwen.core.session.internal.AgentSession;
import com.openjiuwen.core.session.stream.TraceSchema;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

class TracerDecoratorTest {
    private static final String TEST_MODEL_PROVIDER = "tracer-decorator-test-provider";

    interface TestTool {
        String invoke(String input);
    }

    static class TestToolImpl implements TestTool {
        @Override
        public String invoke(String input) {
            return "ok:" + input;
        }
    }

    @Test
    @DisplayName("decorateToolWithTrace supports AgentSessionApi wrappers")
    void decorateToolWithTraceSupportsWrappedSession() {
        TestTool decorated = TracerDecorator.decorateToolWithTrace(new TestToolImpl(), new AgentSessionApi());

        assertTrue(Proxy.isProxyClass(decorated.getClass()));
        assertEquals("ok:ping", decorated.invoke("ping"));
    }

    @Test
    @DisplayName("decorateToolWithTrace supports direct AgentSession instances")
    void decorateToolWithTraceSupportsDirectInnerSession() {
        AgentSession session = new AgentSession("session-1", new Config());
        TestTool decorated = TracerDecorator.decorateToolWithTrace(new TestToolImpl(), session);

        assertTrue(Proxy.isProxyClass(decorated.getClass()));
        assertEquals("ok:ping", decorated.invoke("ping"));
    }

    @Test
    @DisplayName("decorateModelWithTrace records concrete Model request and response")
    @SuppressWarnings("unchecked")
    void decorateModelWithTraceConcreteModelRecordsRequestAndResponse() throws Exception {
        AtomicReference<TraceTestModelClient> clientRef = new AtomicReference<>();
        Model.registerFactory(new Model.ModelClientFactory() {
            @Override
            public String providerName() {
                return TEST_MODEL_PROVIDER;
            }

            @Override
            public BaseModelClient create(ModelRequestConfig modelConfig, ModelClientConfig clientConfig) {
                TraceTestModelClient client = new TraceTestModelClient(modelConfig, clientConfig);
                clientRef.set(client);
                return client;
            }
        });
        ModelClientConfig clientConfig = ModelClientConfig.builder().clientId("tracer-decorator-test")
                .clientProvider(TEST_MODEL_PROVIDER).apiKey("test-key").apiBase("mock://tracer-decorator-test")
                .build();
        ModelRequestConfig requestConfig =
            ModelRequestConfig.builder().modelName("qwen-plus").temperature(0.7).topP(0.9).build();
        Model model = new Model(clientConfig, requestConfig);
        AgentSessionApi session = new AgentSessionApi();
        Map<String, Object> kwargs = new LinkedHashMap<>();
        kwargs.put("custom", "value");

        Model decorated = TracerDecorator.decorateModelWithTrace(model, session);
        AssistantMessage response =
            decorated.invoke("hello", null, null, null, null, null, null, null, null, kwargs);

        assertEquals("{\"result\": 2}", response.getContent());
        assertSame(kwargs, clientRef.get().getReceivedKwargs());
        TraceAgentSpan finishedSpan = null;
        for (int index = 0; index < 4; index++) {
            Object frame =
                session.getInner().streamWriterManager().getStreamEmitter().getStreamQueue().receive(1_000);
            TraceSchema trace = assertInstanceOf(TraceSchema.class, frame);
            finishedSpan = assertInstanceOf(TraceAgentSpan.class, trace.getPayload());
        }

        assertNotNull(finishedSpan);
        assertEquals("finish", finishedSpan.getStatus());
        assertEquals("llm", finishedSpan.getInvokeType());
        assertEquals("Model", finishedSpan.getName());
        assertEquals(Map.of("class_name", "Model", "type", "llm"), finishedSpan.getMetaData());
        assertEquals(2, finishedSpan.getOnInvokeData().size());
        Map<String, Object> params =
            (Map<String, Object>) finishedSpan.getOnInvokeData().get(0).get("llm_params");
        assertEquals("qwen-plus", params.get("model"));
        assertEquals(false, params.get("stream"));
        Map<String, Object> tracedResponse =
            (Map<String, Object>) finishedSpan.getOnInvokeData().get(1).get("llm_response");
        assertEquals("{\"result\": 2}", tracedResponse.get("content"));

        model.invoke("untraced", null, null, null, null, null, null, null, null, kwargs);
        assertNull(session.getInner().streamWriterManager().getStreamEmitter().getStreamQueue().receive(10));
    }

    @Test
    @DisplayName("decorateModelWithTrace preserves concrete Model stream iterator identity")
    void decorateModelWithTracePreservesStreamIteratorIdentity() throws Exception {
        String provider = TEST_MODEL_PROVIDER + "-stream";
        AtomicReference<TraceTestModelClient> clientRef = new AtomicReference<>();
        Model.registerFactory(new Model.ModelClientFactory() {
            @Override
            public String providerName() {
                return provider;
            }

            @Override
            public BaseModelClient create(ModelRequestConfig modelConfig, ModelClientConfig clientConfig) {
                TraceTestModelClient client = new TraceTestModelClient(modelConfig, clientConfig);
                clientRef.set(client);
                return client;
            }
        });
        Model model = new Model(ModelClientConfig.builder().clientId("tracer-stream-test")
                .clientProvider(provider).apiKey("test-key").apiBase("mock://tracer-stream-test").build(),
                ModelRequestConfig.builder().modelName("qwen-plus").build());
        CloseableChunkIterator source = new CloseableChunkIterator();
        clientRef.get().setStreamIterator(source);

        Model decorated = TracerDecorator.decorateModelWithTrace(model, new AgentSessionApi());
        Iterator<AssistantMessageChunk> actual =
            decorated.stream("hello", null, null, null, null, null, null, null, null, null);

        assertSame(source, actual);
        source.close();
        assertTrue(source.isClosed());
    }

    private static class TraceTestModelClient extends BaseModelClient {
        private Map<String, Object> receivedKwargs;
        private Iterator<AssistantMessageChunk> streamIterator = List.<AssistantMessageChunk>of().iterator();

        TraceTestModelClient(ModelRequestConfig modelConfig, ModelClientConfig modelClientConfig) {
            super(modelConfig, modelClientConfig);
        }

        @Override
        public AssistantMessage invoke(Object messages, Object tools, Float temperature, Float topP, String model,
                Integer maxTokens, String stop, BaseOutputParser outputParser, Float timeout,
                Map<String, Object> kwargs) {
            receivedKwargs = kwargs;
            Map<String, Object> params = buildRequestParams(messages, tools,
                    temperature != null ? temperature.doubleValue() : null,
                    topP != null ? topP.doubleValue() : null, model, stop, maxTokens, false, kwargs);
            recordRequestTrace(params);
            return new AssistantMessage("{\"result\": 2}");
        }

        @Override
        public Iterator<AssistantMessageChunk> stream(Object messages, Object tools, Float temperature, Float topP,
                String model, Integer maxTokens, String stop, BaseOutputParser outputParser, Float timeout,
                Map<String, Object> kwargs) {
            Map<String, Object> params = buildRequestParams(messages, tools,
                    temperature != null ? temperature.doubleValue() : null,
                    topP != null ? topP.doubleValue() : null, model, stop, maxTokens, true, kwargs);
            recordRequestTrace(params);
            return streamIterator;
        }

        Map<String, Object> getReceivedKwargs() {
            return receivedKwargs;
        }

        void setStreamIterator(Iterator<AssistantMessageChunk> streamIterator) {
            this.streamIterator = streamIterator;
        }

        @Override
        public ImageGenerationResponse generateImage(List<UserMessage> messages, String model, String size,
                String negativePrompt, int n, boolean promptExtend, boolean watermark, int seed,
                Map<String, Object> kwargs) {
            throw new UnsupportedOperationException();
        }

        @Override
        public AudioGenerationResponse generateSpeech(List<UserMessage> messages, String model, String voice,
                String languageType, Map<String, Object> kwargs) {
            throw new UnsupportedOperationException();
        }

        @Override
        public VideoGenerationResponse generateVideo(List<UserMessage> messages, String imgUrl, String audioUrl,
                String model, String size, String resolution, int duration, boolean promptExtend, boolean watermark,
                String negativePrompt, Integer seed, Map<String, Object> kwargs) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class CloseableChunkIterator implements Iterator<AssistantMessageChunk>, AutoCloseable {
        private boolean closed;

        @Override
        public boolean hasNext() {
            return false;
        }

        @Override
        public AssistantMessageChunk next() {
            return List.<AssistantMessageChunk>of().iterator().next();
        }

        @Override
        public void close() {
            closed = true;
        }

        boolean isClosed() {
            return closed;
        }
    }
}

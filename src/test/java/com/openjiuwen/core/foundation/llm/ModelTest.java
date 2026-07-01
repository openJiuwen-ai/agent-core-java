/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.llm;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessageChunk;
import com.openjiuwen.core.foundation.llm.schema.AudioGenerationResponse;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.ImageGenerationResponse;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.foundation.llm.schema.ProviderType;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import com.openjiuwen.core.foundation.llm.schema.VideoGenerationResponse;
import com.openjiuwen.core.foundation.tool.schema.ToolInfo;
import com.openjiuwen.core.runner.callback.CallbackInfo;
import com.openjiuwen.core.runner.callback.DecoratorFramework;
import com.openjiuwen.core.runner.callback.EventFilter;
import com.openjiuwen.core.runner.callback.LLMCallEvents;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Focused parity tests for the LLM model facade.
 *
 * <p>Mirrors Python's {@code Model} and {@code init_model} in
 * {@code openjiuwen/core/foundation/llm/model.py}.</p>
 */
class ModelTest {

    @AfterEach
    void tearDown() {
        Model.clearCallbackFramework();
    }

    @Test
    void rejectsMissingModelClientConfigLikePythonConstructor() {
        BaseError error = assertThrows(BaseError.class, () -> new Model(null, null));

        assertEquals(StatusCode.MODEL_SERVICE_CONFIG_ERROR, error.getStatus());
        assertEquals("model client config is none", error.getParams().get("error_msg"));
    }

    @Test
    void initModelBuildsConfigsAndDelegatesInvokeThroughRegisteredProvider() {
        RecordingInvoker invoker = new RecordingInvoker(new AssistantMessage("ok"));
        Model.registerInvoker("OpenAI", invoker);

        Model model = Model.init_model(
                "OpenAI",
                "model-a",
                "key",
                "https://example.test",
                0.2f,
                0.3f,
                42,
                7.5f,
                4,
                false,
                Map.of("x-trace", "abc")
        );

        AssistantMessage response = model.invoke("hello").toCompletableFuture().join();

        assertEquals("ok", response.getContentAsString());
        assertEquals("model-a", invoker.modelConfigs.getFirst().getModelName());
        assertEquals("OpenAI", invoker.clientConfigs.getFirst().getClientProvider());
        assertEquals("https://example.test", invoker.clientConfigs.getFirst().getApiBase());
        assertEquals("abc", invoker.clientConfigs.getFirst().getCustomHeaders().get("x-trace"));
        assertInstanceOf(UserMessage.class, invoker.messages.getFirst().getFirst());
    }

    @Test
    void registerInvokerOverridesExistingClientFactoryForSameProvider() {
        Model.registerClientFactory("OpenAI", (clientConfig, requestConfig) ->
                (messages, options) -> CompletableFuture.completedFuture(new AssistantMessage("factory")));
        Model.registerInvoker("OpenAI", (messages, modelConfig, modelClientConfig, options) ->
                CompletableFuture.completedFuture(new AssistantMessage("invoker")));

        Model model = Model.init_model(
                "OpenAI",
                "model-a",
                "key",
                "https://example.test",
                0.2f,
                0.3f,
                42,
                7.5f,
                4,
                false,
                null
        );

        AssistantMessage response = model.invoke("hello").toCompletableFuture().join();

        assertEquals("invoker", response.getContentAsString());
    }

    @Test
    void constructorCreatesBuiltinOpenAiClientWithoutManualRegistration() {
        ModelClientConfig clientConfig = ModelClientConfig.builder()
                .clientProvider(ProviderType.OPEN_AI)
                .apiKey("test-key")
                .apiBase("https://example.test/v1")
                .verifySsl(false)
                .build();
        ModelRequestConfig requestConfig = ModelRequestConfig.builder().modelName("gpt-test").build();

        Model model = new Model(clientConfig, requestConfig);

        assertNotNull(model);
    }

    @Test
    void callbackFrameworkSeesInvokeInputOutputAndCanTransformResult() {
        RecordingModelClient client = new RecordingModelClient();
        RecordingFramework framework = new RecordingFramework();
        framework.transformedOutput = new AssistantMessage("transformed");
        Model.setCallbackFramework(framework);
        Model model = new Model(client);

        AssistantMessage response = model.invoke(
                List.of(new UserMessage("hello")),
                ModelInvokeOptions.builder().model("model-a").temperature(0.7f).build()
        ).toCompletableFuture().join();

        assertEquals("transformed", response.getContentAsString());
        assertEquals(List.of(
                LLMCallEvents.LLM_INVOKE_INPUT,
                "transform:" + LLMCallEvents.LLM_INVOKE_INPUT,
                "transform:" + LLMCallEvents.LLM_INVOKE_OUTPUT,
                LLMCallEvents.LLM_INVOKE_OUTPUT
        ), framework.events);
        assertEquals("model-a", client.invokeOptions.getFirst().getModel());
        assertEquals(0.7f, client.invokeOptions.getFirst().getTemperature());
    }

    @Test
    void streamDelegatesChunksAndTriggersPerItemOutputEvents() {
        RecordingModelClient client = new RecordingModelClient();
        client.streamChunks = List.of(
                AssistantMessageChunk.builder().content("a").build(),
                AssistantMessageChunk.builder().content("b").build()
        );
        RecordingFramework framework = new RecordingFramework();
        Model.setCallbackFramework(framework);
        Model model = new Model(client);

        Iterator<AssistantMessageChunk> iterator = model.stream(List.of(new UserMessage("hello")));
        List<String> chunks = new ArrayList<>();
        while (iterator.hasNext()) {
            chunks.add(iterator.next().getContentAsString());
        }

        assertEquals(List.of("a", "b"), chunks);
        assertEquals(2, framework.events.stream().filter(LLMCallEvents.LLM_STREAM_OUTPUT::equals).count());
    }

    @Test
    void streamCallbackIteratorIsCloseableAndClosesDelegate() throws Exception {
        RecordingModelClient client = new RecordingModelClient();
        CloseableChunkIterator closeableIterator = new CloseableChunkIterator(List.of(
                AssistantMessageChunk.builder().content("a").build()
        ));
        client.streamIterator = closeableIterator;
        Model.setCallbackFramework(new RecordingFramework());
        Model model = new Model(client);

        Iterator<AssistantMessageChunk> iterator = model.stream(List.of(new UserMessage("hello")));

        assertInstanceOf(AutoCloseable.class, iterator);
        ((AutoCloseable) iterator).close();
        assertTrue(closeableIterator.closed);
    }

    @Test
    void releaseAndKvCacheHelpersMirrorUnderlyingClientCapability() {
        RecordingModelClient client = new RecordingModelClient();
        client.kvCacheReleaseSupported = true;
        Model model = new Model(client);

        CompletionStage<Boolean> result = model.release(
                "session-1",
                List.of(new BaseMessage("user", "old")),
                0,
                List.of(ToolInfo.builder().name("search").build()),
                0
        );
        Map<String, Object> kwargs = model.buildKvCacheInvokeKwargs(new SessionStub("session-1"), true);

        assertTrue(result.toCompletableFuture().join());
        assertEquals("session-1", client.releaseSessionId);
        assertEquals(0, client.releaseMessagesReleasedIndex);
        assertEquals("session-1", kwargs.get("session_id"));
        assertEquals(true, kwargs.get("enable_cache_sharing"));
        assertTrue(model.supportsKvCacheRelease());
    }

    @Test
    void nonKvClientReturnsEmptyKvKwargsAndFalseRelease() {
        Model model = new Model(new RecordingModelClient());

        assertFalse(model.supportsKvCacheRelease());
        assertTrue(model.buildKvCacheInvokeKwargs(new SessionStub("ignored"), true).isEmpty());
        assertFalse(model.release("session", List.of(), 0, null, null).toCompletableFuture().join());
    }

    @Test
    void multimodalGenerationMethodsDelegateWithPythonDefaults() {
        RecordingModelClient client = new RecordingModelClient();
        Model model = new Model(client);
        List<UserMessage> messages = List.of(new UserMessage("draw"));

        model.generateImage(messages).toCompletableFuture().join();
        model.generateSpeech(messages).toCompletableFuture().join();
        model.generateVideo(messages).toCompletableFuture().join();

        assertEquals("1664*928", client.lastImageOptions.size());
        assertEquals(1, client.lastImageOptions.n());
        assertTrue(client.lastImageOptions.promptExtend());
        assertFalse(client.lastImageOptions.watermark());
        assertEquals(0, client.lastImageOptions.seed());
        assertEquals("Cherry", client.lastSpeechOptions.voice());
        assertEquals("Auto", client.lastSpeechOptions.languageType());
        assertEquals(5, client.lastVideoOptions.duration());
        assertTrue(client.lastVideoOptions.promptExtend());
        assertFalse(client.lastVideoOptions.watermark());
    }

    /**
     * Invoke-only test double.
     *
     * <p>Mirrors Python's {@code self._client.invoke} collaborator in
     * {@code openjiuwen/core/foundation/llm/model.py}.</p>
     */
    private static final class RecordingInvoker implements Model.ModelInvoker {
        private final AssistantMessage response;
        private final List<List<BaseMessage>> messages = new ArrayList<>();
        private final List<ModelRequestConfig> modelConfigs = new ArrayList<>();
        private final List<ModelClientConfig> clientConfigs = new ArrayList<>();

        private RecordingInvoker(AssistantMessage response) {
            this.response = response;
        }

        @Override
        public CompletionStage<AssistantMessage> invoke(List<BaseMessage> messages, ModelRequestConfig modelConfig,
                                                        ModelClientConfig modelClientConfig,
                                                        ModelInvokeOptions options) {
            this.messages.add(messages);
            this.modelConfigs.add(modelConfig);
            this.clientConfigs.add(modelClientConfig);
            return CompletableFuture.completedFuture(response);
        }
    }

    /**
     * Full client test double.
     *
     * <p>Mirrors Python's {@code self._client} delegate in
     * {@code openjiuwen/core/foundation/llm/model.py}.</p>
     */
    private static final class RecordingModelClient implements Model.ModelClient {
        private final List<ModelInvokeOptions> invokeOptions = new ArrayList<>();
        private List<AssistantMessageChunk> streamChunks = List.of();
        private Iterator<AssistantMessageChunk> streamIterator;
        private boolean kvCacheReleaseSupported;
        private String releaseSessionId;
        private Integer releaseMessagesReleasedIndex;
        private Model.ImageGenerationOptions lastImageOptions;
        private Model.SpeechGenerationOptions lastSpeechOptions;
        private Model.VideoGenerationOptions lastVideoOptions;

        @Override
        public CompletionStage<AssistantMessage> invoke(List<BaseMessage> messages, ModelInvokeOptions options) {
            invokeOptions.add(options);
            return CompletableFuture.completedFuture(new AssistantMessage("ok"));
        }

        @Override
        public Iterator<AssistantMessageChunk> stream(List<BaseMessage> messages, ModelInvokeOptions options) {
            if (streamIterator != null) {
                return streamIterator;
            }
            return streamChunks.iterator();
        }

        @Override
        public CompletionStage<Boolean> release(String sessionId, List<BaseMessage> messages,
                                                Integer messagesReleasedIndex, List<ToolInfo> tools,
                                                Integer toolsReleasedIndex) {
            releaseSessionId = sessionId;
            releaseMessagesReleasedIndex = messagesReleasedIndex;
            return CompletableFuture.completedFuture(kvCacheReleaseSupported);
        }

        @Override
        public boolean supportsKvCacheRelease() {
            return kvCacheReleaseSupported;
        }

        @Override
        public CompletionStage<ImageGenerationResponse> generateImage(List<UserMessage> messages,
                                                                      Model.ImageGenerationOptions options) {
            lastImageOptions = options;
            return CompletableFuture.completedFuture(ImageGenerationResponse.builder().images(List.of("image")).build());
        }

        @Override
        public CompletionStage<AudioGenerationResponse> generateSpeech(List<UserMessage> messages,
                                                                       Model.SpeechGenerationOptions options) {
            lastSpeechOptions = options;
            return CompletableFuture.completedFuture(AudioGenerationResponse.builder().audioUrl("audio").build());
        }

        @Override
        public CompletionStage<VideoGenerationResponse> generateVideo(List<UserMessage> messages,
                                                                      Model.VideoGenerationOptions options) {
            lastVideoOptions = options;
            return CompletableFuture.completedFuture(VideoGenerationResponse.builder().videoUrl("video").build());
        }
    }

    private static final class CloseableChunkIterator implements Iterator<AssistantMessageChunk>, AutoCloseable {
        private final Iterator<AssistantMessageChunk> delegate;
        private boolean closed;

        private CloseableChunkIterator(List<AssistantMessageChunk> chunks) {
            this.delegate = chunks.iterator();
        }

        @Override
        public boolean hasNext() {
            return delegate.hasNext();
        }

        @Override
        public AssistantMessageChunk next() {
            return delegate.next();
        }

        @Override
        public void close() {
            closed = true;
        }
    }

    /**
     * Callback framework test double.
     *
     * <p>Mirrors Python's {@code Runner.callback_framework} in
     * {@code openjiuwen/core/foundation/llm/model.py}.</p>
     */
    private static final class RecordingFramework implements DecoratorFramework {
        private final List<String> events = new ArrayList<>();
        private Object transformedOutput;

        @Override
        public CallbackInfo registerSync(String event, Function<Map<String, Object>, Object> callback, int priority,
                                         boolean once, String namespace, Set<String> tags, List<EventFilter> filters,
                                         Function<Map<String, Object>, Object> rollbackHandler,
                                         Function<Map<String, Object>, Object> errorHandler, int maxRetries,
                                         double retryDelay, Double timeout, String callbackType) {
            return CallbackInfo.builder().callback(callback).priority(priority).build();
        }

        @Override
        public void trigger(String event, Object[] args, Map<String, Object> kwargs) {
            events.add(event);
        }

        @Override
        public Object triggerTransform(String event, Object[] args, Map<String, Object> kwargs) {
            events.add("transform:" + event);
            if (LLMCallEvents.LLM_INVOKE_OUTPUT.equals(event) && transformedOutput != null) {
                return transformedOutput;
            }
            return kwargs;
        }

        @Override
        public Map<String, List<CallbackInfo>> getCallbacks() {
            return new LinkedHashMap<>();
        }
    }

    /**
     * Session id provider test double.
     *
     * <p>Mirrors Python's {@code session.get_session_id()} collaborator in
     * {@code openjiuwen/core/foundation/llm/model.py}.</p>
     */
    public static final class SessionStub {
        private final String sessionId;

        private SessionStub(String sessionId) {
            this.sessionId = sessionId;
        }

        public String getSessionId() {
            return sessionId;
        }
    }
}

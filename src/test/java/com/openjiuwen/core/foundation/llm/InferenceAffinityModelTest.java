/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.llm;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.foundation.llm.output_parsers.BaseOutputParser;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessageChunk;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.foundation.tool.schema.ToolInfo;
import org.junit.jupiter.api.Test;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Focused parity tests for {@link InferenceAffinityModel}.
 *
 * <p>Mirrors Python's {@code InferenceAffinityModel} in
 * {@code openjiuwen/core/foundation/llm/inference_affinity_model.py}.</p>
 */
class InferenceAffinityModelTest {

    @Test
    void constructorRejectsMissingClientConfigLikePython() {
        assertThatThrownBy(() -> new InferenceAffinityModel(null, ModelRequestConfig.builder().build(),
                new RecordingClient()))
                .isInstanceOf(BaseError.class)
                .hasMessageContaining("model client config is none");
    }

    @Test
    void invokeDelegatesAllOptionsAndCacheKwargsToClient() {
        RecordingClient client = new RecordingClient();
        InferenceAffinityModel model = new InferenceAffinityModel(clientConfig(), requestConfig(), client);
        List<BaseMessage> messages = List.of(new BaseMessage("user", "hello"));
        List<ToolInfo> tools = List.of(ToolInfo.builder().name("search").description("Search").build());
        Map<String, Object> kwargs = new LinkedHashMap<>();
        kwargs.put("tracer_record_data", "trace");

        AssistantMessage result = model.invoke(messages, tools, 0.2f, 0.7f, 128, "stop", "override-model",
                null, "session-1", true, kwargs).toCompletableFuture().join();

        assertThat(result.getContent()).isEqualTo("ok");
        assertThat(client.invokeCall.messages).isSameAs(messages);
        assertThat(client.invokeCall.tools).isSameAs(tools);
        assertThat(client.invokeCall.temperature).isEqualTo(0.2f);
        assertThat(client.invokeCall.topP).isEqualTo(0.7f);
        assertThat(client.invokeCall.maxTokens).isEqualTo(128);
        assertThat(client.invokeCall.stop).isEqualTo("stop");
        assertThat(client.invokeCall.model).isEqualTo("override-model");
        assertThat(client.invokeCall.kwargs)
                .containsEntry("tracer_record_data", "trace")
                .containsEntry("session_id", "session-1")
                .containsEntry("enable_cache_sharing", true);
    }

    @Test
    void streamDelegatesAndReturnsClientIterator() {
        RecordingClient client = new RecordingClient();
        InferenceAffinityModel model = new InferenceAffinityModel(clientConfig(), requestConfig(), client);

        Iterator<AssistantMessageChunk> chunks = model.stream("hello", null, null, null, null, null,
                null, null, null, false, null);

        assertThat(chunks).isSameAs(client.chunks);
        assertThat(client.streamCall.messages).isEqualTo("hello");
        assertThat(client.streamCall.kwargs).containsEntry("enable_cache_sharing", false);
    }

    @Test
    void releaseImplementsKvCacheManagerPort() {
        RecordingClient client = new RecordingClient();
        InferenceAffinityModel model = new InferenceAffinityModel(clientConfig(), requestConfig(), client);
        List<BaseMessage> messages = List.of(new BaseMessage("user", "hello"));
        List<ToolInfo> tools = List.of(ToolInfo.builder().name("search").build());

        Boolean released = model.release("session-1", messages, 1, tools, 0).toCompletableFuture().join();

        assertThat(released).isTrue();
        assertThat(client.releaseCall.sessionId).isEqualTo("session-1");
        assertThat(client.releaseCall.messages).isSameAs(messages);
        assertThat(client.releaseCall.messagesReleasedIndex).isEqualTo(1);
        assertThat(client.releaseCall.tools).isSameAs(tools);
        assertThat(client.releaseCall.toolsReleasedIndex).isZero();
    }

    @Test
    void kvCacheHelpersMirrorPythonStaticHelpers() {
        SessionLike session = new SessionLike("session-9");

        Map<String, Object> enabled = InferenceAffinityModel.buildKvCacheInvokeKwargs(session, true);
        Map<String, Object> disabled = InferenceAffinityModel.buildKvCacheInvokeKwargs(session, false);

        assertThat(InferenceAffinityModel.supportsKvCacheRelease()).isTrue();
        assertThat(enabled)
                .containsEntry("session_id", "session-9")
                .containsEntry("enable_cache_sharing", true);
        assertThat(disabled).containsEntry("session_id", "session-9");
        assertThat(disabled).doesNotContainKey("enable_cache_sharing");
    }

    private static ModelClientConfig clientConfig() {
        return ModelClientConfig.builder()
                .clientProvider("InferenceAffinity")
                .apiBase("http://localhost:8000")
                .apiKey("key")
                .build();
    }

    private static ModelRequestConfig requestConfig() {
        return ModelRequestConfig.builder()
                .modelName("test-model")
                .build();
    }

    /**
     * Session test double for KV-cache kwargs.
     *
     * <p>Mirrors Python's {@code session.get_session_id()} collaborator in
     * {@code openjiuwen/core/foundation/llm/inference_affinity_model.py}.</p>
     */
    private record SessionLike(String sessionId) {
        public String getSessionId() {
            return sessionId;
        }
    }

    /**
     * Recording client test double for delegated InferenceAffinity calls.
     *
     * <p>Mirrors Python's {@code InferenceAffinityModel._client} collaborator in
     * {@code openjiuwen/core/foundation/llm/inference_affinity_model.py}.</p>
     */
    private static final class RecordingClient implements InferenceAffinityModel.InferenceAffinityClient {
        private final AssistantMessage assistantMessage = new AssistantMessage("ok");
        private final Iterator<AssistantMessageChunk> chunks = List.<AssistantMessageChunk>of().iterator();
        private ClientCall invokeCall;
        private ClientCall streamCall;
        private ReleaseCall releaseCall;

        @Override
        public CompletionStage<AssistantMessage> invoke(Object messages,
                                                        List<?> tools,
                                                        Float temperature,
                                                        Float topP,
                                                        Integer maxTokens,
                                                        String stop,
                                                        String model,
                                                        BaseOutputParser outputParser,
                                                        String sessionId,
                                                        boolean enableCacheSharing,
                                                        Map<String, Object> kwargs) {
            invokeCall = new ClientCall(messages, tools, temperature, topP, maxTokens, stop, model, outputParser,
                    sessionId, enableCacheSharing, kwargs);
            return CompletableFuture.completedFuture(assistantMessage);
        }

        @Override
        public Iterator<AssistantMessageChunk> stream(Object messages,
                                                      List<?> tools,
                                                      Float temperature,
                                                      Float topP,
                                                      Integer maxTokens,
                                                      String stop,
                                                      String model,
                                                      BaseOutputParser outputParser,
                                                      String sessionId,
                                                      boolean enableCacheSharing,
                                                      Map<String, Object> kwargs) {
            streamCall = new ClientCall(messages, tools, temperature, topP, maxTokens, stop, model, outputParser,
                    sessionId, enableCacheSharing, kwargs);
            return chunks;
        }

        @Override
        public CompletionStage<Boolean> release(String sessionId,
                                                Object messages,
                                                int messagesReleasedIndex,
                                                Object tools,
                                                Integer toolsReleasedIndex,
                                                String model) {
            releaseCall = new ReleaseCall(sessionId, messages, messagesReleasedIndex, tools, toolsReleasedIndex,
                    model);
            return CompletableFuture.completedFuture(true);
        }
    }

    /**
     * Captured invoke/stream call arguments.
     *
     * <p>Mirrors Python's keyword forwarding in
     * {@code openjiuwen/core/foundation/llm/inference_affinity_model.py}.</p>
     */
    private record ClientCall(Object messages, List<?> tools, Float temperature, Float topP, Integer maxTokens,
                              String stop, String model, BaseOutputParser outputParser, String sessionId,
                              boolean enableCacheSharing, Map<String, Object> kwargs) {
    }

    /**
     * Captured release call arguments.
     *
     * <p>Mirrors Python's {@code InferenceAffinityModel.release} arguments in
     * {@code openjiuwen/core/foundation/llm/inference_affinity_model.py}.</p>
     */
    private record ReleaseCall(String sessionId, Object messages, int messagesReleasedIndex, Object tools,
                               Integer toolsReleasedIndex, String model) {
    }
}

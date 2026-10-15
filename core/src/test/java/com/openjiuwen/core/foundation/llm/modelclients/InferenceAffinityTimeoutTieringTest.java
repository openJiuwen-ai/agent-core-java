/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.llm.modelclients;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.foundation.llm.ModelInvokeOptions;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessageChunk;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.foundation.llm.schema.ProviderType;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;

import org.junit.jupiter.api.Test;

import java.util.Iterator;
import java.util.List;

/**
 * UT-I group for FEAT-058: the inference-affinity stack exposes the same tiered timeout
 * semantics, defaults, and error taxonomy as the primary stack.
 *
 * @since 2026-10-09
 */
class InferenceAffinityTimeoutTieringTest {
    @Test
    void affinityConnectTimeoutAligned() {
        // Given an affinity client pointing at a black-hole address with a 5s connect window
        InferenceAffinityModelClient client = client(ModelClientConfig.builder()
                .clientProvider(ProviderType.INFERENCE_AFFINITY)
                .apiKey("sk-test")
                .apiBase("http://10.255.255.1:81")
                .verifySsl(false)
                .connectTimeoutSeconds(5.0D)
                .build());

        // When streaming
        long started = System.nanoTime();
        Throwable error = catchThrowable(() -> client.stream(
                List.of(new UserMessage("hello")), ModelInvokeOptions.builder().build()));
        long elapsedMillis = (System.nanoTime() - started) / 1_000_000L;

        // Then the connect phase fails fast with the same taxonomy as the primary stack
        BaseError typed = unwrap(error);
        assertThat(typed.getStatus()).isEqualTo(StatusCode.MODEL_CALL_TIMEOUT);
        assertThat(typed.getParams().get("phase")).isEqualTo("BEFORE_CONNECT");
        assertThat(elapsedMillis).isLessThan(15_000L);
    }

    @Test
    void affinityIdleTimeoutSameSemantics() {
        // Given a stub that sends two segments then goes silent
        try (TieredTimeoutStubServer stub = TieredTimeoutStubServer.start(
                new TieredTimeoutStubServer.StubScript()
                        .events(List.of(
                                TieredTimeoutStubServer.sseContent("a"),
                                TieredTimeoutStubServer.sseContent("b")))
                        .silentAfterEvents())) {
            InferenceAffinityModelClient client = client(baseConfig(stub.baseUrl()).build());
            ModelInvokeOptions options = ModelInvokeOptions.builder().timeout(2.0F).build();

            // When the peer stays silent beyond the idle window
            Iterator<AssistantMessageChunk> iterator = client.stream(
                    List.of(new UserMessage("hello")), options);
            assertThat(iterator.next()).isNotNull();
            assertThat(iterator.next()).isNotNull();
            Throwable error = catchThrowable(iterator::hasNext);

            // Then the dead link is detected as an after-first-segment idle timeout
            BaseError typed = unwrap(error);
            assertThat(typed.getStatus()).isEqualTo(StatusCode.MODEL_CALL_TIMEOUT);
            assertThat(typed.getParams().get("phase")).isEqualTo("AFTER_FIRST_SEGMENT");
        }
    }

    @Test
    void affinityErrorSurfaceSameTaxonomy() {
        // Given a stub hanging before the first segment
        try (TieredTimeoutStubServer stub = TieredTimeoutStubServer.start(
                new TieredTimeoutStubServer.StubScript().silentAfterEvents())) {
            InferenceAffinityModelClient client = client(baseConfig(stub.baseUrl()).build());
            ModelInvokeOptions options = ModelInvokeOptions.builder().timeout(2.0F).build();

            // When the idle timeout fires
            Iterator<AssistantMessageChunk> iterator = client.stream(
                    List.of(new UserMessage("hello")), options);
            Throwable error = catchThrowable(iterator::hasNext);

            // Then the error taxonomy matches the primary stack exactly
            BaseError typed = unwrap(error);
            assertThat(typed.getStatus()).isEqualTo(StatusCode.MODEL_CALL_TIMEOUT);
            assertThat(typed.getParams().get("phase")).isEqualTo("BEFORE_FIRST_SEGMENT");
        }
    }

    @Test
    void affinityPerCallOverride() {
        // Given a stub with a 3s gap between segments
        try (TieredTimeoutStubServer stub = TieredTimeoutStubServer.start(
                new TieredTimeoutStubServer.StubScript()
                        .events(List.of(
                                TieredTimeoutStubServer.sseContent("a"),
                                TieredTimeoutStubServer.sseContent("b"),
                                TieredTimeoutStubServer.sseDone()))
                        .gapMillis(3_000L))) {
            InferenceAffinityModelClient client = client(baseConfig(stub.baseUrl()).build());

            // When this call relaxes the idle window to 8s
            ModelInvokeOptions options = ModelInvokeOptions.builder().timeout(8.0F).build();
            Iterator<AssistantMessageChunk> iterator = client.stream(
                    List.of(new UserMessage("hello")), options);

            // Then the stream survives the gap and completes
            int chunks = 0;
            while (iterator.hasNext()) {
                iterator.next();
                chunks++;
            }
            assertThat(chunks).isEqualTo(2);
        }
    }

    @Test
    void affinityBlockingHeaderWaitProtected() {
        // Given a stub that delays headers beyond the 2s legacy header-wait override,
        // and a single-attempt retry budget
        try (TieredTimeoutStubServer stub = TieredTimeoutStubServer.start(
                new TieredTimeoutStubServer.StubScript()
                        .headerDelayMillis(4_000L)
                        .contentType("application/json")
                        .events(List.of(TieredTimeoutStubServer.blockingBody("late"))))) {
            InferenceAffinityModelClient client = client(baseConfig(stub.baseUrl())
                    .maxRetries(1)
                    .timeout(2.0D)
                    .build());

            // When invoking blocking
            Throwable error = catchThrowable(() -> client.invoke(
                    List.of(new UserMessage("hello")), ModelInvokeOptions.builder().build())
                    .toCompletableFuture().join());

            // Then the header wait is protected by a before-first-segment timeout
            BaseError typed = unwrap(error);
            assertThat(typed.getStatus()).isEqualTo(StatusCode.MODEL_CALL_TIMEOUT);
            assertThat(typed.getParams().get("phase")).isEqualTo("BEFORE_FIRST_SEGMENT");
        }
    }

    private static ModelClientConfig.ModelClientConfigBuilder baseConfig(String baseUrl) {
        return ModelClientConfig.builder()
                .clientProvider(ProviderType.INFERENCE_AFFINITY)
                .apiKey("sk-test")
                .apiBase(baseUrl)
                .verifySsl(false)
                .maxRetries(1);
    }

    private static InferenceAffinityModelClient client(ModelClientConfig config) {
        return new InferenceAffinityModelClient(
                ModelRequestConfig.builder().modelName("affinity-test").build(), config);
    }

    private static BaseError unwrap(Throwable error) {
        assertThat(error).isNotNull();
        Throwable cursor = error;
        while (cursor != null && !(cursor instanceof BaseError)) {
            cursor = cursor.getCause();
        }
        assertThat(cursor).isInstanceOf(BaseError.class);
        return (BaseError) cursor;
    }
}

/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.llm.modelclients;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.common.security.UrlUtils;
import com.openjiuwen.core.foundation.llm.ModelInvokeOptions;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessageChunk;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.foundation.llm.schema.ProviderType;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * UT-M group for FEAT-058: tiered timeout behavior of the primary stack
 * ({@link OpenAIModelClient}) against a scriptable stub endpoint.
 *
 * @since 2026-10-09
 */
class OpenAIModelTimeoutTieringTest {
    private static final double TOLERANCE = 0.0001D;

    @BeforeAll
    static void disableCorporateProxy() throws Exception {
        // The connect-timeout case dials a black-hole address; a configured corporate
        // proxy would answer 407 instead of letting the TCP connect window expire.
        setEnvReader(name -> null);
    }

    @AfterAll
    static void restoreEnvReader() throws Exception {
        Method method = UrlUtils.class.getDeclaredMethod("resetEnvReaderForTests");
        method.setAccessible(true);
        method.invoke(null);
    }

    private static void setEnvReader(Function<String, String> reader) throws Exception {
        Method method = UrlUtils.class.getDeclaredMethod("setEnvReaderForTests", Function.class);
        method.setAccessible(true);
        method.invoke(null, reader);
    }

    @Test
    void connectTimeoutFailsFast() {
        // Given a client pointing at a black-hole address with a 5s connect window
        OpenAIModelClient client = client("http://10.255.255.1:81", ModelClientConfig.builder()
                .maxRetries(0)
                .connectTimeoutSeconds(5.0D)
                .build());

        // When invoking
        long started = System.nanoTime();
        Throwable error = catchThrowable(() -> client.invoke(
                List.of(new UserMessage("hello")), ModelInvokeOptions.builder().build())
                .toCompletableFuture().join());
        long elapsedMillis = (System.nanoTime() - started) / 1_000_000L;

        // Then the call fails as a connect-phase timeout well before any read window
        BaseError typed = unwrap(error);
        assertThat(typed.getStatus()).isEqualTo(StatusCode.MODEL_CALL_TIMEOUT);
        assertThat(typed.getParams().get("phase")).isEqualTo("BEFORE_CONNECT");
        assertThat(elapsedMillis).isLessThan(15_000L);
    }

    @Test
    void totalTimerReleasedAfterHeaders() {
        // Given a stub that answers headers quickly but writes the stream over ~4.5s total,
        // and a client whose header-wait window is only 2s (legacy explicit override)
        try (TieredTimeoutStubServer stub = TieredTimeoutStubServer.start(scriptedSlowStream())) {
            ModelClientConfig config = baseConfig(stub.baseUrl())
                    .timeout(2.0D)
                    .build();
            OpenAIModelClient client = client(config);
            ModelInvokeOptions options = ModelInvokeOptions.builder()
                    .timeout(8.0F)
                    .build();

            // When streaming
            Iterator<AssistantMessageChunk> iterator = client.stream(
                    List.of(new UserMessage("hello")), options);

            // Then the whole stream is read to the end although total time exceeds the
            // header-wait window, proving no total-duration cap remains
            int chunks = drain(iterator);
            assertThat(chunks).isEqualTo(3);
        }
    }

    @Test
    void idleTimeoutBetweenSegments() {
        // Given a stub that sends two segments then goes silent
        try (TieredTimeoutStubServer stub = TieredTimeoutStubServer.start(
                new TieredTimeoutStubServer.StubScript()
                        .events(List.of(
                                TieredTimeoutStubServer.sseContent("a"),
                                TieredTimeoutStubServer.sseContent("b")))
                        .silentAfterEvents())) {
            OpenAIModelClient client = client(baseConfig(stub.baseUrl()).build());
            ModelInvokeOptions options = ModelInvokeOptions.builder().timeout(2.0F).build();

            // When the peer stays silent beyond the idle window
            long started = System.nanoTime();
            Iterator<AssistantMessageChunk> iterator = client.stream(
                    List.of(new UserMessage("hello")), options);
            assertThat(iterator.next()).isNotNull();
            assertThat(iterator.next()).isNotNull();
            Throwable error = catchThrowable(iterator::hasNext);
            long elapsedMillis = (System.nanoTime() - started) / 1_000_000L;

            // Then the dead link is detected inside the window, not via the peer hanging up
            BaseError typed = unwrap(error);
            assertThat(typed.getStatus()).isEqualTo(StatusCode.MODEL_CALL_TIMEOUT);
            assertThat(typed.getParams().get("phase")).isEqualTo("AFTER_FIRST_SEGMENT");
            assertThat(elapsedMillis).isLessThan(10_000L);
        }
    }

    @Test
    void idleTimeoutBeforeFirstSegment() {
        // Given a stub that sends headers but never a first segment
        try (TieredTimeoutStubServer stub = TieredTimeoutStubServer.start(
                new TieredTimeoutStubServer.StubScript().silentAfterEvents())) {
            OpenAIModelClient client = client(baseConfig(stub.baseUrl()).build());
            ModelInvokeOptions options = ModelInvokeOptions.builder().timeout(2.0F).build();

            // When streaming
            long started = System.nanoTime();
            Iterator<AssistantMessageChunk> iterator = client.stream(
                    List.of(new UserMessage("hello")), options);
            Throwable error = catchThrowable(iterator::hasNext);
            long elapsedMillis = (System.nanoTime() - started) / 1_000_000L;

            // Then the silence is a before-first-segment idle timeout inside the window
            BaseError typed = unwrap(error);
            assertThat(typed.getStatus()).isEqualTo(StatusCode.MODEL_CALL_TIMEOUT);
            assertThat(typed.getParams().get("phase")).isEqualTo("BEFORE_FIRST_SEGMENT");
            assertThat(elapsedMillis).isLessThan(10_000L);
        }
    }

    @Test
    void callerCancelNotCountedAsTimeout() {
        // Given a slowly dripping stream
        try (TieredTimeoutStubServer stub = TieredTimeoutStubServer.start(
                new TieredTimeoutStubServer.StubScript()
                        .events(List.of(
                                TieredTimeoutStubServer.sseContent("a"),
                                TieredTimeoutStubServer.sseContent("b"),
                                TieredTimeoutStubServer.sseDone()))
                        .gapMillis(1_500L))) {
            OpenAIModelClient client = client(baseConfig(stub.baseUrl()).build());
            ModelInvokeOptions options = ModelInvokeOptions.builder().timeout(8.0F).build();

            // When the consumer reads one chunk then cancels by closing the iterator
            Throwable error = catchThrowable(() -> {
                Iterator<AssistantMessageChunk> iterator = client.stream(
                        List.of(new UserMessage("hello")), options);
                assertThat(iterator.next()).isNotNull();
                if (iterator instanceof AutoCloseable closeable) {
                    closeable.close();
                }
            });

            // Then no timeout error surfaces
            assertThat(error).isNull();
        }
    }

    @Test
    void blockingCallHeaderWaitProtected() {
        // Given a stub that delays response headers beyond the 2s legacy header-wait override
        try (TieredTimeoutStubServer stub = TieredTimeoutStubServer.start(
                new TieredTimeoutStubServer.StubScript()
                        .headerDelayMillis(4_000L)
                        .contentType("application/json")
                        .events(List.of(TieredTimeoutStubServer.blockingBody("late"))))) {
            OpenAIModelClient client = client(baseConfig(stub.baseUrl())
                    .maxRetries(0)
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

    @Test
    void blockingCallLongBodyReadsToEnd() {
        // Given a stub that answers headers fast but writes the body in slow slices
        try (TieredTimeoutStubServer stub = TieredTimeoutStubServer.start(
                new TieredTimeoutStubServer.StubScript()
                        .contentType("application/json")
                        .events(List.of(
                                "{\"id\":\"stub\",\"choices\":[{\"message\":{\"role\":\"assistant\",",
                                "\"content\":\"slow-ok\"},\"finish_reason\":\"stop\"}]",
                                ",\"usage\":{\"prompt_tokens\":1,\"completion_tokens\":1,\"total_tokens\":2}}"))
                        .gapMillis(1_000L))) {
            OpenAIModelClient client = client(baseConfig(stub.baseUrl()).build());
            ModelInvokeOptions options = ModelInvokeOptions.builder().timeout(8.0F).build();

            // When invoking blocking
            Object result = client.invoke(List.of(new UserMessage("hello")), options)
                    .toCompletableFuture().join();

            // Then the slow body is read to the end without any total cap
            assertThat(result).isNotNull();
        }
    }

    @Test
    void timeoutErrorsProgrammaticallyDistinguishable() {
        // Given a stub hanging before the first segment
        try (TieredTimeoutStubServer stub = TieredTimeoutStubServer.start(
                new TieredTimeoutStubServer.StubScript().silentAfterEvents())) {
            OpenAIModelClient client = client(baseConfig(stub.baseUrl()).build());
            ModelInvokeOptions options = ModelInvokeOptions.builder().timeout(2.0F).build();

            // When the idle timeout fires
            Iterator<AssistantMessageChunk> iterator = client.stream(
                    List.of(new UserMessage("hello")), options);
            Throwable error = catchThrowable(iterator::hasNext);

            // Then the error surface carries phase, effective timeout, layer, and stream flag
            BaseError typed = unwrap(error);
            assertThat(typed.getStatus()).isEqualTo(StatusCode.MODEL_CALL_TIMEOUT);
            Object details = typed.getDetails();
            assertThat(details).isInstanceOf(Map.class);
            Map<?, ?> detailMap = (Map<?, ?>) details;
            assertThat(detailMap.get("phase")).isEqualTo("BEFORE_FIRST_SEGMENT");
            assertThat(detailMap.get("layer")).isEqualTo("stream_idle");
            assertThat(detailMap.get("stream")).isEqualTo(true);
            Object timeoutSeconds = detailMap.get("timeout_seconds");
            if (!(timeoutSeconds instanceof Number timeoutNumber)) {
                throw new AssertionError("timeout_seconds is not a Number: " + timeoutSeconds);
            }
            assertThat(timeoutNumber.doubleValue())
                    .isCloseTo(2.0D, org.assertj.core.data.Offset.offset(TOLERANCE));
        }
    }

    @Test
    void blockingBodyMultiByteCharSurvivesReadBoundary() {
        // Given a blocking body longer than one 8KB read, with a three-byte character
        // straddling the read boundary
        String marker = "中";
        String prefix = "{\"id\":\"stub\",\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"";
        String suffix = "\"},\"finish_reason\":\"stop\"}],\"usage\":{\"prompt_tokens\":1,"
                + "\"completion_tokens\":1,\"total_tokens\":2}}";
        int prefixBytes = prefix.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
        int padding = 8191 - prefixBytes;
        String body = prefix + "x".repeat(padding) + marker + "tail" + suffix;
        try (TieredTimeoutStubServer stub = TieredTimeoutStubServer.start(
                new TieredTimeoutStubServer.StubScript()
                        .contentType("application/json")
                        .events(List.of(body)))) {
            OpenAIModelClient client = client(baseConfig(stub.baseUrl()).build());

            // When invoking blocking (body spans multiple reads)
            AssistantMessage response = client.invoke(
                    List.of(new UserMessage("hello")), ModelInvokeOptions.builder().build())
                    .toCompletableFuture().join();

            // Then the straddled character decodes intact with no replacement character
            String content = String.valueOf(response.getContent());
            assertThat(content).contains(marker).doesNotContain("�");
            assertThat(content).endsWith("tail");
        }
    }

    @Test
    void slowConsumerNotKilledByWatchdog() {
        // Given a fast stub and a consumer slower than the per-call idle window
        try (TieredTimeoutStubServer stub = TieredTimeoutStubServer.start(scriptedLongStream())) {
            OpenAIModelClient client = client(baseConfig(stub.baseUrl()).build());
            ModelInvokeOptions options = ModelInvokeOptions.builder().timeout(1.0F).build();

            // When the consumer sleeps between iterations beyond the idle window
            Throwable error = catchThrowable(() -> {
                Iterator<AssistantMessageChunk> iterator = client.stream(
                        List.of(new UserMessage("hello")), options);
                while (iterator.hasNext()) {
                    assertThat(iterator.next()).isNotNull();
                    Thread.sleep(1_500L);
                }
            });

            // Then the consumer-paced iteration is never mistaken for peer silence
            assertThat(error).isNull();
        }
    }

    private static TieredTimeoutStubServer.StubScript scriptedSlowStream() {
        // Slow stub: ~4.5s total stream, used to prove no total-duration cap remains.
        return scriptedStream(1_500L);
    }

    private static TieredTimeoutStubServer.StubScript scriptedLongStream() {
        // Fast stub: data is always ready, so any blocking read is attributable to the slow
        // consumer rather than peer silence.
        return scriptedStream(50L);
    }

    private static TieredTimeoutStubServer.StubScript scriptedStream(long gapMillis) {
        return new TieredTimeoutStubServer.StubScript()
                .events(List.of(
                        TieredTimeoutStubServer.sseContent("a"),
                        TieredTimeoutStubServer.sseContent("b"),
                        TieredTimeoutStubServer.sseContent("c"),
                        TieredTimeoutStubServer.sseDone()))
                .gapMillis(gapMillis);
    }

    private static ModelClientConfig.ModelClientConfigBuilder baseConfig(String baseUrl) {
        return ModelClientConfig.builder()
                .clientProvider(ProviderType.OPEN_AI)
                .apiKey("sk-test")
                .apiBase(baseUrl)
                .verifySsl(false)
                .maxRetries(0);
    }

    private static OpenAIModelClient client(ModelClientConfig config) {
        return new OpenAIModelClient(
                ModelRequestConfig.builder().modelName("gpt-test").build(), config);
    }

    private static OpenAIModelClient client(String baseUrl, ModelClientConfig config) {
        ModelClientConfig effective = ModelClientConfig.builder()
                .clientProvider(ProviderType.OPEN_AI)
                .apiKey("sk-test")
                .apiBase(baseUrl)
                .verifySsl(config.isVerifySsl())
                .maxRetries(config.getMaxRetries())
                .connectTimeoutSeconds(config.getConnectTimeoutSeconds())
                .streamIdleSeconds(config.getStreamIdleSeconds())
                .build();
        return client(effective);
    }

    private static int drain(Iterator<AssistantMessageChunk> iterator) {
        int count = 0;
        while (iterator.hasNext()) {
            iterator.next();
            count++;
        }
        return count;
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

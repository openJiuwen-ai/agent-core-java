/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.llm.model_clients;

import com.openjiuwen.core.common.logging.LogManager;
import com.openjiuwen.core.common.logging.LoggerProtocol;
import com.openjiuwen.core.foundation.llm.ModelRetryEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import javax.net.ssl.SSLSession;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.DoubleSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

class OpenAIRetryingHttpClientTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-07-13T08:00:00Z"), ZoneOffset.UTC);
    private static final String RETRY_WARNING = "OpenAI request will retry. {}";

    private RecordingLogger recordingLogger;

    @BeforeEach
    void setUpRetryLogger() {
        LogManager.reset();
        recordingLogger = new RecordingLogger();
        LogManager.registerLogger("llm", recordingLogger);
    }

    @AfterEach
    void resetRetryLogger() {
        LogManager.reset();
    }

    @Test
    void successfulResponseIsSentOnlyOnce() throws Exception {
        RecordingSleeper sleeper = new RecordingSleeper();
        OpenAIRetryingHttpClient client = client(3, sleeper);
        List<Integer> retryCounts = new ArrayList<>();

        HttpResponse<String> result = client.send(retryCount -> {
            retryCounts.add(retryCount);
            return response(200);
        });

        assertThat(result.statusCode()).isEqualTo(200);
        assertThat(retryCounts).containsExactly(0);
        assertThat(sleeper.delays).isEmpty();
    }

    @Test
    void publishesHttpRetryEventAfterDelayIsResolved() throws Exception {
        List<ModelRetryEvent> events = new ArrayList<>();
        OpenAIRetryingHttpClient client = client(1, new RecordingSleeper());

        client.send(retryCount -> response(retryCount == 0 ? 500 : 200), events::add);

        assertThat(events).containsExactly(new ModelRetryEvent(
                1,
                1,
                500,
                null,
                Duration.ofMillis(500),
                "exponential_backoff"));
    }

    @Test
    void publishesIoExceptionRetryEvent() throws Exception {
        List<ModelRetryEvent> events = new ArrayList<>();
        OpenAIRetryingHttpClient client = client(1, new RecordingSleeper());

        client.send(retryCount -> {
            if (retryCount == 0) {
                throw new IOException("connection failed");
            }
            return response(200);
        }, events::add);

        assertThat(events).containsExactly(new ModelRetryEvent(
                1,
                1,
                null,
                IOException.class.getName(),
                Duration.ofMillis(500),
                "exponential_backoff"));
    }

    @Test
    void retryAfterEventUsesServerDelaySource() throws Exception {
        List<ModelRetryEvent> events = new ArrayList<>();
        OpenAIRetryingHttpClient client = client(1, new RecordingSleeper());

        client.send(retryCount -> retryCount == 0
                ? response(429, Map.of("retry-after", "1.25"), "retry")
                : response(200), events::add);

        assertThat(events).singleElement().satisfies(event -> {
            assertThat(event.delay()).isEqualTo(Duration.ofMillis(1250));
            assertThat(event.delaySource()).isEqualTo("retry_after");
        });
    }

    @Test
    void publishesEventAndWarningBeforeSleeping() throws Exception {
        List<ModelRetryEvent> events = new ArrayList<>();
        AtomicInteger sleeps = new AtomicInteger();
        OpenAIRetryingHttpClient client = new OpenAIRetryingHttpClient(
                1,
                delay -> {
                    sleeps.incrementAndGet();
                    assertThat(events).singleElement().satisfies(event -> assertThat(event.delay()).isEqualTo(delay));
                    assertThat(retryWarnings()).singleElement().satisfies(warning ->
                            assertThat(((Map<?, ?>) warning.arguments()[0]).get("delay_ms")).isEqualTo(500L));
                },
                FIXED_CLOCK,
                () -> 0.0D);

        client.send(retryCount -> response(retryCount == 0 ? 500 : 200), events::add);

        assertThat(sleeps).hasValue(1);
    }

    @Test
    void logsWhitelistedWarningWhenRequestWillRetry() throws Exception {
        OpenAIRetryingHttpClient client = client(1, new RecordingSleeper());

        client.send(retryCount -> response(retryCount == 0 ? 500 : 200));

        assertThat(retryWarnings()).singleElement().satisfies(warning -> {
            assertThat(warning.message()).isEqualTo(RETRY_WARNING);
            assertThat(warning.arguments()).hasSize(1);
            assertThat(warning.arguments()[0]).isInstanceOf(Map.class);
            assertThat(((Map<?, ?>) warning.arguments()[0]).keySet().stream().map(String::valueOf).toList())
                    .containsExactlyInAnyOrder(
                    "retry_count", "max_retries", "status_code", "delay_ms", "delay_source");
            assertThat(((Map<?, ?>) warning.arguments()[0]).get("delay_ms")).isEqualTo(500L);
        });
    }

    @Test
    void doesNotLogRetryWarningForNonRetryableExhaustedOrInterruptedRequests() throws Exception {
        client(1, new RecordingSleeper()).send(retryCount -> response(400));
        client(0, new RecordingSleeper()).send(retryCount -> response(500));

        Thread.interrupted();
        try {
            catchThrowableOfType(() -> client(1, new RecordingSleeper()).send(retryCount -> {
                throw new InterruptedException("request interrupted");
            }), InterruptedException.class);
        } finally {
            Thread.interrupted();
        }

        assertThat(retryWarnings()).isEmpty();
    }

    @Test
    void retryWarningsNeverContainRequestResponseOrCredentialData() throws Exception {
        String apiKey = "sk-sensitive-api-key";
        String authorization = "Bearer sensitive-authorization";
        String prompt = "sensitive prompt text";
        String requestBody = "sensitive request body";
        String responseBody = "sensitive response body";

        client(1, new RecordingSleeper()).send(retryCount -> retryCount == 0
                ? response(500, Map.of(), prompt + requestBody + responseBody)
                : response(200));
        client(1, new RecordingSleeper()).send(retryCount -> {
            if (retryCount == 0) {
                throw new IOException(apiKey + authorization + prompt + requestBody + responseBody);
            }
            return response(200);
        });

        assertThat(retryWarnings()).hasSize(2).allSatisfy(warning -> {
            assertThat(warning.message()).isEqualTo(RETRY_WARNING);
            assertThat(((Map<?, ?>) warning.arguments()[0]).keySet()).allMatch(java.util.Set.of(
                    "retry_count", "max_retries", "status_code", "exception_type", "delay_ms", "delay_source")::contains);
        });
        String logged = recordingLogger.warnings.toString();
        assertThat(logged).doesNotContain(apiKey, authorization, prompt, requestBody, responseBody);
    }

    @Test
    void publishesCompleteHttpRetryRangeWithoutEventAfterBudgetIsExhausted() throws Exception {
        List<ModelRetryEvent> events = new ArrayList<>();
        AtomicInteger attempts = new AtomicInteger();

        HttpResponse<String> response = client(3, new RecordingSleeper()).send(retryCount -> {
            attempts.incrementAndGet();
            return response(500);
        }, events::add);

        assertThat(response.statusCode()).isEqualTo(500);
        assertThat(attempts).hasValue(4);
        assertThat(events).extracting(ModelRetryEvent::retryCount).containsExactly(1, 2, 3);
        assertThat(events).extracting(ModelRetryEvent::maxRetries).containsOnly(3);
        assertThat(retryWarnings()).hasSize(3);
    }

    @Test
    void publishesCompleteIoRetryRangeWithoutEventForFinalException() {
        List<ModelRetryEvent> events = new ArrayList<>();
        AtomicInteger attempts = new AtomicInteger();

        IOException thrown = catchThrowableOfType(() -> client(3, new RecordingSleeper()).send(retryCount -> {
            attempts.incrementAndGet();
            throw new IOException("failure " + retryCount);
        }, events::add), IOException.class);

        assertThat(thrown).hasMessage("failure 3");
        assertThat(attempts).hasValue(4);
        assertThat(events).extracting(ModelRetryEvent::retryCount).containsExactly(1, 2, 3);
        assertThat(events).extracting(ModelRetryEvent::maxRetries).containsOnly(3);
        assertThat(retryWarnings()).hasSize(3);
    }

    @Test
    void doesNotPublishForNonRetryableOrExhaustedResponses() throws Exception {
        List<ModelRetryEvent> nonRetryableEvents = new ArrayList<>();
        List<ModelRetryEvent> exhaustedEvents = new ArrayList<>();

        client(1, new RecordingSleeper()).send(retryCount -> response(400), nonRetryableEvents::add);
        client(0, new RecordingSleeper()).send(retryCount -> response(500), exhaustedEvents::add);

        assertThat(nonRetryableEvents).isEmpty();
        assertThat(exhaustedEvents).isEmpty();
    }

    @Test
    void listenerFailureDoesNotInterruptRetry() throws Exception {
        AtomicInteger attempts = new AtomicInteger();
        AtomicInteger listenerCalls = new AtomicInteger();
        OpenAIRetryingHttpClient client = client(1, new RecordingSleeper());

        HttpResponse<String> result = client.send(retryCount -> {
            attempts.incrementAndGet();
            return response(retryCount == 0 ? 500 : 200);
        }, event -> {
            listenerCalls.incrementAndGet();
            throw new IllegalStateException("listener failed");
        });

        assertThat(result.statusCode()).isEqualTo(200);
        assertThat(attempts).hasValue(2);
        assertThat(listenerCalls).hasValue(1);
    }

    @Test
    void maxRetriesZeroDoesNotSendSecondAttempt() throws Exception {
        assertThat(retryCountsForExhaustedResponse(0)).containsExactly(0);
    }

    @Test
    void maxRetriesAllowsInitialAttemptPlusConfiguredRetries() throws Exception {
        assertThat(retryCountsForExhaustedResponse(3)).containsExactly(0, 1, 2, 3);
    }

    @Test
    void negativeMaxRetriesIsTreatedAsZero() throws Exception {
        assertThat(retryCountsForExhaustedResponse(-2)).containsExactly(0);
    }

    @ParameterizedTest
    @ValueSource(ints = {408, 409, 429, 500, 503, 599})
    void retriesDefaultRetryableStatuses(int statusCode) throws Exception {
        assertThat(retryCountsForResponse(statusCode, Map.of())).containsExactly(0, 1);
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 401, 404, 422, 499})
    void doesNotRetryOtherClientErrorStatuses(int statusCode) throws Exception {
        assertThat(retryCountsForResponse(statusCode, Map.of())).containsExactly(0);
    }

    @Test
    void trueRetryHeaderOverridesNonRetryableStatusIgnoringCase() throws Exception {
        assertThat(retryCountsForResponse(400, Map.of("X-Should-Retry", "TrUe")))
                .containsExactly(0, 1);
    }

    @Test
    void falseRetryHeaderOverridesRetryableStatusIgnoringCase() throws Exception {
        assertThat(retryCountsForResponse(500, Map.of("x-should-retry", "FaLsE")))
                .containsExactly(0);
    }

    @Test
    void retriesIoExceptionAndThrowsLastFailureAfterExhaustion() {
        OpenAIRetryingHttpClient client = client(2, new RecordingSleeper());
        List<Integer> retryCounts = new ArrayList<>();
        IOException lastFailure = new IOException("third failure");

        IOException thrown = catchThrowableOfType(() -> client.send(retryCount -> {
            retryCounts.add(retryCount);
            if (retryCount == 2) {
                throw lastFailure;
            }
            throw new IOException("failure " + retryCount);
        }), IOException.class);

        assertThat(thrown).isSameAs(lastFailure);
        assertThat(retryCounts).containsExactly(0, 1, 2);
    }

    @Test
    void interruptedRequestIsNotRetriedAndRestoresInterruptFlag() {
        Thread.interrupted();
        AtomicInteger attempts = new AtomicInteger();
        List<ModelRetryEvent> events = new ArrayList<>();
        try {
            OpenAIRetryingHttpClient client = client(3, new RecordingSleeper());

            InterruptedException thrown = catchThrowableOfType(() -> client.send(retryCount -> {
                attempts.incrementAndGet();
                throw new InterruptedException("request interrupted");
            }, events::add), InterruptedException.class);

            assertThat(thrown).hasMessage("request interrupted");
            assertThat(attempts).hasValue(1);
            assertThat(events).isEmpty();
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void interruptedWaitKeepsPublishedRetryEventAndWarningButStopsNextAttempt() {
        Thread.interrupted();
        AtomicInteger attempts = new AtomicInteger();
        List<ModelRetryEvent> events = new ArrayList<>();
        try {
            OpenAIRetryingHttpClient client = new OpenAIRetryingHttpClient(
                    3,
                    delay -> {
                        throw new InterruptedException("wait interrupted");
                    },
                    FIXED_CLOCK,
                    () -> 0.0D);

            InterruptedException thrown = catchThrowableOfType(() -> client.send(retryCount -> {
                attempts.incrementAndGet();
                return response(500);
            }, events::add), InterruptedException.class);

            assertThat(thrown).hasMessage("wait interrupted");
            assertThat(attempts).hasValue(1);
            assertThat(events).singleElement().satisfies(event -> {
                assertThat(event.retryCount()).isEqualTo(1);
                assertThat(event.maxRetries()).isEqualTo(3);
                assertThat(event.statusCode()).isEqualTo(500);
                assertThat(event.delay()).isEqualTo(Duration.ofMillis(500));
            });
            assertThat(retryWarnings()).singleElement().satisfies(warning ->
                    assertThat(((Map<?, ?>) warning.arguments()[0]).get("delay_ms")).isEqualTo(500L));
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void retryAfterMillisecondsTakesPriorityOverRetryAfter() throws Exception {
        assertThat(delaysForHeaders(Map.of("retry-after-ms", "1500", "retry-after", "9"), 1, () -> 0.0D))
                .containsExactly(Duration.ofMillis(1500));
    }

    @Test
    void parsesFractionalRetryAfterSeconds() throws Exception {
        assertThat(delaysForHeaders(Map.of("retry-after", "1.25"), 1, () -> 0.0D))
                .containsExactly(Duration.ofMillis(1250));
    }

    @Test
    void roundsHighPrecisionRetryAfterSecondsUpToAvoidEarlyRetry() throws Exception {
        assertThat(delaysForHeaders(Map.of("retry-after", "1.0000000001"), 1, () -> 0.0D))
                .containsExactly(Duration.ofSeconds(1).plusNanos(1));
    }

    @Test
    void roundsHighPrecisionRetryAfterMillisecondsUpToAvoidEarlyRetry() throws Exception {
        assertThat(delaysForHeaders(Map.of("retry-after-ms", "1000.0000001"), 1, () -> 0.0D))
                .containsExactly(Duration.ofSeconds(1).plusNanos(1));
    }

    @Test
    void roundsHighPrecisionSecondsBelowLimitUpToSixtySeconds() throws Exception {
        assertThat(delaysForHeaders(Map.of("retry-after", "59.9999999999"), 1, () -> 0.0D))
                .containsExactly(Duration.ofSeconds(60));
    }

    @Test
    void roundsHighPrecisionMillisecondsBelowLimitUpToSixtySeconds() throws Exception {
        assertThat(delaysForHeaders(Map.of("retry-after-ms", "59999.9999999"), 1, () -> 0.0D))
                .containsExactly(Duration.ofSeconds(60));
    }

    @Test
    void highPrecisionSecondsAboveLimitFallsBackToExponential() throws Exception {
        assertThat(delaysForHeaders(Map.of("retry-after", "60.0000000001"), 1, () -> 0.0D))
                .containsExactly(Duration.ofMillis(500));
    }

    @Test
    void highPrecisionMillisecondsAboveLimitFallsBackToExponential() throws Exception {
        assertThat(delaysForHeaders(Map.of("retry-after-ms", "60000.0000001"), 1, () -> 0.0D))
                .containsExactly(Duration.ofMillis(500));
    }

    @Test
    void parsesRetryAfterHttpDateAgainstInjectedClock() throws Exception {
        String retryAt = DateTimeFormatter.RFC_1123_DATE_TIME.format(
                FIXED_CLOCK.instant().plusSeconds(10).atZone(ZoneOffset.UTC));

        assertThat(delaysForHeaders(Map.of("retry-after", retryAt), 1, () -> 0.0D))
                .containsExactly(Duration.ofSeconds(10));
    }

    @ParameterizedTest
    @ValueSource(strings = {"invalid", "0", "-1", "60001"})
    void invalidNonPositiveOrOversizedRetryAfterMillisecondsFallsBackToExponential(String value)
            throws Exception {
        assertThat(delaysForHeaders(Map.of("retry-after-ms", value), 1, () -> 0.0D))
                .containsExactly(Duration.ofMillis(500));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-0.5", "60.001"})
    void nonPositiveOrOversizedRetryAfterSecondsFallsBackToExponential(String value) throws Exception {
        assertThat(delaysForHeaders(Map.of("retry-after", value), 1, () -> 0.0D))
                .containsExactly(Duration.ofMillis(500));
    }

    @Test
    void exponentialDelayDoublesAndCapsBaseAtEightSeconds() throws Exception {
        assertThat(delaysForHeaders(Map.of(), 6, () -> 0.0D)).containsExactly(
                Duration.ofMillis(500),
                Duration.ofSeconds(1),
                Duration.ofSeconds(2),
                Duration.ofSeconds(4),
                Duration.ofSeconds(8),
                Duration.ofSeconds(8));
    }

    @Test
    void exponentialDelayAppliesInjectedJitter() throws Exception {
        assertThat(delaysForHeaders(Map.of(), 1, () -> 0.4D))
                .containsExactly(Duration.ofMillis(450));
    }

    @Test
    void closesRetryableInputStreamBeforeNextAttempt() throws Exception {
        TrackingInputStream retryBody = new TrackingInputStream(false);
        OpenAIRetryingHttpClient client = client(1, new RecordingSleeper());

        HttpResponse<InputStream> result = client.send(retryCount -> {
            if (retryCount == 0) {
                return response(500, Map.of(), retryBody);
            }
            assertThat(retryBody.closed).isTrue();
            return response(200, Map.of(), new ByteArrayInputStream(new byte[0]));
        });

        assertThat(result.statusCode()).isEqualTo(200);
    }

    @Test
    void retryContinuesWhenClosingInputStreamFails() throws Exception {
        TrackingInputStream retryBody = new TrackingInputStream(true);
        OpenAIRetryingHttpClient client = client(1, new RecordingSleeper());

        HttpResponse<InputStream> result = client.send(retryCount -> retryCount == 0
                ? response(500, Map.of(), retryBody)
                : response(200, Map.of(), new ByteArrayInputStream(new byte[0])));

        assertThat(result.statusCode()).isEqualTo(200);
        assertThat(retryBody.closed).isTrue();
    }

    @Test
    void doesNotCloseFinalResponseBody() throws Exception {
        TrackingInputStream finalBody = new TrackingInputStream(false);
        OpenAIRetryingHttpClient client = client(0, new RecordingSleeper());

        HttpResponse<InputStream> result = client.send(retryCount -> response(500, Map.of(), finalBody));

        assertThat(result.body()).isSameAs(finalBody);
        assertThat(finalBody.closed).isFalse();
    }

    @Test
    void concurrentCallsKeepRetryCountsIsolated() throws Exception {
        OpenAIRetryingHttpClient client = new OpenAIRetryingHttpClient(
                1, delay -> { }, FIXED_CLOCK, () -> 0.0D);
        CyclicBarrier firstAttempts = new CyclicBarrier(2);
        Callable<List<Integer>> call = () -> {
            List<Integer> retryCounts = new ArrayList<>();
            client.send(retryCount -> {
                retryCounts.add(retryCount);
                if (retryCount == 0) {
                    await(firstAttempts);
                    return response(500);
                }
                return response(200);
            });
            return retryCounts;
        };
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<List<Integer>> first = executor.submit(call);
            Future<List<Integer>> second = executor.submit(call);

            assertThat(first.get(5, TimeUnit.SECONDS)).containsExactly(0, 1);
            assertThat(second.get(5, TimeUnit.SECONDS)).containsExactly(0, 1);
        } finally {
            executor.shutdownNow();
        }
    }

    private static List<Integer> retryCountsForExhaustedResponse(int maxRetries) throws Exception {
        RecordingSleeper sleeper = new RecordingSleeper();
        OpenAIRetryingHttpClient client = client(maxRetries, sleeper);
        List<Integer> retryCounts = new ArrayList<>();
        HttpResponse<String> finalResponse = client.send(retryCount -> {
            retryCounts.add(retryCount);
            return response(500);
        });

        assertThat(finalResponse.statusCode()).isEqualTo(500);
        assertThat(sleeper.delays).hasSize(Math.max(0, maxRetries));
        return retryCounts;
    }

    private static List<Integer> retryCountsForResponse(int statusCode, Map<String, String> headers)
            throws Exception {
        OpenAIRetryingHttpClient client = client(1, new RecordingSleeper());
        List<Integer> retryCounts = new ArrayList<>();
        client.send(retryCount -> {
            retryCounts.add(retryCount);
            return response(statusCode, headers, "body");
        });
        return retryCounts;
    }

    private static List<Duration> delaysForHeaders(
            Map<String, String> headers,
            int maxRetries,
            DoubleSupplier random) throws Exception {
        RecordingSleeper sleeper = new RecordingSleeper();
        OpenAIRetryingHttpClient client = new OpenAIRetryingHttpClient(
                maxRetries, sleeper, FIXED_CLOCK, random);
        client.send(retryCount -> response(500, headers, "body"));
        return sleeper.delays;
    }

    private static void await(CyclicBarrier barrier) throws IOException {
        try {
            barrier.await(5, TimeUnit.SECONDS);
        } catch (Exception exception) {
            throw new IOException("Failed to synchronize concurrent attempts", exception);
        }
    }

    private List<WarningRecord> retryWarnings() {
        return recordingLogger.warnings.stream()
                .filter(warning -> RETRY_WARNING.equals(warning.message()))
                .toList();
    }

    private static OpenAIRetryingHttpClient client(int maxRetries, RecordingSleeper sleeper) {
        return new OpenAIRetryingHttpClient(maxRetries, sleeper, FIXED_CLOCK, () -> 0.0D);
    }

    private static <T> FakeResponse<T> response(int statusCode) {
        return response(statusCode, Map.of(), null);
    }

    private static <T> FakeResponse<T> response(int statusCode, Map<String, String> headers, T body) {
        return new FakeResponse<>(statusCode, headers, body);
    }

    private static final class RecordingSleeper implements OpenAIRetryingHttpClient.Sleeper {
        private final List<Duration> delays = new ArrayList<>();

        @Override
        public void sleep(Duration delay) {
            delays.add(delay);
        }
    }

    private static final class TrackingInputStream extends ByteArrayInputStream {
        private final boolean failOnClose;
        private boolean closed;

        private TrackingInputStream(boolean failOnClose) {
            super(new byte[0]);
            this.failOnClose = failOnClose;
        }

        @Override
        public void close() throws IOException {
            closed = true;
            if (failOnClose) {
                throw new IOException("close failed");
            }
            super.close();
        }
    }

    private record WarningRecord(String message, Object[] arguments) {
        @Override
        public String toString() {
            return message + " " + java.util.Arrays.deepToString(arguments);
        }
    }

    private static final class RecordingLogger implements LoggerProtocol {
        private final List<WarningRecord> warnings = new CopyOnWriteArrayList<>();

        @Override
        public void debug(String msg, Object... args) {
        }

        @Override
        public void info(String msg, Object... args) {
        }

        @Override
        public void warning(String msg, Object... args) {
            warnings.add(new WarningRecord(msg, args.clone()));
        }

        @Override
        public void error(String msg, Object... args) {
        }

        @Override
        public void critical(String msg, Object... args) {
        }

        @Override
        public void exception(String msg, Throwable throwable, Object... args) {
        }

        @Override
        public void log(int level, String msg, Object... args) {
        }

        @Override
        public void setLevel(int level) {
        }

        @Override
        public Map<String, Object> getConfig() {
            return Map.of();
        }

        @Override
        public void reconfigure(Map<String, Object> config) {
        }
    }

    private static final class FakeResponse<T> implements HttpResponse<T> {
        private final int statusCode;
        private final HttpHeaders headers;
        private final T body;

        private FakeResponse(int statusCode, Map<String, String> headers, T body) {
            this.statusCode = statusCode;
            this.headers = HttpHeaders.of(
                    headers.entrySet().stream().collect(java.util.stream.Collectors.toMap(
                            Map.Entry::getKey,
                            entry -> List.of(entry.getValue()))),
                    (name, value) -> true);
            this.body = body;
        }

        @Override
        public int statusCode() {
            return statusCode;
        }

        @Override
        public HttpRequest request() {
            return HttpRequest.newBuilder(URI.create("https://example.test/v1/chat/completions")).build();
        }

        @Override
        public Optional<HttpResponse<T>> previousResponse() {
            return Optional.empty();
        }

        @Override
        public HttpHeaders headers() {
            return headers;
        }

        @Override
        public T body() {
            return body;
        }

        @Override
        public Optional<SSLSession> sslSession() {
            return Optional.empty();
        }

        @Override
        public URI uri() {
            return URI.create("https://example.test/v1/chat/completions");
        }

        @Override
        public HttpClient.Version version() {
            return HttpClient.Version.HTTP_2;
        }
    }
}

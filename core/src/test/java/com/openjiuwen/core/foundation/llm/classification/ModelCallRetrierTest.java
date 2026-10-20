/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.llm.classification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.StatusCode;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.ConnectException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * UT-R: classification-driven retry behavior of the unified model call retrier.
 *
 * @since 2026-10-19
 */
class ModelCallRetrierTest {
    private final ModelCallRetrier retrier = new ModelCallRetrier(delay -> { }, () -> 0.0D);

    @Test
    void execute_rateLimitedThenSuccess_retriesAndReturns() throws IOException {
        // Given a first 429 then success, When executed, Then one retry and success
        AtomicInteger calls = new AtomicInteger();
        String result = retrier.execute(context(2), () -> {
            if (calls.incrementAndGet() == 1) {
                throw classifiedHttp(429, "{\"error\":{}}");
            }
            return "ok";
        }, null);

        assertThat(result).isEqualTo("ok");
        assertThat(calls).hasValue(2);
    }

    @Test
    void execute_networkFailureThenSuccess_retriesAndReturns() throws IOException {
        AtomicInteger calls = new AtomicInteger();
        String result = retrier.execute(context(2), () -> {
            if (calls.incrementAndGet() == 1) {
                throw new ConnectException("connection refused");
            }
            return "ok";
        }, null);

        assertThat(result).isEqualTo("ok");
        assertThat(calls).hasValue(2);
    }

    @Test
    void execute_persistentRateLimit_exhaustsBudgetAndSurfacesTypedError() {
        // Given persistent 429 with default maxRetries=2, When executed, Then 3 calls total
        AtomicInteger calls = new AtomicInteger();
        assertThatThrownBy(() -> retrier.execute(context(2), () -> {
            calls.incrementAndGet();
            throw classifiedHttp(429, "{\"error\":{}}");
        }, null))
            .isInstanceOf(BaseError.class)
            .satisfies(error -> assertThat(((BaseError) error).getStatus())
                .isEqualTo(StatusCode.MODEL_CALL_RATE_LIMITED));
        assertThat(calls).hasValue(3);
    }

    @Test
    void execute_quotaExceeded_neverRetries() {
        AtomicInteger calls = new AtomicInteger();
        assertThatThrownBy(() -> retrier.execute(context(2), () -> {
            calls.incrementAndGet();
            throw classifiedHttp(402, "{\"error\":{}}");
        }, null))
            .isInstanceOf(BaseError.class)
            .satisfies(error -> assertThat(((BaseError) error).getStatus())
                .isEqualTo(StatusCode.MODEL_CALL_QUOTA_EXCEEDED));
        assertThat(calls).hasValue(1);
    }

    @Test
    void execute_authFailed_neverRetries() {
        AtomicInteger calls = new AtomicInteger();
        assertThatThrownBy(() -> retrier.execute(context(2), () -> {
            calls.incrementAndGet();
            throw classifiedHttp(401, "{\"error\":{}}");
        }, null)).isInstanceOf(BaseError.class);
        assertThat(calls).hasValue(1);
    }

    @Test
    void execute_cancellationShaped_neverRetriesAndRestoresCancellation() {
        // Given a cancellation-shaped failure, When executed, Then CancellationException, one call
        AtomicInteger calls = new AtomicInteger();
        assertThatThrownBy(() -> retrier.execute(context(2), () -> {
            calls.incrementAndGet();
            throw wrapCancel();
        }, null)).isInstanceOf(CancellationException.class);
        assertThat(calls).hasValue(1);
    }

    @Test
    void execute_contextLengthExceeded_neverRetries() {
        AtomicInteger calls = new AtomicInteger();
        assertThatThrownBy(() -> retrier.execute(context(2), () -> {
            calls.incrementAndGet();
            throw classifiedHttp(400, "{\"error\":{\"code\":\"prompt_too_long\"}}");
        }, null))
            .isInstanceOf(BaseError.class)
            .satisfies(error -> assertThat(((BaseError) error).getStatus())
                .isEqualTo(StatusCode.MODEL_CONTEXT_LENGTH_EXCEEDED));
        assertThat(calls).hasValue(1);
    }

    @Test
    void execute_configuredMaxRetries_retryCountMatchesConfiguration() {
        // Given maxRetries=1 and maxRetries=3, When persistently rate limited,
        // Then the actual attempt counts match the configuration exactly
        AtomicInteger callsOne = new AtomicInteger();
        assertThatThrownBy(() -> retrier.execute(context(1), () -> {
            callsOne.incrementAndGet();
            throw classifiedHttp(429, "{\"error\":{}}");
        }, null)).isInstanceOf(BaseError.class);
        assertThat(callsOne).hasValue(2);

        AtomicInteger callsThree = new AtomicInteger();
        assertThatThrownBy(() -> retrier.execute(context(3), () -> {
            callsThree.incrementAndGet();
            throw classifiedHttp(429, "{\"error\":{}}");
        }, null)).isInstanceOf(BaseError.class);
        assertThat(callsThree).hasValue(4);
    }

    @Test
    void execute_retryOnRateLimitDisabled_neverRetries() {
        AtomicInteger calls = new AtomicInteger();
        RetryContext context = new RetryContext(new ModelCallRetryPolicy(2, false, true));
        assertThatThrownBy(() -> retrier.execute(context, () -> {
            calls.incrementAndGet();
            throw classifiedHttp(429, "{\"error\":{}}");
        }, null)).isInstanceOf(BaseError.class);
        assertThat(calls).hasValue(1);
    }

    @Test
    void execute_suggestedDelay_consumedFromClassification() throws IOException {
        // The no-op sleeper cannot observe the delay directly; verify the classification
        // drives the retry and the suggested delay survives into the retry event path
        AtomicInteger calls = new AtomicInteger();
        String result = retrier.execute(context(2), () -> {
            if (calls.incrementAndGet() == 1) {
                throw classifiedWithDelay();
            }
            return "ok";
        }, null);
        assertThat(result).isEqualTo("ok");
        assertThat(calls).hasValue(2);
    }

    @Test
    void execute_suggestedDelay_sleepsExactlyTheServerHint() throws IOException {
        // Given a 429 carrying a Retry-After hint and a recording sleeper, When executed,
        // Then the slept delay equals the server suggested delay, not the backoff
        List<Duration> slept = new ArrayList<>();
        ModelCallRetrier recordingRetrier = new ModelCallRetrier(slept::add, () -> 0.0D);
        AtomicInteger calls = new AtomicInteger();
        String result = recordingRetrier.execute(context(2), () -> {
            if (calls.incrementAndGet() == 1) {
                throw classifiedWithDelay();
            }
            return "ok";
        }, null);
        assertThat(result).isEqualTo("ok");
        assertThat(slept).containsExactly(Duration.ofMillis(1500L));
    }

    private static RetryContext context(int maxRetries) {
        return new RetryContext(new ModelCallRetryPolicy(maxRetries, true, true));
    }

    private static BaseError classifiedHttp(int status, String body) {
        ModelErrorFacts facts = ModelErrorFacts.Builder.create()
            .httpStatus(status)
            .errorBody(body)
            .stream(false)
            .build();
        ModelCallClassification classification =
            new RuleBasedModelErrorClassifier().classify(facts);
        return ClassifiedModelErrors.toError(classification, facts, "HTTP " + status, null);
    }

    private static BaseError classifiedWithDelay() {
        ModelErrorFacts facts = ModelErrorFacts.Builder.create()
            .httpStatus(429)
            .errorBody("{\"error\":{}}")
            .retryAfterSeconds(1.5D)
            .stream(false)
            .build();
        ModelCallClassification classification =
            new RuleBasedModelErrorClassifier().classify(facts);
        return ClassifiedModelErrors.toError(classification, facts, "HTTP 429", null);
    }

    private static BaseError wrapCancel() {
        ModelErrorFacts facts = ModelErrorFacts.ofThrowable(
            new IOException("wrapped", new InterruptedIOException("model response body read interrupted")),
            true);
        ModelCallClassification classification =
            new RuleBasedModelErrorClassifier().classify(facts);
        return ClassifiedModelErrors.toError(classification, facts, "read failed", null);
    }
}

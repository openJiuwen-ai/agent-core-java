/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.llm.classification;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.foundation.llm.ModelRetryEvent;
import com.openjiuwen.core.foundation.llm.ModelRetryListener;

import java.io.IOException;
import java.time.Duration;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeUnit;
import java.util.function.DoubleSupplier;

/**
 * The single retry loop owner for model calls across all client families.
 * Every automatic retry (non-streaming calls, the streaming open phase, and
 * the streaming first-segment window) flows through this class, driven by the
 * classification of each failure: retryable categories (rate limited,
 * temporary) are retried while the per-call {@link RetryContext} budget lasts;
 * permanent categories fail fast; cancellation restores
 * {@link CancellationException} semantics and is never retried. Backoff honors
 * the server suggested delay (Retry-After) when the classification carries
 * one, otherwise uses exponential backoff with jitter. This class replaces
 * the former send-stage retry wrapper, whose backoff assets were migrated
 * here.
 *
 * @since 2026-10-19
 */
public final class ModelCallRetrier {
    private static final double BACKOFF_BASE_SECONDS = 0.5D;
    private static final double BACKOFF_CAP_BASE_SECONDS = 8.0D;
    private static final int BACKOFF_CAP_RETRY_NUMBER = 5;
    private static final double BACKOFF_JITTER_RATIO = 0.25D;
    private static final double NANOS_PER_SECOND = 1_000_000_000.0D;
    private static final Integer NULL_STATUS = null;

    private final Sleeper sleeper;
    private final DoubleSupplier random;

    /**
     * Creates a retrier with the default sleeper and jitter source.
     */
    public ModelCallRetrier() {
        this(delay -> TimeUnit.NANOSECONDS.sleep(delay.toNanos()), Math::random);
    }

    /**
     * Creates a retrier with explicit sleeper and jitter source, for tests.
     *
     * @param sleeper sleep implementation, must not be null
     * @param random jitter source in [0, 1), must not be null
     */
    public ModelCallRetrier(Sleeper sleeper, DoubleSupplier random) {
        this.sleeper = sleeper;
        this.random = random;
    }

    /**
     * Executes a non-streaming call attempt with classification-driven retry.
     *
     * @param context per-call retry budget, must not be null
     * @param attempt call attempt, re-invoked on each retry, must not be null
     * @param listener retry event listener, may be null
     * @param <T> result type
     * @return the attempt result
     * @throws IOException when the final attempt fails with an I/O error
     */
    public <T> T execute(RetryContext context, CallAttempt<T> attempt, ModelRetryListener listener)
        throws IOException {
        while (true) {
            try {
                return attempt.call();
            } catch (InterruptedException interrupt) {
                throw cancellation("model call interrupted");
            } catch (IOException failure) {
                if (!prepareRetry(context, failure, listener)) {
                    throw failure;
                }
            } catch (BaseError failure) {
                if (!prepareRetry(context, failure, listener)) {
                    throw failure;
                }
            }
        }
    }

    /**
     * Opens a stream with classification-driven retry over the open phase:
     * failures raised while initiating the call (transport errors, non-2xx
     * responses) happen before any data segment, so the whole call may be
     * re-initiated. Failures during later segment reads are the lazy
     * first-segment window's concern, see {@link FirstSegmentRetryIterator}.
     *
     * @param context per-call retry budget, must not be null
     * @param attempt stream open attempt, re-invoked on each retry, must not be null
     * @param listener retry event listener, may be null
     * @param <T> chunk type
     * @return handle of the opened stream
     * @throws IOException when the final open attempt fails with an I/O error
     */
    public <T> StreamHandle<T> openStream(RetryContext context, StreamAttempt<T> attempt,
        ModelRetryListener listener) throws IOException {
        while (true) {
            try {
                return attempt.open();
            } catch (InterruptedException interrupt) {
                throw cancellation("model stream interrupted");
            } catch (IOException failure) {
                if (!prepareRetry(context, failure, listener)) {
                    throw failure;
                }
            } catch (BaseError failure) {
                if (!prepareRetry(context, failure, listener)) {
                    throw failure;
                }
            }
        }
    }

    boolean prepareRetry(RetryContext context, Exception failure, ModelRetryListener listener) {
        ModelCallClassification classification = classify(failure);
        if (classification.category() == ModelErrorCategory.USER_CANCELLED) {
            throw cancellation("model call cancelled");
        }
        boolean isAllowed = classification.isRetryable() && context.policy().allows(classification.category());
        if (!isAllowed || !context.tryConsumeRetry()) {
            return false;
        }
        Duration delay = delayFor(context.consumedRetries(), classification);
        publishRetry(listener, toEvent(context, classification, failure, delay));
        sleepQuietly(delay);
        return true;
    }

    static boolean isAfterFirstSegment(BaseError failure) {
        if (failure.getDetails() instanceof Map<?, ?> details) {
            Object flag = details.get(ModelCallClassification.DETAIL_FIRST_SEGMENT);
            return flag instanceof Boolean received && received;
        }
        return false;
    }

    static CancellationException cancellation(String reason) {
        return ClassifiedModelErrors.toCancellation(reason);
    }

    private void sleepQuietly(Duration delay) {
        try {
            sleeper.sleep(delay);
        } catch (InterruptedException interrupt) {
            // The interrupt flag is consumed by sleep(); it is deliberately NOT restored
            // (project rule). Cancellation semantics are carried by the exception instead.
            throw cancellation("model call retry sleep interrupted");
        }
    }

    private Duration delayFor(int retryNumber, ModelCallClassification classification) {
        if (classification.suggestedDelaySeconds() != null) {
            return Duration.ofNanos(Math.round(classification.suggestedDelaySeconds() * NANOS_PER_SECOND));
        }
        double baseSeconds = retryNumber >= BACKOFF_CAP_RETRY_NUMBER
            ? BACKOFF_CAP_BASE_SECONDS
            : BACKOFF_BASE_SECONDS * Math.pow(2.0D, retryNumber - 1.0D);
        double delaySeconds = baseSeconds * (1.0D - BACKOFF_JITTER_RATIO * random.getAsDouble());
        return Duration.ofNanos(Math.round(delaySeconds * NANOS_PER_SECOND));
    }

    private static ModelCallClassification classify(Throwable failure) {
        return ModelCallClassification.of(failure).orElseGet(() -> ModelErrorClassifiers
            .defaultClassifier().classify(ModelErrorFacts.ofThrowable(failure, false)));
    }

    private static ModelRetryEvent toEvent(RetryContext context, ModelCallClassification classification,
        Exception failure, Duration delay) {
        Integer statusCode = failure instanceof BaseError baseError ? httpStatusOf(baseError) : null;
        String exceptionType = failure instanceof BaseError ? null : failure.getClass().getName();
        String delaySource = classification.suggestedDelaySeconds() != null ? "retry_after" : "exponential_backoff";
        return new ModelRetryEvent(context.consumedRetries(), context.policy().maxRetries(), statusCode,
            exceptionType, delay, delaySource, classification.category());
    }

    private static Integer httpStatusOf(BaseError failure) {
        if (failure.getDetails() instanceof Map<?, ?> details) {
            Object status = details.get(ModelCallClassification.DETAIL_HTTP_STATUS);
            if (status instanceof Number number) {
                return Integer.valueOf(number.intValue());
            }
        }
        return NULL_STATUS;
    }

    private void publishRetry(ModelRetryListener listener, ModelRetryEvent event) {
        Loggers.LLM.warning("Model call will retry. {}", retryLogFields(event));
        if (listener == null) {
            return;
        }
        try {
            listener.onRetry(event);
        } catch (IllegalStateException | IllegalArgumentException | NullPointerException exception) {
            Loggers.LLM.warning("Model retry listener failed. {}", Map.of(
                "retry_count", event.retryCount(),
                "listener_exception_type", exception.getClass().getName()));
        }
    }

    private static Map<String, Object> retryLogFields(ModelRetryEvent event) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("retry_count", event.retryCount());
        fields.put("max_retries", event.maxRetries());
        fields.put("category", event.category().name());
        if (event.statusCode() != null) {
            fields.put("status_code", event.statusCode());
        }
        if (event.exceptionType() != null) {
            fields.put("exception_type", event.exceptionType());
        }
        fields.put("delay_ms", event.delay().toMillis());
        fields.put("delay_source", event.delaySource());
        return fields;
    }

    /**
     * One non-streaming call attempt.
     *
     * @param <T> result type
     */
    @FunctionalInterface
    public interface CallAttempt<T> {
        /**
         * Performs the call once.
         *
         * @return call result
         * @throws IOException on I/O failure
         * @throws InterruptedException when the calling thread is interrupted
         */
        T call() throws IOException, InterruptedException;
    }

    /**
     * One streaming open attempt.
     *
     * @param <T> chunk type
     */
    @FunctionalInterface
    public interface StreamAttempt<T> {
        /**
         * Opens the stream once.
         *
         * @return stream handle wrapping the chunk iterator
         * @throws IOException on I/O failure
         * @throws InterruptedException when the calling thread is interrupted
         */
        StreamHandle<T> open() throws IOException, InterruptedException;
    }

    /**
     * Handle to an opened stream so the retrier can dispose abandoned attempts
     * without knowing the concrete iterator type.
     *
     * @param <T> chunk type
     */
    public interface StreamHandle<T> {
        /**
         * The chunk iterator of this attempt.
         *
         * @return chunk iterator
         */
        Iterator<T> iterator();

        /**
         * Releases the stream resources of an abandoned attempt, swallowing
         * non-fatal close failures.
         */
        void closeQuietly();

        /**
         * Reports a terminal failure surfaced through the lazy first-segment
         * window (see {@link FirstSegmentRetryIterator}). Such failures escape
         * the call site that mounted the circuit breaker, so the handle reports
         * them to the breaker itself. Default no-op for handles without
         * breaker accounting.
         *
         * @param failure classified terminal failure, must not be null
         */
        default void onWindowFailure(BaseError failure) {
            // Default no-op: handles without circuit breaker accounting ignore window failures.
        }
    }

    /**
     * Sleep strategy, replaceable in tests.
     */
    @FunctionalInterface
    public interface Sleeper {
        /**
         * Sleeps for the given delay.
         *
         * @param delay delay to sleep
         * @throws InterruptedException when the sleeping thread is interrupted
         */
        void sleep(Duration delay) throws InterruptedException;
    }
}

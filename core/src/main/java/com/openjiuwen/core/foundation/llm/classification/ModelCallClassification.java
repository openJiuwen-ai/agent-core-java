/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.llm.classification;

import com.openjiuwen.core.common.exception.BaseError;

import java.util.Map;
import java.util.Optional;

/**
 * Outcome of classifying one model call failure: the category, whether the
 * failure is intrinsically retryable (before configuration and budget), and an
 * optional server suggested delay. Classification results travel with the
 * raised error inside its details map, so downstream control flow (retry
 * wrapper, main loop guardrail, circuit breaker) can recover them by type via
 * {@link #of(Throwable)} without re-running the classifier.
 *
 * @since 2026-10-19
 */
public final class ModelCallClassification {
    /**
     * Details key carrying the {@link ModelErrorCategory} enum value.
     */
    public static final String DETAIL_CATEGORY = "error_category";

    /**
     * Details key carrying the intrinsic retryable flag.
     */
    public static final String DETAIL_RETRYABLE = "retryable";

    /**
     * Details key carrying the HTTP status code when available.
     */
    public static final String DETAIL_HTTP_STATUS = "http_status";

    /**
     * Details key carrying the first effective segment flag for streaming calls.
     */
    public static final String DETAIL_FIRST_SEGMENT = "first_segment_received";

    /**
     * Details key carrying the call shape flag.
     */
    public static final String DETAIL_STREAM = "stream";

    /**
     * Details key carrying the suggested retry delay in seconds when available.
     */
    public static final String DETAIL_SUGGESTED_DELAY = "suggested_delay_seconds";

    private final ModelErrorCategory category;
    private final boolean isRetryable;
    private final Double suggestedDelaySeconds;

    /**
     * Creates a classification result.
     *
     * @param category failure category, must not be null
     * @param isRetryable whether the failure is intrinsically retryable
     * @param delaySeconds server suggested retry delay in seconds, may be null
     */
    public ModelCallClassification(ModelErrorCategory category, boolean isRetryable, Double delaySeconds) {
        this.category = category;
        this.isRetryable = isRetryable;
        this.suggestedDelaySeconds = delaySeconds;
    }

    /**
     * Extracts the classification carried by an error's details, walking the
     * cause chain. Returns empty when no error in the chain carries one.
     *
     * @param error error to inspect, may be null (returns empty)
     * @return the carried classification, or empty
     */
    public static Optional<ModelCallClassification> of(Throwable error) {
        Throwable cursor = error;
        while (cursor != null) {
            if (cursor instanceof BaseError baseError) {
                Optional<ModelCallClassification> found = fromDetails(baseError.getDetails());
                if (found.isPresent()) {
                    return found;
                }
            }
            cursor = cursor.getCause();
        }
        return Optional.empty();
    }

    /**
     * Extracts the carried classification, or classifies the throwable on the
     * spot with the default classifier when none is carried. Used by the main
     * loop guardrail, which must also understand errors raised before the
     * classification layer (for example timeout errors built elsewhere).
     *
     * @param error error to inspect, must not be null
     * @return the carried or freshly computed classification
     */
    public static ModelCallClassification ofOrClassify(Throwable error) {
        return of(error).orElseGet(
            () -> ModelErrorClassifiers.defaultClassifier().classify(ModelErrorFacts.ofThrowable(error, false)));
    }

    private static Optional<ModelCallClassification> fromDetails(Object details) {
        if (!(details instanceof Map<?, ?> map)) {
            return Optional.empty();
        }
        Object rawCategory = map.get(DETAIL_CATEGORY);
        if (!(rawCategory instanceof ModelErrorCategory category)) {
            return Optional.empty();
        }
        Object rawRetryable = map.get(DETAIL_RETRYABLE);
        boolean isRetryable = rawRetryable instanceof Boolean isRetryableFlag && isRetryableFlag;
        Object rawDelay = map.get(DETAIL_SUGGESTED_DELAY);
        Double delaySeconds = rawDelay instanceof Number number ? number.doubleValue() : null;
        return Optional.of(new ModelCallClassification(category, isRetryable, delaySeconds));
    }

    /**
     * The failure category.
     *
     * @return category, never null
     */
    public ModelErrorCategory category() {
        return category;
    }

    /**
     * Whether the failure is intrinsically retryable, before configuration
     * category switches and remaining retry budget are applied.
     *
     * @return true when retrying can help
     */
    public boolean isRetryable() {
        return isRetryable;
    }

    /**
     * Server suggested retry delay in seconds, or null when absent.
     *
     * @return suggested delay in seconds or null
     */
    public Double suggestedDelaySeconds() {
        return suggestedDelaySeconds;
    }
}

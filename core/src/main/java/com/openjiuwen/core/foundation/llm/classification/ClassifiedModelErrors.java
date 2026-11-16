/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.llm.classification;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.common.logging.Loggers;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CancellationException;

/**
 * Builds the classified error surface from a {@link ModelCallClassification}:
 * retryable categories raise {@code ModelError}, permanent categories raise
 * {@code PermanentModelError} (so upstream surfaces report them as
 * non-recoverable), and cancellation restores {@link CancellationException}
 * semantics instead of producing a failure surface at all. The classification
 * itself is attached to the error details so downstream control flow can
 * recover it by type via {@link ModelCallClassification#of(Throwable)}.
 *
 * @since 2026-10-19
 */
public final class ClassifiedModelErrors {
    private ClassifiedModelErrors() {
    }

    /**
     * Builds the typed error for a classified failure.
     *
     * @param classification classification result, must not be null
     * @param facts failure facts used for the details payload, must not be null
     * @param sanitizedMessage sanitized human readable summary, must not be null
     * @param cause original cause, may be null
     * @return the typed model error carrying the classification in its details
     */
    public static BaseError toError(ModelCallClassification classification, ModelErrorFacts facts,
        String sanitizedMessage, Throwable cause) {
        StatusCode status = statusCodeOf(classification.category());
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("error_msg", sanitizedMessage);
        return ErrorHelper.buildError(status, null, detailsPayload(classification, facts), cause, params);
    }

    /**
     * Restores cancellation semantics for a failure classified as user
     * cancelled, replacing the usual failure surface. A structured log anchor
     * ({@code event=model_call_cancelled}) is emitted so cancellation remains
     * observable alongside the timeout and classified-error surfaces.
     *
     * @param reason human readable cancellation reason, must not be null
     * @return a cancellation exception carrying the reason
     */
    public static CancellationException toCancellation(String reason) {
        Loggers.LLM.info("event=model_call_cancelled, reason={}", reason);
        return new CancellationException(reason);
    }

    /**
     * Builds the details payload carrying the classification for downstream
     * typed consumption. All values are serializable.
     *
     * @param classification classification result, must not be null
     * @param facts failure facts, must not be null
     * @return details map carrying category, retryable flag and call metadata
     */
    public static Map<String, Object> detailsPayload(ModelCallClassification classification, ModelErrorFacts facts) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put(ModelCallClassification.DETAIL_CATEGORY, classification.category());
        details.put(ModelCallClassification.DETAIL_RETRYABLE, classification.isRetryable());
        if (facts.httpStatus() != null) {
            details.put(ModelCallClassification.DETAIL_HTTP_STATUS, facts.httpStatus());
        }
        if (facts.firstSegmentReceived() != null) {
            details.put(ModelCallClassification.DETAIL_FIRST_SEGMENT, facts.firstSegmentReceived());
        }
        details.put(ModelCallClassification.DETAIL_STREAM, facts.isStream());
        if (classification.suggestedDelaySeconds() != null) {
            details.put(ModelCallClassification.DETAIL_SUGGESTED_DELAY, classification.suggestedDelaySeconds());
        }
        return details;
    }

    private static StatusCode statusCodeOf(ModelErrorCategory category) {
        return switch (category) {
            case RATE_LIMITED -> StatusCode.MODEL_CALL_RATE_LIMITED;
            case QUOTA_EXCEEDED -> StatusCode.MODEL_CALL_QUOTA_EXCEEDED;
            case AUTH_FAILED -> StatusCode.MODEL_CALL_AUTH_FAILED;
            case CONTEXT_LENGTH_EXCEEDED -> StatusCode.MODEL_CONTEXT_LENGTH_EXCEEDED;
            case TEMPORARY, USER_CANCELLED -> StatusCode.MODEL_CALL_FAILED;
            default -> throw new IllegalStateException("unmapped category: " + category);
        };
    }
}

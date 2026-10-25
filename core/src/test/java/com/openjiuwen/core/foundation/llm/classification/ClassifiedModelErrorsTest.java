/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.llm.classification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.PermanentModelError;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.common.logging.LogManager;
import com.openjiuwen.core.common.logging.LoggerProtocol;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CancellationException;

/**
 * UT-E: classified error surface elements, user readable messages, and
 * classification-aware circuit breaker accounting.
 *
 * @since 2026-10-19
 */
class ClassifiedModelErrorsTest {
    @Test
    void toError_rateLimited_carriesCategoryRetryableAndCode() {
        // Given a 429 classification, When the error is built, Then it carries
        // category + retryable + the 181xxx code, and the classification round-trips
        BaseError error = build(429, true);

        assertThat(error.getStatus()).isEqualTo(StatusCode.MODEL_CALL_RATE_LIMITED);
        assertThat(error.isRecoverable()).isTrue();
        ModelCallClassification restored = ModelCallClassification.of(error).orElseThrow();
        assertThat(restored.category()).isEqualTo(ModelErrorCategory.RATE_LIMITED);
        assertThat(restored.isRetryable()).isTrue();
        if (error.getDetails() instanceof java.util.Map<?, ?> details) {
            assertThat(details.get(ModelCallClassification.DETAIL_HTTP_STATUS)).isEqualTo(429);
        } else {
            throw new IllegalStateException("details map expected");
        }
    }

    @Test
    void toError_quotaExceeded_userReadableTopUpSuggestion() {
        BaseError error = build(402, false);

        assertThat(error.getStatus()).isEqualTo(StatusCode.MODEL_CALL_QUOTA_EXCEEDED);
        assertThat(error.getMessage()).contains("top up");
        assertThat(error).isInstanceOf(PermanentModelError.class);
        assertThat(error.isRecoverable()).isFalse();
    }

    @Test
    void toError_authFailed_userReadableKeySuggestion() {
        BaseError error = build(401, false);

        assertThat(error.getStatus()).isEqualTo(StatusCode.MODEL_CALL_AUTH_FAILED);
        assertThat(error.getMessage()).contains("API key");
        assertThat(error.isRecoverable()).isFalse();
    }

    @Test
    void toError_detailsAreSerializable() {
        BaseError error = build(429, true);
        assertThat(error.getDetails()).isInstanceOf(java.io.Serializable.class);
    }

    @Test
    void toCancellation_emitsStructuredLogAnchor() {
        LoggerProtocol previous = LogManager.getLogger("llm");
        LoggerProtocol capturing = mock(LoggerProtocol.class);
        LogManager.registerLogger("llm", capturing);
        try {
            CancellationException cancellation = ClassifiedModelErrors.toCancellation("model call cancelled");

            assertThat(cancellation).hasMessage("model call cancelled");
            verify(capturing).info(eq("event=model_call_cancelled, reason={}"), eq("model call cancelled"));
        } finally {
            LogManager.registerLogger("llm", previous);
        }
    }

    private static BaseError build(int status, boolean isRetryableIgnored) {
        ModelErrorFacts facts = ModelErrorFacts.Builder.create()
            .httpStatus(status)
            .stream(false)
            .build();
        ModelCallClassification classification =
            new RuleBasedModelErrorClassifier().classify(facts);
        return ClassifiedModelErrors.toError(classification, facts, "HTTP " + status, null);
    }
}

/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.llm.classification;

import static org.assertj.core.api.Assertions.assertThat;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.foundation.llm.modelclients.IdleTimeoutIOException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * UT-C: rule table of the default model error classifier.
 *
 * @since 2026-10-19
 */
class RuleBasedModelErrorClassifierTest {
    private final RuleBasedModelErrorClassifier classifier = new RuleBasedModelErrorClassifier();

    @Test
    void classify_status429_rateLimitedRetryable() {
        // Given a 429 failure, When classified, Then rate limited and retryable
        ModelCallClassification result = classifier.classify(httpFacts(429, "{\"error\":{}}"));
        assertThat(result.category()).isEqualTo(ModelErrorCategory.RATE_LIMITED);
        assertThat(result.isRetryable()).isTrue();
    }

    @Test
    void classify_status402_quotaExceededNotRetryable() {
        ModelCallClassification result = classifier.classify(httpFacts(402, "{\"error\":{}}"));
        assertThat(result.category()).isEqualTo(ModelErrorCategory.QUOTA_EXCEEDED);
        assertThat(result.isRetryable()).isFalse();
    }

    @Test
    void classify_status401_authFailedNotRetryable() {
        ModelCallClassification result = classifier.classify(httpFacts(401, null));
        assertThat(result.category()).isEqualTo(ModelErrorCategory.AUTH_FAILED);
        assertThat(result.isRetryable()).isFalse();
    }

    @Test
    void classify_status403_authFailed() {
        ModelCallClassification result = classifier.classify(httpFacts(403, null));
        assertThat(result.category()).isEqualTo(ModelErrorCategory.AUTH_FAILED);
    }

    @ParameterizedTest
    @ValueSource(ints = {500, 502, 503, 529})
    void classify_status5xx_temporaryRetryable(int status) {
        ModelCallClassification result = classifier.classify(httpFacts(status, null));
        assertThat(result.category()).isEqualTo(ModelErrorCategory.TEMPORARY);
        assertThat(result.isRetryable()).isTrue();
    }

    @Test
    void classify_connectException_temporaryRetryable() {
        ModelErrorFacts facts = ModelErrorFacts.ofThrowable(new ConnectException("connection refused"), false);
        ModelCallClassification result = classifier.classify(facts);
        assertThat(result.category()).isEqualTo(ModelErrorCategory.TEMPORARY);
        assertThat(result.isRetryable()).isTrue();
    }

    @Test
    void classify_interruptedIo_userCancelledDistinctFromTimeout() {
        // Given a cancellation-shaped failure and a timeout-shaped one, When classified,
        // Then they land on different categories
        ModelCallClassification cancelled = classifier.classify(ModelErrorFacts.ofThrowable(
            new IOException("wrapped", new InterruptedIOException("model response body read interrupted")),
            true));
        assertThat(cancelled.category()).isEqualTo(ModelErrorCategory.USER_CANCELLED);

        ModelCallClassification timeout = classifier.classify(
            ModelErrorFacts.ofThrowable(new SocketTimeoutException("read timed out"), true));
        assertThat(timeout.category()).isEqualTo(ModelErrorCategory.TEMPORARY);
        assertThat(timeout.isRetryable()).isTrue();
    }

    @Test
    void classify_contextLengthExceeded_sixthCategoryNotRetryable() {
        ModelCallClassification result = classifier.classify(httpFacts(400,
            "{\"error\":{\"code\":\"context_length_exceeded\",\"message\":\"too long\"}}"));
        assertThat(result.category()).isEqualTo(ModelErrorCategory.CONTEXT_LENGTH_EXCEEDED);
        assertThat(result.isRetryable()).isFalse();

        ModelCallClassification variant = classifier.classify(httpFacts(400,
            "{\"error\":{\"code\":\"prompt_too_long\",\"message\":\"too long\"}}"));
        assertThat(variant.category()).isEqualTo(ModelErrorCategory.CONTEXT_LENGTH_EXCEEDED);
    }

    @Test
    void classify_embeddedErrorBody_classifiedNotSuccess() {
        // Given HTTP 200 with an embedded error body, When classified, Then it is a failure
        ModelCallClassification result = classifier.classify(httpFacts(200,
            "{\"error\":{\"message\":\"rate limit reached, slow down\"}}"));
        assertThat(result.category()).isEqualTo(ModelErrorCategory.RATE_LIMITED);
    }

    @Test
    void classify_timeoutErrorCodeMarker_temporaryRetryable() {
        // Given a 181008 timeout error, When classified, Then temporary and retryable
        BaseError timeout = ErrorHelper.buildError(StatusCode.MODEL_CALL_TIMEOUT, null, null, null,
            Map.of("error_msg", "model call timed out"));
        ModelCallClassification result = classifier.classify(ModelErrorFacts.ofThrowable(timeout, false));
        assertThat(result.category()).isEqualTo(ModelErrorCategory.TEMPORARY);
        assertThat(result.isRetryable()).isTrue();
    }

    @Test
    void classify_idleTimeoutTypeMarker_temporaryRetryable() {
        // Given an idle-timeout I/O marker, When classified, Then temporary and retryable
        ModelCallClassification result = classifier.classify(
            ModelErrorFacts.ofThrowable(new IdleTimeoutIOException(30.0D), true));
        assertThat(result.category()).isEqualTo(ModelErrorCategory.TEMPORARY);
        assertThat(result.isRetryable()).isTrue();
    }

    @Test
    void classify_truncatedBody_temporaryRetryable() {
        // Given a truncated-body transport failure, When classified, Then temporary
        // and retryable (the JDK reports it as a plain IOException with markers only)
        ModelCallClassification result = classifier.classify(ModelErrorFacts.ofThrowable(
            new IOException("fixed content-length: 4096, bytes received: 11"), true));
        assertThat(result.category()).isEqualTo(ModelErrorCategory.TEMPORARY);
        assertThat(result.isRetryable()).isTrue();
    }

    @Test
    void classify_prematureEofMarker_temporaryRetryable() {
        ModelCallClassification result = classifier.classify(
            ModelErrorFacts.ofThrowable(new IOException("Premature EOF"), true));
        assertThat(result.category()).isEqualTo(ModelErrorCategory.TEMPORARY);
        assertThat(result.isRetryable()).isTrue();
    }

    @Test
    void classify_headerParserNoBytesMarker_temporaryRetryable() {
        ModelCallClassification result = classifier.classify(ModelErrorFacts.ofThrowable(
            new IOException("HTTP/1.1 header parser received no bytes"), true));
        assertThat(result.category()).isEqualTo(ModelErrorCategory.TEMPORARY);
        assertThat(result.isRetryable()).isTrue();
    }

    @Test
    void classify_truncationMarkerInside4xxBody_staysPermanent() {
        // Given a 400 whose server-written body echoes a truncation marker, When
        // classified, Then the permanent-4xx rule still wins over the keyword rule
        ModelCallClassification result = classifier.classify(httpFacts(400,
            "{\"error\":{\"message\":\"upstream failed: premature eof\"}}"));
        assertThat(result.category()).isEqualTo(ModelErrorCategory.TEMPORARY);
        assertThat(result.isRetryable()).isFalse();
    }

    @Test
    void classify_uncoveredShape_temporaryNotRetryableFallback() {
        ModelCallClassification result = classifier.classify(
            ModelErrorFacts.ofThrowable(new IOException("totally unexpected"), false));
        assertThat(result.category()).isEqualTo(ModelErrorCategory.TEMPORARY);
        assertThat(result.isRetryable()).isFalse();
    }

    @Test
    void classify_appendedRule_takesEffectWithoutChangingCategorySet() {
        // Given a classifier with one appended keyword rule, When a 400 with the keyword
        // arrives, Then the appended rule wins over the permanent-4xx fallback
        ClassificationRule customQuota = facts -> {
            String body = facts.errorBody();
            if (body != null && body.contains("custom_quota_marker")) {
                return Optional.of(new ModelCallClassification(ModelErrorCategory.QUOTA_EXCEEDED, false, null));
            }
            return Optional.empty();
        };
        RuleBasedModelErrorClassifier extended = new RuleBasedModelErrorClassifier(List.of(customQuota));

        ModelCallClassification result = extended.classify(httpFacts(400, "{\"error\":\"custom_quota_marker\"}"));
        assertThat(result.category()).isEqualTo(ModelErrorCategory.QUOTA_EXCEEDED);
        assertThat(ModelErrorCategory.values()).hasSize(6);
    }

    @Test
    void classify_status429WithQuotaWording_statusWinsOverKeywords() {
        // Documented deviation: 429 with quota wording still classifies as rate limited
        ModelCallClassification result = classifier.classify(httpFacts(429,
            "{\"error\":{\"type\":\"insufficient_quota\"}}"));
        assertThat(result.category()).isEqualTo(ModelErrorCategory.RATE_LIMITED);
    }

    @Test
    void classify_retryAfter_suggestedDelayPropagated() {
        ModelErrorFacts facts = ModelErrorFacts.Builder.create()
            .httpStatus(429)
            .retryAfterSeconds(2.5D)
            .build();
        ModelCallClassification result = classifier.classify(facts);
        assertThat(result.suggestedDelaySeconds()).isEqualTo(2.5D);
    }

    private static ModelErrorFacts httpFacts(Integer status, String body) {
        ModelErrorFacts.Builder builder = ModelErrorFacts.Builder.create()
            .httpStatus(status)
            .stream(false);
        if (body != null) {
            builder.errorBody(body);
        }
        return builder.build();
    }
}

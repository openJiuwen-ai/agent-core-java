/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.llm;

import static org.assertj.core.api.Assertions.assertThat;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.foundation.llm.classification.ClassifiedModelErrors;
import com.openjiuwen.core.foundation.llm.classification.ModelCallClassification;
import com.openjiuwen.core.foundation.llm.classification.ModelErrorCategory;
import com.openjiuwen.core.foundation.llm.classification.ModelErrorFacts;
import com.openjiuwen.core.foundation.llm.classification.RuleBasedModelErrorClassifier;

import org.junit.jupiter.api.Test;

/**
 * UT-E05: circuit breaker failure accounting consumes the classification:
 * rate limited and temporary failures count; permanent 4xx categories and
 * cancellations do not.
 *
 * @since 2026-10-19
 */
class ModelCircuitBreakerClassificationTest {
    @Test
    void onFailure_rateLimitedAndTemporaryCount_permanentDoesNot() {
        // Given a breaker at threshold 2, When fed classified failures,
        // Then only rate limited and temporary ones move the counter
        ModelCircuitBreaker breaker = new ModelCircuitBreaker(2, 60_000L);
        breaker.onFailure(classifiedHttp(429));
        breaker.onFailure(classifiedHttp(402));
        breaker.onFailure(classifiedHttp(401));
        assertThat(breaker.isOpen()).isFalse();
        assertThat(breaker.consecutiveFailures()).isEqualTo(1);

        breaker.onFailure(classifiedHttp(503));
        assertThat(breaker.isOpen()).isTrue();
    }

    @Test
    void onFailure_contextLengthExceeded_notCounted() {
        ModelCircuitBreaker breaker = new ModelCircuitBreaker(1, 60_000L);
        ModelErrorFacts facts = ModelErrorFacts.Builder.create()
            .errorCode("context_length_exceeded")
            .errorMessage("context_length_exceeded")
            .build();
        ModelCallClassification classification =
            new RuleBasedModelErrorClassifier().classify(facts);
        breaker.onFailure(ClassifiedModelErrors.toError(classification, facts, "too long", null));
        assertThat(breaker.isOpen()).isFalse();
    }

    @Test
    void onFailure_cancellationShaped_notCounted() {
        ModelCircuitBreaker breaker = new ModelCircuitBreaker(1, 60_000L);
        ModelCallClassification classification =
            new ModelCallClassification(ModelErrorCategory.USER_CANCELLED, false, null);
        ModelErrorFacts facts = ModelErrorFacts.Builder.create().build();
        BaseError cancel = ClassifiedModelErrors.toError(classification, facts, "cancelled", null);
        breaker.onFailure(cancel);
        assertThat(breaker.isOpen()).isFalse();
        assertThat(breaker.consecutiveFailures()).isEqualTo(0);
    }

    @Test
    void onFailure_unclassifiedFallsBackToConnectHeuristic() {
        ModelCircuitBreaker breaker = new ModelCircuitBreaker(1, 60_000L);
        breaker.onFailure(new java.net.ConnectException("connection refused"));
        assertThat(breaker.isOpen()).isTrue();
    }

    private static BaseError classifiedHttp(int status) {
        ModelErrorFacts facts = ModelErrorFacts.Builder.create()
            .httpStatus(status)
            .stream(false)
            .build();
        ModelCallClassification classification =
            new RuleBasedModelErrorClassifier().classify(facts);
        return ClassifiedModelErrors.toError(classification, facts, "HTTP " + status, null);
    }
}

/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.llm.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.ModelCircuitBreaker;
import com.openjiuwen.core.foundation.llm.ModelCircuitBreakerSnapshot;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ConnectException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Tests the public circuit-breaker snapshot API and client compatibility.
 *
 * @since 0.1.17
 */
class ModelCircuitBreakerSnapshotTest {
    @Test
    void snapshotRetainsSampleWhileBreakerOpensAndResets() throws Exception {
        ModelCircuitBreaker breaker = new ModelCircuitBreaker(2, 60_000L);
        ModelCircuitBreakerSnapshot initial = breaker.snapshot();
        assertThat(initial.isOpen()).isFalse();
        assertThat(initial.consecutiveFailures()).isZero();

        breaker.onFailure(new ConnectException("connection refused"));
        ModelCircuitBreakerSnapshot firstFailure = breaker.snapshot();
        assertThat(firstFailure.isOpen()).isFalse();
        assertThat(firstFailure.consecutiveFailures()).isEqualTo(1);

        breaker.onFailure(new ConnectException("connection refused"));
        ModelCircuitBreakerSnapshot opened = breaker.snapshot();
        assertThat(opened.isOpen()).isTrue();
        assertThat(opened.consecutiveFailures()).isEqualTo(2);
        assertThatThrownBy(breaker::beforeCall).isInstanceOf(IOException.class);

        breaker.onSuccess();
        assertThat(breaker.snapshot().isOpen()).isFalse();
        assertThat(breaker.snapshot().consecutiveFailures()).isZero();
        breaker.beforeCall();
        assertThat(firstFailure.consecutiveFailures()).isEqualTo(1);
        assertThat(opened.isOpen()).isTrue();
        assertThat(opened.consecutiveFailures()).isEqualTo(2);
    }

    @Test
    void expiredWindowClosesWithoutClearingFailureCount() throws Exception {
        ModelCircuitBreaker breaker = new ModelCircuitBreaker(1, 100L);
        breaker.onFailure(new ConnectException("connection refused"));
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (breaker.snapshot().isOpen() && System.nanoTime() < deadline) {
            Thread.sleep(5L);
        }

        assertThat(breaker.snapshot().isOpen()).isFalse();
        assertThat(breaker.snapshot().consecutiveFailures()).isEqualTo(1);
        breaker.beforeCall();
    }

    @Test
    void customClientWithoutObservationRemainsUsable() {
        Model.ModelClient client = (messages, options) ->
            CompletableFuture.completedFuture(new AssistantMessage("ok"));
        Model model = new Model(client);

        assertThat(model.getCircuitBreakerSnapshot()).isEmpty();
        assertThat(model.invoke("hello").toCompletableFuture().join().getContent()).isEqualTo("ok");
    }
}

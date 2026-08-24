/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;

class ModelCircuitBreakerTest {

    @Test
    void opensAfterConsecutiveConnectFailuresAndFailsFast() throws Exception {
        ModelCircuitBreaker breaker = new ModelCircuitBreaker(3, 60_000L);

        breaker.onFailure(new ConnectException("Failed to connect to host"));
        breaker.onFailure(new ConnectException("Failed to connect to host"));
        assertThat(breaker.isOpen()).isFalse();

        breaker.onFailure(new ConnectException("Failed to connect to host"));
        assertThat(breaker.isOpen()).isTrue();

        assertThatThrownBy(breaker::beforeCall).isInstanceOf(IOException.class)
                .hasMessageContaining("circuit breaker open");
    }

    @Test
    void ignoresNonConnectFailures() {
        ModelCircuitBreaker breaker = new ModelCircuitBreaker(2, 60_000L);
        breaker.onFailure(new SocketTimeoutException("read timed out"));
        breaker.onFailure(new SocketTimeoutException("read timed out"));
        assertThat(breaker.isOpen()).isFalse();
        assertThat(breaker.consecutiveFailures()).isZero();
    }

    @Test
    void successResetsOpenCircuit() throws Exception {
        ModelCircuitBreaker breaker = new ModelCircuitBreaker(2, 60_000L);
        breaker.onFailure(new ConnectException("refused"));
        breaker.onFailure(new ConnectException("refused"));
        assertThat(breaker.isOpen()).isTrue();

        breaker.onSuccess();
        assertThat(breaker.isOpen()).isFalse();
        breaker.beforeCall();
    }
}

/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.rails;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

/**
 * Tests the first-iteration gate lifecycle behavior.
 *
 * <p>Mirrors Python's tests for
 * {@code openjiuwen/agent_teams/rails/first_iteration_gate.py}.</p>
 */
class FirstIterationGateTest {

    @Test
    void startsClosedUntilFirstIteration() {
        FirstIterationGate gate = new FirstIterationGate();

        assertThat(gate.isReady()).isFalse();
        assertThat(gate.waitReady().toCompletableFuture()).isNotCompleted();
    }

    @Test
    void beforeTaskIterationOpensGateOnce() {
        FirstIterationGate gate = new FirstIterationGate();
        CompletableFuture<Void> waitFuture = gate.waitReady().toCompletableFuture();

        assertThat(gate.beforeTaskIteration().toCompletableFuture()).isCompleted();

        assertThat(gate.isReady()).isTrue();
        assertThat(waitFuture).isCompleted();
        assertThat(gate.beforeTaskIteration(new Object()).toCompletableFuture()).isCompleted();
        assertThat(gate.isReady()).isTrue();
    }

    @Test
    void resetClosesAnOpenedGateForAnotherRound() {
        FirstIterationGate gate = new FirstIterationGate();
        gate.beforeTaskIteration();

        gate.reset();

        CompletableFuture<Void> nextWait = gate.waitReady().toCompletableFuture();
        assertThat(gate.isReady()).isFalse();
        assertThat(nextWait).isNotCompleted();

        gate.beforeTaskIteration(new Object());

        assertThat(gate.isReady()).isTrue();
        assertThat(nextWait).isCompleted();
    }

    @Test
    void resetBeforeOpenLeavesGateClosed() {
        FirstIterationGate gate = new FirstIterationGate();

        gate.reset();

        assertThat(gate.isReady()).isFalse();
        assertThat(gate.waitReady().toCompletableFuture()).isNotCompleted();
    }
}

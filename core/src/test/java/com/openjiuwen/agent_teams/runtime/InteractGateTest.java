/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/**
 * Missing-test parity coverage for interact gate timing behavior.
 *
 * <p>Mirrors Python's {@code tests/unit_tests/agent_teams/runtime/test_gate.py}.</p>
 */
class InteractGateTest {

    @Test
    void admitReturnsTicketWhenOpen() {
        InteractGate gate = new InteractGate();

        AdmissionTicket ticket = gate.admit();

        assertThat(ticket).isNotNull();
        assertThat(gate.getInflight()).isEqualTo(1);
        assertThat(gate.isClosed()).isFalse();
    }

    @Test
    void admitReturnsNullWhenClosed() {
        InteractGate gate = new InteractGate();

        gate.closeAndDrain();

        assertThat(gate.isClosed()).isTrue();
        assertThat(gate.admit()).isNull();
    }

    @Test
    void consumeDoneDecrementsInflight() {
        InteractGate gate = new InteractGate();
        AdmissionTicket ticket = gate.admit();
        assertThat(gate.getInflight()).isEqualTo(1);

        gate.consumeDone(ticket);

        assertThat(gate.getInflight()).isZero();
    }

    @Test
    void consumeDoneIsIdempotentAfterDrain() {
        InteractGate gate = new InteractGate();
        AdmissionTicket ticket = gate.admit();

        gate.consumeDone(ticket);
        gate.consumeDone(ticket);

        assertThat(gate.getInflight()).isZero();
    }

    @Test
    void consumeDoneIgnoresForeignTicket() {
        InteractGate gate = new InteractGate();
        InteractGate other = new InteractGate();
        AdmissionTicket foreignTicket = other.admit();
        gate.admit();

        gate.consumeDone(foreignTicket);

        assertThat(gate.getInflight()).isEqualTo(1);
    }

    @Test
    void closeAndDrainReturnsImmediatelyWhenIdle() throws Exception {
        InteractGate gate = new InteractGate();

        CompletableFuture<Void> drainFuture = CompletableFuture.runAsync(gate::closeAndDrain);

        drainFuture.get(500, TimeUnit.MILLISECONDS);
        assertThat(gate.isClosed()).isTrue();
    }

    @Test
    void closeAndDrainBlocksUntilInflightConsumed() throws Exception {
        InteractGate gate = new InteractGate();
        AdmissionTicket ticket = gate.admit();

        CompletableFuture<Void> drainFuture = CompletableFuture.runAsync(gate::closeAndDrain);
        TimeUnit.MILLISECONDS.sleep(50);
        assertThat(drainFuture).isNotDone();

        gate.consumeDone(ticket);

        drainFuture.get(500, TimeUnit.MILLISECONDS);
        assertThat(gate.getInflight()).isZero();
        assertThat(gate.isClosed()).isTrue();
    }

    @Test
    void admitAfterDrainIsRejectedWhileCloseIsWaiting() throws Exception {
        InteractGate gate = new InteractGate();
        AdmissionTicket ticket = gate.admit();
        CompletableFuture<Void> drainFuture = CompletableFuture.runAsync(gate::closeAndDrain);
        TimeUnit.MILLISECONDS.sleep(10);

        AdmissionTicket rejected = gate.admit();

        gate.consumeDone(ticket);
        drainFuture.get(500, TimeUnit.MILLISECONDS);
        assertThat(rejected).isNull();
    }

    @Test
    void multipleInflightDrainedTogether() throws Exception {
        InteractGate gate = new InteractGate();
        List<AdmissionTicket> tickets = List.of(gate.admit(), gate.admit(), gate.admit());
        assertThat(gate.getInflight()).isEqualTo(3);

        CompletableFuture<Void> first = delayedConsume(gate, tickets.get(0), 20);
        CompletableFuture<Void> second = delayedConsume(gate, tickets.get(1), 40);
        CompletableFuture<Void> third = delayedConsume(gate, tickets.get(2), 60);

        gate.closeAndDrain();
        CompletableFuture.allOf(first, second, third).get(500, TimeUnit.MILLISECONDS);

        assertThat(gate.getInflight()).isZero();
    }

    private static CompletableFuture<Void> delayedConsume(InteractGate gate, AdmissionTicket ticket, long delayMs) {
        return CompletableFuture.runAsync(() -> {
            try {
                TimeUnit.MILLISECONDS.sleep(delayMs);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(exception);
            }
            gate.consumeDone(ticket);
        });
    }
}


package com.openjiuwen.agentteams.agent;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

class FirstIterationGateCompatibilityTest {
    @Test
    void shouldStartNotReady() {
        FirstIterationGate gate = new FirstIterationGate();
        assertThat(gate.isReady()).isFalse();
    }

    @Test
    void shouldBecomeReadyAfterBeforeModelCall() {
        FirstIterationGate gate = new FirstIterationGate();
        gate.beforeModelCall(null);
        assertThat(gate.isReady()).isTrue();
    }

    @Test
    void shouldUnblockAwaitAfterBeforeModelCall() throws InterruptedException {
        FirstIterationGate gate = new FirstIterationGate();
        AtomicBoolean unblocked = new AtomicBoolean(false);

        Thread waiter = new Thread(() -> {
            try {
                gate.await();
                unblocked.set(true);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        waiter.start();

        // Give the waiter thread time to block
        Thread.sleep(100);
        assertThat(unblocked.get()).isFalse();

        // Trigger the gate
        gate.beforeModelCall(null);
        waiter.join(1000);
        assertThat(unblocked.get()).isTrue();
    }

    @Test
    void shouldResetToNotReady() {
        FirstIterationGate gate = new FirstIterationGate();
        gate.beforeModelCall(null);
        assertThat(gate.isReady()).isTrue();

        gate.reset();
        assertThat(gate.isReady()).isFalse();
    }

    @Test
    void shouldAwaitWithTimeout() throws InterruptedException {
        FirstIterationGate gate = new FirstIterationGate();
        boolean result = gate.await(50, TimeUnit.MILLISECONDS);
        assertThat(result).isFalse();

        gate.beforeModelCall(null);
        result = gate.await(100, TimeUnit.MILLISECONDS);
        assertThat(result).isTrue();
    }

    @Test
    void shouldOnlySetOnce() {
        FirstIterationGate gate = new FirstIterationGate();
        gate.beforeModelCall(null);
        gate.beforeModelCall(null);
        assertThat(gate.isReady()).isTrue();
    }

    @Test
    void shouldAcceptNullContext() {
        FirstIterationGate gate = new FirstIterationGate();
        gate.beforeModelCall(null);
        assertThat(gate.isReady()).isTrue();
    }
}

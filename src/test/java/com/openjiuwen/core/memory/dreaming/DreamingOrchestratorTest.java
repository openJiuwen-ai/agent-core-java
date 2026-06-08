/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.dreaming;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class DreamingOrchestratorTest {

    @Test
    void tickSkipsSweepWhenBusy() {
        AtomicInteger sweeps = new AtomicInteger();
        DreamingOrchestrator orchestrator = new DreamingOrchestrator(
                () -> {
                    sweeps.incrementAndGet();
                    return CompletableFuture.completedFuture(null);
                },
                5.0,
                () -> true,
                "busy");

        orchestrator.tick().join();

        assertThat(sweeps).hasValue(0);
        assertThat(orchestrator.getHealth()).containsEntry("running", false).containsEntry("interval_seconds", 60.0d);
    }

    @Test
    void tickRunsSweepWhenBusyCheckerFailsOpen() {
        AtomicInteger sweeps = new AtomicInteger();
        DreamingOrchestrator orchestrator = new DreamingOrchestrator(
                () -> {
                    sweeps.incrementAndGet();
                    return CompletableFuture.completedFuture(null);
                },
                90.0,
                () -> {
                    throw new IllegalStateException("boom");
                },
                "fallback");

        orchestrator.tick().join();

        assertThat(sweeps).hasValue(1);
    }

    @Test
    void startAndStopAreIdempotent() {
        DreamingOrchestrator orchestrator = new DreamingOrchestrator(
                () -> CompletableFuture.completedFuture(null),
                90.0);

        orchestrator.start().join();
        orchestrator.start().join();
        assertThat(orchestrator.getHealth()).containsEntry("running", true);

        orchestrator.stop().join();
        orchestrator.stop().join();
        assertThat(orchestrator.getHealth()).containsEntry("running", false);
    }
}

/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.concurrent;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

/**
 * Verifies interruption restoration and cancellation signals bound to the worker that registered them.
 *
 * @since 2026-10-28
 */
@Timeout(10)
class InterruptSignalsTest {
    @Test
    void restorationPreservesTheCurrentThreadsInterruptStatus() {
        assertThat(Thread.currentThread().isInterrupted()).isFalse();
        try {
            InterruptSignals.restoreCurrentThread();
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void capturedSignalInterruptsOnlyItsWorkerAndReleasesABlockingWait() throws Exception {
        CompletableFuture<Runnable> signal = new CompletableFuture<>();
        CountDownLatch stop = new CountDownLatch(1);
        FutureTask<Boolean> operation = new FutureTask<>(() -> {
            signal.complete(InterruptSignals.captureCurrentThread());
            try {
                stop.await();
                return false;
            } catch (InterruptedException error) {
                InterruptSignals.restoreCurrentThread();
                return Thread.currentThread().isInterrupted();
            }
        });
        ExecutorService worker = OpenJiuwenExecutors.newBoundedModulePool("interrupt-signal-regression", 1, 1, true);
        try {
            worker.execute(operation);
            signal.get(5, TimeUnit.SECONDS).run();
            assertThat(operation.get(5, TimeUnit.SECONDS)).isTrue();
            assertThat(Thread.currentThread().isInterrupted()).isFalse();
        } finally {
            stop.countDown();
            worker.shutdownNow();
            assertThat(worker.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }
}

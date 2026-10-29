/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.graph.pregel;

import com.openjiuwen.core.common.concurrent.InterruptSignals;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Tracks a node's worker separately from its published result.
 *
 * @since 2026-10-29
 */
final class PregelTaskExecution {
    private final Object lifecycleLock = new Object();
    private final CompletableFuture<Void> stopped = new CompletableFuture<>();
    private final CompletableFuture<Object> result;
    private Runnable interruptWorker;
    private boolean isCancelled;

    PregelTaskExecution(PregelNode node, PregelConfig config, int version, Executor executor) {
        result = CompletableFuture.supplyAsync(() -> {
            try {
                return new NodeTask(node, config, version).run();
            } catch (Exception error) {
                throw new CompletionException(error);
            }
        }, command -> executor.execute(() -> runOwned(command)));
    }

    CompletableFuture<Object> result() {
        return result;
    }

    void cancel() {
        synchronized (lifecycleLock) {
            if (result.isDone()) {
                return;
            }
            isCancelled = true;
            if (interruptWorker == null) {
                result.cancel(false);
                stopped.complete(null);
            } else {
                interruptWorker.run();
            }
        }
    }

    boolean isStopped() {
        return stopped.isDone();
    }

    void awaitStopped(long deadlineNanos) throws InterruptedException, ExecutionException, TimeoutException {
        stopped.get(Math.max(1L, deadlineNanos - System.nanoTime()), TimeUnit.NANOSECONDS);
    }

    private void runOwned(Runnable command) {
        synchronized (lifecycleLock) {
            if (isCancelled) {
                return;
            }
            interruptWorker = InterruptSignals.captureCurrentThread();
        }
        try {
            command.run();
        } finally {
            synchronized (lifecycleLock) {
                interruptWorker = null;
                stopped.complete(null);
            }
        }
    }
}

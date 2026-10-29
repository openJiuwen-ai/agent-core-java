/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.graph.pregel;

import com.openjiuwen.core.common.concurrent.InterruptSignals;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies cancellation and checkpoint collection without relying on worker scheduling delays.
 *
 * @since 2026-10-29
 */
class TaskExecutorPoolCancellationTest {
    private final PregelConfig config = new PregelConfig("cancellation-test", "root", 5);
    private final TaskExecutorPool pool = new TaskExecutorPool(config);

    @AfterEach
    void closePool() {
        pool.shutdown();
    }

    @Test
    void cancellationCollectsAlreadyCompletedRoutesAndInterrupts() throws Exception {
        pool.submit(routingNode("complete"), 1);
        pool.submit(new PregelNode("paused", ignored -> new GraphInterrupt("input"), List.of()), 1);
        for (CompletableFuture<Object> result : pool.getRunningTasks().keySet()) {
            result.get(5, TimeUnit.SECONDS);
        }

        pool.cancelAll();

        assertEquals(1, pool.getSucceedMessages().size());
        assertEquals("complete", pool.getSucceedMessages().get(0).getSender());
        assertEquals(PregelConstants.TASK_STATUS_INTERRUPT, pool.getFailed().get("paused").getStatus());
        assertTrue(pool.getRunningTasks().isEmpty());
        assertTrue(pool.isQuiescent());
    }

    @Test
    void cancellationWaitsForTheInterruptedWorkerCleanup() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch interrupted = new CountDownLatch(1);
        CountDownLatch releaseCleanup = new CountDownLatch(1);
        AtomicBoolean isCleaned = new AtomicBoolean();
        pool.submit(new PregelNode("blocked", ignored -> {
            started.countDown();
            try {
                await(new CountDownLatch(1));
            } catch (CancellationException error) {
                interrupted.countDown();

                // Keep cleanup blocked after observing cancellation, until the test releases it.
                Thread.interrupted();
                await(releaseCleanup);
                isCleaned.set(true);
                throw error;
            }
            return null;
        }, List.of()), 1);
        await(started);
        CompletableFuture<Void> cancellation = CompletableFuture.runAsync(pool::cancelAll);
        try {
            await(interrupted);
            assertFalse(cancellation.isDone());
            assertFalse(pool.isQuiescent());
        } finally {
            releaseCleanup.countDown();
            cancellation.get(5, TimeUnit.SECONDS);
        }
        assertTrue(isCleaned.get());
        assertTrue(pool.getFailed().containsKey("blocked"));
        assertTrue(pool.isQuiescent());
    }

    @Test
    void cancellationPreservesAResultCompletedDuringDrain() {
        CountDownLatch started = new CountDownLatch(1);
        pool.submit(new PregelNode("finishing", ignored -> {
            started.countDown();
            try {
                await(new CountDownLatch(1));
            } catch (CancellationException error) {
                // A cooperative node may finish normally before observing a cancellation point.
                return null;
            }
            return null;
        }, List.of(source -> List.of(new TriggerMessage(source, "end")))), 1);
        await(started);

        pool.cancelAll();

        assertTrue(pool.getFailed().isEmpty());
        assertEquals(1, pool.getSucceedMessages().size());
        assertEquals("finishing", pool.getSucceedMessages().get(0).getSender());
    }

    @Test
    void cancellationBeforeWorkerStartsDoesNotRunTheNode() throws Exception {
        List<Runnable> queued = new ArrayList<>();
        AtomicBoolean wasCalled = new AtomicBoolean();
        PregelTaskExecution execution = new PregelTaskExecution(new PregelNode("queued", ignored -> {
            wasCalled.set(true);
            return null;
        }, List.of()), config, 1, queued::add);

        execution.cancel();
        execution.awaitStopped(System.nanoTime() + TimeUnit.SECONDS.toNanos(5));
        queued.get(0).run();

        assertTrue(execution.result().isCancelled());
        assertFalse(wasCalled.get());
    }

    @Test
    void failedStepKeepsAnAlreadyCompletedSibling() throws Exception {
        pool.submit(routingNode("complete"), 1);
        pool.getRunningTasks().keySet().iterator().next().get(5, TimeUnit.SECONDS);
        pool.submit(new PregelNode("failed", ignored -> {
            throw new IllegalStateException("expected failure");
        }, List.of()), 1);

        assertThrows(IllegalStateException.class, pool::waitAll);

        assertEquals(1, pool.getSucceedMessages().size());
        assertEquals("complete", pool.getSucceedMessages().get(0).getSender());
        assertTrue(pool.getFailed().containsKey("failed"));
    }

    private PregelNode routingNode(String name) {
        return new PregelNode(name, ignored -> null,
                List.of(source -> List.of(new TriggerMessage(source, "end"))));
    }

    private static void await(CountDownLatch latch) {
        try {
            assertTrue(latch.await(5, TimeUnit.SECONDS), "Fixture synchronization timed out");
        } catch (InterruptedException error) {
            InterruptSignals.restoreCurrentThread();
            throw new CancellationException("Fixture worker interrupted");
        }
    }
}

/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.spawn;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Verifies that cancelling a health-check worker stops it without reporting an unhealthy process.
 *
 * @since 2026-10-27
 */
class HealthCheckCancellationTest {
    private final AtomicBoolean isAlive = new AtomicBoolean(true);
    private final CountDownLatch releaseResponse = new CountDownLatch(1);
    private final AtomicReference<Throwable> uncaught = new AtomicReference<>();
    private final ThreadPoolExecutor executor = new ThreadPoolExecutor(
            1, 1, 0L, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(4), runnable -> {
                Thread thread = new Thread(runnable, "health-cancellation-test");
                thread.setDaemon(true);
                thread.setUncaughtExceptionHandler((worker, error) -> uncaught.compareAndSet(null, error));
                return thread;
            });

    @AfterEach
    void cleanup() throws InterruptedException {
        isAlive.set(false);
        releaseResponse.countDown();
        executor.shutdownNow();
        assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        assertThat(uncaught.get()).isNull();
    }

    @Test
    void cancellingSleepingHealthWorkerDoesNotReportFailure() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        Process process = process(entered);
        AtomicInteger unhealthyCalls = new AtomicInteger();
        SpawnedProcessHandle handle = new SpawnedProcessHandle(
                "cancel-sleep", process, new SpawnConfig(), unhealthyCalls::incrementAndGet, 1);
        Future<?> worker = startHealthWorker(handle, 50.0D);
        assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();

        worker.cancel(true);
        executor.submit(() -> Boolean.TRUE).get(5, TimeUnit.SECONDS);

        assertThat(unhealthyCalls.get()).isZero();
        assertThat(handle.isHealthy()).isTrue();
    }

    @Test
    void cancellingResponseWaitDoesNotReportFailure() throws Exception {
        CountDownLatch responseWait = new CountDownLatch(1);
        Process process = process(new CountDownLatch(1));
        InputStream response = mock(InputStream.class);
        when(response.read(any(byte[].class), anyInt(), anyInt())).thenAnswer(call -> {
            responseWait.countDown();
            assertThat(releaseResponse.await(5, TimeUnit.SECONDS)).isTrue();
            return -1;
        });
        when(process.getInputStream()).thenReturn(response);
        AtomicInteger unhealthyCalls = new AtomicInteger();
        SpawnedProcessHandle handle = new SpawnedProcessHandle(
                "cancel-response", process, new SpawnConfig(0.0D, 1.0D, 30.0D),
                unhealthyCalls::incrementAndGet, 1);
        Future<?> worker = startHealthWorker(handle, 0.0D);
        assertThat(responseWait.await(5, TimeUnit.SECONDS)).isTrue();

        worker.cancel(true);
        executor.submit(() -> Boolean.TRUE).get(5, TimeUnit.SECONDS);

        assertThat(unhealthyCalls.get()).isZero();
        assertThat(handle.isHealthy()).isTrue();
    }

    private Process process(CountDownLatch entered) {
        Process process = mock(Process.class);
        when(process.isAlive()).thenAnswer(call -> {
            entered.countDown();
            return isAlive.get();
        });
        when(process.getOutputStream()).thenReturn(new ByteArrayOutputStream());
        return process;
    }

    private Future<?> startHealthWorker(SpawnedProcessHandle handle, double interval) throws NoSuchMethodException {
        // Use a dedicated executor so its barrier observes actual worker exit, not just Future cancellation.
        Method loop = SpawnedProcessHandle.class.getDeclaredMethod("healthCheckLoop", double.class);
        loop.setAccessible(true);
        return executor.submit(() -> {
            try {
                loop.invoke(handle, interval);
            } catch (IllegalAccessException | InvocationTargetException error) {
                throw new IllegalStateException("Health-check worker failed", error);
            }
        });
    }
}

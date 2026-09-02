/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.concurrent;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

class VirtualThreadSupportTest {
    private static final long TEST_TIMEOUT_SECONDS = 2L;

    @Test
    @DisplayName("虚拟线程能力检测与当前运行时一致")
    void supportDetectionMatchesRuntimeFeature() {
        assertThat(VirtualThreadSupport.isSupported()).isEqualTo(Runtime.version().feature() >= 21);
    }

    @Test
    @DisplayName("共享普通业务任务在 JDK 17 使用平台线程，在 JDK 21 使用虚拟线程")
    void sharedBusinessExecutorUsesRuntimeAppropriateThread()
            throws ExecutionException, InterruptedException, TimeoutException {
        ThreadDetails details = threadDetails(OpenJiuwenExecutors.backgroundExecutor());

        assertThat(details.name()).startsWith("openjiuwen-background-");
        assertThat(details.isVirtual()).isEqualTo(VirtualThreadSupport.isSupported());
        assertThat(details.isDaemon()).isTrue();
    }

    @Test
    @DisplayName("固定大小普通任务不以 daemon 配置判断是否使用虚拟线程")
    void fixedExecutorUsesRuntimeAppropriateThreadRegardlessOfDaemon()
            throws ExecutionException, InterruptedException, TimeoutException {
        ExecutorService executor = OpenJiuwenExecutors.newFixedThreadPool("fixed-virtual-test", 2, false);
        try {
            ThreadDetails details = executor.submit(VirtualThreadSupportTest::currentThreadDetails)
                    .get(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            assertThat(details.name()).startsWith("fixed-virtual-test-");
            assertThat(details.isVirtual()).isEqualTo(VirtualThreadSupport.isSupported());
            assertThat(details.isDaemon()).isEqualTo(VirtualThreadSupport.isSupported());

            int expectedConcurrency = VirtualThreadSupport.isSupported() ? 4 : 2;
            verifyConcurrency(executor, 4, expectedConcurrency);
        } finally {
            shutdown(executor);
        }
    }

    @Test
    @DisplayName("有界普通任务不以 daemon 配置判断是否使用虚拟线程")
    void boundedExecutorUsesRuntimeAppropriateThreadRegardlessOfDaemon()
            throws ExecutionException, InterruptedException, TimeoutException {
        ExecutorService executor = OpenJiuwenExecutors.newBoundedModulePool("bounded-virtual-test", 2, 1, false);
        try {
            ThreadDetails details = threadDetails(executor);
            assertThat(details.isVirtual()).isEqualTo(VirtualThreadSupport.isSupported());
            assertThat(details.isDaemon()).isEqualTo(VirtualThreadSupport.isSupported());

            int expectedConcurrency = VirtualThreadSupport.isSupported() ? 4 : 2;
            int taskCount = VirtualThreadSupport.isSupported() ? 4 : 2;
            verifyConcurrency(executor, taskCount, expectedConcurrency);
        } finally {
            shutdown(executor);
        }
    }

    @Test
    @DisplayName("自定义线程池在 JDK 17 保留配置，在 JDK 21 使用虚拟线程")
    void customExecutorUsesRuntimeAppropriateThread()
            throws ExecutionException, InterruptedException, TimeoutException {
        ExecutorService executor = newCustomExecutor("custom-executor-test", true);
        try {
            ThreadDetails details = threadDetails(executor);
            assertThat(details.isVirtual()).isEqualTo(VirtualThreadSupport.isSupported());
            assertThat(details.isDaemon()).isTrue();

            int expectedConcurrency = VirtualThreadSupport.isSupported() ? 4 : 1;
            verifyConcurrency(executor, expectedConcurrency, expectedConcurrency);
        } finally {
            shutdown(executor);
        }
    }

    @Test
    @DisplayName("单线程和定时执行器始终使用平台线程")
    void platformOnlyExecutorsRemainPlatformThreads()
            throws ExecutionException, InterruptedException, TimeoutException {
        ExecutorService single = OpenJiuwenExecutors.newSingleThreadExecutor("single-thread-test", true);
        ScheduledExecutorService scheduled = OpenJiuwenExecutors.newScheduledThreadPool(
                "scheduled-thread-test", 1, true);
        try {
            assertThat(threadDetails(single).isVirtual()).isFalse();
            assertThat(threadDetails(scheduled).isVirtual()).isFalse();
        } finally {
            shutdown(single);
            shutdown(scheduled);
        }
    }

    private static void verifyConcurrency(ExecutorService executor, int taskCount, int expectedConcurrency)
            throws InterruptedException, ExecutionException, TimeoutException {
        CountDownLatch firstWaveStarted = new CountDownLatch(expectedConcurrency);
        CountDownLatch releaseTasks = new CountDownLatch(1);
        AtomicInteger activeTasks = new AtomicInteger();
        AtomicInteger peakActiveTasks = new AtomicInteger();
        List<Future<?>> futures = new ArrayList<>();
        for (int index = 0; index < taskCount; index++) {
            futures.add(executor.submit(() -> runLimitedTask(firstWaveStarted, releaseTasks,
                    activeTasks, peakActiveTasks)));
        }

        assertThat(firstWaveStarted.await(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
        assertThat(activeTasks.get()).isEqualTo(expectedConcurrency);
        releaseTasks.countDown();
        for (Future<?> future : futures) {
            future.get(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        }
        assertThat(peakActiveTasks.get()).isEqualTo(expectedConcurrency);
    }

    private static void runLimitedTask(CountDownLatch firstWaveStarted, CountDownLatch releaseTasks,
            AtomicInteger activeTasks, AtomicInteger peakActiveTasks) {
        int currentActiveTasks = activeTasks.incrementAndGet();
        peakActiveTasks.updateAndGet(previous -> Math.max(previous, currentActiveTasks));
        firstWaveStarted.countDown();
        try {
            releaseTasks.await();
        } catch (InterruptedException exception) {
            throw new IllegalStateException("Task interrupted while waiting for test release", exception);
        } finally {
            activeTasks.decrementAndGet();
        }
    }

    private static ThreadDetails threadDetails(ExecutorService executor)
            throws ExecutionException, InterruptedException, TimeoutException {
        return executor.submit(VirtualThreadSupportTest::currentThreadDetails)
                .get(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    private static ExecutorService newCustomExecutor(String threadNamePrefix, boolean isDaemon) {
        return OpenJiuwenExecutors.newThreadPool(threadNamePrefix,
                OpenJiuwenExecutors.ThreadPoolConfig.builder()
                        .poolSize(1, 1)
                        .keepAlive(0L, TimeUnit.MILLISECONDS)
                        .workQueue(new ArrayBlockingQueue<>(1))
                        .isDaemon(isDaemon)
                        .rejectionHandler(new ThreadPoolExecutor.AbortPolicy())
                        .build());
    }

    private static ThreadDetails currentThreadDetails() {
        Thread currentThread = Thread.currentThread();
        return new ThreadDetails(currentThread.getName(), isVirtual(currentThread), currentThread.isDaemon());
    }

    private static boolean isVirtual(Thread thread) {
        try {
            Method isVirtual = Thread.class.getMethod("isVirtual");
            return Boolean.TRUE.equals(isVirtual.invoke(thread));
        } catch (NoSuchMethodException ignored) {
            return false;
        } catch (IllegalAccessException exception) {
            throw new IllegalStateException("Failed to access Thread.isVirtual", exception);
        } catch (InvocationTargetException exception) {
            throw new IllegalStateException("Failed to invoke Thread.isVirtual", exception.getTargetException());
        }
    }

    private static void shutdown(ExecutorService executor) throws InterruptedException {
        executor.shutdownNow();
        assertThat(executor.awaitTermination(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
    }

    private record ThreadDetails(String name, boolean isVirtual, boolean isDaemon) {
    }
}

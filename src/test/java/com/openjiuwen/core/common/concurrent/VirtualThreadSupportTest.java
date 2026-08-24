/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.concurrent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.SynchronousQueue;
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
    @DisplayName("每任务 daemon 配置在 JDK 17 使用平台线程，在 JDK 21 使用虚拟线程")
    void perTaskDaemonExecutorUsesRuntimeAppropriateThread()
            throws ExecutionException, InterruptedException, TimeoutException {
        ExecutorService executor = newPerTaskExecutor("per-task-test", true);
        try {
            ThreadDetails details = executor.submit(VirtualThreadSupportTest::currentThreadDetails)
                    .get(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            assertThat(details.name()).startsWith("per-task-test-");
            assertThat(details.isVirtual()).isEqualTo(VirtualThreadSupport.isSupported());
            assertThat(details.isDaemon()).isTrue();
        } finally {
            shutdown(executor);
        }
    }

    @Test
    @DisplayName("固定大小 daemon 执行器在 JDK 21 使用虚拟线程并保留最大并发数")
    void fixedDaemonExecutorPreservesMaximumConcurrency()
            throws ExecutionException, InterruptedException, TimeoutException {
        ExecutorService executor = OpenJiuwenExecutors.newFixedThreadPool("fixed-virtual-test", 2, true);
        try {
            ThreadDetails details = executor.submit(VirtualThreadSupportTest::currentThreadDetails)
                    .get(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            assertThat(details.name()).startsWith("fixed-virtual-test-");
            assertThat(details.isVirtual()).isEqualTo(VirtualThreadSupport.isSupported());
            assertThat(details.isDaemon()).isTrue();

            verifyMaximumConcurrency(executor, 2);
        } finally {
            shutdown(executor);
        }
    }

    @Test
    @DisplayName("有界 daemon 执行器在 JDK 21 使用虚拟线程并保留并发、排队和拒绝语义")
    void boundedDaemonExecutorPreservesConcurrencyAndAdmissionLimits()
            throws ExecutionException, InterruptedException, TimeoutException {
        ExecutorService executor = OpenJiuwenExecutors.newBoundedModulePool("bounded-virtual-test", 2, 1, true);
        CountDownLatch firstWaveStarted = new CountDownLatch(2);
        CountDownLatch releaseTasks = new CountDownLatch(1);
        AtomicInteger activeTasks = new AtomicInteger();
        AtomicInteger peakActiveTasks = new AtomicInteger();
        List<Future<?>> futures = new ArrayList<>();
        try {
            ThreadDetails details = threadDetails(executor);
            assertThat(details.isVirtual()).isEqualTo(VirtualThreadSupport.isSupported());

            for (int index = 0; index < 2; index++) {
                futures.add(executor.submit(() -> runLimitedTask(firstWaveStarted, releaseTasks,
                        activeTasks, peakActiveTasks)));
            }
            assertThat(firstWaveStarted.await(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
            futures.add(executor.submit(() -> runLimitedTask(firstWaveStarted, releaseTasks,
                    activeTasks, peakActiveTasks)));
            assertThatThrownBy(() -> executor.submit(() -> {
            })).isInstanceOf(RejectedExecutionException.class);

            releaseTasks.countDown();
            for (Future<?> future : futures) {
                future.get(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            }
            assertThat(peakActiveTasks.get()).isEqualTo(2);
        } finally {
            releaseTasks.countDown();
            shutdown(executor);
        }
    }

    @Test
    @DisplayName("虚拟最大并发配置只调整 JDK 21 并发且不扩大最大接纳量")
    void virtualMaximumConcurrencyOnlyChangesVirtualExecution()
            throws ExecutionException, InterruptedException, TimeoutException {
        String propertyName = "openjiuwen.executor.bounded-virtual-config-test.virtual-max-concurrency";
        System.setProperty(propertyName, "4");
        ExecutorService executor = OpenJiuwenExecutors.newBoundedModulePool(
                "bounded-virtual-config-test", 2, 2, true);
        int expectedConcurrency = VirtualThreadSupport.isSupported() ? 4 : 2;
        CountDownLatch started = new CountDownLatch(expectedConcurrency);
        CountDownLatch releaseTasks = new CountDownLatch(1);
        AtomicInteger activeTasks = new AtomicInteger();
        AtomicInteger peakActiveTasks = new AtomicInteger();
        List<Future<?>> futures = new ArrayList<>();
        try {
            submitTasks(executor, futures, expectedConcurrency, started, releaseTasks,
                    activeTasks, peakActiveTasks);
            assertThat(started.await(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
            submitTasks(executor, futures, 4 - expectedConcurrency, started, releaseTasks,
                    activeTasks, peakActiveTasks);
            assertThatThrownBy(() -> executor.submit(() -> {
            })).isInstanceOf(RejectedExecutionException.class);

            releaseTasks.countDown();
            awaitTasks(futures);
            assertThat(peakActiveTasks.get()).isEqualTo(expectedConcurrency);
        } finally {
            System.clearProperty(propertyName);
            releaseTasks.countDown();
            shutdown(executor);
        }
    }

    @Test
    @DisplayName("非 daemon 每任务配置始终保留平台线程池")
    void nonDaemonPerTaskExecutorRemainsPlatformThread()
            throws ExecutionException, InterruptedException, TimeoutException {
        ExecutorService executor = newPerTaskExecutor("non-daemon-per-task-test", false);
        try {
            assertThat(executor).isInstanceOf(ThreadPoolExecutor.class);
            ThreadDetails details = threadDetails(executor);
            assertThat(details.isVirtual()).isFalse();
            assertThat(details.isDaemon()).isFalse();
        } finally {
            shutdown(executor);
        }
    }

    @Test
    @DisplayName("非 daemon 固定池和单线程执行器始终使用平台线程")
    void platformOnlyExecutorsRemainPlatformThreads()
            throws ExecutionException, InterruptedException, TimeoutException {
        ExecutorService nonDaemonFixed = OpenJiuwenExecutors.newFixedThreadPool("non-daemon-fixed-test", 2, false);
        ExecutorService single = OpenJiuwenExecutors.newSingleThreadExecutor("single-thread-test", true);
        try {
            assertThat(threadDetails(nonDaemonFixed).isVirtual()).isFalse();
            assertThat(threadDetails(single).isVirtual()).isFalse();
        } finally {
            shutdown(nonDaemonFixed);
            shutdown(single);
        }
    }

    private static void verifyMaximumConcurrency(ExecutorService executor, int maximumConcurrency)
            throws InterruptedException, ExecutionException, TimeoutException {
        CountDownLatch firstWaveStarted = new CountDownLatch(maximumConcurrency);
        CountDownLatch releaseTasks = new CountDownLatch(1);
        AtomicInteger activeTasks = new AtomicInteger();
        AtomicInteger peakActiveTasks = new AtomicInteger();
        List<Future<?>> futures = new ArrayList<>();
        for (int index = 0; index < maximumConcurrency * 2; index++) {
            futures.add(executor.submit(() -> runLimitedTask(firstWaveStarted, releaseTasks,
                    activeTasks, peakActiveTasks)));
        }

        assertThat(firstWaveStarted.await(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
        assertThat(activeTasks.get()).isEqualTo(maximumConcurrency);
        releaseTasks.countDown();
        for (Future<?> future : futures) {
            future.get(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        }
        assertThat(peakActiveTasks.get()).isEqualTo(maximumConcurrency);
    }

    private static void submitTasks(ExecutorService executor, List<Future<?>> futures, int taskCount,
            CountDownLatch started, CountDownLatch releaseTasks, AtomicInteger activeTasks,
            AtomicInteger peakActiveTasks) {
        for (int index = 0; index < taskCount; index++) {
            futures.add(executor.submit(() -> runLimitedTask(started, releaseTasks,
                    activeTasks, peakActiveTasks)));
        }
    }

    private static void awaitTasks(List<Future<?>> futures)
            throws ExecutionException, InterruptedException, TimeoutException {
        for (Future<?> future : futures) {
            future.get(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        }
    }

    private static void runLimitedTask(CountDownLatch firstWaveStarted, CountDownLatch releaseTasks,
            AtomicInteger activeTasks, AtomicInteger peakActiveTasks) {
        int currentActiveTasks = activeTasks.incrementAndGet();
        peakActiveTasks.updateAndGet(previous -> Math.max(previous, currentActiveTasks));
        firstWaveStarted.countDown();
        try {
            releaseTasks.await();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        } finally {
            activeTasks.decrementAndGet();
        }
    }

    private static ThreadDetails threadDetails(ExecutorService executor)
            throws ExecutionException, InterruptedException, TimeoutException {
        return executor.submit(VirtualThreadSupportTest::currentThreadDetails)
                .get(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    private static ExecutorService newPerTaskExecutor(String threadNamePrefix, boolean isDaemon) {
        return OpenJiuwenExecutors.newThreadPool(threadNamePrefix,
                OpenJiuwenExecutors.ThreadPoolConfig.builder()
                        .poolSize(0, Integer.MAX_VALUE)
                        .keepAlive(0L, TimeUnit.MILLISECONDS)
                        .workQueue(new SynchronousQueue<>())
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

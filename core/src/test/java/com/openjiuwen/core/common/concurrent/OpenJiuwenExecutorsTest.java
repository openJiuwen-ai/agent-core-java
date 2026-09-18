/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.concurrent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

class OpenJiuwenExecutorsTest {
    @Test
    @DisplayName("任务并发默认值与 I/O 池上限对齐")
    void defaultTaskConcurrencyMatchesIoBoundMaxSize() {
        assertThat(OpenJiuwenExecutors.defaultTaskConcurrency())
                .isEqualTo(Math.max(40, Runtime.getRuntime().availableProcessors() * 8));
        assertThat(OpenJiuwenExecutors.isVirtualThreadSupported())
                .isEqualTo(VirtualThreadSupport.isSupported());
    }

    @Test
    @DisplayName("JDK 21 普通业务执行器使用虚拟线程")
    void boundedModulePoolUsesVirtualThreadsOnJdk21() throws Exception {
        Assumptions.assumeTrue(OpenJiuwenExecutors.isVirtualThreadSupported());
        ExecutorService executor = OpenJiuwenExecutors.newBoundedModulePool("deep-agent-stream", true);
        try {
            assertThat(executor).isNotInstanceOf(ThreadPoolExecutor.class);
            Boolean virtual = executor.submit(() -> isVirtual(Thread.currentThread())).get(2, TimeUnit.SECONDS);
            assertThat(virtual).isTrue();
            String threadName = executor.submit(() -> Thread.currentThread().getName()).get(2, TimeUnit.SECONDS);
            assertThat(threadName).startsWith("deep-agent-stream-");
        } finally {
            OpenJiuwenExecutors.shutdown(executor);
        }
    }

    @Test
    @DisplayName("单线程执行器在 JDK 21 仍保持平台线程串行语义")
    void singleThreadExecutorStaysOnPlatformThreads() throws Exception {
        ExecutorService executor = OpenJiuwenExecutors.newSingleThreadExecutor("single-thread-serial", true);
        try {
            assertThat(executor).isInstanceOf(ThreadPoolExecutor.class);
            Boolean virtual = executor.submit(() -> isVirtual(Thread.currentThread())).get(2, TimeUnit.SECONDS);
            assertThat(virtual).isFalse();
        } finally {
            OpenJiuwenExecutors.shutdown(executor);
        }
    }

    @Test
    @DisplayName("newThread 在 JDK 21 返回未启动的虚拟线程")
    void newThreadMatchesRuntimeCapability() {
        Thread thread = OpenJiuwenExecutors.newThread(() -> {
        }, "new-thread-test", true);
        assertThat(thread.getName()).isEqualTo("new-thread-test");
        assertThat(isVirtual(thread))
                .isEqualTo(OpenJiuwenExecutors.isVirtualThreadSupported());
        assertThat(thread.isAlive()).isFalse();
    }

    @Test
    @DisplayName("DeepAgent stream 模块池默认 max(40, CPU×8) 且可配置")
    void deepAgentStreamPoolUsesCpuScaledDefaultMaxSize() throws Exception {
        Assumptions.assumeFalse(OpenJiuwenExecutors.isVirtualThreadSupported());
        int expectedDefault = OpenJiuwenExecutors.defaultIoBoundMaxSize();
        assertThat(expectedDefault).isEqualTo(Math.max(40, Runtime.getRuntime().availableProcessors() * 8));

        ExecutorService executor = OpenJiuwenExecutors.newBoundedModulePool("deep-agent-stream", true);
        try {
            assertThat(executor).isInstanceOf(ThreadPoolExecutor.class);
            assertThat(((ThreadPoolExecutor) executor).getMaximumPoolSize()).isEqualTo(expectedDefault);
            String threadName = executor.submit(() -> Thread.currentThread().getName()).get(2, TimeUnit.SECONDS);
            assertThat(threadName).startsWith("deep-agent-stream-");
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(2, TimeUnit.SECONDS)).isTrue();
        }

        System.setProperty("openjiuwen.executor.deep-agent-stream.max-size", "5");
        ExecutorService overridden = OpenJiuwenExecutors.newBoundedModulePool("deep-agent-stream", true);
        try {
            assertThat(((ThreadPoolExecutor) overridden).getMaximumPoolSize()).isEqualTo(5);
        } finally {
            System.clearProperty("openjiuwen.executor.deep-agent-stream.max-size");
            overridden.shutdownNow();
            assertThat(overridden.awaitTermination(2, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    @DisplayName("有界模块池使用统一命名且最大线程数可配置")
    void boundedModulePoolUsesPrefixAndRespectsMaxSize() throws Exception {
        Assumptions.assumeFalse(OpenJiuwenExecutors.isVirtualThreadSupported());
        System.setProperty("openjiuwen.executor.executor-bounded-test.max-size", "2");
        ExecutorService executor = OpenJiuwenExecutors.newBoundedModulePool("executor-bounded-test", 8, 16, true);
        try {
            String threadName = executor.submit(() -> Thread.currentThread().getName()).get(2, TimeUnit.SECONDS);
            assertThat(threadName).startsWith("executor-bounded-test-");
            assertThat(executor).isInstanceOf(ThreadPoolExecutor.class);
            assertThat(((ThreadPoolExecutor) executor).getMaximumPoolSize()).isEqualTo(2);
        } finally {
            System.clearProperty("openjiuwen.executor.executor-bounded-test.max-size");
            executor.shutdownNow();
            assertThat(executor.awaitTermination(2, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    @DisplayName("有界队列模块池 core=max，避免 core=0 单 worker 串行陷阱")
    void boundedQueueModulePoolUsesCoreEqualToMaxSize() throws Exception {
        Assumptions.assumeFalse(OpenJiuwenExecutors.isVirtualThreadSupported());
        ExecutorService executor = OpenJiuwenExecutors.newBoundedModulePool("workflow-stream", true);
        try {
            ThreadPoolExecutor pool = (ThreadPoolExecutor) executor;
            int maxSize = pool.getMaximumPoolSize();
            assertThat(pool.getCorePoolSize()).isEqualTo(maxSize);
            assertThat(pool.getQueue()).isInstanceOf(ArrayBlockingQueue.class);

            int workers = Math.min(4, maxSize);
            CountDownLatch allStarted = new CountDownLatch(workers);
            CountDownLatch release = new CountDownLatch(1);
            for (int i = 0; i < workers; i++) {
                executor.submit(() -> {
                    allStarted.countDown();
                    await(release);
                });
            }
            assertThat(allStarted.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(pool.getActiveCount()).isEqualTo(workers);
            release.countDown();
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    @DisplayName("模块池统一 core=max + ArrayBlockingQueue")
    void allModulePoolsUseArrayBlockingQueueWithCoreEqualsMax() {
        Assumptions.assumeFalse(OpenJiuwenExecutors.isVirtualThreadSupported());
        for (String prefix : List.of(
                "pregel-task", "workflow-stream", "vertex-stream", "stream-actor",
                "deep-agent-stream", "deep-agent-invoke", "spawn-process-manager"
        )) {
            ExecutorService executor = OpenJiuwenExecutors.newBoundedModulePool(prefix, true);
            try {
                ThreadPoolExecutor pool = (ThreadPoolExecutor) executor;
                assertThat(pool.getCorePoolSize()).isEqualTo(pool.getMaximumPoolSize());
                assertThat(pool.getQueue()).isInstanceOf(ArrayBlockingQueue.class);
            } finally {
                executor.shutdownNow();
            }
        }
    }

    @Test
    @DisplayName("默认异步任务使用统一命名的后台线程池")
    void backgroundExecutorUsesDedicatedThreadPrefix() throws Exception {
        String backgroundThread = OpenJiuwenExecutors.backgroundExecutor()
                .submit(() -> Thread.currentThread().getName()).get(2, TimeUnit.SECONDS);

        assertThat(backgroundThread).startsWith("openjiuwen-background-");
    }

    @Test
    @DisplayName("实例专用线程池由统一工厂创建且可正常关闭")
    void instanceExecutorUsesCentralFactoryAndCanBeClosed() throws Exception {
        ExecutorService executor = OpenJiuwenExecutors.newFixedThreadPool("executor-test", 1, true);
        try {
            String threadName = executor.submit(() -> Thread.currentThread().getName()).get(2, TimeUnit.SECONDS);
            assertThat(threadName).startsWith("executor-test-");
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(2, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    @DisplayName("自定义线程池保留队列容量和拒绝策略")
    void customExecutorPreservesQueueAndRejectionPolicy() throws Exception {
        Assumptions.assumeFalse(OpenJiuwenExecutors.isVirtualThreadSupported());
        CountDownLatch taskStarted = new CountDownLatch(1);
        CountDownLatch releaseTask = new CountDownLatch(1);
        ExecutorService executor = OpenJiuwenExecutors.newThreadPool("bounded-executor-test",
                OpenJiuwenExecutors.ThreadPoolConfig.builder()
                        .poolSize(1, 1)
                        .keepAlive(0L, TimeUnit.MILLISECONDS)
                        .workQueue(new ArrayBlockingQueue<>(1))
                        .isDaemon(true)
                        .rejectionHandler(new ThreadPoolExecutor.AbortPolicy())
                        .build());
        try {
            executor.submit(() -> {
                taskStarted.countDown();
                await(releaseTask);
            });
            assertThat(taskStarted.await(2, TimeUnit.SECONDS)).isTrue();
            executor.submit(() -> {
            });

            assertThatThrownBy(() -> executor.submit(() -> {
            })).isInstanceOf(RejectedExecutionException.class);
        } finally {
            releaseTask.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(2, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    @DisplayName("统一关闭会等待已开始的任务完成")
    void shutdownExecutorsWaitsForRunningTask() throws Exception {
        CountDownLatch taskStarted = new CountDownLatch(1);
        CountDownLatch taskCompleted = new CountDownLatch(1);
        ExecutorService executor = OpenJiuwenExecutors.newFixedThreadPool("graceful-shutdown-test", 1, true);
        executor.submit(() -> {
            taskStarted.countDown();
            try {
                TimeUnit.MILLISECONDS.sleep(100L);
                taskCompleted.countDown();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        assertThat(taskStarted.await(2, TimeUnit.SECONDS)).isTrue();
        OpenJiuwenExecutors.shutdownExecutors(List.of(executor), 1L, TimeUnit.SECONDS);

        assertThat(taskCompleted.getCount()).isZero();
        assertThat(executor.awaitTermination(2, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    @DisplayName("统一关闭超时后会中断未结束任务")
    void shutdownExecutorsInterruptsTaskAfterTimeout() throws Exception {
        CountDownLatch taskStarted = new CountDownLatch(1);
        CountDownLatch taskInterrupted = new CountDownLatch(1);
        ExecutorService executor = OpenJiuwenExecutors.newFixedThreadPool("forced-shutdown-test", 1, true);
        executor.submit(() -> {
            taskStarted.countDown();
            try {
                new CountDownLatch(1).await();
            } catch (InterruptedException e) {
                taskInterrupted.countDown();
                Thread.currentThread().interrupt();
            }
        });

        assertThat(taskStarted.await(2, TimeUnit.SECONDS)).isTrue();
        OpenJiuwenExecutors.shutdownExecutors(List.of(executor), 50L, TimeUnit.MILLISECONDS);

        assertThat(taskInterrupted.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(executor.awaitTermination(2, TimeUnit.SECONDS)).isTrue();
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
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
}

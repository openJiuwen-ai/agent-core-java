/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.concurrent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

class OpenJiuwenExecutorsTest {
    @Test
    @DisplayName("DeepAgent stream 模块池默认 max(16, CPU×4) 且可配置")
    void deepAgentStreamPoolUsesCpuScaledDefaultMaxSize() throws Exception {
        int expectedDefault = OpenJiuwenExecutors.defaultDeepAgentStreamMaxSize();
        assertThat(expectedDefault).isEqualTo(Math.max(16, Runtime.getRuntime().availableProcessors() * 4));

        ExecutorService executor = OpenJiuwenExecutors.newBoundedModulePool("deep-agent-stream", true);
        try {
            assertThat(executor).isInstanceOf(ThreadPoolExecutor.class);
            ThreadPoolExecutor streamExecutor = (ThreadPoolExecutor) executor;
            assertThat(streamExecutor.getMaximumPoolSize()).isEqualTo(expectedDefault);
            assertThat(streamExecutor.getQueue()).isInstanceOf(SynchronousQueue.class);
            assertThat(streamExecutor.getRejectedExecutionHandler())
                    .isInstanceOf(ThreadPoolExecutor.CallerRunsPolicy.class);
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
}

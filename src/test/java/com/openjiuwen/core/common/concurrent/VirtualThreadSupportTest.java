/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.concurrent;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

class VirtualThreadSupportTest {
    @Test
    @DisplayName("虚拟线程能力检测与当前 JDK 版本一致")
    void supportDetectionMatchesRuntimeFeature() {
        assertThat(VirtualThreadSupport.isSupported()).isEqualTo(Runtime.version().feature() >= 21);
    }

    @Test
    @DisplayName("阻塞任务执行器在 JDK 17 使用有界平台线程，在 JDK 21 使用虚拟线程")
    void blockingTaskExecutorUsesRuntimeAppropriateThread()
            throws ExecutionException, InterruptedException, TimeoutException {
        ExecutorService executor = OpenJiuwenExecutors.newBlockingTaskExecutor("blocking-task-test", false);
        try {
            ThreadDetails details = executor.submit(VirtualThreadSupportTest::currentThreadDetails)
                    .get(2L, TimeUnit.SECONDS);

            assertThat(details.name()).startsWith("blocking-task-test-");
            if (VirtualThreadSupport.isSupported()) {
                assertThat(details.isVirtual()).isTrue();
                assertThat(details.isDaemon()).isTrue();
                assertThat(executor).isNotInstanceOf(ThreadPoolExecutor.class);
            } else {
                assertThat(details.isVirtual()).isFalse();
                assertThat(details.isDaemon()).isFalse();
                assertThat(executor).isInstanceOf(ThreadPoolExecutor.class);
            }
        } finally {
            OpenJiuwenExecutors.shutdownNowAndDeregister(executor);
            assertThat(executor.awaitTermination(2L, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    @DisplayName("固定并发阻塞任务执行器在 JDK 17 保留线程数，在 JDK 21 使用虚拟线程")
    void fixedSizeBlockingTaskExecutorPreservesPlatformThreadCount()
            throws ExecutionException, InterruptedException, TimeoutException {
        ExecutorService executor = OpenJiuwenExecutors.newBlockingTaskExecutor("fixed-blocking-task-test", 2, true);
        try {
            ThreadDetails details = executor.submit(VirtualThreadSupportTest::currentThreadDetails)
                    .get(2L, TimeUnit.SECONDS);

            assertThat(details.name()).startsWith("fixed-blocking-task-test-");
            if (VirtualThreadSupport.isSupported()) {
                assertThat(details.isVirtual()).isTrue();
                assertThat(executor).isNotInstanceOf(ThreadPoolExecutor.class);
            } else {
                assertThat(details.isVirtual()).isFalse();
                assertThat(details.isDaemon()).isTrue();
                assertThat(executor).isInstanceOf(ThreadPoolExecutor.class);
                assertThat(((ThreadPoolExecutor) executor).getMaximumPoolSize()).isEqualTo(2);
            }
        } finally {
            OpenJiuwenExecutors.shutdownNowAndDeregister(executor);
            assertThat(executor.awaitTermination(2L, TimeUnit.SECONDS)).isTrue();
        }
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
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Failed to access Thread.isVirtual", e);
        } catch (InvocationTargetException e) {
            throw new IllegalStateException("Failed to invoke Thread.isVirtual", e.getTargetException());
        }
    }

    private record ThreadDetails(String name, boolean isVirtual, boolean isDaemon) {
    }
}

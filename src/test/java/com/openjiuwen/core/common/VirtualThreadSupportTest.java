/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class VirtualThreadSupportTest {

    @Test
    void startThreadUsesVirtualThreadWhenRuntimeSupportsIt() throws Exception {
        Assumptions.assumeTrue(runtimeSupportsVirtualThreads());
        AtomicReference<Thread> runningThread = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);

        Thread started = VirtualThreadSupport.startThread("virtual-support-test", () -> {
            runningThread.set(Thread.currentThread());
            done.countDown();
        });

        assertThat(done.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(isVirtual(started)).isTrue();
        assertThat(isVirtual(runningThread.get())).isTrue();
        assertThat(started.getName()).isEqualTo("virtual-support-test");
    }

    @Test
    void newThreadPerTaskExecutorUsesVirtualThreadWhenRuntimeSupportsIt() throws Exception {
        Assumptions.assumeTrue(runtimeSupportsVirtualThreads());
        AtomicReference<Thread> runningThread = new AtomicReference<>();
        AtomicBoolean executed = new AtomicBoolean();
        ExecutorService executor = VirtualThreadSupport.newThreadPerTaskExecutor("virtual-support-executor");
        try {
            executor.submit(() -> {
                runningThread.set(Thread.currentThread());
                executed.set(true);
            }).get(5, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }

        assertThat(executed.get()).isTrue();
        assertThat(isVirtual(runningThread.get())).isTrue();
    }

    private static boolean runtimeSupportsVirtualThreads() {
        try {
            Thread.class.getMethod("isVirtual");
            Thread.class.getMethod("ofVirtual");
            return true;
        } catch (NoSuchMethodException ignored) {
            return false;
        }
    }

    private static boolean isVirtual(Thread thread) {
        assertThat(thread).isNotNull();
        try {
            Method method = Thread.class.getMethod("isVirtual");
            return (Boolean) method.invoke(thread);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(exception);
        }
    }
}

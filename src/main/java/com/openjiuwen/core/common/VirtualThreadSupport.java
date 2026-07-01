/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * JDK version compatibility utilities.
 *
 * <p>Provides runtime-adaptive thread creation: uses virtual threads
 * ({@code Thread.ofVirtual()}) on JDK 21+, falls back to cached platform
 * threads on JDK 17. All call sites compile on both JDK versions without
 * requiring multi-release JARs.</p>
 */
public final class VirtualThreadSupport {

    private static final MethodHandle THREAD_OF_VIRTUAL;
    private static final MethodHandle VIRTUAL_BUILDER_START;
    private static final MethodHandle VIRTUAL_BUILDER_NAME;
    private static final MethodHandle VIRTUAL_EXECUTOR_METHOD;

    static {
        MethodHandle ofVirtual = null;
        MethodHandle builderStart = null;
        MethodHandle builderName = null;
        MethodHandle vteMethod = null;

        try {
            ofVirtual = MethodHandles.publicLookup()
                    .findStatic(Thread.class, "ofVirtual",
                            MethodType.methodType(loadClass("java.lang.Thread$Builder")));

            Class<?> builderClass = loadClass("java.lang.Thread$Builder");
            builderStart = MethodHandles.publicLookup()
                    .findVirtual(builderClass, "start",
                            MethodType.methodType(Thread.class, Runnable.class));
            builderName = MethodHandles.publicLookup()
                    .findVirtual(builderClass, "name",
                            MethodType.methodType(builderClass, String.class));

            vteMethod = MethodHandles.publicLookup()
                    .findStatic(Executors.class, "newVirtualThreadPerTaskExecutor",
                            MethodType.methodType(ExecutorService.class));
        } catch (Exception e) {
            // JDK < 21: virtual threads not available
        }

        THREAD_OF_VIRTUAL = ofVirtual;
        VIRTUAL_BUILDER_START = builderStart;
        VIRTUAL_BUILDER_NAME = builderName;
        VIRTUAL_EXECUTOR_METHOD = vteMethod;
    }


    private VirtualThreadSupport() {
    }

    /**
     * Returns an executor that creates a new thread per task.
     * On JDK 21+ this is a virtual-thread-per-task executor;
     * on JDK 17 it falls back to a cached thread pool.
     */
    public static ExecutorService newThreadPerTaskExecutor() {
        if (VIRTUAL_EXECUTOR_METHOD != null) {
            try {
                return (ExecutorService) VIRTUAL_EXECUTOR_METHOD.invoke();
            } catch (Throwable e) {
                throw new RuntimeException("Failed to create virtual thread executor", e);
            }
        }
        return Executors.newCachedThreadPool();
    }

    /**
     * Returns an executor that creates a new thread per task.
     * On JDK 21+ this is a virtual-thread-per-task executor;
     * on JDK 17 it falls back to a cached thread pool using the given
     * name prefix for thread naming.
     */
    public static ExecutorService newThreadPerTaskExecutor(String namePrefix) {
        if (VIRTUAL_EXECUTOR_METHOD != null) {
            return newThreadPerTaskExecutor();
        }
        AtomicInteger counter = new AtomicInteger(1);
        return Executors.newCachedThreadPool(runnable -> {
            Thread thread = new Thread(runnable, namePrefix + "-" + counter.getAndIncrement());
            thread.setDaemon(true);
            return thread;
        });
    }

    /**
     * Starts a new thread to execute the given task.
     * Uses a virtual thread on JDK 21+, a daemon platform thread on JDK 17.
     */
    public static Thread startThread(Runnable task) {
        if (THREAD_OF_VIRTUAL != null) {
            try {
                Object builder = THREAD_OF_VIRTUAL.invoke();
                return (Thread) VIRTUAL_BUILDER_START.invoke(builder, task);
            } catch (Throwable e) {
                // fall through to platform thread
            }
        }
        Thread t = new Thread(task);
        t.setDaemon(true);
        t.start();
        return t;
    }

    /**
     * Starts a new named thread to execute the given task.
     * Uses a named virtual thread on JDK 21+, a named daemon platform thread on JDK 17.
     */
    public static Thread startThread(String threadName, Runnable task) {
        if (THREAD_OF_VIRTUAL != null && VIRTUAL_BUILDER_NAME != null) {
            try {
                Object builder = THREAD_OF_VIRTUAL.invoke();
                builder = VIRTUAL_BUILDER_NAME.invoke(builder, threadName);
                return (Thread) VIRTUAL_BUILDER_START.invoke(builder, task);
            } catch (Throwable e) {
                // fall through to platform thread
            }
        }
        Thread t = new Thread(task, threadName);
        t.setDaemon(true);
        t.start();
        return t;
    }

    private static Class<?> loadClass(String name) {
        try {
            return Class.forName(name);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("Class not found: " + name, e);
        }
    }
}
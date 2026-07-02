/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
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
    private static final MethodHandle VIRTUAL_BUILDER_NAME_WITH_COUNTER;
    private static final MethodHandle VIRTUAL_BUILDER_FACTORY;
    private static final MethodHandle VIRTUAL_EXECUTOR_METHOD;
    private static final MethodHandle THREAD_PER_TASK_EXECUTOR_METHOD;
    private static final MethodHandle THREAD_IS_VIRTUAL;

    static {
        MethodHandle ofVirtual = null;
        MethodHandle builderStart = null;
        MethodHandle builderName = null;
        MethodHandle builderNameWithCounter = null;
        MethodHandle builderFactory = null;
        MethodHandle vteMethod = null;
        MethodHandle threadPerTaskExecutorMethod = null;
        MethodHandle threadIsVirtual = null;

        try {
            MethodHandles.Lookup lookup = MethodHandles.publicLookup();
            Class<?> virtualBuilderClass = loadClass("java.lang.Thread$Builder$OfVirtual");
            ofVirtual = lookup.unreflect(Thread.class.getMethod("ofVirtual"));
            builderStart = lookup.unreflect(virtualBuilderClass.getMethod("start", Runnable.class));
            builderName = lookup.unreflect(virtualBuilderClass.getMethod("name", String.class));
            builderNameWithCounter = lookup.unreflect(virtualBuilderClass.getMethod("name", String.class, long.class));
            builderFactory = lookup.unreflect(virtualBuilderClass.getMethod("factory"));
            vteMethod = lookup.unreflect(Executors.class.getMethod("newVirtualThreadPerTaskExecutor"));
            threadPerTaskExecutorMethod = lookup.unreflect(Executors.class.getMethod(
                    "newThreadPerTaskExecutor", ThreadFactory.class));
            Method isVirtual = Thread.class.getMethod("isVirtual");
            threadIsVirtual = lookup.unreflect(isVirtual);
        } catch (Exception e) {
            // JDK < 21: virtual threads not available
        }

        THREAD_OF_VIRTUAL = ofVirtual;
        VIRTUAL_BUILDER_START = builderStart;
        VIRTUAL_BUILDER_NAME = builderName;
        VIRTUAL_BUILDER_NAME_WITH_COUNTER = builderNameWithCounter;
        VIRTUAL_BUILDER_FACTORY = builderFactory;
        VIRTUAL_EXECUTOR_METHOD = vteMethod;
        THREAD_PER_TASK_EXECUTOR_METHOD = threadPerTaskExecutorMethod;
        THREAD_IS_VIRTUAL = threadIsVirtual;
    }


    private VirtualThreadSupport() {
    }

    /**
     * Returns whether the current runtime exposes JDK virtual thread APIs.
     */
    public static boolean isVirtualThreadSupported() {
        return THREAD_OF_VIRTUAL != null && VIRTUAL_BUILDER_START != null && THREAD_IS_VIRTUAL != null;
    }

    /**
     * Returns whether the current thread is a virtual thread.
     */
    public static boolean isCurrentThreadVirtual() {
        return isVirtual(Thread.currentThread());
    }

    /**
     * Returns whether the given thread is a virtual thread.
     */
    public static boolean isVirtual(Thread thread) {
        if (thread == null || THREAD_IS_VIRTUAL == null) {
            return false;
        }
        try {
            return (boolean) THREAD_IS_VIRTUAL.invoke(thread);
        } catch (Throwable e) {
            return false;
        }
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
        if (THREAD_OF_VIRTUAL != null
                && VIRTUAL_BUILDER_NAME_WITH_COUNTER != null
                && VIRTUAL_BUILDER_FACTORY != null
                && THREAD_PER_TASK_EXECUTOR_METHOD != null) {
            try {
                Object builder = THREAD_OF_VIRTUAL.invoke();
                Object namedBuilder = VIRTUAL_BUILDER_NAME_WITH_COUNTER.invoke(builder, namePrefix + "-", 1L);
                ThreadFactory factory = (ThreadFactory) VIRTUAL_BUILDER_FACTORY.invoke(namedBuilder);
                return (ExecutorService) THREAD_PER_TASK_EXECUTOR_METHOD.invoke(factory);
            } catch (Throwable e) {
                throw new RuntimeException("Failed to create named virtual thread executor", e);
            }
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

/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common;

import java.lang.reflect.InvocationTargetException;
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
 *
 * @since 0.1.14
 */
public final class VirtualThreadSupport {

    private static final int MINIMUM_VIRTUAL_THREAD_VERSION = 21;
    private static final Method THREAD_OF_VIRTUAL;
    private static final Method VIRTUAL_BUILDER_START;
    private static final Method VIRTUAL_BUILDER_NAME;
    private static final Method VIRTUAL_BUILDER_NAME_WITH_COUNTER;
    private static final Method VIRTUAL_BUILDER_UNCAUGHT;
    private static final Method VIRTUAL_BUILDER_UNSTARTED;
    private static final Method VIRTUAL_BUILDER_FACTORY;
    private static final Method VIRTUAL_EXECUTOR_METHOD;
    private static final Method THREAD_PER_TASK_EXECUTOR_METHOD;
    private static final Method THREAD_IS_VIRTUAL;

    static {
        Method ofVirtual = null;
        Method builderStart = null;
        Method builderName = null;
        Method builderNameWithCounter = null;
        Method builderUncaught = null;
        Method builderUnstarted = null;
        Method builderFactory = null;
        Method vteMethod = null;
        Method threadPerTaskExecutorMethod = null;
        Method threadIsVirtual = null;

        if (Runtime.version().feature() >= MINIMUM_VIRTUAL_THREAD_VERSION) {
            try {
                Class<?> virtualBuilderClass = loadClass("java.lang.Thread$Builder$OfVirtual");
                ofVirtual = Thread.class.getMethod("ofVirtual");
                builderStart = virtualBuilderClass.getMethod("start", Runnable.class);
                builderName = virtualBuilderClass.getMethod("name", String.class);
                builderNameWithCounter = virtualBuilderClass.getMethod("name", String.class, long.class);
                builderUncaught = virtualBuilderClass.getMethod(
                        "uncaughtExceptionHandler", Thread.UncaughtExceptionHandler.class);
                builderUnstarted = virtualBuilderClass.getMethod("unstarted", Runnable.class);
                builderFactory = virtualBuilderClass.getMethod("factory");
                vteMethod = Executors.class.getMethod("newVirtualThreadPerTaskExecutor");
                threadPerTaskExecutorMethod = Executors.class.getMethod(
                        "newThreadPerTaskExecutor", ThreadFactory.class);
                threadIsVirtual = Thread.class.getMethod("isVirtual");
            } catch (NoSuchMethodException | SecurityException | IllegalStateException | NullPointerException ignored) {
                // Current runtime does not expose stable virtual-thread APIs.
            }
        }

        THREAD_OF_VIRTUAL = ofVirtual;
        VIRTUAL_BUILDER_START = builderStart;
        VIRTUAL_BUILDER_NAME = builderName;
        VIRTUAL_BUILDER_NAME_WITH_COUNTER = builderNameWithCounter;
        VIRTUAL_BUILDER_UNCAUGHT = builderUncaught;
        VIRTUAL_BUILDER_UNSTARTED = builderUnstarted;
        VIRTUAL_BUILDER_FACTORY = builderFactory;
        VIRTUAL_EXECUTOR_METHOD = vteMethod;
        THREAD_PER_TASK_EXECUTOR_METHOD = threadPerTaskExecutorMethod;
        THREAD_IS_VIRTUAL = threadIsVirtual;
    }

    private VirtualThreadSupport() {
    }

    /**
     * Returns whether the current runtime exposes JDK virtual thread APIs.
     *
     * @return {@code true} when virtual-thread APIs are available
     */
    public static boolean isVirtualThreadSupported() {
        return THREAD_OF_VIRTUAL != null && VIRTUAL_BUILDER_START != null && THREAD_IS_VIRTUAL != null;
    }

    /**
     * Returns whether the current thread is a virtual thread.
     *
     * @return {@code true} when the current thread is virtual
     */
    public static boolean isCurrentThreadVirtual() {
        return isVirtual(Thread.currentThread());
    }

    /**
     * Returns whether the given thread is a virtual thread.
     *
     * @param thread thread to inspect
     * @return {@code true} when {@code thread} is virtual
     */
    public static boolean isVirtual(Thread thread) {
        if (thread == null || THREAD_IS_VIRTUAL == null) {
            return false;
        }
        Object result = invokeQuietly(THREAD_IS_VIRTUAL, thread);
        if (result instanceof Boolean value) {
            return value;
        }
        return false;
    }

    /**
     * Returns an executor that creates a new thread per task.
     * On JDK 21+ this is a virtual-thread-per-task executor;
     * on JDK 17 it falls back to a cached thread pool.
     *
     * @return per-task executor
     */
    public static ExecutorService newThreadPerTaskExecutor() {
        if (VIRTUAL_EXECUTOR_METHOD != null) {
            Object executor = invokeQuietly(VIRTUAL_EXECUTOR_METHOD, null);
            if (executor instanceof ExecutorService service) {
                return service;
            }
        }
        return Executors.newCachedThreadPool();
    }

    /**
     * Returns an executor that creates a new thread per task.
     * On JDK 21+ this is a virtual-thread-per-task executor;
     * on JDK 17 it falls back to a cached thread pool using the given
     * name prefix for thread naming.
     *
     * @param namePrefix thread name prefix
     * @return per-task executor
     */
    public static ExecutorService newThreadPerTaskExecutor(String namePrefix) {
        return newThreadPerTaskExecutor(namePrefix, null);
    }

    /**
     * Returns a named per-task executor, optionally with an uncaught-exception handler.
     * On JDK 21+ this uses virtual threads; on JDK 17 it falls back to a cached pool.
     *
     * @param namePrefix thread name prefix
     * @param exceptionHandler optional uncaught-exception handler
     * @return per-task executor
     */
    public static ExecutorService newThreadPerTaskExecutor(String namePrefix,
            Thread.UncaughtExceptionHandler exceptionHandler) {
        ExecutorService virtualExecutor = tryNamedVirtualExecutor(namePrefix, exceptionHandler);
        if (virtualExecutor != null) {
            return virtualExecutor;
        }
        AtomicInteger counter = new AtomicInteger(1);
        return Executors.newCachedThreadPool(runnable -> {
            Thread thread = new Thread(runnable, namePrefix + "-" + counter.getAndIncrement());
            thread.setDaemon(true);
            if (exceptionHandler != null) {
                thread.setUncaughtExceptionHandler(exceptionHandler);
            }
            return thread;
        });
    }

    /**
     * Creates an unstarted virtual thread, or {@code null} when the runtime has no VT APIs.
     *
     * @param threadName thread name
     * @param task task to run
     * @param exceptionHandler optional uncaught-exception handler
     * @return unstarted virtual thread, or {@code null}
     */
    public static Thread newUnstartedThread(String threadName, Runnable task,
            Thread.UncaughtExceptionHandler exceptionHandler) {
        if (THREAD_OF_VIRTUAL == null || VIRTUAL_BUILDER_NAME == null || VIRTUAL_BUILDER_UNSTARTED == null) {
            return null;
        }
        Object builder = invokeQuietly(THREAD_OF_VIRTUAL, null);
        Object namedBuilder = invokeQuietly(VIRTUAL_BUILDER_NAME, builder, threadName);
        if (exceptionHandler != null && VIRTUAL_BUILDER_UNCAUGHT != null) {
            namedBuilder = invokeQuietly(VIRTUAL_BUILDER_UNCAUGHT, namedBuilder, exceptionHandler);
        }
        Object started = invokeQuietly(VIRTUAL_BUILDER_UNSTARTED, namedBuilder, task);
        if (started instanceof Thread thread) {
            return thread;
        }
        return null;
    }

    /**
     * Starts a new thread to execute the given task.
     * Uses a virtual thread on JDK 21+, a daemon platform thread on JDK 17.
     *
     * @param task task to run
     * @return started thread
     */
    public static Thread startThread(Runnable task) {
        if (THREAD_OF_VIRTUAL != null && VIRTUAL_BUILDER_START != null) {
            Object builder = invokeQuietly(THREAD_OF_VIRTUAL, null);
            Object started = invokeQuietly(VIRTUAL_BUILDER_START, builder, task);
            if (started instanceof Thread thread) {
                return thread;
            }
        }
        Thread platformThread = new Thread(task);
        platformThread.setDaemon(true);
        platformThread.start();
        return platformThread;
    }

    /**
     * Starts a new named thread to execute the given task.
     * Uses a named virtual thread on JDK 21+, a named daemon platform thread on JDK 17.
     *
     * @param threadName thread name
     * @param task task to run
     * @return started thread
     */
    public static Thread startThread(String threadName, Runnable task) {
        if (THREAD_OF_VIRTUAL != null && VIRTUAL_BUILDER_NAME != null && VIRTUAL_BUILDER_START != null) {
            Object builder = invokeQuietly(THREAD_OF_VIRTUAL, null);
            Object namedBuilder = invokeQuietly(VIRTUAL_BUILDER_NAME, builder, threadName);
            Object started = invokeQuietly(VIRTUAL_BUILDER_START, namedBuilder, task);
            if (started instanceof Thread thread) {
                return thread;
            }
        }
        Thread platformThread = new Thread(task, threadName);
        platformThread.setDaemon(true);
        platformThread.start();
        return platformThread;
    }

    private static ExecutorService tryNamedVirtualExecutor(
            String namePrefix,
            Thread.UncaughtExceptionHandler exceptionHandler) {
        if (THREAD_OF_VIRTUAL == null
                || VIRTUAL_BUILDER_NAME_WITH_COUNTER == null
                || VIRTUAL_BUILDER_FACTORY == null
                || THREAD_PER_TASK_EXECUTOR_METHOD == null) {
            return null;
        }
        Object builder = invokeQuietly(THREAD_OF_VIRTUAL, null);
        Object namedBuilder = invokeQuietly(
                VIRTUAL_BUILDER_NAME_WITH_COUNTER, builder, namePrefix + "-", 1L);
        if (exceptionHandler != null && VIRTUAL_BUILDER_UNCAUGHT != null) {
            namedBuilder = invokeQuietly(VIRTUAL_BUILDER_UNCAUGHT, namedBuilder, exceptionHandler);
        }
        Object factory = invokeQuietly(VIRTUAL_BUILDER_FACTORY, namedBuilder);
        Object executor = invokeQuietly(THREAD_PER_TASK_EXECUTOR_METHOD, null, factory);
        if (executor instanceof ExecutorService service) {
            return service;
        }
        return null;
    }

    private static Object invokeQuietly(Method method, Object target, Object... args) {
        if (method == null) {
            return null;
        }
        try {
            return method.invoke(target, args);
        } catch (IllegalAccessException | InvocationTargetException | IllegalArgumentException ignored) {
            return null;
        }
    }

    private static Class<?> loadClass(String name) {
        try {
            return Class.forName(name);
        } catch (ClassNotFoundException exception) {
            throw new IllegalStateException("Class not found: " + name, exception);
        }
    }
}

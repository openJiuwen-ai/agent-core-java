/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.concurrent;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

/**
 * 通过反射访问 JDK 21 虚拟线程接口，使项目继续使用 JDK 17 编译和运行。
 *
 * @since 0.1.14
 */
final class VirtualThreadSupport {
    private static final int MINIMUM_VIRTUAL_THREAD_VERSION = 21;
    private static final Optional<VirtualThreadMethods> VIRTUAL_THREAD_METHODS = resolveVirtualThreadMethods();

    private VirtualThreadSupport() {
    }

    static boolean isSupported() {
        return VIRTUAL_THREAD_METHODS.isPresent();
    }

    static ExecutorService newVirtualExecutor(String threadNamePrefix,
            Thread.UncaughtExceptionHandler exceptionHandler) {
        Objects.requireNonNull(threadNamePrefix, "threadNamePrefix");
        Objects.requireNonNull(exceptionHandler, "exceptionHandler");
        VirtualThreadMethods methods = VIRTUAL_THREAD_METHODS.orElseThrow(() ->
                new IllegalStateException("Virtual threads are not available on this Java runtime"));
        try {
            Object builder = methods.ofVirtual().invoke(null);
            Object namedBuilder = methods.name().invoke(builder, threadNamePrefix + "-", 1L);
            Object configuredBuilder = methods.uncaughtExceptionHandler().invoke(namedBuilder, exceptionHandler);
            ThreadFactory threadFactory = ThreadFactory.class.cast(methods.factory().invoke(configuredBuilder));
            return ExecutorService.class.cast(methods.newThreadPerTaskExecutor().invoke(null, threadFactory));
        } catch (IllegalAccessException exception) {
            throw new IllegalStateException("Failed to access JDK virtual thread interfaces", exception);
        } catch (InvocationTargetException exception) {
            throw new IllegalStateException("Failed to create virtual thread executor", exception.getTargetException());
        }
    }

    /**
     * 创建一个已配置但未启动的虚拟线程。
     *
     * <p>JDK 21 及以上返回虚拟线程，JDK 17 返回 {@code null}（调用方应回退到平台线程）。</p>
     *
     * @param runnable 任务
     * @param threadName 线程名
     * @param exceptionHandler 未捕获异常处理器
     * @return 已配置但未启动的虚拟线程；当前运行时不支持虚拟线程时返回 {@code null}
     */
    static Thread newVirtualThread(Runnable runnable, String threadName,
            Thread.UncaughtExceptionHandler exceptionHandler) {
        Objects.requireNonNull(runnable, "runnable");
        Objects.requireNonNull(threadName, "threadName");
        Objects.requireNonNull(exceptionHandler, "exceptionHandler");
        VirtualThreadMethods methods = VIRTUAL_THREAD_METHODS.orElse(null);
        if (methods == null) {
            return null;
        }
        try {
            Object builder = methods.ofVirtual().invoke(null);
            Object namedBuilder = methods.nameSingle().invoke(builder, threadName);
            Object configuredBuilder = methods.uncaughtExceptionHandler().invoke(namedBuilder, exceptionHandler);
            return Thread.class.cast(methods.unstarted().invoke(configuredBuilder, runnable));
        } catch (IllegalAccessException exception) {
            throw new IllegalStateException("Failed to access JDK virtual thread interfaces", exception);
        } catch (InvocationTargetException exception) {
            throw new IllegalStateException("Failed to create virtual thread", exception.getTargetException());
        }
    }

    private static Optional<VirtualThreadMethods> resolveVirtualThreadMethods() {
        if (Runtime.version().feature() < MINIMUM_VIRTUAL_THREAD_VERSION) {
            return Optional.empty();
        }
        try {
            Method ofVirtual = Thread.class.getMethod("ofVirtual");
            Class<?> builderClass = ofVirtual.getReturnType();
            Method name = builderClass.getMethod("name", String.class, long.class);
            Method nameSingle = builderClass.getMethod("name", String.class);
            Method uncaughtExceptionHandler = builderClass.getMethod(
                    "uncaughtExceptionHandler", Thread.UncaughtExceptionHandler.class);
            Method factory = builderClass.getMethod("factory");
            Method unstarted = builderClass.getMethod("unstarted", Runnable.class);
            Method newThreadPerTaskExecutor = Executors.class.getMethod(
                    "newThreadPerTaskExecutor", ThreadFactory.class);
            return Optional.of(new VirtualThreadMethods(ofVirtual, name, nameSingle, uncaughtExceptionHandler,
                    factory, unstarted, newThreadPerTaskExecutor));
        } catch (NoSuchMethodException ignored) {
            // 当前运行时没有提供稳定的虚拟线程接口，统一执行器将继续使用平台线程。
            return Optional.empty();
        }
    }

    private record VirtualThreadMethods(Method ofVirtual, Method name, Method nameSingle,
            Method uncaughtExceptionHandler, Method factory, Method unstarted,
            Method newThreadPerTaskExecutor) {
    }
}

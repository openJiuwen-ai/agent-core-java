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
 * 通过反射访问 JDK 21 虚拟线程 API，保持产物可在 JDK 17 上加载。
 */
final class VirtualThreadSupport {
    private static final int MINIMUM_VIRTUAL_THREAD_VERSION = 21;
    private static final Optional<VirtualThreadMethods> VIRTUAL_THREAD_METHODS = resolveVirtualThreadMethods();

    private VirtualThreadSupport() {
    }

    static boolean isSupported() {
        return VIRTUAL_THREAD_METHODS.isPresent();
    }

    static ExecutorService newVirtualExecutor(String threadNamePrefix) {
        Objects.requireNonNull(threadNamePrefix, "threadNamePrefix");
        VirtualThreadMethods methods = VIRTUAL_THREAD_METHODS.orElseThrow(() ->
                new IllegalStateException("Virtual threads are not available on this Java runtime"));
        try {
            Object builder = methods.ofVirtual().invoke(null);
            Object namedBuilder = methods.name().invoke(builder, threadNamePrefix + "-", 1L);
            ThreadFactory threadFactory = ThreadFactory.class.cast(methods.factory().invoke(namedBuilder));
            return ExecutorService.class.cast(methods.newThreadPerTaskExecutor().invoke(null, threadFactory));
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Failed to access JDK virtual thread APIs", e);
        } catch (InvocationTargetException e) {
            throw new IllegalStateException("Failed to create virtual thread executor", e.getTargetException());
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
            Method factory = builderClass.getMethod("factory");
            Method newThreadPerTaskExecutor = Executors.class.getMethod(
                    "newThreadPerTaskExecutor", ThreadFactory.class);
            return Optional.of(new VirtualThreadMethods(ofVirtual, name, factory, newThreadPerTaskExecutor));
        } catch (NoSuchMethodException ignored) {
            // 运行时未提供所需的稳定虚拟线程 API，
            // 统一入口会回退到现有有界平台线程池。
            return Optional.empty();
        }
    }

    private record VirtualThreadMethods(Method ofVirtual, Method name, Method factory,
                                        Method newThreadPerTaskExecutor) {
    }
}

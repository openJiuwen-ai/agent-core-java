/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.store.graph;

import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.common.logging.Loggers;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Factory class to assemble graph store instances.
 *
 * <p>Mirrors Python's {@code GraphStoreFactory} in
 * {@code openjiuwen/core/foundation/store/graph/base.py}.</p>
 */
public final class GraphStoreFactory {

    private static final Map<String, Class<?>> CLASS_MAP = new LinkedHashMap<>();
    private static final ReentrantLock THREAD_LOCK = new ReentrantLock();
    private static final String DEFAULT_MILVUS_SUPPORT_CLASS =
            "com.openjiuwen.core.foundation.store.graph.milvus.MilvusGraphStorePackage";
    private static String milvusSupportClass = DEFAULT_MILVUS_SUPPORT_CLASS;

    private GraphStoreFactory() {
        throw ErrorHelper.buildError(
                StatusCode.STORE_GRAPH_FACTORY_NOT_INSTANTIABLE,
                "class_name",
                GraphStoreFactory.class.getSimpleName()
        );
    }

    public static void registerBackend(String name, Class<?> backend) {
        registerBackend(name, backend, false);
    }

    public static void registerBackend(String name, Class<?> backend, boolean force) {
        THREAD_LOCK.lock();
        try {
            if (name == null || name.isEmpty()) {
                throw ErrorHelper.buildError(
                        StatusCode.STORE_GRAPH_BACKEND_NAME_INVALID,
                        "error_msg",
                        "Backend name cannot be registered as an empty value."
                );
            }
            if (CLASS_MAP.containsKey(name) && !force) {
                Class<?> existing = CLASS_MAP.get(name);
                throw ErrorHelper.buildError(
                        StatusCode.STORE_GRAPH_BACKEND_ALREADY_EXISTS,
                        "name",
                        name,
                        "existing",
                        existing == null ? "null" : existing.getSimpleName()
                );
            }
            if (backend == null || !GraphStore.class.isAssignableFrom(backend)) {
                String errorMessage = name + " did not implement GraphStore Protocol!";
                if (!force) {
                    throw ErrorHelper.buildError(
                            StatusCode.STORE_GRAPH_PROTOCOL_NOT_IMPLEMENTED,
                            "error_msg",
                            errorMessage
                    );
                }
                Loggers.STORE.warning(errorMessage);
            }
            CLASS_MAP.put(name, backend);
            Loggers.STORE.info("Graph Store registered: %s", name);
        } finally {
            THREAD_LOCK.unlock();
        }
    }

    public static GraphStore fromConfig(GraphConfig config) {
        return fromConfig(config, null, Map.of());
    }

    public static GraphStore fromConfig(GraphConfig config, String backendName) {
        return fromConfig(config, backendName, Map.of());
    }

    public static GraphStore fromConfig(GraphConfig config, String backendName, Map<String, Object> kwargs) {
        THREAD_LOCK.lock();
        try {
            String name = backendName != null && !backendName.isEmpty() ? backendName : config.getBackend();
            if (CLASS_MAP.containsKey(name)) {
                return createBackend(name, CLASS_MAP.get(name), config, kwargs == null ? Map.of() : kwargs);
            }
            if ("milvus".equals(name)) {
                registerMilvusSupport();
                if (CLASS_MAP.containsKey(name)) {
                    return createBackend(name, CLASS_MAP.get(name), config, kwargs == null ? Map.of() : kwargs);
                }
            }
            throw ErrorHelper.buildError(StatusCode.STORE_GRAPH_BACKEND_NOT_FOUND, "name", name);
        } finally {
            THREAD_LOCK.unlock();
        }
    }

    public static boolean isRegistered(String name) {
        THREAD_LOCK.lock();
        try {
            return CLASS_MAP.containsKey(name);
        } finally {
            THREAD_LOCK.unlock();
        }
    }

    public static Class<?> getBackendClass(String name) {
        THREAD_LOCK.lock();
        try {
            return CLASS_MAP.get(name);
        } finally {
            THREAD_LOCK.unlock();
        }
    }

    static void clearRegistryForTest() {
        THREAD_LOCK.lock();
        try {
            CLASS_MAP.clear();
            milvusSupportClass = DEFAULT_MILVUS_SUPPORT_CLASS;
        } finally {
            THREAD_LOCK.unlock();
        }
    }

    static void setMilvusSupportClassForTest(String className) {
        THREAD_LOCK.lock();
        try {
            milvusSupportClass = className == null || className.isBlank()
                    ? DEFAULT_MILVUS_SUPPORT_CLASS
                    : className;
        } finally {
            THREAD_LOCK.unlock();
        }
    }

    private static GraphStore createBackend(String name,
                                            Class<?> backendClass,
                                            GraphConfig config,
                                            Map<String, Object> kwargs) {
        if (backendClass == null || !GraphStore.class.isAssignableFrom(backendClass)) {
            throw ErrorHelper.buildError(
                    StatusCode.STORE_GRAPH_PROTOCOL_NOT_IMPLEMENTED,
                    "error_msg",
                    name + " did not implement GraphStore Protocol!"
            );
        }
        try {
            Method method = backendClass.getMethod("fromConfig", GraphConfig.class, Map.class);
            if (!Modifier.isStatic(method.getModifiers())) {
                throw new NoSuchMethodException(method.getName());
            }
            Object result = method.invoke(null, config, kwargs);
            if (result instanceof GraphStore store) {
                return store;
            }
            throw ErrorHelper.buildError(
                    StatusCode.STORE_GRAPH_PROTOCOL_NOT_IMPLEMENTED,
                    "error_msg",
                    name + " did not implement GraphStore Protocol!"
            );
        } catch (NoSuchMethodException exception) {
            return createBackendWithConfigOnly(name, backendClass, config);
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new IllegalStateException("Failed to create graph store backend: " + name, cause);
        } catch (IllegalAccessException exception) {
            throw new IllegalStateException("Failed to create graph store backend: " + name, exception);
        }
    }

    private static GraphStore createBackendWithConfigOnly(String name, Class<?> backendClass, GraphConfig config) {
        try {
            Method method = backendClass.getMethod("fromConfig", GraphConfig.class);
            if (!Modifier.isStatic(method.getModifiers())) {
                throw new NoSuchMethodException(method.getName());
            }
            Object result = method.invoke(null, config);
            if (result instanceof GraphStore store) {
                return store;
            }
            throw ErrorHelper.buildError(
                    StatusCode.STORE_GRAPH_PROTOCOL_NOT_IMPLEMENTED,
                    "error_msg",
                    name + " did not implement GraphStore Protocol!"
            );
        } catch (NoSuchMethodException exception) {
            throw ErrorHelper.buildError(
                    StatusCode.STORE_GRAPH_PROTOCOL_NOT_IMPLEMENTED,
                    "error_msg",
                    name + " did not implement GraphStore Protocol!"
            );
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new IllegalStateException("Failed to create graph store backend: " + name, cause);
        } catch (IllegalAccessException exception) {
            throw new IllegalStateException("Failed to create graph store backend: " + name, exception);
        }
    }

    private static void registerMilvusSupport() {
        try {
            Class<?> supportClass = Class.forName(milvusSupportClass);
            Method method = supportClass.getMethod("registerMilvusSupport");
            method.invoke(null);
        } catch (ClassNotFoundException ignored) {
            // The milvus package translation registers this backend when T01304 is completed.
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new IllegalStateException("Failed to register Milvus graph support", cause);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Failed to register Milvus graph support", exception);
        }
    }
}

/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.spi.store.query;

import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe registry for database-specific query language definitions.
 * <p>
 * Replaces the Python module-level {@code QUERY_EXPR_FUNCTIONS} dict.
 * 
 * @since 0.1.7
 */
public final class QueryLanguageRegistry {
    private static final Map<String, QueryLanguageDefinition> DEFINITIONS = new ConcurrentHashMap<>();
    private static volatile boolean builtInRegistered = false;

    /**
     * QueryLanguageRegistry.
     * 
     * @since 0.1.7
     */
    private QueryLanguageRegistry() {
        // static utility
    }

    /**
     * Ensure built-in dialects (milvus, chroma) are registered.
     * Called automatically on first {@link #get(String)} invocation.
     * 
     * @since 0.1.7
     */
    private static void ensureBuiltInRegistered() {
        if (!builtInRegistered) {
            synchronized (QueryLanguageRegistry.class) {
                if (!builtInRegistered) {
                    try {
                        Class<?> registration =
                            Class.forName("com.openjiuwen.core.foundation.store.query.QueryDialectRegistration");
                        registration.getMethod("ensureRegistered").invoke(null);
                    } catch (Exception ignored) {
                        // Foundation module may not be on classpath; skip auto-registration
                    }
                    builtInRegistered = true;
                }
            }
        }
    }

    /**
     * Register a database query language definition.
     * 
     * @param name database name (e.g. "milvus", "chroma")
     * @param definition the query language definition
     * @since 0.1.7
     */
    public static void register(String name, QueryLanguageDefinition definition) {
        DEFINITIONS.put(name, definition);
    }

    /**
     * Retrieve a registered definition, throwing if not found.
     * 
     * @param name database name
     * @return the registered definition
     * @since 0.1.7
     */
    public static QueryLanguageDefinition get(String name) {
        ensureBuiltInRegistered();
        QueryLanguageDefinition def = DEFINITIONS.get(name);
        if (def == null) {
            throw ErrorHelper.buildError(StatusCode.RETRIEVAL_VECTOR_STORE_QUERY_INVALID, "reason",
                    "Database query language " + name + " not registered via QueryLanguageRegistry.register()");
        }
        return def;
    }

    /**
     * Check whether a language is registered.
     * 
     * @param name name
     * @return the result
     * @since 0.1.7
     */
    public static boolean isRegistered(String name) {
        return DEFINITIONS.containsKey(name);
    }
}

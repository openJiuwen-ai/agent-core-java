/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
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
 * </p>
 */
public final class QueryLanguageRegistry {

    private static final Map<String, QueryLanguageDefinition> DEFINITIONS = new ConcurrentHashMap<>();
    private static volatile boolean builtInRegistered = false;

    private QueryLanguageRegistry() {
        // static utility
    }

    /**
     * Ensure built-in dialects (milvus, chroma) are registered.
     * Called automatically on first {@link #get(String)} invocation.
     */
    private static void ensureBuiltInRegistered() {
        if (!builtInRegistered) {
            synchronized (QueryLanguageRegistry.class) {
                if (!builtInRegistered) {
                    try {
                        Class<?> registration = Class.forName(
                                "com.openjiuwen.spi.store.query.QueryDialectRegistration");
                        registration.getMethod("ensureRegistered").invoke(null);
                    } catch (Exception ignored) {
                        // Dialect registration class may not be on classpath; skip auto-registration
                    }
                    builtInRegistered = true;
                }
            }
        }
    }

    /**
     * Register a database query language definition.
     *
     * @param name       database name (e.g. "milvus", "chroma")
     * @param definition the query language definition
     */
    public static void register(String name, QueryLanguageDefinition definition) {
        DEFINITIONS.put(name, definition);
    }

    /**
     * Register a database query language definition with optional force overwrite.
     * <p>
     * This method provides API compatibility with the 0.1.14 core package's
     * {@code QueryLanguageRegistry.registerDatabaseQueryLanguage()}.
     * </p>
     *
     * @param name       database name (e.g. "milvus", "chroma")
     * @param definition the query language definition
     * @param force      if true, overwrite existing registration
     */
    public static void registerDatabaseQueryLanguage(String name, QueryLanguageDefinition definition, boolean force) {
        if (force) {
            DEFINITIONS.put(name, definition);
        } else {
            QueryLanguageDefinition previous = DEFINITIONS.putIfAbsent(name, definition);
            if (previous != null) {
                throw ErrorHelper.buildError(StatusCode.RETRIEVAL_VECTOR_STORE_QUERY_INVALID,
                        "reason", "Database query language for name='" + name + "' already registered");
            }
        }
    }

    /**
     * Register a database query language definition (no force).
     *
     * @param name       database name
     * @param definition the query language definition
     */
    public static void registerDatabaseQueryLanguage(String name, QueryLanguageDefinition definition) {
        registerDatabaseQueryLanguage(name, definition, false);
    }

    /**
     * Retrieve a registered definition, throwing if not found.
     *
     * @param name database name
     * @return the registered definition
     */
    public static QueryLanguageDefinition get(String name) {
        ensureBuiltInRegistered();
        QueryLanguageDefinition def = DEFINITIONS.get(name);
        if (def == null) {
            throw ErrorHelper.buildError(StatusCode.RETRIEVAL_VECTOR_STORE_QUERY_INVALID,
                    "reason", "Database query language " + name
                            + " not registered via QueryLanguageRegistry.register()");
        }
        return def;
    }

    /**
     * Check whether a language is registered.
     *
     * @param name database name
     * @return true if the language is registered
     */
    public static boolean isRegistered(String name) {
        return DEFINITIONS.containsKey(name);
    }
}

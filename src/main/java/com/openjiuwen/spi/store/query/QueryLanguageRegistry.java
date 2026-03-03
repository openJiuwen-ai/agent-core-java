/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
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
 */
public final class QueryLanguageRegistry {

    private static final Map<String, QueryLanguageDefinition> DEFINITIONS = new ConcurrentHashMap<>();

    private QueryLanguageRegistry() {
        // static utility
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
     * Retrieve a registered definition, throwing if not found.
     *
     * @param name database name
     * @return the registered definition
     */
    public static QueryLanguageDefinition get(String name) {
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
     */
    public static boolean isRegistered(String name) {
        return DEFINITIONS.containsKey(name);
    }
}

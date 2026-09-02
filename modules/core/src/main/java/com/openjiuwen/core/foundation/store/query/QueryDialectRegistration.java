/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.store.query;

import com.openjiuwen.spi.store.query.QueryLanguageRegistry;

/**
 * Registers built-in query dialect implementations for Milvus and Chroma.
 * <p>
 * Call {@link #ensureRegistered()} once during application startup to register
 * the built-in query dialects. This mirrors Python's auto-registration in
 * {@code store/query/__init__.py}.
 * 
 * @since 0.1.7
 */
public final class QueryDialectRegistration {
    private static volatile boolean registered = false;

    /**
     * QueryDialectRegistration.
     * 
     * @since 0.1.7
     */
    private QueryDialectRegistration() {
    }

    /**
     * Register built-in query dialect implementations (idempotent).
     * 
     * @since 0.1.7
     */
    public static void ensureRegistered() {
        if (!registered) {
            synchronized (QueryDialectRegistration.class) {
                if (!registered) {
                    QueryLanguageRegistry.register("milvus", MilvusQueryDialect.definition());
                    QueryLanguageRegistry.register("chroma", ChromaQueryDialect.definition());
                    registered = true;
                }
            }
        }
    }
}

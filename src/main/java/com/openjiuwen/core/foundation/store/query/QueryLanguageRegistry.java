/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.store.query;

import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.common.logging.LoggerProtocol;

import java.util.Objects;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Mirrors Python's {@code openjiuwen.core.foundation.store.query.registry} in
 * {@code openjiuwen/core/foundation/store/query/registry.py}.
 */
public final class QueryLanguageRegistry {

    private static final LoggerProtocol LOGGER = Loggers.STORE;
    private static final ReentrantLock QUERY_LANGUAGE_REGISTER_LOCK = new ReentrantLock();

    private QueryLanguageRegistry() {
    }

    public static void registerDatabaseQueryLanguage(String name, QueryLanguageDefinition definition) {
        registerDatabaseQueryLanguage(name, definition, false);
    }

    public static void registerDatabaseQueryLanguage(String name,
                                                     QueryLanguageDefinition definition,
                                                     boolean force) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(definition, "definition");
        QUERY_LANGUAGE_REGISTER_LOCK.lock();
        try {
            if (QueryExpr.isLanguageRegistered(name) && !force) {
                QueryExpr.raiseQueryError("Database query language for name='" + name + "' already registered");
            }
            QueryExpr.registerLanguage(name, definition, true);
            LOGGER.info("Registered query expression support for {}", name);
        } finally {
            QUERY_LANGUAGE_REGISTER_LOCK.unlock();
        }
    }
}

/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.spi.store.kv;

import com.openjiuwen.core.foundation.store.kv.SqliteKVStore;
import com.openjiuwen.spi.store.BaseKVStore;
import com.openjiuwen.spi.store.KVStoreProvider;

import java.util.Map;

/**
 * Built-in provider for SQLite key-value stores.
 *
 * @since 0.1.14
 */
public final class SqliteKVStoreProvider implements KVStoreProvider {
    private static final String DEFAULT_DATABASE_PATH = "checkpointer";
    private static final int DEFAULT_TIMEOUT_SECONDS = 30;

    /** {@inheritDoc} */
    @Override
    public String typeName() {
        return "sqlite";
    }

    /** {@inheritDoc} */
    @Override
    public BaseKVStore create(Map<String, Object> conf) {
        Map<String, Object> safeConf = conf == null ? Map.of() : conf;
        String databasePath = readDatabasePath(safeConf.get("db_path"));
        int timeoutSeconds = readTimeout(safeConf.get("db_timeout"));
        boolean isWalEnabled = readWalEnabled(safeConf.get("db_enable_wal"));
        return new SqliteKVStore(databasePath, timeoutSeconds, isWalEnabled);
    }

    private static String readDatabasePath(Object configuredPath) {
        if (configuredPath == null) {
            return DEFAULT_DATABASE_PATH;
        }
        if (configuredPath instanceof String databasePath) {
            return databasePath;
        }
        throw new IllegalArgumentException("SQLite db_path must be a string");
    }

    private static int readTimeout(Object configuredTimeout) {
        if (configuredTimeout == null) {
            return DEFAULT_TIMEOUT_SECONDS;
        }
        if (configuredTimeout instanceof Integer timeoutSeconds) {
            return timeoutSeconds;
        }
        if (configuredTimeout instanceof Short timeoutSeconds) {
            return timeoutSeconds.intValue();
        }
        if (configuredTimeout instanceof Byte timeoutSeconds) {
            return timeoutSeconds.intValue();
        }
        if (configuredTimeout instanceof Long timeoutSeconds) {
            if (timeoutSeconds > Integer.MAX_VALUE || timeoutSeconds < Integer.MIN_VALUE) {
                throw new IllegalArgumentException("SQLite db_timeout is outside the supported integer range");
            }
            return timeoutSeconds.intValue();
        }
        throw new IllegalArgumentException("SQLite db_timeout must be an integer number of seconds");
    }

    private static boolean readWalEnabled(Object configuredWal) {
        if (configuredWal == null) {
            return true;
        }
        if (configuredWal instanceof Boolean isWalEnabled) {
            return isWalEnabled;
        }
        throw new IllegalArgumentException("SQLite db_enable_wal must be a boolean");
    }
}

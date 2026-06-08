/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.store.kv;

import com.openjiuwen.core.foundation.store.BasedKVStorePipeline;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.Disabled;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DbBasedKVStoreTest {

    @TempDir
    Path tempDir;

    @AfterEach
    void cleanupDatabaseFiles() throws Exception {
        if (tempDir != null && Files.exists(tempDir)) {
            try (var paths = Files.walk(tempDir)) {
                paths.sorted((left, right) -> right.getNameCount() - left.getNameCount())
                        .forEach(path -> {
                            try {
                                Files.deleteIfExists(path);
                            } catch (Exception ignored) {
                                // Mirrors Python cleanup behavior that ignores Windows file-lock cleanup failures.
                            }
                        });
            }
        }
    }

    @Test
    void sqliteKvStoreBehaviorMatchesPythonBaseline() {
        DbBasedKVStore kvStore = new DbBasedKVStore(sqliteDataSource());

        runDefaultKvStore(kvStore);
    }

    @Disabled("Skipped in Python source: no mysql environment")
    @Test
    void mysqlKvStoreIsDisabledLikePythonSource() {
        // Python baseline: tests/unit_tests/core/foundation/store/test_db_based_kv_store.py::test_mysql_kv_store
        // skip reason: no mysql environment
    }

    private void runDefaultKvStore(DbBasedKVStore kvStore) {
        kvStore.set("key1", "value1").join();
        assertThat(kvStore.get("key1").join()).isEqualTo("value1");
        kvStore.set("key1", "update_value1").join();
        assertThat(kvStore.get("key1").join()).isEqualTo("update_value1");
        assertThat(kvStore.exclusiveSet("key1", "update_value2", null).join()).isFalse();
        assertThat(kvStore.get("key1").join()).isEqualTo("update_value1");

        kvStore.set("key2", "value2").join();
        kvStore.set("key3", "value3").join();
        kvStore.set("key345", "value345").join();
        kvStore.set("key3456", "value3456").join();
        kvStore.set("key4", "value4").join();

        assertThat(kvStore.get("key2").join()).isEqualTo("value2");
        kvStore.delete("key2").join();
        assertThat(kvStore.exists("key2").join()).isFalse();
        assertThat(kvStore.getByPrefix("key3").join())
                .containsExactly(
                        Map.entry("key3", "value3"),
                        Map.entry("key345", "value345"),
                        Map.entry("key3456", "value3456"));
        kvStore.deleteByPrefix("key3", null).join();
        assertThat(kvStore.getByPrefix("key3").join()).isEmpty();
        assertThat(kvStore.mget(List.of("key4", "key53245", "key1")).join())
                .containsExactly("value4", null, "update_value1");

        assertThat(kvStore.exclusiveSet("exclusive_key", "exclusive_value", 1).join()).isTrue();
        assertThat(kvStore.get("exclusive_key").join()).isEqualTo("exclusive_value");
        assertThat(kvStore.exclusiveSet("exclusive_key", "update_exclusive_value", 1).join()).isFalse();
        sleep(1100L);
        assertThat(kvStore.exclusiveSet("exclusive_key", "update_exclusive_value", 1).join()).isTrue();
        assertThat(kvStore.get("exclusive_key").join()).isEqualTo("update_exclusive_value");

        kvStore.set("key56", "10").join();
        assertThat(kvStore.get("key56").join()).isEqualTo("10");

        kvStore.set("bytes", "hello".getBytes()).join();
        assertThat((byte[]) kvStore.get("bytes").join()).isEqualTo("hello".getBytes());

        assertThat(kvStore.batchDelete(List.of("key1", "key4", "missing"), 2).join()).isEqualTo(2);
        assertThat(kvStore.exists("key1").join()).isFalse();
        assertThat(kvStore.exists("key4").join()).isFalse();

        BasedKVStorePipeline pipeline = kvStore.pipeline();
        pipeline.set("pipe-set", "value", null).join();
        pipeline.get("pipe-set").join();
        pipeline.exists("key56").join();
        assertThat(pipeline.execute().join()).containsExactly(null, "value", true);
    }

    private JdbcDataSource sqliteDataSource() {
        JdbcDataSource dataSource = new JdbcDataSource();
        String dbPath = tempDir.resolve("kv_store").toAbsolutePath().toString().replace('\\', '/');
        dataSource.setURL("jdbc:h2:file:" + dbPath + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");
        dataSource.setPassword("");
        try (Connection ignored = dataSource.getConnection()) {
            // Force database creation so path cleanup mirrors the Python fixture lifecycle.
        } catch (SQLException exception) {
            throw new RuntimeException("Failed to create H2 datasource", exception);
        }
        return dataSource;
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while waiting for expiry", exception);
        }
    }
}

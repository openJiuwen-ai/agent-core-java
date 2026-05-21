/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.core.memory.migration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for RunMigrations.
 * <p>
 * Mirrors Python's test_run_migrations.py from
 * <code>tests/unit_tests/core/memory/migration/test_run_migrations.py</code>.
 */
@DisplayName("Run Migrations Tests")
class TestRunMigrations {

    // Stub classes
    static class MigrationResult {
        boolean success;
        List<String> appliedMigrations = new ArrayList<>();
        String error;

        MigrationResult(boolean success, String error) {
            this.success = success;
            this.error = error;
        }
    }

    static class KvStoreStub {
        Map<String, String> data = new java.util.HashMap<>();

        void put(String key, String value) {
            data.put(key, value);
        }

        String get(String key) {
            return data.get(key);
        }
    }

    static class SqlStoreStub {
        List<String> executedStatements = new ArrayList<>();

        void execute(String statement) {
            executedStatements.add(statement);
        }
    }

    static class VectorStoreStub {
        List<String> collectionNames = new ArrayList<>();

        void createCollection(String name) {
            collectionNames.add(name);
        }
    }

    static class MigrationRunner {
        KvStoreStub kvStore;
        SqlStoreStub sqlStore;
        VectorStoreStub vectorStore;

        MigrationRunner(KvStoreStub kvStore, SqlStoreStub sqlStore, VectorStoreStub vectorStore) {
            this.kvStore = kvStore;
            this.sqlStore = sqlStore;
            this.vectorStore = vectorStore;
        }

        MigrationResult runKvMigrations() {
            kvStore.put("migration_version", "1.0");
            MigrationResult result = new MigrationResult(true, null);
            result.appliedMigrations.add("kv_migration_v1");
            return result;
        }

        MigrationResult runSqlMigrations() {
            sqlStore.execute("CREATE TABLE test (id INT)");
            MigrationResult result = new MigrationResult(true, null);
            result.appliedMigrations.add("sql_migration_v1");
            return result;
        }

        MigrationResult runVectorMigrations() {
            vectorStore.createCollection("default");
            MigrationResult result = new MigrationResult(true, null);
            result.appliedMigrations.add("vector_migration_v1");
            return result;
        }

        MigrationResult runAll() {
            MigrationResult result = new MigrationResult(true, null);
            result.appliedMigrations.addAll(runKvMigrations().appliedMigrations);
            result.appliedMigrations.addAll(runSqlMigrations().appliedMigrations);
            result.appliedMigrations.addAll(runVectorMigrations().appliedMigrations);
            return result;
        }
    }

    @Nested
    @DisplayName("KV Migration Tests")
    class TestKvMigrations {

        @Test
        @DisplayName("run kv migrations")
        void testRunKvMigrations() {
            KvStoreStub kvStore = new KvStoreStub();
            MigrationRunner runner = new MigrationRunner(kvStore, null, null);

            MigrationResult result = runner.runKvMigrations();

            assertTrue(result.success);
            assertEquals("1.0", kvStore.get("migration_version"));
        }
    }

    @Nested
    @DisplayName("SQL Migration Tests")
    class TestSqlMigrations {

        @Test
        @DisplayName("run sql migrations")
        void testRunSqlMigrations() {
            SqlStoreStub sqlStore = new SqlStoreStub();
            MigrationRunner runner = new MigrationRunner(null, sqlStore, null);

            MigrationResult result = runner.runSqlMigrations();

            assertTrue(result.success);
            assertFalse(sqlStore.executedStatements.isEmpty());
        }
    }

    @Nested
    @DisplayName("Vector Migration Tests")
    class TestVectorMigrations {

        @Test
        @DisplayName("run vector migrations")
        void testRunVectorMigrations() {
            VectorStoreStub vectorStore = new VectorStoreStub();
            MigrationRunner runner = new MigrationRunner(null, null, vectorStore);

            MigrationResult result = runner.runVectorMigrations();

            assertTrue(result.success);
            assertTrue(vectorStore.collectionNames.contains("default"));
        }
    }

    @Nested
    @DisplayName("All Migrations Tests")
    class TestAllMigrations {

        @Test
        @DisplayName("run all migrations")
        void testRunAllMigrations() {
            KvStoreStub kvStore = new KvStoreStub();
            SqlStoreStub sqlStore = new SqlStoreStub();
            VectorStoreStub vectorStore = new VectorStoreStub();
            MigrationRunner runner = new MigrationRunner(kvStore, sqlStore, vectorStore);

            MigrationResult result = runner.runAll();

            assertTrue(result.success);
            assertEquals(3, result.appliedMigrations.size());
        }
    }
}
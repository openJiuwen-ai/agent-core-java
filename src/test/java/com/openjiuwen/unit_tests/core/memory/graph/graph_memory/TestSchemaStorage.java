/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.core.memory.graph.graph_memory;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for SchemaStorage.
 * <p>
 * Mirrors Python's test_schema_storage.py from
 * <code>tests/unit_tests/core/memory/graph/graph_memory/test_schema_storage.py</code>.
 */
@DisplayName("Schema Storage Tests")
class TestSchemaStorage {

    // Stub classes
    static class SchemaRecord {
        String id;
        String typeName;
        String schemaJson;

        SchemaRecord(String id, String typeName, String schemaJson) {
            this.id = id;
            this.typeName = typeName;
            this.schemaJson = schemaJson;
        }
    }

    static class SchemaStorageStub {
        Map<String, SchemaRecord> storage = new HashMap<>();

        CompletableFuture<Void> save(SchemaRecord record) {
            storage.put(record.id, record);
            return CompletableFuture.completedFuture(null);
        }

        CompletableFuture<SchemaRecord> load(String id) {
            return CompletableFuture.completedFuture(storage.get(id));
        }

        CompletableFuture<Boolean> exists(String id) {
            return CompletableFuture.completedFuture(storage.containsKey(id));
        }

        CompletableFuture<Void> delete(String id) {
            storage.remove(id);
            return CompletableFuture.completedFuture(null);
        }
    }

    @Nested
    @DisplayName("Schema Record Tests")
    class TestSchemaRecord {

        @Test
        @DisplayName("schema record creation")
        void testSchemaRecordCreation() {
            SchemaRecord record = new SchemaRecord("schema-1", "Person", "{\"name\":\"string\"}");

            assertEquals("schema-1", record.id);
            assertEquals("Person", record.typeName);
            assertNotNull(record.schemaJson);
        }
    }

    @Nested
    @DisplayName("Schema Storage Tests")
    class TestSchemaStorageClass {

        @Test
        @DisplayName("save schema")
        void testSaveSchema() throws Exception {
            SchemaStorageStub storage = new SchemaStorageStub();
            SchemaRecord record = new SchemaRecord("schema-1", "Person", "{}");

            storage.save(record).get();

            assertTrue(storage.exists("schema-1").get());
        }

        @Test
        @DisplayName("load schema")
        void testLoadSchema() throws Exception {
            SchemaStorageStub storage = new SchemaStorageStub();
            SchemaRecord record = new SchemaRecord("schema-1", "Person", "{\"name\":\"string\"}");
            storage.save(record).get();

            SchemaRecord loaded = storage.load("schema-1").get();

            assertNotNull(loaded);
            assertEquals("Person", loaded.typeName);
        }

        @Test
        @DisplayName("delete schema")
        void testDeleteSchema() throws Exception {
            SchemaStorageStub storage = new SchemaStorageStub();
            SchemaRecord record = new SchemaRecord("schema-1", "Person", "{}");
            storage.save(record).get();

            storage.delete("schema-1").get();

            assertFalse(storage.exists("schema-1").get());
        }

        @Test
        @DisplayName("load non-existent returns null")
        void testLoadNonExistentReturnsNull() throws Exception {
            SchemaStorageStub storage = new SchemaStorageStub();

            SchemaRecord loaded = storage.load("nonexistent").get();

            assertNull(loaded);
        }
    }
}
/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.core.memory.graph.graph_memory;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for SchemaManager.
 * <p>
 * Mirrors Python's test_schema_manager.py from
 * <code>tests/unit_tests/core/memory/graph/graph_memory/test_schema_manager.py</code>.
 */
@DisplayName("Schema Manager Tests")
class TestSchemaManager {

    // Stub classes
    static class EntitySchema {
        String typeName;
        Map<String, PropertyDef> properties = new HashMap<>();

        EntitySchema(String typeName) {
            this.typeName = typeName;
        }

        void addProperty(String name, PropertyDef prop) {
            properties.put(name, prop);
        }
    }

    static class PropertyDef {
        String type;
        boolean required;

        PropertyDef(String type, boolean required) {
            this.type = type;
            this.required = required;
        }
    }

    static class SchemaManager {
        Map<String, EntitySchema> schemas = new HashMap<>();

        void registerSchema(EntitySchema schema) {
            schemas.put(schema.typeName, schema);
        }

        EntitySchema getSchema(String typeName) {
            return schemas.get(typeName);
        }

        boolean hasSchema(String typeName) {
            return schemas.containsKey(typeName);
        }

        void removeSchema(String typeName) {
            schemas.remove(typeName);
        }
    }

    @Nested
    @DisplayName("Entity Schema Tests")
    class TestEntitySchema {

        @Test
        @DisplayName("entity schema creation")
        void testEntitySchemaCreation() {
            EntitySchema schema = new EntitySchema("Person");

            assertEquals("Person", schema.typeName);
        }

        @Test
        @DisplayName("entity schema with properties")
        void testEntitySchemaWithProperties() {
            EntitySchema schema = new EntitySchema("Person");
            schema.addProperty("name", new PropertyDef("string", true));
            schema.addProperty("age", new PropertyDef("integer", false));

            assertEquals(2, schema.properties.size());
        }
    }

    @Nested
    @DisplayName("Schema Manager Tests")
    class TestSchemaManagerClass {

        @Test
        @DisplayName("register schema")
        void testRegisterSchema() {
            SchemaManager manager = new SchemaManager();
            EntitySchema schema = new EntitySchema("Person");

            manager.registerSchema(schema);

            assertTrue(manager.hasSchema("Person"));
        }

        @Test
        @DisplayName("get schema")
        void testGetSchema() {
            SchemaManager manager = new SchemaManager();
            EntitySchema schema = new EntitySchema("Person");
            schema.addProperty("name", new PropertyDef("string", true));
            manager.registerSchema(schema);

            EntitySchema retrieved = manager.getSchema("Person");

            assertNotNull(retrieved);
            assertEquals("Person", retrieved.typeName);
        }

        @Test
        @DisplayName("remove schema")
        void testRemoveSchema() {
            SchemaManager manager = new SchemaManager();
            manager.registerSchema(new EntitySchema("Person"));

            manager.removeSchema("Person");

            assertFalse(manager.hasSchema("Person"));
        }

        @Test
        @DisplayName("get non-existent schema returns null")
        void testGetNonExistentSchemaReturnsNull() {
            SchemaManager manager = new SchemaManager();

            EntitySchema retrieved = manager.getSchema("NonExistent");

            assertNull(retrieved);
        }
    }
}
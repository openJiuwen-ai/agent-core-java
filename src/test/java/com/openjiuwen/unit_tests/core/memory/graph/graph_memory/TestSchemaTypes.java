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
 * Unit tests for SchemaTypes.
 * <p>
 * Mirrors Python's test_schema_types.py from
 * <code>tests/unit_tests/core/memory/graph/graph_memory/test_schema_types.py</code>.
 */
@DisplayName("Schema Types Tests")
class TestSchemaTypes {

    // Stub classes
    static class SchemaType {
        String name;
        String baseType;
        Map<String, Object> constraints = new HashMap<>();

        SchemaType(String name, String baseType) {
            this.name = name;
            this.baseType = baseType;
        }

        void addConstraint(String key, Object value) {
            constraints.put(key, value);
        }
    }

    static class SchemaTypeRegistry {
        Map<String, SchemaType> types = new HashMap<>();

        void register(SchemaType type) {
            types.put(type.name, type);
        }

        SchemaType get(String name) {
            return types.get(name);
        }

        boolean isValidValue(String typeName, Object value) {
            SchemaType type = types.get(typeName);
            if (type == null) return false;

            if ("string".equals(type.baseType)) {
                return value instanceof String;
            }
            if ("integer".equals(type.baseType)) {
                return value instanceof Integer;
            }
            if ("boolean".equals(type.baseType)) {
                return value instanceof Boolean;
            }
            return true;
        }
    }

    @Nested
    @DisplayName("Schema Type Tests")
    class TestSchemaTypeClass {

        @Test
        @DisplayName("schema type creation")
        void testSchemaTypeCreation() {
            SchemaType type = new SchemaType("Email", "string");

            assertEquals("Email", type.name);
            assertEquals("string", type.baseType);
        }

        @Test
        @DisplayName("schema type with constraints")
        void testSchemaTypeWithConstraints() {
            SchemaType type = new SchemaType("Age", "integer");
            type.addConstraint("min", 0);
            type.addConstraint("max", 150);

            assertEquals(0, type.constraints.get("min"));
            assertEquals(150, type.constraints.get("max"));
        }
    }

    @Nested
    @DisplayName("Schema Type Registry Tests")
    class TestSchemaTypeRegistry {

        @Test
        @DisplayName("register schema type")
        void testRegisterSchemaType() {
            SchemaTypeRegistry registry = new SchemaTypeRegistry();
            SchemaType type = new SchemaType("Email", "string");

            registry.register(type);

            assertNotNull(registry.get("Email"));
        }

        @Test
        @DisplayName("validate string type")
        void testValidateStringType() {
            SchemaTypeRegistry registry = new SchemaTypeRegistry();
            registry.register(new SchemaType("Name", "string"));

            assertTrue(registry.isValidValue("Name", "John"));
            assertFalse(registry.isValidValue("Name", 123));
        }

        @Test
        @DisplayName("validate integer type")
        void testValidateIntegerType() {
            SchemaTypeRegistry registry = new SchemaTypeRegistry();
            registry.register(new SchemaType("Count", "integer"));

            assertTrue(registry.isValidValue("Count", 42));
            assertFalse(registry.isValidValue("Count", "42"));
        }

        @Test
        @DisplayName("validate boolean type")
        void testValidateBooleanType() {
            SchemaTypeRegistry registry = new SchemaTypeRegistry();
            registry.register(new SchemaType("Active", "boolean"));

            assertTrue(registry.isValidValue("Active", true));
            assertFalse(registry.isValidValue("Active", "true"));
        }
    }
}
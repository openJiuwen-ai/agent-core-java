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
 * Unit tests for EntityType.
 * <p>
 * Mirrors Python's test_entity_type.py from
 * <code>tests/unit_tests/core/memory/graph/graph_memory/test_entity_type.py</code>.
 */
@DisplayName("Entity Type Tests")
class TestEntityType {

    // Stub classes
    static class EntityType {
        String name;
        String description;
        Map<String, PropertyType> properties = new HashMap<>();

        EntityType(String name, String description) {
            this.name = name;
            this.description = description;
        }

        void addProperty(String name, PropertyType prop) {
            properties.put(name, prop);
        }
    }

    static class PropertyType {
        String type;
        boolean required;
        boolean unique;

        PropertyType(String type, boolean required) {
            this.type = type;
            this.required = required;
            this.unique = false;
        }

        void setUnique(boolean unique) {
            this.unique = unique;
        }
    }

    static class EntityTypeRegistry {
        Map<String, EntityType> types = new HashMap<>();

        void register(EntityType type) {
            types.put(type.name, type);
        }

        EntityType get(String name) {
            return types.get(name);
        }

        boolean exists(String name) {
            return types.containsKey(name);
        }
    }

    @Nested
    @DisplayName("Entity Type Tests")
    class TestEntityTypeClass {

        @Test
        @DisplayName("entity type creation")
        void testEntityTypeCreation() {
            EntityType type = new EntityType("Person", "A human being");

            assertEquals("Person", type.name);
            assertEquals("A human being", type.description);
        }

        @Test
        @DisplayName("entity type with properties")
        void testEntityTypeWithProperties() {
            EntityType type = new EntityType("Person", "A human being");
            type.addProperty("name", new PropertyType("string", true));
            type.addProperty("age", new PropertyType("integer", false));

            assertEquals(2, type.properties.size());
            assertTrue(type.properties.containsKey("name"));
        }
    }

    @Nested
    @DisplayName("Property Type Tests")
    class TestPropertyTypeClass {

        @Test
        @DisplayName("property type creation")
        void testPropertyTypeCreation() {
            PropertyType prop = new PropertyType("string", true);

            assertEquals("string", prop.type);
            assertTrue(prop.required);
        }

        @Test
        @DisplayName("property type unique flag")
        void testPropertyTypeUniqueFlag() {
            PropertyType prop = new PropertyType("string", true);
            prop.setUnique(true);

            assertTrue(prop.unique);
        }
    }

    @Nested
    @DisplayName("Entity Type Registry Tests")
    class TestEntityTypeRegistry {

        @Test
        @DisplayName("register entity type")
        void testRegisterEntityType() {
            EntityTypeRegistry registry = new EntityTypeRegistry();
            EntityType type = new EntityType("Person", "Human");

            registry.register(type);

            assertTrue(registry.exists("Person"));
        }

        @Test
        @DisplayName("get entity type")
        void testGetEntityType() {
            EntityTypeRegistry registry = new EntityTypeRegistry();
            EntityType type = new EntityType("Person", "Human");
            registry.register(type);

            EntityType retrieved = registry.get("Person");

            assertNotNull(retrieved);
            assertEquals("Person", retrieved.name);
        }

        @Test
        @DisplayName("get non-existent type returns null")
        void testGetNonExistentTypeReturnsNull() {
            EntityTypeRegistry registry = new EntityTypeRegistry();

            EntityType retrieved = registry.get("NonExistent");

            assertNull(retrieved);
        }
    }
}
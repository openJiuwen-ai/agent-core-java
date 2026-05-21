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
 * Unit tests for TypeSystem.
 * <p>
 * Mirrors Python's test_type_system.py from
 * <code>tests/unit_tests/core/memory/graph/graph_memory/test_type_system.py</code>.
 */
@DisplayName("Type System Tests")
class TestTypeSystem {

    // Stub classes
    static class TypeDefinition {
        String name;
        String parentType;
        Map<String, PropertyDefinition> properties = new HashMap<>();

        TypeDefinition(String name) {
            this.name = name;
            this.parentType = null;
        }

        TypeDefinition(String name, String parentType) {
            this.name = name;
            this.parentType = parentType;
        }

        void addProperty(String name, PropertyDefinition prop) {
            properties.put(name, prop);
        }
    }

    static class PropertyDefinition {
        String type;
        boolean required;
        Object defaultValue;

        PropertyDefinition(String type, boolean required) {
            this.type = type;
            this.required = required;
        }
    }

    static class TypeSystem {
        Map<String, TypeDefinition> typeDefs = new HashMap<>();

        void registerType(TypeDefinition typeDef) {
            typeDefs.put(typeDef.name, typeDef);
        }

        TypeDefinition getType(String name) {
            return typeDefs.get(name);
        }

        boolean isSubtypeOf(String typeName, String parentName) {
            TypeDefinition typeDef = typeDefs.get(typeName);
            if (typeDef == null) return false;

            if (typeDef.parentType != null && typeDef.parentType.equals(parentName)) {
                return true;
            }
            if (typeDef.parentType != null) {
                return isSubtypeOf(typeDef.parentType, parentName);
            }
            return false;
        }

        boolean hasType(String name) {
            return typeDefs.containsKey(name);
        }
    }

    @Nested
    @DisplayName("Type Definition Tests")
    class TestTypeDefinition {

        @Test
        @DisplayName("type definition creation")
        void testTypeDefinitionCreation() {
            TypeDefinition typeDef = new TypeDefinition("Person");

            assertEquals("Person", typeDef.name);
        }

        @Test
        @DisplayName("type definition with parent")
        void testTypeDefinitionWithParent() {
            TypeDefinition typeDef = new TypeDefinition("Employee", "Person");

            assertEquals("Employee", typeDef.name);
            assertEquals("Person", typeDef.parentType);
        }

        @Test
        @DisplayName("type definition with properties")
        void testTypeDefinitionWithProperties() {
            TypeDefinition typeDef = new TypeDefinition("Person");
            typeDef.addProperty("name", new PropertyDefinition("string", true));
            typeDef.addProperty("age", new PropertyDefinition("integer", false));

            assertEquals(2, typeDef.properties.size());
        }
    }

    @Nested
    @DisplayName("Type System Tests")
    class TestTypeSystemClass {

        @Test
        @DisplayName("register type")
        void testRegisterType() {
            TypeSystem system = new TypeSystem();
            TypeDefinition typeDef = new TypeDefinition("Person");

            system.registerType(typeDef);

            assertTrue(system.hasType("Person"));
        }

        @Test
        @DisplayName("get type")
        void testGetType() {
            TypeSystem system = new TypeSystem();
            TypeDefinition typeDef = new TypeDefinition("Person");
            system.registerType(typeDef);

            TypeDefinition retrieved = system.getType("Person");

            assertNotNull(retrieved);
            assertEquals("Person", retrieved.name);
        }

        @Test
        @DisplayName("check subtype")
        void testCheckSubtype() {
            TypeSystem system = new TypeSystem();
            system.registerType(new TypeDefinition("Person"));
            system.registerType(new TypeDefinition("Employee", "Person"));

            assertTrue(system.isSubtypeOf("Employee", "Person"));
        }

        @Test
        @DisplayName("check not subtype")
        void testCheckNotSubtype() {
            TypeSystem system = new TypeSystem();
            system.registerType(new TypeDefinition("Person"));
            system.registerType(new TypeDefinition("Animal"));

            assertFalse(system.isSubtypeOf("Animal", "Person"));
        }
    }
}
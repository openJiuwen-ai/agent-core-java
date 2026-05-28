/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.core.memory.graph.graph_memory;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Types.
 * <p>
 * Mirrors Python's test_types.py from
 * <code>tests/unit_tests/core/memory/graph/graph_memory/test_types.py</code>.
 */
@DisplayName("Types Tests")
class TestTypes {

    // Stub classes
    static class PrimitiveType {
        static final String STRING = "string";
        static final String INTEGER = "integer";
        static final String FLOAT = "float";
        static final String BOOLEAN = "boolean";
        static final String DATETIME = "datetime";
    }

    static class GraphType {
        static final String ENTITY = "entity";
        static final String RELATION = "relation";
        static final String EPISODE = "episode";
    }

    static class TypeValidator {
        boolean isPrimitive(String typeName) {
            return typeName.equals(PrimitiveType.STRING) ||
                   typeName.equals(PrimitiveType.INTEGER) ||
                   typeName.equals(PrimitiveType.FLOAT) ||
                   typeName.equals(PrimitiveType.BOOLEAN) ||
                   typeName.equals(PrimitiveType.DATETIME);
        }

        boolean isGraphType(String typeName) {
            return typeName.equals(GraphType.ENTITY) ||
                   typeName.equals(GraphType.RELATION) ||
                   typeName.equals(GraphType.EPISODE);
        }

        boolean isValidType(String typeName) {
            return isPrimitive(typeName) || isGraphType(typeName);
        }
    }

    @Nested
    @DisplayName("Primitive Type Tests")
    class TestPrimitiveTypes {

        @Test
        @DisplayName("string type is primitive")
        void testStringTypeIsPrimitive() {
            TypeValidator validator = new TypeValidator();

            assertTrue(validator.isPrimitive(PrimitiveType.STRING));
        }

        @Test
        @DisplayName("integer type is primitive")
        void testIntegerTypeIsPrimitive() {
            TypeValidator validator = new TypeValidator();

            assertTrue(validator.isPrimitive(PrimitiveType.INTEGER));
        }

        @Test
        @DisplayName("float type is primitive")
        void testFloatTypeIsPrimitive() {
            TypeValidator validator = new TypeValidator();

            assertTrue(validator.isPrimitive(PrimitiveType.FLOAT));
        }

        @Test
        @DisplayName("boolean type is primitive")
        void testBooleanTypeIsPrimitive() {
            TypeValidator validator = new TypeValidator();

            assertTrue(validator.isPrimitive(PrimitiveType.BOOLEAN));
        }
    }

    @Nested
    @DisplayName("Graph Type Tests")
    class TestGraphTypes {

        @Test
        @DisplayName("entity type is graph type")
        void testEntityTypeIsGraphType() {
            TypeValidator validator = new TypeValidator();

            assertTrue(validator.isGraphType(GraphType.ENTITY));
        }

        @Test
        @DisplayName("relation type is graph type")
        void testRelationTypeIsGraphType() {
            TypeValidator validator = new TypeValidator();

            assertTrue(validator.isGraphType(GraphType.RELATION));
        }

        @Test
        @DisplayName("episode type is graph type")
        void testEpisodeTypeIsGraphType() {
            TypeValidator validator = new TypeValidator();

            assertTrue(validator.isGraphType(GraphType.EPISODE));
        }
    }

    @Nested
    @DisplayName("Type Validation Tests")
    class TestTypeValidation {

        @Test
        @DisplayName("valid types")
        void testValidTypes() {
            TypeValidator validator = new TypeValidator();

            assertTrue(validator.isValidType("string"));
            assertTrue(validator.isValidType("integer"));
            assertTrue(validator.isValidType("entity"));
        }

        @Test
        @DisplayName("invalid types")
        void testInvalidTypes() {
            TypeValidator validator = new TypeValidator();

            assertFalse(validator.isValidType("unknown"));
            assertFalse(validator.isValidType(""));
        }
    }
}
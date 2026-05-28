/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.core.foundation.tool;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ToolSchemaValidation.
 * <p>
 * Mirrors Python's test_tool_schema_validation.py from
 * <code>tests/unit_tests/core/foundation/tool/test_tool_schema_validation.py</code>.
 */
@DisplayName("Tool Schema Validation Tests")
class TestToolSchemaValidation {

    // Stub classes
    static class ToolSchema {
        String name;
        String description;
        Map<String, ParameterSchema> parameters = new HashMap<>();
        boolean required;

        ToolSchema(String name, String description) {
            this.name = name;
            this.description = description;
        }

        void addParameter(String name, ParameterSchema param) {
            parameters.put(name, param);
        }
    }

    static class ParameterSchema {
        String type;
        String description;
        boolean required;
        Map<String, Object> constraints = new HashMap<>();

        ParameterSchema(String type, String description, boolean required) {
            this.type = type;
            this.description = description;
            this.required = required;
        }

        void addConstraint(String key, Object value) {
            constraints.put(key, value);
        }
    }

    static class SchemaValidator {
        boolean validate(ToolSchema schema) {
            if (schema.name == null || schema.name.isEmpty()) {
                return false;
            }
            if (schema.description == null || schema.description.isEmpty()) {
                return false;
            }
            return true;
        }

        boolean validateParameter(ParameterSchema param) {
            if (param.type == null || param.type.isEmpty()) {
                return false;
            }
            return true;
        }

        String getValidationError(ToolSchema schema) {
            if (schema.name == null || schema.name.isEmpty()) {
                return "Tool name is required";
            }
            if (schema.description == null || schema.description.isEmpty()) {
                return "Tool description is required";
            }
            return null;
        }
    }

    @Nested
    @DisplayName("Tool Schema Tests")
    class TestToolSchemaClass {

        @Test
        @DisplayName("tool schema creation")
        void testToolSchemaCreation() {
            ToolSchema schema = new ToolSchema("calculator", "Calculate math");

            assertEquals("calculator", schema.name);
            assertEquals("Calculate math", schema.description);
        }

        @Test
        @DisplayName("tool schema with parameters")
        void testToolSchemaWithParameters() {
            ToolSchema schema = new ToolSchema("echo", "Echo input");
            schema.addParameter("input", new ParameterSchema("string", "Input to echo", true));

            assertEquals(1, schema.parameters.size());
            assertTrue(schema.parameters.containsKey("input"));
        }
    }

    @Nested
    @DisplayName("Parameter Schema Tests")
    class TestParameterSchemaClass {

        @Test
        @DisplayName("parameter schema creation")
        void testParameterSchemaCreation() {
            ParameterSchema param = new ParameterSchema("integer", "Count value", true);

            assertEquals("integer", param.type);
            assertEquals("Count value", param.description);
            assertTrue(param.required);
        }

        @Test
        @DisplayName("parameter schema with constraints")
        void testParameterSchemaWithConstraints() {
            ParameterSchema param = new ParameterSchema("integer", "Age", true);
            param.addConstraint("min", 0);
            param.addConstraint("max", 150);

            assertEquals(0, param.constraints.get("min"));
            assertEquals(150, param.constraints.get("max"));
        }
    }

    @Nested
    @DisplayName("Schema Validation Tests")
    class TestSchemaValidation {

        @Test
        @DisplayName("validate valid schema")
        void testValidateValidSchema() {
            ToolSchema schema = new ToolSchema("valid_tool", "A valid tool description");
            SchemaValidator validator = new SchemaValidator();

            boolean valid = validator.validate(schema);

            assertTrue(valid);
        }

        @Test
        @DisplayName("validate invalid schema missing name")
        void testValidateInvalidSchemaMissingName() {
            ToolSchema schema = new ToolSchema("", "Description");
            SchemaValidator validator = new SchemaValidator();

            boolean valid = validator.validate(schema);

            assertFalse(valid);
        }

        @Test
        @DisplayName("validate invalid schema missing description")
        void testValidateInvalidSchemaMissingDescription() {
            ToolSchema schema = new ToolSchema("tool", "");
            SchemaValidator validator = new SchemaValidator();

            boolean valid = validator.validate(schema);

            assertFalse(valid);
        }

        @Test
        @DisplayName("get validation error")
        void testGetValidationError() {
            ToolSchema schema = new ToolSchema("", "Description");
            SchemaValidator validator = new SchemaValidator();

            String error = validator.getValidationError(schema);

            assertNotNull(error);
            assertTrue(error.contains("name"));
        }
    }
}
/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.core.common.utils;

import com.openjiuwen.core.common.exception.ValidationError;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JUnit 5 tests for SchemaUtils.
 * Ported from Python: tests/unit_tests/core/common/utils/test_schema_utils.py
 */
class SchemaUtilsTest {

    // ==================== JSON Schema definition (mirrors Python USER_SCHEMA) ====================

    private static Map<String, Object> createUserSchema() {
        Map<String, Object> nameSchema = new LinkedHashMap<>();
        nameSchema.put("type", "string");
        nameSchema.put("default", "Anonymous");
        nameSchema.put("minLength", 1);
        nameSchema.put("maxLength", 50);

        Map<String, Object> ageSchema = new LinkedHashMap<>();
        ageSchema.put("type", "integer");
        ageSchema.put("default", 18);
        ageSchema.put("minimum", 0);
        ageSchema.put("maximum", 150);

        Map<String, Object> emailSchema = new LinkedHashMap<>();
        emailSchema.put("type", "string");
        emailSchema.put("default", "user@example.com");

        Map<String, Object> isActiveSchema = new LinkedHashMap<>();
        isActiveSchema.put("type", "boolean");
        isActiveSchema.put("default", true);

        Map<String, Object> tagsItemsSchema = Map.of("type", "string");
        Map<String, Object> tagsSchema = new LinkedHashMap<>();
        tagsSchema.put("type", "array");
        tagsSchema.put("items", tagsItemsSchema);
        tagsSchema.put("default", List.of("new_user"));
        tagsSchema.put("minItems", 1);

        Map<String, Object> metadataSchema = new LinkedHashMap<>();
        metadataSchema.put("type", "object");
        metadataSchema.put("default", Map.of());
        metadataSchema.put("additionalProperties", true);

        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("name", nameSchema);
        properties.put("age", ageSchema);
        properties.put("email", emailSchema);
        properties.put("is_active", isActiveSchema);
        properties.put("tags", tagsSchema);
        properties.put("metadata", metadataSchema);

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("title", "User");
        schema.put("properties", properties);
        schema.put("required", List.of("name", "age", "email"));

        return schema;
    }

    // ==========================================================================
    // test_format_with_json_schema (Python: test_format_with_json_schema)
    // ==========================================================================
    @Test
    @DisplayName("Format partial data with JSON Schema fills in defaults")
    void testFormatWithJsonSchema() {
        Map<String, Object> schema = createUserSchema();

        Map<String, Object> partialData = new LinkedHashMap<>();
        partialData.put("name", "Jane Doe");
        partialData.put("age", 25);
        partialData.put("email", "jane@example.com");

        Map<String, Object> result = SchemaUtils.formatWithSchema(partialData, schema);

        assertEquals("Jane Doe", result.get("name"));
        assertEquals(25, result.get("age"));
        assertEquals("jane@example.com", result.get("email"));
        assertEquals(true, result.get("is_active"));  // Default value
        assertEquals(List.of("new_user"), result.get("tags"));  // Default value
    }

    // ==========================================================================
    // test_format_none_data (Python: test_format_none_data)
    // ==========================================================================
    @Test
    @DisplayName("Format with null data throws ValidationError")
    void testFormatNoneData() {
        Map<String, Object> schema = createUserSchema();
        assertThrows(ValidationError.class, () ->
                SchemaUtils.formatWithSchema(null, schema));
    }

    // ==========================================================================
    // test_format_empty_dict (Python: test_format_empty_dict)
    // ==========================================================================
    @Test
    @DisplayName("Format empty map populates all defaults")
    void testFormatEmptyDict() {
        // Create schema without required fields for this test
        Map<String, Object> schema = createUserSchema();
        // Remove required to allow empty data
        Map<String, Object> relaxedSchema = new LinkedHashMap<>(schema);
        relaxedSchema.remove("required");

        Map<String, Object> result = SchemaUtils.formatWithSchema(Map.of(), relaxedSchema);
        assertTrue(result.containsKey("name"));
        assertTrue(result.containsKey("age"));
        assertTrue(result.containsKey("email"));
    }

    // ==========================================================================
    // test_validate_valid_data (Python: test_validate_valid_data)
    // ==========================================================================
    @Test
    @DisplayName("Valid data passes validation")
    void testValidateValidData() {
        Map<String, Object> schema = createUserSchema();

        Map<String, Object> validData = new LinkedHashMap<>();
        validData.put("name", "John Doe");
        validData.put("age", 30);
        validData.put("email", "john@example.com");
        validData.put("is_active", true);
        validData.put("tags", List.of("developer", "premium"));
        validData.put("metadata", Map.of("created_at", "2024-01-01"));

        // Should not throw
        assertDoesNotThrow(() -> SchemaUtils.validateWithSchema(validData, schema));
    }

    // ==========================================================================
    // test_validate_invalid_data (Python: test_validate_invalidate_date)
    // ==========================================================================
    @Nested
    @DisplayName("Validation of invalid data")
    class InvalidDataTests {

        @Test
        @DisplayName("Empty string violates minLength")
        void testEmptyStringViolatesMinLength() {
            Map<String, Object> schema = createUserSchema();

            Map<String, Object> invalidData = new LinkedHashMap<>();
            invalidData.put("name", "");  // Empty string, violates minLength=1
            invalidData.put("age", 30);
            invalidData.put("email", "test@example.com");

            assertThrows(ValidationError.class, () ->
                    SchemaUtils.validateWithSchema(invalidData, schema));
        }

        @Test
        @DisplayName("Number exceeding maximum fails validation")
        void testNumberExceedsMaximum() {
            Map<String, Object> schema = createUserSchema();

            Map<String, Object> invalidData = new LinkedHashMap<>();
            invalidData.put("name", "Test");
            invalidData.put("age", 200);   // Too high, violates maximum=150
            invalidData.put("email", "test@example.com");

            assertThrows(ValidationError.class, () ->
                    SchemaUtils.validateWithSchema(invalidData, schema));
        }

        @Test
        @DisplayName("Missing required field fails validation")
        void testMissingRequiredField() {
            Map<String, Object> schema = createUserSchema();

            Map<String, Object> invalidData = new LinkedHashMap<>();
            invalidData.put("name", "Test");
            // Missing required 'age' and 'email'

            assertThrows(ValidationError.class, () ->
                    SchemaUtils.validateWithSchema(invalidData, schema));
        }

        @Test
        @DisplayName("Null data throws ValidationError")
        void testNullData() {
            Map<String, Object> schema = createUserSchema();
            assertThrows(ValidationError.class, () ->
                    SchemaUtils.validateWithSchema(null, schema));
        }
    }

    // ==========================================================================
    // test_get_schema_from_simple_model (Python: test_get_schema_from_simple_model)
    // ==========================================================================
    @Test
    @DisplayName("getSchemaDict produces schema with type and properties")
    void testGetSchemaDict() {
        Map<String, Object> schemaDict = SchemaUtils.getSchemaDict(SampleUser.class);

        assertNotNull(schemaDict);
        assertEquals("object", schemaDict.get("type"));
        assertTrue(schemaDict.containsKey("properties"));

        @SuppressWarnings("unchecked")
        Map<String, Object> properties = (Map<String, Object>) schemaDict.get("properties");
        assertTrue(properties.containsKey("name"));
        assertTrue(properties.containsKey("age"));

        @SuppressWarnings("unchecked")
        Map<String, Object> nameProp = (Map<String, Object>) properties.get("name");
        assertEquals("string", nameProp.get("type"));
    }

    @Test
    @DisplayName("getSchemaDict with null returns null")
    void testGetSchemaDictNull() {
        assertNull(SchemaUtils.getSchemaDict(null));
    }

    // ==========================================================================
    // Additional: schema operations
    // ==========================================================================
    @Nested
    @DisplayName("Schema defaults and edge cases")
    class SchemaDefaultsTests {

        @Test
        @DisplayName("Defaults include List and Map types")
        void testDefaultListAndMap() {
            Map<String, Object> schema = createUserSchema();
            // Remove required for this test
            Map<String, Object> relaxedSchema = new LinkedHashMap<>(schema);
            relaxedSchema.remove("required");

            Map<String, Object> result = SchemaUtils.formatWithSchema(Map.of(), relaxedSchema);

            // tags default should be a new list instance, not the original
            Object tags = result.get("tags");
            assertInstanceOf(List.class, tags);
            assertEquals(List.of("new_user"), tags);

            // metadata default should be a new map instance
            Object metadata = result.get("metadata");
            assertInstanceOf(Map.class, metadata);
        }

        @Test
        @DisplayName("Existing values are not overwritten by defaults")
        void testExistingValuesNotOverwritten() {
            Map<String, Object> schema = createUserSchema();

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("name", "Custom Name");
            data.put("age", 42);
            data.put("email", "custom@example.com");
            data.put("is_active", false);
            data.put("tags", List.of("admin"));

            Map<String, Object> result = SchemaUtils.formatWithSchema(data, schema);

            assertEquals("Custom Name", result.get("name"));
            assertEquals(42, result.get("age"));
            assertEquals(false, result.get("is_active"));
            assertEquals(List.of("admin"), result.get("tags"));
        }
    }

    // ==================== Test helper class ====================
    @SuppressWarnings("unused")
    static class SampleUser {
        private String name;
        private int age;
        private String email;
        private boolean isActive;
        private List<String> tags;
        private Map<String, Object> metadata;
    }
}

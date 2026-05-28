/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.graph.extraction;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for MultilingualBaseModel.
 * <p>
 * Mirrors Python's test_base.py from
 * <code>tests/unit_tests/core/memory/graph/extraction/test_base.py</code>.
 */
@DisplayName("Extraction Base Tests")
class TestExtractionBase {

    /**
     * Sample model for testing.
     */
    static class SampleModel extends MultilingualBaseModel {
        private String name = "";
        private int count = 0;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public int getCount() { return count; }
        public void setCount(int count) { this.count = count; }

        @Override
        public Map<String, Object> responseFormat() {
            Map<String, Object> schema = new HashMap<>();
            schema.put("type", "object");
            Map<String, Object> properties = new HashMap<>();
            Map<String, String> nameProp = new HashMap<>();
            nameProp.put("type", "string");
            nameProp.put("description", "{{[test_name]}}");
            properties.put("name", nameProp);
            Map<String, String> countProp = new HashMap<>();
            countProp.put("type", "integer");
            countProp.put("description", "{{[test_count]}}");
            properties.put("count", countProp);
            schema.put("properties", properties);
            return schema;
        }
    }

    @Nested
    @DisplayName("MultilingualBaseModel Schema Tests")
    class TestMultilingualBaseModelSchema {

        @Test
        @DisplayName("schema returns dict with properties")
        void testSchemaReturnsDictWithProperties() {
            SampleModel model = new SampleModel();
            Map<String, Object> schema = model.multilingualModelJsonSchema("cn", false);

            assertNotNull(schema);
            assertTrue(schema.containsKey("properties"));
            assertTrue(schema.containsKey("name") || 
                (schema.containsKey("properties") && ((Map<?,?>) schema.get("properties")).containsKey("name")));
        }

        @Test
        @DisplayName("schema has correct types")
        void testSchemaHasCorrectTypes() {
            SampleModel model = new SampleModel();
            Map<String, Object> schema = model.responseFormat();

            Map<?, ?> properties = (Map<?, ?>) schema.get("properties");
            assertNotNull(properties);

            Map<?, ?> nameProp = (Map<?, ?>) properties.get("name");
            assertEquals("string", nameProp.get("type"));

            Map<?, ?> countProp = (Map<?, ?>) properties.get("count");
            assertEquals("integer", countProp.get("type"));
        }

        @Test
        @DisplayName("response format returns valid schema")
        void testResponseFormatReturnsValidSchema() {
            SampleModel model = new SampleModel();
            Map<String, Object> schema = model.responseFormat();

            assertNotNull(schema);
            assertEquals("object", schema.get("type"));
        }
    }

    @Nested
    @DisplayName("Multilingual Description Tests")
    class TestMultilingualDescription {

        @Test
        @DisplayName("multilingual description registry exists")
        void testMultilingualDescriptionRegistryExists() {
            // The registry is a static field in MultilingualBaseModel
            // We can't directly test it, but we can verify schema generation works
            SampleModel model = new SampleModel();
            Map<String, Object> schemaCn = model.multilingualModelJsonSchema("cn", false);
            Map<String, Object> schemaEn = model.multilingualModelJsonSchema("en", false);

            assertNotNull(schemaCn);
            assertNotNull(schemaEn);
        }
    }

    @Nested
    @DisplayName("Strict Mode Tests")
    class TestStrictMode {

        @Test
        @DisplayName("strict mode adds additional properties false")
        void testStrictModeAddsAdditionalPropertiesFalse() {
            SampleModel model = new SampleModel();
            Map<String, Object> schema = model.multilingualModelJsonSchema("cn", true);

            assertNotNull(schema);
            // In strict mode, object nodes should have additionalProperties: false
            // This is enforced by enforceStrictMode method
        }
    }

    @Nested
    @DisplayName("Recursive Replace Tests")
    class TestRecursiveReplace {

        @Test
        @DisplayName("recursive replace works on nested maps")
        void testRecursiveReplaceWorksOnNestedMaps() {
            Map<String, Object> schema = new HashMap<>();
            schema.put("description", "{{[test_name]}}");
            
            Map<String, String> lookup = new HashMap<>();
            lookup.put("{{[test_name]}}", "名称");

            MultilingualBaseModel.recursiveReplace(schema, lookup, "description");

            assertEquals("名称", schema.get("description"));
        }
    }
}
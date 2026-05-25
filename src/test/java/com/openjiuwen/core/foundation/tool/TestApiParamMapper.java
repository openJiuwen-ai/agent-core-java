/* *  Copyright (c) Huawei Technologies Co., Ltd. 2025-2026. All rights reserved. */
package com.openjiuwen.core.foundation.tool;

import com.openjiuwen.core.foundation.tool.schema.APIParamLocation;
import com.openjiuwen.core.foundation.tool.schema.APIParamMapper;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for APIParamMapper.
 * Mirrors Python's tests/unit_tests/core/foundation/tool/test_api_param_mapper.py
 */
class TestApiParamMapper {

    private static final Map<String, Object> SIMPLE_SCHEMA = Map.of(
            "type", "object",
            "properties", Map.of(
                    "id", Map.of("type", "integer", "location", "path"),
                    "name", Map.of("type", "string", "location", "query"),
                    "age", Map.of("type", "integer", "location", "query"),
                    "data", Map.of("type", "object", "location", "body"),
                    "auth_token", Map.of("type", "string", "location", "header")
            )
    );

    @Test
    @DisplayName("test init with simple schema")
    void testInitWithSimpleSchema() {
        APIParamMapper mapper = new APIParamMapper(SIMPLE_SCHEMA);
        assertNotNull(mapper.getSchema());
        assertEquals(SIMPLE_SCHEMA, mapper.getSchema());
        assertNotNull(mapper.getDefaults(APIParamLocation.QUERY));
        assertNotNull(mapper.getDefaults(APIParamLocation.PATH));
    }

    @Nested
    @DisplayName("Map with dict tests")
    class MapWithDictTests {

        @Test
        @DisplayName("test map with dict inputs")
        void testMapWithDict() {
            APIParamMapper mapper = new APIParamMapper(SIMPLE_SCHEMA);
            Map<String, Object> inputs = Map.of(
                    "id", 123,
                    "name", "John",
                    "age", 30,
                    "data", Map.of("key", "value"),
                    "auth_token", "abc123"
            );

            Map<APIParamLocation, Map<String, Object>> result = mapper.map(inputs);

            assertEquals(Map.of("id", 123), result.get(APIParamLocation.PATH));
            assertEquals(Map.of("name", "John", "age", 30), result.get(APIParamLocation.QUERY));
            assertEquals(Map.of("key", "value"), result.get(APIParamLocation.BODY).get("data"));
            assertEquals(Map.of("auth_token", "abc123"), result.get(APIParamLocation.HEADER));
        }
    }

    @Nested
    @DisplayName("Map with defaults tests")
    class MapWithDefaultsTests {

        @Test
        @DisplayName("test map with defaults merged")
        void testMapWithDefaults() {
            Map<String, Object> defaults = Map.of(
                    "lang", "en",
                    "format", "json"
            );
            APIParamMapper mapper = new APIParamMapper(SIMPLE_SCHEMA, defaults);

            Map<String, Object> inputs = Map.of(
                    "id", 123,
                    "name", "John"
            );

            Map<APIParamLocation, Map<String, Object>> result = mapper.map(inputs);

            // Defaults should be merged with inputs
            assertTrue(result.get(APIParamLocation.QUERY).containsKey("lang"));
            assertTrue(result.get(APIParamLocation.QUERY).containsKey("format"));
            assertEquals("John", result.get(APIParamLocation.QUERY).get("name"));
        }

        @Test
        @DisplayName("test input values override defaults")
        void testInputOverridesDefaults() {
            Map<String, Object> defaults = Map.of(
                    "lang", "en",
                    "name", "Default Name"
            );
            APIParamMapper mapper = new APIParamMapper(SIMPLE_SCHEMA, defaults);

            Map<String, Object> inputs = Map.of(
                    "id", 123,
                    "name", "Actual Name"
            );

            Map<APIParamLocation, Map<String, Object>> result = mapper.map(inputs);

            assertEquals("Actual Name", result.get(APIParamLocation.QUERY).get("name"));
        }
    }

    @Nested
    @DisplayName("Null and empty string handling")
    class NullHandlingTests {

        @Test
        @DisplayName("test null and empty string preserve defaults")
        void testNullAndEmptyPreserveDefaults() {
            Map<String, Object> defaults = Map.of(
                    "lang", "en",
                    "format", "json"
            );
            APIParamMapper mapper = new APIParamMapper(SIMPLE_SCHEMA, defaults);

            // Input null and empty should not override defaults
            Map<String, Object> inputs = Map.of(
                    "id", 123,
                    "lang", null,
                    "format", ""
            );

            Map<APIParamLocation, Map<String, Object>> result = mapper.map(inputs);

            // Null for lang means no value was set - default preserved
            assertEquals("en", result.get(APIParamLocation.QUERY).get("lang"));
            // Empty string for format preserved default
            assertEquals("json", result.get(APIParamLocation.QUERY).get("format"));
        }
    }
}
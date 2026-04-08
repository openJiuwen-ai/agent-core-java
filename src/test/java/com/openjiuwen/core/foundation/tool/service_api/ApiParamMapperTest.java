/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */
package com.openjiuwen.core.foundation.tool.service_api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ApiParamMapper.
 * Ported from Python: tests/unit_tests/core/foundation/tool/test_api_param_mapper.py
 */
class ApiParamMapperTest {

    private static final Map<String, Object> DEFAULT_SCHEMAS = Map.of(
            "type", "object",
            "properties", Map.of(
                    "id", Map.of("type", "integer", "location", "path"),
                    "name", Map.of("type", "string", "location", "query"),
                    "age", Map.of("type", "integer", "location", "query"),
                    "data", Map.of("type", "object", "location", "body"),
                    "auth_token", Map.of("type", "string", "location", "header")
            )
    );

    @Nested
    @DisplayName("Initialization tests")
    class InitTests {

        @Test
        @DisplayName("Init with dict schema stores schema and empty defaults")
        void testInitBase() {
            ApiParamMapper mapper = new ApiParamMapper(DEFAULT_SCHEMAS, null, null, null);

            // Defaults should be empty maps for each location
            Map<ApiParamLocation, Map<String, Object>> result =
                    mapper.map(Map.of(), ApiParamLocation.BODY);

            // With no inputs, query/path/header should have empty defaults
            assertTrue(result.get(ApiParamLocation.QUERY).isEmpty());
            assertTrue(result.get(ApiParamLocation.HEADER).isEmpty());
            assertTrue(result.get(ApiParamLocation.PATH).isEmpty());
        }
    }

    @Nested
    @DisplayName("Map method tests")
    class MapTests {

        @Test
        @DisplayName("Map distributes params to correct locations based on schema")
        void testMapWithDictSchema() {
            ApiParamMapper mapper = new ApiParamMapper(DEFAULT_SCHEMAS, null, null, null);

            Map<String, Object> inputs = new LinkedHashMap<>();
            inputs.put("id", 123);
            inputs.put("name", "John");
            inputs.put("age", 30);
            inputs.put("data", Map.of("key", "value"));
            inputs.put("auth_token", "abc123");

            Map<ApiParamLocation, Map<String, Object>> result =
                    mapper.map(inputs, ApiParamLocation.BODY);

            assertEquals(Map.of("id", 123), result.get(ApiParamLocation.PATH));
            assertEquals(Map.of("name", "John", "age", 30), result.get(ApiParamLocation.QUERY));
            assertEquals(Map.of("data", Map.of("key", "value")), result.get(ApiParamLocation.BODY));
            assertEquals(Map.of("auth_token", "abc123"), result.get(ApiParamLocation.HEADER));
        }

        @Test
        @DisplayName("Map with default values merges correctly")
        void testMapWithDefaultValues() {
            ApiParamMapper mapper = new ApiParamMapper(
                    DEFAULT_SCHEMAS,
                    Map.of("lang", "en", "format", "json"),  // default queries
                    Map.of("X-API-Key", "test-key"),           // default headers
                    Map.of("version", "v1")                    // default paths
            );

            Map<String, Object> inputs = new LinkedHashMap<>();
            inputs.put("id", 123);
            inputs.put("name", "John");

            Map<ApiParamLocation, Map<String, Object>> result =
                    mapper.map(inputs, ApiParamLocation.BODY);

            // Defaults should be merged with input values
            Map<String, Object> pathResult = result.get(ApiParamLocation.PATH);
            assertEquals("v1", pathResult.get("version"));
            assertEquals(123, pathResult.get("id"));

            Map<String, Object> queryResult = result.get(ApiParamLocation.QUERY);
            assertEquals("en", queryResult.get("lang"));
            assertEquals("json", queryResult.get("format"));
            assertEquals("John", queryResult.get("name"));

            Map<String, Object> headerResult = result.get(ApiParamLocation.HEADER);
            assertEquals("test-key", headerResult.get("X-API-Key"));

            assertTrue(result.get(ApiParamLocation.BODY).isEmpty());
        }

        @Test
        @DisplayName("Input values override default values")
        void testMapInputOverridesDefaults() {
            ApiParamMapper mapper = new ApiParamMapper(
                    DEFAULT_SCHEMAS,
                    Map.of("lang", "en", "name", "Default Name"),  // default queries
                    null,
                    Map.of("id", 999, "version", "v1")             // default paths
            );

            Map<String, Object> inputs = new LinkedHashMap<>();
            inputs.put("id", 123);
            inputs.put("name", "Actual Name");

            Map<ApiParamLocation, Map<String, Object>> result =
                    mapper.map(inputs, ApiParamLocation.BODY);

            // Input should override default
            assertEquals(123, result.get(ApiParamLocation.PATH).get("id"));
            assertEquals("v1", result.get(ApiParamLocation.PATH).get("version"));
            assertEquals("Actual Name", result.get(ApiParamLocation.QUERY).get("name"));
            assertEquals("en", result.get(ApiParamLocation.QUERY).get("lang"));
        }

        @Test
        @DisplayName("Map with null schema puts all inputs to default location")
        void testMapNullSchema() {
            ApiParamMapper mapper = new ApiParamMapper(null, null, null, null);

            Map<String, Object> inputs = Map.of("key1", "val1", "key2", "val2");
            Map<ApiParamLocation, Map<String, Object>> result =
                    mapper.map(inputs, ApiParamLocation.BODY);

            assertEquals(inputs, result.get(ApiParamLocation.BODY));
        }

        @Test
        @DisplayName("Map with empty inputs returns only defaults")
        void testMapEmptyInputs() {
            ApiParamMapper mapper = new ApiParamMapper(
                    DEFAULT_SCHEMAS,
                    Map.of("lang", "en"),
                    Map.of("X-Key", "abc"),
                    null
            );

            Map<ApiParamLocation, Map<String, Object>> result =
                    mapper.map(Map.of(), ApiParamLocation.BODY);

            assertEquals(Map.of("lang", "en"), result.get(ApiParamLocation.QUERY));
            assertEquals(Map.of("X-Key", "abc"), result.get(ApiParamLocation.HEADER));
            assertTrue(result.get(ApiParamLocation.PATH).isEmpty());
            assertTrue(result.get(ApiParamLocation.BODY).isEmpty());
        }
    }

    @Nested
    @DisplayName("ApiParamLocation tests")
    class ParamLocationTests {

        @Test
        @DisplayName("fromString returns correct enum values")
        void testFromString() {
            assertEquals(ApiParamLocation.QUERY, ApiParamLocation.fromString("query"));
            assertEquals(ApiParamLocation.PATH, ApiParamLocation.fromString("path"));
            assertEquals(ApiParamLocation.BODY, ApiParamLocation.fromString("body"));
            assertEquals(ApiParamLocation.HEADER, ApiParamLocation.fromString("header"));
        }

        @Test
        @DisplayName("fromString is case-insensitive")
        void testFromStringCaseInsensitive() {
            assertEquals(ApiParamLocation.QUERY, ApiParamLocation.fromString("QUERY"));
            assertEquals(ApiParamLocation.HEADER, ApiParamLocation.fromString("Header"));
        }

        @Test
        @DisplayName("fromString defaults to BODY for unknown values")
        void testFromStringDefaultsToBody() {
            assertEquals(ApiParamLocation.BODY, ApiParamLocation.fromString("unknown"));
            assertEquals(ApiParamLocation.BODY, ApiParamLocation.fromString(""));
        }
    }
}

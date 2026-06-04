/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.unit_tests.core.foundation.tool;

import com.openjiuwen.core.foundation.tool.service_api.ApiParamLocation;
import com.openjiuwen.core.foundation.tool.service_api.ApiParamMapper;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for APIParamMapper.
 * <p>
 * Mirrors Python's {@code test_api_param_mapper.py} from
 * {@code tests/unit_tests/core/foundation/tool/test_api_param_mapper.py}.
 */
@DisplayName("API Param Mapper Tests")
class TestApiParamMapper {

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

    private static final Map<String, Object> FORM_TYPE_SCHEMA = Map.of(
            "type", "object",
            "properties", Map.of(
                    "file", Map.of(
                            "type", "string",
                            "location", "form",
                            "form_handler_type", "file",
                            "description", "PDF file"),
                    "image", Map.of(
                            "type", "string",
                            "location", "form",
                            "form_handler_type", "file",
                            "description", "Image file"),
                    "name", Map.of(
                            "type", "string",
                            "location", "body",
                            "description", "File name")
            )
    );

    @Nested
    @DisplayName("Dictionary schema mapping")
    class DictionarySchemaMapping {

        @Test
        void testInitBase() {
            ApiParamMapper mapper = new ApiParamMapper(DEFAULT_SCHEMAS, null, null, null);

            Map<ApiParamLocation, Map<String, Object>> result = mapper.map(Map.of(), ApiParamLocation.BODY);

            assertTrue(result.get(ApiParamLocation.QUERY).isEmpty());
            assertTrue(result.get(ApiParamLocation.HEADER).isEmpty());
            assertTrue(result.get(ApiParamLocation.PATH).isEmpty());
            assertTrue(result.get(ApiParamLocation.BODY).isEmpty());
        }

        @Test
        void testMapWithDictSchema() {
            ApiParamMapper mapper = new ApiParamMapper(DEFAULT_SCHEMAS, null, null, null);
            Map<String, Object> inputs = new LinkedHashMap<>();
            inputs.put("id", 123);
            inputs.put("name", "John");
            inputs.put("age", 30);
            inputs.put("data", Map.of("key", "value"));
            inputs.put("auth_token", "abc123");

            Map<ApiParamLocation, Map<String, Object>> result = mapper.map(inputs, ApiParamLocation.BODY);

            assertEquals(Map.of("id", 123), result.get(ApiParamLocation.PATH));
            assertEquals(Map.of("name", "John", "age", 30), result.get(ApiParamLocation.QUERY));
            assertEquals(Map.of("data", Map.of("key", "value")), result.get(ApiParamLocation.BODY));
            assertEquals(Map.of("auth_token", "abc123"), result.get(ApiParamLocation.HEADER));
        }

        @Test
        void testMapWithPydanticModelSchemaEquivalent() {
            Map<String, Object> modelSchema = Map.of(
                    "type", "object",
                    "properties", Map.of(
                            "id", Map.of("type", "integer", "location", "path"),
                            "name", Map.of("type", "string", "location", "query"),
                            "data", Map.of("type", "object", "location", "body"),
                            "token", Map.of("type", "string", "location", "header")
                    )
            );
            ApiParamMapper mapper = new ApiParamMapper(modelSchema, null, null, null);

            Map<ApiParamLocation, Map<String, Object>> result = mapper.map(Map.of(
                    "id", 123,
                    "name", "John",
                    "data", Map.of("key", "value"),
                    "token", "xyz789"), ApiParamLocation.BODY);

            assertEquals(Map.of("id", 123), result.get(ApiParamLocation.PATH));
            assertEquals(Map.of("name", "John"), result.get(ApiParamLocation.QUERY));
            assertEquals(Map.of("data", Map.of("key", "value")), result.get(ApiParamLocation.BODY));
            assertEquals(Map.of("token", "xyz789"), result.get(ApiParamLocation.HEADER));
        }

        @Test
        void testMapWithDefaultValues() {
            ApiParamMapper mapper = new ApiParamMapper(
                    DEFAULT_SCHEMAS,
                    Map.of("lang", "en", "format", "json"),
                    Map.of("X-API-Key", "test-key"),
                    Map.of("version", "v1"));

            Map<ApiParamLocation, Map<String, Object>> result = mapper.map(
                    Map.of("id", 123, "name", "John"), ApiParamLocation.BODY);

            assertEquals(Map.of("version", "v1", "id", 123), result.get(ApiParamLocation.PATH));
            assertEquals(Map.of("lang", "en", "format", "json", "name", "John"),
                    result.get(ApiParamLocation.QUERY));
            assertEquals(Map.of("X-API-Key", "test-key"), result.get(ApiParamLocation.HEADER));
            assertTrue(result.get(ApiParamLocation.BODY).isEmpty());
        }

        @Test
        void testMapInputOverridesDefaults() {
            ApiParamMapper mapper = new ApiParamMapper(
                    DEFAULT_SCHEMAS,
                    Map.of("lang", "en", "name", "Default Name"),
                    null,
                    Map.of("id", 999, "version", "v1"));

            Map<ApiParamLocation, Map<String, Object>> result = mapper.map(
                    Map.of("id", 123, "name", "Actual Name"), ApiParamLocation.BODY);

            assertEquals(Map.of("version", "v1", "id", 123), result.get(ApiParamLocation.PATH));
            assertEquals(Map.of("lang", "en", "name", "Actual Name"), result.get(ApiParamLocation.QUERY));
        }

        @Test
        void testMapNoneAndEmptyStringPreserveDefaults() {
            ApiParamMapper mapper = new ApiParamMapper(
                    DEFAULT_SCHEMAS,
                    Map.of("lang", "en", "format", "json"),
                    Map.of("X-API-Key", "test-key", "X-User-ID", "default-user"),
                    Map.of("version", "v1"));
            Map<String, Object> inputs = new LinkedHashMap<>();
            inputs.put("id", null);
            inputs.put("name", "");
            inputs.put("age", 25);
            inputs.put("auth_token", null);

            Map<ApiParamLocation, Map<String, Object>> result = mapper.map(inputs, ApiParamLocation.BODY);

            assertEquals(Map.of("version", "v1"), result.get(ApiParamLocation.PATH));
            assertEquals(Map.of("lang", "en", "format", "json", "age", 25), result.get(ApiParamLocation.QUERY));
            assertEquals(Map.of("X-API-Key", "test-key", "X-User-ID", "default-user"),
                    result.get(ApiParamLocation.HEADER));
            assertTrue(result.get(ApiParamLocation.BODY).isEmpty());
        }
    }

    @Nested
    @DisplayName("Form parameter mapping")
    class FormParameterMapping {

        @Test
        void testSingleFormParamMapping() {
            ApiParamMapper mapper = new ApiParamMapper(Map.of(
                    "type", "object",
                    "properties", Map.of(
                            "file", Map.of(
                                    "type", "string",
                                    "location", "form",
                                    "form_handler_type", "file",
                                    "description", "PDF file"))), null, null, null);

            Map<ApiParamLocation, Map<String, Object>> result = mapper.map(
                    Map.of("file", "http://example.com/document.pdf"), ApiParamLocation.BODY);

            assertEquals(Map.of("form_handler_type", "file", "value", "http://example.com/document.pdf"),
                    result.get(ApiParamLocation.FORM).get("file"));
        }

        @Test
        void testMultipleFormParamsMapping() {
            ApiParamMapper mapper = new ApiParamMapper(FORM_TYPE_SCHEMA, null, null, null);

            Map<ApiParamLocation, Map<String, Object>> result = mapper.map(Map.of(
                    "file", "http://example.com/document.pdf",
                    "image", "http://example.com/image.png",
                    "name", "test_document"), ApiParamLocation.BODY);

            assertEquals(Map.of(
                            "file", Map.of("form_handler_type", "file", "value", "http://example.com/document.pdf"),
                            "image", Map.of("form_handler_type", "file", "value", "http://example.com/image.png")),
                    result.get(ApiParamLocation.FORM));
            assertEquals(Map.of("name", "test_document"), result.get(ApiParamLocation.BODY));
        }

        @Test
        void testMixedFormAndRegularParams() {
            Map<String, Object> schema = Map.of(
                    "type", "object",
                    "properties", Map.of(
                            "document", Map.of("type", "string", "location", "form",
                                    "form_handler_type", "file"),
                            "title", Map.of("type", "string", "location", "body"),
                            "user_id", Map.of("type", "integer", "location", "query"),
                            "auth_token", Map.of("type", "string", "location", "header"),
                            "version", Map.of("type", "string", "location", "path")
                    )
            );
            ApiParamMapper mapper = new ApiParamMapper(schema, null, null, null);

            Map<ApiParamLocation, Map<String, Object>> result = mapper.map(Map.of(
                    "document", "http://example.com/doc.pdf",
                    "title", "My Document",
                    "user_id", 123,
                    "auth_token", "token123",
                    "version", "v1"), ApiParamLocation.BODY);

            assertEquals(Map.of("document", Map.of("form_handler_type", "file",
                    "value", "http://example.com/doc.pdf")), result.get(ApiParamLocation.FORM));
            assertEquals(Map.of("title", "My Document"), result.get(ApiParamLocation.BODY));
            assertEquals(Map.of("user_id", 123), result.get(ApiParamLocation.QUERY));
            assertEquals(Map.of("auth_token", "token123"), result.get(ApiParamLocation.HEADER));
            assertEquals(Map.of("version", "v1"), result.get(ApiParamLocation.PATH));
        }

        @Test
        void testDefaultFormHandlerType() {
            ApiParamMapper mapper = new ApiParamMapper(Map.of(
                    "type", "object",
                    "properties", Map.of("file", Map.of(
                            "type", "string", "location", "form", "description", "File"))),
                    null, null, null);

            Map<ApiParamLocation, Map<String, Object>> result = mapper.map(
                    Map.of("file", "http://example.com/file.pdf"), ApiParamLocation.BODY);

            assertEquals(Map.of("form_handler_type", "default", "value", "http://example.com/file.pdf"),
                    result.get(ApiParamLocation.FORM).get("file"));
        }

        @Test
        void testCustomFormHandlerType() {
            ApiParamMapper mapper = new ApiParamMapper(Map.of(
                    "type", "object",
                    "properties", Map.of("data", Map.of(
                            "type", "string", "location", "form", "form_handler_type", "custom"))),
                    null, null, null);

            Map<ApiParamLocation, Map<String, Object>> result = mapper.map(
                    Map.of("data", "custom_value"), ApiParamLocation.BODY);

            assertEquals(Map.of("form_handler_type", "custom", "value", "custom_value"),
                    result.get(ApiParamLocation.FORM).get("data"));
        }

        @Test
        void testEmptyFormHandlerType() {
            ApiParamMapper mapper = new ApiParamMapper(Map.of(
                    "type", "object",
                    "properties", Map.of("file", Map.of(
                            "type", "string", "location", "form", "form_handler_type", ""))),
                    null, null, null);

            Map<ApiParamLocation, Map<String, Object>> result = mapper.map(
                    Map.of("file", "http://example.com/file.pdf"), ApiParamLocation.BODY);

            assertEquals(Map.of("form_handler_type", "", "value", "http://example.com/file.pdf"),
                    result.get(ApiParamLocation.FORM).get("file"));
        }

        @Test
        void testFormParamValueIsNone() {
            ApiParamMapper mapper = formFieldMapper();
            Map<String, Object> inputs = new LinkedHashMap<>();
            inputs.put("form_field", null);

            Map<ApiParamLocation, Map<String, Object>> result = mapper.map(inputs, ApiParamLocation.BODY);

            assertTrue(result.get(ApiParamLocation.FORM).isEmpty());
        }

        @Test
        void testFormParamValueIsEmptyString() {
            ApiParamMapper mapper = formFieldMapper();

            Map<ApiParamLocation, Map<String, Object>> result = mapper.map(
                    Map.of("form_field", ""), ApiParamLocation.BODY);

            assertTrue(result.get(ApiParamLocation.FORM).isEmpty());
        }

        @Test
        void testFormParamValueIsValid() {
            ApiParamMapper mapper = formFieldMapper();

            Map<ApiParamLocation, Map<String, Object>> result = mapper.map(
                    Map.of("form_field", "test_value"), ApiParamLocation.BODY);

            assertEquals(Map.of("form_handler_type", "default", "value", "test_value"),
                    result.get(ApiParamLocation.FORM).get("form_field"));
        }

        @Test
        void testInputsNotContainFormParam() {
            ApiParamMapper mapper = new ApiParamMapper(FORM_TYPE_SCHEMA, null, null, null);

            Map<ApiParamLocation, Map<String, Object>> result = mapper.map(
                    Map.of("name", "test"), ApiParamLocation.BODY);

            assertTrue(result.get(ApiParamLocation.FORM).isEmpty());
            assertEquals(Map.of("name", "test"), result.get(ApiParamLocation.BODY));
        }

        @Test
        void testEmptySchemaUsesDefaultLocation() {
            ApiParamMapper mapper = new ApiParamMapper(null, null, null, null);

            Map<ApiParamLocation, Map<String, Object>> result = mapper.map(
                    Map.of("field", "value"), ApiParamLocation.BODY);

            assertEquals(Map.of("field", "value"), result.get(ApiParamLocation.BODY));
        }

        private ApiParamMapper formFieldMapper() {
            return new ApiParamMapper(Map.of(
                    "type", "object",
                    "properties", Map.of("form_field", Map.of(
                            "type", "string",
                            "location", "form",
                            "form_handler_type", "default"))),
                    null, null, null);
        }
    }
}

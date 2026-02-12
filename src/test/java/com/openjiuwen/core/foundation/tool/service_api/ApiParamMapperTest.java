package com.openjiuwen.core.foundation.tool.service_api;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * API参数映射器测试
 * 
 * <p>严格对齐Python测试: test_api_param_mapper.py
 * 
 * @author OpenJiuwen
 * @since 2026-01-30
 */
class ApiParamMapperTest {

    /**
     * 测试初始化基本状态
     * 对应Python: test_init_base
     */
    @Test
    void testInitBase() {
        Map<String, Object> schema = new HashMap<>();
        Map<String, Object> properties = new HashMap<>();
        
        Map<String, Object> idSchema = new HashMap<>();
        idSchema.put("type", "integer");
        idSchema.put("location", "path");
        properties.put("id", idSchema);
        
        Map<String, Object> nameSchema = new HashMap<>();
        nameSchema.put("type", "string");
        nameSchema.put("location", "query");
        properties.put("name", nameSchema);
        
        Map<String, Object> ageSchema = new HashMap<>();
        ageSchema.put("type", "integer");
        ageSchema.put("location", "query");
        properties.put("age", ageSchema);
        
        Map<String, Object> dataSchema = new HashMap<>();
        dataSchema.put("type", "object");
        dataSchema.put("location", "body");
        properties.put("data", dataSchema);
        
        Map<String, Object> authTokenSchema = new HashMap<>();
        authTokenSchema.put("type", "string");
        authTokenSchema.put("location", "header");
        properties.put("auth_token", authTokenSchema);
        
        schema.put("type", "object");
        schema.put("properties", properties);
        
        ApiParamMapper mapper = new ApiParamMapper(schema, null, null, null);
        
        assertNotNull(mapper);
        // 验证默认值为空Map
        Map<ApiParamLocation, Map<String, Object>> result = mapper.map(new HashMap<>(), ApiParamLocation.BODY);
        assertTrue(result.get(ApiParamLocation.QUERY).isEmpty());
        assertTrue(result.get(ApiParamLocation.HEADER).isEmpty());
        assertTrue(result.get(ApiParamLocation.PATH).isEmpty());
    }

    /**
     * 测试字典schema映射
     * 对应Python: test_map_with_dict_schema
     */
    @Test
    void testMapWithDictSchema() {
        Map<String, Object> schema = new HashMap<>();
        Map<String, Object> properties = new HashMap<>();
        
        Map<String, Object> idSchema = new HashMap<>();
        idSchema.put("type", "integer");
        idSchema.put("location", "path");
        properties.put("id", idSchema);
        
        Map<String, Object> nameSchema = new HashMap<>();
        nameSchema.put("type", "string");
        nameSchema.put("location", "query");
        properties.put("name", nameSchema);
        
        Map<String, Object> ageSchema = new HashMap<>();
        ageSchema.put("type", "integer");
        ageSchema.put("location", "query");
        properties.put("age", ageSchema);
        
        Map<String, Object> dataSchema = new HashMap<>();
        dataSchema.put("type", "object");
        dataSchema.put("location", "body");
        properties.put("data", dataSchema);
        
        Map<String, Object> authTokenSchema = new HashMap<>();
        authTokenSchema.put("type", "string");
        authTokenSchema.put("location", "header");
        properties.put("auth_token", authTokenSchema);
        
        schema.put("type", "object");
        schema.put("properties", properties);
        
        ApiParamMapper mapper = new ApiParamMapper(schema, null, null, null);
        
        Map<String, Object> inputs = new HashMap<>();
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

    /**
     * 测试默认值合并
     * 对应Python: test_map_with_default_values
     */
    @Test
    void testMapWithDefaultValues() {
        Map<String, Object> schema = new HashMap<>();
        Map<String, Object> properties = new HashMap<>();
        
        Map<String, Object> idSchema = new HashMap<>();
        idSchema.put("type", "integer");
        idSchema.put("location", "path");
        properties.put("id", idSchema);
        
        Map<String, Object> nameSchema = new HashMap<>();
        nameSchema.put("type", "string");
        nameSchema.put("location", "query");
        properties.put("name", nameSchema);
        
        schema.put("type", "object");
        schema.put("properties", properties);
        
        Map<String, Object> defaultQueries = new HashMap<>();
        defaultQueries.put("lang", "en");
        defaultQueries.put("format", "json");
        
        Map<String, Object> defaultHeaders = new HashMap<>();
        defaultHeaders.put("X-API-Key", "test-key");
        
        Map<String, Object> defaultPaths = new HashMap<>();
        defaultPaths.put("version", "v1");
        
        ApiParamMapper mapper = new ApiParamMapper(schema, defaultQueries, defaultHeaders, defaultPaths);
        
        Map<String, Object> inputs = new HashMap<>();
        inputs.put("id", 123);
        inputs.put("name", "John");
        
        Map<ApiParamLocation, Map<String, Object>> result = mapper.map(inputs, ApiParamLocation.BODY);
        
        // 默认值应与输入合并
        Map<String, Object> expectedPath = new HashMap<>();
        expectedPath.put("version", "v1");
        expectedPath.put("id", 123);
        assertEquals(expectedPath, result.get(ApiParamLocation.PATH));
        
        Map<String, Object> expectedQuery = new HashMap<>();
        expectedQuery.put("lang", "en");
        expectedQuery.put("format", "json");
        expectedQuery.put("name", "John");
        assertEquals(expectedQuery, result.get(ApiParamLocation.QUERY));
        
        assertEquals(Map.of("X-API-Key", "test-key"), result.get(ApiParamLocation.HEADER));
        assertTrue(result.get(ApiParamLocation.BODY).isEmpty());
    }

    /**
     * 测试输入值覆盖默认值
     * 对应Python: test_map_input_overrides_defaults
     */
    @Test
    void testMapInputOverridesDefaults() {
        Map<String, Object> schema = new HashMap<>();
        Map<String, Object> properties = new HashMap<>();
        
        Map<String, Object> idSchema = new HashMap<>();
        idSchema.put("type", "integer");
        idSchema.put("location", "path");
        properties.put("id", idSchema);
        
        Map<String, Object> nameSchema = new HashMap<>();
        nameSchema.put("type", "string");
        nameSchema.put("location", "query");
        properties.put("name", nameSchema);
        
        schema.put("type", "object");
        schema.put("properties", properties);
        
        Map<String, Object> defaultQueries = new HashMap<>();
        defaultQueries.put("lang", "en");
        defaultQueries.put("name", "Default Name");  // 将被覆盖
        
        Map<String, Object> defaultPaths = new HashMap<>();
        defaultPaths.put("id", 999);  // 将被覆盖
        defaultPaths.put("version", "v1");
        
        ApiParamMapper mapper = new ApiParamMapper(schema, defaultQueries, null, defaultPaths);
        
        Map<String, Object> inputs = new HashMap<>();
        inputs.put("id", 123);
        inputs.put("name", "Actual Name");
        
        Map<ApiParamLocation, Map<String, Object>> result = mapper.map(inputs, ApiParamLocation.BODY);
        
        // 输入值应覆盖默认值
        Map<String, Object> expectedPath = new HashMap<>();
        expectedPath.put("version", "v1");
        expectedPath.put("id", 123);  // 输入覆盖默认值999
        assertEquals(expectedPath, result.get(ApiParamLocation.PATH));
        
        Map<String, Object> expectedQuery = new HashMap<>();
        expectedQuery.put("lang", "en");
        expectedQuery.put("name", "Actual Name");  // 输入覆盖默认值"Default Name"
        assertEquals(expectedQuery, result.get(ApiParamLocation.QUERY));
    }

    /**
     * 测试Query位置参数映射
     */
    @Test
    void testMapWithQueryLocation() {
        Map<String, Object> schema = new HashMap<>();
        Map<String, Object> properties = new HashMap<>();
        
        Map<String, Object> userIdSchema = new HashMap<>();
        userIdSchema.put("type", "integer");
        userIdSchema.put("location", "query");
        properties.put("user_id", userIdSchema);
        
        Map<String, Object> filterSchema = new HashMap<>();
        filterSchema.put("type", "string");
        filterSchema.put("location", "query");
        properties.put("filter", filterSchema);
        
        schema.put("type", "object");
        schema.put("properties", properties);
        
        ApiParamMapper mapper = new ApiParamMapper(schema, null, null, null);
        
        Map<String, Object> inputs = new HashMap<>();
        inputs.put("user_id", 123);
        inputs.put("filter", "active");
        
        Map<ApiParamLocation, Map<String, Object>> result = mapper.map(inputs, ApiParamLocation.BODY);
        
        assertEquals(123, result.get(ApiParamLocation.QUERY).get("user_id"));
        assertEquals("active", result.get(ApiParamLocation.QUERY).get("filter"));
        assertTrue(result.get(ApiParamLocation.BODY).isEmpty());
    }

    /**
     * 测试Path位置参数映射
     */
    @Test
    void testMapWithPathLocation() {
        Map<String, Object> schema = new HashMap<>();
        Map<String, Object> properties = new HashMap<>();
        
        Map<String, Object> idSchema = new HashMap<>();
        idSchema.put("type", "integer");
        idSchema.put("location", "path");
        properties.put("id", idSchema);
        
        schema.put("properties", properties);
        
        ApiParamMapper mapper = new ApiParamMapper(schema, null, null, null);
        
        Map<String, Object> inputs = Map.of("id", 456);
        Map<ApiParamLocation, Map<String, Object>> result = mapper.map(inputs, ApiParamLocation.BODY);
        
        assertEquals(456, result.get(ApiParamLocation.PATH).get("id"));
    }

    /**
     * 测试Header位置参数映射
     */
    @Test
    void testMapWithHeaderLocation() {
        Map<String, Object> schema = new HashMap<>();
        Map<String, Object> properties = new HashMap<>();
        
        Map<String, Object> authSchema = new HashMap<>();
        authSchema.put("type", "string");
        authSchema.put("location", "header");
        properties.put("authorization", authSchema);
        
        schema.put("properties", properties);
        
        ApiParamMapper mapper = new ApiParamMapper(schema, null, null, null);
        
        Map<String, Object> inputs = Map.of("authorization", "Bearer token123");
        Map<ApiParamLocation, Map<String, Object>> result = mapper.map(inputs, ApiParamLocation.BODY);
        
        assertEquals("Bearer token123", result.get(ApiParamLocation.HEADER).get("authorization"));
    }

    /**
     * 测试混合位置参数映射
     */
    @Test
    void testMapWithMixedLocations() {
        Map<String, Object> schema = new HashMap<>();
        Map<String, Object> properties = new HashMap<>();
        
        Map<String, Object> idSchema = new HashMap<>();
        idSchema.put("location", "path");
        properties.put("id", idSchema);
        
        Map<String, Object> categorySchema = new HashMap<>();
        categorySchema.put("location", "query");
        properties.put("category", categorySchema);
        
        Map<String, Object> apiKeySchema = new HashMap<>();
        apiKeySchema.put("location", "header");
        properties.put("api_key", apiKeySchema);
        
        Map<String, Object> dataSchema = new HashMap<>();
        dataSchema.put("location", "body");
        properties.put("data", dataSchema);
        
        schema.put("properties", properties);
        
        ApiParamMapper mapper = new ApiParamMapper(schema, null, null, null);
        
        Map<String, Object> inputs = new HashMap<>();
        inputs.put("id", 789);
        inputs.put("category", "tech");
        inputs.put("api_key", "key123");
        inputs.put("data", Map.of("name", "test"));
        
        Map<ApiParamLocation, Map<String, Object>> result = mapper.map(inputs, ApiParamLocation.BODY);
        
        assertEquals(789, result.get(ApiParamLocation.PATH).get("id"));
        assertEquals("tech", result.get(ApiParamLocation.QUERY).get("category"));
        assertEquals("key123", result.get(ApiParamLocation.HEADER).get("api_key"));
        assertEquals(Map.of("name", "test"), result.get(ApiParamLocation.BODY).get("data"));
    }

    /**
     * 测试默认值合并（输入值应覆盖默认值）
     */
    @Test
    void testMapWithDefaultValuesOverride() {
        Map<String, Object> schema = new HashMap<>();
        Map<String, Object> properties = new HashMap<>();
        
        Map<String, Object> formatSchema = new HashMap<>();
        formatSchema.put("location", "query");
        properties.put("format", formatSchema);
        
        schema.put("properties", properties);
        
        Map<String, Object> defaultQueries = Map.of("format", "xml", "limit", 20);
        Map<String, Object> defaultHeaders = Map.of("Accept", "application/json");
        
        ApiParamMapper mapper = new ApiParamMapper(schema, defaultQueries, defaultHeaders, null);
        
        Map<String, Object> inputs = Map.of("format", "json");
        Map<ApiParamLocation, Map<String, Object>> result = mapper.map(inputs, ApiParamLocation.BODY);
        
        // 输入值"json"应覆盖默认值"xml"
        assertEquals("json", result.get(ApiParamLocation.QUERY).get("format"));
        // 默认的limit应保留
        assertEquals(20, result.get(ApiParamLocation.QUERY).get("limit"));
        // 默认header应保留
        assertEquals("application/json", result.get(ApiParamLocation.HEADER).get("Accept"));
    }

    /**
     * 测试无schema时的默认行为
     */
    @Test
    void testMapWithNoSchema() {
        ApiParamMapper mapper = new ApiParamMapper(null, null, null, null);
        
        Map<String, Object> inputs = Map.of("key1", "value1", "key2", "value2");
        Map<ApiParamLocation, Map<String, Object>> result = mapper.map(inputs, ApiParamLocation.BODY);
        
        // 所有参数应放到默认位置（BODY）
        assertEquals("value1", result.get(ApiParamLocation.BODY).get("key1"));
        assertEquals("value2", result.get(ApiParamLocation.BODY).get("key2"));
    }

    /**
     * 测试未指定location时使用默认location
     */
    @Test
    void testMapWithNoLocationSpecified() {
        Map<String, Object> schema = new HashMap<>();
        Map<String, Object> properties = new HashMap<>();
        
        Map<String, Object> nameSchema = new HashMap<>();
        nameSchema.put("type", "string");
        // 不指定location
        properties.put("name", nameSchema);
        
        schema.put("properties", properties);
        
        ApiParamMapper mapper = new ApiParamMapper(schema, null, null, null);
        
        Map<String, Object> inputs = Map.of("name", "test");
        Map<ApiParamLocation, Map<String, Object>> result = mapper.map(inputs, ApiParamLocation.BODY);
        
        // 应使用默认location（BODY）
        assertEquals("test", result.get(ApiParamLocation.BODY).get("name"));
    }
}


// coding: utf-8
// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.

package com.openjiuwen.core.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.StatusCode;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 单元测试类：测试 JsonUtils 工具类
 */
class JsonUtilsTest {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeAll
    static void setup() {
        // 配置 ObjectMapper 以匹配 Python json 模块的行为
        objectMapper.enable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    }

    // ==================== safeJsonLoads 测试 ====================

    @Test
    void testSafeJsonLoadsSuccess() throws BaseError {
        String jsonString = "{\"key\": \"value\", \"number\": 123}";
        Object result = JsonUtils.safeJsonLoads(jsonString);

        assertNotNull(result);
        assertTrue(result instanceof Map);
        Map<?, ?> map = (Map<?, ?>) result;
        assertEquals("value", map.get("key"));
        assertEquals(123, map.get("number"));
    }

    @Test
    void testSafeJsonLoadsWithArray() throws BaseError {
        String jsonString = "[1, 2, 3, \"four\"]";
        Object result = JsonUtils.safeJsonLoads(jsonString);

        assertNotNull(result);
        assertTrue(result instanceof List);
        @SuppressWarnings("unchecked")
        List<Object> list = (List<Object>) result;
        assertEquals(4, list.size());
        assertEquals(1, list.get(0));
        assertEquals("four", list.get(3));
    }

    @Test
    void testSafeJsonLoadsWithNestedObject() throws BaseError {
        String jsonString = "{\"user\": {\"name\": \"Alice\", \"age\": 30}, \"active\": true}";
        Object result = JsonUtils.safeJsonLoads(jsonString);

        assertNotNull(result);
        assertTrue(result instanceof Map);
        @SuppressWarnings("unchecked")
        Map<String, Object> map = (Map<String, Object>) result;
        assertTrue(map.containsKey("user"));
        assertTrue(map.containsKey("active"));
        assertEquals(true, map.get("active"));
    }

    @Test
    void testSafeJsonLoadsWithDefault() throws BaseError {
        String jsonString = "{\"key\": \"value\"}";
        Object defaultValue = "default_value";
        Object result = JsonUtils.safeJsonLoads(jsonString, defaultValue);

        assertNotNull(result);
        assertTrue(result instanceof Map);
        // 成功解析时不应返回默认值
        assertNotEquals(defaultValue, result);
    }

    @Test
    void testSafeJsonLoadsWithDefaultOnInvalidJson() throws BaseError {
        String invalidJsonString = "{invalid json";
        Object defaultValue = "default_value";
        Object result = JsonUtils.safeJsonLoads(invalidJsonString, defaultValue);

        // 失败时应返回默认值
        assertEquals(defaultValue, result);
    }

    @Test
    void testSafeJsonLoadsFailureInvalidJson() {
        String invalidJsonString = "{invalid json";

        BaseError error = assertThrows(BaseError.class, () -> JsonUtils.safeJsonLoads(invalidJsonString));
        assertEquals(StatusCode.COMMON_JSON_INPUT_PROCESS_ERROR, error.getStatus());
    }

    @Test
    void testSafeJsonLoadsFailureEmptyString() {
        String emptyString = "";

        BaseError error = assertThrows(BaseError.class, () -> JsonUtils.safeJsonLoads(emptyString));
        assertEquals(StatusCode.COMMON_JSON_INPUT_PROCESS_ERROR, error.getStatus());
    }

    @Test
    void testSafeJsonLoadsFailureNullInput() {
        BaseError error = assertThrows(BaseError.class, () -> JsonUtils.safeJsonLoads(null));
        assertEquals(StatusCode.COMMON_JSON_INPUT_PROCESS_ERROR, error.getStatus());
    }

    // ==================== safeJsonDumps 测试 ====================

    @Test
    void testSafeJsonDumpsSuccess() throws BaseError {
        Map<String, Object> obj = new HashMap<>();
        obj.put("key", "value");
        obj.put("number", 123);

        String result = JsonUtils.safeJsonDumps(obj);

        assertNotNull(result);
        assertTrue(result.contains("\"key\":\"value\"") || result.contains("\"key\": \"value\""));
        assertTrue(result.contains("\"number\":123") || result.contains("\"number\": 123"));
    }

    @Test
    void testSafeJsonDumpsWithList() throws BaseError {
        List<Object> list = List.of(1, 2, 3, "four");

        String result = JsonUtils.safeJsonDumps(list);

        assertNotNull(result);
        assertTrue(result.startsWith("["));
        assertTrue(result.endsWith("]"));
    }

    @Test
    void testSafeJsonDumpsWithNestedObject() throws BaseError {
        Map<String, Object> nested = new HashMap<>();
        nested.put("name", "Alice");
        nested.put("age", 30);

        Map<String, Object> obj = new HashMap<>();
        obj.put("user", nested);
        obj.put("active", true);

        String result = JsonUtils.safeJsonDumps(obj);

        assertNotNull(result);
        assertTrue(result.contains("\"user\"") || result.contains("\"user\":"));
        assertTrue(result.contains("\"active\":true") || result.contains("\"active\": true"));
    }

    @Test
    void testSafeJsonDumpsWithDefault() throws BaseError {
        Map<String, Object> obj = new HashMap<>();
        obj.put("key", "value");
        String defaultValue = "{}";
        String result = JsonUtils.safeJsonDumps(obj, defaultValue);

        assertNotNull(result);
        // 成功序列化时不应返回默认值
        assertNotEquals(defaultValue, result);
    }

    @Test
    void testSafeJsonDumpsWithDefaultOnFailure() throws BaseError {
        // 创建一个不可序列化的对象（包含循环引用）
        Map<String, Object> obj = new HashMap<>();
        obj.put("self", obj);

        String defaultValue = "{}";
        String result = JsonUtils.safeJsonDumps(obj, defaultValue);

        // 失败时应返回默认值
        assertEquals(defaultValue, result);
    }

    @Test
    void testSafeJsonDumpsFailureUnserializable() {
        // 创建一个包含不可序列化对象的 Map
        Map<String, Object> obj = new HashMap<>();
        class Unserializable {
            @Override
            public String toString() {
                return "unserializable";
            }
        }
        obj.put("key", new Unserializable());

        BaseError error = assertThrows(BaseError.class, () -> JsonUtils.safeJsonDumps(obj));
        assertEquals(StatusCode.COMMON_JSON_EXECUTION_PROCESS_ERROR, error.getStatus());
    }

    @Test
    void testSafeJsonDumpsFailureNullInput() {
        BaseError error = assertThrows(BaseError.class, () -> JsonUtils.safeJsonDumps(null));
        assertEquals(StatusCode.COMMON_JSON_EXECUTION_PROCESS_ERROR, error.getStatus());
    }
}
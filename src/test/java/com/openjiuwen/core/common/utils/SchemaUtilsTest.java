package com.openjiuwen.core.common.utils;

import com.openjiuwen.core.common.exception.ValidationError;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SchemaUtils 测试类
 * 
 * 从 Python test_schema_utils.py 转换
 */
public class SchemaUtilsTest {

    // 用户Schema定义
    private static final Map<String, Object> USER_SCHEMA = createUserSchema();

    private static Map<String, Object> createUserSchema() {
        Map<String, Object> schema = new HashMap<>();
        schema.put("type", "object");
        schema.put("title", "User");

        Map<String, Object> properties = new HashMap<>();

        // name 属性
        Map<String, Object> nameProp = new HashMap<>();
        nameProp.put("type", "string");
        nameProp.put("default", "Anonymous");
        nameProp.put("minLength", 1);
        nameProp.put("maxLength", 50);
        nameProp.put("description", "User's name");
        properties.put("name", nameProp);

        // age 属性
        Map<String, Object> ageProp = new HashMap<>();
        ageProp.put("type", "integer");
        ageProp.put("default", 18);
        ageProp.put("minimum", 0);
        ageProp.put("maximum", 150);
        properties.put("age", ageProp);

        // email 属性
        Map<String, Object> emailProp = new HashMap<>();
        emailProp.put("type", "string");
        emailProp.put("format", "email");
        emailProp.put("default", "user@example.com");
        properties.put("email", emailProp);

        // is_active 属性
        Map<String, Object> isActiveProp = new HashMap<>();
        isActiveProp.put("type", "boolean");
        isActiveProp.put("default", true);
        properties.put("is_active", isActiveProp);

        // tags 属性
        Map<String, Object> tagsProp = new HashMap<>();
        tagsProp.put("type", "array");
        Map<String, Object> tagsItems = new HashMap<>();
        tagsItems.put("type", "string");
        tagsProp.put("items", tagsItems);
        tagsProp.put("default", Collections.singletonList("new_user"));
        tagsProp.put("minItems", 1);
        properties.put("tags", tagsProp);

        // metadata 属性
        Map<String, Object> metadataProp = new HashMap<>();
        metadataProp.put("type", "object");
        metadataProp.put("default", new HashMap<>());
        metadataProp.put("additionalProperties", true);
        properties.put("metadata", metadataProp);

        schema.put("properties", properties);
        schema.put("required", Arrays.asList("name", "age", "email"));

        return schema;
    }

    @Test
    public void testFormatWithJsonSchema() {
        // 部分用户数据
        Map<String, Object> partialUserData = new HashMap<>();
        partialUserData.put("name", "Jane Doe");
        partialUserData.put("age", 25);
        // Missing email, should use default

        Object result = SchemaUtils.formatWithSchema(partialUserData, USER_SCHEMA, false, false);

        assertNotNull(result, "结果不应该为null");
        assertTrue(result instanceof Map, "结果应该是Map类型");

        @SuppressWarnings("unchecked")
        Map<String, Object> resultMap = (Map<String, Object>) result;

        assertEquals("Jane Doe", resultMap.get("name"), "name应该是Jane Doe");
        assertEquals(25, resultMap.get("age"), "age应该是25");
        assertEquals("user@example.com", resultMap.get("email"), "email应该使用默认值");
        assertEquals(true, resultMap.get("is_active"), "is_active应该使用默认值true");

        Object tags = resultMap.get("tags");
        assertTrue(tags instanceof List, "tags应该是List类型");
        @SuppressWarnings("unchecked")
        List<String> tagsList = (List<String>) tags;
        assertEquals(Collections.singletonList("new_user"), tagsList, "tags应该是默认值");
    }

    @Test
    public void testFormatNoneData() {
        // 测试null数据
        assertThrows(ValidationError.class, () -> {
            SchemaUtils.formatWithSchema(null, USER_SCHEMA, false, false);
        }, "null数据应该抛出ValidationError");
    }

    @Test
    public void testFormatEmptyDict() {
        Object result = SchemaUtils.formatWithSchema(new HashMap<>(), USER_SCHEMA, false, false);

        assertNotNull(result, "结果不应该为null");
        assertTrue(result instanceof Map, "结果应该是Map类型");

        @SuppressWarnings("unchecked")
        Map<String, Object> resultMap = (Map<String, Object>) result;

        assertTrue(resultMap.containsKey("name"), "应该包含name键");
        assertTrue(resultMap.containsKey("age"), "应该包含age键");
        assertTrue(resultMap.containsKey("email"), "应该包含email键");
    }

    @Test
    public void testValidateValidData() {
        // 有效的用户数据
        Map<String, Object> validUserData = new HashMap<>();
        validUserData.put("name", "John Doe");
        validUserData.put("age", 30);
        validUserData.put("email", "john@example.com");
        validUserData.put("is_active", true);
        validUserData.put("tags", Arrays.asList("developer", "premium"));
        
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("created_at", "2024-01-01");
        validUserData.put("metadata", metadata);

        // 不应该抛出异常
        assertDoesNotThrow(() -> {
            SchemaUtils.validateWithSchema(validUserData, USER_SCHEMA);
        }, "有效数据不应该抛出异常");
    }

    @Test
    public void testValidateInvalidData() {
        // 无效的用户数据
        Map<String, Object> invalidUserData = new HashMap<>();
        invalidUserData.put("name", ""); // Empty string, violates minLength
        invalidUserData.put("age", 200); // Too high, violates maximum
        invalidUserData.put("email", "invalid-email"); // Invalid email format

        assertThrows(ValidationError.class, () -> {
            SchemaUtils.validateWithSchema(invalidUserData, USER_SCHEMA);
        }, "无效数据应该抛出ValidationError");
    }

    @Test
    public void testRemoveNoneValues() {
        // 测试移除null值
        Map<String, Object> dataWithNulls = new HashMap<>();
        dataWithNulls.put("name", "Test");
        dataWithNulls.put("age", null);
        dataWithNulls.put("email", "test@example.com");
        
        Map<String, Object> nested = new HashMap<>();
        nested.put("key1", "value1");
        nested.put("key2", null);
        dataWithNulls.put("nested", nested);
        
        List<Object> listWithNulls = new ArrayList<>();
        listWithNulls.add("item1");
        listWithNulls.add(null);
        listWithNulls.add("item2");
        dataWithNulls.put("list", listWithNulls);

        Object result = SchemaUtils.removeNoneValues(dataWithNulls);

        assertNotNull(result, "结果不应该为null");
        assertTrue(result instanceof Map, "结果应该是Map类型");

        @SuppressWarnings("unchecked")
        Map<String, Object> resultMap = (Map<String, Object>) result;

        assertTrue(resultMap.containsKey("name"), "应该保留name");
        assertFalse(resultMap.containsKey("age"), "不应该包含null的age");
        assertTrue(resultMap.containsKey("email"), "应该保留email");

        @SuppressWarnings("unchecked")
        Map<String, Object> nestedResult = (Map<String, Object>) resultMap.get("nested");
        assertNotNull(nestedResult, "nested不应该为null");
        assertTrue(nestedResult.containsKey("key1"), "应该保留key1");
        assertFalse(nestedResult.containsKey("key2"), "不应该包含null的key2");

        @SuppressWarnings("unchecked")
        List<Object> listResult = (List<Object>) resultMap.get("list");
        assertNotNull(listResult, "list不应该为null");
        assertEquals(2, listResult.size(), "list应该只有2个非null元素");
        assertEquals("item1", listResult.get(0), "第一个元素应该是item1");
        assertEquals("item2", listResult.get(1), "第二个元素应该是item2");
    }

    @Test
    public void testFormatWithSkipNoneValue() {
        // 测试跳过null值的格式化
        Map<String, Object> dataWithNulls = new HashMap<>();
        dataWithNulls.put("name", "Test User");
        dataWithNulls.put("age", null);
        dataWithNulls.put("email", "test@example.com");

        Object result = SchemaUtils.formatWithSchema(dataWithNulls, USER_SCHEMA, true, false);

        assertNotNull(result, "结果不应该为null");
        assertTrue(result instanceof Map, "结果应该是Map类型");

        @SuppressWarnings("unchecked")
        Map<String, Object> resultMap = (Map<String, Object>) result;

        assertEquals("Test User", resultMap.get("name"), "name应该是Test User");
        // age被移除后，应该使用默认值
        assertNotNull(resultMap.get("age"), "age应该有默认值");
        assertEquals("test@example.com", resultMap.get("email"), "email应该是test@example.com");
    }

    @Test
    public void testFormatWithSkipValidate() {
        // 测试跳过验证的格式化
        Map<String, Object> invalidData = new HashMap<>();
        invalidData.put("name", ""); // 无效：空字符串
        invalidData.put("age", 200); // 无效：超出范围
        invalidData.put("email", "invalid");

        // 跳过验证，应该不抛出异常
        assertDoesNotThrow(() -> {
            SchemaUtils.formatWithSchema(invalidData, USER_SCHEMA, false, true);
        }, "跳过验证时不应该抛出异常");
    }

    @Test
    public void testComplexNestedSchema() {
        // 测试复杂嵌套schema
        Map<String, Object> complexSchema = new HashMap<>();
        complexSchema.put("type", "object");

        Map<String, Object> properties = new HashMap<>();

        // 嵌套对象
        Map<String, Object> addressProp = new HashMap<>();
        addressProp.put("type", "object");
        Map<String, Object> addressProperties = new HashMap<>();
        
        Map<String, Object> cityProp = new HashMap<>();
        cityProp.put("type", "string");
        cityProp.put("default", "Unknown");
        addressProperties.put("city", cityProp);
        
        addressProp.put("properties", addressProperties);
        addressProp.put("default", new HashMap<>());
        properties.put("address", addressProp);

        complexSchema.put("properties", properties);

        Map<String, Object> data = new HashMap<>();
        Object result = SchemaUtils.formatWithSchema(data, complexSchema, false, false);

        assertNotNull(result, "结果不应该为null");
        assertTrue(result instanceof Map, "结果应该是Map类型");
    }
}



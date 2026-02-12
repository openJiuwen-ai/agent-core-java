// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.foundation.tool.utils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CallableSchemaExtractor测试类
 */
@DisplayName("CallableSchemaExtractor Tests")
class CallableSchemaExtractorTest {

    @Test
    @DisplayName("humanizeName应正确转换snake_case")
    void testHumanizeNameSnakeCase() {
        assertEquals("user name", CallableSchemaExtractor.humanizeName("user_name"));
        assertEquals("first name", CallableSchemaExtractor.humanizeName("first_name"));
        assertEquals("my variable name", CallableSchemaExtractor.humanizeName("my_variable_name"));
    }

    @Test
    @DisplayName("humanizeName应正确转换camelCase")
    void testHumanizeNameCamelCase() {
        assertEquals("user name", CallableSchemaExtractor.humanizeName("userName"));
        assertEquals("first name", CallableSchemaExtractor.humanizeName("firstName"));
        assertEquals("my variable name", CallableSchemaExtractor.humanizeName("myVariableName"));
    }

    @Test
    @DisplayName("humanizeName应正确转换PascalCase")
    void testHumanizeNamePascalCase() {
        assertEquals("user name", CallableSchemaExtractor.humanizeName("UserName"));
        assertEquals("first name", CallableSchemaExtractor.humanizeName("FirstName"));
    }

    @Test
    @DisplayName("humanizeName应处理空字符串")
    void testHumanizeNameEmpty() {
        assertEquals("", CallableSchemaExtractor.humanizeName(""));
        assertEquals("", CallableSchemaExtractor.humanizeName(null));
    }

    @Test
    @DisplayName("humanizeName应处理常见缩写")
    void testHumanizeNameAbbreviations() {
        String result = CallableSchemaExtractor.humanizeName("userId");
        assertTrue(result.toLowerCase().contains("id"));
        
        result = CallableSchemaExtractor.humanizeName("apiUrl");
        assertTrue(result.toLowerCase().contains("api"));
        assertTrue(result.toLowerCase().contains("url"));
    }

    @Test
    @DisplayName("generateSchema应为简单方法生成正确的schema")
    void testGenerateSchemaSimpleMethod() throws NoSuchMethodException {
        Method method = TestClass.class.getMethod("simpleMethod", String.class, int.class);
        
        Map<String, Object> schema = CallableSchemaExtractor.generateSchema(method);
        
        assertNotNull(schema);
        assertEquals("object", schema.get("type"));
        
        @SuppressWarnings("unchecked")
        Map<String, Object> properties = (Map<String, Object>) schema.get("properties");
        assertNotNull(properties);
        
        // Java反射获取的参数名默认是arg0, arg1（除非编译时开启-parameters选项）
        // 使用反射获取实际参数名来验证
        String param0Name = method.getParameters()[0].getName();  // String参数
        String param1Name = method.getParameters()[1].getName();  // int参数
        
        assertTrue(properties.containsKey(param0Name), "Should contain param: " + param0Name);
        assertTrue(properties.containsKey(param1Name), "Should contain param: " + param1Name);
        
        @SuppressWarnings("unchecked")
        Map<String, Object> stringParamSchema = (Map<String, Object>) properties.get(param0Name);
        assertEquals("string", stringParamSchema.get("type"));
        
        @SuppressWarnings("unchecked")
        Map<String, Object> intParamSchema = (Map<String, Object>) properties.get(param1Name);
        assertEquals("integer", intParamSchema.get("type"));
    }

    @Test
    @DisplayName("generateSchema应正确处理必填参数")
    void testGenerateSchemaRequiredParams() throws NoSuchMethodException {
        Method method = TestClass.class.getMethod("simpleMethod", String.class, int.class);
        
        Map<String, Object> schema = CallableSchemaExtractor.generateSchema(method);
        
        @SuppressWarnings("unchecked")
        List<String> required = (List<String>) schema.get("required");
        assertNotNull(required);
        
        // Java反射获取的参数名默认是arg0, arg1（除非编译时开启-parameters选项）
        // 使用反射获取实际参数名来验证
        String param0Name = method.getParameters()[0].getName();
        String param1Name = method.getParameters()[1].getName();
        
        assertTrue(required.contains(param0Name), "Required should contain: " + param0Name);
        assertTrue(required.contains(param1Name), "Required should contain: " + param1Name);
    }

    @Test
    @DisplayName("getTypeSchema应正确处理基本类型")
    void testGetTypeSchemaBasicTypes() {
        Map<String, Object> stringSchema = CallableSchemaExtractor.getTypeSchema(String.class);
        assertEquals("string", stringSchema.get("type"));
        
        Map<String, Object> intSchema = CallableSchemaExtractor.getTypeSchema(Integer.class);
        assertEquals("integer", intSchema.get("type"));
        
        Map<String, Object> boolSchema = CallableSchemaExtractor.getTypeSchema(Boolean.class);
        assertEquals("boolean", boolSchema.get("type"));
        
        Map<String, Object> doubleSchema = CallableSchemaExtractor.getTypeSchema(Double.class);
        assertEquals("number", doubleSchema.get("type"));
    }

    @Test
    @DisplayName("getTypeSchema应为未知类型返回object")
    void testGetTypeSchemaUnknownType() {
        Map<String, Object> schema = CallableSchemaExtractor.getTypeSchema(UnknownClass.class);
        assertEquals("object", schema.get("type"));
    }

    @Test
    @DisplayName("extractFunctionDescription应提取方法描述")
    void testExtractFunctionDescription() throws NoSuchMethodException {
        Method method = TestClass.class.getMethod("documentedMethod", String.class);
        
        String description = CallableSchemaExtractor.extractFunctionDescription(method);
        
        // 方法没有JavaDoc时应返回人性化的方法名
        assertNotNull(description);
        assertFalse(description.isEmpty());
    }

    // 测试用类
    public static class TestClass {
        public void simpleMethod(String name, int age) {
            // 测试方法
        }
        
        /**
         * This is a documented method.
         * @param param The parameter
         */
        public void documentedMethod(String param) {
            // 带文档的方法
        }
        
        public List<String> listMethod(List<Integer> numbers) {
            return null;
        }
        
        public Map<String, Object> mapMethod(Map<String, Integer> input) {
            return null;
        }
    }
    
    // 未知类型测试
    private static class UnknownClass {
    }
}


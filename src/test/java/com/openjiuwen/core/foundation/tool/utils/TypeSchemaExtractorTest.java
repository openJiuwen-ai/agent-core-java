// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.foundation.tool.utils;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TypeSchemaExtractor测试类
 */
@DisplayName("TypeSchemaExtractor Tests")
class TypeSchemaExtractorTest {

    private TypeSchemaExtractorRegistry registry;

    @BeforeEach
    void setUp() {
        registry = TypeSchemaExtractorRegistry.getInstance();
    }

    @Test
    @DisplayName("Registry应为单例")
    void testRegistrySingleton() {
        TypeSchemaExtractorRegistry instance1 = TypeSchemaExtractorRegistry.getInstance();
        TypeSchemaExtractorRegistry instance2 = TypeSchemaExtractorRegistry.getInstance();
        assertSame(instance1, instance2);
    }

    @Test
    @DisplayName("Registry应包含所有默认提取器")
    void testRegistryContainsDefaultExtractors() {
        List<TypeSchemaExtractor> extractors = registry.getExtractors();
        assertFalse(extractors.isEmpty());
        // 应至少包含SimpleTypeSchemaExtractor
        assertTrue(extractors.stream()
            .anyMatch(e -> e instanceof SimpleTypeSchemaExtractor));
    }

    @Test
    @DisplayName("SimpleTypeSchemaExtractor应处理基本类型")
    void testSimpleTypeExtraction() {
        SimpleTypeSchemaExtractor extractor = new SimpleTypeSchemaExtractor();
        
        // String
        assertTrue(extractor.canExtract(String.class));
        Map<String, Object> stringSchema = extractor.extract(String.class, null);
        assertEquals("string", stringSchema.get("type"));
        
        // Integer
        assertTrue(extractor.canExtract(Integer.class));
        Map<String, Object> intSchema = extractor.extract(Integer.class, null);
        assertEquals("integer", intSchema.get("type"));
        
        // Boolean
        assertTrue(extractor.canExtract(Boolean.class));
        Map<String, Object> boolSchema = extractor.extract(Boolean.class, null);
        assertEquals("boolean", boolSchema.get("type"));
        
        // Double
        assertTrue(extractor.canExtract(Double.class));
        Map<String, Object> doubleSchema = extractor.extract(Double.class, null);
        assertEquals("number", doubleSchema.get("type"));
    }

    @Test
    @DisplayName("SimpleTypeSchemaExtractor应处理日期时间类型")
    void testDateTimeTypeExtraction() {
        SimpleTypeSchemaExtractor extractor = new SimpleTypeSchemaExtractor();
        
        // LocalDateTime
        assertTrue(extractor.canExtract(LocalDateTime.class));
        Map<String, Object> dateTimeSchema = extractor.extract(LocalDateTime.class, null);
        assertEquals("string", dateTimeSchema.get("type"));
        assertEquals("date-time", dateTimeSchema.get("format"));
        
        // LocalDate
        assertTrue(extractor.canExtract(LocalDate.class));
        Map<String, Object> dateSchema = extractor.extract(LocalDate.class, null);
        assertEquals("string", dateSchema.get("type"));
        assertEquals("date", dateSchema.get("format"));
        
        // LocalTime
        assertTrue(extractor.canExtract(LocalTime.class));
        Map<String, Object> timeSchema = extractor.extract(LocalTime.class, null);
        assertEquals("string", timeSchema.get("type"));
        assertEquals("time", timeSchema.get("format"));
    }

    @Test
    @DisplayName("SimpleTypeSchemaExtractor应处理特殊类型")
    void testSpecialTypeExtraction() {
        SimpleTypeSchemaExtractor extractor = new SimpleTypeSchemaExtractor();
        
        // UUID
        assertTrue(extractor.canExtract(UUID.class));
        Map<String, Object> uuidSchema = extractor.extract(UUID.class, null);
        assertEquals("string", uuidSchema.get("type"));
        assertEquals("uuid", uuidSchema.get("format"));
        
        // Path
        assertTrue(extractor.canExtract(Path.class));
        Map<String, Object> pathSchema = extractor.extract(Path.class, null);
        assertEquals("string", pathSchema.get("type"));
        assertEquals("path", pathSchema.get("format"));
        
        // BigDecimal
        assertTrue(extractor.canExtract(BigDecimal.class));
        Map<String, Object> decimalSchema = extractor.extract(BigDecimal.class, null);
        assertEquals("number", decimalSchema.get("type"));
    }

    @Test
    @DisplayName("SimpleTypeSchemaExtractor不应处理复杂类型")
    void testSimpleTypeExtractorRejectsComplexTypes() {
        SimpleTypeSchemaExtractor extractor = new SimpleTypeSchemaExtractor();
        
        // List不应被SimpleTypeSchemaExtractor处理（应该由ListSchemaExtractor处理）
        assertFalse(extractor.canExtract(List.class));
        assertFalse(extractor.canExtract(Map.class));
    }

    @Test
    @DisplayName("EnumSchemaExtractor应处理枚举类型")
    void testEnumTypeExtraction() {
        EnumSchemaExtractor extractor = new EnumSchemaExtractor();
        
        assertTrue(extractor.canExtract(TestEnum.class));
        
        // 使用mock回调
        Map<String, Object> enumSchema = extractor.extract(TestEnum.class, 
            type -> Collections.emptyMap());
        
        assertEquals("string", enumSchema.get("type"));
        assertNotNull(enumSchema.get("enum"));
        @SuppressWarnings("unchecked")
        List<String> enumValues = (List<String>) enumSchema.get("enum");
        assertTrue(enumValues.contains("VALUE1"));
        assertTrue(enumValues.contains("VALUE2"));
    }

    @Test
    @DisplayName("EnumSchemaExtractor不应处理非枚举类型")
    void testEnumExtractorRejectsNonEnumTypes() {
        EnumSchemaExtractor extractor = new EnumSchemaExtractor();
        
        assertFalse(extractor.canExtract(String.class));
        assertFalse(extractor.canExtract(Integer.class));
    }

    // 测试用枚举
    enum TestEnum {
        VALUE1, VALUE2, VALUE3
    }
}


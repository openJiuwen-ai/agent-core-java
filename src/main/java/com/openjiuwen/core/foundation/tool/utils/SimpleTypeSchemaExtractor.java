// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.foundation.tool.utils;

import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/**
 * 简单类型Schema提取器
 * 
 * <p>处理基本内置类型，如String、Integer、Boolean等。
 *
 * @author OpenJiuwen
 * @since 2026-01-30
 */
public class SimpleTypeSchemaExtractor extends TypeSchemaExtractor {

    private static final Map<Type, Map<String, Object>> TYPE_MAPPING = new HashMap<>();

    static {
        // 基本类型
        TYPE_MAPPING.put(String.class, Map.of("type", "string"));
        TYPE_MAPPING.put(Integer.class, Map.of("type", "integer"));
        TYPE_MAPPING.put(int.class, Map.of("type", "integer"));
        TYPE_MAPPING.put(Long.class, Map.of("type", "integer"));
        TYPE_MAPPING.put(long.class, Map.of("type", "integer"));
        TYPE_MAPPING.put(Short.class, Map.of("type", "integer"));
        TYPE_MAPPING.put(short.class, Map.of("type", "integer"));
        TYPE_MAPPING.put(Byte.class, Map.of("type", "integer"));
        TYPE_MAPPING.put(byte.class, Map.of("type", "integer"));
        TYPE_MAPPING.put(Float.class, Map.of("type", "number"));
        TYPE_MAPPING.put(float.class, Map.of("type", "number"));
        TYPE_MAPPING.put(Double.class, Map.of("type", "number"));
        TYPE_MAPPING.put(double.class, Map.of("type", "number"));
        TYPE_MAPPING.put(Boolean.class, Map.of("type", "boolean"));
        TYPE_MAPPING.put(boolean.class, Map.of("type", "boolean"));
        TYPE_MAPPING.put(Character.class, Map.of("type", "string"));
        TYPE_MAPPING.put(char.class, Map.of("type", "string"));
        
        // 日期时间类型
        TYPE_MAPPING.put(LocalDateTime.class, Map.of("type", "string", "format", "date-time"));
        TYPE_MAPPING.put(LocalDate.class, Map.of("type", "string", "format", "date"));
        TYPE_MAPPING.put(LocalTime.class, Map.of("type", "string", "format", "time"));
        
        // 特殊类型
        TYPE_MAPPING.put(BigDecimal.class, Map.of("type", "number"));
        TYPE_MAPPING.put(UUID.class, Map.of("type", "string", "format", "uuid"));
        TYPE_MAPPING.put(Path.class, Map.of("type", "string", "format", "path"));
        TYPE_MAPPING.put(byte[].class, Map.of("type", "string", "format", "binary"));
        
        // Object类型
        TYPE_MAPPING.put(Object.class, Map.of());
    }

    @Override
    public boolean canExtract(Type type) {
        return TYPE_MAPPING.containsKey(type);
    }

    @Override
    public Map<String, Object> extract(Type type, Function<Type, Map<String, Object>> typeSchemaResolver) {
        Map<String, Object> schema = TYPE_MAPPING.get(type);
        if (schema != null) {
            return new HashMap<>(schema);
        }
        return Map.of("type", "object");
    }
}


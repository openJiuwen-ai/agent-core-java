// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.foundation.tool.utils;

import java.lang.reflect.Type;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 枚举类型Schema提取器
 * 
 * <p>处理Java枚举类型，生成包含所有枚举值的JSON Schema。
 *
 * @author OpenJiuwen
 * @since 2026-01-30
 */
public class EnumSchemaExtractor extends TypeSchemaExtractor {

    @Override
    public boolean canExtract(Type type) {
        if (type instanceof Class<?> clazz) {
            return clazz.isEnum();
        }
        return false;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> extract(Type type, Function<Type, Map<String, Object>> typeSchemaResolver) {
        Class<? extends Enum<?>> enumClass = (Class<? extends Enum<?>>) type;
        Enum<?>[] constants = enumClass.getEnumConstants();
        
        List<String> enumValues = Arrays.stream(constants)
            .map(Enum::name)
            .collect(Collectors.toList());
        
        String description = "Enum values: " + String.join(", ", enumValues);
        
        Map<String, Object> schema = new HashMap<>();
        schema.put("type", "string");
        schema.put("enum", enumValues);
        schema.put("description", description);
        
        return schema;
    }
}


// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.foundation.tool.utils;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.*;
import java.util.function.Function;

/**
 * Map类型Schema提取器
 * 
 * <p>处理Map、HashMap等映射类型。
 *
 * @author OpenJiuwen
 * @since 2026-01-30
 */
public class MapSchemaExtractor extends TypeSchemaExtractor {

    private static final Set<Class<?>> SUPPORTED_TYPES = Set.of(
        Map.class, HashMap.class, TreeMap.class, LinkedHashMap.class
    );

    @Override
    public boolean canExtract(Type type) {
        if (type instanceof ParameterizedType pt) {
            Type rawType = pt.getRawType();
            if (rawType instanceof Class<?> clazz) {
                return SUPPORTED_TYPES.stream().anyMatch(c -> c.isAssignableFrom(clazz));
            }
        }
        if (type instanceof Class<?> clazz) {
            return SUPPORTED_TYPES.stream().anyMatch(c -> c.isAssignableFrom(clazz));
        }
        return false;
    }

    @Override
    public Map<String, Object> extract(Type type, Function<Type, Map<String, Object>> typeSchemaResolver) {
        Map<String, Object> schema = new HashMap<>();
        schema.put("type", "object");
        
        if (type instanceof ParameterizedType pt) {
            Type[] typeArgs = pt.getActualTypeArguments();
            if (typeArgs.length == 2 && typeSchemaResolver != null) {
                // 提取值类型的schema
                Map<String, Object> valueSchema = typeSchemaResolver.apply(typeArgs[1]);
                schema.put("additionalProperties", valueSchema);
            }
        }
        
        return schema;
    }
}


// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.foundation.tool.utils;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.*;
import java.util.function.Function;

/**
 * List类型Schema提取器
 * 
 * <p>处理List、Set、Collection等集合类型。
 *
 * @author OpenJiuwen
 * @since 2026-01-30
 */
public class ListSchemaExtractor extends TypeSchemaExtractor {

    private static final Set<Class<?>> SUPPORTED_TYPES = Set.of(
        List.class, ArrayList.class, LinkedList.class,
        Set.class, HashSet.class, TreeSet.class, LinkedHashSet.class,
        Collection.class
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
        schema.put("type", "array");
        
        if (type instanceof ParameterizedType pt) {
            Type[] typeArgs = pt.getActualTypeArguments();
            if (typeArgs.length > 0 && typeSchemaResolver != null) {
                Map<String, Object> itemSchema = typeSchemaResolver.apply(typeArgs[0]);
                schema.put("items", itemSchema);
            }
        }
        
        // 如果是Set类型，添加uniqueItems
        if (type instanceof ParameterizedType pt) {
            Type rawType = pt.getRawType();
            if (rawType instanceof Class<?> clazz && Set.class.isAssignableFrom(clazz)) {
                schema.put("uniqueItems", true);
            }
        } else if (type instanceof Class<?> clazz && Set.class.isAssignableFrom(clazz)) {
            schema.put("uniqueItems", true);
        }
        
        return schema;
    }
}


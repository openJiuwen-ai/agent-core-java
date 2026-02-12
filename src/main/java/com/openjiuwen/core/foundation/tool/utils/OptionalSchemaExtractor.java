// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.foundation.tool.utils;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.*;
import java.util.function.Function;

/**
 * Optional类型Schema提取器
 * 
 * <p>处理Optional&lt;T&gt;类型，生成nullable的schema。
 *
 * @author OpenJiuwen
 * @since 2026-01-30
 */
public class OptionalSchemaExtractor extends TypeSchemaExtractor {

    @Override
    public boolean canExtract(Type type) {
        if (type instanceof ParameterizedType pt) {
            Type rawType = pt.getRawType();
            return rawType == Optional.class;
        }
        return type == Optional.class;
    }

    @Override
    public Map<String, Object> extract(Type type, Function<Type, Map<String, Object>> typeSchemaResolver) {
        if (type instanceof ParameterizedType pt) {
            Type[] typeArgs = pt.getActualTypeArguments();
            if (typeArgs.length > 0 && typeSchemaResolver != null) {
                Map<String, Object> innerSchema = new HashMap<>(typeSchemaResolver.apply(typeArgs[0]));
                innerSchema.put("nullable", true);
                return innerSchema;
            }
        }
        
        Map<String, Object> schema = new HashMap<>();
        schema.put("type", "object");
        schema.put("nullable", true);
        return schema;
    }
}


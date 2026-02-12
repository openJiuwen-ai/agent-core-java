// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.foundation.tool.utils;

import java.lang.reflect.Type;
import java.util.Map;
import java.util.function.Function;

/**
 * 类型Schema提取器抽象基类
 * 
 * <p>用于将Java类型转换为JSON Schema。
 * 采用责任链模式，不同的提取器处理不同类型。
 *
 * @author OpenJiuwen
 * @since 2026-01-30
 */
public abstract class TypeSchemaExtractor {

    /**
     * 检查此提取器是否能处理给定的类型
     * 
     * @param type 要检查的类型
     * @return 如果能处理返回true，否则返回false
     */
    public abstract boolean canExtract(Type type);

    /**
     * 提取类型的JSON Schema
     * 
     * @param type 要提取的类型
     * @param typeSchemaResolver 用于解析嵌套类型的回调函数
     * @return JSON Schema（Map形式）
     */
    public abstract Map<String, Object> extract(Type type, Function<Type, Map<String, Object>> typeSchemaResolver);
}


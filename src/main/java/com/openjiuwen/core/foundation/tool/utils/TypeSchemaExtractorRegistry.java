// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.foundation.tool.utils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 类型Schema提取器注册表（单例）
 * 
 * <p>管理所有类型提取器，按优先级顺序注册和查询。
 *
 * @author OpenJiuwen
 * @since 2026-01-30
 */
public class TypeSchemaExtractorRegistry {

    private static volatile TypeSchemaExtractorRegistry instance;
    private final List<TypeSchemaExtractor> extractors;

    private TypeSchemaExtractorRegistry() {
        this.extractors = new ArrayList<>();
        registerDefaultExtractors();
    }

    /**
     * 获取单例实例
     * 
     * @return 注册表实例
     */
    public static TypeSchemaExtractorRegistry getInstance() {
        if (instance == null) {
            synchronized (TypeSchemaExtractorRegistry.class) {
                if (instance == null) {
                    instance = new TypeSchemaExtractorRegistry();
                }
            }
        }
        return instance;
    }

    /**
     * 注册默认的类型提取器
     * 
     * <p>按优先级顺序注册，高优先级的提取器先注册。
     */
    private void registerDefaultExtractors() {
        // 高优先级 - 复合类型
        register(new OptionalSchemaExtractor());
        register(new ListSchemaExtractor());
        register(new MapSchemaExtractor());
        register(new EnumSchemaExtractor());
        
        // 低优先级 - 简单类型（放在最后作为兜底）
        register(new SimpleTypeSchemaExtractor());
    }

    /**
     * 注册类型提取器
     * 
     * @param extractor 提取器
     */
    public void register(TypeSchemaExtractor extractor) {
        extractors.add(extractor);
    }

    /**
     * 获取所有注册的提取器
     * 
     * @return 提取器列表（只读副本）
     */
    public List<TypeSchemaExtractor> getExtractors() {
        return Collections.unmodifiableList(new ArrayList<>(extractors));
    }
}


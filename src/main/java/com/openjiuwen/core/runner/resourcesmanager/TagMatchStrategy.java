// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.runner.resourcesmanager;

/**
 * 标签匹配策略枚举
 * 
 * 对应Python: resources_manager/base.py - TagMatchStrategy
 */
public enum TagMatchStrategy {
    
    /**
     * 全部匹配策略：资源必须包含所有指定的标签
     * 
     * 示例：查询标签["A", "B"]时，只有同时包含标签A和标签B的资源会被匹配
     */
    ALL("all"),
    
    /**
     * 任意匹配策略：资源必须包含任意一个指定的标签
     * 
     * 示例：查询标签["A", "B"]时，包含标签A或标签B的资源都会被匹配
     */
    ANY("any");
    
    private final String value;
    
    TagMatchStrategy(String value) {
        this.value = value;
    }
    
    public String getValue() {
        return value;
    }
    
    public static TagMatchStrategy fromValue(String value) {
        for (TagMatchStrategy strategy : values()) {
            if (strategy.value.equals(value)) {
                return strategy;
            }
        }
        return null;
    }
}


// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.runner.resourcesmanager;

/**
 * 标签更新策略枚举
 * 
 * 对应Python: resources_manager/base.py - TagUpdateStrategy
 */
public enum TagUpdateStrategy {
    
    /**
     * 合并策略：将新标签与现有标签合并，自动去重
     * 
     * 示例：如果资源现有标签["A", "B"]，新标签["B", "C"]合并后结果为["A", "B", "C"]
     */
    MERGE("merge"),
    
    /**
     * 替换策略：完全用新标签替换所有现有标签
     * 
     * 示例：如果资源现有标签["A", "B", "C"]，新标签["X", "Y"]替换后结果为["X", "Y"]
     */
    REPLACE("replace");
    
    private final String value;
    
    TagUpdateStrategy(String value) {
        this.value = value;
    }
    
    public String getValue() {
        return value;
    }
    
    public static TagUpdateStrategy fromValue(String value) {
        for (TagUpdateStrategy strategy : values()) {
            if (strategy.value.equals(value)) {
                return strategy;
            }
        }
        return null;
    }
}


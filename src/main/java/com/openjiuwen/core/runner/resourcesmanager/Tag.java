// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.runner.resourcesmanager;

/**
 * 标签常量定义
 * 
 * 对应Python: resources_manager/base.py - Tag常量
 */
public final class Tag {
    
    private Tag() {
        // 私有构造函数，防止实例化
    }
    
    /**
     * 特殊标签常量，表示所有资源
     * 当用于查询或操作时，匹配所有资源
     */
    public static final String ALL = "*";
    
    /**
     * 默认标签常量，用于没有显式标签的资源
     * 带此标签的资源被视为公共可访问或未分类的通用资源
     */
    public static final String GLOBAL = "__global__";
    
    /**
     * 活跃状态标签常量
     * 用于标记当前活跃且可用的资源
     */
    public static final String ACTIVE = "__active__";
    
    /**
     * 非活跃状态标签常量
     * 用于标记当前非活跃或暂时不可用的资源
     */
    public static final String INACTIVE = "__inactive__";
}


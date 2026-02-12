// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.runner.resourcesmanager;

/**
 * Result模式接口，用于显式错误处理
 * 
 * 对应Python: resources_manager/base.py - Result类型别名
 * 
 * @param <T> 结果值的类型
 */
public interface Result<T> {
    
    /**
     * 检查结果是否表示成功
     * 
     * @return 如果是成功结果返回true
     */
    boolean isOk();
    
    /**
     * 检查结果是否表示错误
     * 
     * @return 如果是错误结果返回true
     */
    boolean isErr();
    
    /**
     * 获取消息/值
     * 
     * @return 封装的值（成功值或错误值）
     */
    T msg();
}


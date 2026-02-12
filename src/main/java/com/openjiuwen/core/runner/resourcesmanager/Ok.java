// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.runner.resourcesmanager;

/**
 * 表示成功操作结果的类
 * 
 * 对应Python: resources_manager/base.py - Ok
 * 
 * @param <T> 成功值的类型
 */
public class Ok<T> implements Result<T> {
    
    private final T value;
    
    public Ok(T value) {
        this.value = value;
    }
    
    @Override
    public boolean isOk() {
        return true;
    }
    
    @Override
    public boolean isErr() {
        return false;
    }
    
    @Override
    public T msg() {
        return value;
    }
    
    /**
     * 获取成功值
     * 
     * @return 封装的成功值
     */
    public T getValue() {
        return value;
    }
}


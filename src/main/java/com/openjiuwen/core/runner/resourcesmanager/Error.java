// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.runner.resourcesmanager;

/**
 * 表示失败操作结果的类
 * 
 * 对应Python: resources_manager/base.py - Error
 * 
 * @param <E> 错误值的类型
 */
public class Error<E> implements Result<E> {
    
    private final E error;
    
    public Error() {
        this(null);
    }
    
    public Error(E error) {
        this.error = error;
    }
    
    @Override
    public boolean isOk() {
        return false;
    }
    
    @Override
    public boolean isErr() {
        return true;
    }
    
    @Override
    public E msg() {
        return error;
    }
    
    /**
     * 获取错误值
     * 
     * @return 封装的错误值
     */
    public E error() {
        return error;
    }
}


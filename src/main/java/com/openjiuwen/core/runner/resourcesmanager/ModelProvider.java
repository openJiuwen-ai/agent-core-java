// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.runner.resourcesmanager;

import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * Model提供者函数式接口
 * 
 * 对应Python: resources_manager/base.py - ModelProvider
 * 
 * 接受可变参数并返回Model实例的可调用对象
 * 用于Model资源的懒加载
 */
@FunctionalInterface
public interface ModelProvider extends Supplier<CompletableFuture<Object>> {
    
    /**
     * 创建Model实例
     * 
     * @return 包含Model的CompletableFuture
     */
    @Override
    CompletableFuture<Object> get();
}


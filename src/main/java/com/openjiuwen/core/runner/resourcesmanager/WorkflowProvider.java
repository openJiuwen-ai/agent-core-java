// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.runner.resourcesmanager;

import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * Workflow提供者函数式接口
 * 
 * 对应Python: resources_manager/base.py - WorkflowProvider
 * 
 * 接受WorkflowCard并返回Workflow实例的可调用对象
 * 用于Workflow资源的懒加载
 * 
 * 注意：由于Workflow模块尚未转换，使用Object作为占位类型
 */
@FunctionalInterface
public interface WorkflowProvider extends Supplier<CompletableFuture<Object>> {
    
    /**
     * 创建Workflow实例
     * 
     * @return 包含Workflow的CompletableFuture
     */
    @Override
    CompletableFuture<Object> get();
}


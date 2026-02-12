// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.runner.resourcesmanager;

import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.core.singleagent.BaseAgent;

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * Agent提供者函数式接口
 * 
 * 对应Python: resources_manager/base.py - AgentProvider
 * 
 * 接受AgentCard并返回BaseAgent实例的可调用对象
 * 用于Agent资源的懒加载，避免注册时立即创建
 */
@FunctionalInterface
public interface AgentProvider extends Function<AgentCard, CompletableFuture<BaseAgent>> {
    
    /**
     * 根据AgentCard创建BaseAgent实例
     * 
     * @param card Agent卡片
     * @return 包含BaseAgent的CompletableFuture
     */
    @Override
    CompletableFuture<BaseAgent> apply(AgentCard card);
}


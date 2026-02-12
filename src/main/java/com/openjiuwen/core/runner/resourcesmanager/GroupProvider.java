// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.runner.resourcesmanager;

import com.openjiuwen.core.multiagent.BaseGroup;
import com.openjiuwen.core.multiagent.schema.GroupCard;

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * AgentGroup提供者函数式接口
 * 
 * 对应Python: resources_manager/base.py - AgentGroupProvider
 * 
 * 接受GroupCard并返回BaseGroup实例的可调用对象
 * 用于AgentGroup资源的懒加载
 */
@FunctionalInterface
public interface GroupProvider extends Function<GroupCard, CompletableFuture<BaseGroup>> {
    
    /**
     * 根据GroupCard创建BaseGroup实例
     * 
     * @param card Group卡片
     * @return 包含BaseGroup的CompletableFuture
     */
    @Override
    CompletableFuture<BaseGroup> apply(GroupCard card);
}


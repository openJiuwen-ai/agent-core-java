// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.runner.resourcesmanager;

import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * AgentGroup管理器
 * 
 * 对应Python: resources_manager/agent_group_manager.py - AgentGroupMgr
 * 
 * @param <T> AgentGroup类型
 */
public class AgentGroupMgr<T> extends AbstractManager<T> {
    
    public AgentGroupMgr() {
        super();
    }
    
    /**
     * 添加AgentGroup（同步Provider）
     * 
     * @param agentGroupId AgentGroup ID
     * @param provider 同步Provider
     */
    public void addAgentGroup(String agentGroupId, Supplier<T> provider) {
        registerResourceProvider(agentGroupId, provider);
    }
    
    /**
     * 添加AgentGroup（异步Provider）
     * 
     * @param agentGroupId AgentGroup ID
     * @param provider 异步Provider
     */
    public void addAsyncAgentGroup(String agentGroupId, Supplier<CompletableFuture<T>> provider) {
        registerAsyncResourceProvider(agentGroupId, provider);
    }
    
    /**
     * 移除AgentGroup
     * 
     * @param agentGroupId AgentGroup ID
     * @return 被移除的Provider，如果不存在返回null
     */
    public Supplier<?> removeAgentGroup(String agentGroupId) {
        return unregisterResourceProvider(agentGroupId);
    }
    
    /**
     * 获取AgentGroup
     * 
     * @param agentGroupId AgentGroup ID
     * @return 包含AgentGroup的CompletableFuture
     */
    public CompletableFuture<T> getAgentGroup(String agentGroupId) {
        return getResourceAsync(agentGroupId);
    }
}


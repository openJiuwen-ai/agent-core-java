// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.runner.resourcesmanager;

import com.openjiuwen.core.runner.RunnerConfig;
import com.openjiuwen.core.runner.drunner.remoteclient.RemoteAgent;
import com.openjiuwen.core.runner.drunner.serveradapter.AgentAdapter;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Agent管理器
 * 
 * 对应Python: resources_manager/agent_manager.py - AgentMgr
 * 
 * @param <T> Agent类型
 */
public class AgentMgr<T> extends AbstractManager<T> {
    
    private static final String AGENT_ADAPTER_PREFIX = "agent_adapter_";
    
    private final Map<String, Object> remoteAgents = new ConcurrentHashMap<>();
    
    public AgentMgr() {
        super();
    }
    
    /**
     * 添加Agent（同步Provider）
     * 
     * @param agentId Agent ID
     * @param provider 同步Provider
     */
    public void addAgent(String agentId, Supplier<T> provider) {
        RunnerConfig config = RunnerConfig.getRunnerConfig();
        
        if (config != null && config.isDistributedMode()) {
            // 分布式模式下创建AgentAdapter
            AgentAdapter adapter = new AgentAdapter(agentId);
            adapter.start();
            remoteAgents.put(AGENT_ADAPTER_PREFIX + agentId, adapter);
        }
        
        if (remoteAgents.containsKey(agentId)) {
            throw new IllegalArgumentException("already same id remote agent, id=" + agentId);
        }
        
        registerResourceProvider(agentId, provider);
    }
    
    /**
     * 添加Agent（异步Provider）
     * 
     * @param agentId Agent ID
     * @param provider 异步Provider
     */
    public void addAsyncAgent(String agentId, Supplier<CompletableFuture<T>> provider) {
        RunnerConfig config = RunnerConfig.getRunnerConfig();
        
        if (config != null && config.isDistributedMode()) {
            AgentAdapter adapter = new AgentAdapter(agentId);
            adapter.start();
            remoteAgents.put(AGENT_ADAPTER_PREFIX + agentId, adapter);
        }
        
        if (remoteAgents.containsKey(agentId)) {
            throw new IllegalArgumentException("already same id remote agent, id=" + agentId);
        }
        
        registerAsyncResourceProvider(agentId, provider);
    }
    
    /**
     * 添加RemoteAgent
     * 
     * @param agentId Agent ID
     * @param remoteAgent RemoteAgent实例
     */
    public void addRemoteAgent(String agentId, RemoteAgent remoteAgent) {
        if (remoteAgents.containsKey(agentId)) {
            throw new IllegalArgumentException("already same id remote agent, id=" + agentId);
        }
        remoteAgents.put(agentId, remoteAgent);
    }
    
    /**
     * 移除Agent
     * 
     * @param agentId Agent ID
     * @return 被移除的Provider，如果不存在返回null
     */
    public Supplier<?> removeAgent(String agentId) {
        RunnerConfig config = RunnerConfig.getRunnerConfig();
        
        if (config != null && config.isDistributedMode()) {
            Object adapter = remoteAgents.remove(AGENT_ADAPTER_PREFIX + agentId);
            if (adapter instanceof AgentAdapter) {
                ((AgentAdapter) adapter).stop();
            }
        }
        
        remoteAgents.remove(agentId);
        return unregisterResourceProvider(agentId);
    }
    
    /**
     * 获取Agent
     * 优先返回RemoteAgent，其次调用Provider
     * 
     * @param agentId Agent ID
     * @return 包含Agent或RemoteAgent的CompletableFuture
     */
    @SuppressWarnings("unchecked")
    public CompletableFuture<Object> getAgent(String agentId) {
        // 优先检查remoteAgents
        Object remoteAgent = remoteAgents.get(agentId);
        if (remoteAgent != null) {
            return CompletableFuture.completedFuture(remoteAgent);
        }
        
        // 调用Provider获取本地Agent
        return (CompletableFuture<Object>) (CompletableFuture<?>) getResourceAsync(agentId);
    }
    
    /**
     * 检查是否有RemoteAgent
     * 
     * @param agentId Agent ID
     * @return 如果存在返回true
     */
    public boolean hasRemoteAgent(String agentId) {
        return remoteAgents.containsKey(agentId);
    }
}


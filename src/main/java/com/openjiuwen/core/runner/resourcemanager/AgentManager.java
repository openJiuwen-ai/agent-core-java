/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.resourcemanager;

import com.openjiuwen.core.runner.RunnerConfig;
import com.openjiuwen.core.runner.drunner.remote_client.RemoteAgent;
import com.openjiuwen.core.runner.drunner.server_adapter.AgentAdapter;
import com.openjiuwen.core.single_agent.schema.AgentCard;

import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

/**
 * Agent resource manager with local-provider and distributed-agent support.
 *
 * <p>Mirrors Python's {@code AgentMgr} in
 * {@code openjiuwen/core/runner/resources_manager/agent_manager.py}.</p>
 */
public class AgentManager extends AbstractManager<Object> {

    private static final String AGENT_ADAPTER_PREFIX = "agent_adapter_";

    private final ThreadSafeDict<String, Object> remoteAgents = new ThreadSafeDict<>();

    public void addAgent(String agentId, Supplier<?> agentProvider) {
        addAgent(agentId, agentProvider, null, null);
    }

    public void addAgent(String agentId, Supplier<?> agentProvider,
                         AgentCard card, String interfaceUrl) {
        if (RunnerConfig.getRunnerConfig().isDistributedMode()) {
            AgentCard effectiveCard = cardWithInterfaceUrl(card, interfaceUrl);
            AgentAdapter adapter = new AgentAdapter(agentId, "", effectiveCard);
            adapter.start();
            remoteAgents.put(AGENT_ADAPTER_PREFIX + agentId, adapter);
        }
        if (remoteAgents.get(agentId) != null) {
            throw new IllegalArgumentException("already same id remote agent, id=" + agentId);
        }
        registerResourceProvider(agentId, agentProvider);
    }

    public void addAgent(String agentId, RemoteAgent agent) {
        addAgent(agentId, agent, null, null);
    }

    public void addAgent(String agentId, RemoteAgent agent, AgentCard card, String interfaceUrl) {
        if (remoteAgents.get(agentId) != null) {
            throw new IllegalArgumentException("already same id remote agent, id=" + agentId);
        }
        remoteAgents.put(agentId, agent);
    }

    /**
     * Returns the registered remote agent/adapter first, then falls back to a local provider.
     *
     * <p>The return value is intentionally a dynamic union because the Python source returns either
     * a {@code RemoteAgent}, an {@code AgentAdapter}, or a provider-created local {@code BaseAgent}.</p>
     *
     * @param agentId agent id
     * @return remote adapter/agent or local agent
     */
    public CompletionStage<Object> getAgent(String agentId) {
        Object agent = remoteAgents.get(agentId, null);
        if (agent != null) {
            return java.util.concurrent.CompletableFuture.completedFuture(agent);
        }
        return getResource(agentId).thenApply(localAgent -> localAgent);
    }

    public Supplier<?> removeAgent(String agentId) {
        if (RunnerConfig.getRunnerConfig().isDistributedMode()) {
            Object adapter = remoteAgents.pop(AGENT_ADAPTER_PREFIX + agentId);
            if (adapter instanceof AgentAdapter agentAdapter) {
                agentAdapter.stop().toCompletableFuture().join();
            }
        }
        remoteAgents.pop(agentId, null);
        return unregisterResourceProvider(agentId);
    }

    public boolean containsRemoteAgent(String agentId) {
        return remoteAgents.get(agentId, null) != null;
    }

    public boolean containsDistributedAdapter(String agentId) {
        return remoteAgents.get(AGENT_ADAPTER_PREFIX + agentId, null) != null;
    }

    private static AgentCard cardWithInterfaceUrl(AgentCard source, String interfaceUrl) {
        if (source == null || interfaceUrl == null) {
            return source;
        }
        AgentCard copy = new AgentCard(source.getId(), source.getName(), source.getDescription());
        copy.setInputParams(source.getInputParams());
        copy.setOutputParams(source.getOutputParams());
        copy.setInterfaceUrl(interfaceUrl);
        return copy;
    }
}

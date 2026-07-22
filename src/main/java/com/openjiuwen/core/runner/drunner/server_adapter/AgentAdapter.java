/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.drunner.server_adapter;

import com.openjiuwen.core.multitenant.TenantContext;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.runner.drunner.DistributedRunner;
import com.openjiuwen.core.session.stream.StreamMode;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Exposes a local agent over the distributed-runner MQ transport.
 * 
 * @since 0.1.7
 */
public class AgentAdapter {
    private final String agentId;
    private final String version;
    private final String topic;
    private final MqServerAdapter server;
    private boolean isStarted;

    /**
     * AgentAdapter.
     * 
     * @param agentId agentId
     * @param version version
     * @since 0.1.7
     */
    public AgentAdapter(String agentId, String version) {
        this.agentId = agentId;
        this.version = version != null ? version : "";
        this.topic = DistributedRunner.agentTopic(agentId, this.version);
        this.server = new MqServerAdapter(agentId, topic, this::handleInvoke, this::handleStream);
    }

    /**
     * AgentAdapter.
     * 
     * @param agentId agentId
     * @since 0.1.7
     */
    public AgentAdapter(String agentId) {
        this(agentId, "");
    }

    /**
     * start.
     * 
     * @since 0.1.7
     */
    public void start() {
        server.start();
        isStarted = true;
    }

    /**
     * stop.
     * 
     * @since 0.1.7
     */
    public void stop() {
        server.stop();
        isStarted = false;
    }

    /**
     * isStarted.
     * 
     * @return the result
     * @since 0.1.7
     */
    public boolean isStarted() {
        return isStarted;
    }

    /**
     * isStopped.
     * 
     * @return the result
     * @since 0.1.7
     */
    public boolean isStopped() {
        return !isStarted;
    }

    /**
     * getTopic.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getTopic() {
        return topic;
    }

    /**
     * getServer.
     * 
     * @return the result
     * @since 0.1.7
     */
    public MqServerAdapter getServer() {
        return server;
    }

    /**
     * handleInvoke.
     * 
     * @param inputs inputs
     * @return the result
     * @since 0.1.7
     */
    Object handleInvoke(Map<String, Object> inputs) {
        TenantContext tenantCtx = extractTenantContext(inputs);
        if (tenantCtx != null) {
            return Runner.runAgent(agentId, inputs, null, null, null, tenantCtx);
        }
        return Runner.runAgent(agentId, inputs, null, null);
    }

    /**
     * handleStream.
     * 
     * @param inputs inputs
     * @return the result
     * @since 0.1.7
     */
    Iterator<Object> handleStream(Map<String, Object> inputs) {
        TenantContext tenantCtx = extractTenantContext(inputs);
        if (tenantCtx != null) {
            return Runner.runAgentStreaming(agentId, inputs, null, null, List.of(StreamMode.OUTPUT), null, tenantCtx);
        }
        return Runner.runAgentStreaming(agentId, inputs, null, null, List.of(StreamMode.OUTPUT));
    }

    private TenantContext extractTenantContext(Map<String, Object> inputs) {
        if (inputs != null) {
            Object tenantId = inputs.get("tenant_id");
            if (tenantId instanceof String tid && !tid.isBlank()) {
                return TenantContext.builder().tenantId(tid).build();
            }
        }
        return null;
    }
}

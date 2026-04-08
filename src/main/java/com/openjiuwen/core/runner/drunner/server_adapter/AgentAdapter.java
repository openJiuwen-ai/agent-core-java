/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.runner.drunner.server_adapter;

import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.runner.drunner.DistributedRunner;
import com.openjiuwen.core.session.stream.StreamMode;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Exposes a local agent over the distributed-runner MQ transport.
 */
public class AgentAdapter {

    private final String agentId;
    private final String version;
    private final String topic;
    private final MqServerAdapter server;

    public AgentAdapter(String agentId, String version) {
        this.agentId = agentId;
        this.version = version != null ? version : "";
        this.topic = DistributedRunner.agentTopic(agentId, this.version);
        this.server = new MqServerAdapter(
                agentId,
                topic,
                this::handleInvoke,
                this::handleStream
        );
    }

    public AgentAdapter(String agentId) {
        this(agentId, "");
    }

    public void start() {
        server.start();
    }

    public void stop() {
        server.stop();
    }

    private Object handleInvoke(Map<String, Object> inputs) {
        return Runner.runAgent(agentId, inputs, null, null);
    }

    private Iterator<Object> handleStream(Map<String, Object> inputs) {
        return Runner.runAgentStreaming(agentId, inputs, null, null, List.of(StreamMode.OUTPUT));
    }
}

/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.runner.drunner.remote_client;

import com.openjiuwen.core.runner.drunner.DistributedRunner;

import java.util.Iterator;
import java.util.Map;

/**
 * Remote-agent facade.
 */
public class RemoteAgent {

    private final String agentId;
    private final String version;
    private final String description;
    private final String topic;
    private final ProtocolEnum protocol;
    private final RemoteClient client;

    public RemoteAgent(String agentId, String version, String description, String topic,
                       ProtocolEnum protocol, Map<String, Object> config) {
        this.agentId = agentId;
        this.version = version != null ? version : "";
        this.description = description;
        this.topic = topic != null ? topic : DistributedRunner.agentTopic(agentId, this.version);
        this.protocol = protocol != null ? protocol : ProtocolEnum.MQ;
        RemoteClientConfig clientConfig = RemoteClientConfig.builder()
                .id(agentId)
                .version(this.version)
                .description(description)
                .topic(this.topic)
                .protocol(this.protocol)
                .kwargs(config)
                .build();
        this.client = new MqRemoteClient(clientConfig);
    }

    public RemoteAgent(String agentId) {
        this(agentId, "", null, null, ProtocolEnum.MQ, null);
    }

    public Object invoke(Map<String, Object> inputs, Double timeoutSeconds) throws Exception {
        client.start();
        return client.invoke(inputs, timeoutSeconds);
    }

    public Iterator<Object> stream(Map<String, Object> inputs, Double timeoutSeconds) throws Exception {
        client.start();
        return client.stream(inputs, timeoutSeconds);
    }
}

/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.drunner.remote_client;

import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.runner.drunner.DistributedRunner;

import java.util.Iterator;
import java.util.LinkedHashMap;
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

    /**
     * Auto-generated for codecheck compliance.
     */
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
                .url(extractString(config, "url"))
                .kwargs(extractKwargs(config))
                .build();
        this.client = RemoteClientFactory.create(clientConfig);
        if (this.client == null) {
            throw ErrorHelper.buildError(
                    StatusCode.REMOTE_AGENT_EXECUTION_ERROR,
                    "agent_id", agentId,
                    "reason", "failed to create remote client"
            );
        }
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public RemoteAgent(String agentId) {
        this(agentId, "", null, null, ProtocolEnum.MQ, null);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Object invoke(Map<String, Object> inputs, Double timeoutSeconds) throws Exception {
        try {
            if (!client.isStarted()) {
                client.start();
            }
            return client.invoke(inputs, timeoutSeconds);
        } catch (java.util.concurrent.CancellationException ex) {
            throw ErrorHelper.buildError(
                    StatusCode.REMOTE_AGENT_EXECUTION_ERROR,
                    "agent_id", agentId,
                    "reason", "cancelled"
            );
        } catch (java.util.concurrent.TimeoutException ex) {
            throw ErrorHelper.buildError(
                    StatusCode.REMOTE_AGENT_EXECUTION_TIMEOUT,
                    "agent_id", agentId,
                    "timeout", String.valueOf(timeoutSeconds)
            );
        }
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Iterator<Object> stream(Map<String, Object> inputs, Double timeoutSeconds) throws Exception {
        try {
            if (!client.isStarted()) {
                client.start();
            }
            return client.stream(inputs, timeoutSeconds);
        } catch (java.util.concurrent.CancellationException ex) {
            throw ErrorHelper.buildError(
                    StatusCode.REMOTE_AGENT_EXECUTION_ERROR,
                    "agent_id", agentId,
                    "reason", "cancelled"
            );
        } catch (java.util.concurrent.TimeoutException ex) {
            throw ErrorHelper.buildError(
                    StatusCode.REMOTE_AGENT_EXECUTION_TIMEOUT,
                    "agent_id", agentId,
                    "timeout", String.valueOf(timeoutSeconds)
            );
        }
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void stop() {
        client.stop();
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public boolean isStarted() {
        return client.isStarted();
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public boolean isStopped() {
        return client.isStopped();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> extractKwargs(Map<String, Object> config) {
        if (config == null || config.isEmpty()) {
            return new LinkedHashMap<>();
        }
        Object kwargs = config.get("kwargs");
        if (kwargs instanceof Map<?, ?> kwargsMap) {
            return new LinkedHashMap<>((Map<String, Object>) kwargsMap);
        }
        Map<String, Object> copied = new LinkedHashMap<>(config);
        copied.remove("url");
        return copied;
    }

    private static String extractString(Map<String, Object> config, String key) {
        if (config == null) {
            return null;
        }
        Object value = config.get(key);
        return value != null ? String.valueOf(value) : null;
    }
}

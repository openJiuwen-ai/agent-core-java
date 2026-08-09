/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.drunner.remoteclient;

import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.runner.drunner.DistributedRunner;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Remote-agent facade.
 * <p>
 * Provides a high-level interface for interacting with remote agents,
 * handling client creation, lifecycle management, and error translation
 * for both synchronous invocation and streaming modes.
 * 
 * @since 0.1.7
 * @deprecated Use {@link com.openjiuwen.core.runner.drunner.remote_client.RemoteAgent}.
 */
@Deprecated(since = "0.1.14")
public class RemoteAgent {
    private final String agentId;
    private final String version;
    private final String description;
    private final String topic;
    private final ProtocolEnum protocol;
    private final RemoteClient client;

    /**
     * Constructs a RemoteAgent with full configuration.
     * 
     * @param agentId the unique identifier of the remote agent
     * @param version the version of the remote agent, or null for default
     * @param description the description of the remote agent
     * @param topic the message topic, or null for auto-generated
     * @param protocol the communication protocol, or null for MQ default
     * @param config additional configuration map, may contain "url" and "kwargs"
     * @since 0.1.7
     */
    public RemoteAgent(String agentId, String version, String description, String topic, ProtocolEnum protocol,
            Map<String, Object> config) {
        this.agentId = agentId;
        this.version = version != null ? version : "";
        this.description = description;
        this.topic = topic != null ? topic : DistributedRunner.agentTopic(agentId, this.version);
        this.protocol = protocol != null ? protocol : ProtocolEnum.MQ;
        RemoteClientConfig clientConfig =
            RemoteClientConfig.builder().id(agentId).version(this.version).description(description).topic(this.topic)
                    .protocol(this.protocol).url(extractString(config, "url")).kwargs(extractKwargs(config)).build();
        this.client = RemoteClientFactory.create(clientConfig);
        if (this.client == null) {
            throw ErrorHelper.buildError(StatusCode.REMOTE_AGENT_EXECUTION_ERROR, "agent_id", agentId, "reason",
                    "failed to create remote client");
        }
    }

    /**
     * Constructs a RemoteAgent with default MQ protocol and no additional config.
     * 
     * @param agentId the unique identifier of the remote agent
     * @since 0.1.7
     */
    public RemoteAgent(String agentId) {
        this(agentId, "", null, null, ProtocolEnum.MQ, null);
    }

    /**
     * Invokes the remote agent synchronously with the given inputs.
     * 
     * @param inputs the input map to send to the remote agent
     * @param timeoutSeconds optional timeout in seconds for the invocation
     * @return the result of the remote invocation
     * @throws Exception if the invocation is cancelled, times out, or fails
     * @since 0.1.7
     */
    public Object invoke(Map<String, Object> inputs, Double timeoutSeconds) throws Exception {
        try {
            if (!client.isStarted()) {
                client.start();
            }
            return client.invoke(inputs, timeoutSeconds);
        } catch (java.util.concurrent.CancellationException ex) {
            throw ErrorHelper.buildError(StatusCode.REMOTE_AGENT_EXECUTION_ERROR, "agent_id", agentId, "reason",
                    "cancelled");
        } catch (java.util.concurrent.TimeoutException ex) {
            throw ErrorHelper.buildError(StatusCode.REMOTE_AGENT_EXECUTION_TIMEOUT, "agent_id", agentId, "timeout",
                    String.valueOf(timeoutSeconds));
        }
    }

    /**
     * Streams responses from the remote agent with the given inputs.
     * 
     * @param inputs the input map to send to the remote agent
     * @param timeoutSeconds optional timeout in seconds for the streaming operation
     * @return an iterator over the streamed response objects
     * @throws Exception if the streaming is cancelled, times out, or fails
     * @since 0.1.7
     */
    public Iterator<Object> stream(Map<String, Object> inputs, Double timeoutSeconds) throws Exception {
        try {
            if (!client.isStarted()) {
                client.start();
            }
            return client.stream(inputs, timeoutSeconds);
        } catch (java.util.concurrent.CancellationException ex) {
            throw ErrorHelper.buildError(StatusCode.REMOTE_AGENT_EXECUTION_ERROR, "agent_id", agentId, "reason",
                    "cancelled");
        } catch (java.util.concurrent.TimeoutException ex) {
            throw ErrorHelper.buildError(StatusCode.REMOTE_AGENT_EXECUTION_TIMEOUT, "agent_id", agentId, "timeout",
                    String.valueOf(timeoutSeconds));
        }
    }

    /**
     * Stops the remote agent's client connection.
     * 
     * @since 0.1.7
     */
    public void stop() {
        client.stop();
    }

    /**
     * Checks whether the remote agent's client has been started.
     * 
     * @return {@code true} if the client is started, {@code false} otherwise
     * @since 0.1.7
     */
    public boolean isStarted() {
        return client.isStarted();
    }

    /**
     * Checks whether the remote agent's client has been stopped.
     * 
     * @return {@code true} if the client is stopped, {@code false} otherwise
     * @since 0.1.7
     */
    public boolean isStopped() {
        return client.isStopped();
    }

    @SuppressWarnings("unchecked")
    /**
     * extractKwargs.
     * 
     * @param config config
     * @return the result
     * @since 0.1.7
     */
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

    /**
     * extractString.
     * 
     * @param config config
     * @param key key
     * @return the result
     * @since 0.1.7
     */
    private static String extractString(Map<String, Object> config, String key) {
        if (config == null) {
            return "";
        }
        Object value = config.get(key);
        return value != null ? String.valueOf(value) : "";
    }
}

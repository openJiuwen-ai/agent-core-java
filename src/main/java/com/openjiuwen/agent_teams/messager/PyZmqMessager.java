/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.messager;

import com.openjiuwen.agent_teams.schema.events.EventMessage;

/**
 * Minimal ZeroMQ-style messager stub for agent_teams external transport parity.
 * <p>
 * Mirrors Python's {@code PyZmqMessager} in
 * {@code openjiuwen.agent_teams.messager.pyzmq_backend}.
 * <p>
 * Java currently does not bundle a ZeroMQ dependency, so this class preserves
 * the transport surface and configuration path while failing closed on real IO.
 */
public class PyZmqMessager implements Messager {

    private final MessagerTransportConfig config;

    public PyZmqMessager(MessagerTransportConfig config) {
        this.config = config != null ? config : new MessagerTransportConfig();
    }

    public MessagerTransportConfig getConfig() {
        return config;
    }

    @Override
    public void start() {
        // no-op placeholder until a Java ZMQ transport is introduced
    }

    @Override
    public void stop() {
        // no-op placeholder until a Java ZMQ transport is introduced
    }

    @Override
    public void publish(String topicId, EventMessage message) {
        throw new UnsupportedOperationException("PyZmqMessager requires an optional Java ZeroMQ transport dependency");
    }

    @Override
    public void subscribe(String topicId, MessagerHandler handler) {
        throw new UnsupportedOperationException("PyZmqMessager requires an optional Java ZeroMQ transport dependency");
    }

    @Override
    public void unsubscribe(String topicId) {
        throw new UnsupportedOperationException("PyZmqMessager requires an optional Java ZeroMQ transport dependency");
    }

    @Override
    public void send(String agentId, EventMessage message) {
        throw new UnsupportedOperationException("PyZmqMessager requires an optional Java ZeroMQ transport dependency");
    }

    @Override
    public void registerDirectMessageHandler(MessagerHandler handler) {
        throw new UnsupportedOperationException("PyZmqMessager requires an optional Java ZeroMQ transport dependency");
    }

    @Override
    public void unregisterDirectMessageHandler() {
        throw new UnsupportedOperationException("PyZmqMessager requires an optional Java ZeroMQ transport dependency");
    }
}

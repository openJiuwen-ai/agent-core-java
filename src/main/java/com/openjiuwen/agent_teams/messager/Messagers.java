/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.messager;

/**
 * Factory helpers for minimal Java messager transports.
 *
 * <p>Mirrors Python's {@code create_messager(...)} in
 * {@code openjiuwen.agent_teams.messager.base}.</p>
 */
public final class Messagers {

    private Messagers() {
    }

    public static Messager createMessager(MessagerTransportConfig config) {
        if (config == null || config.getBackend() == null || "inprocess".equalsIgnoreCase(config.getBackend())) {
            return new InProcessMessager(config);
        }
        if ("pyzmq".equalsIgnoreCase(config.getBackend()) || "zmq".equalsIgnoreCase(config.getBackend())) {
            return new PyZmqMessager(config);
        }
        throw new IllegalArgumentException("Unsupported messager backend: " + config.getBackend());
    }
}

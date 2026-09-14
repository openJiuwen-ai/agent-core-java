/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.messager;

/**
 * Factory helpers for messager transports.
 * <p>
 * Mirrors Python's {@code create_messager(...)} in
 * {@code openjiuwen/agent_teams/messager/base.py}.
 */
public final class Messagers {

    private Messagers() {
    }

    public static Messager createMessager(MessagerTransportConfig config) {
        MessagerTransportConfig effectiveConfig = config != null ? config : new MessagerTransportConfig();
        String backend = effectiveConfig.getBackend();
        if (backend == null || "inprocess".equalsIgnoreCase(backend)) {
            return new InProcessMessager(effectiveConfig);
        }
        if ("pyzmq".equalsIgnoreCase(backend)) {
            return new PyZmqMessager(effectiveConfig);
        }
        throw new IllegalArgumentException("Unsupported messager backend: " + backend);
    }
}

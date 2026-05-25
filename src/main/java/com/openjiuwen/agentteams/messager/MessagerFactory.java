/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentteams.messager;

/**
 * Factory for creating team messager implementations by transport backend.
 *
 * @since 1.0
 */
public final class MessagerFactory {
    private MessagerFactory() {
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static Messager createMessager(MessagerTransportConfig config) {
        if (config == null || "inprocess".equals(config.getBackend())) {
            return new InProcessMessager(config);
        }
        if ("pyzmq".equals(config.getBackend())) {
            return new PyZmqMessager(config);
        }
        throw new IllegalArgumentException("Unsupported messager backend: " + config.getBackend());
    }
}

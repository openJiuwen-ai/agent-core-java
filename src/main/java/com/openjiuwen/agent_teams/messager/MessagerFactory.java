// Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.

package com.openjiuwen.agent_teams.messager;

/**
 * Factory for creating messager transport instances.
 * 
 * Mirrors Python's agent_teams.messager.base.create_messager
 * 
 * @since 0.1.12
 */
public final class MessagerFactory {
    
    /**
     * Build a messager transport from JSON-safe config.
     * 
     * @param config Transport configuration
     * @return A Messager instance appropriate for the backend type
     * @throws IllegalArgumentException if the backend type is unsupported
     */
    public static Messager createMessager(MessagerTransportConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("Config cannot be null");
        }
        return Messagers.createMessager(config);
    }
    
    // Private constructor to prevent instantiation
    private MessagerFactory() {
        throw new AssertionError("MessagerFactory class should not be instantiated");
    }
}

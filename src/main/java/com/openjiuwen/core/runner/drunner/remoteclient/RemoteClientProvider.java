/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.drunner.remoteclient;

/**
 * Provider interface for creating remote client instances.
 * <p>
 * Implementations are discovered via {@link java.util.ServiceLoader} from
 * {@code META-INF/services/com.openjiuwen.core.runner.drunner.remote_client.RemoteClientProvider}.
 * Each provider declares which {@code typeName()} (protocol) it supports.
 * Service adapters can also register providers programmatically via
 * {@link RemoteClientFactory#register(String, RemoteClientProvider)}.
 *
 * @since 0.1.12
 * @see RemoteClientFactory
 * @see RemoteClient
 */
public interface RemoteClientProvider {
    /**
     * The protocol type name this provider handles (e.g., "MQ", "A2A").
     *
     * @return the type name for registration
     */
    String typeName();

    /**
     * Create a remote client for the given configuration.
     *
     * @param config the remote client configuration
     * @return a new RemoteClient instance
     */
    RemoteClient create(RemoteClientConfig config);
}

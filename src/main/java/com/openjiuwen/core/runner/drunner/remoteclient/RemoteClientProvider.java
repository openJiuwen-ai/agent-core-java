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
 * @see RemoteClientFactory
 * @see RemoteClient
 * @since 0.1.7
 */
public interface RemoteClientProvider {
    /**
     * typeName.
     * 
     * @return the result
     * @since 0.1.7
     */
    String typeName();

    /**
     * Create a remote client for the given configuration.
     * 
     * @param config the remote client configuration
     * @return a new RemoteClient instance
     * @since 0.1.7
     */
    RemoteClient create(RemoteClientConfig config);
}

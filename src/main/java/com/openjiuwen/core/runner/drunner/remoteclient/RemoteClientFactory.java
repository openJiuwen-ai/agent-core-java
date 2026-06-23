/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.drunner.remoteclient;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.extensions.a2a.A2ARemoteClient;

import java.util.Map;
import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Factory and registry for remote client instances.
 * <p>
 * Built-in protocols are discovered via {@link ServiceLoader} from
 * {@code META-INF/services/com.openjiuwen.core.runner.drunner.remote_client.RemoteClientProvider}.
 * Service adapters can register additional protocols via
 * {@link #register(String, RemoteClientProvider)} without modifying Core source.
 * <p>
 * Calling point: remote-agent Tool creation in the execution chain.
 *
 * @see RemoteClientProvider
 * @see RemoteClient
 * @since 0.1.12
 */
public final class RemoteClientFactory {
    private static final Map<String, RemoteClientProvider> REGISTRY = new ConcurrentHashMap<>();

    static {
        // Discover and register providers via ServiceLoader
        for (RemoteClientProvider provider : ServiceLoader.load(RemoteClientProvider.class)) {
            REGISTRY.putIfAbsent(provider.typeName(), provider);
        }
    }

    private RemoteClientFactory() {
    }

    /**
     * Register a remote client provider for a given protocol name.
     *
     * @param protocol the protocol name (e.g. "MQ", "A2A", "GRPC")
     * @param provider the provider that creates RemoteClient instances
     */
    public static void register(String protocol, RemoteClientProvider provider) {
        REGISTRY.put(protocol, provider);
    }

    /**
     * Create a remote client from a configuration.
     * <p>
     * The protocol field in the config determines which provider is used.
     * Falls back to MQ if no protocol is specified.
     *
     * @param config the remote client configuration
     * @return a new RemoteClient instance
     * @throws com.openjiuwen.core.common.exception.BaseError if config is null or protocol is unknown
     */
    public static RemoteClient create(RemoteClientConfig config) {
        if (config == null) {
            throw ErrorHelper.buildError(
                    StatusCode.REMOTE_AGENT_EXECUTION_ERROR,
                    "agent_id", "",
                    "reason", "remote client config is null"
            );
        }
        ProtocolEnum protocol = config.getProtocol() != null ? config.getProtocol() : ProtocolEnum.MQ;
        RemoteClientProvider provider = REGISTRY.get(protocol.name());
        if (provider == null) {
            throw ErrorHelper.buildError(
                    StatusCode.REMOTE_AGENT_EXECUTION_ERROR,
                    "agent_id", String.valueOf(config.getId()),
                    "reason", "No remote client provider registered for protocol: " + protocol
            );
        }
        return provider.create(config);
    }

    /**
     * Create an A2A remote client.
     *
     * @param config the remote client configuration
     * @return a new A2A RemoteClient instance
     * @throws com.openjiuwen.core.common.exception.BaseError if creation fails
     */
    public static RemoteClient createA2A(RemoteClientConfig config) {
        try {
            return new A2ARemoteClient(config);
        } catch (Exception ex) {
            throw ErrorHelper.buildError(
                    StatusCode.REMOTE_AGENT_EXECUTION_ERROR,
                    "agent_id", config != null ? String.valueOf(config.getId()) : "",
                    "reason", "failed to instantiate A2A remote client plugin"
            );
        }
    }

    /**
     * Check whether a provider is registered for the given protocol.
     *
     * @param protocol the protocol name
     * @return true if a provider exists
     */
    public static boolean hasProvider(String protocol) {
        return protocol != null && REGISTRY.containsKey(protocol);
    }
}

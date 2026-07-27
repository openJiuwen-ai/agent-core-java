/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.drunner.remoteclient.provider;

import com.openjiuwen.core.runner.drunner.remoteclient.RemoteClient;
import com.openjiuwen.core.runner.drunner.remoteclient.RemoteClientConfig;
import com.openjiuwen.core.runner.drunner.remoteclient.RemoteClientProvider;
import com.openjiuwen.extensions.a2a.A2ARemoteClient;

import java.util.Iterator;
import java.util.Map;

/**
 * Built-in remote client provider for A2A (Agent-to-Agent) protocol.
 * <p>
 * Creates remote clients that communicate with other agents using the
 * A2A JSON-RPC protocol, enabling cross-framework agent interoperability.
 *
 * @since 0.1.12
 * @see RemoteClientProvider
 * @see com.openjiuwen.extensions.a2a.A2ARemoteClient
 */
public final class A2ARemoteClientProvider implements RemoteClientProvider {
    /**
     * Returns the A2A protocol type name.
     *
     * @return the type name "A2A"
     */
    @Override
    public String typeName() {
        return "A2A";
    }

    /**
     * Creates a remote client using A2A protocol.
     *
     * @param config the remote client configuration
     * @return a new A2ARemoteClient instance wrapped as a RemoteClient
     */
    @Override
    public RemoteClient create(RemoteClientConfig config) {
        com.openjiuwen.core.runner.drunner.remote_client.RemoteClientConfig adaptedConfig =
                com.openjiuwen.core.runner.drunner.remote_client.RemoteClientConfig.builder()
                        .id(config.getId())
                        .version(config.getVersion())
                        .name(config.getName())
                        .description(config.getDescription())
                        .protocol(config.getProtocol() != null
                                ? com.openjiuwen.core.runner.drunner.remote_client.ProtocolEnum.valueOf(config.getProtocol().name())
                                : null)
                        .type(config.getType())
                        .topic(config.getTopic())
                        .url(config.getUrl())
                        .kwargs(config.getKwargs())
                        .build();
        A2ARemoteClient a2aClient = new A2ARemoteClient(adaptedConfig);
        return new A2ARemoteClientAdapter(a2aClient);
    }

    /**
     * Adapter that wraps an A2ARemoteClient to implement the remoteclient.RemoteClient interface.
     */
    private static final class A2ARemoteClientAdapter implements RemoteClient {
        private final A2ARemoteClient delegate;

        A2ARemoteClientAdapter(A2ARemoteClient delegate) {
            this.delegate = delegate;
        }

        @Override
        public void start() {
            delegate.start().toCompletableFuture().join();
        }

        @Override
        public void stop() {
            delegate.stop().toCompletableFuture().join();
        }

        @Override
        public boolean isStarted() {
            return delegate.isStarted();
        }

        @Override
        public Object invoke(Map<String, Object> inputs, Double timeoutSeconds) throws Exception {
            return delegate.invoke(inputs, timeoutSeconds).toCompletableFuture().join();
        }

        @Override
        public Iterator<Object> stream(Map<String, Object> inputs, Double timeoutSeconds) throws Exception {
            return delegate.stream(inputs, timeoutSeconds);
        }
    }
}

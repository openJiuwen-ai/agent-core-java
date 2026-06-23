/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.drunner.remoteclient.provider;

import com.openjiuwen.core.runner.drunner.remoteclient.RemoteClient;
import com.openjiuwen.core.runner.drunner.remoteclient.RemoteClientConfig;
import com.openjiuwen.core.runner.drunner.remoteclient.RemoteClientProvider;
import com.openjiuwen.extensions.a2a.A2ARemoteClient;

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
     * @return a new A2ARemoteClient instance
     */
    @Override
    public RemoteClient create(RemoteClientConfig config) {
        return new A2ARemoteClient(config);
    }
}

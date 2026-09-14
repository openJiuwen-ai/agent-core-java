/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.drunner.remoteclient.provider;

import com.openjiuwen.core.runner.drunner.remoteclient.MqRemoteClient;
import com.openjiuwen.core.runner.drunner.remoteclient.RemoteClient;
import com.openjiuwen.core.runner.drunner.remoteclient.RemoteClientConfig;
import com.openjiuwen.core.runner.drunner.remoteclient.RemoteClientProvider;

/**
 * Built-in remote client provider for MQ (Message Queue) protocol.
 * <p>
 * Creates remote clients that communicate with distributed agents via
 * message queue, suitable for asynchronous and distributed execution scenarios.
 * 
 * @author OpenJiuwen Team
 * @see RemoteClientProvider
 * @see com.openjiuwen.core.runner.drunner.remoteclient.MqRemoteClient
 * @since 0.1.7
 * @deprecated Use {@link com.openjiuwen.core.runner.drunner.remote_client}.
 */
@Deprecated(since = "0.1.14")
public final class MqRemoteClientProvider implements RemoteClientProvider {
    /**
     * typeName.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Override
    public String typeName() {
        return "MQ";
    }

    /**
     * Creates a remote client using MQ protocol.
     * 
     * @param config the remote client configuration
     * @return a new MqRemoteClient instance
     * @since 0.1.7
     */
    @Override
    public RemoteClient create(RemoteClientConfig config) {
        return new MqRemoteClient(config);
    }
}

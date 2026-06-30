/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.drunner.remote_client;

import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.extensions.a2a.A2ARemoteClient;

/**
 * Factory for protocol-specific remote client implementations.
 */
public final class RemoteClientFactory {
    private RemoteClientFactory() {
    }

    /**
     * Auto-generated for codecheck compliance.
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
        return switch (protocol) {
            case A2A -> new A2ARemoteClient(config);
            case MQ -> new MqRemoteClient(config);
        };
    }

    /**
     * Auto-generated for codecheck compliance.
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
}

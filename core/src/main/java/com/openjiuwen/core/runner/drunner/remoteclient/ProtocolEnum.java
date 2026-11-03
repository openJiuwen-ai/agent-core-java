/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.drunner.remoteclient;

/**
 * Supported remote transport protocols.
 *
 * <p>Mirrors Python's {@code ProtocolEnum} in
 * {@code openjiuwen/core/runner/drunner/remote_client/remote_client_config.py}.</p>
 *
 * @deprecated Use {@link com.openjiuwen.core.runner.drunner.remote_client.ProtocolEnum}.
 */
@Deprecated(since = "0.1.14")
public enum ProtocolEnum {
    MQ,
    A2A
}

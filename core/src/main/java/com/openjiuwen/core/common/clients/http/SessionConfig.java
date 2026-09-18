/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.clients.http;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Backward-compatible HTTP package facade for session configuration.
 *
 * <p>Mirrors Python's {@code SessionConfig} in
 * {@code openjiuwen/core/common/clients/http_client.py}.</p>
 */
public class SessionConfig extends com.openjiuwen.core.common.clients.SessionConfig {

    public SessionConfig() {
        super();
    }

    public SessionConfig(Map<String, Object> values) {
        super(values);
    }

    public SessionConfig(com.openjiuwen.core.common.clients.SessionConfig config) {
        super();
        if (config == null) {
            return;
        }
        setConnectorPoolConfig(config.getConnectorPoolConfig());
        setHeaders(config.getHeaders());
        setProxy(config.getProxy());
        setTimeout(config.getTimeout());
        setConnectTimeout(config.getConnectTimeout());
        setTimeoutArgs(config.getTimeoutArgs());
        setAuth(config.getAuth());
        setRaiseForStatus(config.isRaiseForStatus());
        setTrustEnv(config.isTrustEnv());
        setExtendArgs(config.getExtendArgs());
    }

    public SessionConfig(Double timeout, Double connectTimeout) {
        super();
        setTimeout(timeout);
        setConnectTimeout(connectTimeout);
    }

    public SessionConfig(Double timeout, Map<String, String> headers) {
        super();
        setTimeout(timeout);
        setHeaders(headers == null ? null : new LinkedHashMap<>(headers));
    }
}

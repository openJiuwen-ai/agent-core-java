/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.clients.llm;

import com.openjiuwen.core.common.clients.ConnectorPoolConfig;

/**
 * Configuration class for HTTPX connector pool.
 * <p>
 * Extends the base ConnectorPoolConfig with HTTPX-specific settings
 * for connection pooling and network configuration.
 * <p>
 * Mirrors Python's {@code HttpXConnectorPoolConfig} from
 * {@code core/common/clients/llm_client.py}.
 */
public class HttpXConnectorPoolConfig extends ConnectorPoolConfig {

    private int maxKeepaliveConnections = 20;
    private String localAddress = null;
    private String proxy = null;
    private boolean needAsync = true;
    private Double timeout = null;

    public HttpXConnectorPoolConfig() {
        super();
    }

    // Getters
    public int getMaxKeepaliveConnections() { return maxKeepaliveConnections; }
    public String getLocalAddress() { return localAddress; }
    public String getProxy() { return proxy; }
    public boolean isNeedAsync() { return needAsync; }
    public Double getTimeout() { return timeout; }

    // Setters
    public void setMaxKeepaliveConnections(int maxKeepaliveConnections) {
        this.maxKeepaliveConnections = maxKeepaliveConnections;
    }
    public void setLocalAddress(String localAddress) { this.localAddress = localAddress; }
    public void setProxy(String proxy) { this.proxy = proxy; }
    public void setNeedAsync(boolean needAsync) { this.needAsync = needAsync; }
    public void setTimeout(Double timeout) { this.timeout = timeout; }

    @Override
    public String generateKey() {
        return super.generateKey() + "&maxKeepalive:" + maxKeepaliveConnections;
    }
}
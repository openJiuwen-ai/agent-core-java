/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.clients;

/**
 * Configuration for HTTPX-style connector pool.
 * <p>
 * Mirrors Python's {@code HttpXConnectorPoolConfig} class from
 * <code>openjiuwen/core/common/clients/llm_client.py</code>.
 *
 * <p>Extends ConnectorPoolConfig with HTTPX-specific settings for connection pooling.
 */
public class HttpXConnectorPoolConfig extends ConnectorPoolConfig {

    private int maxKeepaliveConnections = 20;
    private String localAddress = null;
    private String proxy = null;
    private boolean needAsync = true;

    public HttpXConnectorPoolConfig() {
        super();
    }

    /**
     * Maximum number of keep-alive connections to maintain in the pool.
     * These connections are kept open for reuse, reducing connection establishment overhead.
     *
     * @return max keepalive connections (default 20)
     */
    public int getMaxKeepaliveConnections() {
        return maxKeepaliveConnections;
    }

    public void setMaxKeepaliveConnections(int maxKeepaliveConnections) {
        if (maxKeepaliveConnections < 1) {
            throw new IllegalArgumentException("maxKeepaliveConnections must be >= 1");
        }
        this.maxKeepaliveConnections = maxKeepaliveConnections;
    }

    /**
     * Local IP address or hostname to bind to for outgoing connections.
     * Useful for multi-homed systems or when a specific network interface is required.
     *
     * @return local address or null
     */
    public String getLocalAddress() {
        return localAddress;
    }

    public void setLocalAddress(String localAddress) {
        this.localAddress = localAddress;
    }

    /**
     * Proxy server URL to route HTTP requests through.
     *
     * @return proxy URL or null
     */
    public String getProxy() {
        return proxy;
    }

    public void setProxy(String proxy) {
        this.proxy = proxy;
    }

    /**
     * Enable asynchronous mode for the connector pool.
     * When set to true, the pool will use async HTTP client methods.
     *
     * @return true if async mode enabled
     */
    public boolean isNeedAsync() {
        return needAsync;
    }

    public void setNeedAsync(boolean needAsync) {
        this.needAsync = needAsync;
    }

    @Override
    public String generateKey() {
        StringBuilder sb = new StringBuilder(super.generateKey());
        sb.append("&maxKeepalive=").append(maxKeepaliveConnections);
        if (localAddress != null) {
            sb.append("&localAddr=").append(localAddress);
        }
        if (proxy != null) {
            sb.append("&proxy=").append(proxy);
        }
        sb.append("&async=").append(needAsync);
        return sb.toString();
    }
}
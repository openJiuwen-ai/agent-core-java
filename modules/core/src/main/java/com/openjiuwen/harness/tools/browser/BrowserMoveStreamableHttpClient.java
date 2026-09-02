/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.browser;

import com.openjiuwen.core.foundation.tool.mcp.McpServerConfig;

import lombok.Getter;

/**
 * Public class BrowserMoveStreamableHttpClient used by the Java parity implementation.
 * 
 * @since 0.1.7
 */
@Getter
public class BrowserMoveStreamableHttpClient {
    private final String serverPath;
    private final String name;

    /**
     * BrowserMoveStreamableHttpClient.
     * 
     * @param config config
     * @since 0.1.7
     */
    public BrowserMoveStreamableHttpClient(McpServerConfig config) {
        this.serverPath = config.getServerPath();
        this.name = config.getServerName();
    }
}

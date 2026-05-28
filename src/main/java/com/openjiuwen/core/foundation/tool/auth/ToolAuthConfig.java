/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.tool.auth;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Tool authentication configuration.
 * <p>
 * Mirrors Python's {@code ToolAuthConfig} dataclass from
 * <code>foundation/tool/auth/auth.py</code>.
 *
 * @param authType  authentication type: ssl, header_and_query, etc.
 * @param config    authentication configuration parameters
 * @param toolType  tool type: restful_api, mcp, etc.
 * @param toolId    optional tool identifier
 */
public class ToolAuthConfig {

    private final String authType;
    private final Map<String, Object> config;
    private final String toolType;
    private final String toolId;

    public ToolAuthConfig(String authType, Map<String, Object> config, String toolType) {
        this(authType, config, toolType, null);
    }

    public ToolAuthConfig(String authType, Map<String, Object> config, String toolType, String toolId) {
        this.authType = authType;
        this.config = config != null ? new HashMap<>(config) : new HashMap<>();
        this.toolType = toolType;
        this.toolId = toolId;
    }

    public String getAuthType() {
        return authType;
    }

    public Map<String, Object> getConfig() {
        return Collections.unmodifiableMap(config);
    }

    public String getToolType() {
        return toolType;
    }

    public String getToolId() {
        return toolId;
    }
}

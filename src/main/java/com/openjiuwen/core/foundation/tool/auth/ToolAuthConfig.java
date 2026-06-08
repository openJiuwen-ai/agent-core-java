/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.tool.auth;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;
import java.util.Objects;

/**
 * Tool authentication configuration.
 *
 * <p>Mirrors Python's {@code ToolAuthConfig} in
 * {@code openjiuwen/core/foundation/tool/auth/auth.py}.
 */
public final class ToolAuthConfig {

    private final String authType;
    private final Map<String, Object> config;
    private final String toolType;
    private final String toolId;

    @JsonCreator
    public ToolAuthConfig(
            @JsonProperty("auth_type") String authType,
            @JsonProperty("config") Map<String, Object> config,
            @JsonProperty("tool_type") String toolType,
            @JsonProperty("tool_id") String toolId
    ) {
        this.authType = authType;
        this.config = config == null ? Map.of() : Map.copyOf(config);
        this.toolType = toolType;
        this.toolId = toolId;
    }

    public String getAuthType() {
        return authType;
    }

    public Map<String, Object> getConfig() {
        return config;
    }

    public String getToolType() {
        return toolType;
    }

    public String getToolId() {
        return toolId;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ToolAuthConfig that)) {
            return false;
        }
        return Objects.equals(authType, that.authType)
                && Objects.equals(config, that.config)
                && Objects.equals(toolType, that.toolType)
                && Objects.equals(toolId, that.toolId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(authType, config, toolType, toolId);
    }
}

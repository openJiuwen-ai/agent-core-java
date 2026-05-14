/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.tool.mcp;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * MCP (Model Context Protocol) server configuration.
 * <p>
 * Mirrors Python's {@code McpServerConfig} model.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class McpServerConfig {

    /** Unique server identifier. */
    @Builder.Default
    @JsonProperty("server_id")
    private String serverId = UUID.randomUUID().toString().replace("-", "");

    /** Server display name. */
    @JsonProperty("server_name")
    private String serverName;

    /** Server path or URL. */
    @JsonProperty("server_path")
    private String serverPath;

    /** Client type (e.g., "sse", "stdio"). */
    @Builder.Default
    @JsonProperty("client_type")
    private String clientType = "sse";

    /** Additional parameters. */
    @Builder.Default
    private Map<String, Object> params = new HashMap<>();

    /** Authentication headers. */
    @Builder.Default
    @JsonProperty("auth_headers")
    private Map<String, String> authHeaders = new HashMap<>();

    /** Authentication query parameters. */
    @Builder.Default
    @JsonProperty("auth_query_params")
    private Map<String, String> authQueryParams = new HashMap<>();

    /** Constant for no timeout. */
    public static final float NO_TIMEOUT = -1;

    public String getServerId() {
        return serverId;
    }

    public String getServerName() {
        return serverName;
    }

    public String getServerPath() {
        return serverPath;
    }

    public String getClientType() {
        return clientType;
    }

    public Map<String, Object> getParams() {
        return params;
    }

    public Map<String, String> getAuthHeaders() {
        return authHeaders;
    }

    public Map<String, String> getAuthQueryParams() {
        return authQueryParams;
    }
}

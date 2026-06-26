/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.tool.mcp;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * MCP server configuration.
 *
 * <p>Mirrors Python's {@code McpServerConfig} in
 * {@code openjiuwen/core/foundation/tool/mcp/base.py}.</p>
 *
 * <p>Supports Python's {@code OpenApiClient} in
 * {@code openjiuwen/core/foundation/tool/mcp/client/openapi_client.py}.</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class McpServerConfig {

    public static final float NO_TIMEOUT = -1.0F;

    private String serverId = UUID.randomUUID().toString().replace("-", "");
    private String serverName;
    private String serverPath;
    private String clientType = "sse";
    private Map<String, Object> params = new HashMap<>();
    private Map<String, String> authHeaders = new HashMap<>();
    private Map<String, String> authQueryParams = new HashMap<>();

    public McpServerConfig() {
    }

    public McpServerConfig(String serverName, String serverPath) {
        this.serverName = serverName;
        this.serverPath = serverPath;
    }

    public McpServerConfig(String serverId, String serverName, String serverPath, String clientType,
                           Map<String, Object> params, Map<String, String> authHeaders,
                           Map<String, String> authQueryParams) {
        this.serverId = serverId != null ? serverId : UUID.randomUUID().toString().replace("-", "");
        this.serverName = serverName;
        this.serverPath = serverPath;
        this.clientType = clientType != null ? clientType : "sse";
        setParams(params);
        setAuthHeaders(authHeaders);
        setAuthQueryParams(authQueryParams);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static Builder mcpBuilder() {
        return builder();
    }

    @JsonProperty("server_id")
    public String getServerId() {
        return serverId;
    }

    @JsonProperty("server_id")
    public void setServerId(String serverId) {
        this.serverId = serverId != null ? serverId : UUID.randomUUID().toString().replace("-", "");
    }

    @JsonProperty("server_name")
    public String getServerName() {
        return serverName;
    }

    @JsonProperty("server_name")
    public void setServerName(String serverName) {
        this.serverName = serverName;
    }

    @JsonProperty("server_path")
    public String getServerPath() {
        return serverPath;
    }

    @JsonProperty("server_path")
    public void setServerPath(String serverPath) {
        this.serverPath = serverPath;
    }

    @JsonProperty("client_type")
    public String getClientType() {
        return clientType;
    }

    @JsonProperty("client_type")
    public void setClientType(String clientType) {
        this.clientType = clientType != null ? clientType : "sse";
    }

    public Map<String, Object> getParams() {
        return params;
    }

    public void setParams(Map<String, Object> params) {
        this.params = params != null ? new HashMap<>(params) : new HashMap<>();
    }

    @JsonProperty("auth_headers")
    public Map<String, String> getAuthHeaders() {
        return authHeaders;
    }

    @JsonProperty("auth_headers")
    public void setAuthHeaders(Map<String, String> authHeaders) {
        this.authHeaders = authHeaders != null ? new HashMap<>(authHeaders) : new HashMap<>();
    }

    @JsonProperty("auth_query_params")
    public Map<String, String> getAuthQueryParams() {
        return authQueryParams;
    }

    @JsonProperty("auth_query_params")
    public void setAuthQueryParams(Map<String, String> authQueryParams) {
        this.authQueryParams = authQueryParams != null ? new HashMap<>(authQueryParams) : new HashMap<>();
    }

    /**
     * Builder for translated callers that need Python-style optional fields.
     *
     * <p>Mirrors Python's {@code McpServerConfig} construction used by
     * {@code OpenApiClient} in
     * {@code openjiuwen/core/foundation/tool/mcp/client/openapi_client.py}.</p>
     */
    public static final class Builder {
        private final McpServerConfig value = new McpServerConfig();

        private Builder() {
        }

        public Builder serverId(String serverId) {
            value.setServerId(serverId);
            return this;
        }

        public Builder serverName(String serverName) {
            value.setServerName(serverName);
            return this;
        }

        public Builder serverPath(String serverPath) {
            value.setServerPath(serverPath);
            return this;
        }

        public Builder clientType(String clientType) {
            value.setClientType(clientType);
            return this;
        }

        public Builder params(Map<String, Object> params) {
            value.setParams(params);
            return this;
        }

        public Builder authHeaders(Map<String, String> authHeaders) {
            value.setAuthHeaders(authHeaders);
            return this;
        }

        public Builder authQueryParams(Map<String, String> authQueryParams) {
            value.setAuthQueryParams(authQueryParams);
            return this;
        }

        public McpServerConfig build() {
            return new McpServerConfig(value.serverId, value.serverName, value.serverPath, value.clientType,
                    value.params, value.authHeaders, value.authQueryParams);
        }
    }
}

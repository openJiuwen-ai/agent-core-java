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
public final class McpServerConfig {

    public static final float NO_TIMEOUT = -1.0F;

    /** Server identifier. Defaults to serverName when blank; otherwise a random UUID. */
    private String serverId;
    private String serverName;
    private String serverPath;
    private String clientType = "sse";
    private Map<String, Object> params = new HashMap<>();
    private Map<String, String> authHeaders = new HashMap<>();
    private Map<String, String> authQueryParams = new HashMap<>();
    private Double connectTimeoutSeconds;
    private Double callTimeoutSeconds;

    public McpServerConfig() {
    }

    public McpServerConfig(String serverName, String serverPath) {
        this.serverName = serverName;
        this.serverPath = serverPath;
        normalizeServerId();
    }

    public McpServerConfig(String serverId, String serverName, String serverPath, String clientType,
                           Map<String, Object> params, Map<String, String> authHeaders,
                           Map<String, String> authQueryParams) {
        this.serverId = serverId;
        this.serverName = serverName;
        this.serverPath = serverPath;
        this.clientType = clientType != null ? clientType : "sse";
        setParams(params);
        setAuthHeaders(authHeaders);
        setAuthQueryParams(authQueryParams);
        normalizeServerId();
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
        this.serverId = serverId;
    }

    /**
     * Fills a missing {@link #serverId}: prefer {@link #serverName}, otherwise a random UUID without hyphens.
     * Whitespace-only ids are left unchanged so {@code ResourceMgr} validation can reject them.
     */
    public void normalizeServerId() {
        if (serverId != null && !serverId.isBlank()) {
            return;
        }
        // Keep whitespace-only values for validation (do not silently rewrite to serverName).
        if (serverId != null && serverId.isBlank()) {
            return;
        }
        if (serverName != null && !serverName.isBlank()) {
            serverId = serverName;
        } else {
            serverId = UUID.randomUUID().toString().replace("-", "");
        }
    }

    @JsonProperty("server_name")
    public String getServerName() {
        return serverName;
    }

    @JsonProperty("server_name")
    public void setServerName(String serverName) {
        this.serverName = serverName;
        // Only auto-fill when serverId is absent (null/""); leave whitespace for validation.
        if (this.serverId == null || this.serverId.isEmpty()) {
            normalizeServerId();
        }
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

    @JsonProperty("connect_timeout_seconds")
    public Double getConnectTimeoutSeconds() {
        return connectTimeoutSeconds;
    }

    @JsonProperty("connect_timeout_seconds")
    public void setConnectTimeoutSeconds(Double connectTimeoutSeconds) {
        this.connectTimeoutSeconds = connectTimeoutSeconds;
    }

    @JsonProperty("call_timeout_seconds")
    public Double getCallTimeoutSeconds() {
        return callTimeoutSeconds;
    }

    @JsonProperty("call_timeout_seconds")
    public void setCallTimeoutSeconds(Double callTimeoutSeconds) {
        this.callTimeoutSeconds = callTimeoutSeconds;
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

        public Builder connectTimeoutSeconds(Double connectTimeoutSeconds) {
            value.setConnectTimeoutSeconds(connectTimeoutSeconds);
            return this;
        }

        public Builder callTimeoutSeconds(Double callTimeoutSeconds) {
            value.setCallTimeoutSeconds(callTimeoutSeconds);
            return this;
        }

        public McpServerConfig build() {
            McpServerConfig built = new McpServerConfig(value.serverId, value.serverName, value.serverPath,
                    value.clientType, value.params, value.authHeaders, value.authQueryParams);
            built.setConnectTimeoutSeconds(value.connectTimeoutSeconds);
            built.setCallTimeoutSeconds(value.callTimeoutSeconds);
            built.normalizeServerId();
            return built;
        }
    }
}

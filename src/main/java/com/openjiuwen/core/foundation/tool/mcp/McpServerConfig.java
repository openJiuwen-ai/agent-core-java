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
 *
 * @since 0.1.7
 */
@Data
@Builder(builderMethodName = "lombokBuilder")
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class McpServerConfig {
    /** Server identifier. Defaults to serverName when blank; otherwise a random UUID. */
    @JsonProperty("server_id")
    private String serverId;

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

    /**
     * Additional parameters.
     *
     * @since 0.1.7
     */
    @Builder.Default
    private Map<String, Object> params = new HashMap<>();

    /**
     * Authentication headers.
     *
     * @since 0.1.7
     */
    @Builder.Default
    @JsonProperty("auth_headers")
    private Map<String, String> authHeaders = new HashMap<>();

    /**
     * Authentication query parameters.
     *
     * @since 0.1.7
     */
    @Builder.Default
    @JsonProperty("auth_query_params")
    private Map<String, String> authQueryParams = new HashMap<>();

    /**
     * Connect timeout in seconds for establishing the MCP transport (optional).
     *
     * @since 0.1.14
     */
    @JsonProperty("connect_timeout_seconds")
    private Double connectTimeoutSeconds;

    /**
     * Per-call timeout in seconds for MCP RPC requests (optional).
     *
     * @since 0.1.14
     */
    @JsonProperty("call_timeout_seconds")
    private Double callTimeoutSeconds;

    /**
     * Sentinel timeout value meaning "no timeout" for MCP client calls.
     *
     * @since 0.1.7
     */
    public static final float NO_TIMEOUT = -1;

    /**
     * Creates a builder that normalizes {@link #serverId} on {@code build()}.
     *
     * @return a builder that applies {@link #normalizeServerId()} before returning the config
     * @since 0.1.14
     */
    public static McpServerConfigBuilder builder() {
        return new McpServerConfigBuilder() {
            @Override
            public McpServerConfig build() {
                McpServerConfig cfg = super.build();
                cfg.normalizeServerId();
                return cfg;
            }
        };
    }

    /**
     * Fills a blank {@link #serverId}: prefer {@link #serverName}, otherwise a random UUID without hyphens.
     *
     * @since 0.1.14
     */
    public void normalizeServerId() {
        if (serverId != null && !serverId.isBlank()) {
            return;
        }
        if (serverName != null && !serverName.isBlank()) {
            serverId = serverName;
        } else {
            serverId = UUID.randomUUID().toString().replace("-", "");
        }
    }
}

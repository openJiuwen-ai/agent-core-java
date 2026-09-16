/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.tool.mcp;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.StatusCode;

import java.io.Serial;
import java.util.Map;

/**
 * Recognizable error raised when a server registration targets an id
 * already registered with different connection semantics:
 * the conflicting late registration fails visibly — the loser's
 * connection is recycled and the winner's entry stays untouched — so
 * the facade never silently merges two different servers.
 * <p>
 * Callers must react to this type, never to message text.
 *
 * @since 0.1.16
 */
public class McpServerConfigConflictError extends BaseError {
    @Serial
    private static final long serialVersionUID = 1L;

    private final String serverId;

    /**
     * Creates the error for a conflicting registration attempt.
     *
     * @param serverId the MCP server identifier that is already registered
     * @param existingConfig the config held by the winning registration
     * @param attemptedConfig the conflicting config that lost
     * @since 0.1.16
     */
    public McpServerConfigConflictError(String serverId, McpServerConfig existingConfig,
            McpServerConfig attemptedConfig) {
        super(StatusCode.RESOURCE_MCP_SERVER_ADD_ERROR, null, null, null, Map.of(
                "server_config", attemptedConfig.toMaskedDescription(),
                "reason", "server_id '" + serverId + "' is already registered with a different config: "
                        + existingConfig.toMaskedDescription()));
        this.serverId = serverId;
    }

    /**
     * Returns the MCP server identifier that is already registered.
     *
     * @return the server id
     * @since 0.1.16
     */
    public String getServerId() {
        return serverId;
    }
}

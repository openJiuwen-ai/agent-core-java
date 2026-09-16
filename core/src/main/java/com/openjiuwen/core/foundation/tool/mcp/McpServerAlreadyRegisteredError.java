/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.tool.mcp;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.StatusCode;

import java.io.Serial;
import java.util.Map;

/**
 * Recognizable error raised when a server registration loses the
 * placeholder race to a registration with equivalent connection
 * semantics: the loser has already recycled its own
 * connection, and the DeepAgent facade catches exactly this type to
 * re-query and re-tag the winner's entry, preserving the
 * caller-visible "single attempt succeeds" contract.
 * <p>
 * Callers must react to this type (or {@link #getServerId()}), never
 * to message text.
 *
 * @since 0.1.16
 */
public class McpServerAlreadyRegisteredError extends BaseError {
    @Serial
    private static final long serialVersionUID = 1L;

    private final String serverId;

    /**
     * Creates the error for a server whose equivalent registration won.
     *
     * @param serverId the MCP server identifier that is already registered
     * @param existingConfig the config held by the winning registration
     * @since 0.1.16
     */
    public McpServerAlreadyRegisteredError(String serverId, McpServerConfig existingConfig) {
        super(StatusCode.RESOURCE_MCP_SERVER_ADD_ERROR, null, null, null, Map.of(
                "server_config", existingConfig.toMaskedDescription(),
                "reason", "server_id '" + serverId + "' is already registered with an equivalent config"));
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

/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.tool.mcp;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Synchronous MCP client contract used by concrete MCP transports.
 *
 * <p>Mirrors Python's {@code McpClient} in
 * {@code openjiuwen/core/foundation/tool/mcp/client/mcp_client.py}; this top-level Java contract keeps compatibility
 * with already translated MCP clients in this package.</p>
 *
 * <p>Supports Python's {@code OpenApiClient} in
 * {@code openjiuwen/core/foundation/tool/mcp/client/openapi_client.py}.</p>
 */
public interface McpClient {

    boolean connect(int retryTimes, float timeout) throws Exception;

    default boolean connect() throws Exception {
        return connect(1, McpServerConfig.NO_TIMEOUT);
    }

    boolean disconnect(float timeout) throws Exception;

    default boolean disconnect() throws Exception {
        return disconnect(McpServerConfig.NO_TIMEOUT);
    }

    /**
     * Tear down and re-establish the transport after a transient failure.
     * <p>
     * Default implementation is {@code disconnect} followed by {@code connect}.
     * HTTP transports serialize concurrent reconnects so a network blip does not
     * leave tools permanently unusable.
     * </p>
     *
     * @param timeout timeout in seconds for disconnect and connect
     * @return {@code true} when reconnect succeeds
     * @throws Exception when reconnect fails hard
     * @since 0.1.18
     */
    default boolean reconnect(float timeout) throws Exception {
        disconnect(timeout);
        return connect(1, timeout);
    }

    /**
     * Reconnect using the {@link McpServerConfig#NO_TIMEOUT} sentinel.
     *
     * @return {@code true} when reconnect succeeds
     * @throws Exception when reconnect fails hard
     * @since 0.1.18
     */
    default boolean reconnect() throws Exception {
        return reconnect(McpServerConfig.NO_TIMEOUT);
    }

    List<Object> listTools(float timeout) throws Exception;

    default List<Object> listTools() throws Exception {
        return listTools(McpServerConfig.NO_TIMEOUT);
    }

    Object callTool(String toolName, Map<String, Object> arguments, float timeout) throws Exception;

    default Object callTool(String toolName, Map<String, Object> arguments) throws Exception {
        return callTool(toolName, arguments, McpServerConfig.NO_TIMEOUT);
    }

    Optional<Object> getToolInfo(String toolName, float timeout) throws Exception;

    default Optional<Object> getToolInfo(String toolName) throws Exception {
        return getToolInfo(toolName, McpServerConfig.NO_TIMEOUT);
    }

    default List<Object> listResources(float timeout) throws Exception {
        return null;
    }

    default Object readResource(String uri, float timeout) throws Exception {
        return null;
    }

    String getServerPath();
}

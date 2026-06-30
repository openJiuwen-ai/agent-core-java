/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.tool.mcp;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Abstract MCP client interface for communicating with MCP servers.
 * <p>
 * Mirrors Python's {@code McpClient} ABC. Implementations (SSE, Stdio, etc.)
 * handle the specific transport protocols.
 */
public interface McpClient {

    /**
     * Connect to the MCP server.
     *
     * @param retryTimes number of connection retry attempts
     * @param timeout    connection timeout in seconds; use {@link McpServerConfig#NO_TIMEOUT} for no timeout
     * @return true if connection succeeded
     * @throws Exception if connection fails after all retries
     */
    boolean connect(int retryTimes, float timeout) throws Exception;

    /**
     * Connect with defaults (1 retry, no timeout).
     */
    default boolean connect() throws Exception {
        return connect(1, McpServerConfig.NO_TIMEOUT);
    }

    /**
     * Disconnect from the MCP server.
     *
     * @param timeout disconnect timeout in seconds
     * @return true if disconnection succeeded
     * @throws Exception if disconnection fails
     */
    boolean disconnect(float timeout) throws Exception;

    /**
     * Disconnect with no timeout.
     */
    default boolean disconnect() throws Exception {
        return disconnect(McpServerConfig.NO_TIMEOUT);
    }

    /**
     * List all available tools on the MCP server.
     *
     * @param timeout operation timeout in seconds
     * @return list of tool metadata
     * @throws Exception if the operation fails
     */
    List<Object> listTools(float timeout) throws Exception;

    /**
     * List tools with no timeout.
     */
    default List<Object> listTools() throws Exception {
        return listTools(McpServerConfig.NO_TIMEOUT);
    }

    /**
     * List all available resources on the MCP server.
     *
     * @param timeout operation timeout in seconds
     * @return list of resource metadata
     * @throws Exception if the operation fails
     */
    default List<Object> listResources(float timeout) throws Exception {
        return List.of();
    }

    /**
     * List resources with no timeout.
     */
    default List<Object> listResources() throws Exception {
        return listResources(McpServerConfig.NO_TIMEOUT);
    }

    /**
     * Read one MCP resource by URI.
     *
     * @param uri resource URI
     * @param timeout operation timeout in seconds
     * @return list of resource content blocks
     * @throws Exception if the operation fails
     */
    default List<Object> readResource(String uri, float timeout) throws Exception {
        throw new UnsupportedOperationException("MCP resource read is not supported by this client");
    }

    /**
     * Read one MCP resource with no timeout.
     */
    default List<Object> readResource(String uri) throws Exception {
        return readResource(uri, McpServerConfig.NO_TIMEOUT);
    }

    /**
     * Call a tool on the MCP server.
     *
     * @param toolName  name of the tool to call
     * @param arguments arguments to pass to the tool
     * @param timeout   operation timeout in seconds
     * @return the tool execution result
     * @throws Exception if the call fails
     */
    Object callTool(String toolName, Map<String, Object> arguments, float timeout) throws Exception;

    /**
     * Call a tool with no timeout.
     */
    default Object callTool(String toolName, Map<String, Object> arguments) throws Exception {
        return callTool(toolName, arguments, McpServerConfig.NO_TIMEOUT);
    }

    /**
     * Get information about a specific tool.
     *
     * @param toolName name of the tool
     * @param timeout  operation timeout in seconds
     * @return tool info, or empty if not found
     * @throws Exception if the operation fails
     */
    Optional<Object> getToolInfo(String toolName, float timeout) throws Exception;

    /**
     * Get tool info with no timeout.
     */
    default Optional<Object> getToolInfo(String toolName) throws Exception {
        return getToolInfo(toolName, McpServerConfig.NO_TIMEOUT);
    }

    /**
     * Get the server path this client is connected to.
     */
    String getServerPath();
}

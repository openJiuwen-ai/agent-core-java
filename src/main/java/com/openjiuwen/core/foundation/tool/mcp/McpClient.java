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
 * 
 * @since 0.1.7
 */
public interface McpClient {
    /**
     * connect.
     * 
     * @param retryTimes retryTimes
     * @param timeout timeout
     * @return the result
     * @throws Exception Exception
     * @since 0.1.7
     */
    boolean connect(int retryTimes, float timeout) throws Exception;

    /**
     * Connect with defaults (1 retry, no timeout).
     * 
     * @return the result
     * @throws Exception Exception
     * @since 0.1.7
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
     * @since 0.1.7
     */
    boolean disconnect(float timeout) throws Exception;

    /**
     * Disconnect with no timeout.
     * 
     * @return the result
     * @throws Exception Exception
     * @since 0.1.7
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
     * @since 0.1.7
     */
    List<Object> listTools(float timeout) throws Exception;

    /**
     * List tools with no timeout.
     * 
     * @return the result
     * @throws Exception Exception
     * @since 0.1.7
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
     * @since 0.1.7
     */
    default List<Object> listResources(float timeout) throws Exception {
        return List.of();
    }

    /**
     * List resources with no timeout.
     * 
     * @return the result
     * @throws Exception Exception
     * @since 0.1.7
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
     * @since 0.1.7
     */
    default List<Object> readResource(String uri, float timeout) throws Exception {
        throw new UnsupportedOperationException("MCP resource read is not supported by this client");
    }

    /**
     * Read one MCP resource with no timeout.
     * 
     * @param uri uri
     * @return the result
     * @throws Exception Exception
     * @since 0.1.7
     */
    default List<Object> readResource(String uri) throws Exception {
        return readResource(uri, McpServerConfig.NO_TIMEOUT);
    }

    /**
     * Call a tool on the MCP server.
     * 
     * @param toolName name of the tool to call
     * @param arguments arguments to pass to the tool
     * @param timeout operation timeout in seconds
     * @return the tool execution result
     * @throws Exception if the call fails
     * @since 0.1.7
     */
    Object callTool(String toolName, Map<String, Object> arguments, float timeout) throws Exception;

    /**
     * Call a tool with no timeout.
     * 
     * @param toolName toolName
     * @param arguments arguments
     * @return the result
     * @throws Exception Exception
     * @since 0.1.7
     */
    default Object callTool(String toolName, Map<String, Object> arguments) throws Exception {
        return callTool(toolName, arguments, McpServerConfig.NO_TIMEOUT);
    }

    /**
     * Get information about a specific tool.
     * 
     * @param toolName name of the tool
     * @param timeout operation timeout in seconds
     * @return tool info, or empty if not found
     * @throws Exception if the operation fails
     * @since 0.1.7
     */
    Optional<Object> getToolInfo(String toolName, float timeout) throws Exception;

    /**
     * Get tool info with no timeout.
     * 
     * @param toolName toolName
     * @return the result
     * @throws Exception Exception
     * @since 0.1.7
     */
    default Optional<Object> getToolInfo(String toolName) throws Exception {
        return getToolInfo(toolName, McpServerConfig.NO_TIMEOUT);
    }

    /**
     * Get the server path this client is connected to.
     * 
     * @return the result
     * @since 0.1.7
     */
    String getServerPath();
}

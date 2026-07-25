/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.resourcemanager;

import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.foundation.tool.mcp.McpClient;
import com.openjiuwen.core.foundation.tool.mcp.McpClientFactory;
import com.openjiuwen.core.foundation.tool.mcp.McpServerConfig;
import com.openjiuwen.core.foundation.tool.mcp.McpTool;
import com.openjiuwen.core.foundation.tool.mcp.McpToolCard;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manager for Tool instances, MCP servers, and SysOperation-related tools.
 * <p>
 * Provides registration, lookup, and lifecycle management for tools and MCP servers.
 * MCP client creation is delegated to {@link McpClientFactory} for SPI-based transport selection.
 * <p>
 * Mirrors Python's {@code ToolMgr} in {@code resources_manager/tool_manager.py}.
 * 
 * @since 0.1.7
 */
public class ToolMgr {
    private static final Logger logger = LoggerFactory.getLogger(ToolMgr.class);

    /**
     * ConcurrentHashMap<>.
     * 
     * @since 0.1.7
     */
    private final ConcurrentHashMap<String, Tool> tools = new ConcurrentHashMap<>();

    /**
     * HashMap<>.
     * 
     * @since 0.1.7
     */
    private final Map<String, List<String>> mcpServerNameToIds = new HashMap<>();

    /**
     * HashMap<>.
     * 
     * @since 0.1.7
     */
    private final Map<String, McpServerResource> mcpServerResources = new HashMap<>();

    /**
     * HashMap<>.
     * 
     * @since 0.1.7
     */
    private final Map<String, SysOpToolResource> sysOpResources = new HashMap<>();

    /**
     * Registers a tool with the given identifier.
     * 
     * @param toolId the unique identifier for the tool
     * @param tool the tool instance to register
     * @since 0.1.7
     */
    public void addTool(String toolId, Tool tool) {
        if (tools.containsKey(toolId)) {
            throw new IllegalArgumentException("already exist tool " + toolId);
        }
        tools.put(toolId, tool);
    }

    /**
     * Retrieves a tool by its identifier.
     * 
     * @param toolId the unique identifier of the tool
     * @return the tool instance, or {@code null} if not found
     * @since 0.1.7
     */
    public Tool getTool(String toolId) {
        return tools.get(toolId);
    }

    /**
     * Retrieves an MCP tool by name and server identifier.
     * 
     * @param toolName the name of the MCP tool
     * @param serverId the identifier of the MCP server
     * @return the tool instance, or {@code null} if the server or tool is not found
     * @since 0.1.7
     */
    public Tool getMcpTool(String toolName, String serverId) {
        McpServerResource resource = mcpServerResources.get(serverId);
        if (resource != null) {
            String toolId = generateMcpToolId(serverId, resource.config().getServerName(), toolName);
            return getTool(toolId);
        }
        return null;
    }

    /**
     * Retrieves all tools belonging to the specified MCP server.
     * 
     * @param serverId the identifier of the MCP server
     * @return a list of tools for the server, or {@code null} if the server is not found
     * @since 0.1.7
     */
    public List<Tool> getMcpTools(String serverId) {
        McpServerResource resource = mcpServerResources.get(serverId);
        if (resource != null) {
            List<Tool> results = new ArrayList<>();
            for (String toolId : resource.toolIds()) {
                Tool tool = getTool(toolId);
                if (tool != null) {
                    results.add(tool);
                }
            }
            return results;
        }
        return java.util.Collections.emptyList();
    }

    /**
     * Lists resources exposed by the specified MCP server.
     * 
     * @param serverId the identifier of the MCP server
     * @return a list of resources from the server
     * @throws Exception if listing resources from the server fails
     * @since 0.1.7
     */
    public List<Object> listMcpResources(String serverId) throws Exception {
        McpServerResource resource = mcpServerResources.get(serverId);
        if (resource == null) {
            throw new IllegalArgumentException("MCP server not found: " + serverId);
        }
        return resource.client().listResources();
    }

    /**
     * Reads a resource from the specified MCP server by URI.
     * 
     * @param serverId the identifier of the MCP server
     * @param uri the URI of the resource to read
     * @return a list of resource contents
     * @throws Exception if reading the resource from the server fails
     * @since 0.1.7
     */
    public List<Object> readMcpResource(String serverId, String uri) throws Exception {
        McpServerResource resource = mcpServerResources.get(serverId);
        if (resource == null) {
            throw new IllegalArgumentException("MCP server not found: " + serverId);
        }
        return resource.client().readResource(uri);
    }

    /**
     * Retrieves the MCP tool identifier for a given server and tool name.
     * If {@code toolName} is {@code null}, returns all tool identifiers for the server.
     * 
     * @param serverId the identifier of the MCP server
     * @param toolName the name of the tool, or {@code null} to retrieve all tool identifiers
     * @return the generated tool identifier, a list of all tool identifiers,
     *         or {@code null} if the server is not found
     * @since 0.1.7
     */
    public Object getMcpToolId(String serverId, String toolName) {
        McpServerResource resource = mcpServerResources.get(serverId);
        if (resource != null) {
            if (toolName == null) {
                return resource.toolIds();
            }
            return generateMcpToolId(serverId, resource.config().getServerName(), toolName);
        }
        return null;
    }

    /**
     * Removes and returns the tool associated with the given identifier.
     * 
     * @param toolId the unique identifier of the tool to remove
     * @return the removed tool instance, or {@code null} if no tool was found
     * @since 0.1.7
     */
    public Tool removeTool(String toolId) {
        return tools.remove(toolId);
    }

    /**
     * Generates a composite identifier for an MCP tool based on server and tool information.
     * 
     * @param serverId the identifier of the MCP server
     * @param serverName the name of the MCP server
     * @param toolName the name of the tool
     * @return the generated composite tool identifier
     * @since 0.1.7
     */
    public static String generateMcpToolId(String serverId, String serverName, String toolName) {
        return serverId + "." + serverName + "." + toolName;
    }

    /**
     * Adds an MCP tool server and connects to it, registering all discovered tools.
     * 
     * @param serverConfig the configuration for the MCP server
     * @param expiryTime the time in seconds after which the tool list should be refreshed,
     * @return a list of tool cards discovered from the server
     * @throws Exception if the server already exists, connection fails, or tool discovery fails
     *             or {@code null} for no expiry
     * @since 0.1.7
     */
    public List<McpToolCard> addToolServer(McpServerConfig serverConfig, Double expiryTime) throws Exception {
        if (mcpServerResources.containsKey(serverConfig.getServerId())) {
            throw ErrorHelper.buildError(StatusCode.RESOURCE_MCP_SERVER_ADD_ERROR, "server_config",
                    String.valueOf(serverConfig), "reason", "server_id is already exist");
        }
        McpClient client = createClient(serverConfig);
        try {
            float connectTimeoutSec =
                serverConfig.getConnectTimeoutSeconds() != null && serverConfig.getConnectTimeoutSeconds() > 0
                    ? serverConfig.getConnectTimeoutSeconds().floatValue() : 30f;
            boolean isConnected = client.connect(1, connectTimeoutSec);
            if (!isConnected) {
                throw ErrorHelper.buildError(StatusCode.RESOURCE_MCP_SERVER_CONNECTION_ERROR, "server_config",
                        String.valueOf(serverConfig), "reason", "");
            }
            List<McpToolCard> results = innerRefreshMcpTools(client, serverConfig, expiryTime);
            mcpServerNameToIds.computeIfAbsent(serverConfig.getServerName(), k -> new ArrayList<>())
                    .add(serverConfig.getServerId());
            return results;
        } catch (Exception e) {
            throw ErrorHelper.buildError(StatusCode.RESOURCE_MCP_SERVER_ADD_ERROR, "server_config",
                    String.valueOf(serverConfig), "reason", e.getMessage());
        }
    }

    /**
     * createClient.
     * 
     * @param config config
     * @return the result
     * @since 0.1.7
     */
    private McpClient createClient(McpServerConfig config) {
        return McpClientFactory.create(config);
    }

    /**
     * Retrieves all server identifiers associated with the given server name.
     * 
     * @param serverName the name of the MCP server
     * @return a list of server identifiers, or an empty list if none are found
     * @since 0.1.7
     */
    public List<String> getMcpServerIds(String serverName) {
        return mcpServerNameToIds.getOrDefault(serverName, Collections.emptyList());
    }

    /**
     * Returns the config for a registered MCP server, or {@code null} if unknown / blank id.
     *
     * @param serverId MCP server identifier
     * @return server config, or {@code null}
     * @since 0.1.14
     */
    public McpServerConfig getMcpServerConfig(String serverId) {
        if (serverId == null || serverId.isBlank()) {
            return null;
        }
        McpServerResource resource = mcpServerResources.get(serverId);
        return resource == null ? null : resource.config();
    }

    /**
     * Lists all registered MCP server identifiers.
     *
     * @return a new list of server ids (may be empty)
     * @since 0.1.14
     */
    public List<String> listMcpServerIds() {
        return new ArrayList<>(mcpServerResources.keySet());
    }

    /**
     * Removes an MCP tool server and disconnects its client, cleaning up all associated tools.
     * 
     * @param serverId the identifier of the MCP server to remove
     * @param ignoreNotExist whether to silently ignore a non-existent server
     * @return a list of tool identifiers that were removed
     * @throws Exception if the server does not exist and {@code ignoreNotExist} is {@code false}
     * @since 0.1.7
     */
    public List<String> removeToolServer(String serverId, boolean ignoreNotExist) throws Exception {
        McpServerResource resource = mcpServerResources.remove(serverId);
        if (resource == null) {
            if (!ignoreNotExist) {
                throw ErrorHelper.buildError(StatusCode.RESOURCE_MCP_SERVER_REMOVE_ERROR, "server_id", serverId,
                        "reason", "server is not exist");
            }
            return Collections.emptyList();
        }
        try {
            resource.client().disconnect();
        } catch (Exception e) {
            logger.warn("remove tool server disconnect {}, server_id={}", e.getMessage(), serverId);
        } finally {
            innerRemoveMcpTools(resource.toolIds());
            List<String> ids = mcpServerNameToIds.get(resource.config().getServerName());
            if (ids != null) {
                ids.remove(serverId);
                if (ids.isEmpty()) {
                    mcpServerNameToIds.remove(resource.config().getServerName());
                }
            }
        }
        return resource.toolIds();
    }

    /**
     * Removes an MCP tool server, silently ignoring if it does not exist.
     * 
     * @param serverId the identifier of the MCP server to remove
     * @return a list of tool identifiers that were removed
     * @throws Exception if disconnecting from the server fails
     * @since 0.1.7
     */
    public List<String> removeToolServer(String serverId) throws Exception {
        return removeToolServer(serverId, true);
    }

    /**
     * Associates a list of tool identifiers with a system operation.
     * 
     * @param sysOpId the identifier of the system operation
     * @param toolIds the list of tool identifiers to associate
     * @since 0.1.7
     */
    public void addSysOperationTools(String sysOpId, List<String> toolIds) {
        if (toolIds == null || toolIds.isEmpty()) {
            return;
        }
        sysOpResources.put(sysOpId,
                new SysOpToolResource(sysOpId, new ArrayList<>(toolIds), System.currentTimeMillis()));
    }

    /**
     * Removes the system operation and returns its associated tool identifiers.
     * 
     * @param sysOpId the identifier of the system operation to remove
     * @return a list of tool identifiers that were associated, or an empty list if not found
     * @since 0.1.7
     */
    public List<String> removeSysOperationTools(String sysOpId) {
        SysOpToolResource resource = sysOpResources.remove(sysOpId);
        return resource != null ? resource.toolIds() : Collections.emptyList();
    }

    /**
     * Retrieves the tool identifiers associated with the given system operation.
     * 
     * @param sysOpId the identifier of the system operation
     * @return a list of tool identifiers, or an empty list if the operation is not found
     * @since 0.1.7
     */
    public List<String> getSysOperationToolIds(String sysOpId) {
        SysOpToolResource resource = sysOpResources.get(sysOpId);
        return resource != null ? resource.toolIds() : Collections.emptyList();
    }

    /**
     * Refreshes the tool list for the specified MCP server if expired or forced.
     * 
     * @param serverId the identifier of the MCP server to refresh
     * @param skipNotExist whether to silently skip if the server does not exist
     * @param force whether to force a refresh regardless of expiry
     * @return a list of refreshed tool cards, or an empty list if no refresh was needed
     * @throws Exception if the server does not exist and {@code skipNotExist} is {@code false}, or if refresh fails
     * @since 0.1.7
     */
    public List<McpToolCard> refreshToolServer(String serverId, boolean skipNotExist, boolean force) throws Exception {
        McpServerResource mcpResource = mcpServerResources.get(serverId);
        if (mcpResource == null) {
            if (!skipNotExist) {
                throw ErrorHelper.buildError(StatusCode.RESOURCE_MCP_SERVER_REFRESH_ERROR, "server_id", serverId,
                        "reason", "server is not exist");
            }
            return Collections.emptyList();
        }
        boolean needRefresh = force;
        if (!force && mcpResource.expiryTime() != null) {
            if (System.currentTimeMillis() - mcpResource.lastUpdateTime() >= mcpResource.expiryTime()) {
                needRefresh = true;
            }
        }
        if (needRefresh) {
            return innerRefreshMcpTools(mcpResource.client(), mcpResource.config(), mcpResource.expiryTime());
        }
        return Collections.emptyList();
    }

    /**
     * Releases all resources by disconnecting MCP servers and clearing all managed collections.
     * 
     * @since 0.1.7
     */
    public void release() {
        for (McpServerResource resource : mcpServerResources.values()) {
            try {
                resource.client().disconnect();
            } catch (Exception e) {
                logger.warn("Failed to disconnect MCP server: {}", e.getMessage());
            }
        }
        mcpServerResources.clear();
        mcpServerNameToIds.clear();
        tools.clear();
        sysOpResources.clear();
    }

    /**
     * innerRefreshMcpTools.
     * 
     * @param client client
     * @param serverConfig serverConfig
     * @param expiryTime expiryTime
     * @return the result
     * @throws Exception Exception
     * @since 0.1.7
     */
    private List<McpToolCard> innerRefreshMcpTools(McpClient client, McpServerConfig serverConfig, Double expiryTime)
            throws Exception {
        List<Object> rawCards = client.listTools();
        List<McpToolCard> mcpCards = new ArrayList<>();
        if (rawCards != null) {
            for (Object raw : rawCards) {
                if (raw instanceof McpToolCard card) {
                    mcpCards.add(card);
                }
            }
        }
        List<String> mcpIds = new ArrayList<>();
        for (McpToolCard card : mcpCards) {
            String toolId = generateMcpToolId(serverConfig.getServerId(), serverConfig.getServerName(), card.getName());
            card.setId(toolId);
            addTool(toolId, new McpTool(client, card));
            mcpIds.add(toolId);
        }
        mcpServerResources.put(serverConfig.getServerId(), new McpServerResource(serverConfig, client,
                new ArrayList<>(mcpIds), System.currentTimeMillis(), expiryTime));
        return mcpCards;
    }

    /**
     * innerRemoveMcpTools.
     * 
     * @param toolIds toolIds
     * @since 0.1.7
     */
    private void innerRemoveMcpTools(List<String> toolIds) {
        if (toolIds == null) {
            return;
        }
        for (String toolId : toolIds) {
            tools.remove(toolId);
        }
    }

    // ========== Inner record types ==========

    /**
     * Public record McpServerResource used by the Java parity implementation.
     * 
     * @since 0.1.7
     */
    public record McpServerResource(McpServerConfig config, McpClient client, List<String> toolIds, long lastUpdateTime,
            Double expiryTime) {
    }

    /**
     * Public record SysOpToolResource used by the Java parity implementation.
     * 
     * @since 0.1.7
     */
    public record SysOpToolResource(String sysOpId, List<String> toolIds, long lastUpdateTime) {
    }
}

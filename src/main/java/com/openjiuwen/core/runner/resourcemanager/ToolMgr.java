/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.runner.resourcemanager;

import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.foundation.tool.mcp.McpClient;
import com.openjiuwen.core.foundation.tool.mcp.McpServerConfig;
import com.openjiuwen.core.foundation.tool.mcp.McpTool;
import com.openjiuwen.core.foundation.tool.mcp.McpToolCard;
import com.openjiuwen.core.foundation.tool.mcp.client.OpenApiClient;
import com.openjiuwen.core.foundation.tool.mcp.client.PlaywrightClient;
import com.openjiuwen.core.foundation.tool.mcp.sdk.OfficialMcpClientFactory;

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
 * Mirrors Python's {@code ToolMgr} in {@code resources_manager/tool_manager.py}.
 */
public class ToolMgr {

    private static final Logger logger = LoggerFactory.getLogger(ToolMgr.class);

    private final ConcurrentHashMap<String, Tool> tools = new ConcurrentHashMap<>();
    private final Map<String, List<String>> mcpServerNameToIds = new HashMap<>();
    private final Map<String, McpServerResource> mcpServerResources = new HashMap<>();
    private final Map<String, SysOpToolResource> sysOpResources = new HashMap<>();

    public void addTool(String toolId, Tool tool) {
        if (tools.containsKey(toolId)) {
            throw new IllegalArgumentException("already exist tool " + toolId);
        }
        tools.put(toolId, tool);
    }

    public Tool getTool(String toolId) {
        return tools.get(toolId);
    }

    public Tool getMcpTool(String toolName, String serverId) {
        McpServerResource resource = mcpServerResources.get(serverId);
        if (resource != null) {
            String toolId = generateMcpToolId(serverId, resource.config().getServerName(), toolName);
            return getTool(toolId);
        }
        return null;
    }

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
        return null;
    }

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

    public Tool removeTool(String toolId) {
        return tools.remove(toolId);
    }

    public static String generateMcpToolId(String serverId, String serverName, String toolName) {
        return serverId + "." + serverName + "." + toolName;
    }

    public List<McpToolCard> addToolServer(McpServerConfig serverConfig, Double expiryTime) throws Exception {
        if (mcpServerResources.containsKey(serverConfig.getServerId())) {
            throw ErrorHelper.buildError(StatusCode.RESOURCE_MCP_SERVER_ADD_ERROR,
                    "server_config", String.valueOf(serverConfig),
                    "reason", "server_id is already exist");
        }
        McpClient client = createClient(serverConfig);
        try {
            boolean connected = client.connect();
            if (!connected) {
                throw ErrorHelper.buildError(StatusCode.RESOURCE_MCP_SERVER_CONNECTION_ERROR,
                        "server_config", String.valueOf(serverConfig), "reason", "");
            }
            List<McpToolCard> results = innerRefreshMcpTools(client, serverConfig, expiryTime);
            mcpServerNameToIds.computeIfAbsent(serverConfig.getServerName(), k -> new ArrayList<>())
                    .add(serverConfig.getServerId());
            return results;
        } catch (Exception e) {
            throw ErrorHelper.buildError(StatusCode.RESOURCE_MCP_SERVER_ADD_ERROR,
                    "server_config", String.valueOf(serverConfig),
                    "reason", e.getMessage());
        }
    }

    private McpClient createClient(McpServerConfig config) {
        String clientType = config.getClientType() == null ? "sse" : config.getClientType().toLowerCase();
        return switch (clientType) {
            case "sse" -> OfficialMcpClientFactory.create(config);
            case "stdio" -> OfficialMcpClientFactory.create(config);
            case "openapi" -> new OpenApiClient(config);
            case "streamable_http", "streamable-http", "http" -> OfficialMcpClientFactory.create(config);
            case "playwright" -> new PlaywrightClient(config);
            default -> throw new UnsupportedOperationException("Unsupported MCP client type: " + config.getClientType());
        };
    }

    public List<String> getMcpServerIds(String serverName) {
        return mcpServerNameToIds.getOrDefault(serverName, Collections.emptyList());
    }

    public List<String> removeToolServer(String serverId, boolean ignoreNotExist) throws Exception {
        McpServerResource resource = mcpServerResources.remove(serverId);
        if (resource == null) {
            if (!ignoreNotExist) {
                throw ErrorHelper.buildError(StatusCode.RESOURCE_MCP_SERVER_REMOVE_ERROR,
                        "server_id", serverId, "reason", "server is not exist");
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

    public List<String> removeToolServer(String serverId) throws Exception {
        return removeToolServer(serverId, true);
    }

    public void addSysOperationTools(String sysOpId, List<String> toolIds) {
        if (toolIds == null || toolIds.isEmpty()) {
            return;
        }
        sysOpResources.put(sysOpId, new SysOpToolResource(sysOpId, new ArrayList<>(toolIds),
                System.currentTimeMillis()));
    }

    public List<String> removeSysOperationTools(String sysOpId) {
        SysOpToolResource resource = sysOpResources.remove(sysOpId);
        return resource != null ? resource.toolIds() : Collections.emptyList();
    }

    public List<String> getSysOperationToolIds(String sysOpId) {
        SysOpToolResource resource = sysOpResources.get(sysOpId);
        return resource != null ? resource.toolIds() : Collections.emptyList();
    }

    public List<McpToolCard> refreshToolServer(String serverId, boolean skipNotExist, boolean force)
            throws Exception {
        McpServerResource mcpResource = mcpServerResources.get(serverId);
        if (mcpResource == null) {
            if (!skipNotExist) {
                throw ErrorHelper.buildError(StatusCode.RESOURCE_MCP_SERVER_REFRESH_ERROR,
                        "server_id", serverId, "reason", "server is not exist");
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

    // ========== Internal ==========

    private List<McpToolCard> innerRefreshMcpTools(McpClient client, McpServerConfig serverConfig,
                                                   Double expiryTime) throws Exception {
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
            String toolId = generateMcpToolId(serverConfig.getServerId(), serverConfig.getServerName(),
                    card.getName());
            card.setId(toolId);
            addTool(toolId, new McpTool(client, card));
            mcpIds.add(toolId);
        }
        mcpServerResources.put(serverConfig.getServerId(),
                new McpServerResource(serverConfig, client, new ArrayList<>(mcpIds),
                        System.currentTimeMillis(), expiryTime));
        return mcpCards;
    }

    private void innerRemoveMcpTools(List<String> toolIds) {
        if (toolIds == null) {
            return;
        }
        for (String toolId : toolIds) {
            tools.remove(toolId);
        }
    }

    // ========== Inner record types ==========

    public record McpServerResource(
            McpServerConfig config,
            McpClient client,
            List<String> toolIds,
            long lastUpdateTime,
            Double expiryTime
    ) {
    }

    public record SysOpToolResource(
            String sysOpId,
            List<String> toolIds,
            long lastUpdateTime
    ) {
    }
}

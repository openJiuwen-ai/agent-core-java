/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.resourcemanager;

import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.foundation.tool.mcp.McpClient;
import com.openjiuwen.core.foundation.tool.mcp.McpServerConfig;
import com.openjiuwen.core.foundation.tool.mcp.McpTool;
import com.openjiuwen.core.foundation.tool.mcp.McpToolCard;

import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;

/**
 * Backward-compatible 0.1.12 tool manager facade.
 *
 * <p>Mirrors Python's {@code ToolMgr} in
 * {@code openjiuwen/core/runner/resources_manager/tool_manager.py}.</p>
 */
public class ToolMgr {

    private final ToolManager delegate = new ToolManager();
    private final Map<String, Tool> tools = new LinkedHashMap<>();
    private final Map<String, List<String>> mcpServerNameToIds = new LinkedHashMap<>();
    private final Map<String, McpServerResource> mcpServerResources = new LinkedHashMap<>();
    private final Map<String, Object> compatibilityTools = new LinkedHashMap<>();

    ToolManager asToolManager() {
        return delegate;
    }

    public void addTool(String toolId, Tool tool) {
        if (tools.containsKey(toolId)) {
            throw new IllegalArgumentException("already exist tool " + toolId);
        }
        tools.put(toolId, tool);
        delegate.addTool(toolId, tool);
    }

    public String kind() {
        return "tool";
    }

    public void put(String toolId, Object tool) {
        compatibilityTools.put(toolId, tool);
    }

    public boolean contains(String toolId) {
        return compatibilityTools.containsKey(toolId) || delegate.getTool(toolId) != null;
    }

    public int size() {
        return compatibilityTools.size();
    }

    public Object get(String toolId) {
        Object compatibilityValue = compatibilityTools.get(toolId);
        return compatibilityValue != null ? compatibilityValue : delegate.getTool(toolId);
    }

    public Tool getTool(String toolId) {
        Tool tool = tools.get(toolId);
        return tool == null ? delegate.getTool(toolId) : tool;
    }

    public Tool getMcpTool(String toolName, String serverId) {
        McpServerResource resource = mcpServerResources.get(serverId);
        if (resource != null) {
            return getTool(generateMcpToolId(serverId, resource.config().getServerName(), toolName));
        }
        return delegate.getMcpTool(toolName, serverId, null);
    }

    public List<Tool> getMcpTools(String serverId) {
        McpServerResource resource = mcpServerResources.get(serverId);
        if (resource == null) {
            return delegate.getMcpTools(serverId, null);
        }
        List<Tool> results = new ArrayList<>();
        for (String toolId : resource.toolIds()) {
            Tool tool = getTool(toolId);
            if (tool != null) {
                results.add(tool);
            }
        }
        return results;
    }

    public Object getMcpToolId(String serverId, String toolName) {
        McpServerResource resource = mcpServerResources.get(serverId);
        if (resource != null) {
            return toolName == null ? List.copyOf(resource.toolIds())
                    : generateMcpToolId(serverId, resource.config().getServerName(), toolName);
        }
        return toolName == null ? delegate.getMcpToolId(serverId) : delegate.getMcpToolId(serverId, toolName);
    }

    public Tool removeTool(String toolId) {
        if (compatibilityTools.remove(toolId) != null) {
            return null;
        }
        Tool tool = tools.remove(toolId);
        if (tool != null) {
            delegate.removeTool(toolId);
            return tool;
        }
        return delegate.removeTool(toolId);
    }

    public static String generateMcpToolId(String serverId, String serverName, String toolName) {
        return ToolManager.generateMcpToolId(serverId, serverName, toolName);
    }

    public List<McpToolCard> addToolServer(McpServerConfig serverConfig, Double expiryTime) throws Exception {
        return await(delegate.addToolServer(serverConfig, expiryTime));
    }

    public List<String> getMcpServerIds(String serverName) {
        List<String> ids = mcpServerNameToIds.get(serverName);
        return ids == null ? delegate.getMcpServerIds(serverName) : List.copyOf(ids);
    }

    public List<String> removeToolServer(String serverId, boolean ignoreNotExist) throws Exception {
        McpServerResource resource = mcpServerResources.remove(serverId);
        if (resource != null) {
            disconnectClient(resource.client());
            innerRemoveMcpTools(resource.toolIds());
            List<String> ids = mcpServerNameToIds.get(resource.config().getServerName());
            if (ids != null) {
                ids.remove(serverId);
                if (ids.isEmpty()) {
                    mcpServerNameToIds.remove(resource.config().getServerName());
                }
            }
            return List.copyOf(resource.toolIds());
        }
        return await(delegate.removeToolServer(serverId, ignoreNotExist));
    }

    public List<String> removeToolServer(String serverId) throws Exception {
        return removeToolServer(serverId, true);
    }

    public List<McpToolCard> refreshToolServer(String serverId, boolean skipNotExist, boolean force) throws Exception {
        McpServerResource resource = mcpServerResources.get(serverId);
        if (resource != null) {
            boolean needRefresh = force;
            Double expiryTime = resource.expiryTime();
            if (!force && expiryTime != null
                    && System.currentTimeMillis() - resource.lastUpdateTime() >= expiryTime) {
                needRefresh = true;
            }
            return needRefresh ? innerRefreshMcpTools(resource, serverId) : List.of();
        }
        return await(delegate.refreshToolServer(serverId, skipNotExist, force));
    }

    public void addSysOperationTools(String sysOpId, List<String> toolIds) {
        delegate.addSysOperationTools(sysOpId, toolIds);
    }

    public List<String> removeSysOperationTools(String sysOpId) {
        return delegate.removeSysOperationTools(sysOpId);
    }

    public List<String> getSysOperationToolIds(String sysOpId) {
        return delegate.getSysOperationToolIds(sysOpId);
    }

    public void release() {
        for (McpServerResource resource : mcpServerResources.values()) {
            try {
                disconnectClient(resource.client());
            } catch (Exception ignored) {
            }
        }
        innerRemoveMcpTools(mcpServerResources.values().stream()
                .flatMap(resource -> resource.toolIds().stream())
                .toList());
        mcpServerResources.clear();
        mcpServerNameToIds.clear();
        awaitUnchecked(delegate.release());
    }

    private List<McpToolCard> innerRefreshMcpTools(McpServerResource resource, String serverId) throws Exception {
        List<Object> rawCards = listTools(resource.client());
        List<McpToolCard> cards = new ArrayList<>();
        List<String> toolIds = new ArrayList<>();
        for (Object rawCard : rawCards) {
            if (rawCard instanceof McpToolCard card) {
                card.setServerId(serverId);
                card.setServerName(resource.config().getServerName());
                String toolId = generateMcpToolId(serverId, resource.config().getServerName(), card.getName());
                card.setId(toolId);
                addTool(toolId, new McpTool(clientAsMcp(resource.client()), card));
                cards.add(card);
                toolIds.add(toolId);
            }
        }
        mcpServerResources.put(serverId,
                new McpServerResource(resource.config(), resource.client(), List.copyOf(toolIds),
                        System.currentTimeMillis(), resource.expiryTime()));
        return cards;
    }

    private void innerRemoveMcpTools(List<String> toolIds) {
        for (String toolId : toolIds) {
            tools.remove(toolId);
            delegate.removeTool(toolId);
        }
    }

    private static List<Object> listTools(Object client) throws Exception {
        if (client instanceof McpClient mcpClient) {
            return mcpClient.listTools();
        }
        return List.of();
    }

    private static void disconnectClient(Object client) throws Exception {
        if (client instanceof McpClient mcpClient) {
            mcpClient.disconnect();
        }
    }

    private static McpClient clientAsMcp(Object client) {
        if (client instanceof McpClient mcpClient) {
            return mcpClient;
        }
        throw new IllegalArgumentException("MCP client must implement McpClient");
    }

    private static <T> T await(CompletionStage<T> stage) throws Exception {
        try {
            return stage.toCompletableFuture().get();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw interrupted;
        } catch (ExecutionException | CompletionException error) {
            Throwable cause = error.getCause();
            if (cause instanceof Exception exception) {
                throw exception;
            }
            throw new IllegalStateException(cause);
        }
    }

    private static void awaitUnchecked(CompletionStage<?> stage) {
        try {
            await(stage);
        } catch (RuntimeException runtimeException) {
            throw runtimeException;
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    /**
     * Compatibility resource record used by older ToolMgr tests and helpers.
     *
     * <p>Mirrors Python's {@code McpServerResource} dataclass in
     * {@code openjiuwen/core/runner/resources_manager/tool_manager.py}.</p>
     */
    public record McpServerResource(McpServerConfig config, Object client, List<String> toolIds,
                                    long lastUpdateTime, Double expiryTime) {
    }
}

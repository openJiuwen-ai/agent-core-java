// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.runner.resourcesmanager;

import com.openjiuwen.core.common.exception.ErrorBuilder;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.foundation.tool.mcp.McpClient;
import com.openjiuwen.core.foundation.tool.mcp.McpServerConfig;
import com.openjiuwen.core.foundation.tool.mcp.McpToolCard;
import com.openjiuwen.core.foundation.tool.mcp.MCPTool;
import com.openjiuwen.core.foundation.tool.mcp.client.OpenApiClient;
import com.openjiuwen.core.foundation.tool.mcp.client.PlaywrightClient;
import com.openjiuwen.core.foundation.tool.mcp.client.SseClient;
import com.openjiuwen.core.foundation.tool.mcp.client.StdioClient;
import com.openjiuwen.core.session.internal.AgentSession;
import com.openjiuwen.core.session.tracer.TracerDecorator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tool管理器
 * 
 * 对应Python: resources_manager/tool_manager.py - ToolMgr
 */
public class ToolMgr {
    
    private static final Logger logger = LoggerFactory.getLogger(ToolMgr.class);
    
    /** 默认连接超时（秒） */
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);
    
    private final Map<String, Object> tools = new ConcurrentHashMap<>();
    private final Map<String, List<String>> mcpServerNameToIds = new ConcurrentHashMap<>();
    private final Map<String, McpServerResource> mcpServerResources = new ConcurrentHashMap<>();
    
    public ToolMgr() {
    }
    
    /**
     * 添加Tool
     * 
     * @param toolId Tool ID
     * @param tool Tool实例
     * @throws IllegalArgumentException 如果toolId已存在
     */
    public void addTool(String toolId, Object tool) {
        if (tools.containsKey(toolId)) {
            throw new IllegalArgumentException("already exist tool " + toolId);
        }
        tools.put(toolId, tool);
    }
    
    /**
     * 获取原始Tool（单参数便捷方法）
     * 
     * @param toolId Tool ID
     * @return 原始Tool实例或null
     */
    public Object getTool(String toolId) {
        return tools.get(toolId);
    }
    
    /**
     * 获取Tool（带Trace装饰）
     * 
     * @param toolId Tool ID
     * @param session 会话（可为null）
     * @return TracedTool实例或null
     */
    @SuppressWarnings("unchecked")
    public <T> TracerDecorator.TracedTool<T> getTool(String toolId, AgentSession session) {
        Object tool = tools.get(toolId);
        if (tool == null) {
            return null;
        }
        // 使用适配器将 AgentSession 适配到 TracerDecorator.AgentSession
        TracerDecorator.AgentSession adaptedSession = AgentSessionAdapter.of(session);
        return (TracerDecorator.TracedTool<T>) TracerDecorator.decorateToolWithTrace(tool, adaptedSession);
    }
    
    /**
     * 获取原始Tool（不带Trace装饰）
     * 
     * @param toolId Tool ID
     * @return 原始Tool实例或null
     */
    @SuppressWarnings("unchecked")
    public <T> T getRawTool(String toolId) {
        return (T) tools.get(toolId);
    }
    
    /**
     * 移除Tool
     * 
     * @param toolId Tool ID
     * @return 被移除的Tool，不存在返回null
     */
    public Object removeTool(String toolId) {
        return tools.remove(toolId);
    }
    
    /**
     * 检查是否有Tool
     * 
     * @param toolId Tool ID
     * @return 如果存在返回true
     */
    public boolean hasTool(String toolId) {
        return tools.containsKey(toolId);
    }
    
    /**
     * 生成MCP工具ID
     * 
     * @param serverId 服务器ID
     * @param serverName 服务器名称
     * @param toolName 工具名称
     * @return 格式化的工具ID
     */
    public static String generateMcpToolId(String serverId, String serverName, String toolName) {
        return serverId + "." + serverName + "." + toolName;
    }
    
    /**
     * 获取MCP工具（带Trace装饰）
     * 
     * @param toolName 工具名称
     * @param serverId 服务器ID
     * @param session 会话
     * @return TracedTool实例或null
     */
    public <T> TracerDecorator.TracedTool<T> getMcpTool(String toolName, String serverId, AgentSession session) {
        McpServerResource resource = mcpServerResources.get(serverId);
        if (resource != null) {
            String toolId = generateMcpToolId(serverId, resource.getServerName(), toolName);
            return getTool(toolId, session);
        }
        return null;
    }
    
    /**
     * 获取服务器的所有MCP工具（带Trace装饰）
     * 
     * @param serverId 服务器ID
     * @param session 会话
     * @return TracedTool列表或null
     */
    public List<TracerDecorator.TracedTool<?>> getMcpTools(String serverId, AgentSession session) {
        McpServerResource resource = mcpServerResources.get(serverId);
        if (resource != null) {
            List<TracerDecorator.TracedTool<?>> results = new ArrayList<>();
            for (String toolId : resource.getToolIds()) {
                results.add(getTool(toolId, session));
            }
            return results;
        }
        return null;
    }
    
    /**
     * 获取MCP工具ID
     * 
     * @param serverId 服务器ID
     * @param toolName 工具名称（可为null获取所有）
     * @return 工具ID或ID列表
     */
    public Object getMcpToolId(String serverId, String toolName) {
        McpServerResource resource = mcpServerResources.get(serverId);
        if (resource != null) {
            if (toolName == null) {
                return resource.getToolIds();
            }
            return generateMcpToolId(serverId, resource.getServerName(), toolName);
        }
        return null;
    }
    
    /**
     * 获取服务器名称对应的服务器ID列表
     * 
     * @param serverName 服务器名称
     * @return 服务器ID列表
     */
    public List<String> getMcpServerIds(String serverName) {
        return mcpServerNameToIds.getOrDefault(serverName, Collections.emptyList());
    }
    
    /**
     * 添加MCP工具服务器
     * 
     * 对应Python: add_tool_server
     * 
     * @param serverConfig MCP服务器配置
     * @param expiryTime 过期时间（秒），null表示不过期
     * @return 工具卡片列表的CompletableFuture
     */
    public CompletableFuture<List<McpToolCard>> addToolServer(McpServerConfig serverConfig, Double expiryTime) {
        return CompletableFuture.supplyAsync(() -> {
            String serverId = serverConfig.getServerId();
            
            // 检查服务器是否已存在
            if (mcpServerResources.containsKey(serverId)) {
                throw ErrorBuilder.build(
                    StatusCode.RESOURCE_MCP_SERVER_ADD_ERROR,
                    "server_id is already exist"
                );
            }
            
            // 创建MCP客户端
            McpClient client = createClient(serverConfig);
            
            try {
                // 连接服务器
                Boolean connected = client.connect(DEFAULT_TIMEOUT).join();
                if (!connected) {
                    throw ErrorBuilder.build(
                        StatusCode.RESOURCE_MCP_SERVER_CONNECTION_ERROR,
                        "Failed to connect to MCP server"
                    );
                }
                
                // 刷新工具列表
                return innerRefreshMcpTools(client, serverConfig, expiryTime);
            } catch (Exception e) {
                // 清理连接
                try {
                    client.close();
                } catch (Exception ignored) {
                }
                
                if (e instanceof RuntimeException) {
                    throw (RuntimeException) e;
                }
                throw ErrorBuilder.build(
                    StatusCode.RESOURCE_MCP_SERVER_ADD_ERROR,
                    e.getMessage()
                );
            }
        });
    }
    
    /**
     * 创建MCP客户端
     * 
     * 对应Python: _create_client
     * 
     * @param config MCP服务器配置
     * @return McpClient实例
     */
    public static McpClient createClient(McpServerConfig config) {
        String clientType = config.getClientType();
        
        return switch (clientType.toLowerCase()) {
            case "sse" -> new SseClient(
                config.getServerPath(),
                config.getServerName(),
                config.getAuthHeaders(),
                config.getAuthQueryParams()
            );
            case "stdio" -> new StdioClient(
                config.getServerPath(),
                config.getServerName(),
                config.getParams()
            );
            case "playwright" -> new PlaywrightClient(
                config.getServerPath(),
                config.getServerName()
            );
            case "openapi" -> new OpenApiClient(
                config.getServerPath(),
                config.getServerName()
            );
            default -> throw new IllegalArgumentException(
                "Unsupported MCP client type: " + clientType
            );
        };
    }
    
    /**
     * 添加MCP服务器资源（用于测试）
     * 
     * @param serverId 服务器ID
     * @param serverName 服务器名称
     * @param toolIds 工具ID列表
     * @param expiryTime 过期时间（秒）
     */
    public void addMcpServerResource(String serverId, String serverName, 
                                     List<String> toolIds, Double expiryTime) {
        // 创建临时配置（用于测试）
        McpServerConfig config = McpServerConfig.builder()
            .serverId(serverId)
            .serverName(serverName)
            .build();
        McpServerResource resource = new McpServerResource(
            config, null, new ArrayList<>(toolIds), Instant.now(), expiryTime
        );
        mcpServerResources.put(serverId, resource);
        mcpServerNameToIds.computeIfAbsent(serverName, k -> new ArrayList<>()).add(serverId);
    }
    
    /**
     * 移除MCP工具服务器
     * 
     * @param serverId 服务器ID
     * @param ignoreNotExist 是否忽略不存在
     * @return 被移除的工具ID列表
     */
    public CompletableFuture<List<String>> removeToolServer(String serverId, boolean ignoreNotExist) {
        return CompletableFuture.supplyAsync(() -> {
            McpServerResource resource = mcpServerResources.remove(serverId);
            if (resource == null) {
                if (!ignoreNotExist) {
                    throw ErrorBuilder.build(
                        StatusCode.RESOURCE_MCP_SERVER_REMOVE_ERROR,
                        "server is not exist"
                    );
                }
                return Collections.<String>emptyList();
            }
            
            // 断开客户端连接（忽略异常）
            try {
                McpClient client = resource.getClient();
                if (client != null) {
                    client.disconnect(DEFAULT_TIMEOUT).join();
                }
            } catch (Exception e) {
                logger.warn("remove tool server disconnect error: {}, server_id={}", e.getMessage(), serverId);
            }
            
            // 移除关联的工具
            List<String> removedToolIds = new ArrayList<>(resource.getToolIds());
            innerRemoveMcpTools(removedToolIds);
            
            // 更新服务器名称映射
            List<String> ids = mcpServerNameToIds.get(resource.getServerName());
            if (ids != null) {
                ids.remove(serverId);
            }
            
            return removedToolIds;
        });
    }
    
    /**
     * 刷新MCP工具服务器
     * 
     * @param serverId 服务器ID
     * @param skipNotExist 是否跳过不存在
     * @param force 是否强制刷新
     * @return 刷新的工具卡片列表
     */
    public CompletableFuture<List<McpToolCard>> refreshToolServer(String serverId, boolean skipNotExist, boolean force) {
        return CompletableFuture.supplyAsync(() -> {
            McpServerResource resource = mcpServerResources.get(serverId);
            if (resource == null) {
                if (!skipNotExist) {
                    throw ErrorBuilder.build(
                        StatusCode.RESOURCE_MCP_SERVER_REFRESH_ERROR,
                        "server is not exist"
                    );
                }
                return Collections.<McpToolCard>emptyList();
            }
            
            boolean needRefresh = force || resource.isExpired();
            
            if (needRefresh) {
                McpClient client = resource.getClient();
                McpServerConfig config = resource.getConfig();
                if (client != null && config != null) {
                    // 先移除旧工具
                    innerRemoveMcpTools(resource.getToolIds());
                    // 刷新获取新工具
                    return innerRefreshMcpTools(client, config, resource.getExpiryTime());
                }
            }
            
            return Collections.<McpToolCard>emptyList();
        });
    }
    
    /**
     * 释放所有资源
     * 
     * @return 完成的CompletableFuture
     */
    public CompletableFuture<Void> release() {
        return CompletableFuture.runAsync(() -> {
            for (McpServerResource resource : mcpServerResources.values()) {
                try {
                    McpClient client = resource.getClient();
                    if (client != null) {
                        client.disconnect(DEFAULT_TIMEOUT).join();
                    }
                } catch (Exception e) {
                    logger.debug("Error disconnecting MCP client during release: {}", e.getMessage());
                }
            }
            mcpServerResources.clear();
            mcpServerNameToIds.clear();
        });
    }
    
    // ==================== 内部方法 ====================
    
    /**
     * 内部刷新MCP工具
     * 
     * 对应Python: _inner_refresh_mcp_tools
     */
    private List<McpToolCard> innerRefreshMcpTools(McpClient client, McpServerConfig serverConfig, Double expiryTime) {
        try {
            // 获取工具列表
            List<McpToolCard> mcpCards = client.listTools(DEFAULT_TIMEOUT).join();
            if (mcpCards == null) {
                mcpCards = new ArrayList<>();
            }
            
            List<String> mcpIds = new ArrayList<>();
            
            for (McpToolCard card : mcpCards) {
                // 生成工具ID
                String toolId = generateMcpToolId(
                    serverConfig.getServerId(),
                    serverConfig.getServerName(),
                    card.getName()
                );
                card.setId(toolId);
                card.setServerId(serverConfig.getServerId());
                card.setServerName(serverConfig.getServerName());
                
                // 创建MCPTool并添加
                MCPTool mcpTool = new MCPTool(client, card);
                addTool(toolId, mcpTool);
                mcpIds.add(toolId);
            }
            
            // 更新资源记录
            McpServerResource resource = new McpServerResource(
                serverConfig,
                client,
                new ArrayList<>(mcpIds),
                Instant.now(),
                expiryTime
            );
            mcpServerResources.put(serverConfig.getServerId(), resource);
            
            // 更新服务器名称映射
            mcpServerNameToIds
                .computeIfAbsent(serverConfig.getServerName(), k -> new ArrayList<>())
                .add(serverConfig.getServerId());
            
            logger.info("Refreshed {} MCP tools from server {}", mcpCards.size(), serverConfig.getServerId());
            return mcpCards;
        } catch (Exception e) {
            logger.error("Failed to refresh MCP tools: {}", e.getMessage());
            throw ErrorBuilder.build(
                StatusCode.RESOURCE_MCP_SERVER_REFRESH_ERROR,
                e.getMessage()
            );
        }
    }
    
    /**
     * 内部移除MCP工具
     * 
     * 对应Python: _inner_remove_mcp_tools
     */
    private void innerRemoveMcpTools(List<String> toolIds) {
        if (toolIds == null || toolIds.isEmpty()) {
            return;
        }
        for (String toolId : toolIds) {
            try {
                removeTool(toolId);
            } catch (Exception ignored) {
            }
        }
    }
}

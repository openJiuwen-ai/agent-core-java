// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.foundation.tool.mcp.client;

import com.openjiuwen.core.common.logging.LogManager;
import com.openjiuwen.core.common.logging.LoggerProtocol;
import com.openjiuwen.core.foundation.tool.mcp.McpClient;
import com.openjiuwen.core.foundation.tool.mcp.McpToolCard;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.spec.McpSchema;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Stdio（标准输入输出）传输协议的MCP客户端
 *
 * <p>使用标准输入输出与MCP服务器子进程通信。
 * 基于MCP Java SDK的{@code StdioClientTransport}实现。
 *
 * <p>对应Python: stdio_client.py - StdioClient
 *
 * @author OpenJiuwen
 * @since 2026-01-30
 */
public class StdioClient implements McpClient {

    private static final LoggerProtocol logger = LogManager.getLogger("StdioClient");

    private final String serverPath;
    private final String name;
    private final Map<String, Object> params;
    private McpSyncClient mcpSyncClient;
    private boolean connected = false;
    private boolean isDisconnected = false;

    /**
     * 构造Stdio客户端
     *
     * @param serverPath 服务器路径
     * @param name 客户端名称
     * @param params 连接参数（command, args, env, cwd, encoding_error_handler）
     */
    public StdioClient(String serverPath, String name, Map<String, Object> params) {
        this.serverPath = serverPath;
        this.name = name;
        this.params = params != null ? new HashMap<>(params) : new HashMap<>();
    }

    /**
     * 构造Stdio客户端（无额外参数）
     */
    public StdioClient(String serverPath, String name) {
        this(serverPath, name, null);
    }

    @Override
    public CompletableFuture<Boolean> connect(int retryTimes, Duration timeout) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // 从参数中构建ServerParameters
                String command = (String) params.getOrDefault("command", serverPath);
                @SuppressWarnings("unchecked")
                List<String> args = (List<String>) params.getOrDefault("args", List.of());
                @SuppressWarnings("unchecked")
                Map<String, String> env = (Map<String, String>) params.getOrDefault("env", null);

                // 构建MCP SDK的ServerParameters
                ServerParameters.Builder builder = ServerParameters.builder(command);
                if (args != null && !args.isEmpty()) {
                    builder.args(args);
                }
                if (env != null && !env.isEmpty()) {
                    builder.env(env);
                }
                ServerParameters serverParams = builder.build();

                // 创建Stdio传输和MCP客户端
                StdioClientTransport transport = new StdioClientTransport(serverParams);
                mcpSyncClient = io.modelcontextprotocol.client.McpClient.sync(transport).build();
                mcpSyncClient.initialize();
                connected = true;
                isDisconnected = false;
                logger.info("Stdio client connected successfully");
                return true;
            } catch (Exception e) {
                logger.error("Stdio connection failed: " + e.getMessage());
                try {
                    disconnect(timeout).get();
                } catch (Exception ignored) {
                    // ignore cleanup errors
                }
                return false;
            }
        });
    }

    @Override
    public CompletableFuture<Boolean> disconnect(Duration timeout) {
        return CompletableFuture.supplyAsync(() -> {
            if (isDisconnected) {
                logger.info("Stdio client already disconnected");
                return true;
            }
            try {
                if (mcpSyncClient != null) {
                    mcpSyncClient.close();
                    mcpSyncClient = null;
                }
                isDisconnected = true;
                connected = false;
                logger.info("Stdio client disconnected successfully");
                return true;
            } catch (Exception e) {
                // 处理CancelledError/RuntimeError等异常情况
                logger.info("Stdio client disconnected (with cleanup): " + e.getMessage());
                mcpSyncClient = null;
                isDisconnected = true;
                connected = false;
                return true;
            }
        });
    }

    @Override
    public CompletableFuture<List<McpToolCard>> listTools(Duration timeout) {
        return CompletableFuture.supplyAsync(() -> {
            if (mcpSyncClient == null) {
                throw new RuntimeException("Not connected to Stdio server");
            }
            try {
                McpSchema.ListToolsResult toolsResponse = mcpSyncClient.listTools();
                List<McpToolCard> toolsList = new ArrayList<>();
                for (McpSchema.Tool tool : toolsResponse.tools()) {
                    McpToolCard card = new McpToolCard(
                            tool.name(),
                            tool.description() != null ? tool.description() : "",
                            tool.inputSchema() != null ? convertJsonSchema(tool.inputSchema()) : Map.of()
                    );
                    card.setServerName(name);
                    toolsList.add(card);
                }
                logger.info("Retrieved " + toolsList.size() + " tools from Stdio server");
                return toolsList;
            } catch (Exception e) {
                logger.error("Failed to list tools via Stdio: " + e.getMessage());
                throw new RuntimeException("Failed to list tools via Stdio", e);
            }
        });
    }

    @Override
    public CompletableFuture<Object> callTool(String toolName, Map<String, Object> arguments, Duration timeout) {
        return CompletableFuture.supplyAsync(() -> {
            if (mcpSyncClient == null) {
                throw new RuntimeException("Not connected to Stdio server");
            }
            try {
                logger.info("Calling tool '" + toolName + "' via Stdio with arguments: " + arguments);
                McpSchema.CallToolRequest request = new McpSchema.CallToolRequest(toolName, arguments);
                McpSchema.CallToolResult toolResult = mcpSyncClient.callTool(request);

                // 提取文本内容（与Python一致：取最后一个content的text）
                String resultContent = extractTextContent(toolResult);
                logger.info("Tool '" + toolName + "' call completed via Stdio");
                return resultContent;
            } catch (Exception e) {
                logger.error("Tool call failed via Stdio: " + e.getMessage());
                throw new RuntimeException("Tool call failed via Stdio", e);
            }
        });
    }

    @Override
    public CompletableFuture<Optional<McpToolCard>> getToolInfo(String toolName, Duration timeout) {
        return listTools(timeout).thenApply(tools -> {
            for (McpToolCard tool : tools) {
                if (toolName.equals(tool.getName())) {
                    logger.debug("Found tool info for '" + toolName + "' via Stdio");
                    return Optional.of(tool);
                }
            }
            logger.warning("Tool '" + toolName + "' not found via Stdio");
            return Optional.empty();
        });
    }

    @Override
    public boolean isConnected() {
        return connected && mcpSyncClient != null;
    }

    /**
     * 获取服务器路径
     */
    public String getServerPath() {
        return serverPath;
    }

    /**
     * 获取客户端名称
     */
    public String getName() {
        return name;
    }

    // ==================== 内部方法 ====================

    /**
     * 从CallToolResult提取文本内容
     */
    private String extractTextContent(McpSchema.CallToolResult toolResult) {
        if (toolResult.content() == null || toolResult.content().isEmpty()) {
            return null;
        }
        McpSchema.Content lastContent = toolResult.content().get(toolResult.content().size() - 1);
        if (lastContent instanceof McpSchema.TextContent textContent) {
            return textContent.text();
        }
        return null;
    }

    /**
     * 将MCP SDK的JsonSchema转换为Map
     */
    private Map<String, Object> convertJsonSchema(McpSchema.JsonSchema jsonSchema) {
        Map<String, Object> schemaMap = new HashMap<>();
        if (jsonSchema.type() != null) {
            schemaMap.put("type", jsonSchema.type());
        }
        if (jsonSchema.properties() != null) {
            schemaMap.put("properties", jsonSchema.properties());
        }
        if (jsonSchema.required() != null) {
            schemaMap.put("required", jsonSchema.required());
        }
        if (jsonSchema.additionalProperties() != null) {
            schemaMap.put("additionalProperties", jsonSchema.additionalProperties());
        }
        return schemaMap;
    }

    /**
     * 仅用于测试：注入mock McpSyncClient
     */
    void setMcpSyncClientForTest(McpSyncClient client) {
        this.mcpSyncClient = client;
        this.connected = (client != null);
        this.isDisconnected = (client == null);
    }
}

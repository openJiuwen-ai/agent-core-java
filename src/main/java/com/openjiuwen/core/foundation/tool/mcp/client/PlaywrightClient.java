// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.foundation.tool.mcp.client;

import com.openjiuwen.core.common.logging.LogManager;
import com.openjiuwen.core.common.logging.LoggerProtocol;
import com.openjiuwen.core.foundation.tool.mcp.McpClient;
import com.openjiuwen.core.foundation.tool.mcp.McpToolCard;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.spec.McpClientTransport;
import io.modelcontextprotocol.spec.McpSchema;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Playwright浏览器会话MCP客户端
 *
 * <p>委托模式：根据serverPath类型自动选择SSE或Stdio传输。
 * - HTTP URL（http:// 或 https://）-> SSE传输
 * - StdioServerParameters -> Stdio传输
 *
 * <p>对应Python: playwright_client.py - PlaywrightClient
 *
 * @author OpenJiuwen
 * @since 2026-01-30
 */
public class PlaywrightClient implements McpClient {

    private static final LoggerProtocol logger = LogManager.getLogger("PlaywrightClient");

    private final Object serverPath;
    private final String name;
    private McpSyncClient mcpSyncClient;
    private boolean connected = false;
    private boolean isDisconnected = false;

    /**
     * 构造Playwright客户端
     *
     * @param serverPath 服务器路径（HTTP URL字符串或StdioServerParameters）
     * @param name 客户端名称
     */
    public PlaywrightClient(Object serverPath, String name) {
        this.serverPath = serverPath;
        this.name = name;
    }

    @Override
    public CompletableFuture<Boolean> connect(int retryTimes, Duration timeout) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                McpClientTransport transport;

                if (serverPath instanceof StdioServerParameters stdioParams) {
                    // Stdio传输
                    ServerParameters.Builder builder = ServerParameters.builder(stdioParams.getCommand());
                    if (stdioParams.getArgs() != null && !stdioParams.getArgs().isEmpty()) {
                        builder.args(stdioParams.getArgs());
                    }
                    if (stdioParams.getEnv() != null && !stdioParams.getEnv().isEmpty()) {
                        builder.env(stdioParams.getEnv());
                    }
                    transport = new StdioClientTransport(builder.build());
                    logger.debug("Using Stdio transport for Playwright client");
                } else if (serverPath instanceof String serverUrl
                        && (serverUrl.startsWith("http://") || serverUrl.startsWith("https://"))) {
                    // SSE传输
                    transport = new HttpClientSseClientTransport(serverUrl);
                    logger.debug("Using SSE transport for Playwright client");
                } else {
                    throw new IllegalArgumentException("Unsupported server_path type: " +
                            (serverPath != null ? serverPath.getClass().getName() : "null"));
                }

                // 创建并初始化MCP客户端
                mcpSyncClient = io.modelcontextprotocol.client.McpClient.sync(transport).build();
                mcpSyncClient.initialize();
                connected = true;
                isDisconnected = false;
                logger.info("Playwright client connected successfully");
                return true;
            } catch (Exception e) {
                logger.error("Playwright connection failed: " + e.getMessage());
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
                logger.info("Playwright client already disconnected");
                return true;
            }
            try {
                if (mcpSyncClient != null) {
                    mcpSyncClient.close();
                    mcpSyncClient = null;
                }
                isDisconnected = true;
                connected = false;
                logger.info("Playwright client disconnected successfully");
                return true;
            } catch (Exception e) {
                logger.info("Playwright client disconnected (with cleanup): " + e.getMessage());
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
                throw new RuntimeException("Not connected to Playwright server");
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
                logger.info("Retrieved " + toolsList.size() + " browser tools from Playwright server");
                return toolsList;
            } catch (Exception e) {
                logger.error("Failed to list browser tools: " + e.getMessage());
                throw new RuntimeException("Failed to list browser tools", e);
            }
        });
    }

    @Override
    public CompletableFuture<Object> callTool(String toolName, Map<String, Object> arguments, Duration timeout) {
        return CompletableFuture.supplyAsync(() -> {
            if (mcpSyncClient == null) {
                throw new RuntimeException("Not connected to Playwright server");
            }
            try {
                logger.info("Calling browser tool '" + toolName + "' with arguments: " + arguments);
                McpSchema.CallToolRequest request = new McpSchema.CallToolRequest(toolName, arguments);
                McpSchema.CallToolResult toolResult = mcpSyncClient.callTool(request);

                String resultContent = extractTextContent(toolResult);
                logger.info("Browser tool '" + toolName + "' call completed");
                return resultContent;
            } catch (Exception e) {
                logger.error("Browser tool call failed: " + e.getMessage());
                throw new RuntimeException("Browser tool call failed", e);
            }
        });
    }

    @Override
    public CompletableFuture<Optional<McpToolCard>> getToolInfo(String toolName, Duration timeout) {
        return listTools(timeout).thenApply(tools -> {
            for (McpToolCard tool : tools) {
                if (toolName.equals(tool.getName())) {
                    logger.debug("Found browser tool info for '" + toolName + "'");
                    return Optional.of(tool);
                }
            }
            logger.warning("Browser tool '" + toolName + "' not found");
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
    public Object getServerPath() {
        return serverPath;
    }

    /**
     * 获取客户端名称
     */
    public String getName() {
        return name;
    }

    // ==================== 内部方法 ====================

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

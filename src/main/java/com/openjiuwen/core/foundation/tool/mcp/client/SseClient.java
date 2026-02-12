// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.foundation.tool.mcp.client;

import com.openjiuwen.core.common.logging.LogManager;
import com.openjiuwen.core.common.logging.LoggerProtocol;
import com.openjiuwen.core.foundation.tool.mcp.McpClient;
import com.openjiuwen.core.foundation.tool.mcp.McpToolCard;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import io.modelcontextprotocol.spec.McpSchema;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * SSE（Server-Sent Events）传输协议的MCP客户端
 *
 * <p>使用HTTP Server-Sent Events协议与MCP服务器通信。
 * 基于MCP Java SDK的{@code HttpClientSseClientTransport}实现。
 *
 * <p>对应Python: sse_client.py - SseClient
 *
 * @author OpenJiuwen
 * @since 2026-01-30
 */
public class SseClient implements McpClient {

    private static final LoggerProtocol logger = LogManager.getLogger("SseClient");

    private final String serverPath;
    private final String name;
    private final Map<String, String> authHeaders;
    private final Map<String, String> authQueryParams;
    private McpSyncClient mcpSyncClient;
    private boolean connected = false;

    /**
     * 构造SSE客户端
     *
     * @param serverPath SSE服务器URL
     * @param name 客户端名称
     * @param authHeaders 认证请求头
     * @param authQueryParams 认证查询参数
     */
    public SseClient(String serverPath, String name,
                     Map<String, String> authHeaders,
                     Map<String, String> authQueryParams) {
        this.serverPath = serverPath;
        this.name = name;
        this.authHeaders = authHeaders != null ? new HashMap<>(authHeaders) : new HashMap<>();
        this.authQueryParams = authQueryParams != null ? new HashMap<>(authQueryParams) : new HashMap<>();
        if (!this.authHeaders.isEmpty() || !this.authQueryParams.isEmpty()) {
            logger.info("Using custom header and query authorization for SSE client");
        }
    }

    /**
     * 构造SSE客户端（无认证）
     */
    public SseClient(String serverPath, String name) {
        this(serverPath, name, null, null);
    }

    @Override
    public CompletableFuture<Boolean> connect(int retryTimes, Duration timeout) {
        return CompletableFuture.supplyAsync(() -> {
            for (int attempt = 0; attempt < retryTimes; attempt++) {
                try {
                    // 构建URL（附加查询参数用于认证）
                    String effectiveUrl = buildEffectiveUrl();

                    // 创建SSE传输
                    HttpClientSseClientTransport transport = createSseTransport(effectiveUrl);

                    // 创建并初始化MCP客户端
                    mcpSyncClient = io.modelcontextprotocol.client.McpClient.sync(transport).build();
                    mcpSyncClient.initialize();
                    connected = true;
                    logger.info("SSE client connected successfully to " + serverPath);
                    return true;
                } catch (Exception e) {
                    logger.error("SSE connection failed to " + serverPath + ": " + e.getMessage());
                    if (attempt < retryTimes - 1) {
                        logger.info("Retrying connection... attempt " + (attempt + 2));
                    }
                }
            }
            // 所有重试都失败，尝试断开清理
            try {
                disconnect(timeout).get();
            } catch (Exception ignored) {
                // ignore cleanup errors
            }
            return false;
        });
    }

    @Override
    public CompletableFuture<Boolean> disconnect(Duration timeout) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (mcpSyncClient != null) {
                    mcpSyncClient.close();
                    mcpSyncClient = null;
                }
                connected = false;
                logger.info("SSE client disconnected successfully");
                return true;
            } catch (Exception e) {
                logger.error("SSE disconnection failed: " + e.getMessage());
                return false;
            }
        });
    }

    @Override
    public CompletableFuture<List<McpToolCard>> listTools(Duration timeout) {
        return CompletableFuture.supplyAsync(() -> {
            if (mcpSyncClient == null) {
                throw new RuntimeException("Not connected to SSE server");
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
                logger.info("Retrieved " + toolsList.size() + " tools from SSE server");
                return toolsList;
            } catch (Exception e) {
                logger.error("Failed to list tools via SSE: " + e.getMessage());
                throw new RuntimeException("Failed to list tools via SSE", e);
            }
        });
    }

    @Override
    public CompletableFuture<Object> callTool(String toolName, Map<String, Object> arguments, Duration timeout) {
        return CompletableFuture.supplyAsync(() -> {
            if (mcpSyncClient == null) {
                throw new RuntimeException("Not connected to SSE server");
            }
            try {
                logger.info("Calling tool '" + toolName + "' via SSE with arguments: " + arguments);
                McpSchema.CallToolRequest request = new McpSchema.CallToolRequest(toolName, arguments);
                McpSchema.CallToolResult toolResult = mcpSyncClient.callTool(request);

                // 提取文本内容（与Python一致：取最后一个content的text）
                String resultContent = extractTextContent(toolResult);
                logger.info("Tool '" + toolName + "' call completed via SSE");
                return resultContent;
            } catch (Exception e) {
                logger.error("Tool call failed via SSE: " + e.getMessage());
                throw new RuntimeException("Tool call failed via SSE", e);
            }
        });
    }

    @Override
    public CompletableFuture<Optional<McpToolCard>> getToolInfo(String toolName, Duration timeout) {
        return listTools(timeout).thenApply(tools -> {
            for (McpToolCard tool : tools) {
                if (toolName.equals(tool.getName())) {
                    logger.debug("Found tool info for '" + toolName + "' via SSE");
                    return Optional.of(tool);
                }
            }
            logger.warning("Tool '" + toolName + "' not found via SSE");
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
     * 构建带查询参数的有效URL
     */
    private String buildEffectiveUrl() {
        if (authQueryParams.isEmpty()) {
            return serverPath;
        }
        StringBuilder sb = new StringBuilder(serverPath);
        sb.append(serverPath.contains("?") ? "&" : "?");
        boolean first = true;
        for (Map.Entry<String, String> entry : authQueryParams.entrySet()) {
            if (!first) {
                sb.append("&");
            }
            sb.append(entry.getKey()).append("=").append(entry.getValue());
            first = false;
        }
        return sb.toString();
    }

    /**
     * 创建SSE传输（可被测试覆盖）
     *
     * <p>注意：连接 Python MCP Server（uvicorn）时，需要强制 HTTP/1.1，否则
     * Java HttpClient 默认的 HTTP/2 升级请求会导致 POST body 丢失。
     * 连接 Java MCP Server（Tomcat 等）时则无此限制。
     */
    protected HttpClientSseClientTransport createSseTransport(String effectiveUrl) {
        return new HttpClientSseClientTransport(effectiveUrl);
    }

    /**
     * 从CallToolResult提取文本内容（取最后一个TextContent的text）
     * 与Python实现一致：result_content = tool_result.content[-1].text
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
     * 将MCP SDK的JsonSchema转换为Map（兼容McpToolCard的inputParams格式）
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
    }
}

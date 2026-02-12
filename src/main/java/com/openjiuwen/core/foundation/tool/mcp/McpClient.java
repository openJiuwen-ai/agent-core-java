// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.foundation.tool.mcp;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * MCP客户端接口
 * 
 * <p>定义MCP（Model Context Protocol）客户端的标准接口。
 * MCP协议用于LLM与外部工具服务器之间的通信。
 *
 * <p><b>实现说明：</b>
 * 当前为接口定义，具体实现类包括：
 * <ul>
 *   <li>SseClient - 基于Server-Sent Events的客户端</li>
 *   <li>StdioClient - 基于标准输入输出的客户端</li>
 *   <li>OpenApiClient - 基于OpenAPI规范的客户端</li>
 *   <li>PlaywrightClient - 基于浏览器自动化的客户端</li>
 * </ul>
 *
 * @author OpenJiuwen
 * @since 2026-01-30
 */
public interface McpClient {
    
    /**
     * 无超时限制常量（对应Python的NO_TIMEOUT = -1）
     */
    Duration NO_TIMEOUT = Duration.ofSeconds(-1);
    
    /**
     * 连接到MCP服务器
     *
     * @param retryTimes 重试次数
     * @param timeout 连接超时时间，使用NO_TIMEOUT表示无限等待
     * @return CompletableFuture，完成时返回连接是否成功
     */
    CompletableFuture<Boolean> connect(int retryTimes, Duration timeout);
    
    /**
     * 连接到MCP服务器（默认重试1次）
     *
     * @param timeout 连接超时时间
     * @return CompletableFuture，完成时返回连接是否成功
     */
    default CompletableFuture<Boolean> connect(Duration timeout) {
        return connect(1, timeout);
    }
    
    /**
     * 断开与MCP服务器的连接
     *
     * @param timeout 断开超时时间，使用NO_TIMEOUT表示无限等待
     * @return CompletableFuture，完成时返回断开是否成功
     */
    CompletableFuture<Boolean> disconnect(Duration timeout);
    
    /**
     * 列出所有可用的MCP工具
     *
     * @param timeout 请求超时时间，使用NO_TIMEOUT表示无限等待
     * @return CompletableFuture，完成时返回工具列表
     */
    CompletableFuture<List<McpToolCard>> listTools(Duration timeout);
    
    /**
     * 调用指定的MCP工具
     *
     * @param toolName 工具名称
     * @param arguments 工具参数
     * @param timeout 调用超时时间，使用NO_TIMEOUT表示无限等待
     * @return CompletableFuture，完成时返回工具执行结果
     */
    CompletableFuture<Object> callTool(String toolName, Map<String, Object> arguments, Duration timeout);
    
    /**
     * 获取指定工具的信息
     *
     * @param toolName 工具名称
     * @param timeout 请求超时时间，使用NO_TIMEOUT表示无限等待
     * @return CompletableFuture，完成时返回工具信息，如果工具不存在则返回Optional.empty()
     */
    CompletableFuture<Optional<McpToolCard>> getToolInfo(String toolName, Duration timeout);
    
    /**
     * 简化版调用工具方法（使用默认超时）
     *
     * @param toolName 工具名称
     * @param inputs 工具参数
     * @return 工具执行结果
     * @throws Exception 如果调用失败
     */
    default Object callTool(String toolName, Map<String, Object> inputs) throws Exception {
        try {
            return callTool(toolName, inputs, NO_TIMEOUT).get();
        } catch (Exception e) {
            throw new RuntimeException("Failed to call tool: " + toolName, e);
        }
    }
    
    /**
     * 简化版列出工具方法（使用默认超时）
     *
     * @return 工具列表
     * @throws Exception 如果列表获取失败
     */
    default Map<String, Object> listTools() throws Exception {
        try {
            List<McpToolCard> tools = listTools(NO_TIMEOUT).get();
            return Map.of("tools", tools);
        } catch (Exception e) {
            throw new RuntimeException("Failed to list tools", e);
        }
    }
    
    /**
     * 检查MCP客户端是否已连接
     *
     * @return 如果已连接返回true，否则返回false
     */
    default boolean isConnected() {
        return false;
    }
    
    /**
     * 关闭MCP客户端连接（同步版本）
     */
    default void close() {
        try {
            disconnect(NO_TIMEOUT).get();
        } catch (Exception e) {
            // Ignore exceptions on close
        }
    }
}

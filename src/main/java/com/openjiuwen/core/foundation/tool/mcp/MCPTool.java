package com.openjiuwen.core.foundation.tool.mcp;

import com.openjiuwen.core.common.exception.ErrorBuilder;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.foundation.tool.ToolCard;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

/**
 * MCP工具类
 * 实现通过MCP协议调用工具
 * 
 * @author OpenJiuwen
 * @since 2026-01-30
 */
public class MCPTool extends Tool<Map<String, Object>, Object> {
    
    private final McpClient client;
    
    /**
     * 构造器
     * 
     * @param client MCP客户端
     * @param card 工具卡片
     */
    public MCPTool(McpClient client, McpToolCard card) {
        super(card);
        this.client = client;
    }
    
    /**
     * 获取工具卡片
     * 
     * @return 工具卡片
     */
    @Override
    public ToolCard getCard() {
        return card;
    }
    
    /**
     * 获取MCP工具卡片（类型安全的访问方式）
     * 
     * @return MCP工具卡片
     */
    public McpToolCard getMcpCard() {
        return (McpToolCard) card;
    }
    
    /**
     * 调用MCP工具
     * 
     * @param inputs 输入参数
     * @param kwargs 额外参数
     * @return 执行结果的CompletableFuture
     */
    @Override
    public CompletableFuture<Object> invoke(Map<String, Object> inputs, Map<String, Object> kwargs) {
        if (client == null) {
            return CompletableFuture.failedFuture(
                ErrorBuilder.build(
                    StatusCode.TOOL_MCP_CLIENT_NOT_SUPPORTED,
                    null, null, null,
                    Map.of("card", card.toString())
                )
            );
        }
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                Object result = client.callTool(card.getName(), inputs);
                // 包装返回值以对齐Python实现（Map.of不允许null值）
                Map<String, Object> wrapped = new java.util.HashMap<>();
                wrapped.put("result", result != null ? result : "");
                return (Object) wrapped;
            } catch (Exception e) {
                throw ErrorBuilder.build(
                    StatusCode.TOOL_MCP_EXECUTION_ERROR,
                    null, null, null,
                    Map.of("error", e.getMessage() != null ? e.getMessage() : e.toString())
                );
            }
        });
    }
    
    /**
     * 流式调用（暂不支持）
     * 
     * @param inputs 输入参数
     * @param kwargs 额外参数
     * @return 流式结果
     */
    @Override
    public Stream<Object> stream(Map<String, Object> inputs, Map<String, Object> kwargs) {
        throw ErrorBuilder.build(
            StatusCode.TOOL_STREAM_NOT_SUPPORTED,
            null, null, null,
            Map.of("card", card.toString())
        );
    }
}

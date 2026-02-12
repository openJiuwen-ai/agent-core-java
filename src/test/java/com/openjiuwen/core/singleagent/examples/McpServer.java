// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.singleagent.examples;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.HttpServletSseServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;
import org.apache.catalina.Context;
import org.apache.catalina.startup.Tomcat;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * MCP SSE Server（Java 版）— 使用 MCP Java SDK + 嵌入式 Tomcat。
 *
 * <p>功能等价于 Python 版 {@code mcp_server.py}，提供 {@code query_weather} 工具，
 * 监听 {@code http://127.0.0.1:8188}，通过 SSE 协议与 MCP 客户端通信。
 *
 * <p>由于客户端和服务端均使用 Java MCP SDK，HTTP/2 协议可正常工作，
 * 无需强制 HTTP/1.1。
 *
 * <p>对应 Python: agent-core/examples/test_examples_for_java/react_agent/test/mcp_server.py
 */
public class McpServer {

    private static final int DEFAULT_PORT = 8188;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * query_weather 工具实现 — 返回模拟天气数据。
     *
     * @param location 查询位置
     * @return 模拟天气数据
     */
    public static Map<String, Object> queryWeather(String location) {
        Map<String, Object> mockData = new LinkedHashMap<>();
        mockData.put("location", location);
        mockData.put("temperature", "22℃");
        mockData.put("condition", "晴");
        System.out.println("[query_weather] " + mockData);
        return mockData;
    }

    /**
     * 主函数 — 启动 MCP SSE Server。
     */
    public static void main(String[] args) throws Exception {
        int port = DEFAULT_PORT;

        // 1. 创建 SSE Server Transport Provider（Servlet 实现）
        //    messageEndpoint: 客户端 POST JSON-RPC 消息的路径
        //    sseEndpoint: 客户端 GET SSE 事件流的路径
        HttpServletSseServerTransportProvider transportProvider =
            new HttpServletSseServerTransportProvider(OBJECT_MAPPER, "/messages", "/sse");

        // 2. 定义 query_weather 工具的 JSON Schema
        McpSchema.JsonSchema inputSchema = new McpSchema.JsonSchema(
            "object",
            Map.of("location", Map.of("title", "Location", "type", "string")),
            List.of("location"),
            null
        );
        McpSchema.Tool weatherTool = new McpSchema.Tool(
            "query_weather",
            "查询指定地点的天气信息",
            inputSchema
        );

        // 3. 构建 MCP Server，注册工具
        //    build() 内部会将 server 与 transportProvider 关联，
        //    后续 Tomcat 接收请求时自动路由到 server 处理。
        @SuppressWarnings("unused")
        McpSyncServer mcpSyncServer = io.modelcontextprotocol.server.McpServer
            .sync(transportProvider)
            .serverInfo("McpSseServer", "1.0.0")
            .capabilities(McpSchema.ServerCapabilities.builder()
                .tools(true)
                .build())
            .tool(weatherTool, (exchange, params) -> {
                String location = (String) params.get("location");
                Map<String, Object> result = queryWeather(location);
                try {
                    String jsonResult = OBJECT_MAPPER.writeValueAsString(result);
                    return new McpSchema.CallToolResult(
                        List.of(new McpSchema.TextContent(jsonResult)),
                        false
                    );
                } catch (Exception e) {
                    return new McpSchema.CallToolResult(
                        List.of(new McpSchema.TextContent("Error: " + e.getMessage())),
                        true
                    );
                }
            })
            .build();

        // 4. 创建嵌入式 Tomcat，挂载 MCP Servlet
        Tomcat tomcat = new Tomcat();
        tomcat.setPort(port);
        tomcat.getConnector(); // 触发连接器初始化

        // Tomcat 需要一个 baseDir
        String baseDir = System.getProperty("java.io.tmpdir") + File.separator + "tomcat-mcp-" + port;
        tomcat.setBaseDir(baseDir);

        Context context = tomcat.addContext("", null);
        // 设置请求和响应的字符编码为 UTF-8（Servlet 规范默认 ISO-8859-1 会导致中文乱码）
        context.setRequestCharacterEncoding("UTF-8");
        context.setResponseCharacterEncoding("UTF-8");
        Tomcat.addServlet(context, "mcp-sse", transportProvider);
        context.addServletMappingDecoded("/*", "mcp-sse");

        // 5. 启动
        tomcat.start();
        System.out.println("=".repeat(60));
        System.out.println("MCP SSE Server (Java) started");
        System.out.println("  Listening on: http://127.0.0.1:" + port);
        System.out.println("  SSE endpoint: http://127.0.0.1:" + port + "/sse");
        System.out.println("  Tools: [query_weather]");
        System.out.println("=".repeat(60));
        tomcat.getServer().await();
    }
}

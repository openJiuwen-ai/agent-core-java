/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.deep_agent;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.foundation.tool.mcp.McpServerConfig;
import com.openjiuwen.core.foundation.tool.schema.ToolInfo;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.runner.base.TagMatchStrategy;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.harness.rails.McpRail;
import com.openjiuwen.harness.schema.config.DeepAgentConfig;
import com.openjiuwen.harness.workspace.Workspace;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Verifies MCP tools are exposed to the LLM via AbilityManager.listToolInfo.
 */
class DeepAgentMcpExposureTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @TempDir
    Path tempDir;

    private HttpServer mockServer;
    private final List<String> registeredServerIds = new ArrayList<>();

    @BeforeEach
    void startMockServer() throws IOException {
        mockServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        mockServer.createContext("/mcp", DeepAgentMcpExposureTest::handleMcpRequest);
        mockServer.start();
    }

    @AfterEach
    void cleanup() throws Exception {
        for (String serverId : registeredServerIds) {
            try {
                Runner.resourceMgr().removeMcpServer(serverId, null, null, TagMatchStrategy.ALL, true);
            } catch (Exception ignored) {
                // best-effort cleanup between tests
            }
        }
        registeredServerIds.clear();
        if (mockServer != null) {
            mockServer.stop(0);
        }
    }

    @Test
    @Timeout(value = 20, unit = TimeUnit.SECONDS)
    void mcpToolsRegisteredViaResourceMgrShouldAppearInLlmToolList() throws Exception {
        McpServerConfig config = McpServerConfig.builder()
                .serverName("test-lib")
                .serverPath(mockServerUrl())
                .clientType("streamable_http")
                .build();
        Runner.resourceMgr().addMcpServer(config, null, null);
        registeredServerIds.add(config.getServerId());

        DeepAgent deepAgent = new DeepAgent(
                AgentCard.builder().name("mcp-exposure-agent").description("MCP exposure").build(),
                DeepAgentConfig.builder().rails(List.of(new McpRail())).build(),
                Workspace.builder().rootPath(tempDir.toString()).language("en").build());
        deepAgent.ensureInitialized();

        List<String> toolNames = deepAgent.getAgent().getAbilityManager().listToolInfo().stream()
                .map(ToolInfo::getName)
                .toList();

        assertThat(toolNames).contains("list_mcp_resources", "read_mcp_resource", "search_kb", "get_summary");
        assertThat(config.getServerId()).isEqualTo("test-lib");
    }

    @Test
    @Timeout(value = 20, unit = TimeUnit.SECONDS)
    void configDeclaredMcpsShouldExposeToolsAfterEnsureInitialized() {
        McpServerConfig config = McpServerConfig.builder()
                .serverName("config-lib")
                .serverPath(mockServerUrl())
                .clientType("streamable_http")
                .build();
        registeredServerIds.add(config.getServerId());

        DeepAgent deepAgent = new DeepAgent(
                AgentCard.builder().name("mcp-config-agent").description("MCP config path").build(),
                DeepAgentConfig.builder()
                        .mcps(List.of(config))
                        .rails(List.of(new McpRail()))
                        .build(),
                Workspace.builder().rootPath(tempDir.toString()).language("en").build());
        deepAgent.ensureInitialized();

        List<String> toolNames = deepAgent.getAgent().getAbilityManager().listToolInfo().stream()
                .map(ToolInfo::getName)
                .toList();

        assertThat(toolNames).contains("list_mcp_resources", "read_mcp_resource", "search_kb", "get_summary");
        assertThat(Runner.resourceMgr().getMcpServerConfig("config-lib")).isNotNull();
    }

    private String mockServerUrl() {
        return "http://127.0.0.1:" + mockServer.getAddress().getPort() + "/mcp";
    }

    private static void handleMcpRequest(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(405, -1);
            exchange.close();
            return;
        }
        Map<String, Object> request = MAPPER.readValue(exchange.getRequestBody(), new TypeReference<>() {
        });
        Object id = request.get("id");
        String method = String.valueOf(request.get("method"));
        if (id == null) {
            // JSON-RPC notification — acknowledge with empty body
            exchange.sendResponseHeaders(202, -1);
            exchange.close();
            return;
        }

        Map<String, Object> result = switch (method) {
            case "initialize" -> Map.of(
                    "protocolVersion", "2024-11-05",
                    "capabilities", Map.of("tools", Map.of()),
                    "serverInfo", Map.of("name", "mock-mcp", "version", "1.0.0"));
            case "tools/list" -> Map.of("tools", List.of(
                    toolDescriptor("search_kb", "Search knowledge base"),
                    toolDescriptor("get_summary", "Get document summary")));
            case "tools/call" -> Map.of("content", List.of(Map.of("type", "text", "text", "ok")));
            case "resources/list" -> Map.of("resources", List.of());
            default -> Map.of();
        };
        writeJson(exchange, Map.of("jsonrpc", "2.0", "id", id, "result", result));
    }

    private static Map<String, Object> toolDescriptor(String name, String description) {
        Map<String, Object> tool = new LinkedHashMap<>();
        tool.put("name", name);
        tool.put("description", description);
        tool.put("inputSchema", Map.of("type", "object", "properties", Map.of()));
        return tool;
    }

    private static void writeJson(HttpExchange exchange, Map<String, Object> response) throws IOException {
        byte[] body = MAPPER.writeValueAsBytes(response);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }
}

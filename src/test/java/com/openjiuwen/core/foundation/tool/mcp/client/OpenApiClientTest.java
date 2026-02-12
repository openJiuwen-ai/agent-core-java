// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.foundation.tool.mcp.client;

import com.openjiuwen.core.common.exception.JiuWenBaseException;
import com.openjiuwen.core.foundation.tool.mcp.McpClient;
import com.openjiuwen.core.foundation.tool.mcp.McpToolCard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * OpenApiClient 单元测试
 */
class OpenApiClientTest {

    @TempDir
    Path tempDir;

    private Path openApiJsonFile;

    @BeforeEach
    void setUp() throws Exception {
        // 创建测试用的OpenAPI规范文件
        String openApiSpec = """
                {
                  "openapi": "3.0.0",
                  "info": {
                    "title": "Test API",
                    "version": "1.0.0"
                  },
                  "servers": [
                    {
                      "url": "http://localhost:8080"
                    }
                  ],
                  "paths": {
                    "/items": {
                      "get": {
                        "operationId": "list_items",
                        "summary": "List all items",
                        "description": "Returns a list of items",
                        "responses": {
                          "200": {
                            "description": "Successful response"
                          }
                        }
                      },
                      "post": {
                        "operationId": "create_item",
                        "summary": "Create an item",
                        "requestBody": {
                          "content": {
                            "application/json": {
                              "schema": {
                                "type": "object",
                                "properties": {
                                  "name": {
                                    "type": "string",
                                    "description": "Item name"
                                  },
                                  "price": {
                                    "type": "number",
                                    "description": "Item price"
                                  }
                                },
                                "required": ["name"]
                              }
                            }
                          }
                        },
                        "responses": {
                          "201": {
                            "description": "Item created"
                          }
                        }
                      }
                    },
                    "/items/{id}": {
                      "get": {
                        "operationId": "get_item",
                        "summary": "Get item by ID",
                        "parameters": [
                          {
                            "name": "id",
                            "in": "path",
                            "required": true,
                            "schema": {
                              "type": "string"
                            }
                          }
                        ],
                        "responses": {
                          "200": {
                            "description": "Successful response"
                          }
                        }
                      }
                    }
                  }
                }
                """;
        openApiJsonFile = tempDir.resolve("openapi.json");
        Files.writeString(openApiJsonFile, openApiSpec);
    }

    @Test
    @DisplayName("构造器 - 基本参数")
    void testConstructor() {
        OpenApiClient client = new OpenApiClient("openapi.json", "test-api");
        assertEquals("openapi.json", client.getServerPath());
        assertEquals("test-api", client.getName());
        assertFalse(client.isConnected());
    }

    @Test
    @DisplayName("connect - 解析OpenAPI JSON文件")
    void testConnectWithJsonFile() throws Exception {
        OpenApiClient client = new OpenApiClient(openApiJsonFile.toString(), "test-api");
        boolean result = client.connect(1, McpClient.NO_TIMEOUT).get();
        assertTrue(result);
        assertTrue(client.isConnected());
    }

    @Test
    @DisplayName("connect - 不存在的文件返回false")
    void testConnectNonexistentFile() throws Exception {
        OpenApiClient client = new OpenApiClient("/nonexistent/path/openapi.json", "test");
        boolean result = client.connect(1, McpClient.NO_TIMEOUT).get();
        assertFalse(result);
    }

    @Test
    @DisplayName("listTools - 连接后返回工具列表")
    void testListToolsAfterConnect() throws Exception {
        OpenApiClient client = new OpenApiClient(openApiJsonFile.toString(), "test-api");
        client.connect(1, McpClient.NO_TIMEOUT).get();

        List<McpToolCard> tools = client.listTools(McpClient.NO_TIMEOUT).get();
        assertEquals(3, tools.size()); // list_items, create_item, get_item

        // 验证工具名称
        List<String> toolNames = tools.stream().map(McpToolCard::getName).sorted().toList();
        assertTrue(toolNames.contains("list_items"));
        assertTrue(toolNames.contains("create_item"));
        assertTrue(toolNames.contains("get_item"));

        // 验证serverName
        for (McpToolCard tool : tools) {
            assertEquals("test-api", tool.getServerName());
        }
    }

    @Test
    @DisplayName("getToolInfo - 找到工具")
    void testGetToolInfoFound() throws Exception {
        OpenApiClient client = new OpenApiClient(openApiJsonFile.toString(), "test-api");
        client.connect(1, McpClient.NO_TIMEOUT).get();

        var result = client.getToolInfo("list_items", McpClient.NO_TIMEOUT).get();
        assertTrue(result.isPresent());
        assertEquals("list_items", result.get().getName());
    }

    @Test
    @DisplayName("getToolInfo - 未找到工具返回empty")
    void testGetToolInfoNotFound() throws Exception {
        OpenApiClient client = new OpenApiClient(openApiJsonFile.toString(), "test-api");
        client.connect(1, McpClient.NO_TIMEOUT).get();

        var result = client.getToolInfo("nonexistent", McpClient.NO_TIMEOUT).get();
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("disconnect - 成功")
    void testDisconnect() throws Exception {
        OpenApiClient client = new OpenApiClient(openApiJsonFile.toString(), "test-api");
        client.connect(1, McpClient.NO_TIMEOUT).get();
        assertTrue(client.isConnected());

        boolean result = client.disconnect(McpClient.NO_TIMEOUT).get();
        assertTrue(result);
        assertFalse(client.isConnected());
    }

    @Test
    @DisplayName("loadConf - 加载JSON文件")
    void testLoadConfJson() throws Exception {
        Map<String, Object> data = OpenApiClient.loadConf(openApiJsonFile.toString());
        assertNotNull(data);
        assertEquals("3.0.0", data.get("openapi"));
    }

    @Test
    @DisplayName("loadConf - 加载YAML文件")
    void testLoadConfYaml() throws Exception {
        String yamlContent = """
                openapi: "3.0.0"
                info:
                  title: "Test API"
                  version: "1.0.0"
                paths: {}
                """;
        Path yamlFile = tempDir.resolve("openapi.yaml");
        Files.writeString(yamlFile, yamlContent);

        Map<String, Object> data = OpenApiClient.loadConf(yamlFile.toString());
        assertNotNull(data);
        assertEquals("3.0.0", data.get("openapi"));
    }

    @Test
    @DisplayName("loadConf - 不存在的文件抛异常")
    void testLoadConfNonexistent() {
        assertThrows(JiuWenBaseException.class, () ->
                OpenApiClient.loadConf("/nonexistent/file.json"));
    }

    @Test
    @DisplayName("loadConf - 不支持的文件格式抛异常")
    void testLoadConfUnsupportedFormat() throws Exception {
        Path txtFile = tempDir.resolve("spec.txt");
        Files.writeString(txtFile, "some text");
        assertThrows(JiuWenBaseException.class, () ->
                OpenApiClient.loadConf(txtFile.toString()));
    }

    @Test
    @DisplayName("connect - 多文件用逗号分隔")
    void testConnectMultipleFiles() throws Exception {
        // 创建第二个OpenAPI文件
        String spec2 = """
                {
                  "openapi": "3.0.0",
                  "info": { "title": "API 2", "version": "1.0.0" },
                  "paths": {
                    "/users": {
                      "get": {
                        "operationId": "list_users",
                        "summary": "List users",
                        "responses": { "200": { "description": "OK" } }
                      }
                    }
                  }
                }
                """;
        Path file2 = tempDir.resolve("openapi2.json");
        Files.writeString(file2, spec2);

        String combinedPath = openApiJsonFile.toString() + "," + file2.toString();
        OpenApiClient client = new OpenApiClient(combinedPath, "multi-api");
        boolean result = client.connect(1, McpClient.NO_TIMEOUT).get();
        assertTrue(result);

        List<McpToolCard> tools = client.listTools(McpClient.NO_TIMEOUT).get();
        // 第一个文件3个工具 + 第二个文件1个工具
        assertEquals(4, tools.size());
    }

    @Test
    @DisplayName("工具名称生成 - operationId截断")
    void testToolNameGeneration() throws Exception {
        OpenApiClient client = new OpenApiClient(openApiJsonFile.toString(), "test");
        client.connect(1, McpClient.NO_TIMEOUT).get();

        var tools = client.listTools(McpClient.NO_TIMEOUT).get();
        // 验证operationId被用作工具名，且__后面的部分被截断
        List<String> names = tools.stream().map(McpToolCard::getName).toList();
        assertTrue(names.contains("list_items"));
        assertTrue(names.contains("create_item"));
        assertTrue(names.contains("get_item"));
    }

    @Test
    @DisplayName("inputSchema - 包含参数和请求体")
    void testInputSchemaContainsParams() throws Exception {
        OpenApiClient client = new OpenApiClient(openApiJsonFile.toString(), "test");
        client.connect(1, McpClient.NO_TIMEOUT).get();

        // get_item 应该有 id 参数
        var getItemInfo = client.getToolInfo("get_item", McpClient.NO_TIMEOUT).get();
        assertTrue(getItemInfo.isPresent());
        @SuppressWarnings("unchecked")
        Map<String, Object> schema = (Map<String, Object>) getItemInfo.get().getInputParams();
        assertNotNull(schema.get("properties"));

        // create_item 应该有 name 和 price 属性
        var createItemInfo = client.getToolInfo("create_item", McpClient.NO_TIMEOUT).get();
        assertTrue(createItemInfo.isPresent());
        @SuppressWarnings("unchecked")
        Map<String, Object> createSchema = (Map<String, Object>) createItemInfo.get().getInputParams();
        @SuppressWarnings("unchecked")
        Map<String, Object> props = (Map<String, Object>) createSchema.get("properties");
        assertNotNull(props.get("name"));
        assertNotNull(props.get("price"));
    }
}


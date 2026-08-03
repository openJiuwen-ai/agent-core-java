/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.tool.mcp.client;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.foundation.tool.mcp.McpServerConfig;
import com.openjiuwen.core.foundation.tool.mcp.McpToolCard;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's {@code OpenApiClient} behavior in
 * {@code openjiuwen/core/foundation/tool/mcp/client/openapi_client.py}.
 */
class OpenApiClientTest {

    @TempDir
    Path tempDir;

    @Test
    void loadConfParsesJsonAndYamlAndRejectsUnsupportedSuffix() throws Exception {
        Path json = tempDir.resolve("openapi.json");
        Files.writeString(json, "{\"openapi\":\"3.0.0\",\"paths\":{}}");
        Path yaml = tempDir.resolve("openapi.yaml");
        Files.writeString(yaml, "openapi: 3.0.0\npaths: {}\n");
        Path txt = tempDir.resolve("openapi.txt");
        Files.writeString(txt, "paths: {}\n");

        assertEquals("3.0.0", OpenApiClient.loadConf(json.toString()).get("openapi"));
        assertEquals("3.0.0", OpenApiClient.loadConf(yaml.toString()).get("openapi").toString());

        BaseError error = assertThrows(BaseError.class, () -> OpenApiClient.loadConf(txt.toString()));
        assertEquals(StatusCode.TOOL_OPENAPI_CLIENT_EXECUTION_ERROR, error.getStatus());
        assertTrue(error.getParams().get("reason").toString().contains("only supports"));
    }

    @Test
    void connectCreatesToolCardsWithPythonNameAndParameterRules() throws Exception {
        Path spec = tempDir.resolve("openapi.json");
        Files.writeString(spec, """
                {
                  "openapi": "3.0.0",
                  "servers": [{"url": "https://api.example.test/"}],
                  "components": {
                    "schemas": {
                      "Payload": {
                        "type": "object",
                        "properties": {
                          "name": {"type": "string"}
                        },
                        "required": ["name"]
                      }
                    }
                  },
                  "paths": {
                    "/items/{item_id}": {
                      "get": {
                        "operationId": "fetch_item__generated",
                        "description": "Fetch an item",
                        "tags": ["inventory"],
                        "parameters": [
                          {
                            "name": "item_id",
                            "in": "path",
                            "required": true,
                            "description": "item id",
                            "schema": {"type": "string"}
                          },
                          {
                            "name": "verbose",
                            "in": "query",
                            "schema": {"type": "boolean"}
                          }
                        ],
                        "responses": {
                          "200": {
                            "content": {
                              "application/json": {
                                "schema": {"$ref": "#/components/schemas/Payload"}
                              }
                            }
                          }
                        }
                      }
                    },
                    "/dupe": {
                      "post": {
                        "operationId": "fetch_item__other",
                        "requestBody": {
                          "content": {
                            "application/json": {
                              "schema": {"$ref": "#/components/schemas/Payload"}
                            }
                          }
                        },
                        "responses": {"201": {"description": "created"}}
                      }
                    },
                    "/long": {
                      "get": {
                        "operationId": "abcdefghijklmnopqrstuvwxyzabcdefghijklmnopqrstuvwxyzabcdefghijklmnop__x",
                        "responses": {"204": {"description": "ok"}}
                      }
                    }
                  }
                }
                """);

        OpenApiClient client = new OpenApiClient(new McpServerConfig(
                "srv-1", "openapi-server", spec.toString(), "openapi",
                Map.of(), Map.of(), Map.of()));

        assertTrue(client.connect(1, McpServerConfig.NO_TIMEOUT));

        List<Object> cards = client.listTools(McpServerConfig.NO_TIMEOUT);
        assertEquals(3, cards.size());
        McpToolCard first = assertInstanceOf(McpToolCard.class, cards.get(0));
        McpToolCard second = assertInstanceOf(McpToolCard.class, cards.get(1));
        McpToolCard third = assertInstanceOf(McpToolCard.class, cards.get(2));

        assertEquals("fetch_item", first.getName());
        assertEquals("fetch_item_2", second.getName());
        assertEquals(64, third.getName().length());
        assertEquals("openapi-server", first.getServerName());
        assertEquals("Fetch an item", first.getDescription());

        Map<String, Object> firstSchema = first.getInputParams();
        Map<?, ?> properties = assertInstanceOf(Map.class, firstSchema.get("properties"));
        assertTrue(properties.containsKey("item_id"));
        assertTrue(properties.containsKey("verbose"));
        assertEquals(List.of("item_id"), firstSchema.get("required"));

        Map<String, Object> secondSchema = second.getInputParams();
        Map<?, ?> bodyProperties = assertInstanceOf(Map.class, secondSchema.get("properties"));
        assertTrue(bodyProperties.containsKey("name"));
        assertEquals(List.of("name"), secondSchema.get("required"));
    }

    @Test
    void getToolInfoAndUnknownToolFollowPythonFallbacks() throws Exception {
        Path spec = tempDir.resolve("openapi.yaml");
        Files.writeString(spec, """
                openapi: 3.0.0
                paths:
                  /ping:
                    get:
                      summary: ping
                      responses:
                        "200":
                          description: ok
                """);
        OpenApiClient client = new OpenApiClient(new McpServerConfig("demo", spec.toString()));
        assertTrue(client.connect());

        Object knownInfo = client.getToolInfo("ping", McpServerConfig.NO_TIMEOUT).orElseThrow();
        McpToolCard knownCard = assertInstanceOf(McpToolCard.class, knownInfo);
        assertEquals("ping", knownCard.getName());
        assertNotNull(knownCard.getInputParams());

        Object missingInfo = client.getToolInfo("missing", McpServerConfig.NO_TIMEOUT).orElseThrow();
        McpToolCard missingCard = assertInstanceOf(McpToolCard.class, missingInfo);
        assertEquals("missing", missingCard.getName());
        assertEquals("", missingCard.getDescription());
        assertTrue(missingCard.getInputParams().isEmpty());
        assertNull(client.callTool("missing", Map.of(), McpServerConfig.NO_TIMEOUT));
    }
}

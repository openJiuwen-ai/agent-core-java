/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.systemtest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.openjiuwen.core.foundation.tool.mcp.McpToolCard;
import com.openjiuwen.core.foundation.tool.service_api.RestfulApi;
import com.openjiuwen.core.foundation.tool.service_api.RestfulApiCard;
import com.openjiuwen.core.foundation.tool.service_api.parser.ParserRegistry;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * System tests for advanced Tool features: RestfulApi, McpTool card, ParserRegistry.
 * Covers gaps identified in CHECK doc for Tool module.
 * All tests are local (no remote API required).
 */
@Tag("system-test")
class ToolAdvancedSystemTest {
    @Nested
    @DisplayName("RestfulApi Tests")
    class RestfulApiTests {
        @Test
        @DisplayName("RestfulApiCard construction with valid fields")
        void testRestfulApiCardConstruction() {
            RestfulApiCard card =
                RestfulApiCard.builder().id("test_api").name("Test API").description("A test REST API tool")
                        .url("https://api.example.com/test").method("GET").timeout(30).build();

            assertNotNull(card);
            assertEquals("test_api", card.getId());
            assertEquals("GET", card.getMethod());
            assertEquals(30, card.getTimeout());
            assertEquals("https://api.example.com/test", card.getUrl());
            System.out.println("[RestfulApiCard] Created: " + card.getId());
        }

        @Test
        @DisplayName("RestfulApiCard default values")
        void testRestfulApiCardDefaults() {
            RestfulApiCard card =
                RestfulApiCard.builder().id("default_api").url("https://api.example.com/default").build();

            assertEquals("POST", card.getMethod());
            assertEquals(60, card.getTimeout());
            assertNotNull(card.getHeaders());
            assertNotNull(card.getQueries());
        }

        @Test
        @DisplayName("RestfulApi rejects invalid URL scheme")
        void testRestfulApiRejectsInvalidUrlScheme() {
            RestfulApiCard card = RestfulApiCard.builder().id("bad_api").name("Bad API")
                    .url("ftp://bad.example.com/test").method("GET").build();

            assertThrows(Exception.class, () -> new RestfulApi(card), "Construction should fail for non-http(s) URL");
        }

        @Test
        @DisplayName("RestfulApi rejects unsupported HTTP method")
        void testRestfulApiRejectsUnsupportedMethod() {
            RestfulApiCard card = RestfulApiCard.builder().id("bad_method_api").name("Bad Method API")
                    .url("https://httpbin.org/get").method("TRACE").build();

            assertThrows(Exception.class, () -> new RestfulApi(card),
                    "Construction should fail for unsupported method");
        }
    }

    @Nested
    @DisplayName("McpToolCard Tests")
    class McpToolCardTests {
        @Test
        @DisplayName("McpToolCard construction with server info")
        void testMcpToolCardConstruction() {
            McpToolCard card = McpToolCard.builder().id("mcp_tool_1").name("MCP Search")
                    .description("Search tool via MCP").serverName("search-server").serverId("srv_001").build();

            assertNotNull(card);
            assertEquals("mcp_tool_1", card.getId());
            assertEquals("search-server", card.getServerName());
            assertEquals("srv_001", card.getServerId());
            System.out.println("[McpToolCard] ServerName: " + card.getServerName());
        }

        @Test
        @DisplayName("McpToolCard generates McpToolInfo")
        void testMcpToolCardToolInfo() {
            McpToolCard card = McpToolCard.builder().id("mcp_tool_2").name("MCP Calculator")
                    .description("Calculator via MCP protocol").serverName("calc-server").serverId("srv_002").build();

            assertNotNull(card.toolInfo());
            System.out.println("[McpToolCard ToolInfo] " + card.toolInfo());
        }
    }

    @Nested
    @DisplayName("ParserRegistry Tests")
    class ParserRegistryTests {
        @Test
        @DisplayName("ParserRegistry singleton instance")
        void testParserRegistrySingleton() {
            ParserRegistry registry1 = ParserRegistry.getInstance();
            ParserRegistry registry2 = ParserRegistry.getInstance();
            assertNotNull(registry1);
            assertTrue(registry1 == registry2, "Should be singleton");
        }

        @Test
        @DisplayName("ParserRegistry parses JSON content")
        void testParserRegistryJsonParse() {
            ParserRegistry registry = ParserRegistry.getInstance();

            byte[] jsonBytes = "{\"status\":\"ok\",\"count\":42}".getBytes(StandardCharsets.UTF_8);
            Map<String, String> headers = Map.of("content-type", "application/json");

            Object result = registry.parse(headers, jsonBytes, 200);
            assertNotNull(result, "JSON should be parseable");
            System.out.println("[ParserRegistry JSON] Parsed: " + result);
        }

        @Test
        @DisplayName("ParserRegistry parses text content")
        void testParserRegistryTextParse() {
            ParserRegistry registry = ParserRegistry.getInstance();

            byte[] textBytes = "Hello World".getBytes(StandardCharsets.UTF_8);
            Map<String, String> headers = Map.of("content-type", "text/plain");

            Object result = registry.parse(headers, textBytes, 200);
            assertNotNull(result, "Text should be parseable");
            System.out.println("[ParserRegistry Text] Parsed: " + result);
        }
    }
}

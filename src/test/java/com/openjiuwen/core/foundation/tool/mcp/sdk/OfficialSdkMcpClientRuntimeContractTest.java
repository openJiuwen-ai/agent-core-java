/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.foundation.tool.mcp.sdk;

import com.openjiuwen.core.foundation.tool.mcp.McpServerConfig;
import com.openjiuwen.core.foundation.tool.mcp.McpToolCard;
import com.openjiuwen.core.foundation.tool.mcp.sdk.support.LocalOfficialMcpHttpFixture;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OfficialSdkMcpClientRuntimeContractTest {

    private static final float TEST_TIMEOUT_SECONDS = 2.0f;

    @Test
    @DisplayName("listTools keeps raw toolName and stable schema semantics")
    void listToolsKeepsRawToolNameAndStableSchemaSemantics() throws Exception {
        try (LocalOfficialMcpHttpFixture fixture = LocalOfficialMcpHttpFixture.start(LocalOfficialMcpHttpFixture.Mode.SUCCESS)) {
            OfficialSdkMcpClient client = createClient(fixture, LocalOfficialMcpHttpFixture.Mode.SUCCESS);
            client.connect(0, TEST_TIMEOUT_SECONDS);

            List<Object> tools = client.listTools(TEST_TIMEOUT_SECONDS);

            assertEquals(1, tools.size());
            assertInstanceOf(McpToolCard.class, tools.get(0));
            McpToolCard card = (McpToolCard) tools.get(0);
            assertEquals("fixture_http_tool", card.getName());
            assertFalse(card.getName().contains("server-1.fixture-server.fixture_http_tool"));
            assertEquals("string", ((Map<?, ?>) ((Map<?, ?>) card.getInputParams().get("properties")).get("city")).get("type"));
            assertEquals(List.of("city"), card.getInputParams().get("required"));
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("successfulCallToolModes")
    @DisplayName("callTool success results use one stable normalized map contract")
    void callToolSuccessResultsUseOneStableNormalizedMapContract(String ignoredCaseName,
                                                                 LocalOfficialMcpHttpFixture.Mode mode,
                                                                 String expectedText,
                                                                 Map<String, Object> expectedStructuredContent,
                                                                 List<Map<String, Object>> expectedContent) throws Exception {
        try (LocalOfficialMcpHttpFixture fixture = LocalOfficialMcpHttpFixture.start(mode)) {
            OfficialSdkMcpClient client = createClient(fixture, mode);
            client.connect(0, TEST_TIMEOUT_SECONDS);

            Object result = client.callTool("fixture_http_tool", Map.of("city", "北京"), TEST_TIMEOUT_SECONDS);

            assertInstanceOf(Map.class, result);
            @SuppressWarnings("unchecked")
            Map<String, Object> resultMap = (Map<String, Object>) result;
            assertEquals(List.of("tool_name", "text", "content", "structured_content", "is_error"), List.copyOf(resultMap.keySet()));
            assertEquals("fixture_http_tool", resultMap.get("tool_name"));
            assertEquals(expectedText, resultMap.get("text"));
            assertEquals(false, resultMap.get("is_error"));
            assertEquals(expectedStructuredContent, resultMap.get("structured_content"));
            assertEquals(expectedContent, resultMap.get("content"));
        }
    }

    @Test
    @DisplayName("missing tool diagnostics stay distinguishable from schema mismatch")
    void missingToolDiagnosticsStayDistinguishableFromSchemaMismatch() throws Exception {
        try (LocalOfficialMcpHttpFixture fixture = LocalOfficialMcpHttpFixture.start(LocalOfficialMcpHttpFixture.Mode.CALL_UNKNOWN_TOOL)) {
            OfficialSdkMcpClient client = createClient(fixture, LocalOfficialMcpHttpFixture.Mode.CALL_UNKNOWN_TOOL);
            client.connect(0, TEST_TIMEOUT_SECONDS);

            RuntimeException error = assertThrows(RuntimeException.class,
                    () -> client.callTool("missing_tool", Map.of("city", "北京"), TEST_TIMEOUT_SECONDS));

            assertTrue(error.getMessage().contains("tool_missing") || error.getMessage().contains("missing_tool"));
            assertFalse(error.getMessage().contains("SCHEMA_VALIDATE_INVALID"));
            assertFalse(error.getMessage().contains("SCHEMA_FORMAT_INVALID"));
        }
    }

    private static Stream<org.junit.jupiter.params.provider.Arguments> successfulCallToolModes() {
        return Stream.of(
                org.junit.jupiter.params.provider.Arguments.of(
                        "text result",
                        LocalOfficialMcpHttpFixture.Mode.SUCCESS_TEXT_RESULT,
                        "fixture ok",
                        Map.of(),
                        List.of(Map.of("type", "text", "text", "fixture ok"))
                ),
                org.junit.jupiter.params.provider.Arguments.of(
                        "structured result",
                        LocalOfficialMcpHttpFixture.Mode.SUCCESS_STRUCTURED_RESULT,
                        "weather for 北京\ntemperature is 22℃",
                        Map.of("location", "北京", "temperature", "22℃"),
                        List.of(
                                Map.of("type", "text", "text", "weather for 北京"),
                                Map.of("type", "text", "text", "temperature is 22℃")
                        )
                )
        );
    }

    private OfficialSdkMcpClient createClient(LocalOfficialMcpHttpFixture fixture, LocalOfficialMcpHttpFixture.Mode mode) {
        McpServerConfig config = McpServerConfig.builder()
                .serverId("server-1")
                .serverName("fixture-server")
                .serverPath(fixture.streamableHttpUrl())
                .clientType("streamable-http")
                .build();
        return new OfficialSdkMcpClient(config, OfficialMcpClientFactory.map(config));
    }
}

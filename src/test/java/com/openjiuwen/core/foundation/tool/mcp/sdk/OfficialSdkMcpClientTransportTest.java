/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.foundation.tool.mcp.sdk;

import com.openjiuwen.core.foundation.tool.mcp.McpServerConfig;
import com.openjiuwen.core.foundation.tool.mcp.McpToolCard;
import com.openjiuwen.core.foundation.tool.mcp.sdk.support.LocalOfficialMcpHttpFixture;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OfficialSdkMcpClientTransportTest {

    @Test
    @DisplayName("stdio fixture preserves params command args env cwd through official adapter")
    void stdioFixturePreservesLegacyFacadeShape() throws Exception {
        Path cwd = Files.createTempDirectory("official-mcp-stdio-fixture");
        McpServerConfig config = McpServerConfig.builder()
                .serverId("stdio-server")
                .serverName("stdio-fixture")
                .serverPath("java")
                .clientType("stdio")
                .params(Map.of(
                        "command", "java",
                        "args", List.of("-cp", System.getProperty("java.class.path"),
                                "com.openjiuwen.core.foundation.tool.mcp.sdk.support.OfficialMcpFixtureMain",
                                "arg-one", "arg-two"),
                        "env", Map.of("STDIO_FIXTURE_TOKEN", "fixture-token"),
                        "cwd", cwd.toString()
                ))
                .build();

        OfficialSdkMcpClient client = new OfficialSdkMcpClient(config, OfficialMcpClientFactory.map(config));

        assertTrue(client.connect(1, 2.0f));
        List<Object> tools = client.listTools(2.0f);

        assertEquals(1, tools.size());
        McpToolCard toolCard = assertInstanceOf(McpToolCard.class, tools.get(0));
        assertEquals("fixture_stdio_tool", toolCard.getName());
        assertTrue(toolCard.getDescription().contains("STDIO_FIXTURE_TOKEN") || toolCard.getDescription().contains("fixture-token"));
        assertTrue(toolCard.getDescription().contains("arg-one,arg-two"));
        assertTrue(toolCard.getDescription().contains(cwd.toString()));
    }

    @Test
    @DisplayName("clientType sse fails honestly when official transport cannot complete readiness")
    void clientTypeSseFailsHonestlyWithoutLegacyFallback() throws Exception {
        try (LocalOfficialMcpHttpFixture fixture = LocalOfficialMcpHttpFixture.start(LocalOfficialMcpHttpFixture.Mode.SUCCESS)) {
            McpServerConfig config = baseHttpConfig(fixture.sseUrl(), "sse");
            OfficialSdkMcpClient client = new OfficialSdkMcpClient(config, OfficialMcpClientFactory.map(config));

            OfficialSdkMcpClient.TransportException exception = assertThrows(OfficialSdkMcpClient.TransportException.class,
                    () -> client.connect(1, 0.3f));

            assertEquals(OfficialSdkMcpClient.ReadyStage.INITIALIZE, exception.getStage());
            assertFalse(exception.getMessage().contains("SseClient"));
        }
    }

    @Test
    @DisplayName("initialize and tools list failures expose stage aware transport exceptions")
    void failuresExposeStageAwareTransportExceptions() throws Exception {
        try (LocalOfficialMcpHttpFixture initializeFixture = LocalOfficialMcpHttpFixture.start(LocalOfficialMcpHttpFixture.Mode.FAIL_INITIALIZE)) {
            McpServerConfig initializeConfig = baseHttpConfig(initializeFixture.streamableHttpUrl(), "streamable-http");
            OfficialSdkMcpClient initializeClient = new OfficialSdkMcpClient(initializeConfig,
                    OfficialMcpClientFactory.map(initializeConfig));

            OfficialSdkMcpClient.TransportException initializeException = assertThrows(OfficialSdkMcpClient.TransportException.class,
                    () -> initializeClient.connect(1, 0.3f));
            assertEquals(OfficialSdkMcpClient.ReadyStage.INITIALIZE, initializeException.getStage());
        }

        try (LocalOfficialMcpHttpFixture listFixture = LocalOfficialMcpHttpFixture.start(LocalOfficialMcpHttpFixture.Mode.FAIL_LIST_TOOLS)) {
            McpServerConfig listConfig = baseHttpConfig(listFixture.streamableHttpUrl(), "streamable-http");
            OfficialSdkMcpClient listClient = new OfficialSdkMcpClient(listConfig, OfficialMcpClientFactory.map(listConfig));
            assertTrue(listClient.connect(1, 0.3f));

            OfficialSdkMcpClient.TransportException listException = assertThrows(OfficialSdkMcpClient.TransportException.class,
                    () -> listClient.listTools(0.3f));
            assertEquals(OfficialSdkMcpClient.ReadyStage.LIST_TOOLS, listException.getStage());
        }
    }

    @Test
    @DisplayName("timeout failures expose stage aware exception and clear half connected client")
    void timeoutFailuresExposeStageAwareExceptionAndClearClient() throws Exception {
        try (LocalOfficialMcpHttpFixture fixture = LocalOfficialMcpHttpFixture.start(LocalOfficialMcpHttpFixture.Mode.TIMEOUT_INITIALIZE)) {
            McpServerConfig config = baseHttpConfig(fixture.streamableHttpUrl(), "streamable-http");
            OfficialSdkMcpClient client = new OfficialSdkMcpClient(config, OfficialMcpClientFactory.map(config));

            OfficialSdkMcpClient.TransportException exception = assertThrows(OfficialSdkMcpClient.TransportException.class,
                    () -> client.connect(1, 0.1f));

            assertEquals(OfficialSdkMcpClient.ReadyStage.INITIALIZE, exception.getStage());
            assertTrue(exception.isTimeout());
            OfficialSdkMcpClient.TransportException listToolsAfterCleanup = assertThrows(OfficialSdkMcpClient.TransportException.class,
                    () -> client.listTools(0.1f));
            assertEquals(OfficialSdkMcpClient.ReadyStage.LIST_TOOLS, listToolsAfterCleanup.getStage());
            assertTrue(listToolsAfterCleanup.getCause() instanceof IllegalStateException);
        }
    }

    private McpServerConfig baseHttpConfig(String serverPath, String clientType) {
        return McpServerConfig.builder()
                .serverId(clientType + "-server")
                .serverName(clientType + "-fixture")
                .serverPath(serverPath)
                .clientType(clientType)
                .build();
    }
}

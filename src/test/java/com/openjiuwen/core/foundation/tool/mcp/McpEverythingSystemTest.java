/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.tool.mcp;

import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.runner.resourcemanager.ToolManager;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.Transferable;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@Tag("system-test")
@Testcontainers(disabledWithoutDocker = true)
class McpEverythingSystemTest {
    private static final DockerImageName IMAGE = DockerImageName.parse("mcp/everything");
    private static final String STREAMABLE_HTTP_SERVER = """
            import express from "express";
            import { randomUUID } from "node:crypto";
            import { StreamableHTTPServerTransport } from "/app/node_modules/@modelcontextprotocol/sdk/dist/esm/server/streamableHttp.js";
            import { isInitializeRequest } from "/app/node_modules/@modelcontextprotocol/sdk/dist/esm/types.js";
            import { createServer } from "/app/dist/everything.js";

            const app = express();
            app.use(express.json());
            const sessions = {};

            app.all("/mcp", async (req, res) => {
              try {
                const sessionId = req.headers["mcp-session-id"];
                let entry = sessionId ? sessions[sessionId] : undefined;
                if (!entry && req.method === "POST" && isInitializeRequest(req.body)) {
                  const lifecycle = createServer();
                  const transport = new StreamableHTTPServerTransport({
                    sessionIdGenerator: () => randomUUID(),
                    onsessioninitialized: id => {
                      sessions[id] = { transport, lifecycle };
                    }
                  });
                  transport.onclose = async () => {
                    const id = transport.sessionId;
                    if (id) {
                      delete sessions[id];
                    }
                    await lifecycle.cleanup();
                    await lifecycle.server.close();
                  };
                  await lifecycle.server.connect(transport);
                  entry = { transport, lifecycle };
                }
                if (!entry) {
                  res.status(400).json({
                    jsonrpc: "2.0",
                    error: { code: -32000, message: "Bad Request: No valid session ID provided" },
                    id: null
                  });
                  return;
                }
                await entry.transport.handleRequest(req, res, req.body);
              } catch (error) {
                console.error("Streamable HTTP everything wrapper error", error);
                if (!res.headersSent) {
                  res.status(500).json({
                    jsonrpc: "2.0",
                    error: { code: -32603, message: "Internal server error" },
                    id: null
                  });
                }
              }
            });

            const PORT = process.env.PORT || 3001;
            app.listen(PORT, () => console.log(`Streamable HTTP everything server is running on port ${PORT}`));
            """;
    private final ToolManager toolManager = new ToolManager();
    private String stdioContainerName;

    @AfterEach
    void cleanup() throws Exception {
        if (stdioContainerName != null) {
            forceRemoveContainer(stdioContainerName);
        }
    }

    @Test
    @Timeout(90)
    void streamableHttpRegistersAndInvokesEchoTool() throws Exception {
        assumeDocker();
        try (GenericContainer<?> container = streamableHttpContainer()) {
            container.start();
            String url = "http://" + container.getHost() + ":" + container.getMappedPort(3001) + "/mcp";
            assertEchoWorks(McpServerConfig.builder()
                    .serverId("everything-streamable-http")
                    .serverName("everything")
                    .serverPath(url)
                    .clientType("streamable-http")
                    .build());
        }
    }

    @Test
    @Timeout(90)
    void sseRegistersAndInvokesEchoToolWithUppercaseClientType() throws Exception {
        assumeDocker();
        try (GenericContainer<?> container = sseContainer()) {
            container.start();
            String url = "http://" + container.getHost() + ":" + container.getMappedPort(3001) + "/sse";
            assertEchoWorks(McpServerConfig.builder()
                    .serverId("everything-sse")
                    .serverName("everything")
                    .serverPath(url)
                    .clientType("SSE")
                    .build());
        }
    }

    @Test
    @Timeout(90)
    void stdioRegistersAndInvokesEchoToolThroughDockerProcess() throws Exception {
        assumeDocker();
        stdioContainerName = "agent-core-mcp-everything-" + UUID.randomUUID().toString().replace("-", "");
        assertEchoWorks(McpServerConfig.builder()
                .serverId("everything-stdio")
                .serverName("everything")
                .serverPath("stdio")
                .clientType("stdio")
                .params(Map.of("command", "docker", "args", List.of(
                        "run", "-i", "--rm", "--name", stdioContainerName, "mcp/everything"
                )))
                .build());
    }

    private void assertEchoWorks(McpServerConfig config) throws Exception {
        Exception primaryException = null;
        AssertionError primaryAssertion = null;
        try {
            List<McpToolCard> cards = toolManager.addToolServer(config, 60.0D).toCompletableFuture().join();
            assertThat(cards).extracting(McpToolCard::getName).contains("echo");
            Tool tool = toolManager.getMcpTool("echo", config.getServerId(), null);
            assertThat(tool).isInstanceOf(McpTool.class);
            Object result = tool.invoke(Map.of("message", "hello-mcp-everything"));
            assertThat(String.valueOf(result)).contains("hello-mcp-everything");
        } catch (Exception error) {
            primaryException = error;
            throw error;
        } catch (AssertionError error) {
            primaryAssertion = error;
            throw error;
        } finally {
            try {
                toolManager.removeToolServer(config.getServerId(), true).toCompletableFuture().join();
            } catch (Exception cleanupError) {
                if (primaryException != null) {
                    primaryException.addSuppressed(cleanupError);
                } else if (primaryAssertion != null) {
                    primaryAssertion.addSuppressed(cleanupError);
                } else {
                    throw cleanupError;
                }
            }
        }
    }

    private static GenericContainer<?> streamableHttpContainer() {
        return baseEverythingContainer()
                .withCopyToContainer(
                        Transferable.of(STREAMABLE_HTTP_SERVER.getBytes(StandardCharsets.UTF_8)),
                        "/app/streamable-http.mjs"
                )
                .withCommand("node", "/app/streamable-http.mjs");
    }

    private static GenericContainer<?> sseContainer() {
        return baseEverythingContainer()
                .withCommand("node", "dist/sse.js");
    }

    private static GenericContainer<?> baseEverythingContainer() {
        return new GenericContainer<>(IMAGE)
                .withEnv("PORT", "3001")
                .withExposedPorts(3001)
                .waitingFor(Wait.forListeningPort().withStartupTimeout(Duration.ofSeconds(60)));
    }

    private static void assumeDocker() {
        assumeTrue(DockerClientFactory.instance().isDockerAvailable(),
                "Docker is required for mcp/everything system test");
    }

    private static void forceRemoveContainer(String containerName) throws IOException, InterruptedException {
        if (containerName == null || containerName.isBlank()) {
            return;
        }
        if (!containerName.startsWith("agent-core-mcp-everything-")) {
            throw new IllegalArgumentException("Refusing to remove unexpected Docker container: " + containerName);
        }
        Process process = new ProcessBuilder("docker", "rm", "-f", containerName)
                .redirectErrorStream(true)
                .start();
        if (!process.waitFor(10, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            throw new IOException("Timed out removing Docker container: " + containerName);
        }
        int exitCode = process.exitValue();
        if (exitCode != 0) {
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            String lowerOutput = output.toLowerCase(Locale.ROOT);
            if (lowerOutput.contains("no such container") || lowerOutput.contains("no such object")) {
                return;
            }
            throw new IOException("Failed to remove Docker container " + containerName
                    + " (exit " + exitCode + "): " + output);
        }
    }
}

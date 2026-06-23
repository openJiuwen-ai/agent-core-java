/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.browser_move.playwright_runtime;

import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.foundation.tool.mcp.McpServerConfig;
import com.openjiuwen.core.single_agent.AbilityManager;
import com.openjiuwen.harness.tools.browser_move.clients.BrowserMoveStreamableHttpClient;
import com.openjiuwen.harness.tools.browser_move.utils.BrowserMoveEnv;
import com.openjiuwen.harness.tools.browser_move.utils.ParsingUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * <p>Mirrors Python's {@code tests/unit_tests/harness/tools/browser_move/test_config.py}.</p>
 */
class BrowserMoveConfigPythonParityTest {

    @TempDir
    private Path tempDir;

    @Test
    void buildRuntimeSettingsUsesSharedDefaults() {
        RuntimeSettings settings = BrowserRuntimeConfig.buildRuntimeSettings(Map.of());

        assertEquals("openai", settings.getProvider());
        assertEquals("", settings.getApiKey());
        assertEquals("https://api.openai.com/v1", settings.getApiBase());
        assertEquals(BrowserMoveEnv.DEFAULT_MODEL_NAME, settings.getModelName());
        assertEquals(BrowserMoveEnv.DEFAULT_BROWSER_TIMEOUT_S, settings.getGuardrails().getTimeoutSeconds());
        assertEquals(BrowserMoveEnv.DEFAULT_BROWSER_TIMEOUT_S, settings.getMcpConfig().getParams().get("timeout_s"));
    }

    @Test
    void buildRuntimeSettingsRespectsEnvOverrides() {
        RuntimeSettings settings = BrowserRuntimeConfig.buildRuntimeSettings(Map.of(
                "MODEL_PROVIDER", "openrouter",
                "OPENROUTER_API_KEY", "test-key",
                "MODEL_NAME", "google/gemini-3.1-pro-preview",
                "BROWSER_TIMEOUT_S", "45",
                "PLAYWRIGHT_MCP_ARGS", "[\"-y\", \"@playwright/mcp@latest\", \"--headless\"]"
        ));

        assertEquals("openrouter", settings.getProvider());
        assertEquals("test-key", settings.getApiKey());
        assertEquals("google/gemini-3.1-pro-preview", settings.getModelName());
        assertEquals(45, settings.getGuardrails().getTimeoutSeconds());
        assertEquals(List.of("-y", "@playwright/mcp@latest", "--headless"),
                settings.getMcpConfig().getParams().get("args"));
        assertEquals(45, settings.getMcpConfig().getParams().get("timeout_s"));
    }

    @Test
    void parseCommandArgsAcceptsJsonList() {
        assertEquals(List.of("-y", "@playwright/mcp@latest"),
                BrowserRuntimeConfig.parseCommandArgs("[\"-y\", \"@playwright/mcp@latest\"]"));
    }

    @Test
    void extractJsonObjectHandlesFencedJson() {
        assertEquals(Map.of("ok", true, "value", 1),
                ParsingUtils.extractJsonObject("```json\n{\"ok\": true, \"value\": 1}\n```"));
    }

    @Test
    void buildBrowserRuntimeMcpConfigDisabledByDefault() {
        assertNull(BrowserTools.buildBrowserRuntimeMcpConfig(Map.of()));
    }

    @Test
    void buildBrowserRuntimeMcpConfigStreamableHttpDefaults() {
        McpServerConfig cfg = BrowserTools.buildBrowserRuntimeMcpConfig(Map.of(
                "PLAYWRIGHT_RUNTIME_MCP_ENABLED", "1",
                "PLAYWRIGHT_RUNTIME_MCP_CLIENT_TYPE", "http",
                "PLAYWRIGHT_RUNTIME_MCP_HOST", "127.0.0.1",
                "PLAYWRIGHT_RUNTIME_MCP_PORT", "8940"
        ));

        assertNotNull(cfg);
        assertEquals("streamable-http", cfg.getClientType());
        assertEquals("http://127.0.0.1:8940/mcp", cfg.getServerPath());
    }

    @Test
    void buildBrowserRuntimeMcpConfigStdioDefaults() {
        McpServerConfig cfg = BrowserTools.buildBrowserRuntimeMcpConfig(Map.of(
                "PLAYWRIGHT_RUNTIME_MCP_ENABLED", "1",
                "PLAYWRIGHT_RUNTIME_MCP_CLIENT_TYPE", "stdio"
        ));

        assertNotNull(cfg);
        assertEquals("stdio", cfg.getClientType());
        assertEquals("stdio://playwright-runtime-wrapper", cfg.getServerPath());
        assertEquals(Path.of("").toAbsolutePath().normalize().toString(), cfg.getParams().get("cwd"));
        @SuppressWarnings("unchecked")
        List<String> args = (List<String>) cfg.getParams().get("args");
        assertEquals(List.of("-m", "openjiuwen.harness.tools.browser_move.playwright_runtime_mcp_server"),
                args.subList(0, 2));
        assertTrue(args.contains("--transport"));
        assertTrue(args.contains("stdio"));
        assertTrue(args.contains("--no-banner"));
        assertTrue(args.contains("--log-level"));
        assertTrue(args.contains("ERROR"));
    }

    @Test
    void buildBrowserRuntimeMcpConfigSupportsLegacyEnvNames() {
        McpServerConfig cfg = BrowserTools.buildBrowserRuntimeMcpConfig(Map.of(
                "BROWSER_RUNTIME_MCP_ENABLED", "1",
                "BROWSER_RUNTIME_MCP_CLIENT_TYPE", "streamable_http",
                "BROWSER_RUNTIME_MCP_HOST", "localhost",
                "BROWSER_RUNTIME_MCP_PORT", "9999",
                "BROWSER_RUNTIME_MCP_PATH", "custom"
        ));

        assertNotNull(cfg);
        assertEquals("streamable-http", cfg.getClientType());
        assertEquals("http://localhost:9999/custom", cfg.getServerPath());
    }

    @Test
    void buildBrowserRuntimeMcpConfigStdioPassesChildEnvAndMapsOpenai() {
        McpServerConfig cfg = BrowserTools.buildBrowserRuntimeMcpConfig(Map.of(
                "PLAYWRIGHT_RUNTIME_MCP_ENABLED", "1",
                "PLAYWRIGHT_RUNTIME_MCP_CLIENT_TYPE", "stdio",
                "API_KEY", "openai-key",
                "API_BASE", "https://api.openai.com/v1",
                "MODEL_PROVIDER", "OpenAI",
                "PLAYWRIGHT_MCP_CDP_ENDPOINT", "http://127.0.0.1:9333",
                "HTTP_PROXY", "http://127.0.0.1:9"
        ));

        assertNotNull(cfg);
        assertEquals("stdio", cfg.getClientType());
        @SuppressWarnings("unchecked")
        Map<String, String> childEnv = (Map<String, String>) cfg.getParams().get("env");
        assertEquals("openai-key", childEnv.get("API_KEY"));
        assertEquals("openai-key", childEnv.get("OPENAI_API_KEY"));
        assertEquals("https://api.openai.com/v1", childEnv.get("OPENAI_BASE_URL"));
        assertEquals("openai", childEnv.get("MODEL_PROVIDER"));
        assertEquals("http://127.0.0.1:9333", childEnv.get("PLAYWRIGHT_MCP_CDP_ENDPOINT"));
        assertFalse(childEnv.containsKey("HTTP_PROXY"));
    }

    @Test
    void buildBrowserRuntimeMcpConfigStdioUsesExplicitRuntimeCwd() {
        McpServerConfig cfg = BrowserTools.buildBrowserRuntimeMcpConfig(Map.of(
                "PLAYWRIGHT_RUNTIME_MCP_ENABLED", "1",
                "PLAYWRIGHT_RUNTIME_MCP_CLIENT_TYPE", "stdio",
                "PLAYWRIGHT_RUNTIME_MCP_CWD", tempDir.toString()
        ));

        assertNotNull(cfg);
        assertEquals(tempDir.toAbsolutePath().normalize().toString(), cfg.getParams().get("cwd"));
    }

    @Test
    void buildBrowserRuntimeMcpConfigStdioMapsOpenrouterEnv() {
        McpServerConfig cfg = BrowserTools.buildBrowserRuntimeMcpConfig(Map.of(
                "PLAYWRIGHT_RUNTIME_MCP_ENABLED", "1",
                "PLAYWRIGHT_RUNTIME_MCP_CLIENT_TYPE", "stdio",
                "API_KEY", "openrouter-key",
                "API_BASE", "https://openrouter.ai/api/v1"
        ));

        assertNotNull(cfg);
        @SuppressWarnings("unchecked")
        Map<String, String> childEnv = (Map<String, String>) cfg.getParams().get("env");
        assertEquals("openrouter-key", childEnv.get("OPENROUTER_API_KEY"));
        assertEquals("https://openrouter.ai/api/v1", childEnv.get("OPENROUTER_BASE_URL"));
    }

    @Test
    void browserMoveStreamableHttpClientAcceptsConfigConstructor() {
        McpServerConfig cfg = McpServerConfig.builder()
                .serverId("playwright-runtime-wrapper")
                .serverName("playwright-runtime-wrapper")
                .serverPath("http://127.0.0.1:8940/mcp")
                .clientType("streamable-http")
                .build();
        BrowserMoveStreamableHttpClient client = new BrowserMoveStreamableHttpClient(cfg);

        assertEquals("http://127.0.0.1:8940/mcp", client.getServerPath());
        assertEquals("playwright-runtime-wrapper", client.getName());
    }

    @Test
    void executeWrapperRaisesTimeoutError() {
        PlaywrightAgents.CompatibleToolExecutor wrapped = PlaywrightAgents.ensureExecuteSignatureCompat(
                (ctx, toolCall, session, tag) -> new CompletableFuture<>(),
                Map.of("PLAYWRIGHT_TOOL_TIMEOUT_S", "0.05")
        );
        ToolCall toolCall = ToolCall.builder().name("browser_run_task").arguments("{}").build();

        CompletionException error = assertThrows(CompletionException.class,
                () -> wrapped.execute(new Object(), toolCall, new Object(), null).toCompletableFuture().join());

        assertTrue(error.getCause().getMessage().contains("tool_execution_timeout:"));
        assertTrue(error.getCause().getMessage().contains("browser_run_task"));
        assertTrue(error.getCause().getMessage().contains("timeout_s="));
    }

    @Test
    void executeWrapperDropsNoneToolArguments() {
        AtomicReference<String> seenArguments = new AtomicReference<>();
        PlaywrightAgents.CompatibleToolExecutor wrapped = PlaywrightAgents.ensureExecuteSignatureCompat(
                recordingExecutor(seenArguments),
                Map.of()
        );
        ToolCall toolCall = ToolCall.builder()
                .name("browser_snapshot")
                .arguments("{\"filename\": null, \"depth\": null}")
                .build();

        wrapped.execute(new Object(), toolCall, new Object(), null).toCompletableFuture().join();

        assertEquals(Map.of(), ParsingUtils.extractJsonObject(seenArguments.get()));
    }

    @Test
    void executeWrapperPreservesNonNoneToolArguments() {
        AtomicReference<String> seenArguments = new AtomicReference<>();
        PlaywrightAgents.CompatibleToolExecutor wrapped = PlaywrightAgents.ensureExecuteSignatureCompat(
                recordingExecutor(seenArguments),
                Map.of()
        );
        ToolCall toolCall = ToolCall.builder()
                .name("browser_snapshot")
                .arguments("{\"filename\": \"snapshot.md\", \"depth\": 10}")
                .build();

        wrapped.execute(new Object(), toolCall, new Object(), null).toCompletableFuture().join();

        assertEquals(Map.of("filename", "snapshot.md", "depth", 10),
                ParsingUtils.extractJsonObject(seenArguments.get()));
    }

    @Test
    void localBrowserRuntimeServerLogsAreWrittenUnderRuntimeLogDir() throws Exception {
        AtomicReference<BrowserTools.LocalServerCommand> captured = new AtomicReference<>();
        BrowserTools.LocalServerLauncher launcher = command -> {
            captured.set(command);
            return new FakeProcess();
        };

        String url = BrowserTools.startLocalServer(
                "streamable-http",
                "127.0.0.1",
                8940,
                "/mcp",
                launcher,
                Map.of("PLAYWRIGHT_RUNTIME_LOG_DIR", tempDir.toString())
        );

        assertEquals("http://127.0.0.1:8940/mcp", url);
        assertTrue(Files.exists(tempDir.resolve("browser_runtime_stdout.log")));
        assertTrue(Files.exists(tempDir.resolve("browser_runtime_stderr.log")));
        assertEquals(Path.of("").toAbsolutePath().normalize(), captured.get().cwd());
        BrowserTools.stopLocalBrowserRuntimeServer();
        assertFalse(BrowserTools.hasOpenLogHandles());
    }

    private static PlaywrightAgents.CompatibleToolExecutor recordingExecutor(AtomicReference<String> seenArguments) {
        return (ctx, toolCall, session, tag) -> {
            seenArguments.set(toolCall.getArguments());
            return CompletableFuture.completedFuture(List.of(new AbilityManager.ExecutionResult(
                    Map.of("ok", true),
                    null
            )));
        };
    }

    private static final class FakeProcess extends Process {
        private boolean alive = true;

        @Override
        public OutputStream getOutputStream() {
            return OutputStream.nullOutputStream();
        }

        @Override
        public InputStream getInputStream() {
            return InputStream.nullInputStream();
        }

        @Override
        public InputStream getErrorStream() {
            return InputStream.nullInputStream();
        }

        @Override
        public int waitFor() {
            alive = false;
            return 0;
        }

        @Override
        public boolean waitFor(long timeout, TimeUnit unit) {
            alive = false;
            return true;
        }

        @Override
        public int exitValue() {
            return 0;
        }

        @Override
        public void destroy() {
            alive = false;
        }

        @Override
        public Process destroyForcibly() {
            alive = false;
            return this;
        }

        @Override
        public boolean isAlive() {
            return alive;
        }
    }
}

/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.harness.tools.browser_move;

import com.openjiuwen.core.foundation.tool.mcp.McpServerConfig;
import com.openjiuwen.harness.tools.browser_move.clients.BrowserMoveStreamableHttpClient;
import com.openjiuwen.harness.tools.browser_move.playwright_runtime.BrowserRuntimeConfig;
import com.openjiuwen.harness.tools.browser_move.playwright_runtime.BrowserRuntimeMcpSupport;
import com.openjiuwen.harness.tools.browser_move.playwright_runtime.RuntimeSettings;
import com.openjiuwen.harness.tools.browser_move.utils.EnvUtils;
import com.openjiuwen.harness.tools.browser_move.utils.ParsingUtils;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for browser_move config and MCP compatibility plumbing.
 *
 * <p>Mirrors Python's {@code test_config.py} in
 * {@code tests.unit_tests.harness.tools.browser_move}.</p>
 */
class TestConfig {

    private static final List<String> MCP_KEYS = List.of(
            "PLAYWRIGHT_RUNTIME_MCP_ENABLED",
            "BROWSER_RUNTIME_MCP_ENABLED",
            "PLAYWRIGHT_RUNTIME_MCP_CLIENT_TYPE",
            "BROWSER_RUNTIME_MCP_CLIENT_TYPE",
            "PLAYWRIGHT_RUNTIME_MCP_HOST",
            "BROWSER_RUNTIME_MCP_HOST",
            "PLAYWRIGHT_RUNTIME_MCP_PORT",
            "BROWSER_RUNTIME_MCP_PORT",
            "PLAYWRIGHT_RUNTIME_MCP_PATH",
            "BROWSER_RUNTIME_MCP_PATH",
            "PLAYWRIGHT_RUNTIME_MCP_CWD",
            "BROWSER_RUNTIME_MCP_CWD",
            "PLAYWRIGHT_RUNTIME_MCP_TIMEOUT_S",
            "BROWSER_RUNTIME_MCP_TIMEOUT_S",
            "API_KEY",
            "API_BASE",
            "MODEL_PROVIDER",
            "PLAYWRIGHT_MCP_CDP_ENDPOINT",
            "HTTP_PROXY"
    );

    @TempDir
    Path tempDir;

    @Test
    @Tag("level0")
    void testBuildRuntimeSettingsUsesSharedDefaults() {
        RuntimeSettings settings = BrowserRuntimeConfig.buildRuntimeSettings(Map.of());

        assertEquals("openai", settings.provider());
        assertEquals("", settings.apiKey());
        assertEquals("https://api.openai.com/v1", settings.apiBase());
        assertEquals(BrowserRuntimeConfig.DEFAULT_MODEL_NAME, settings.modelName());
        assertEquals(BrowserRuntimeConfig.DEFAULT_BROWSER_TIMEOUT_S, settings.guardrails().getTimeoutS());
        assertEquals(BrowserRuntimeConfig.DEFAULT_BROWSER_TIMEOUT_S, settings.mcpCfg().getParams().get("timeout_s"));
    }

    @Test
    @Tag("level0")
    void testBuildRuntimeSettingsRespectsEnvOverrides() {
        RuntimeSettings settings = BrowserRuntimeConfig.buildRuntimeSettings(Map.of(
                "MODEL_PROVIDER", "openrouter",
                "OPENROUTER_API_KEY", "test-key",
                "MODEL_NAME", "google/gemini-3.1-pro-preview",
                "BROWSER_TIMEOUT_S", "45",
                "PLAYWRIGHT_MCP_ARGS", "[\"-y\", \"@playwright/mcp@latest\", \"--headless\"]"
        ));

        assertEquals("openrouter", settings.provider());
        assertEquals("test-key", settings.apiKey());
        assertEquals("google/gemini-3.1-pro-preview", settings.modelName());
        assertEquals(45, settings.guardrails().getTimeoutS());
        assertEquals(List.of("-y", "@playwright/mcp@latest", "--headless"), settings.mcpCfg().getParams().get("args"));
        assertEquals(45, settings.mcpCfg().getParams().get("timeout_s"));
    }

    @Test
    @Tag("level0")
    void testParseCommandArgsAcceptsJsonList() {
        assertEquals(List.of("-y", "@playwright/mcp@latest"),
                EnvUtils.parseCommandArgs("[\"-y\", \"@playwright/mcp@latest\"]"));
    }

    @Test
    @Tag("level0")
    void testExtractJsonObjectHandlesFencedJson() {
        assertEquals(Map.of("ok", true, "value", 1),
                ParsingUtils.extractJsonObject("```json\n{\"ok\": true, \"value\": 1}\n```"));
    }

    @Test
    @Tag("level0")
    void testBuildBrowserRuntimeMcpConfigDisabledByDefault() {
        withProperties(Map.of("PLAYWRIGHT_RUNTIME_MCP_ENABLED", "0"), () ->
                assertNull(BrowserRuntimeMcpSupport.buildBrowserRuntimeMcpConfig()));
    }

    @Test
    @Tag("level0")
    void testBuildBrowserRuntimeMcpConfigStreamableHttpDefaults() {
        withProperties(Map.of(
                "PLAYWRIGHT_RUNTIME_MCP_ENABLED", "1",
                "PLAYWRIGHT_RUNTIME_MCP_CLIENT_TYPE", "http",
                "PLAYWRIGHT_RUNTIME_MCP_HOST", "127.0.0.1",
                "PLAYWRIGHT_RUNTIME_MCP_PORT", "8940"
        ), () -> {
            McpServerConfig cfg = BrowserRuntimeMcpSupport.buildBrowserRuntimeMcpConfig();

            assertNotNull(cfg);
            assertEquals("streamable-http", cfg.getClientType());
            assertEquals("http://127.0.0.1:8940/mcp", cfg.getServerPath());
        });
    }

    @Test
    @Tag("level0")
    void testBuildBrowserRuntimeMcpConfigStdioDefaults() {
        withProperties(Map.of(
                "PLAYWRIGHT_RUNTIME_MCP_ENABLED", "1",
                "PLAYWRIGHT_RUNTIME_MCP_CLIENT_TYPE", "stdio"
        ), () -> {
            McpServerConfig cfg = BrowserRuntimeMcpSupport.buildBrowserRuntimeMcpConfig();

            assertNotNull(cfg);
            assertEquals("stdio", cfg.getClientType());
            assertEquals("stdio://playwright-runtime-wrapper", cfg.getServerPath());
            assertEquals(Path.of("").toAbsolutePath().toString(), cfg.getParams().get("cwd"));
            @SuppressWarnings("unchecked")
            List<String> args = (List<String>) cfg.getParams().get("args");
            assertEquals(List.of("-m", "openjiuwen.harness.tools.browser_move.playwright_runtime_mcp_server"),
                    args.subList(0, 2));
            assertTrue(args.contains("--transport"));
            assertTrue(args.contains("stdio"));
            assertTrue(args.contains("--no-banner"));
            assertTrue(args.contains("--log-level"));
            assertTrue(args.contains("ERROR"));
        });
    }

    @Test
    @Tag("level0")
    void testBuildBrowserRuntimeMcpConfigSupportsLegacyEnvNames() {
        withProperties(Map.of(
                "BROWSER_RUNTIME_MCP_ENABLED", "1",
                "BROWSER_RUNTIME_MCP_CLIENT_TYPE", "streamable_http",
                "BROWSER_RUNTIME_MCP_HOST", "localhost",
                "BROWSER_RUNTIME_MCP_PORT", "9999",
                "BROWSER_RUNTIME_MCP_PATH", "custom"
        ), () -> {
            McpServerConfig cfg = BrowserRuntimeMcpSupport.buildBrowserRuntimeMcpConfig();

            assertNotNull(cfg);
            assertEquals("streamable-http", cfg.getClientType());
            assertEquals("http://localhost:9999/custom", cfg.getServerPath());
        });
    }

    @Test
    @Tag("level0")
    void testBuildBrowserRuntimeMcpConfigStdioPassesChildEnvAndMapsOpenai() {
        withProperties(Map.of(
                "PLAYWRIGHT_RUNTIME_MCP_ENABLED", "1",
                "PLAYWRIGHT_RUNTIME_MCP_CLIENT_TYPE", "stdio",
                "API_KEY", "openai-key",
                "API_BASE", "https://api.openai.com/v1",
                "MODEL_PROVIDER", "OpenAI",
                "PLAYWRIGHT_MCP_CDP_ENDPOINT", "http://127.0.0.1:9333",
                "HTTP_PROXY", "http://127.0.0.1:9"
        ), () -> {
            McpServerConfig cfg = BrowserRuntimeMcpSupport.buildBrowserRuntimeMcpConfig();

            assertNotNull(cfg);
            @SuppressWarnings("unchecked")
            Map<String, String> childEnv = (Map<String, String>) cfg.getParams().get("env");
            assertEquals("openai-key", childEnv.get("API_KEY"));
            assertEquals("openai-key", childEnv.get("OPENAI_API_KEY"));
            assertEquals("https://api.openai.com/v1", childEnv.get("OPENAI_BASE_URL"));
            assertEquals("openai", childEnv.get("MODEL_PROVIDER"));
            assertEquals("http://127.0.0.1:9333", childEnv.get("PLAYWRIGHT_MCP_CDP_ENDPOINT"));
            assertFalse(childEnv.containsKey("HTTP_PROXY"));
        });
    }

    @Test
    @Tag("level0")
    void testBuildBrowserRuntimeMcpConfigStdioUsesExplicitRuntimeCwd() {
        withProperties(Map.of(
                "PLAYWRIGHT_RUNTIME_MCP_ENABLED", "1",
                "PLAYWRIGHT_RUNTIME_MCP_CLIENT_TYPE", "stdio",
                "PLAYWRIGHT_RUNTIME_MCP_CWD", tempDir.toString()
        ), () -> {
            McpServerConfig cfg = BrowserRuntimeMcpSupport.buildBrowserRuntimeMcpConfig();

            assertNotNull(cfg);
            assertEquals(tempDir.toAbsolutePath().toString(), cfg.getParams().get("cwd"));
        });
    }

    @Test
    @Tag("level0")
    void testBuildBrowserRuntimeMcpConfigStdioMapsOpenrouterEnv() {
        withProperties(Map.of(
                "PLAYWRIGHT_RUNTIME_MCP_ENABLED", "1",
                "PLAYWRIGHT_RUNTIME_MCP_CLIENT_TYPE", "stdio",
                "API_KEY", "openrouter-key",
                "API_BASE", "https://openrouter.ai/api/v1"
        ), () -> {
            McpServerConfig cfg = BrowserRuntimeMcpSupport.buildBrowserRuntimeMcpConfig();

            assertNotNull(cfg);
            @SuppressWarnings("unchecked")
            Map<String, String> childEnv = (Map<String, String>) cfg.getParams().get("env");
            assertEquals("openrouter-key", childEnv.get("OPENROUTER_API_KEY"));
            assertEquals("https://openrouter.ai/api/v1", childEnv.get("OPENROUTER_BASE_URL"));
        });
    }

    @Test
    @Tag("level0")
    void testBrowserMoveStreamableHttpClientAcceptsConfigConstructor() {
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
    @Tag("level0")
    void testParseCommandArgsAcceptsQuotedShellString() {
        assertEquals(List.of("-y", "@playwright/mcp@latest", "--browser", "chrome stable"),
                EnvUtils.parseCommandArgs("-y @playwright/mcp@latest --browser \"chrome stable\""));
    }

    @Test
    @Tag("level0")
    void testBuildRuntimeSettingsRespectsGuardrailOverrides() {
        RuntimeSettings settings = BrowserRuntimeConfig.buildRuntimeSettings(Map.of(
                "BROWSER_GUARDRAIL_MAX_STEPS", "9",
                "BROWSER_GUARDRAIL_MAX_FAILURES", "1",
                "BROWSER_GUARDRAIL_RETRY_ONCE", "false",
                "BROWSER_GUARDRAIL_RESUME_ON_MAX_ITERATIONS", "true"
        ));

        assertEquals(9, settings.guardrails().getMaxSteps());
        assertEquals(1, settings.guardrails().getMaxFailures());
        assertFalse(settings.guardrails().isRetryOnce());
        assertTrue(settings.guardrails().isResumeOnMaxIterations());
    }

    @Test
    @Tag("level0")
    void testExtractJsonObjectHandlesEmbeddedObject() {
        assertEquals(Map.of("done", true),
                ParsingUtils.extractJsonObject("prefix {\"done\": true} suffix"));
    }

    @Test
    @Tag("level0")
    void testBuildBrowserRuntimeMcpConfigRejectsUnsupportedClientType() {
        withProperties(Map.of(
                "PLAYWRIGHT_RUNTIME_MCP_ENABLED", "1",
                "PLAYWRIGHT_RUNTIME_MCP_CLIENT_TYPE", "bad-client"
        ), () -> assertThrows(
                IllegalArgumentException.class,
                BrowserRuntimeMcpSupport::buildBrowserRuntimeMcpConfig));
    }

    @Test
    @Tag("level0")
    void testBuildRuntimeSettingsModelProviderAliases() {
        RuntimeSettings settings = BrowserRuntimeConfig.buildRuntimeSettings(Map.of(
                "MODEL_PROVIDER", "silicon_flow",
                "SILICONFLOW_API_KEY", "sf-key"
        ));

        assertEquals("siliconflow", settings.provider());
        assertEquals("sf-key", settings.apiKey());
        assertEquals("https://api.siliconflow.cn/v1", settings.apiBase());
    }

    private static void withProperties(Map<String, String> values, Runnable runnable) {
        Map<String, String> oldValues = new LinkedHashMap<>();
        for (String key : MCP_KEYS) {
            oldValues.put(key, System.getProperty(key));
            System.clearProperty(key);
        }
        values.forEach(System::setProperty);
        try {
            runnable.run();
        } finally {
            for (String key : MCP_KEYS) {
                String old = oldValues.get(key);
                if (old == null) {
                    System.clearProperty(key);
                } else {
                    System.setProperty(key, old);
                }
            }
        }
    }
}

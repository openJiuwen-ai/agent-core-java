package com.openjiuwen.harness.tools.browser_move;

import com.openjiuwen.core.runner.base.Error;
import com.openjiuwen.core.runner.base.Result;
import com.openjiuwen.core.foundation.tool.mcp.McpServerConfig;
import com.openjiuwen.harness.DeepAgent;
import com.openjiuwen.harness.HarnessFactory;
import com.openjiuwen.harness.tools.browser_move.playwright_runtime.BrowserRuntimeMcpSupport;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BrowserMoveRegistrationTest {

    @org.junit.jupiter.api.AfterEach
    void clearProperties() {
        System.clearProperty("PLAYWRIGHT_RUNTIME_MCP_ENABLED");
        System.clearProperty("PLAYWRIGHT_RUNTIME_MCP_CLIENT_TYPE");
        System.clearProperty("PLAYWRIGHT_RUNTIME_MCP_HOST");
        System.clearProperty("PLAYWRIGHT_RUNTIME_MCP_PORT");
        System.clearProperty("PLAYWRIGHT_RUNTIME_MCP_PATH");
        System.clearProperty("PLAYWRIGHT_RUNTIME_MCP_CWD");
        System.clearProperty("BROWSER_RUNTIME_MCP_ENABLED");
        System.clearProperty("BROWSER_RUNTIME_MCP_CLIENT_TYPE");
        System.clearProperty("BROWSER_RUNTIME_MCP_HOST");
        System.clearProperty("BROWSER_RUNTIME_MCP_PORT");
        System.clearProperty("BROWSER_RUNTIME_MCP_PATH");
        System.clearProperty("API_KEY");
        System.clearProperty("API_BASE");
        System.clearProperty("MODEL_PROVIDER");
        System.clearProperty("OPENROUTER_API_KEY");
        System.clearProperty("OPENROUTER_BASE_URL");
        System.clearProperty("OPENAI_API_KEY");
        System.clearProperty("OPENAI_BASE_URL");
        System.clearProperty("PLAYWRIGHT_MCP_CDP_ENDPOINT");
        System.clearProperty("HTTP_PROXY");
        System.clearProperty("HTTPS_PROXY");
        System.clearProperty("ALL_PROXY");
    }

    @Test
    void packageEntryExposesRepoRootAndNoopLifecycleHooks() {
        assertTrue(BrowserMove.REPO_ROOT.toAbsolutePath().toString().length() > 0);
        assertNull(BrowserMove.restartLocalBrowserRuntimeServer());
        BrowserMove.stopLocalBrowserRuntimeServer();
    }

    @Test
    void restartLocalBrowserRuntimeServerReturnsConfiguredUrlWhenEnabled() {
        System.setProperty("PLAYWRIGHT_RUNTIME_MCP_ENABLED", "true");
        System.setProperty("PLAYWRIGHT_RUNTIME_MCP_CLIENT_TYPE", "streamable-http");
        System.setProperty("PLAYWRIGHT_RUNTIME_MCP_HOST", "127.0.0.1");
        System.setProperty("PLAYWRIGHT_RUNTIME_MCP_PORT", "8940");
        System.setProperty("PLAYWRIGHT_RUNTIME_MCP_PATH", "/mcp");

        try {
            setLastBrowserRuntimeUrl("http://127.0.0.1:8939/mcp");
            String url = BrowserMove.restartLocalBrowserRuntimeServer();
            assertTrue(String.valueOf(url).contains("127.0.0.1:8940/mcp"));
        } finally {
            BrowserMove.stopLocalBrowserRuntimeServer();
        }
    }

    @Test
    void restartLocalBrowserRuntimeServerReturnsNullWhenNoPriorLocalServerExists() {
        System.setProperty("PLAYWRIGHT_RUNTIME_MCP_ENABLED", "true");
        System.setProperty("PLAYWRIGHT_RUNTIME_MCP_CLIENT_TYPE", "streamable-http");
        System.setProperty("PLAYWRIGHT_RUNTIME_MCP_HOST", "127.0.0.1");
        System.setProperty("PLAYWRIGHT_RUNTIME_MCP_PORT", "8940");
        System.setProperty("PLAYWRIGHT_RUNTIME_MCP_PATH", "/mcp");

        String url = BrowserMove.restartLocalBrowserRuntimeServer();

        assertNull(url);
    }

    @Test
    void buildBrowserRuntimeMcpConfigReturnsNullWhenFeatureDisabled() {
        assertNull(BrowserMove.buildBrowserRuntimeMcpConfig());
    }

    @Test
    void browserMoveClientPatchDetectionMatchesPlaywrightNaming() {
        McpServerConfig config = McpServerConfig.builder()
                .serverId("playwright_runtime_wrapper")
                .serverName("playwright-runtime-wrapper")
                .serverPath("stdio://playwright")
                .clientType("stdio")
                .build();

        assertTrue(BrowserRuntimeMcpSupport.shouldUseBrowserMoveClientPatch(config));
        assertFalse(BrowserRuntimeMcpSupport.shouldUseBrowserMoveClientPatch(
                McpServerConfig.builder().serverId("other").serverName("other").serverPath("stdio://other").clientType("stdio").build()
        ));
    }

    @Test
    void buildBrowserRuntimeMcpConfigNormalizesHttpClientType() {
        System.setProperty("PLAYWRIGHT_RUNTIME_MCP_ENABLED", "true");
        System.setProperty("PLAYWRIGHT_RUNTIME_MCP_CLIENT_TYPE", "streamable_http");
        System.setProperty("PLAYWRIGHT_RUNTIME_MCP_HOST", "127.0.0.1");
        System.setProperty("PLAYWRIGHT_RUNTIME_MCP_PORT", "8940");
        System.setProperty("PLAYWRIGHT_RUNTIME_MCP_PATH", "/mcp");

        try {
            McpServerConfig config = BrowserMove.buildBrowserRuntimeMcpConfig();
            assertEquals("streamable-http", config.getClientType());
            assertTrue(config.getServerPath().contains("127.0.0.1:8940/mcp"));
        } finally {
            BrowserMove.stopLocalBrowserRuntimeServer();
        }
    }

    @Test
    void buildBrowserRuntimeMcpConfigSupportsBrowserPrefixProperties() {
        System.setProperty("BROWSER_RUNTIME_MCP_ENABLED", "true");
        System.setProperty("BROWSER_RUNTIME_MCP_CLIENT_TYPE", "http");
        System.setProperty("BROWSER_RUNTIME_MCP_HOST", "127.0.0.1");
        System.setProperty("BROWSER_RUNTIME_MCP_PORT", "9012");
        System.setProperty("BROWSER_RUNTIME_MCP_PATH", "/bridge");

        McpServerConfig config = BrowserMove.buildBrowserRuntimeMcpConfig();

        assertEquals("streamable-http", config.getClientType());
        assertTrue(config.getServerPath().contains("127.0.0.1:9012/bridge"));
    }

    @Test
    void buildBrowserRuntimeMcpConfigStdioDefaultsMirrorPythonShape() {
        System.setProperty("PLAYWRIGHT_RUNTIME_MCP_ENABLED", "true");
        System.setProperty("PLAYWRIGHT_RUNTIME_MCP_CLIENT_TYPE", "stdio");

        McpServerConfig config = BrowserMove.buildBrowserRuntimeMcpConfig();

        assertEquals("stdio", config.getClientType());
        assertEquals("stdio://playwright-runtime-wrapper", config.getServerPath());
        Map<String, Object> params = config.getParams();
        assertEquals("java", params.get("command"));
        assertTrue(String.valueOf(params.get("cwd")).length() > 0);
        @SuppressWarnings("unchecked")
        List<String> args = (List<String>) params.get("args");
        assertEquals(List.of("-m", "openjiuwen.harness.tools.browser_move.playwright_runtime_mcp_server"), args.subList(0, 2));
        assertTrue(args.contains("--transport"));
        assertTrue(args.contains("stdio"));
        assertTrue(args.contains("--no-banner"));
        assertTrue(args.contains("--log-level"));
        assertTrue(args.contains("ERROR"));
    }

    @Test
    void buildBrowserRuntimeMcpConfigStdioPassesChildEnvAndMapsOpenAi() {
        System.setProperty("PLAYWRIGHT_RUNTIME_MCP_ENABLED", "true");
        System.setProperty("PLAYWRIGHT_RUNTIME_MCP_CLIENT_TYPE", "stdio");
        System.setProperty("API_KEY", "openai-key");
        System.setProperty("API_BASE", "https://api.openai.com/v1");
        System.setProperty("MODEL_PROVIDER", "OpenAI");
        System.setProperty("PLAYWRIGHT_MCP_CDP_ENDPOINT", "http://127.0.0.1:9333");
        System.setProperty("HTTP_PROXY", "http://127.0.0.1:9");

        McpServerConfig config = BrowserMove.buildBrowserRuntimeMcpConfig();

        @SuppressWarnings("unchecked")
        Map<String, String> env = (Map<String, String>) config.getParams().get("env");
        assertEquals("openai-key", env.get("API_KEY"));
        assertEquals("openai-key", env.get("OPENAI_API_KEY"));
        assertEquals("https://api.openai.com/v1", env.get("OPENAI_BASE_URL"));
        assertEquals("openai", env.get("MODEL_PROVIDER"));
        assertEquals("http://127.0.0.1:9333", env.get("PLAYWRIGHT_MCP_CDP_ENDPOINT"));
        assertFalse(env.containsKey("HTTP_PROXY"));
    }

    @Test
    void buildBrowserRuntimeMcpConfigStdioMapsOpenRouterEnv() {
        System.setProperty("PLAYWRIGHT_RUNTIME_MCP_ENABLED", "true");
        System.setProperty("PLAYWRIGHT_RUNTIME_MCP_CLIENT_TYPE", "stdio");
        System.setProperty("API_KEY", "openrouter-key");
        System.setProperty("API_BASE", "https://openrouter.ai/api/v1");

        McpServerConfig config = BrowserMove.buildBrowserRuntimeMcpConfig();

        @SuppressWarnings("unchecked")
        Map<String, String> env = (Map<String, String>) config.getParams().get("env");
        assertEquals("openrouter-key", env.get("OPENROUTER_API_KEY"));
        assertEquals("https://openrouter.ai/api/v1", env.get("OPENROUTER_BASE_URL"));
        assertEquals("openrouter", env.get("MODEL_PROVIDER"));
    }

    @Test
    void registerBrowserRuntimeMcpServerReturnsFalseWhenFeatureDisabled() throws Exception {
        DeepAgent agent = HarnessFactory.createDeepAgent();
        assertFalse(BrowserMove.registerBrowserRuntimeMcpServer(agent));
        assertEquals(0, agent.getDelegate().getAbilityManager().list().size());
    }

    @Test
    void finalizeRegistrationTreatsAlreadyExistErrorAsSuccess() {
        DeepAgent agent = HarnessFactory.createDeepAgent();
        McpServerConfig config = McpServerConfig.builder()
                .serverId("playwright_runtime_wrapper")
                .serverName("playwright-runtime-wrapper")
                .serverPath("stdio://playwright")
                .clientType("stdio")
                .build();

        boolean registered = BrowserRuntimeMcpSupport.finalizeRegistration(
                agent,
                config,
                List.<Result<String>>of(new Error<>(new IllegalStateException("mcp server already exist")))
        );

        assertTrue(registered);
        assertEquals(1, agent.getDelegate().getAbilityManager().list().size());
    }

    @Test
    void finalizeRegistrationReturnsFalseWhenResultsContainNoSuccessSignal() {
        DeepAgent agent = HarnessFactory.createDeepAgent();
        McpServerConfig config = McpServerConfig.builder()
                .serverId("playwright_runtime_wrapper")
                .serverName("playwright-runtime-wrapper")
                .serverPath("stdio://playwright")
                .clientType("stdio")
                .build();
        List<Result<String>> results = new java.util.ArrayList<>();
        results.add(new Error<>(new IllegalStateException("network timeout")));
        results.add(null);

        boolean registered = BrowserRuntimeMcpSupport.finalizeRegistration(
                agent,
                config,
                results
        );

        assertFalse(registered);
        assertEquals(0, agent.getDelegate().getAbilityManager().list().size());
    }

    private static void setLastBrowserRuntimeUrl(String value) {
        try {
            Field field = BrowserMove.class.getDeclaredField("lastBrowserRuntimeUrl");
            field.setAccessible(true);
            field.set(null, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to seed lastBrowserRuntimeUrl for test", e);
        }
    }
}

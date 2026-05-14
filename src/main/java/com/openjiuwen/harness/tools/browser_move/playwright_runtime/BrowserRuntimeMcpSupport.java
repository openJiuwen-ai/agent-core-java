package com.openjiuwen.harness.tools.browser_move.playwright_runtime;

import com.openjiuwen.core.foundation.tool.mcp.McpServerConfig;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.runner.base.Result;
import com.openjiuwen.harness.DeepAgent;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Mirrors Python's browser runtime MCP registration helpers in
 * {@code openjiuwen.harness.tools.browser_move.playwright_runtime.browser_tools}.
 */
public final class BrowserRuntimeMcpSupport {

    public static final String DEFAULT_SERVER_ID = "playwright_runtime_wrapper";
    public static final String DEFAULT_SERVER_NAME = "playwright-runtime-wrapper";
    private static final List<String> SUPPORTED_CLIENT_TYPES = List.of("stdio", "sse", "streamable-http", "streamable_http", "http");

    private BrowserRuntimeMcpSupport() {
    }

    public static McpServerConfig buildBrowserRuntimeMcpConfig() {
        if (!envBoolean("PLAYWRIGHT_RUNTIME_MCP_ENABLED", "BROWSER_RUNTIME_MCP_ENABLED")) {
            return null;
        }

        String serverId = envFirst(DEFAULT_SERVER_ID, "PLAYWRIGHT_RUNTIME_MCP_SERVER_ID", "BROWSER_RUNTIME_MCP_SERVER_ID");
        String serverName = envFirst(DEFAULT_SERVER_NAME, "PLAYWRIGHT_RUNTIME_MCP_SERVER_NAME", "BROWSER_RUNTIME_MCP_SERVER_NAME");
        String clientType = normalizeClientType(envFirst("streamable-http", "PLAYWRIGHT_RUNTIME_MCP_CLIENT_TYPE", "BROWSER_RUNTIME_MCP_CLIENT_TYPE"));
        if (!SUPPORTED_CLIENT_TYPES.contains(clientType)) {
            throw new IllegalArgumentException("PLAYWRIGHT_RUNTIME_MCP_CLIENT_TYPE must be stdio, sse, or streamable-http.");
        }

        if ("sse".equals(clientType)) {
            return McpServerConfig.builder()
                    .serverId(serverId)
                    .serverName(serverName)
                    .serverPath(envFirst(defaultServerUrl("sse"), "PLAYWRIGHT_RUNTIME_MCP_SERVER_PATH", "BROWSER_RUNTIME_MCP_SERVER_PATH"))
                    .clientType("sse")
                    .build();
        }
        if ("streamable-http".equals(clientType)) {
            return McpServerConfig.builder()
                    .serverId(serverId)
                    .serverName(serverName)
                    .serverPath(envFirst(defaultServerUrl("streamable-http"), "PLAYWRIGHT_RUNTIME_MCP_SERVER_PATH", "BROWSER_RUNTIME_MCP_SERVER_PATH"))
                    .clientType("streamable-http")
                    .build();
        }

        Map<String, Object> params = new HashMap<>();
        params.put("command", envFirst("java", "PLAYWRIGHT_RUNTIME_MCP_COMMAND", "BROWSER_RUNTIME_MCP_COMMAND"));
        params.put("cwd", envFirst(Path.of("").toAbsolutePath().toString(), "PLAYWRIGHT_RUNTIME_MCP_CWD", "BROWSER_RUNTIME_MCP_CWD"));
        params.put("args", List.of(
                "-m",
                "openjiuwen.harness.tools.browser_move.playwright_runtime_mcp_server",
                "--transport",
                "stdio",
                "--no-banner",
                "--log-level",
                "ERROR"
        ));
        Object timeout = BrowserRuntimeConfig.DEFAULT_BROWSER_TIMEOUT_S;
        String timeoutText = envFirst(String.valueOf(BrowserRuntimeConfig.DEFAULT_BROWSER_TIMEOUT_S), "PLAYWRIGHT_RUNTIME_MCP_TIMEOUT_S", "BROWSER_RUNTIME_MCP_TIMEOUT_S", "PLAYWRIGHT_TOOL_TIMEOUT_S");
        try {
            timeout = Integer.parseInt(timeoutText);
        } catch (NumberFormatException ignored) {
            timeout = BrowserRuntimeConfig.DEFAULT_BROWSER_TIMEOUT_S;
        }
        params.put("timeout_s", timeout);
        Map<String, String> childEnv = buildChildEnv();
        if (!childEnv.isEmpty()) {
            params.put("env", childEnv);
        }
        return McpServerConfig.builder()
                .serverId(serverId)
                .serverName(serverName)
                .serverPath(envFirst("stdio://playwright-runtime-wrapper", "PLAYWRIGHT_RUNTIME_MCP_SERVER_PATH", "BROWSER_RUNTIME_MCP_SERVER_PATH"))
                .clientType("stdio")
                .params(params)
                .build();
    }

    public static boolean registerBrowserRuntimeMcpServer(DeepAgent agent) throws Exception {
        return registerBrowserRuntimeMcpServer(agent, "agent.main");
    }

    public static boolean registerBrowserRuntimeMcpServer(DeepAgent agent, String tag) throws Exception {
        McpServerConfig cfg = buildBrowserRuntimeMcpConfig();
        if (cfg == null) {
            return false;
        }
        List<Result<String>> results = Runner.resourceMgr().addMcpServer(cfg, tag, null);
        return finalizeRegistration(agent, cfg, results);
    }

    public static boolean finalizeRegistration(DeepAgent agent, McpServerConfig cfg, List<Result<String>> results) {
        if (agent == null || cfg == null || results == null) {
            return false;
        }
        for (Result<String> result : results) {
            if (result == null) {
                continue;
            }
            if (result.isOk()) {
                agent.getDelegate().getAbilityManager().add(cfg);
                return true;
            }
            if (result.isError()) {
                Exception error = result.getError();
                String message = error == null ? "" : String.valueOf(error.getMessage()).toLowerCase(Locale.ROOT);
                if (message.contains("already exist")) {
                    agent.getDelegate().getAbilityManager().add(cfg);
                    return true;
                }
            }
        }
        return false;
    }

    public static boolean shouldUseBrowserMoveClientPatch(McpServerConfig config) {
        if (config == null) {
            return false;
        }
        String serverId = safeLower(config.getServerId());
        String serverName = safeLower(config.getServerName());
        return serverId.contains("playwright")
                || serverId.contains("browser_runtime")
                || serverName.contains("playwright")
                || serverName.contains("browser-runtime")
                || serverName.contains("browser_runtime");
    }

    private static String normalizeClientType(String clientType) {
        String value = safeLower(clientType);
        if ("http".equals(value) || "streamable_http".equals(value)) {
            return "streamable-http";
        }
        return value;
    }

    private static String defaultServerUrl(String transport) {
        String host = envFirst("127.0.0.1", "PLAYWRIGHT_RUNTIME_MCP_HOST", "BROWSER_RUNTIME_MCP_HOST");
        String port = envFirst("8940", "PLAYWRIGHT_RUNTIME_MCP_PORT", "BROWSER_RUNTIME_MCP_PORT");
        String path = "sse".equals(transport)
                ? envFirst("/sse", "PLAYWRIGHT_RUNTIME_MCP_PATH", "BROWSER_RUNTIME_MCP_PATH")
                : envFirst("/mcp", "PLAYWRIGHT_RUNTIME_MCP_PATH", "BROWSER_RUNTIME_MCP_PATH");
        return "http://" + host + ":" + port + path;
    }

    private static Map<String, String> buildChildEnv() {
        Map<String, String> env = new HashMap<>();
        copyIfPresent(env, "MODEL_PROVIDER");
        copyIfPresent(env, "API_KEY");
        copyIfPresent(env, "API_BASE");
        copyIfPresent(env, "OPENROUTER_API_KEY");
        copyIfPresent(env, "OPENROUTER_BASE_URL");
        copyIfPresent(env, "OPENAI_API_KEY");
        copyIfPresent(env, "OPENAI_BASE_URL");
        copyIfPresent(env, "PLAYWRIGHT_MCP_CDP_ENDPOINT");

        String apiKey = env.getOrDefault("API_KEY", "");
        String apiBase = env.getOrDefault("API_BASE", "");
        String modelProvider = env.getOrDefault("MODEL_PROVIDER", "").toLowerCase(Locale.ROOT);
        boolean openRouterBase = apiBase.contains("openrouter.ai");

        if (!apiKey.isBlank() && !env.containsKey("OPENROUTER_API_KEY") && openRouterBase) {
            env.put("OPENROUTER_API_KEY", apiKey);
        }
        if (!apiBase.isBlank() && !env.containsKey("OPENROUTER_BASE_URL") && openRouterBase) {
            env.put("OPENROUTER_BASE_URL", apiBase);
        }
        if (!apiKey.isBlank() && !env.containsKey("OPENAI_API_KEY") && !openRouterBase) {
            env.put("OPENAI_API_KEY", apiKey);
        }
        if (!apiBase.isBlank() && !env.containsKey("OPENAI_BASE_URL") && !openRouterBase) {
            env.put("OPENAI_BASE_URL", apiBase);
        }
        if (env.containsKey("OPENROUTER_API_KEY") || env.containsKey("OPENROUTER_BASE_URL")) {
            env.put("MODEL_PROVIDER", "openrouter");
        } else if (!modelProvider.isBlank()) {
            env.put("MODEL_PROVIDER", modelProvider);
        }

        env.remove("HTTP_PROXY");
        env.remove("HTTPS_PROXY");
        env.remove("ALL_PROXY");
        return env;
    }

    private static void copyIfPresent(Map<String, String> target, String key) {
        String value = envFirst("", key);
        if (!value.isBlank()) {
            target.put(key, value);
        }
    }

    private static boolean envBoolean(String... names) {
        String value = envFirst("", names);
        if (value.isBlank()) {
            return false;
        }
        return List.of("1", "true", "yes", "on").contains(safeLower(value));
    }

    private static String envFirst(String defaultValue, String... names) {
        for (String name : names) {
            String property = System.getProperty(name);
            if (property != null && !property.isBlank()) {
                return property.trim();
            }
            String value = System.getenv(name);
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return defaultValue;
    }

    private static String safeLower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }
}

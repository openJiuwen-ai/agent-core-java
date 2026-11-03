/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.browser_move.playwright_runtime;

import com.openjiuwen.core.foundation.tool.mcp.McpServerConfig;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Browser MCP integration helpers for playwright_runtime.
 *
 * <p>Mirrors Python's helpers in
 * {@code openjiuwen/harness/tools/browser_move/playwright_runtime/browser_tools.py}.</p>
 */
public final class BrowserTools {

    private static final String DEFAULT_SERVER_ID = "playwright_runtime_wrapper";
    private static final String DEFAULT_SERVER_NAME = "playwright-runtime-wrapper";
    private static final String STDOUT_LOG_NAME = "browser_runtime_stdout.log";
    private static final String STDERR_LOG_NAME = "browser_runtime_stderr.log";
    private static final Set<String> SUPPORTED_CLIENT_TYPES = Set.of(
            "stdio", "sse", "streamable-http", "streamable_http", "http");
    private static final Set<String> PROXY_BLOCKLIST = Set.of("http://127.0.0.1:9", "http://localhost:9");

    private static volatile boolean clientPatchApplied;
    private static Process localServerProcess;
    private static String localServerUrl;
    private static OutputStream browserRuntimeStdoutHandle;
    private static OutputStream browserRuntimeStderrHandle;

    private BrowserTools() {
    }

    public static void ensureBrowserRuntimeClientPatch() {
        clientPatchApplied = true;
    }

    public static boolean isClientPatchApplied() {
        return clientPatchApplied;
    }

    public static McpServerConfig buildBrowserRuntimeMcpConfig() {
        return buildBrowserRuntimeMcpConfig(System.getenv());
    }

    public static McpServerConfig buildBrowserRuntimeMcpConfig(Map<String, String> env) {
        if (!envBool(env, false, "PLAYWRIGHT_RUNTIME_MCP_ENABLED", "BROWSER_RUNTIME_MCP_ENABLED")) {
            return null;
        }

        String serverId = envFirst(env, DEFAULT_SERVER_ID,
                "PLAYWRIGHT_RUNTIME_MCP_SERVER_ID", "BROWSER_RUNTIME_MCP_SERVER_ID");
        String serverName = envFirst(env, DEFAULT_SERVER_NAME,
                "PLAYWRIGHT_RUNTIME_MCP_SERVER_NAME", "BROWSER_RUNTIME_MCP_SERVER_NAME");
        String clientType = normalizeClientType(envFirst(env, "streamable-http",
                "PLAYWRIGHT_RUNTIME_MCP_CLIENT_TYPE", "BROWSER_RUNTIME_MCP_CLIENT_TYPE"));
        if (!SUPPORTED_CLIENT_TYPES.contains(clientType)) {
            throw new IllegalArgumentException(
                    "PLAYWRIGHT_RUNTIME_MCP_CLIENT_TYPE must be stdio, sse, or streamable-http.");
        }

        if ("sse".equals(clientType)) {
            String serverPath = envFirst(env, buildServerUrl(env, "sse"),
                    "PLAYWRIGHT_RUNTIME_MCP_SERVER_PATH", "BROWSER_RUNTIME_MCP_SERVER_PATH");
            return McpServerConfig.builder()
                    .serverId(serverId)
                    .serverName(serverName)
                    .serverPath(serverPath)
                    .clientType("sse")
                    .build();
        }
        if ("streamable-http".equals(clientType)) {
            String serverPath = envFirst(env, buildServerUrl(env, "streamable-http"),
                    "PLAYWRIGHT_RUNTIME_MCP_SERVER_PATH", "BROWSER_RUNTIME_MCP_SERVER_PATH");
            return McpServerConfig.builder()
                    .serverId(serverId)
                    .serverName(serverName)
                    .serverPath(serverPath)
                    .clientType("streamable-http")
                    .build();
        }

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("command", envFirst(env, "python",
                "PLAYWRIGHT_RUNTIME_MCP_COMMAND", "BROWSER_RUNTIME_MCP_COMMAND"));
        String argsRaw = envFirst(env, "", "PLAYWRIGHT_RUNTIME_MCP_ARGS", "BROWSER_RUNTIME_MCP_ARGS");
        params.put("args", argsRaw.isBlank()
                ? List.of("-m", "openjiuwen.harness.tools.browser_move.playwright_runtime_mcp_server",
                "--transport", "stdio", "--no-banner", "--log-level", "ERROR")
                : BrowserRuntimeConfig.parseCommandArgs(argsRaw));
        params.put("cwd", runtimeCwd(env).toString());
        int timeout = parsePositiveInt(envFirst(env, "300",
                "PLAYWRIGHT_RUNTIME_MCP_TIMEOUT_S", "BROWSER_RUNTIME_MCP_TIMEOUT_S"), 300);
        if (timeout > 0) {
            params.put("timeout_s", timeout);
        }
        Map<String, String> childEnv = buildChildEnv(env);
        if (!childEnv.isEmpty()) {
            params.put("env", childEnv);
        }

        String serverPath = envFirst(env, "stdio://playwright-runtime-wrapper",
                "PLAYWRIGHT_RUNTIME_MCP_SERVER_PATH", "BROWSER_RUNTIME_MCP_SERVER_PATH");
        return McpServerConfig.builder()
                .serverId(serverId)
                .serverName(serverName)
                .serverPath(serverPath)
                .clientType("stdio")
                .params(params)
                .build();
    }

    public static boolean registerBrowserRuntimeMcpServer(Object agent, String tag) {
        ensureBrowserRuntimeClientPatch();
        return buildBrowserRuntimeMcpConfig() != null;
    }

    public static String restartLocalBrowserRuntimeServer() {
        stopLocalBrowserRuntimeServer();
        return null;
    }

    public static void stopLocalBrowserRuntimeServer() {
        Process process = localServerProcess;
        localServerProcess = null;
        localServerUrl = null;
        if (process != null && process.isAlive()) {
            process.destroy();
            try {
                if (!process.waitFor(5, TimeUnit.SECONDS)) {
                    process.destroyForcibly();
                    process.waitFor(2, TimeUnit.SECONDS);
                }
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                process.destroyForcibly();
            }
        }
        closeBrowserRuntimeLogHandles();
    }

    static String startLocalServer(
            String transport,
            String host,
            int port,
            String path,
            LocalServerLauncher launcher,
            Map<String, String> env
    ) throws IOException {
        String normalized = normalizeClientType(transport);
        if (!Set.of("sse", "streamable-http").contains(normalized)) {
            throw new IllegalArgumentException("Unsupported local server transport: " + transport);
        }
        Path stdoutLog = runtimeLogsDir(env).resolve(STDOUT_LOG_NAME);
        Path stderrLog = runtimeLogsDir(env).resolve(STDERR_LOG_NAME);
        closeBrowserRuntimeLogHandles();
        OutputStream stdout = Files.newOutputStream(stdoutLog, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        OutputStream stderr = Files.newOutputStream(stderrLog, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        try {
            List<String> commandLine = new ArrayList<>();
            commandLine.add(envFirst(env, "python", "PLAYWRIGHT_RUNTIME_MCP_COMMAND", "BROWSER_RUNTIME_MCP_COMMAND"));
            commandLine.add(serverScript().toString());
            commandLine.addAll(List.of(
                    "--transport", normalized,
                    "--host", host,
                    "--port", String.valueOf(port),
                    "--path", path,
                    "--no-banner"
            ));
            LocalServerCommand command = new LocalServerCommand(
                    commandLine,
                    runtimeCwd(env),
                    buildChildEnv(env),
                    stdout,
                    stderr
            );
            Process process = launcher.start(command);
            localServerProcess = process;
            browserRuntimeStdoutHandle = stdout;
            browserRuntimeStderrHandle = stderr;
            localServerUrl = "http://" + host + ":" + port + path;
            return localServerUrl;
        } catch (IOException | RuntimeException error) {
            stdout.close();
            stderr.close();
            throw error;
        }
    }

    static boolean hasOpenLogHandles() {
        return browserRuntimeStdoutHandle != null || browserRuntimeStderrHandle != null;
    }

    static Map<String, String> buildChildEnv(Map<String, String> env) {
        Map<String, String> childEnv = new LinkedHashMap<>();
        if (env != null) {
            childEnv.putAll(env);
        }
        for (String key : List.of(
                "MODEL_NAME",
                "MODEL_PROVIDER",
                "API_KEY",
                "API_BASE",
                "OPENROUTER_API_KEY",
                "OPENROUTER_BASE_URL",
                "OPENAI_API_KEY",
                "OPENAI_BASE_URL",
                "PLAYWRIGHT_MCP_COMMAND",
                "PLAYWRIGHT_MCP_ARGS",
                "PLAYWRIGHT_CDP_URL",
                "PLAYWRIGHT_CDP_HEADERS",
                "PLAYWRIGHT_MCP_CDP_ENDPOINT",
                "PLAYWRIGHT_MCP_CDP_TIMEOUT",
                "PLAYWRIGHT_MCP_BROWSER",
                "PLAYWRIGHT_MCP_DEVICE",
                "PLAYWRIGHT_BROWSERS_PATH",
                "PLAYWRIGHT_TOOL_TIMEOUT_S",
                "PLAYWRIGHT_RUNTIME_MCP_COMMAND",
                "PLAYWRIGHT_RUNTIME_MCP_ARGS",
                "PLAYWRIGHT_RUNTIME_MCP_SERVER_PATH",
                "PLAYWRIGHT_RUNTIME_MCP_TIMEOUT_S",
                "BROWSER_TIMEOUT_S",
                "HTTP_PROXY",
                "HTTPS_PROXY",
                "NO_PROXY"
        )) {
            String value = envValue(env, key);
            if (!value.isBlank()) {
                childEnv.put(key, value);
            }
        }

        String apiKey = envValue(env, "API_KEY");
        String apiBase = envValue(env, "API_BASE");
        String apiBaseLower = apiBase.toLowerCase(Locale.ROOT);
        String modelProvider = envValue(env, "MODEL_PROVIDER").toLowerCase(Locale.ROOT);
        if (!apiKey.isBlank() && childEnv.get("OPENROUTER_API_KEY") == null && apiBaseLower.contains("openrouter.ai")) {
            childEnv.put("OPENROUTER_API_KEY", apiKey);
        }
        if (!apiBase.isBlank() && childEnv.get("OPENROUTER_BASE_URL") == null && apiBaseLower.contains("openrouter.ai")) {
            childEnv.put("OPENROUTER_BASE_URL", apiBase);
        }
        if (!apiKey.isBlank() && childEnv.get("OPENAI_API_KEY") == null && !apiBaseLower.contains("openrouter.ai")) {
            childEnv.put("OPENAI_API_KEY", apiKey);
        }
        if (!apiBase.isBlank() && childEnv.get("OPENAI_BASE_URL") == null && !apiBaseLower.contains("openrouter.ai")) {
            childEnv.put("OPENAI_BASE_URL", apiBase);
        }
        if (Set.of("openai", "openrouter", "siliconflow").contains(modelProvider)) {
            childEnv.put("MODEL_PROVIDER", modelProvider);
        }
        for (String proxyKey : List.of("HTTP_PROXY", "HTTPS_PROXY", "ALL_PROXY")) {
            String proxyValue = childEnv.get(proxyKey);
            if (proxyValue != null && PROXY_BLOCKLIST.contains(proxyValue.trim().toLowerCase(Locale.ROOT))) {
                childEnv.remove(proxyKey);
            }
        }
        return childEnv;
    }

    private static Path runtimeLogsDir(Map<String, String> env) throws IOException {
        String configured = envFirst(env, "", "PLAYWRIGHT_RUNTIME_LOG_DIR", "BROWSER_RUNTIME_LOG_DIR");
        Path path = configured.isBlank() ? runtimeCwd(env).resolve("logs") : Path.of(configured).toAbsolutePath();
        Files.createDirectories(path);
        return path.normalize();
    }

    private static Path runtimeCwd(Map<String, String> env) {
        Path cwd = Path.of(BrowserRuntimeConfig.resolvePlaywrightMcpCwd(env)).toAbsolutePath().normalize();
        try {
            Files.createDirectories(cwd);
        } catch (IOException ignored) {
            // Python only needs best-effort directory preparation before process launch.
        }
        return cwd;
    }

    private static Path serverScript() {
        return Path.of("openjiuwen", "harness", "tools", "browser_move", "playwright_runtime_mcp_server.py")
                .toAbsolutePath()
                .normalize();
    }

    private static String normalizeClientType(String clientType) {
        String value = clientType == null ? "" : clientType.trim().toLowerCase(Locale.ROOT);
        if ("http".equals(value) || "streamable_http".equals(value)) {
            return "streamable-http";
        }
        return value;
    }

    private static String buildServerUrl(Map<String, String> env, String transport) {
        return "http://" + runtimeHost(env) + ":" + runtimePort(env) + runtimePath(env, transport);
    }

    private static String runtimeHost(Map<String, String> env) {
        return envFirst(env, "127.0.0.1", "PLAYWRIGHT_RUNTIME_MCP_HOST", "BROWSER_RUNTIME_MCP_HOST");
    }

    private static String runtimePort(Map<String, String> env) {
        return envFirst(env, "8940", "PLAYWRIGHT_RUNTIME_MCP_PORT", "BROWSER_RUNTIME_MCP_PORT");
    }

    private static String runtimePath(Map<String, String> env, String transport) {
        String defaultPath = "streamable-http".equals(normalizeClientType(transport)) ? "/mcp" : "/sse";
        String path = envFirst(env, defaultPath, "PLAYWRIGHT_RUNTIME_MCP_PATH", "BROWSER_RUNTIME_MCP_PATH");
        return path.startsWith("/") ? path : "/" + path;
    }

    private static boolean envBool(Map<String, String> env, boolean defaultValue, String... names) {
        String value = envFirst(env, "", names);
        if (value.isBlank()) {
            return defaultValue;
        }
        return List.of("1", "true", "yes", "on").contains(value.toLowerCase(Locale.ROOT));
    }

    private static String envFirst(Map<String, String> env, String defaultValue, String... names) {
        for (String name : names) {
            String value = envValue(env, name);
            if (!value.isBlank()) {
                return value;
            }
        }
        return defaultValue;
    }

    private static String envValue(Map<String, String> env, String key) {
        if (env == null || key == null) {
            return "";
        }
        String value = env.get(key);
        return value == null ? "" : value.trim();
    }

    private static int parsePositiveInt(String value, int fallback) {
        try {
            int parsed = Integer.parseInt(value);
            return parsed > 0 ? parsed : fallback;
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static void closeBrowserRuntimeLogHandles() {
        closeHandle(browserRuntimeStdoutHandle);
        closeHandle(browserRuntimeStderrHandle);
        browserRuntimeStdoutHandle = null;
        browserRuntimeStderrHandle = null;
    }

    private static void closeHandle(OutputStream handle) {
        if (handle == null) {
            return;
        }
        try {
            handle.close();
        } catch (IOException ignored) {
        }
    }

    @FunctionalInterface
    interface LocalServerLauncher {
        Process start(LocalServerCommand command) throws IOException;
    }

    record LocalServerCommand(
            List<String> commandLine,
            Path cwd,
            Map<String, String> env,
            OutputStream stdout,
            OutputStream stderr
    ) {
    }
}

/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.browser_move;

import com.openjiuwen.harness.tools.browser_move.controllers.ActionController;
import com.openjiuwen.harness.tools.browser_move.playwright_runtime.BrowserAgentRuntime;
import com.openjiuwen.harness.tools.browser_move.playwright_runtime.BrowserRuntimeConfig;
import com.openjiuwen.harness.tools.browser_move.playwright_runtime.RuntimeSettings;
import com.openjiuwen.harness.tools.browser_move.utils.BrowserMoveEnv;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.locks.ReentrantLock;

/**
 * MCP tool service wrapper for Playwright browser runtime.
 *
 * <p>Mirrors Python's {@code openjiuwen/harness/tools/browser_move/playwright_runtime_mcp_server.py}.</p>
 */
public final class PlaywrightRuntimeMcpServer {

    public static final String SERVER_NAME = "playwright-runtime-mcp";
    public static final String DEFAULT_HOST = "127.0.0.1";
    public static final int DEFAULT_PORT = 8940;
    public static final int DEFAULT_BROWSER_TIMEOUT_SECONDS = BrowserRuntimeConfig.DEFAULT_BROWSER_TIMEOUT_SECONDS;

    private static final ReentrantLock RUNTIME_LOCK = new ReentrantLock();
    private static RuntimeOperations runtime;

    static {
        BrowserMoveEnv.loadRepoDotenv(false);
    }

    private PlaywrightRuntimeMcpServer() {
    }

    public static void main(String[] args) {
        ServerArgs parsed = parseArgs(args == null ? new String[0] : args);
        applyTimeoutDefaults();
        if ("stdio".equals(parsed.transport())) {
            configureStdioLogging();
        }
        serverRunMetadata(parsed);
    }

    public static BrowserAgentRuntime buildRuntime() {
        RuntimeSettings settings = BrowserRuntimeConfig.buildRuntimeSettings();
        if (settings.getApiKey() == null || settings.getApiKey().isBlank()) {
            throw new IllegalStateException(BrowserMoveEnv.MISSING_API_KEY_MESSAGE);
        }
        return new BrowserAgentRuntime(
                settings.getProvider(),
                settings.getApiKey(),
                settings.getApiBase(),
                settings.getModelName(),
                settings.getMcpConfig(),
                settings.getGuardrails()
        );
    }

    public static RuntimeOperations getRuntime() {
        RuntimeOperations existing = runtime;
        if (existing != null) {
            return existing;
        }
        RUNTIME_LOCK.lock();
        try {
            if (runtime == null) {
                RuntimeOperations created = new BrowserAgentRuntimeOperations(buildRuntime());
                created.ensureStarted();
                bindRuntime(created);
                runtime = created;
            }
            return runtime;
        } finally {
            RUNTIME_LOCK.unlock();
        }
    }

    public static void shutdownRuntime() {
        RUNTIME_LOCK.lock();
        try {
            if (runtime != null) {
                runtime.shutdown();
                runtime = null;
            }
            ActionController.clearRuntimeRunnerForDefault();
        } finally {
            RUNTIME_LOCK.unlock();
        }
    }

    public static String resolveSessionId(String explicitSessionId, McpContext ctx) {
        String explicit = trimToEmpty(explicitSessionId);
        if (!explicit.isEmpty()) {
            return explicit;
        }
        return ctx == null ? "" : trimToEmpty(ctx.sessionId());
    }

    public static String resolveRequestId(String explicitRequestId, McpContext ctx) {
        String explicit = trimToEmpty(explicitRequestId);
        if (!explicit.isEmpty()) {
            return explicit;
        }
        return ctx == null ? "" : trimToEmpty(ctx.requestId());
    }

    public static Map<String, Object> browserRunTask(
            String task,
            String sessionId,
            String requestId,
            int timeoutSeconds,
            McpContext ctx
    ) {
        RuntimeOperations currentRuntime = getRuntime();
        String effectiveSessionId = resolveSessionId(sessionId, ctx);
        String effectiveRequestId = resolveRequestId(requestId, ctx);
        Integer timeout = timeoutSeconds > 0 ? timeoutSeconds : null;
        Map<String, Object> result = currentRuntime.runBrowserTask(
                task,
                emptyToNull(effectiveSessionId),
                emptyToNull(effectiveRequestId),
                timeout
        );
        Object screenshot = result.get("screenshot");
        if (screenshot instanceof String text && text.startsWith("data:")) {
            Map<String, Object> copy = new LinkedHashMap<>(result);
            copy.put("screenshot", "[screenshot saved]");
            return copy;
        }
        return result;
    }

    public static Map<String, Object> browserCancelTask(String sessionId, String requestId, McpContext ctx) {
        RuntimeOperations currentRuntime = getRuntime();
        String effectiveSessionId = resolveSessionId(sessionId, ctx);
        if (effectiveSessionId.isEmpty()) {
            throw new IllegalArgumentException("session_id is required for cancellation");
        }
        String effectiveRequestId = resolveRequestId(requestId, ctx);
        return currentRuntime.cancelRun(effectiveSessionId, emptyToNull(effectiveRequestId));
    }

    public static Map<String, Object> browserClearCancel(String sessionId, String requestId, McpContext ctx) {
        RuntimeOperations currentRuntime = getRuntime();
        String effectiveSessionId = resolveSessionId(sessionId, ctx);
        if (effectiveSessionId.isEmpty()) {
            throw new IllegalArgumentException("session_id is required to clear cancellation");
        }
        String effectiveRequestId = resolveRequestId(requestId, ctx);
        return currentRuntime.clearCancel(effectiveSessionId, emptyToNull(effectiveRequestId));
    }

    public static Map<String, Object> browserCustomAction(
            String action,
            String sessionId,
            String requestId,
            Map<String, Object> params,
            McpContext ctx
    ) {
        String effectiveSessionId = resolveSessionId(sessionId, ctx);
        String effectiveRequestId = resolveRequestId(requestId, ctx);
        Map<String, Object> result = ActionController.runActionForDefault(
                action,
                effectiveSessionId,
                effectiveRequestId,
                params == null ? Map.of() : params
        );
        Object error = result.get("error");
        if (error instanceof String text && text.startsWith("runtime_not_bound:")) {
            bindRuntime(getRuntime());
            return ActionController.runActionForDefault(
                    action,
                    effectiveSessionId,
                    effectiveRequestId,
                    params == null ? Map.of() : params
            );
        }
        return result;
    }

    public static Map<String, Object> browserListCustomActions() {
        ActionController.getDefaultController().registerBuiltinActions();
        return Map.of("ok", true, "actions", ActionController.listActionsForDefault());
    }

    public static Map<String, Object> browserRuntimeHealth() {
        RuntimeSettings settings = BrowserRuntimeConfig.buildRuntimeSettings();
        RuntimeOperations current = runtime;
        if (current == null) {
            return Map.of(
                    "ok", false,
                    "started", false,
                    "last_heartbeat_ok", false,
                    "provider", settings.getProvider(),
                    "api_base", settings.getApiBase(),
                    "model_name", settings.getModelName()
            );
        }
        return current.runtimeHealth();
    }

    public static ServerArgs parseArgs(String[] args) {
        String transport = envOrDefault("PLAYWRIGHT_RUNTIME_MCP_TRANSPORT", "stdio").trim().toLowerCase(Locale.ROOT);
        String host = envOrDefault("PLAYWRIGHT_RUNTIME_MCP_HOST", DEFAULT_HOST).trim();
        int port = parseInt(envOrDefault("PLAYWRIGHT_RUNTIME_MCP_PORT", String.valueOf(DEFAULT_PORT)), DEFAULT_PORT);
        String path = envOrDefault("PLAYWRIGHT_RUNTIME_MCP_PATH", "").trim();
        String logLevel = envOrDefault("PLAYWRIGHT_RUNTIME_MCP_LOG_LEVEL", "INFO").trim();
        boolean noBanner = false;
        boolean statelessHttp = false;

        List<String> values = new ArrayList<>(Arrays.asList(args == null ? new String[0] : args));
        for (int index = 0; index < values.size(); index++) {
            String item = values.get(index);
            switch (item) {
                case "--transport" -> transport = nextValue(values, ++index, transport).toLowerCase(Locale.ROOT);
                case "--host" -> host = nextValue(values, ++index, host);
                case "--port" -> port = parseInt(nextValue(values, ++index, String.valueOf(port)), port);
                case "--path" -> path = nextValue(values, ++index, path);
                case "--log-level" -> logLevel = nextValue(values, ++index, logLevel);
                case "--no-banner" -> noBanner = true;
                case "--stateless-http" -> statelessHttp = true;
                default -> {
                    // Ignore unknown flags to match argparse's command-line tolerance only for translated helper tests.
                }
            }
        }
        if (!List.of("stdio", "sse", "streamable-http", "http").contains(transport)) {
            throw new IllegalArgumentException("Unsupported transport: " + transport);
        }
        return new ServerArgs(transport, host, port, path, logLevel, noBanner, statelessHttp);
    }

    public static void applyTimeoutDefaults() {
        String timeoutText = String.valueOf(DEFAULT_BROWSER_TIMEOUT_SECONDS);
        setPropertyIfBlank("BROWSER_TIMEOUT_S", timeoutText);
        setPropertyIfBlank("PLAYWRIGHT_TOOL_TIMEOUT_S", System.getProperty("BROWSER_TIMEOUT_S", timeoutText));
    }

    public static boolean resolveStatelessHttp(ServerArgs args, String envRaw) {
        if (args != null && args.statelessHttp()) {
            return true;
        }
        String normalized = trimToEmpty(envRaw).toLowerCase(Locale.ROOT);
        if (List.of("1", "true", "yes", "on").contains(normalized)) {
            return true;
        }
        if (List.of("0", "false", "no", "off").contains(normalized)) {
            return false;
        }
        return args != null && List.of("streamable-http", "http").contains(args.transport());
    }

    public static Map<String, Object> serverRunMetadata(ServerArgs args) {
        ServerArgs resolved = args == null ? parseArgs(new String[0]) : args;
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("server", SERVER_NAME);
        metadata.put("transport", resolved.transport());
        metadata.put("host", resolved.host());
        metadata.put("port", resolved.port());
        metadata.put("path", resolved.path());
        metadata.put("log_level", resolved.logLevel());
        metadata.put("show_banner", !resolved.noBanner());
        metadata.put("stateless_http", resolveStatelessHttp(
                resolved,
                envOrDefault("PLAYWRIGHT_RUNTIME_MCP_STATELESS_HTTP", "")
        ));
        return metadata;
    }

    public static void setRuntimeForTesting(RuntimeOperations testRuntime) {
        runtime = testRuntime;
        if (testRuntime != null) {
            bindRuntime(testRuntime);
        }
    }

    public static void resetRuntimeForTesting() {
        runtime = null;
        ActionController.clearRuntimeRunnerForDefault();
    }

    public interface RuntimeOperations {
        void ensureStarted();

        void shutdown();

        Map<String, Object> runBrowserTask(String task, String sessionId, String requestId, Integer timeoutSeconds);

        Map<String, Object> cancelRun(String sessionId, String requestId);

        Map<String, Object> clearCancel(String sessionId, String requestId);

        Map<String, Object> runtimeHealth();
    }

    public record McpContext(String sessionId, String requestId) {
    }

    public record ServerArgs(
            String transport,
            String host,
            int port,
            String path,
            String logLevel,
            boolean noBanner,
            boolean statelessHttp
    ) {
    }

    private record BrowserAgentRuntimeOperations(BrowserAgentRuntime runtime) implements RuntimeOperations {

        @Override
        public void ensureStarted() {
            runtime.ensureStarted();
        }

        @Override
        public void shutdown() {
            runtime.shutdown();
        }

        @Override
        public Map<String, Object> runBrowserTask(
                String task,
                String sessionId,
                String requestId,
                Integer timeoutSeconds
        ) {
            return runtime.runBrowserTask(task, sessionId, requestId, timeoutSeconds);
        }

        @Override
        public Map<String, Object> cancelRun(String sessionId, String requestId) {
            return runtime.cancelRun(sessionId, requestId);
        }

        @Override
        public Map<String, Object> clearCancel(String sessionId, String requestId) {
            return runtime.clearCancel(sessionId, requestId);
        }

        @Override
        public Map<String, Object> runtimeHealth() {
            return runtime.runtimeHealth();
        }
    }

    private static void bindRuntime(RuntimeOperations currentRuntime) {
        ActionController.getDefaultController().registerBuiltinActions();
        ActionController.bindRuntimeRunnerForDefault((task, sessionId, requestId, timeoutS) ->
                CompletableFuture.completedFuture(
                        currentRuntime.runBrowserTask(task, sessionId, requestId, timeoutS)
                ));
    }

    private static void configureStdioLogging() {
        String configured = trimToEmpty(envOrDefault("PLAYWRIGHT_RUNTIME_LOG_DIR", ""));
        if (configured.isEmpty()) {
            configured = trimToEmpty(envOrDefault("BROWSER_RUNTIME_LOG_DIR", ""));
        }
        if (configured.isEmpty()) {
            configured = "logs";
        }
        System.setProperty("openjiuwen.log.output", "file");
        System.setProperty("openjiuwen.log.path", configured);
    }

    private static void setPropertyIfBlank(String key, String value) {
        if (System.getProperty(key, "").isBlank()) {
            System.setProperty(key, value);
        }
    }

    private static String nextValue(List<String> values, int index, String fallback) {
        return index >= 0 && index < values.size() ? values.get(index) : fallback;
    }

    private static int parseInt(String raw, int fallback) {
        try {
            return Integer.parseInt(trimToEmpty(raw));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static String envOrDefault(String key, String fallback) {
        String env = System.getenv(key);
        if (env != null && !env.isBlank()) {
            return env;
        }
        return System.getProperty(key, fallback);
    }

    private static String emptyToNull(String value) {
        String text = trimToEmpty(value);
        return text.isEmpty() ? null : text;
    }

    private static String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }
}

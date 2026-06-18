/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.browser_move.playwright_runtime;

import com.openjiuwen.core.foundation.tool.schema.ToolInfo;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.harness.tools.browser_move.utils.BrowserMoveEnv;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Scanner;

/**
 * CLI entrypoint for Playwright browser runtime.
 *
 * <p>Mirrors Python's {@code main} in
 * {@code openjiuwen/harness/tools/browser_move/playwright_runtime/main.py}.</p>
 */
public final class PlaywrightRuntimeMain {

    public static final String DEFAULT_SESSION_ID = "demo-browser-session";

    private static final Logger LOGGER = LoggerFactory.getLogger(PlaywrightRuntimeMain.class);

    private PlaywrightRuntimeMain() {
    }

    public static void main(String[] args) {
        RuntimeSettings settings = loadSettingsFromEnvironment();
        BrowserAgentRuntime runtime = buildRuntime(settings);
        try {
            runInteractive(
                    new BrowserAgentRuntimeOperations(runtime),
                    settings,
                    System.getenv("AGENT_QUERY"),
                    System.getenv("AGENT_SESSION_ID"),
                    System.in
            );
        } finally {
            runtime.shutdown();
        }
    }

    public static RuntimeSettings loadSettingsFromEnvironment() {
        BrowserMoveEnv.loadRepoDotenv(false);
        RuntimeSettings settings = BrowserRuntimeConfig.buildRuntimeSettings();
        if (settings.getApiKey() == null || settings.getApiKey().isBlank()) {
            throw new IllegalStateException(BrowserMoveEnv.MISSING_API_KEY_MESSAGE);
        }
        return settings;
    }

    public static BrowserAgentRuntime buildRuntime(RuntimeSettings settings) {
        if (settings == null || settings.getApiKey() == null || settings.getApiKey().isBlank()) {
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

    public static String resolveInitialQuery(String rawQuery) {
        return rawQuery == null ? "" : rawQuery.trim();
    }

    public static String resolveSessionId(String rawSessionId) {
        String text = rawSessionId == null ? "" : rawSessionId.trim();
        return text.isEmpty() ? DEFAULT_SESSION_ID : text;
    }

    public static List<String> discoverMcpToolNames(RuntimeSettings settings) {
        if (settings == null || settings.getMcpConfig() == null) {
            return List.of();
        }
        try {
            List<ToolInfo> toolInfos = Runner.getResourceMgr().getMcpToolInfos(
                    null,
                    List.of(settings.getMcpConfig().getServerId()),
                    null,
                    null,
                    null,
                    true,
                    true
            ).toCompletableFuture().join();
            List<String> names = new ArrayList<>();
            for (ToolInfo toolInfo : toolInfos) {
                if (toolInfo != null && toolInfo.getName() != null && !toolInfo.getName().isBlank()) {
                    names.add(toolInfo.getName());
                }
            }
            return List.copyOf(names);
        } catch (RuntimeException ex) {
            LOGGER.debug("Failed to discover Playwright MCP tool names", ex);
            return List.of();
        }
    }

    public static Map<String, Object> runSingleQuery(
            RuntimeOperations runtime,
            RuntimeSettings settings,
            String query,
            String sessionId
    ) {
        String resolvedQuery = resolveInitialQuery(query);
        if (resolvedQuery.isEmpty()) {
            return Map.of("ok", false, "skipped", true, "reason", "empty_query");
        }
        if (isExitCommand(resolvedQuery)) {
            return Map.of("ok", true, "skipped", true, "reason", "exit");
        }
        runtime.ensureStarted();
        logRuntimeBanner(settings, resolveSessionId(sessionId), discoverMcpToolNames(settings));
        return runtime.runBrowserTask(resolvedQuery, resolveSessionId(sessionId));
    }

    public static void runInteractive(
            RuntimeOperations runtime,
            RuntimeSettings settings,
            String initialQuery,
            String rawSessionId,
            InputStream inputStream
    ) {
        String sessionId = resolveSessionId(rawSessionId);
        String query = resolveInitialQuery(initialQuery);
        runtime.ensureStarted();
        logRuntimeBanner(settings, sessionId, discoverMcpToolNames(settings));
        Scanner scanner = new Scanner(inputStream == null ? InputStream.nullInputStream() : inputStream);
        try {
            while (true) {
                if (query.isEmpty()) {
                    if (!scanner.hasNextLine()) {
                        return;
                    }
                    query = scanner.nextLine().trim();
                }
                if (query.isEmpty()) {
                    continue;
                }
                if (isExitCommand(query)) {
                    return;
                }
                Map<String, Object> answer = runtime.runBrowserTask(query, sessionId);
                LOGGER.info("Result: {}", answer);
                query = "";
            }
        } finally {
            runtime.shutdown();
        }
    }

    public interface RuntimeOperations {
        void ensureStarted();

        Map<String, Object> runBrowserTask(String task, String sessionId);

        void shutdown();
    }

    private record BrowserAgentRuntimeOperations(BrowserAgentRuntime runtime) implements RuntimeOperations {

        @Override
        public void ensureStarted() {
            runtime.ensureStarted();
        }

        @Override
        public Map<String, Object> runBrowserTask(String task, String sessionId) {
            return runtime.runBrowserTask(task, sessionId, null, null);
        }

        @Override
        public void shutdown() {
            runtime.shutdown();
        }
    }

    private static boolean isExitCommand(String query) {
        String lowered = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        return "exit".equals(lowered) || "quit".equals(lowered);
    }

    private static void logRuntimeBanner(RuntimeSettings settings, String sessionId, List<String> mcpToolNames) {
        RuntimeSettings resolved = settings == null ? new RuntimeSettings() : settings;
        List<String> tools = mcpToolNames == null ? List.of() : mcpToolNames;
        LOGGER.info("========================================================================");
        LOGGER.info("Playwright Browser Runtime");
        LOGGER.info("========================================================================");
        LOGGER.info("Model provider: {}", resolved.getProvider());
        LOGGER.info("Model: {}", resolved.getModelName());
        if (resolved.getMcpConfig() != null && resolved.getMcpConfig().getParams() != null) {
            LOGGER.info("MCP command: {}", resolved.getMcpConfig().getParams().get("command"));
            LOGGER.info("MCP args: {}", resolved.getMcpConfig().getParams().get("args"));
        }
        LOGGER.info("Discovered browser tools: {}", tools.size());
        for (String name : tools) {
            LOGGER.info("  - {}", name);
        }
        LOGGER.info("========================================================================");
        LOGGER.info("Session: {}", sessionId);
        LOGGER.info("Continuous mode: enter a task and press Enter.");
        LOGGER.info("Type 'exit' or 'quit' to stop.");
    }
}

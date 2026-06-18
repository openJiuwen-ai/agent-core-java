/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.browser_move.playwright_runtime;

import com.openjiuwen.core.foundation.tool.mcp.McpServerConfig;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Runtime configuration helpers.
 *
 * <p>Mirrors Python's helper functions in
 * {@code openjiuwen/harness/tools/browser_move/playwright_runtime/config.py}.</p>
 */
public final class BrowserRuntimeConfig {

    public static final int DEFAULT_BROWSER_TIMEOUT_SECONDS = 180;
    public static final int DEFAULT_GUARDRAIL_MAX_FAILURES = 2;
    public static final int DEFAULT_GUARDRAIL_MAX_STEPS = 20;
    public static final String DEFAULT_MODEL_NAME = "gpt-4.1-mini";
    public static final String DEFAULT_PLAYWRIGHT_MCP_COMMAND = "npx";
    public static final String DEFAULT_PLAYWRIGHT_MCP_ARGS = "@playwright/mcp@latest";

    private BrowserRuntimeConfig() {
    }

    public static String resolvePlaywrightMcpCwd() {
        String configured = firstNonBlank(
                System.getenv("PLAYWRIGHT_RUNTIME_MCP_CWD"),
                System.getenv("BROWSER_RUNTIME_MCP_CWD"),
                System.getenv("PLAYWRIGHT_RUNTIME_WORKDIR"),
                System.getenv("BROWSER_RUNTIME_WORKDIR")
        );
        if (!configured.isBlank()) {
            return Path.of(configured).toAbsolutePath().normalize().toString();
        }
        return Path.of("").toAbsolutePath().normalize().toString();
    }

    public static BrowserRunGuardrails buildBrowserGuardrails() {
        return new BrowserRunGuardrails(
                resolveIntEnv("BROWSER_GUARDRAIL_MAX_STEPS", DEFAULT_GUARDRAIL_MAX_STEPS, 1),
                resolveIntEnv("BROWSER_GUARDRAIL_MAX_FAILURES", DEFAULT_GUARDRAIL_MAX_FAILURES, 0),
                resolveIntEnv("BROWSER_TIMEOUT_S", DEFAULT_BROWSER_TIMEOUT_SECONDS, 1),
                resolveBoolEnv("BROWSER_GUARDRAIL_RETRY_ONCE", true),
                resolveBoolEnv("BROWSER_GUARDRAIL_RESUME_ON_MAX_ITERATIONS", false)
        );
    }

    public static McpServerConfig buildPlaywrightMcpConfig() {
        String command = envOrDefault("PLAYWRIGHT_MCP_COMMAND", DEFAULT_PLAYWRIGHT_MCP_COMMAND);
        List<String> args = parseCommandArgs(envOrDefault("PLAYWRIGHT_MCP_ARGS", DEFAULT_PLAYWRIGHT_MCP_ARGS));
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("command", command);
        params.put("args", args);
        params.put("cwd", resolvePlaywrightMcpCwd());
        params.put("timeout_s", resolveIntEnv("PLAYWRIGHT_MCP_TIMEOUT_S", DEFAULT_BROWSER_TIMEOUT_SECONDS, 1));

        Map<String, String> env = new LinkedHashMap<>();
        copyEnv(env, "PLAYWRIGHT_BROWSERS_PATH");
        copyEnv(env, "HTTP_PROXY");
        copyEnv(env, "HTTPS_PROXY");
        copyEnv(env, "NO_PROXY");
        if (!env.isEmpty()) {
            params.put("env", env);
        }

        return McpServerConfig.builder()
                .serverId("playwright_official_stdio")
                .serverName("playwright-official")
                .serverPath("stdio://playwright")
                .clientType("stdio")
                .params(params)
                .build();
    }

    public static RuntimeSettings buildRuntimeSettings() {
        return new RuntimeSettings(
                envOrDefault("MODEL_PROVIDER", ""),
                firstNonBlank(System.getenv("API_KEY"), System.getenv("OPENAI_API_KEY"), System.getenv("OPENROUTER_API_KEY")),
                firstNonBlank(System.getenv("API_BASE"), System.getenv("OPENAI_BASE_URL"), System.getenv("OPENROUTER_BASE_URL")),
                envOrDefault("MODEL_NAME", DEFAULT_MODEL_NAME),
                buildPlaywrightMcpConfig(),
                buildBrowserGuardrails()
        );
    }

    public static List<String> parseCommandArgs(String raw) {
        String text = raw == null ? "" : raw.trim();
        if (text.isEmpty()) {
            return new ArrayList<>();
        }
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inSingle = false;
        boolean inDouble = false;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch == '\'' && !inDouble) {
                inSingle = !inSingle;
                continue;
            }
            if (ch == '"' && !inSingle) {
                inDouble = !inDouble;
                continue;
            }
            if (Character.isWhitespace(ch) && !inSingle && !inDouble) {
                if (!current.isEmpty()) {
                    result.add(current.toString());
                    current.setLength(0);
                }
                continue;
            }
            current.append(ch);
        }
        if (!current.isEmpty()) {
            result.add(current.toString());
        }
        return result;
    }

    private static void copyEnv(Map<String, String> target, String key) {
        String value = System.getenv(key);
        if (value != null && !value.isBlank()) {
            target.put(key, value);
        }
    }

    private static String envOrDefault(String key, String defaultValue) {
        String value = System.getenv(key);
        return value == null || value.isBlank() ? defaultValue : value.trim();
    }

    private static int resolveIntEnv(String key, int defaultValue, int minimum) {
        String value = System.getenv(key);
        try {
            return Math.max(minimum, value == null || value.isBlank() ? defaultValue : Integer.parseInt(value.trim()));
        } catch (NumberFormatException ignored) {
            return Math.max(minimum, defaultValue);
        }
    }

    private static boolean resolveBoolEnv(String key, boolean defaultValue) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return List.of("1", "true", "yes", "y", "on").contains(value.trim().toLowerCase());
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "";
    }
}

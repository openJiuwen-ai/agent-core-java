/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.browser_move.playwright_runtime;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.foundation.tool.mcp.McpServerConfig;
import com.openjiuwen.harness.tools.browser_move.utils.BrowserMoveEnv;

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

    public static final int DEFAULT_BROWSER_TIMEOUT_SECONDS = BrowserMoveEnv.DEFAULT_BROWSER_TIMEOUT_S;
    public static final int DEFAULT_GUARDRAIL_MAX_FAILURES = BrowserMoveEnv.DEFAULT_GUARDRAIL_MAX_FAILURES;
    public static final int DEFAULT_GUARDRAIL_MAX_STEPS = BrowserMoveEnv.DEFAULT_GUARDRAIL_MAX_STEPS;
    public static final String DEFAULT_MODEL_NAME = BrowserMoveEnv.DEFAULT_MODEL_NAME;
    public static final String DEFAULT_PLAYWRIGHT_MCP_COMMAND = BrowserMoveEnv.DEFAULT_PLAYWRIGHT_MCP_COMMAND;
    public static final String DEFAULT_PLAYWRIGHT_MCP_ARGS = BrowserMoveEnv.DEFAULT_PLAYWRIGHT_MCP_ARGS;

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private BrowserRuntimeConfig() {
    }

    public static String resolvePlaywrightMcpCwd() {
        return resolvePlaywrightMcpCwd(System.getenv());
    }

    public static String resolvePlaywrightMcpCwd(Map<String, String> env) {
        String configured = firstNonBlank(
                envValue(env, "PLAYWRIGHT_RUNTIME_MCP_CWD"),
                envValue(env, "BROWSER_RUNTIME_MCP_CWD"),
                envValue(env, "PLAYWRIGHT_RUNTIME_WORKDIR"),
                envValue(env, "BROWSER_RUNTIME_WORKDIR")
        );
        if (!configured.isBlank()) {
            return Path.of(configured).toAbsolutePath().normalize().toString();
        }
        return Path.of("").toAbsolutePath().normalize().toString();
    }

    public static BrowserRunGuardrails buildBrowserGuardrails() {
        return buildBrowserGuardrails(System.getenv());
    }

    public static BrowserRunGuardrails buildBrowserGuardrails(Map<String, String> env) {
        return new BrowserRunGuardrails(
                resolveIntEnv(env, DEFAULT_GUARDRAIL_MAX_STEPS, 1, "BROWSER_GUARDRAIL_MAX_STEPS"),
                resolveIntEnv(env, DEFAULT_GUARDRAIL_MAX_FAILURES, 0, "BROWSER_GUARDRAIL_MAX_FAILURES"),
                resolveIntEnv(env, DEFAULT_BROWSER_TIMEOUT_SECONDS, 1, "BROWSER_TIMEOUT_S", "PLAYWRIGHT_TOOL_TIMEOUT_S"),
                resolveBoolEnv(env, BrowserMoveEnv.DEFAULT_GUARDRAIL_RETRY_ONCE, "BROWSER_GUARDRAIL_RETRY_ONCE"),
                resolveBoolEnv(env, false, "BROWSER_GUARDRAIL_RESUME_ON_MAX_ITERATIONS")
        );
    }

    public static McpServerConfig buildPlaywrightMcpConfig() {
        return buildPlaywrightMcpConfig(System.getenv());
    }

    public static McpServerConfig buildPlaywrightMcpConfig(Map<String, String> env) {
        String command = envOrDefault(env, "PLAYWRIGHT_MCP_COMMAND", DEFAULT_PLAYWRIGHT_MCP_COMMAND);
        List<String> args = parseCommandArgs(envOrDefault(env, "PLAYWRIGHT_MCP_ARGS", DEFAULT_PLAYWRIGHT_MCP_ARGS));
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("command", command);
        params.put("args", args);
        params.put("cwd", resolvePlaywrightMcpCwd(env));
        params.put("timeout_s", resolveIntEnv(
                env, DEFAULT_BROWSER_TIMEOUT_SECONDS, 1, "PLAYWRIGHT_MCP_TIMEOUT_S", "BROWSER_TIMEOUT_S"));

        Map<String, String> childEnv = new LinkedHashMap<>();
        copyEnv(childEnv, env, "PLAYWRIGHT_BROWSERS_PATH");
        copyEnv(childEnv, env, "HTTP_PROXY");
        copyEnv(childEnv, env, "HTTPS_PROXY");
        copyEnv(childEnv, env, "NO_PROXY");
        appendJsonEnv(childEnv, envOrDefault(env, "PLAYWRIGHT_MCP_ENV_JSON", ""));
        appendCdpEnv(childEnv, env);
        if (!childEnv.isEmpty()) {
            params.put("env", childEnv);
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
        return buildRuntimeSettings(System.getenv());
    }

    public static RuntimeSettings buildRuntimeSettings(Map<String, String> env) {
        ModelSettings modelSettings = resolveModelSettings(env);
        return new RuntimeSettings(
                modelSettings.provider(),
                modelSettings.apiKey(),
                modelSettings.apiBase(),
                envOrDefault(env, "MODEL_NAME", DEFAULT_MODEL_NAME),
                buildPlaywrightMcpConfig(env),
                buildBrowserGuardrails(env)
        );
    }

    public static List<String> parseCommandArgs(String raw) {
        String text = raw == null ? "" : raw.trim();
        if (text.isEmpty()) {
            return new ArrayList<>();
        }
        if (text.startsWith("[") && text.endsWith("]")) {
            try {
                List<?> parsed = OBJECT_MAPPER.readValue(text, List.class);
                return parsed.stream().map(String::valueOf).toList();
            } catch (JsonProcessingException ignored) {
                // Fall through to shell-style splitting, matching Python's tolerant helper.
            }
        }
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inSingle = false;
        boolean inDouble = false;
        boolean escaping = false;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (escaping) {
                current.append(ch);
                escaping = false;
                continue;
            }
            if (ch == '\\' && !inSingle) {
                escaping = true;
                continue;
            }
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

    private static void copyEnv(Map<String, String> target, Map<String, String> env, String key) {
        String value = envValue(env, key);
        if (!value.isBlank()) {
            target.put(key, value);
        }
    }

    private static String envOrDefault(Map<String, String> env, String key, String defaultValue) {
        String value = envValue(env, key);
        return value.isBlank() ? defaultValue : value;
    }

    private static int resolveIntEnv(Map<String, String> env, int defaultValue, int minimum, String... keys) {
        for (String key : keys) {
            String value = envValue(env, key);
            if (value.isBlank()) {
                continue;
            }
            try {
                return Math.max(minimum, Integer.parseInt(value));
            } catch (NumberFormatException ignored) {
            }
        }
        return Math.max(minimum, defaultValue);
    }

    private static boolean resolveBoolEnv(Map<String, String> env, boolean defaultValue, String... keys) {
        for (String key : keys) {
            String value = envValue(env, key);
            if (value.isBlank()) {
                continue;
            }
            return List.of("1", "true", "yes", "y", "on").contains(value.toLowerCase());
        }
        return defaultValue;
    }

    private static ModelSettings resolveModelSettings(Map<String, String> env) {
        String provider = normalizeProvider(firstNonBlank(
                envValue(env, "MODEL_PROVIDER"),
                envValue(env, "MODEL_CLIENT_PROVIDER")
        ));
        String explicitBase = firstNonBlank(envValue(env, "API_BASE"), envValue(env, "MODEL_API_BASE"));
        if (provider.isBlank()) {
            provider = inferProvider(firstNonBlank(
                    explicitBase,
                    envValue(env, "OPENROUTER_BASE_URL"),
                    envValue(env, "OPENROUTER_API_BASE"),
                    envValue(env, "OPENAI_BASE_URL"),
                    envValue(env, "OPENAI_API_BASE")
            ));
        }
        if (provider.isBlank()) {
            provider = envValue(env, "OPENROUTER_API_KEY").isBlank() ? "openai" : "openrouter";
        }
        if ("openrouter".equals(provider)) {
            return new ModelSettings(
                    provider,
                    firstNonBlank(envValue(env, "API_KEY"), envValue(env, "MODEL_API_KEY"),
                            envValue(env, "OPENROUTER_API_KEY"), envValue(env, "OPENAI_API_KEY")),
                    firstNonBlank(explicitBase, envValue(env, "OPENROUTER_BASE_URL"),
                            envValue(env, "OPENROUTER_API_BASE"), "https://openrouter.ai/api/v1")
            );
        }
        return new ModelSettings(
                "openai",
                firstNonBlank(envValue(env, "API_KEY"), envValue(env, "MODEL_API_KEY"),
                        envValue(env, "OPENAI_API_KEY"), envValue(env, "OPENROUTER_API_KEY")),
                firstNonBlank(explicitBase, envValue(env, "OPENAI_BASE_URL"),
                        envValue(env, "OPENAI_API_BASE"), "https://api.openai.com/v1")
        );
    }

    private static String inferProvider(String apiBase) {
        String normalized = apiBase == null ? "" : apiBase.trim().toLowerCase();
        if (normalized.contains("openrouter.ai")) {
            return "openrouter";
        }
        return normalized.isBlank() ? "" : "openai";
    }

    private static String normalizeProvider(String provider) {
        String value = provider == null ? "" : provider.trim().toLowerCase();
        if ("openrouter".equals(value) || "openai".equals(value)) {
            return value;
        }
        return value;
    }

    private static void appendJsonEnv(Map<String, String> target, String rawJson) {
        if (rawJson == null || rawJson.isBlank()) {
            return;
        }
        try {
            Map<?, ?> parsed = OBJECT_MAPPER.readValue(rawJson, Map.class);
            parsed.forEach((key, value) -> target.put(String.valueOf(key), String.valueOf(value)));
        } catch (JsonProcessingException error) {
            throw new IllegalArgumentException("Invalid PLAYWRIGHT_MCP_ENV_JSON: " + error.getOriginalMessage(), error);
        }
    }

    private static void appendCdpEnv(Map<String, String> target, Map<String, String> env) {
        String cdpEndpoint = firstNonBlank(envValue(env, "PLAYWRIGHT_MCP_CDP_ENDPOINT"),
                envValue(env, "PLAYWRIGHT_CDP_URL"));
        if (!cdpEndpoint.isBlank()) {
            target.put("PLAYWRIGHT_MCP_CDP_ENDPOINT", cdpEndpoint);
            if (envValue(env, "PLAYWRIGHT_MCP_BROWSER").isBlank()) {
                target.put("PLAYWRIGHT_MCP_BROWSER", "chrome");
            }
        }
        copyEnv(target, env, "PLAYWRIGHT_MCP_CDP_HEADERS");
        copyEnv(target, env, "PLAYWRIGHT_MCP_CDP_TIMEOUT");
    }

    private static String envValue(Map<String, String> env, String key) {
        if (env == null || key == null) {
            return "";
        }
        String value = env.get(key);
        return value == null ? "" : value.trim();
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

    private record ModelSettings(String provider, String apiKey, String apiBase) {
    }
}

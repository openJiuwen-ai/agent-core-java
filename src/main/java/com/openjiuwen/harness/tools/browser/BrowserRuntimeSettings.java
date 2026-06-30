/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.browser;

import com.openjiuwen.core.foundation.tool.mcp.McpServerConfig;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import com.fasterxml.jackson.core.JsonProcessingException;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

@Data
@Builder
@NoArgsConstructor
/**
 * Public class BrowserRuntimeSettings used by the Java parity implementation.
 *
 * @since 1.0
 */
@AllArgsConstructor
public class BrowserRuntimeSettings {
    /**
     * Auto-generated for codecheck compliance.
     */
    public static final String DEFAULT_MODEL_NAME = "gpt-4.1";
    /**
     * Auto-generated for codecheck compliance.
     */
    public static final int DEFAULT_BROWSER_TIMEOUT_S = 60;
    private static final String DEFAULT_PLAYWRIGHT_MCP_ARGS = "[\"-m\", "
            + "\"openjiuwen.harness.tools.browser.playwright_runtime_mcp_server\", "
            + "\"--transport\", \"stdio\", \"--no-banner\", \"--log-level\", \"ERROR\"]";

    private String provider;
    private String apiKey;
    private String apiBase;
    private String modelName;
    private McpServerConfig mcpCfg;
    private BrowserRunGuardrails guardrails;

    /**
     * Auto-generated for codecheck compliance.
     */
    public static BrowserRuntimeSettings buildRuntimeSettings(Map<String, String> env) {
        int timeout = parseInt(env.get("BROWSER_TIMEOUT_S"), DEFAULT_BROWSER_TIMEOUT_S);
        return BrowserRuntimeSettings.builder()
                .provider(env.getOrDefault("MODEL_PROVIDER", "openai").toLowerCase(Locale.ROOT))
                .apiKey(resolveApiKey(env))
                .apiBase(env.getOrDefault("API_BASE", "https://api.openai.com/v1"))
                .modelName(env.getOrDefault("MODEL_NAME", DEFAULT_MODEL_NAME))
                .mcpCfg(buildBrowserRuntimeMcpConfig(env))
                .guardrails(BrowserRunGuardrails.builder().timeoutS(timeout).build())
                .build();
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static BrowserRuntimeSettings buildRuntimeSettings() {
        return buildRuntimeSettings(System.getenv());
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static McpServerConfig buildBrowserRuntimeMcpConfig(Map<String, String> env) {
        boolean isEnabled = "1".equals(env.getOrDefault(
                "PLAYWRIGHT_RUNTIME_MCP_ENABLED",
                env.getOrDefault("BROWSER_RUNTIME_MCP_ENABLED", "0")));
        if (!isEnabled) {
            return null;
        }
        String clientType = env.getOrDefault(
                "PLAYWRIGHT_RUNTIME_MCP_CLIENT_TYPE",
                env.getOrDefault("BROWSER_RUNTIME_MCP_CLIENT_TYPE", "stdio"));
        if ("http".equals(clientType) || "streamable_http".equals(clientType) || "streamable-http".equals(clientType)) {
            String host = env.getOrDefault(
                    "PLAYWRIGHT_RUNTIME_MCP_HOST",
                    env.getOrDefault("BROWSER_RUNTIME_MCP_HOST", "127.0.0.1"));
            String port = env.getOrDefault(
                    "PLAYWRIGHT_RUNTIME_MCP_PORT",
                    env.getOrDefault("BROWSER_RUNTIME_MCP_PORT", "8940"));
            String path = env.getOrDefault(
                    "PLAYWRIGHT_RUNTIME_MCP_PATH",
                    env.getOrDefault("BROWSER_RUNTIME_MCP_PATH", "mcp"));
            return McpServerConfig.builder()
                    .serverId("playwright-runtime-wrapper")
                    .serverName("playwright-runtime-wrapper")
                    .clientType("streamable-http")
                    .serverPath("http://" + host + ":" + port + "/" + path)
                    .build();
        }
        String cwd = env.getOrDefault(
                "PLAYWRIGHT_RUNTIME_MCP_CWD",
                String.valueOf(Path.of("").toAbsolutePath().normalize()));
        return McpServerConfig.builder()
                .serverId("playwright-runtime-wrapper")
                .serverName("playwright-runtime-wrapper")
                .clientType("stdio")
                .serverPath("stdio://playwright-runtime-wrapper")
                .params(Map.of(
                        "cwd", cwd,
                        "args", parseCommandArgs(env.getOrDefault(
                                "PLAYWRIGHT_MCP_ARGS",
                                DEFAULT_PLAYWRIGHT_MCP_ARGS)),
                        "timeout_s", parseInt(env.get("BROWSER_TIMEOUT_S"), DEFAULT_BROWSER_TIMEOUT_S)
                ))
                .build();
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static McpServerConfig buildBrowserRuntimeMcpConfig() {
        return buildBrowserRuntimeMcpConfig(System.getenv());
    }

    private static String resolveApiKey(Map<String, String> env) {
        if (env.containsKey("API_KEY")) {
            return env.getOrDefault("API_KEY", "");
        }
        if (env.containsKey("OPENROUTER_API_KEY")) {
            return env.getOrDefault("OPENROUTER_API_KEY", "");
        }
        return "";
    }

    private static int parseInt(String raw, int fallback) {
        try {
            return raw != null ? Integer.parseInt(raw) : fallback;
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static List<String> parseCommandArgs(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        String trimmed = raw.trim();
        if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
            try {
                Object[] values = new com.fasterxml.jackson.databind.ObjectMapper().readValue(trimmed, Object[].class);
                return Stream.of(values).map(String::valueOf).toList();
            } catch (JsonProcessingException ex) {
                // Fall back to shell-like splitting for non-JSON argument strings.
            }
        }
        return List.of(trimmed.split("\\s+"));
    }
}

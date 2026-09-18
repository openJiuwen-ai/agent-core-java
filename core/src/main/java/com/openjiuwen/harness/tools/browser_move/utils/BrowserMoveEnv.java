/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.browser_move.utils;

import com.openjiuwen.harness.tools.browser_move.playwright_runtime.PlaywrightRuntimeBootstrap;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Environment and settings helpers for browser-move runtime.
 *
 * <p>Mirrors Python's {@code openjiuwen/harness/tools/browser_move/utils/env.py}.</p>
 */
public final class BrowserMoveEnv {

    public static final List<String> SUPPORTED_MODEL_PROVIDERS =
            List.of("openai", "openrouter", "siliconflow", "dashscope");
    public static final String DEFAULT_MODEL_NAME = "anthropic/claude-sonnet-4.5";
    public static final int DEFAULT_BROWSER_TIMEOUT_S = 180;
    public static final int DEFAULT_GUARDRAIL_MAX_STEPS = 20;
    public static final int DEFAULT_GUARDRAIL_MAX_FAILURES = 2;
    public static final boolean DEFAULT_GUARDRAIL_RETRY_ONCE = true;
    public static final String DEFAULT_PLAYWRIGHT_MCP_COMMAND = "npx";
    public static final String DEFAULT_PLAYWRIGHT_MCP_ARGS = "-y @playwright/mcp@latest";
    public static final String DEFAULT_BROWSER_UPLOAD_ROOT = "";
    public static final String MISSING_API_KEY_MESSAGE =
            "Missing API key. Set API_KEY (or OPENROUTER_API_KEY / SILICONFLOW_API_KEY / OPENAI_API_KEY / DASHSCOPE_API_KEY).";

    private static final List<String> TRUTHY_VALUES = List.of("1", "true", "yes", "on");
    private static final List<String> FALSY_VALUES = List.of("0", "false", "no", "off");

    private BrowserMoveEnv() {
    }

    public static Path resolveRepoDotenvPath() {
        return PlaywrightRuntimeBootstrap.resolveRepoRoot().resolve(".env").normalize();
    }

    public static boolean loadRepoDotenv(boolean override) {
        Path envPath = resolveRepoDotenvPath();
        if (!Files.isRegularFile(envPath)) {
            return false;
        }
        try {
            for (String line : Files.readAllLines(envPath)) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#") || !trimmed.contains("=")) {
                    continue;
                }
                int split = trimmed.indexOf('=');
                String key = trimmed.substring(0, split).trim();
                String value = trimmed.substring(split + 1).trim();
                if (!override && !System.getenv(key).isEmpty()) {
                    continue;
                }
                System.setProperty(key, stripQuotes(value));
            }
            return true;
        } catch (IOException ex) {
            return false;
        }
    }

    public static List<String> parseCommandArgs(String value) {
        String text = value == null ? "" : value.trim();
        if (text.isEmpty()) {
            return List.of();
        }
        if (text.startsWith("[") && text.endsWith("]")) {
            String body = text.substring(1, text.length() - 1).trim();
            if (body.isEmpty()) {
                return List.of();
            }
            List<String> values = new ArrayList<>();
            for (String part : body.split(",")) {
                values.add(stripQuotes(part.trim()));
            }
            return values;
        }
        return shellSplit(text);
    }

    public static String firstNonEmptyEnv(String... keys) {
        for (String key : keys) {
            String value = getEnv(key);
            if (!value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    public static String normalizeProvider(String provider) {
        String raw = provider == null ? "" : provider.trim();
        String lowered = raw.toLowerCase(Locale.ROOT);
        if (SUPPORTED_MODEL_PROVIDERS.contains(lowered)) {
            return lowered;
        }
        if ("alibaba".equals(lowered) || "aliyun".equals(lowered)) {
            return "dashscope";
        }
        if ("silicon-flow".equals(lowered) || "silicon_flow".equals(lowered)) {
            return "siliconflow";
        }
        return raw;
    }

    public static boolean isTruthyEnv(String value) {
        return TRUTHY_VALUES.contains(normalize(value));
    }

    public static boolean isFalsyEnv(String value) {
        return FALSY_VALUES.contains(normalize(value));
    }

    public static int resolveIntEnv(int defaultValue, Integer minimum, String... keys) {
        for (String key : keys) {
            String raw = getEnv(key);
            if (raw.isBlank()) {
                continue;
            }
            try {
                int parsed = Integer.parseInt(raw);
                if (minimum == null || parsed >= minimum) {
                    return parsed;
                }
            } catch (NumberFormatException ignored) {
            }
        }
        return defaultValue;
    }

    public static boolean resolveBoolEnv(boolean defaultValue, String... keys) {
        for (String key : keys) {
            String raw = getEnv(key);
            if (raw.isBlank()) {
                continue;
            }
            if (isTruthyEnv(raw)) {
                return true;
            }
            if (isFalsyEnv(raw)) {
                return false;
            }
        }
        return defaultValue;
    }

    public static String inferProviderFromApiBase(String apiBase) {
        String base = normalize(apiBase);
        if (base.isEmpty()) {
            return "";
        }
        if (base.contains("openrouter.ai")) {
            return "openrouter";
        }
        if (base.contains("siliconflow.cn") || base.contains("siliconflow")) {
            return "siliconflow";
        }
        if (base.contains("dashscope.aliyuncs.com") || base.contains("dashscope")) {
            return "dashscope";
        }
        return "openai";
    }

    public static String resolveModelName() {
        String name = firstNonEmptyEnv("MODEL_NAME");
        return name.isBlank() ? DEFAULT_MODEL_NAME : name;
    }

    public static int resolveBrowserTimeoutS() {
        return resolveIntEnv(DEFAULT_BROWSER_TIMEOUT_S, 1, "BROWSER_TIMEOUT_S", "PLAYWRIGHT_TOOL_TIMEOUT_S");
    }

    public static Path resolveUploadRoot() {
        String raw = firstNonEmptyEnv("BROWSER_UPLOAD_ROOT");
        return raw.isBlank() ? null : Path.of(raw).toAbsolutePath().normalize();
    }

    public static ModelSettings resolveModelSettings() {
        String providerMode = normalizeProvider(firstNonEmptyEnv("MODEL_PROVIDER", "MODEL_CLIENT_PROVIDER"));
        if (!providerMode.isEmpty() && !SUPPORTED_MODEL_PROVIDERS.contains(providerMode)) {
            throw new IllegalArgumentException(
                    "Unsupported MODEL_PROVIDER '" + providerMode + "'. Supported: openai, openrouter, siliconflow, dashscope."
            );
        }

        String explicitApiBase = firstNonEmptyEnv("API_BASE", "MODEL_API_BASE");
        String provider = providerMode;
        if (provider.isEmpty()) {
            String baseHint = explicitApiBase.isEmpty()
                    ? firstNonEmptyEnv(
                            "OPENROUTER_BASE_URL",
                            "OPENROUTER_API_BASE",
                            "SILICONFLOW_BASE_URL",
                            "SILICONFLOW_API_BASE",
                            "DASHSCOPE_BASE_URL",
                            "DASHSCOPE_API_BASE",
                            "OPENAI_BASE_URL",
                            "OPENAI_API_BASE"
                    )
                    : explicitApiBase;
            provider = inferProviderFromApiBase(baseHint);
            if (provider.isEmpty()) {
                if (!firstNonEmptyEnv("OPENROUTER_API_KEY").isEmpty()) {
                    provider = "openrouter";
                } else if (!firstNonEmptyEnv("SILICONFLOW_API_KEY").isEmpty()) {
                    provider = "siliconflow";
                } else if (!firstNonEmptyEnv("DASHSCOPE_API_KEY").isEmpty()) {
                    provider = "dashscope";
                } else {
                    provider = "openai";
                }
            }
        }

        if ("openrouter".equals(provider)) {
            return new ModelSettings(
                    provider,
                    firstNonEmptyEnv("API_KEY", "MODEL_API_KEY", "OPENROUTER_API_KEY", "OPENAI_API_KEY"),
                    defaultIfBlank(
                            firstNonEmptyEnv("API_BASE", "MODEL_API_BASE", "OPENROUTER_BASE_URL", "OPENROUTER_API_BASE"),
                            "https://openrouter.ai/api/v1"
                    )
            );
        }
        if ("siliconflow".equals(provider)) {
            return new ModelSettings(
                    provider,
                    firstNonEmptyEnv("API_KEY", "MODEL_API_KEY", "SILICONFLOW_API_KEY", "OPENAI_API_KEY", "OPENROUTER_API_KEY"),
                    defaultIfBlank(
                            firstNonEmptyEnv("API_BASE", "MODEL_API_BASE", "SILICONFLOW_BASE_URL", "SILICONFLOW_API_BASE"),
                            "https://api.siliconflow.cn/v1"
                    )
            );
        }
        if ("dashscope".equals(provider)) {
            return new ModelSettings(
                    provider,
                    firstNonEmptyEnv("API_KEY", "MODEL_API_KEY", "DASHSCOPE_API_KEY", "OPENAI_API_KEY", "OPENROUTER_API_KEY"),
                    defaultIfBlank(
                            firstNonEmptyEnv("API_BASE", "MODEL_API_BASE", "DASHSCOPE_BASE_URL", "DASHSCOPE_API_BASE"),
                            "https://dashscope.aliyuncs.com/compatible-mode/v1"
                    )
            );
        }
        return new ModelSettings(
                "openai",
                firstNonEmptyEnv("API_KEY", "MODEL_API_KEY", "OPENAI_API_KEY", "OPENROUTER_API_KEY"),
                defaultIfBlank(
                        firstNonEmptyEnv("API_BASE", "MODEL_API_BASE", "OPENAI_BASE_URL", "OPENAI_API_BASE"),
                        "https://api.openai.com/v1"
                )
        );
    }

    public record ModelSettings(String provider, String apiKey, String apiBase) {
    }

    private static String getEnv(String key) {
        String env = System.getenv(key);
        if (env != null) {
            return env.trim();
        }
        return System.getProperty(key, "").trim();
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static String defaultIfBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String stripQuotes(String value) {
        if (value == null) {
            return "";
        }
        String text = value.trim();
        if ((text.startsWith("\"") && text.endsWith("\"")) || (text.startsWith("'") && text.endsWith("'"))) {
            return text.substring(1, text.length() - 1);
        }
        return text;
    }

    private static List<String> shellSplit(String value) {
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inSingle = false;
        boolean inDouble = false;
        boolean escaping = false;
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
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
            if (!inSingle && !inDouble && Character.isWhitespace(ch)) {
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
}

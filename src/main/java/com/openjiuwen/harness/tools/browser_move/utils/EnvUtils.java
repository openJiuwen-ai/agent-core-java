/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.browser_move.utils;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Environment and settings helpers for the runtime.
 *
 * <p>Mirrors Python's {@code openjiuwen.harness.tools.browser_move.utils.env}.
 */
public final class EnvUtils {

    public static final Set<String> SUPPORTED_MODEL_PROVIDERS = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList("openai", "openrouter", "siliconflow", "dashscope"))
    );

    public static final Set<String> TRUTHY_ENV_VALUES = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList("1", "true", "yes", "on"))
    );

    public static final Set<String> FALSY_ENV_VALUES = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList("0", "false", "no", "off"))
    );

    public static final String DEFAULT_MODEL_NAME = "anthropic/claude-sonnet-4.5";
    public static final int DEFAULT_BROWSER_TIMEOUT_S = 180;
    public static final int DEFAULT_GUARDRAIL_MAX_STEPS = 20;
    public static final int DEFAULT_GUARDRAIL_MAX_FAILURES = 2;
    public static final boolean DEFAULT_GUARDRAIL_RETRY_ONCE = true;
    public static final String DEFAULT_PLAYWRIGHT_MCP_COMMAND = "npx";
    public static final String DEFAULT_PLAYWRIGHT_MCP_ARGS = "-y @playwright/mcp@latest";
    public static final String DEFAULT_BROWSER_UPLOAD_ROOT = "";
    public static final String MISSING_API_KEY_MESSAGE =
            "Missing API key. Set API_KEY (or OPENROUTER_API_KEY / SILICONFLOW_API_KEY / " +
            "OPENAI_API_KEY / DASHSCOPE_API_KEY).";

    private static final Path REPO_ROOT = Path.of("").toAbsolutePath();

    private EnvUtils() {
    }

    public static Path resolveRepoDotenvPath() {
        return REPO_ROOT.resolve(".env");
    }

    public static boolean loadRepoDotenv(boolean override) {
        Path envPath = resolveRepoDotenvPath();
        if (!envPath.toFile().exists()) {
            return false;
        }
        return false;
    }

    public static List<String> parseCommandArgs(String value) {
        String trimmed = (value == null) ? "" : value.trim();
        if (trimmed.isEmpty()) {
            return Collections.emptyList();
        }
        if (trimmed.startsWith("[")) {
            try {
                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                List<?> parsed = mapper.readValue(trimmed, List.class);
                return parsed.stream()
                        .map(Object::toString)
                        .toList();
            } catch (Exception ignored) {
            }
        }
        return splitCommandLine(trimmed);
    }

    private static List<String> splitCommandLine(String input) {
        List<String> result = new java.util.ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        char quoteChar = '\0';
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (inQuotes) {
                if (c == quoteChar) {
                    inQuotes = false;
                } else {
                    current.append(c);
                }
            } else if (c == '"' || c == '\'') {
                inQuotes = true;
                quoteChar = c;
            } else if (Character.isWhitespace(c)) {
                if (current.length() > 0) {
                    result.add(current.toString());
                    current.setLength(0);
                }
            } else {
                current.append(c);
            }
        }
        if (current.length() > 0) {
            result.add(current.toString());
        }
        return result;
    }

    public static String firstNonEmptyEnv(String... keys) {
        for (String key : keys) {
            String value = System.getenv(key);
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
            String property = System.getProperty(key);
            if (property != null && !property.isBlank()) {
                return property.trim();
            }
        }
        return "";
    }

    public static String normalizeProvider(String provider) {
        String raw = (provider == null) ? "" : provider.trim();
        String lowered = raw.toLowerCase(Locale.ROOT);
        if (SUPPORTED_MODEL_PROVIDERS.contains(lowered)) {
            return lowered;
        }
        if (lowered.equals("alibaba") || lowered.equals("aliyun")) {
            return "dashscope";
        }
        if (lowered.equals("silicon-flow") || lowered.equals("silicon_flow")) {
            return "siliconflow";
        }
        return raw;
    }

    public static boolean isTruthyEnv(String value) {
        String lowered = (value == null) ? "" : value.trim().toLowerCase(Locale.ROOT);
        return TRUTHY_ENV_VALUES.contains(lowered);
    }

    public static boolean isFalsyEnv(String value) {
        String lowered = (value == null) ? "" : value.trim().toLowerCase(Locale.ROOT);
        return FALSY_ENV_VALUES.contains(lowered);
    }

    public static int resolveIntEnv(int defaultValue, Integer minimum, String... keys) {
        for (String key : keys) {
            String raw = firstNonEmptyEnv(key);
            if (raw.isEmpty()) {
                continue;
            }
            try {
                int value = Integer.parseInt(raw);
                if (minimum == null || value >= minimum) {
                    return value;
                }
            } catch (NumberFormatException ignored) {
            }
        }
        return defaultValue;
    }

    public static boolean resolveBoolEnv(boolean defaultValue, String... keys) {
        for (String key : keys) {
            String raw = firstNonEmptyEnv(key);
            if (raw.isEmpty()) {
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
        String base = (apiBase == null) ? "" : apiBase.trim().toLowerCase(Locale.ROOT);
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
        return name.isEmpty() ? DEFAULT_MODEL_NAME : name;
    }

    public static int resolveBrowserTimeoutS() {
        return resolveIntEnv(DEFAULT_BROWSER_TIMEOUT_S, 1, "BROWSER_TIMEOUT_S", "PLAYWRIGHT_TOOL_TIMEOUT_S");
    }

    public static Path resolveUploadRoot() {
        String raw = firstNonEmptyEnv("BROWSER_UPLOAD_ROOT");
        if (raw.isEmpty()) {
            return null;
        }
        return Path.of(raw).toAbsolutePath();
    }

    public static ModelSettings resolveModelSettings() {
        String providerMode = normalizeProvider(firstNonEmptyEnv("MODEL_PROVIDER", "MODEL_CLIENT_PROVIDER"));
        if (!providerMode.isEmpty() && !SUPPORTED_MODEL_PROVIDERS.contains(providerMode)) {
            throw new IllegalArgumentException(
                    "Unsupported MODEL_PROVIDER '" + providerMode + "'. " +
                    "Supported: openai, openrouter, siliconflow, dashscope."
            );
        }

        String explicitApiKey = firstNonEmptyEnv("API_KEY", "MODEL_API_KEY");
        String explicitApiBase = firstNonEmptyEnv("API_BASE", "MODEL_API_BASE");

        String provider;
        if (!providerMode.isEmpty()) {
            provider = providerMode;
        } else {
            String baseHint = !explicitApiBase.isEmpty() ? explicitApiBase : firstNonEmptyEnv(
                    "OPENROUTER_BASE_URL",
                    "OPENROUTER_API_BASE",
                    "SILICONFLOW_BASE_URL",
                    "SILICONFLOW_API_BASE",
                    "DASHSCOPE_BASE_URL",
                    "DASHSCOPE_API_BASE",
                    "OPENAI_BASE_URL",
                    "OPENAI_API_BASE"
            );
            provider = inferProviderFromApiBase(baseHint);
            if (provider.isEmpty()) {
                boolean hasOpenrouterKey = !firstNonEmptyEnv("OPENROUTER_API_KEY").isEmpty();
                boolean hasSiliconflowKey = !firstNonEmptyEnv("SILICONFLOW_API_KEY").isEmpty();
                boolean hasDashscopeKey = !firstNonEmptyEnv("DASHSCOPE_API_KEY").isEmpty();
                if (hasOpenrouterKey) {
                    provider = "openrouter";
                } else if (hasSiliconflowKey) {
                    provider = "siliconflow";
                } else if (hasDashscopeKey) {
                    provider = "dashscope";
                } else {
                    provider = "openai";
                }
            }
        }

        String apiKey;
        String apiBase;

        switch (provider) {
            case "openrouter":
                apiKey = firstNonEmptyEnv(
                        "API_KEY",
                        "MODEL_API_KEY",
                        "OPENROUTER_API_KEY",
                        "OPENAI_API_KEY"
                );
                apiBase = firstNonEmptyEnv(
                        "API_BASE",
                        "MODEL_API_BASE",
                        "OPENROUTER_BASE_URL",
                        "OPENROUTER_API_BASE"
                );
                if (apiBase.isEmpty()) {
                    apiBase = "https://openrouter.ai/api/v1";
                }
                break;
            case "siliconflow":
                apiKey = firstNonEmptyEnv(
                        "API_KEY",
                        "MODEL_API_KEY",
                        "SILICONFLOW_API_KEY",
                        "OPENAI_API_KEY",
                        "OPENROUTER_API_KEY"
                );
                apiBase = firstNonEmptyEnv(
                        "API_BASE",
                        "MODEL_API_BASE",
                        "SILICONFLOW_BASE_URL",
                        "SILICONFLOW_API_BASE"
                );
                if (apiBase.isEmpty()) {
                    apiBase = "https://api.siliconflow.cn/v1";
                }
                break;
            case "dashscope":
                apiKey = firstNonEmptyEnv(
                        "API_KEY",
                        "MODEL_API_KEY",
                        "DASHSCOPE_API_KEY",
                        "OPENAI_API_KEY",
                        "OPENROUTER_API_KEY"
                );
                apiBase = firstNonEmptyEnv(
                        "API_BASE",
                        "MODEL_API_BASE",
                        "DASHSCOPE_BASE_URL",
                        "DASHSCOPE_API_BASE"
                );
                if (apiBase.isEmpty()) {
                    apiBase = "https://dashscope.aliyuncs.com/compatible-mode/v1";
                }
                break;
            default:
                apiKey = firstNonEmptyEnv(
                        "API_KEY",
                        "MODEL_API_KEY",
                        "OPENAI_API_KEY",
                        "OPENROUTER_API_KEY"
                );
                apiBase = firstNonEmptyEnv(
                        "API_BASE",
                        "MODEL_API_BASE",
                        "OPENAI_BASE_URL",
                        "OPENAI_API_BASE"
                );
                if (apiBase.isEmpty()) {
                    apiBase = "https://api.openai.com/v1";
                }
                break;
        }

        return new ModelSettings(provider, apiKey, apiBase);
    }

    public static final class ModelSettings {
        private final String provider;
        private final String apiKey;
        private final String apiBase;

        public ModelSettings(String provider, String apiKey, String apiBase) {
            this.provider = provider;
            this.apiKey = apiKey;
            this.apiBase = apiBase;
        }

        public String getProvider() {
            return provider;
        }

        public String getApiKey() {
            return apiKey;
        }

        public String getApiBase() {
            return apiBase;
        }
    }
}
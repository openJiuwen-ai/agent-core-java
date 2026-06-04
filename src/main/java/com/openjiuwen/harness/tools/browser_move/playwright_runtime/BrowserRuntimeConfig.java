package com.openjiuwen.harness.tools.browser_move.playwright_runtime;

import com.openjiuwen.core.foundation.tool.mcp.McpServerConfig;
import com.openjiuwen.harness.tools.browser_move.utils.EnvUtils;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Minimal config helpers mirroring Python browser_move runtime config.
 *
 * <p>Mirrors Python's {@code openjiuwen.harness.tools.browser_move.playwright_runtime.config}.</p>
 */
public final class BrowserRuntimeConfig {

    public static final int DEFAULT_BROWSER_TIMEOUT_S = EnvUtils.DEFAULT_BROWSER_TIMEOUT_S;
    public static final String DEFAULT_MODEL_NAME = EnvUtils.DEFAULT_MODEL_NAME;

    private BrowserRuntimeConfig() {
    }

    public static RuntimeSettings buildRuntimeSettings() {
        return buildRuntimeSettings(System.getenv());
    }

    public static RuntimeSettings buildRuntimeSettings(Map<String, String> env) {
        Map<String, String> safeEnv = env != null ? env : Map.of();
        ModelSettings modelSettings = resolveModelSettings(safeEnv);
        int timeoutS = resolveInt(safeEnv, DEFAULT_BROWSER_TIMEOUT_S, 1,
                "BROWSER_TIMEOUT_S", "PLAYWRIGHT_TOOL_TIMEOUT_S");
        BrowserRunGuardrails guardrails = buildBrowserGuardrails(safeEnv, timeoutS);
        McpServerConfig mcpConfig = buildPlaywrightMcpConfig(safeEnv, timeoutS);
        return new RuntimeSettings(
                modelSettings.provider(),
                modelSettings.apiKey(),
                modelSettings.apiBase(),
                firstNonEmpty(safeEnv, DEFAULT_MODEL_NAME, "MODEL_NAME"),
                mcpConfig,
                guardrails
        );
    }

    public static McpServerConfig buildPlaywrightMcpConfig(int timeoutS) {
        return buildPlaywrightMcpConfig(System.getenv(), timeoutS);
    }

    public static McpServerConfig buildPlaywrightMcpConfig(Map<String, String> env, int timeoutS) {
        Map<String, String> safeEnv = env != null ? env : Map.of();
        Map<String, Object> params = new HashMap<>();
        params.put("command", firstNonEmpty(safeEnv, EnvUtils.DEFAULT_PLAYWRIGHT_MCP_COMMAND, "PLAYWRIGHT_MCP_COMMAND"));
        params.put("args", EnvUtils.parseCommandArgs(firstNonEmpty(
                safeEnv,
                EnvUtils.DEFAULT_PLAYWRIGHT_MCP_ARGS,
                "PLAYWRIGHT_MCP_ARGS")));
        params.put("cwd", resolvePlaywrightMcpCwd(safeEnv));
        params.put("timeout_s", timeoutS);
        return McpServerConfig.builder()
                .serverId("playwright_official_stdio")
                .serverName("playwright-official")
                .serverPath("stdio://playwright")
                .clientType("stdio")
                .params(params)
                .build();
    }

    private static BrowserRunGuardrails buildBrowserGuardrails(Map<String, String> env, int timeoutS) {
        BrowserRunGuardrails guardrails = new BrowserRunGuardrails();
        guardrails.setMaxSteps(resolveInt(env, EnvUtils.DEFAULT_GUARDRAIL_MAX_STEPS, 1,
                "BROWSER_GUARDRAIL_MAX_STEPS"));
        guardrails.setMaxFailures(resolveInt(env, EnvUtils.DEFAULT_GUARDRAIL_MAX_FAILURES, 0,
                "BROWSER_GUARDRAIL_MAX_FAILURES"));
        guardrails.setTimeoutS(timeoutS);
        guardrails.setRetryOnce(resolveBoolean(env, EnvUtils.DEFAULT_GUARDRAIL_RETRY_ONCE,
                "BROWSER_GUARDRAIL_RETRY_ONCE"));
        guardrails.setResumeOnMaxIterations(resolveBoolean(env, false,
                "BROWSER_GUARDRAIL_RESUME_ON_MAX_ITERATIONS"));
        return guardrails;
    }

    private static ModelSettings resolveModelSettings(Map<String, String> env) {
        String provider = normalizeProvider(firstNonEmpty(env, "", "MODEL_PROVIDER", "MODEL_CLIENT_PROVIDER"));
        String explicitApiBase = firstNonEmpty(env, "", "API_BASE", "MODEL_API_BASE");
        if (provider.isBlank()) {
            String baseHint = !explicitApiBase.isBlank() ? explicitApiBase : firstNonEmpty(env, "",
                    "OPENROUTER_BASE_URL", "OPENROUTER_API_BASE",
                    "SILICONFLOW_BASE_URL", "SILICONFLOW_API_BASE",
                    "DASHSCOPE_BASE_URL", "DASHSCOPE_API_BASE",
                    "OPENAI_BASE_URL", "OPENAI_API_BASE");
            provider = inferProviderFromApiBase(baseHint);
            if (provider.isBlank()) {
                if (!firstNonEmpty(env, "", "OPENROUTER_API_KEY").isBlank()) {
                    provider = "openrouter";
                } else if (!firstNonEmpty(env, "", "SILICONFLOW_API_KEY").isBlank()) {
                    provider = "siliconflow";
                } else if (!firstNonEmpty(env, "", "DASHSCOPE_API_KEY").isBlank()) {
                    provider = "dashscope";
                } else {
                    provider = "openai";
                }
            }
        }

        String apiKey;
        String apiBase;
        switch (provider) {
            case "openrouter" -> {
                apiKey = firstNonEmpty(env, "", "API_KEY", "MODEL_API_KEY", "OPENROUTER_API_KEY", "OPENAI_API_KEY");
                apiBase = firstNonEmpty(env, "https://openrouter.ai/api/v1",
                        "API_BASE", "MODEL_API_BASE", "OPENROUTER_BASE_URL", "OPENROUTER_API_BASE");
            }
            case "siliconflow" -> {
                apiKey = firstNonEmpty(env, "", "API_KEY", "MODEL_API_KEY", "SILICONFLOW_API_KEY",
                        "OPENAI_API_KEY", "OPENROUTER_API_KEY");
                apiBase = firstNonEmpty(env, "https://api.siliconflow.cn/v1",
                        "API_BASE", "MODEL_API_BASE", "SILICONFLOW_BASE_URL", "SILICONFLOW_API_BASE");
            }
            case "dashscope" -> {
                apiKey = firstNonEmpty(env, "", "API_KEY", "MODEL_API_KEY", "DASHSCOPE_API_KEY",
                        "OPENAI_API_KEY", "OPENROUTER_API_KEY");
                apiBase = firstNonEmpty(env, "https://dashscope.aliyuncs.com/compatible-mode/v1",
                        "API_BASE", "MODEL_API_BASE", "DASHSCOPE_BASE_URL", "DASHSCOPE_API_BASE");
            }
            default -> {
                provider = "openai";
                apiKey = firstNonEmpty(env, "", "API_KEY", "MODEL_API_KEY", "OPENAI_API_KEY", "OPENROUTER_API_KEY");
                apiBase = firstNonEmpty(env, "https://api.openai.com/v1",
                        "API_BASE", "MODEL_API_BASE", "OPENAI_BASE_URL", "OPENAI_API_BASE");
            }
        }
        return new ModelSettings(provider, apiKey, apiBase);
    }

    private static String resolvePlaywrightMcpCwd(Map<String, String> env) {
        String configured = firstNonEmpty(env, "",
                "PLAYWRIGHT_RUNTIME_MCP_CWD",
                "BROWSER_RUNTIME_MCP_CWD",
                "PLAYWRIGHT_RUNTIME_WORKDIR",
                "BROWSER_RUNTIME_WORKDIR");
        return configured.isBlank()
                ? Path.of("").toAbsolutePath().normalize().toString()
                : Path.of(configured).toAbsolutePath().normalize().toString();
    }

    private static int resolveInt(Map<String, String> env, int defaultValue, int minimum, String... keys) {
        for (String key : keys) {
            String raw = env.getOrDefault(key, "").trim();
            if (raw.isBlank()) {
                continue;
            }
            try {
                int value = Integer.parseInt(raw);
                if (value >= minimum) {
                    return value;
                }
            } catch (NumberFormatException ignored) {
            }
        }
        return defaultValue;
    }

    private static boolean resolveBoolean(Map<String, String> env, boolean defaultValue, String... keys) {
        for (String key : keys) {
            String raw = env.getOrDefault(key, "").trim().toLowerCase(Locale.ROOT);
            if (raw.isBlank()) {
                continue;
            }
            if (EnvUtils.TRUTHY_ENV_VALUES.contains(raw)) {
                return true;
            }
            if (EnvUtils.FALSY_ENV_VALUES.contains(raw)) {
                return false;
            }
        }
        return defaultValue;
    }

    private static String firstNonEmpty(Map<String, String> env, String defaultValue, String... keys) {
        for (String key : keys) {
            String value = env.get(key);
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return defaultValue;
    }

    private static String normalizeProvider(String provider) {
        String lowered = provider == null ? "" : provider.trim().toLowerCase(Locale.ROOT);
        if ("alibaba".equals(lowered) || "aliyun".equals(lowered)) {
            return "dashscope";
        }
        if ("silicon-flow".equals(lowered) || "silicon_flow".equals(lowered)) {
            return "siliconflow";
        }
        return lowered;
    }

    private static String inferProviderFromApiBase(String apiBase) {
        String base = apiBase == null ? "" : apiBase.toLowerCase(Locale.ROOT);
        if (base.contains("openrouter.ai")) {
            return "openrouter";
        }
        if (base.contains("siliconflow.cn") || base.contains("siliconflow")) {
            return "siliconflow";
        }
        if (base.contains("dashscope.aliyuncs.com") || base.contains("dashscope")) {
            return "dashscope";
        }
        return base.isBlank() ? "" : "openai";
    }

    private record ModelSettings(String provider, String apiKey, String apiBase) {
    }
}

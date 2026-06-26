/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.browser_move.playwright_runtime;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.foundation.tool.mcp.McpServerConfig;
import com.openjiuwen.core.single_agent.AbilityManager;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Agent builders for runtime and browser worker.
 *
 * <p>Mirrors Python's browser worker helpers in
 * {@code openjiuwen/harness/tools/browser_move/playwright_runtime/agents.py}.</p>
 */
public final class PlaywrightAgents {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private PlaywrightAgents() {
    }

    public static String buildBrowserWorkerSystemPrompt(String screenshotSubdir, String artifactsSubdir) {
        String screenshots = normalizeSubdir(screenshotSubdir, "screenshots");
        String artifacts = normalizeSubdir(artifactsSubdir, "artifacts");
        return "You are a browser worker agent.\n"
                + "Execute browser tasks step-by-step with Playwright MCP tools and approved runtime helper tools only.\n"
                + "Keep actions targeted and avoid unnecessary page snapshots.\n"
                + "Use browser_probe_interactives for page-level controls and browser_probe_cards for repeated cards/listings.\n"
                + "Never launch nested browser tasks from the browser worker.\n"
                + "If a screenshot is needed, save it under '" + screenshots + "/'.\n"
                + "If output files are produced, write them to '" + artifacts + "/'.\n"
                + "Final output MUST be a single JSON object with keys ok, final, page, screenshot, error, and status.";
    }

    public static Map<String, Object> buildBrowserWorkerAgent(
            String provider,
            String apiKey,
            String apiBase,
            String modelName,
            McpServerConfig mcpConfig,
            int maxSteps
    ) {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("provider", provider == null ? "" : provider);
        config.put("api_key", apiKey == null ? "" : apiKey);
        config.put("api_base", apiBase == null ? "" : apiBase);
        config.put("model_name", modelName == null ? "" : modelName);
        config.put("mcp_cfg", mcpConfig);
        config.put("max_steps", Math.max(1, maxSteps));
        config.put("system_prompt", buildBrowserWorkerSystemPrompt("screenshots", "artifacts"));
        return config;
    }

    public static CompatibleToolExecutor ensureExecuteSignatureCompat(CompatibleToolExecutor executor) {
        return ensureExecuteSignatureCompat(executor, System.getenv());
    }

    public static CompatibleToolExecutor ensureExecuteSignatureCompat(
            CompatibleToolExecutor executor,
            Map<String, String> env
    ) {
        if (executor == null) {
            return (ctx, toolCall, session, tag) -> CompletableFuture.completedFuture(List.of());
        }
        double timeoutSeconds = resolveToolTimeoutSeconds(env, 180.0D);
        long timeoutMillis = Math.max(1L, Math.round(timeoutSeconds * 1000.0D));
        return (ctx, toolCall, session, tag) -> CompletableFuture.supplyAsync(() -> {
            dropNoneToolArguments(toolCall);
            String toolNames = formatToolNames(toolCall);
            try {
                return executor.execute(ctx, toolCall, session, tag)
                        .toCompletableFuture()
                        .get(timeoutMillis, TimeUnit.MILLISECONDS);
            } catch (TimeoutException error) {
                throw new CompletionException(new RuntimeException(
                        "tool_execution_timeout: tools=" + toolNames + ", timeout_s="
                                + String.format(Locale.ROOT, "%.1f", timeoutSeconds),
                        error
                ));
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                throw new CompletionException(error);
            } catch (Exception error) {
                throw new CompletionException(error);
            }
        });
    }

    static void dropNoneToolArguments(ToolCall toolCall) {
        if (toolCall == null || toolCall.getArguments() == null) {
            return;
        }
        try {
            Object parsed = OBJECT_MAPPER.readValue(toolCall.getArguments(), Object.class);
            Object cleaned = removeNullValues(parsed);
            if (cleaned == null) {
                cleaned = Map.of();
            }
            if (!Objects.equals(parsed, cleaned)) {
                toolCall.setArguments(OBJECT_MAPPER.writeValueAsString(cleaned));
            }
        } catch (JsonProcessingException ignored) {
            // Invalid JSON is handled by the downstream ability manager, matching Python's tolerant wrapper.
        }
    }

    private static Object removeNullValues(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> cleaned = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                Object nested = removeNullValues(entry.getValue());
                if (nested != null) {
                    cleaned.put(String.valueOf(entry.getKey()), nested);
                }
            }
            return cleaned;
        }
        if (value instanceof List<?> list) {
            List<Object> cleaned = new ArrayList<>();
            for (Object item : list) {
                Object nested = removeNullValues(item);
                if (nested != null) {
                    cleaned.add(nested);
                }
            }
            return cleaned;
        }
        return value;
    }

    private static String formatToolNames(ToolCall toolCall) {
        if (toolCall == null || toolCall.getName() == null || toolCall.getName().isBlank()) {
            return "<unknown>";
        }
        return toolCall.getName();
    }

    private static double resolveToolTimeoutSeconds(Map<String, String> env, double defaultValue) {
        for (String key : List.of("PLAYWRIGHT_TOOL_TIMEOUT_S", "PLAYWRIGHT_MCP_TIMEOUT_S", "BROWSER_TIMEOUT_S")) {
            String raw = envValue(env, key);
            if (raw.isBlank()) {
                continue;
            }
            try {
                double parsed = Double.parseDouble(raw);
                if (parsed > 0.0D) {
                    return parsed;
                }
            } catch (NumberFormatException ignored) {
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

    private static String normalizeSubdir(String value, String fallback) {
        String text = value == null ? "" : value.trim().replace('\\', '/');
        while (text.startsWith("/")) {
            text = text.substring(1);
        }
        while (text.endsWith("/")) {
            text = text.substring(0, text.length() - 1);
        }
        return text.isBlank() ? fallback : text;
    }

    @FunctionalInterface
    public interface CompatibleToolExecutor {
        CompletionStage<List<AbilityManager.ExecutionResult>> execute(
                Object ctx,
                ToolCall toolCall,
                Object session,
                String tag
        );
    }
}

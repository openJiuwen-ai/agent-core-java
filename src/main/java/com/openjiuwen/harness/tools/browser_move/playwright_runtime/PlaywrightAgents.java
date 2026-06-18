/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.browser_move.playwright_runtime;

import com.openjiuwen.core.foundation.tool.mcp.McpServerConfig;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Agent builders for runtime and browser worker.
 *
 * <p>Mirrors Python's browser worker helpers in
 * {@code openjiuwen/harness/tools/browser_move/playwright_runtime/agents.py}.</p>
 */
public final class PlaywrightAgents {

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
}

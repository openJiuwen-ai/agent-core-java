/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.browser_move.playwright_runtime;

import lombok.Builder;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Playwright runtime browser tools configuration.
 *
 * <p>Mirrors Python's browser_tools in
 * {@code openjiuwen.harness.tools.browser_move.playwright_runtime.browser_tools}.
 */
public final class BrowserToolsConfig {

    private static final Logger LOG = LoggerFactory.getLogger(BrowserToolsConfig.class);

    /** Standard browser tools. */
    public static final List<String> STANDARD_BROWSER_TOOLS = List.of(
            "browser_navigate",
            "browser_click",
            "browser_type",
            "browser_select",
            "browser_screenshot",
            "browser_scroll",
            "browser_wait",
            "browser_inspect",
            "browser_extract"
    );

    /**
     * Build MCP config for Playwright browser tools.
     */
    public static Map<String, Object> buildPlaywrightMcpConfig() {
        Map<String, Object> mcpConfig = new LinkedHashMap<>();
        mcpConfig.put("server_type", "playwright");
        mcpConfig.put("tools", STANDARD_BROWSER_TOOLS);
        LOG.debug("[BrowserToolsConfig] build_playwright_mcp_config tools={}", STANDARD_BROWSER_TOOLS.size());
        return mcpConfig;
    }

    /**
     * Build guardrails for browser operations.
     */
    public static List<Object> buildBrowserGuardrails() {
        List<Object> guardrails = new ArrayList<>();
        LOG.debug("[BrowserToolsConfig] build_browser_guardrails");
        return guardrails;
    }

    /**
     * Tool card configuration.
     */
    @Data
    @Builder
    public static class ToolCardConfig {
        private String name;
        private String description;
        private Map<String, Object> parameters;
        private boolean requiresConfirmation;
    }
}
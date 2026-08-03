/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.browser_move.playwright_runtime;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.openjiuwen.core.foundation.tool.mcp.McpServerConfig;

/**
 * Browser runtime model and MCP settings.
 *
 * <p>Mirrors Python's {@code RuntimeSettings} in
 * {@code openjiuwen/harness/tools/browser_move/playwright_runtime/config.py}.</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RuntimeSettings {

    private String provider;
    private String apiKey;
    private String apiBase;
    private String modelName;
    private McpServerConfig mcpConfig;
    private BrowserRunGuardrails guardrails;

    public RuntimeSettings() {
        this("", "", "", "", BrowserRuntimeConfig.buildPlaywrightMcpConfig(),
                BrowserRuntimeConfig.buildBrowserGuardrails());
    }

    public RuntimeSettings(
            String provider,
            String apiKey,
            String apiBase,
            String modelName,
            McpServerConfig mcpConfig,
            BrowserRunGuardrails guardrails
    ) {
        this.provider = provider == null ? "" : provider;
        this.apiKey = apiKey == null ? "" : apiKey;
        this.apiBase = apiBase == null ? "" : apiBase;
        this.modelName = modelName == null ? "" : modelName;
        this.mcpConfig = mcpConfig == null ? BrowserRuntimeConfig.buildPlaywrightMcpConfig() : mcpConfig;
        this.guardrails = guardrails == null ? BrowserRuntimeConfig.buildBrowserGuardrails() : guardrails;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider == null ? "" : provider;
    }

    @JsonProperty("api_key")
    public String getApiKey() {
        return apiKey;
    }

    @JsonProperty("api_key")
    public void setApiKey(String apiKey) {
        this.apiKey = apiKey == null ? "" : apiKey;
    }

    @JsonProperty("api_base")
    public String getApiBase() {
        return apiBase;
    }

    @JsonProperty("api_base")
    public void setApiBase(String apiBase) {
        this.apiBase = apiBase == null ? "" : apiBase;
    }

    @JsonProperty("model_name")
    public String getModelName() {
        return modelName;
    }

    @JsonProperty("model_name")
    public void setModelName(String modelName) {
        this.modelName = modelName == null ? "" : modelName;
    }

    @JsonProperty("mcp_cfg")
    public McpServerConfig getMcpConfig() {
        return mcpConfig;
    }

    @JsonProperty("mcp_cfg")
    public void setMcpConfig(McpServerConfig mcpConfig) {
        this.mcpConfig = mcpConfig == null ? BrowserRuntimeConfig.buildPlaywrightMcpConfig() : mcpConfig;
    }

    public BrowserRunGuardrails getGuardrails() {
        return guardrails;
    }

    public void setGuardrails(BrowserRunGuardrails guardrails) {
        this.guardrails = guardrails == null ? BrowserRuntimeConfig.buildBrowserGuardrails() : guardrails;
    }
}

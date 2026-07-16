
package com.openjiuwen.harness.tools;

import static org.assertj.core.api.Assertions.assertThat;

import com.openjiuwen.core.foundation.tool.mcp.McpServerConfig;
import com.openjiuwen.harness.tools.browser.BrowserJsonUtils;
import com.openjiuwen.harness.tools.browser.BrowserMoveStreamableHttpClient;
import com.openjiuwen.harness.tools.browser.BrowserRuntimeSettings;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

class HarnessBrowserConfigCompatibilityTest {
    @Test
    void runtimeSettingsShouldUseDefaultsAndOverrides() {
        BrowserRuntimeSettings defaults = BrowserRuntimeSettings.buildRuntimeSettings(Map.of());
        BrowserRuntimeSettings overridden =
            BrowserRuntimeSettings.buildRuntimeSettings(Map.of("MODEL_PROVIDER", "openrouter", "OPENROUTER_API_KEY",
                    "test-key", "MODEL_NAME", "google/gemini-3.1-pro-preview", "BROWSER_TIMEOUT_S", "45",
                    "PLAYWRIGHT_MCP_ARGS", "[\"-y\", \"@playwright/mcp@latest\", \"--headless\"]"));

        assertThat(defaults.getProvider()).isEqualTo("openai");
        assertThat(defaults.getApiBase()).isEqualTo("https://api.openai.com/v1");
        assertThat(overridden.getProvider()).isEqualTo("openrouter");
        assertThat(overridden.getApiKey()).isEqualTo("test-key");
        assertThat(overridden.getModelName()).isEqualTo("google/gemini-3.1-pro-preview");
        assertThat(overridden.getGuardrails().getTimeoutS()).isEqualTo(45);
        assertThat(BrowserRuntimeSettings.parseCommandArgs("[\"-y\", \"@playwright/mcp@latest\"]"))
                .isEqualTo(List.of("-y", "@playwright/mcp@latest"));
    }

    @Test
    void browserRuntimeMcpConfigAndJsonUtilsShouldBehave() {
        McpServerConfig disabled = BrowserRuntimeSettings.buildBrowserRuntimeMcpConfig(Map.of());
        McpServerConfig httpCfg = BrowserRuntimeSettings.buildBrowserRuntimeMcpConfig(
                Map.of("PLAYWRIGHT_RUNTIME_MCP_ENABLED", "1", "PLAYWRIGHT_RUNTIME_MCP_CLIENT_TYPE", "http",
                        "PLAYWRIGHT_RUNTIME_MCP_HOST", "127.0.0.1", "PLAYWRIGHT_RUNTIME_MCP_PORT", "8940"));
        BrowserMoveStreamableHttpClient client = new BrowserMoveStreamableHttpClient(McpServerConfig.builder()
                .serverId("playwright-runtime-wrapper").serverName("playwright-runtime-wrapper")
                .serverPath("http://127.0.0.1:8940/mcp").clientType("streamable-http").build());

        assertThat(disabled).isNull();
        assertThat(httpCfg.getClientType()).isEqualTo("streamable-http");
        assertThat(httpCfg.getServerPath()).isEqualTo("http://127.0.0.1:8940/mcp");
        assertThat(client.getServerPath()).isEqualTo("http://127.0.0.1:8940/mcp");
        assertThat(client.getName()).isEqualTo("playwright-runtime-wrapper");
        assertThat(BrowserJsonUtils.extractJsonObject("```json\n{\"ok\": true, \"value\": 1}\n```"))
                .isEqualTo(Map.of("ok", true, "value", 1));
    }
}

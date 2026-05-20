package com.openjiuwen.harness.subagents;

import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.foundation.tool.mcp.McpServerConfig;
import com.openjiuwen.harness.rails.MemoryRail;
import com.openjiuwen.harness.tools.browser.BrowserRuntimeRail;
import com.openjiuwen.harness.tools.browser.BrowserRunGuardrails;
import com.openjiuwen.harness.tools.browser.BrowserRuntimeSettings;
import com.openjiuwen.harness.workspace.Workspace;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BrowserAgentFactoryCompatibilityTest {

    private static BrowserRuntimeSettings settings() {
        return BrowserRuntimeSettings.builder()
                .provider("openai")
                .apiKey("test-key")
                .apiBase("https://example.invalid/v1")
                .modelName("test-model")
                .mcpCfg(McpServerConfig.builder()
                        .serverId("test")
                        .serverName("test")
                        .serverPath("stdio://playwright")
                        .clientType("stdio")
                        .build())
                .guardrails(BrowserRunGuardrails.builder().maxSteps(3).maxFailures(1).timeoutS(30).retryOnce(false).build())
                .build();
    }

    @Test
    void buildBrowserAgentConfigShouldExposeFactoryMetadata() {
        SubAgentConfig spec = BrowserAgentFactory.buildBrowserAgentConfig(settings(), "en");

        assertThat(spec.getAgentCard().getName()).isEqualTo("browser_agent");
        assertThat(spec.getSystemPrompt()).isEqualTo(BrowserAgentFactory.DEFAULT_BROWSER_AGENT_SYSTEM_PROMPT.get("en"));
        assertThat(spec.getFactoryName()).isEqualTo(BrowserAgentFactory.BROWSER_AGENT_FACTORY_NAME);
        assertThat(spec.getFactoryKwargs()).containsKey("settings");
    }

    @Test
    void buildBrowserAgentConfigShouldPreserveCustomRails() {
        MemoryRail memoryRail = new MemoryRail();

        SubAgentConfig spec = BrowserAgentFactory.buildBrowserAgentConfig(
                settings(),
                "en",
                java.util.Map.of("custom_rails", java.util.List.of(memoryRail))
        );

        assertThat(spec.getRails()).containsExactly(memoryRail);
        assertThat(spec.getFactoryKwargs()).containsKey("custom_rails");
    }

    @Test
    void createBrowserAgentShouldReturnDeepAgent() {
        var agent = BrowserAgentFactory.createBrowserAgent(
                settings(),
                "cn",
                Workspace.builder().rootPath(".").language("cn").build(),
                new java.util.ArrayList<>(),
                java.util.List.of()
        );

        assertThat(agent.getCard().getName()).isEqualTo("browser_agent");
        assertThat(agent.getConfig().getTools())
                .hasSize(5)
                .allSatisfy(tool -> assertThat(tool).isInstanceOf(Tool.class));
        assertThat(agent.getConfig().getTools().stream()
                .map(Tool.class::cast)
                .map(tool -> tool.getCard().getName()))
                .containsExactly(
                        "browser_cancel",
                        "browser_clear_cancel",
                        "browser_custom_action",
                        "browser_list_actions",
                        "browser_runtime_health"
                );
        assertThat(agent.getConfig().getRails())
                .anySatisfy(rail -> assertThat(rail).isInstanceOf(BrowserRuntimeRail.class));
    }

    @Test
    void createBrowserAgentShouldMergeCustomRailsWithRuntimeRail() {
        MemoryRail memoryRail = new MemoryRail();
        BrowserRuntimeSettings browserSettings = settings();
        SubAgentConfig spec = BrowserAgentFactory.buildBrowserAgentConfig(
                browserSettings,
                "en",
                java.util.Map.of("custom_rails", java.util.List.of(memoryRail))
        );

        var agent = BrowserAgentFactory.createBrowserAgent(
                browserSettings,
                "en",
                Workspace.builder().rootPath(".").language("en").build(),
                new java.util.ArrayList<>(),
                java.util.List.of(),
                java.util.Map.of("custom_rails", java.util.List.of(memoryRail), "rails_merge_mode", "append")
        );

        assertThat(spec.getRails()).contains(memoryRail);
        assertThat(agent.getConfig().getRails())
                .anySatisfy(rail -> assertThat(rail).isInstanceOf(BrowserRuntimeRail.class));
        assertThat(agent.getConfig().getRails()).contains(memoryRail);
    }
}

package com.openjiuwen.harness.subagents;

import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.foundation.tool.mcp.McpServerConfig;
import com.openjiuwen.harness.deep_agent.DeepAgent;
import com.openjiuwen.harness.rails.memory.MemoryRail;
import com.openjiuwen.harness.rails.DeepAgentRail;
import com.openjiuwen.harness.schema.DeepAgentConfig;
import com.openjiuwen.harness.tools.browser_move.playwright_runtime.RuntimeSettings;
import com.openjiuwen.harness.tools.browser_move.playwright_runtime.BrowserRuntimeRail;
import com.openjiuwen.harness.tools.browser_move.playwright_runtime.BrowserRunGuardrails;
import com.openjiuwen.harness.tools.browser.BrowserRuntimeSettings;
import com.openjiuwen.harness.workspace.Workspace;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class BrowserAgentFactoryCompatibilityTest {

    private static RuntimeSettings settings() {
        return new RuntimeSettings(
                "openai",
                "test-key",
                "https://example.invalid/v1",
                "test-model",
                McpServerConfig.builder()
                        .serverId("test")
                        .serverName("test")
                        .serverPath("stdio://playwright")
                        .clientType("stdio")
                        .build(),
                new BrowserRunGuardrails()
        );
    }

    @Test
    void buildBrowserAgentConfigShouldExposeFactoryMetadata() {
        RuntimeSettings settings = settings();
        DeepAgentConfig.SubAgentConfig spec = BrowserAgentFactory.buildBrowserAgentConfig(
                null,
                null,
                null,
                java.util.List.of(),
                java.util.List.of(),
                java.util.List.of(),
                settings,
                "en",
                false,
                25
        );

        assertThat(spec.getAgentCard().getName()).isEqualTo("browser_agent");
        assertThat(spec.getSystemPrompt()).isEqualTo(BrowserAgentFactory.DEFAULT_BROWSER_AGENT_SYSTEM_PROMPT.get("en"));
        assertThat(spec.getFactoryName()).isEqualTo(BrowserAgentFactory.BROWSER_AGENT_FACTORY_NAME);
        assertThat(spec.getFactoryKwargs()).containsEntry("settings", settings);
    }

    @Test
    void buildBrowserAgentConfigShouldPreserveCustomRails() {
        MemoryRail memoryRail = new MemoryRail();
        RuntimeSettings settings = settings();

        DeepAgentConfig.SubAgentConfig spec = BrowserAgentFactory.buildBrowserAgentConfig(
                null,
                null,
                null,
                java.util.List.of(),
                java.util.List.of(),
                java.util.List.of(memoryRail),
                settings,
                "en",
                false,
                25
        );

        assertThat(spec.getRails()).containsExactly(memoryRail);
        assertThat(spec.getFactoryKwargs()).containsEntry("settings", settings);
    }

    @Test
    void createBrowserAgentShouldReturnDeepAgent() {
        DeepAgent agent = BrowserAgentFactory.createBrowserAgent(
                null,
                java.util.List.of(),
                java.util.List.of(),
                java.util.List.of(),
                null,
                "en",
                settings()
        );

        assertThat(agent.getCard().getName()).isEqualTo("browser_agent");
        assertThat(agent.getTools())
                .hasSize(7);
        assertThat(agent.getTools().values())
                .hasSize(7)
                .allSatisfy(tool -> assertThat(tool).isInstanceOf(Tool.class));
        assertThat(agent.getTools().keySet())
                .containsExactlyInAnyOrder(
                        "browser_cancel_run",
                        "browser_clear_cancel",
                        "browser_custom_action",
                        "browser_list_custom_actions",
                        "browser_probe_cards",
                        "browser_probe_interactives",
                        "browser_runtime_health"
                );
        assertThat(agent.getRails())
                .anySatisfy(rail -> assertThat(rail).isInstanceOf(BrowserRuntimeRail.class));
    }

    @Test
    void createBrowserAgentShouldMergeCustomRailsWithRuntimeRail() {
        MemoryRail memoryRail = new MemoryRail();
        RuntimeSettings browserSettings = settings();
        DeepAgentConfig.SubAgentConfig spec = BrowserAgentFactory.buildBrowserAgentConfig(
                null,
                null,
                null,
                java.util.List.of(),
                java.util.List.of(),
                java.util.List.of(memoryRail),
                browserSettings,
                "en",
                false,
                25
        );

        DeepAgent agent = BrowserAgentFactory.createBrowserAgent(
                null,
                java.util.List.of(),
                java.util.List.of(),
                java.util.List.of(memoryRail),
                null,
                "en",
                browserSettings
        );

        assertThat(spec.getRails()).contains(memoryRail);
        assertThat(agent.deepConfig().getRails())
                .anySatisfy(rail -> assertThat(rail).isInstanceOf(BrowserRuntimeRail.class));
        assertThat(agent.deepConfig().getRails()).anySatisfy(rail -> assertThat(rail).isInstanceOf(MemoryRail.class));
    }
}

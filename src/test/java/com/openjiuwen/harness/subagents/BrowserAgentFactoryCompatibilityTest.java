package com.openjiuwen.harness.subagents;

import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.foundation.tool.mcp.McpServerConfig;
import com.openjiuwen.harness.DeepAgent;
import com.openjiuwen.harness.rails.MemoryRail;
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
        DeepAgentConfig.SubAgentConfig spec = BrowserAgentFactory.buildBrowserAgentConfig(settings());

        assertThat(spec.getAgentCard().getName()).isEqualTo("browser_agent");
        assertThat(spec.getSystemPrompt()).isEqualTo(BrowserAgentFactory.DEFAULT_BROWSER_AGENT_SYSTEM_PROMPT.get("en"));
        assertThat(spec.getFactoryName()).isEqualTo(BrowserAgentFactory.BROWSER_AGENT_FACTORY_NAME);
        assertThat(spec.getFactoryKwargs()).containsKey("settings");
    }

    @Test
    void buildBrowserAgentConfigShouldPreserveCustomRails() {
        MemoryRail memoryRail = new MemoryRail();

        DeepAgentConfig.SubAgentConfig spec = BrowserAgentFactory.buildBrowserAgentConfig(
                settings(),
                null,
                "en",
                java.util.List.of(),
                java.util.List.of(),
                java.util.List.of(memoryRail),
                null,
                "en",
                false,
                25
        );

        assertThat(spec.getRails()).containsExactly(memoryRail);
        assertThat(spec.getFactoryKwargs()).containsKey("settings");
    }

    @Test
    void createBrowserAgentShouldReturnDeepAgent() {
        DeepAgent agent = BrowserAgentFactory.createBrowserAgent(
                settings(),
                java.util.List.of(),
                java.util.List.of(),
                java.util.List.of(),
                null,
                "cn",
                null
        );

        assertThat(agent.getCard().getName()).isEqualTo("browser_agent");
        assertThat(agent.getTools())
                .hasSize(5);
        assertThat(agent.getTools().values())
                .hasSize(5)
                .allSatisfy(tool -> assertThat(tool).isInstanceOf(Tool.class));
        assertThat(agent.getTools().keySet())
                .containsExactly(
                        "browser_cancel",
                        "browser_clear_cancel",
                        "browser_custom_action",
                        "browser_list_actions",
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
                browserSettings,
                null,
                "en",
                java.util.List.of(),
                java.util.List.of(),
                java.util.List.of(memoryRail),
                null,
                "en",
                false,
                25
        );

        DeepAgent agent = BrowserAgentFactory.createBrowserAgent(
                browserSettings,
                java.util.List.of(),
                java.util.List.of(),
                java.util.List.of(memoryRail),
                null,
                "en",
                null
        );

        assertThat(spec.getRails()).contains(memoryRail);
        assertThat(agent.deepConfig().getRails())
                .anySatisfy(rail -> assertThat(rail).isInstanceOf(BrowserRuntimeRail.class));
        assertThat(agent.deepConfig().getRails()).anySatisfy(rail -> assertThat(rail).isInstanceOf(MemoryRail.class));
    }
}

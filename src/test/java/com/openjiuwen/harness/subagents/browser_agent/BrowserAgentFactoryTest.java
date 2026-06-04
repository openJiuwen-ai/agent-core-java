package com.openjiuwen.harness.subagents.browser_agent;

import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.harness.DeepAgent;
import com.openjiuwen.harness.tools.browser_move.playwright_runtime.BrowserRuntimeRail;
import com.openjiuwen.harness.tools.browser_move.playwright_runtime.RuntimeSettings;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BrowserAgentFactoryTest {

    private Model fakeModel() {
        ModelClientConfig clientConfig = ModelClientConfig.builder()
                .clientProvider("openai")
                .apiKey("test-key")
                .apiBase("https://example.invalid/v1")
                .timeout(30)
                .build();
        ModelRequestConfig requestConfig = ModelRequestConfig.builder()
                .modelName("test-model")
                .build();
        return new Model(clientConfig, requestConfig);
    }

    @Test
    void buildBrowserAgentConfigUsesBrowserFactoryAndSettings() {
        Model model = fakeModel();
        BrowserAgentConfigSpec spec = BrowserAgentFactory.buildBrowserAgentConfig(model, null, "en");

        assertEquals("browser_agent", spec.agentCard().getName());
        assertEquals(BrowserAgentFactory.BROWSER_AGENT_FACTORY_NAME, spec.factoryName());
        assertEquals(BrowserAgentFactory.DEFAULT_BROWSER_AGENT_SYSTEM_PROMPT.get("en"), spec.systemPrompt());
        assertNotNull(spec.settings());
        assertEquals("test-model", spec.settings().modelName());
    }

    @Test
    void createBrowserAgentInjectsBrowserToolsAndRuntimeRail() {
        DeepAgent agent = BrowserAgentFactory.createBrowserAgent(fakeModel());

        assertEquals("browser_agent", agent.getCard().getName());
        assertTrue(agent.getConfig() instanceof com.openjiuwen.harness.DeepAgentConfig);
        com.openjiuwen.harness.DeepAgentConfig config = (com.openjiuwen.harness.DeepAgentConfig) agent.getConfig();
        assertEquals(BrowserAgentFactory.DEFAULT_BROWSER_AGENT_SYSTEM_PROMPT.get("cn"), config.getSystemPrompt());
        assertEquals(5, config.getTools().size());
        assertTrue(config.getTools().stream().map(ToolCard::getName).toList().containsAll(List.of(
                "browser_cancel_run",
                "browser_clear_cancel",
                "browser_custom_action",
                "browser_list_custom_actions",
                "browser_runtime_health"
        )));
        assertEquals(1, config.getRails().size());
        assertInstanceOf(BrowserRuntimeRail.class, config.getRails().getFirst());
    }

    @Test
    void buildBrowserAgentConfigCarriesExplicitSettings() {
        RuntimeSettings settings = new RuntimeSettings(
                "openai",
                "explicit-key",
                "https://example.invalid/v1",
                "explicit-model",
                com.openjiuwen.harness.tools.browser_move.playwright_runtime.BrowserRuntimeConfig.buildPlaywrightMcpConfig(90),
                new com.openjiuwen.harness.tools.browser_move.playwright_runtime.BrowserRunGuardrails(3, 1, 90, false)
        );
        BrowserAgentConfigSpec spec = BrowserAgentFactory.buildBrowserAgentConfig(fakeModel(), settings, "en");

        assertEquals(settings, spec.settings());
        assertEquals("explicit-model", spec.settings().modelName());
    }
}

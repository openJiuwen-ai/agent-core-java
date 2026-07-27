package com.openjiuwen.deepagents;

import com.openjiuwen.deepagents.subagents.DeepAgentSubagents;
import com.openjiuwen.harness.DeepAgent;
import com.openjiuwen.harness.schema.DeepAgentConfig;
import com.openjiuwen.harness.tools.browser.BrowserRunGuardrails;
import com.openjiuwen.harness.tools.browser.BrowserRuntimeSettings;
import com.openjiuwen.harness.workspace.Workspace;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DeepAgentSubagentsCompatibilityTest {

    @Test
    void deepAgentSubagentsShouldExposeHarnessBackedConfigs() {
        DeepAgentConfig.SubAgentConfig code = (DeepAgentConfig.SubAgentConfig) DeepAgentSubagents.buildCodeAgentConfig("en");
        com.openjiuwen.harness.subagents.SubAgentConfig plan = DeepAgentSubagents.buildPlanAgentConfig("en");
        DeepAgentConfig.SubAgentConfig verify = (DeepAgentConfig.SubAgentConfig) DeepAgentSubagents.buildVerificationAgentConfig("cn");

        assertThat(code.getAgentCard().getName()).isEqualTo("code_agent");
        assertThat(plan.getAgentCard().getName()).isEqualTo("plan_agent");
        assertThat(verify.getAgentCard().getName()).isEqualTo("verification_agent");
    }

    @Test
    void deepAgentSubagentsShouldCreateAgentsThroughUnifiedFacade() {
        Workspace workspace = new Workspace(".", "en");

        com.openjiuwen.harness.deep_agent.DeepAgent explore =
                (com.openjiuwen.harness.deep_agent.DeepAgent) DeepAgentSubagents.createExploreAgent("en", workspace);
        DeepAgent research = (DeepAgent) DeepAgentSubagents.create("research_agent", "en", workspace);

        assertThat(explore.getCard().getName()).isEqualTo("explore_agent");
        assertThat(research.getCard().getName()).startsWith("research_agent");
    }

    @Test
    void deepAgentSubagentsShouldCreateBrowserAgent() {
        BrowserRuntimeSettings settings = BrowserRuntimeSettings.builder()
                .provider("openai")
                .apiKey("test")
                .apiBase("https://example.invalid/v1")
                .modelName("test-model")
                .guardrails(BrowserRunGuardrails.builder().timeoutS(5).build())
                .build();

        DeepAgent browser = (DeepAgent) DeepAgentSubagents.createBrowserAgent(
                settings,
                "en",
                new Workspace(".", "en")
        );

        assertThat(browser.getCard().getName()).isEqualTo("browser_agent");
    }
}

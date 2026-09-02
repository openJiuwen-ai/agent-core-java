
package com.openjiuwen.deepagents;

import static org.assertj.core.api.Assertions.assertThat;

import com.openjiuwen.deepagents.subagents.DeepAgentSubagents;
import com.openjiuwen.harness.deep_agent.DeepAgent;
import com.openjiuwen.harness.subagents.SubAgentConfig;
import com.openjiuwen.harness.tools.browser.BrowserRunGuardrails;
import com.openjiuwen.harness.tools.browser.BrowserRuntimeSettings;
import com.openjiuwen.harness.workspace.Workspace;

import org.junit.jupiter.api.Test;

class DeepAgentSubagentsCompatibilityTest {
    @Test
    void deepAgentSubagentsShouldExposeHarnessBackedConfigs() {
        SubAgentConfig code = DeepAgentSubagents.buildCodeAgentConfig("en");
        SubAgentConfig plan = DeepAgentSubagents.buildPlanAgentConfig("en");
        SubAgentConfig verify = DeepAgentSubagents.buildVerificationAgentConfig("cn");

        assertThat(code.getAgentCard().getName()).isEqualTo("code_agent");
        assertThat(plan.getAgentCard().getName()).isEqualTo("plan_agent");
        assertThat(verify.getAgentCard().getName()).isEqualTo("verification_agent");
    }

    @Test
    void deepAgentSubagentsShouldCreateAgentsThroughUnifiedFacade() {
        Workspace workspace = Workspace.builder().rootPath(".").language("en").build();

        DeepAgent explore = DeepAgentSubagents.createExploreAgent("en", workspace);
        DeepAgent research = DeepAgentSubagents.create("research_agent", "en", workspace);

        assertThat(explore.getCard().getName()).isEqualTo("explore_agent");
        assertThat(research.getCard().getName()).startsWith("research_agent");
    }

    @Test
    void deepAgentSubagentsShouldCreateBrowserAgent() {
        BrowserRuntimeSettings settings =
            BrowserRuntimeSettings.builder().provider("openai").apiKey("test").apiBase("https://example.invalid/v1")
                    .modelName("test-model").guardrails(BrowserRunGuardrails.builder().timeoutS(5).build()).build();

        DeepAgent browser = DeepAgentSubagents.createBrowserAgent(settings, "en",
                Workspace.builder().rootPath(".").language("en").build());

        assertThat(browser.getCard().getName()).isEqualTo("browser_agent");
    }
}

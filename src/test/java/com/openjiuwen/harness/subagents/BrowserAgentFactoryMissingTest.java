/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.subagents;

import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.ModelInvokeOptions;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.foundation.tool.mcp.McpServerConfig;
import com.openjiuwen.harness.deep_agent.DeepAgent;
import com.openjiuwen.harness.schema.DeepAgentConfig;
import com.openjiuwen.harness.tools.browser_move.playwright_runtime.BrowserAgentRuntime;
import com.openjiuwen.harness.tools.browser_move.playwright_runtime.BrowserRunGuardrails;
import com.openjiuwen.harness.tools.browser_move.playwright_runtime.BrowserRuntimeRail;
import com.openjiuwen.harness.tools.browser_move.playwright_runtime.RuntimeSettings;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * <p>Mirrors Python's browser-agent factory tests in
 * {@code tests/unit_tests/harness/tools/browser_move/test_create_browser_agent.py}.</p>
 */
class BrowserAgentFactoryMissingTest {

    @Test
    void defaultWiringCreatesOneAgent() {
        DeepAgent agent = BrowserAgentFactory.createBrowserAgent(fakeModel(), null, null, null, null, "cn",
                fakeSettings());

        assertThat(agent).isNotNull();
        assertThat(agent.getCard().getName()).isEqualTo("browser_agent");
    }

    @Test
    void defaultWiringMainAgentCardIsBrowserAgent() {
        DeepAgent agent = BrowserAgentFactory.createBrowserAgent(fakeModel(), null, null, null, null, "cn",
                fakeSettings());

        assertThat(agent.getCard().getName()).isEqualTo("browser_agent");
    }

    @Test
    void defaultWiringMainAgentHasNoSubagents() {
        DeepAgent agent = BrowserAgentFactory.createBrowserAgent(fakeModel(), null, null, null, null, "cn",
                fakeSettings());

        assertThat(agent.getSubagents()).isEmpty();
    }

    @Test
    void defaultWiringMainAgentReceivesBrowserHelperTools() {
        DeepAgent agent = BrowserAgentFactory.createBrowserAgent(fakeModel(), null, null, null, null, "cn",
                fakeSettings());

        assertThat(agent.getTools().keySet()).containsAll(browserToolNames());
    }

    @Test
    void defaultWiringDoesNotPreRegisterPlaywrightMcpOnSubagent() {
        RuntimeSettings settings = fakeSettings();
        DeepAgent agent = BrowserAgentFactory.createBrowserAgent(fakeModel(), null, null, null, null, "cn",
                settings);

        assertThat(agent.deepConfig().getMcps()).doesNotContain(settings.getMcpConfig());
    }

    @Test
    void defaultWiringMainAgentHasBrowserRuntimeRail() {
        DeepAgent agent = BrowserAgentFactory.createBrowserAgent(fakeModel(), null, null, null, null, "cn",
                fakeSettings());

        assertThat(agent.getRails()).anySatisfy(rail -> assertThat(rail).isInstanceOf(BrowserRuntimeRail.class));
    }

    @Test
    void defaultWiringDoesNotAddSysOperationRail() {
        DeepAgent agent = BrowserAgentFactory.createBrowserAgent(fakeModel(), null, null, null, null, "cn",
                fakeSettings());

        assertThat(agent.getRails()).noneSatisfy(rail -> assertThat(rail.getClass().getSimpleName())
                .isEqualTo("SysOperationRail"));
    }

    @Test
    void defaultWiringBuildToolsCalledWithRuntimeInstance() throws Exception {
        DeepAgent agent = BrowserAgentFactory.createBrowserAgent(fakeModel(), null, null, null, null, "cn",
                fakeSettings());
        BrowserAgentRuntime runtime = runtimeFromRail(agent);

        Tool probeCards = agent.getTools().get("browser_probe_cards");

        assertThat(runtimeFromTool(probeCards)).isSameAs(runtime);
    }

    @Test
    void customSubagentsAreForwarded() {
        DeepAgentConfig.SubAgentConfig custom = new DeepAgentConfig.SubAgentConfig(
                "custom_agent", "Custom agent", "Custom prompt");
        DeepAgent agent = BrowserAgentFactory.createBrowserAgent(fakeModel(), null, null, List.of(custom), null,
                null, "cn", fakeSettings());

        assertThat(agent.getSubagents()).containsEntry("custom_agent", custom);
    }

    @Test
    void settingsForwardedToRuntimeConstructor() {
        RuntimeSettings settings = fakeSettings();
        DeepAgent agent = BrowserAgentFactory.createBrowserAgent(fakeModel(), null, null, null, null, "cn",
                settings);
        BrowserAgentRuntime runtime = runtimeFromRail(agent);

        assertThat(runtime.getService().getProvider()).isEqualTo(settings.getProvider());
        assertThat(runtime.getService().getApiKey()).isEqualTo(settings.getApiKey());
        assertThat(runtime.getService().getApiBase()).isEqualTo(settings.getApiBase());
        assertThat(runtime.getService().getModelName()).isEqualTo(settings.getModelName());
        assertThat(runtime.getService().getMcpConfig()).isSameAs(settings.getMcpConfig());
        assertThat(runtime.getService().getGuardrails()).isSameAs(settings.getGuardrails());
    }

    @Test
    void languageEnUsesEnglishPrompt() {
        DeepAgent agent = BrowserAgentFactory.createBrowserAgent(fakeModel(), null, null, null, null, "en",
                fakeSettings());

        assertThat(agent.deepConfig().getSystemPrompt())
                .isEqualTo(BrowserAgentFactory.DEFAULT_BROWSER_AGENT_SYSTEM_PROMPT.get("en"));
    }

    @Test
    void languageCnUsesChinesePrompt() {
        DeepAgent agent = BrowserAgentFactory.createBrowserAgent(fakeModel(), null, null, null, null, "cn",
                fakeSettings());

        assertThat(agent.deepConfig().getSystemPrompt())
                .isEqualTo(BrowserAgentFactory.DEFAULT_BROWSER_AGENT_SYSTEM_PROMPT.get("cn"));
    }

    @Test
    void userToolsAreMergedWithBrowserTools() {
        Tool userTool = new MarkerTool("user_tool");
        DeepAgent agent = BrowserAgentFactory.createBrowserAgent(fakeModel(), List.of(userTool), null, null, null,
                "cn", fakeSettings());

        assertThat(agent.getTools()).containsEntry("user_tool", userTool);
        assertThat(agent.getTools().keySet()).containsAll(browserToolNames());
    }

    @Test
    void buildBrowserAgentConfigUsesBrowserFactory() {
        RuntimeSettings settings = fakeSettings();
        DeepAgentConfig.SubAgentConfig spec = BrowserAgentFactory.buildBrowserAgentConfig(
                fakeModel(), null, null, null, null, null, settings, "en", false, 25);

        assertThat(spec).isInstanceOf(DeepAgentConfig.SubAgentConfig.class);
        assertThat(spec.getAgentCard().getName()).isEqualTo("browser_agent");
        assertThat(spec.getSystemPrompt()).isEqualTo(BrowserAgentFactory.DEFAULT_BROWSER_AGENT_SYSTEM_PROMPT.get("en"));
        assertThat(spec.getFactoryName()).isEqualTo(BrowserAgentFactory.BROWSER_AGENT_FACTORY_NAME);
        assertThat(spec.getFactoryKwargs()).containsEntry("settings", settings);
    }

    @Test
    void buildBrowserAgentConfigFallbackUsesModelNameField() {
        ModelClientConfig clientConfig = ModelClientConfig.builder()
                .clientProvider("openai")
                .apiKey("test-key")
                .apiBase("https://example.invalid/v1")
                .build();
        ModelRequestConfig requestConfig = ModelRequestConfig.builder().modelName("test-model-name").build();
        Model model = new Model(new NoopModelClient(), clientConfig, requestConfig);

        DeepAgentConfig.SubAgentConfig spec = BrowserAgentFactory.buildBrowserAgentConfig(model);

        RuntimeSettings settings = (RuntimeSettings) spec.getFactoryKwargs().get("settings");
        assertThat(settings.getModelName()).isEqualTo("test-model-name");
        assertThat(settings.getProvider()).isEqualTo("OpenAI");
        assertThat(settings.getApiKey()).isEqualTo("test-key");
        assertThat(settings.getApiBase()).isEqualTo("https://example.invalid/v1");
    }

    private static Set<String> browserToolNames() {
        return Set.of(
                "browser_cancel_run",
                "browser_clear_cancel",
                "browser_custom_action",
                "browser_list_custom_actions",
                "browser_probe_interactives",
                "browser_probe_cards",
                "browser_runtime_health"
        );
    }

    private static RuntimeSettings fakeSettings() {
        McpServerConfig mcpConfig = McpServerConfig.builder()
                .serverId("test")
                .serverName("test")
                .serverPath("stdio://playwright")
                .clientType("stdio")
                .params(Map.of("cwd", "."))
                .build();
        return new RuntimeSettings(
                "openai",
                "test-key",
                "https://example.invalid/v1",
                "test-model",
                mcpConfig,
                new BrowserRunGuardrails(3, 1, 30, false, false)
        );
    }

    private static Model fakeModel() {
        return new Model(new NoopModelClient());
    }

    private static BrowserAgentRuntime runtimeFromRail(DeepAgent agent) {
        return agent.getRails().stream()
                .filter(BrowserRuntimeRail.class::isInstance)
                .map(BrowserRuntimeRail.class::cast)
                .findFirst()
                .orElseThrow()
                .getRuntime();
    }

    private static BrowserAgentRuntime runtimeFromTool(Tool tool) throws Exception {
        Field runtimeField = tool.getClass().getDeclaredField("runtime");
        runtimeField.setAccessible(true);
        return (BrowserAgentRuntime) runtimeField.get(tool);
    }

    private static final class MarkerTool extends Tool {
        private MarkerTool(String name) {
            super(new ToolCard(name, name, "marker"));
        }
    }

    private static final class NoopModelClient implements Model.ModelClient {
        @Override
        public CompletionStage<AssistantMessage> invoke(List<BaseMessage> messages, ModelInvokeOptions options) {
            return CompletableFuture.completedFuture(new AssistantMessage("ok"));
        }
    }
}

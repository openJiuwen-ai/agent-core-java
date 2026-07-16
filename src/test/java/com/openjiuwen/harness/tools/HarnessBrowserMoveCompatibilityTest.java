
package com.openjiuwen.harness.tools;

import static org.assertj.core.api.Assertions.assertThat;

import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.foundation.tool.mcp.McpServerConfig;
import com.openjiuwen.harness.tools.browser.BrowserActionController;
import com.openjiuwen.harness.tools.browser.BrowserAgentRuntime;
import com.openjiuwen.harness.tools.browser.BrowserRunGuardrails;
import com.openjiuwen.harness.tools.browser.BrowserRuntimeSettings;
import com.openjiuwen.harness.tools.browser.BrowserRuntimeTools;
import com.openjiuwen.harness.tools.browser.BrowserService;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

class HarnessBrowserMoveCompatibilityTest {
    @Test
    void runtimeSettingsShouldBuildDefaultsAndMcpConfig() {
        BrowserRuntimeSettings settings = BrowserRuntimeSettings.buildRuntimeSettings(Map.of());
        McpServerConfig disabled = BrowserRuntimeSettings.buildBrowserRuntimeMcpConfig(Map.of());
        McpServerConfig httpCfg = BrowserRuntimeSettings.buildBrowserRuntimeMcpConfig(
                Map.of("PLAYWRIGHT_RUNTIME_MCP_ENABLED", "1", "PLAYWRIGHT_RUNTIME_MCP_CLIENT_TYPE", "http"));

        assertThat(settings.getProvider()).isEqualTo("openai");
        assertThat(settings.getModelName()).isEqualTo(BrowserRuntimeSettings.DEFAULT_MODEL_NAME);
        assertThat(disabled).isNull();
        assertThat(httpCfg.getClientType()).isEqualTo("streamable-http");
    }

    @Test
    void actionControllerShouldRegisterListAndRunActions() {
        BrowserActionController controller = new BrowserActionController();
        controller.registerAction(" MyAction ", (sessionId, requestId, params) -> Map.of("ok", true, "value", 42));

        Map<String, Object> result =
            controller.runAction("MYACTION", "session-1", "request-1", Map.of("source", "test"));

        assertThat(controller.listActions()).containsExactly("myaction");
        assertThat(result).containsEntry("ok", true).containsEntry("action", "myaction");
    }

    @Test
    void browserRuntimeAndHelperToolsShouldDelegate() throws Exception {
        McpServerConfig cfg = McpServerConfig.builder().serverId("test").serverName("test")
                .serverPath("stdio://playwright").clientType("stdio").params(Map.of("cwd", ".")).build();
        BrowserAgentRuntime runtime =
            new BrowserAgentRuntime("openai", "test-key", "https://example.invalid/v1", "test-model", cfg,
                    BrowserRunGuardrails.builder().maxSteps(3).maxFailures(1).timeoutS(30).retryOnce(false).build(),
                    new BrowserService("openai", "test-key", "https://example.invalid/v1", "test-model", cfg,
                            BrowserRunGuardrails.builder().maxSteps(3).maxFailures(1).timeoutS(30).retryOnce(false)
                                    .build()) {
                        @Override
                        protected java.util.Map<String, Object> runTaskOnce(String task, String sessionId,
                                String requestId, Integer timeoutS) {
                            java.util.Map<String, Object> payload = new java.util.LinkedHashMap<>();
                            payload.put("ok", true);
                            payload.put("session_id", sessionId);
                            payload.put("request_id", requestId);
                            payload.put("final", "done");
                            payload.put("page", Map.of());
                            payload.put("screenshot", null);
                            payload.put("error", null);
                            return payload;
                        }
                    });

        Map<String, Object> taskResult =
            runtime.runBrowserTask("Submit onboarding form", "session-1", "request-1", 120);
        runtime.controller().registerExampleActions();
        runtime.controller().bindRuntimeRunner(runtime::runBrowserTask);
        Map<String, Object> custom =
            runtime.runCustomAction("browser_task", "s4", "r4", Map.of("task", "go to example.com", "timeout_s", 7));
        List<Object> tools = BrowserRuntimeTools.buildBrowserRuntimeTools(runtime);
        List<Tool> functionTools = BrowserRuntimeTools.buildBrowserRuntimeToolFunctions(runtime, "browser-owner");
        Tool actionTool = functionTools.stream()
                .filter(tool -> "browser_custom_action".equals(tool.getCard().getName())).findFirst().orElseThrow();

        assertThat(taskResult).containsEntry("ok", true);
        assertThat(custom).containsEntry("final", "done");
        assertThat(tools).hasSize(5);
        assertThat(functionTools).hasSize(5)
                .allSatisfy(tool -> assertThat(tool.getCard().getId()).startsWith("browser-owner."));
        assertThat(actionTool.invoke(Map.of("action", "browser_task", "session_id", "s5", "request_id", "r5", "params",
                Map.of("task", "open example.org", "timeout_s", 8)))).isInstanceOf(Map.class);
        assertThat(runtime.listActions()).containsEntry("ok", true);
        assertThat(runtime.runtimeHealth()).containsEntry("started", true);
    }
}

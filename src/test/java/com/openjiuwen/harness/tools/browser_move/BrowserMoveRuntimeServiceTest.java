package com.openjiuwen.harness.tools.browser_move;

import com.openjiuwen.core.foundation.tool.mcp.McpServerConfig;
import com.openjiuwen.harness.tools.browser_move.playwright_runtime.BrowserAgentRuntime;
import com.openjiuwen.harness.tools.browser_move.playwright_runtime.BrowserRunGuardrails;
import com.openjiuwen.harness.tools.browser_move.playwright_runtime.BrowserRuntimeConfig;
import com.openjiuwen.harness.tools.browser_move.playwright_runtime.BrowserService;
import com.openjiuwen.harness.tools.browser_move.playwright_runtime.RuntimeSettings;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BrowserMoveRuntimeServiceTest {

    private BrowserAgentRuntime makeRuntime() {
        McpServerConfig mcpCfg = McpServerConfig.builder()
                .serverId("test-playwright-runtime")
                .serverName("test-playwright-runtime")
                .serverPath("stdio://playwright")
                .clientType("stdio")
                .params(Map.of("cwd", Path.of("").toAbsolutePath().toString()))
                .build();
        return new BrowserAgentRuntime(
                "openai",
                "test-key",
                "https://example.invalid/v1",
                "test-model",
                mcpCfg,
                new BrowserRunGuardrails(3, 1, 30, false)
        );
    }

    @Test
    void runtimeConfigUsesSharedDefaults() {
        RuntimeSettings settings = BrowserRuntimeConfig.buildRuntimeSettings();
        assertEquals("openai", settings.provider());
        assertEquals("", settings.apiKey());
        assertEquals("https://api.openai.com/v1", settings.apiBase());
        assertEquals(BrowserRuntimeConfig.DEFAULT_MODEL_NAME, settings.modelName());
        assertEquals(BrowserRuntimeConfig.DEFAULT_BROWSER_TIMEOUT_S, settings.guardrails().getTimeoutS());
        assertEquals(BrowserRuntimeConfig.DEFAULT_BROWSER_TIMEOUT_S, settings.mcpCfg().getParams().get("timeout_s"));
    }

    @Test
    void runtimeEnsureStartedRegistersBridgeToolsOnceAndForwardsTasks() {
        BrowserAgentRuntime runtime = makeRuntime();

        runtime.ensureStarted();
        runtime.ensureStarted();

        assertNotNull(runtime.getBrowserCustomActionTool());
        assertNotNull(runtime.getBrowserListActionsTool());
        assertEquals(2, runtime.getBridgeToolRegisterCount());

        Map<String, Object> result = runtime.runBrowserTask("Submit onboarding form", "session-1", "request-1", 120);
        assertEquals(true, result.get("ok"));
        assertEquals("session-1", result.get("session_id"));
        assertTrue(runtime.isRuntimeReady());
    }

    @Test
    void serviceFailureSummaryIsReusedThenCleared() {
        List<String> observedTasks = new ArrayList<>();
        List<Map<String, Object>> responses = List.of(
                response(false, "Opened dashboard and attempted submit.", "submit button not found"),
                response(true, "Submitted successfully.", null),
                response(true, "Confirmed completion.", null)
        );

        BrowserService service = new BrowserService("openai", "test-key", "https://example.invalid/v1", "test-model",
                McpServerConfig.builder().serverId("test-playwright").serverName("test-playwright").serverPath("stdio://playwright").clientType("stdio").params(Map.of("cwd", Path.of("").toAbsolutePath().toString())).build(),
                new BrowserRunGuardrails(3, 1, 30, false)) {
            @Override
            protected Map<String, Object> runTaskOnce(String task, String sessionId, String requestId, Integer timeoutS) {
                observedTasks.add(task);
                return responses.get(observedTasks.size() - 1);
            }
        };

        Map<String, Object> first = service.runTask("Submit onboarding form", "session-1", "req-1", null);
        assertEquals(false, first.get("ok"));
        assertTrue(String.valueOf(first.get("failure_summary")).contains("submit button not found"));
        assertTrue(!observedTasks.get(0).contains("Previous failed attempt context:"));

        Map<String, Object> second = service.runTask("Submit onboarding form", "session-1", "req-2", null);
        assertEquals(true, second.get("ok"));
        assertEquals(null, second.get("failure_summary"));
        assertTrue(observedTasks.get(1).contains("Previous failed attempt context:"));
        assertTrue(observedTasks.get(1).contains("submit button not found"));

        Map<String, Object> third = service.runTask("Submit onboarding form", "session-1", "req-3", null);
        assertEquals(true, third.get("ok"));
        assertEquals(null, third.get("failure_summary"));
        assertTrue(!observedTasks.get(2).contains("Previous failed attempt context:"));
    }

    private Map<String, Object> response(boolean ok, String fin, String error) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("ok", ok);
        response.put("final", fin);
        response.put("page", Map.of("url", "https://example.com/form", "title", "Form"));
        response.put("screenshot", ok ? null : "screenshots/form.png");
        response.put("error", error);
        return response;
    }
}

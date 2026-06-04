package com.openjiuwen.harness.tools.browser_move;

import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.harness.tools.browser_move.drivers.BrowserProfile;
import com.openjiuwen.harness.tools.browser_move.drivers.ManagedBrowserDriver;
import com.openjiuwen.harness.tools.browser_move.playwright_runtime.BrowserAgentRuntime;
import com.openjiuwen.harness.tools.browser_move.playwright_runtime.BrowserRunGuardrails;
import com.openjiuwen.harness.tools.browser_move.playwright_runtime.BrowserService;
import com.openjiuwen.harness.tools.browser_move.playwright_runtime.runtime_tools.BrowserCancelTool;
import com.openjiuwen.harness.tools.browser_move.playwright_runtime.runtime_tools.BrowserClearCancelTool;
import com.openjiuwen.harness.tools.browser_move.playwright_runtime.runtime_tools.BrowserCustomActionTool;
import com.openjiuwen.harness.tools.browser_move.playwright_runtime.runtime_tools.BrowserListActionsTool;
import com.openjiuwen.harness.tools.browser_move.playwright_runtime.runtime_tools.BrowserRuntimeHealthTool;
import com.openjiuwen.harness.tools.browser_move.playwright_runtime.runtime_tools.BrowserRuntimeTools;
import com.openjiuwen.core.foundation.tool.mcp.McpServerConfig;
import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpServer;

import java.nio.file.Path;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BrowserMoveManagedAndToolsTest {

    private BrowserAgentRuntime makeRuntime() {
        return new BrowserAgentRuntime(
                "openai",
                "test-key",
                "https://example.invalid/v1",
                "test-model",
                McpServerConfig.builder().serverId("test").serverName("test").serverPath("stdio://playwright").clientType("stdio").params(Map.of("cwd", ".")).build(),
                new BrowserRunGuardrails(3, 1, 30, false)
        );
    }

    @Test
    void managedBrowserReusesEndpointAndDoesNotKillExternalProcess() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 9333), 0);
        server.createContext("/json/version", exchange -> {
            byte[] body = "{\"Browser\":\"Chrome\",\"webSocketDebuggerUrl\":\"ws://127.0.0.1:9333/devtools/browser/test\"}".getBytes();
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();

        ManagedBrowserDriver driver = new ManagedBrowserDriver(new BrowserProfile(
                "test-profile", "managed", "http://127.0.0.1:9333", ".", 9333, "127.0.0.1"
        ));
        String endpoint = driver.start();
        assertEquals("http://127.0.0.1:9333", endpoint);
        assertFalse(driver.isOwnsProcess());

        Process process = new ProcessBuilder().command("cmd.exe", "/c", "exit", "0").start();
        ManagedBrowserDriver external = new ManagedBrowserDriver(new BrowserProfile(
                "test-profile", "managed", "http://127.0.0.1:9333", ".", 9333, "127.0.0.1"
        ));
        external.setProcess(process);
        external.setOwnsProcess(false);
        external.stop();
        assertTrue(process.isAlive() || process.exitValue() == 0);

        server.stop(0);
    }

    @Test
    void heartbeatStartReusesRunningTaskAndReplacesDoneTask() {
        BrowserService service = new BrowserService("openai", "test-key", "https://example.invalid/v1", "test-model",
                McpServerConfig.builder().serverId("test").serverName("test").serverPath("stdio://playwright").clientType("stdio").params(Map.of("cwd", ".")).build(),
                new BrowserRunGuardrails(3, 1, 30, false));

        service.startHeartbeat();
        CompletableFuture<Void> first = service.getHeartbeatTask();
        service.startHeartbeat();
        assertEquals(first, service.getHeartbeatTask());

        first.join();
        service.startHeartbeat();
        assertNotSame(first, service.getHeartbeatTask());
    }

    @Test
    void browserRuntimeToolsExposeExpectedHelpers() {
        BrowserAgentRuntime runtime = makeRuntime();
        runtime.ensureRuntimeReady();
        List<Tool> tools = BrowserRuntimeTools.buildBrowserRuntimeTools(runtime);
        assertEquals(5, tools.size());
        assertEquals(List.of(
                "browser_cancel_run",
                "browser_clear_cancel",
                "browser_custom_action",
                "browser_list_custom_actions",
                "browser_runtime_health"
        ), tools.stream().map(tool -> tool.getCard().getName()).toList());

        assertInstanceOf(BrowserCancelTool.class, tools.get(0));
        assertInstanceOf(BrowserClearCancelTool.class, tools.get(1));
        assertInstanceOf(BrowserCustomActionTool.class, tools.get(2));
        assertInstanceOf(BrowserListActionsTool.class, tools.get(3));
        assertInstanceOf(BrowserRuntimeHealthTool.class, tools.get(4));

        BrowserListActionsTool listActionsTool = (BrowserListActionsTool) tools.get(3);
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) ((com.openjiuwen.harness.tools.ToolOutput) listActionsTool.invoke(Map.of(), Map.of())).getData();
        @SuppressWarnings("unchecked")
        List<String> actions = (List<String>) data.get("actions");
        assertTrue(actions.containsAll(List.of("ping", "echo", "browser_task", "run_browser_task")));

        BrowserCustomActionTool customActionTool = (BrowserCustomActionTool) tools.get(2);
        @SuppressWarnings("unchecked")
        Map<String, Object> customData = (Map<String, Object>) ((com.openjiuwen.harness.tools.ToolOutput) customActionTool.invoke(Map.of("action", "ping"), Map.of())).getData();
        assertEquals("ping", customData.get("action"));
        assertTrue(Boolean.TRUE.equals(customData.get("pong")));

        BrowserCancelTool cancelTool = (BrowserCancelTool) tools.get(0);
        @SuppressWarnings("unchecked")
        Map<String, Object> cancelData = (Map<String, Object>) ((com.openjiuwen.harness.tools.ToolOutput) cancelTool.invoke(Map.of("session_id", "s1"), Map.of())).getData();
        assertEquals("s1", cancelData.get("session_id"));
        assertTrue(Boolean.TRUE.equals(cancelData.get("ok")));

        BrowserRuntimeHealthTool healthTool = (BrowserRuntimeHealthTool) tools.get(4);
        @SuppressWarnings("unchecked")
        Map<String, Object> healthData = (Map<String, Object>) ((com.openjiuwen.harness.tools.ToolOutput) healthTool.invoke(Map.of(), Map.of())).getData();
        assertTrue(Boolean.TRUE.equals(healthData.get("ok")));
        assertEquals("test-model", healthData.get("model_name"));
    }
}

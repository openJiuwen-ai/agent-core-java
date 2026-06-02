/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.harness.tools.browser_move;

import com.openjiuwen.core.foundation.tool.mcp.McpServerConfig;
import com.openjiuwen.harness.tools.browser_move.drivers.ManagedBrowserDriver;
import com.openjiuwen.harness.tools.browser_move.playwright_runtime.BrowserProfile;
import com.openjiuwen.harness.tools.browser_move.playwright_runtime.BrowserRunGuardrails;
import com.openjiuwen.harness.tools.browser_move.playwright_runtime.BrowserService;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for BrowserService guardrails, retries, and worker conversation behavior.
 *
 * <p>Mirrors Python's {@code test_service.py} in
 * {@code tests/unit_tests/harness/tools/browser_move/test_service.py}.</p>
 */
@DisplayName("Service Tests")
class TestService {

    @Test
    @DisplayName("failure summary is reused then cleared")
    void testFailureSummaryIsReusedThenCleared() {
        ScriptedBrowserService service = makeService(false);
        service.enqueue(response(false, "Opened dashboard and attempted submit.", "submit button not found"));
        service.enqueue(response(true, "Submitted successfully.", null, "https://example.com/done", "Done"));
        service.enqueue(response(true, "Confirmed completion.", null, "https://example.com/done", "Done"));

        Map<String, Object> first = service.runTask("Submit onboarding form", "session-1", "req-1", null);
        assertEquals(false, first.get("ok"));
        assertTrue(String.valueOf(first.get("failure_summary")).contains("submit button not found"));
        assertFalse(service.observedTasks.get(0).contains("Previous failed attempt context:"));

        Map<String, Object> second = service.runTask("Submit onboarding form", "session-1", "req-2", null);
        assertEquals(true, second.get("ok"));
        assertNull(second.get("failure_summary"));
        assertTrue(service.observedTasks.get(1).contains("Previous failed attempt context:"));
        assertTrue(service.observedTasks.get(1).contains("submit button not found"));

        Map<String, Object> third = service.runTask("Submit onboarding form", "session-1", "req-3", null);
        assertEquals(true, third.get("ok"));
        assertNull(third.get("failure_summary"));
        assertFalse(service.observedTasks.get(2).contains("Previous failed attempt context:"));
    }

    @Test
    @DisplayName("timeout failure generates summary")
    void testTimeoutFailureGeneratesSummary() {
        ScriptedBrowserService service = makeService(false);
        service.enqueue(new BrowserService.TaskTimeoutException("simulated timeout"));
        service.enqueue(response(true, "Recovered", null, "https://example.com", "Example"));

        Map<String, Object> failed = service.runTask("Check status", "session-timeout", "req-timeout", null);
        assertEquals(false, failed.get("ok"));
        assertTrue(String.valueOf(failed.get("error")).contains("task_timeout:"));
        assertTrue(String.valueOf(failed.get("failure_summary")).contains("task_timeout:"));

        Map<String, Object> recovered = service.runTask("Check status", "session-timeout", "req-retry", null);
        assertEquals(true, recovered.get("ok"));
        assertNull(recovered.get("failure_summary"));
        assertEquals(2, service.observedTasks.size());
        assertTrue(service.observedTasks.get(1).contains("Previous failed attempt context:"));
    }

    @Test
    @DisplayName("retryable runtime error retries once")
    void testRetryableRuntimeErrorRetriesOnce() {
        ScriptedBrowserService service = makeService(true);
        service.enqueue(response(false, "### Error\nError: page.goto: Frame has been detached.",
                "tool execution failed", "https://www.lazada.sg", ""));
        service.enqueue(response(true, "Navigation recovered and task completed.",
                null, "https://www.lazada.sg", "Lazada"));

        Map<String, Object> result = service.runTask("Open Lazada homepage", "session-retry", "req-retry", null);

        assertEquals(true, result.get("ok"));
        assertEquals(2, result.get("attempt"));
        assertNull(result.get("failure_summary"));
        assertEquals(2, service.observedTasks.size());
        assertTrue(service.observedTasks.get(1).contains("Previous failed attempt context:"));
        assertEquals(1, service.restartCalls);
    }

    @Test
    @DisplayName("max iteration failure preserves worker output without progress summary")
    void testMaxIterationFailurePreservesWorkerOutputWithoutProgressSummary() {
        ScriptedBrowserService service = makeService(false);
        service.enqueue(response(false, BrowserService.MAX_ITERATION_MESSAGE,
                "max_iterations_reached", "https://www.lazada.sg", "Lazada"));

        Map<String, Object> result = service.runTask(
                "Add carbonara ingredients to cart",
                "session-max-iter",
                "req-max-iter",
                null
        );

        assertEquals(false, result.get("ok"));
        assertTrue(String.valueOf(result.get("final")).contains(BrowserService.MAX_ITERATION_MESSAGE));
        assertFalse(String.valueOf(result.get("final")).contains("Partial progress (recent tool steps):"));
        assertFalse(String.valueOf(result.get("failure_summary")).contains("Partial progress (recent tool steps):"));
        assertEquals(1, service.observedTasks.size());
    }

    @Test
    @DisplayName("max iteration resume requires opt-in guardrail")
    void testMaxIterationResumeRequiresOptInGuardrail() {
        ScriptedBrowserService service = makeService(false);
        service.getGuardrails().setResumeOnMaxIterations(true);
        service.enqueue(response(false, BrowserService.MAX_ITERATION_MESSAGE,
                "max_iterations_reached", "https://www.baidu.com", "Baidu"));
        service.enqueue(response(true, "Completed after continuation.", null, "https://www.baidu.com", "Baidu"));

        Map<String, Object> result = service.runTask(
                "Open Baidu homepage",
                "session-max-iter-resume",
                "req-max-iter-resume",
                null
        );

        assertEquals(true, result.get("ok"));
        assertEquals(2, result.get("attempt"));
        assertEquals(2, service.observedTasks.size());
        assertTrue(service.observedTasks.get(1).contains("Continuation context:"));
    }

    @Test
    @DisplayName("max iteration failure includes observed tool progress")
    @SuppressWarnings("unchecked")
    void testMaxIterationFailureIncludesObservedToolProgress() {
        ScriptedBrowserService service = makeService(false);
        service.recordToolProgress(
                "session-progress",
                "req-progress-1",
                "browser_click",
                Map.of(
                        "message", "Clicked Add to cart",
                        "page", Map.of("url", "https://example.com/cart", "title", "Cart")
                )
        );
        service.enqueue(response(false, BrowserService.MAX_ITERATION_MESSAGE,
                "max_iterations_reached", "https://example.com/cart", "Cart"));

        Map<String, Object> result = service.runTask("Add item to cart", "session-progress", "req-progress-1", null);

        assertEquals(false, result.get("ok"));
        assertTrue(String.valueOf(result.get("failure_summary")).contains("Known progress for continuation:"));
        assertTrue(String.valueOf(result.get("failure_summary")).contains("Clicked Add to cart"));
        Map<String, Object> progressState = (Map<String, Object>) result.get("progress_state");
        List<String> recentToolSteps = (List<String>) progressState.get("recent_tool_steps");
        assertTrue(recentToolSteps.get(recentToolSteps.size() - 1).startsWith("browser_click:"));
    }

    @Test
    @DisplayName("structured progress is reused on next invocation")
    @SuppressWarnings("unchecked")
    void testStructuredProgressIsReusedOnNextInvocation() {
        ScriptedBrowserService service = makeService(false);
        service.enqueue(progressResponse());
        service.enqueue(response(true, "Coupon applied and order submitted.",
                null, "https://example.com/done", "Done"));

        Map<String, Object> first = service.runTask("Checkout cart", "session-reuse", "req-1", null);
        Map<String, Object> second = service.runTask("Checkout cart", "session-reuse", "req-2", null);

        assertEquals(false, first.get("ok"));
        Map<String, Object> progressState = (Map<String, Object>) first.get("progress_state");
        assertEquals("partial", progressState.get("status"));
        assertTrue(((List<String>) progressState.get("completed_steps")).contains("Opened cart"));
        assertEquals(true, second.get("ok"));
        assertNull(second.get("failure_summary"));
        assertNull(second.get("progress_state"));
        assertTrue(service.observedTasks.get(1).contains("Known progress for continuation:"));
        assertTrue(service.observedTasks.get(1).contains("Opened cart"));
        assertTrue(service.observedTasks.get(1).contains("Apply coupon"));
    }

    @Test
    @DisplayName("completed status overrides false ok when evidence is present")
    void testCompletedStatusOverridesFalseOkWhenEvidenceIsPresent() {
        ScriptedBrowserService service = makeService(false);
        Map<String, Object> parsed = response(false, "The confirmation page shows order #12345.",
                "worker_marked_incomplete", "https://example.com/done", "Done");
        parsed.put("status", "completed");
        parsed.put("progress", Map.of(
                "completion_evidence", List.of("Confirmation page shows order #12345"),
                "missing_requirements", List.of()
        ));
        service.enqueue(parsed);

        Map<String, Object> result = service.runTask("Place order", "session-complete", "req-complete", null);

        assertEquals(true, result.get("ok"));
        assertNull(result.get("error"));
        assertNull(result.get("failure_summary"));
    }

    @Test
    @DisplayName("run task once uses fresh worker conversation ids")
    void testRunTaskOnceUsesFreshWorkerConversationIds() {
        ScriptedBrowserService service = makeService(false);

        Map<String, Object> first = service.exposeRunTaskOnce("Open page", "session-1", "req-1");
        Map<String, Object> second = service.exposeRunTaskOnce("Open page", "session-1", "req-1");

        assertEquals(true, first.get("ok"));
        assertEquals(true, second.get("ok"));
        String firstConversationId = String.valueOf(first.get("worker_conversation_id"));
        String secondConversationId = String.valueOf(second.get("worker_conversation_id"));
        assertFalse(firstConversationId.isBlank());
        assertFalse(secondConversationId.isBlank());
        assertNotEquals(firstConversationId, secondConversationId);
        assertEquals("req-1", first.get("request_id"));
        assertEquals("req-1", second.get("request_id"));
    }

    @Test
    @DisplayName("ensure managed driver started reuses healthy existing driver")
    void testEnsureManagedDriverStartedReusesHealthyExistingDriver(@TempDir Path tempDir) {
        ScriptedBrowserService service = makeService(false, tempDir);
        service.setDriverMode("managed");
        FakeDriver healthyDriver = new FakeDriver(true);
        service.setManagedDriver(healthyDriver);

        boolean rebound = service.exposeEnsureManagedDriverStarted();

        assertFalse(rebound);
        assertSame(healthyDriver, service.getManagedDriver());
        assertEquals(0, healthyDriver.startCalls);
    }

    @Test
    @DisplayName("ensure managed driver started replaces stale driver")
    void testEnsureManagedDriverStartedReplacesStaleDriver(@TempDir Path tempDir) {
        ScriptedBrowserService service = makeService(false, tempDir);
        service.setDriverMode("managed");
        FakeDriver staleDriver = new FakeDriver(false);
        FakeDriver newDriver = new FakeDriver(false);
        service.setManagedDriver(staleDriver);
        service.createdDrivers.add(newDriver);

        boolean rebound = service.exposeEnsureManagedDriverStarted();

        assertTrue(rebound);
        assertSame(newDriver, service.getManagedDriver());
        assertEquals(1, staleDriver.stopCalls);
        assertEquals(1, newDriver.startCalls);
    }

    @Test
    @DisplayName("ensure runtime ready refreshes MCP binding after managed browser restart")
    void testEnsureRuntimeReadyRefreshesMcpBindingAfterManagedBrowserRestart(@TempDir Path tempDir) {
        ScriptedBrowserService service = makeService(false, tempDir);
        service.setStarted(true);
        service.setDriverMode("managed");
        service.setRegisteredCdpEndpoint("http://127.0.0.1:9333");
        service.exposeInjectCdpEndpoint("http://127.0.0.1:9333");
        service.setBrowserAgent(new Object());
        service.setManagedDriver(new FakeDriver(false));
        FakeDriver newDriver = new FakeDriver(false);
        service.createdDrivers.add(newDriver);

        service.ensureRuntimeReady();

        assertEquals(1, service.refreshCalls);
        assertSame(newDriver, service.getManagedDriver());
        assertNull(service.getBrowserAgent());
    }

    @Test
    @DisplayName("profile store defaults to runtime workspace")
    void testProfileStoreDefaultsToRuntimeWorkspace(@TempDir Path tempDir) {
        ScriptedBrowserService service = makeService(false, tempDir);

        assertEquals(tempDir.resolve(".browser").resolve("profiles.json").normalize(),
                service.getProfileStore().getPath().normalize());
    }

    @Test
    @DisplayName("build managed profile defaults user data dir to runtime workspace")
    void testBuildManagedProfileDefaultsUserDataDirToRuntimeWorkspace(@TempDir Path tempDir) {
        ScriptedBrowserService service = makeService(false, tempDir);

        BrowserProfile profile = service.exposeBuildManagedProfile();

        assertEquals(tempDir.resolve(".browser-profiles").resolve("jiuwenclaw").normalize(),
                Path.of(profile.getUserDataDir()).normalize());
    }

    @Test
    @DisplayName("run task does not reset browser runtime after completion")
    void testRunTaskDoesNotResetBrowserRuntimeAfterCompletion(@TempDir Path tempDir) {
        ScriptedBrowserService service = makeService(false, tempDir);
        service.setDriverMode("managed");
        service.setManagedDriver(new FakeDriver(true));
        service.enqueue(response(true, "done", null, "https://www.baidu.com", "Baidu"));

        Map<String, Object> result = service.runTask("Open Baidu", "keep-after-task", "req-keep", null);

        assertEquals(true, result.get("ok"));
        assertEquals(0, service.resetCalls);
    }

    private static ScriptedBrowserService makeService(boolean retryOnce) {
        return makeService(retryOnce, Path.of("").toAbsolutePath().normalize());
    }

    private static ScriptedBrowserService makeService(boolean retryOnce, Path runtimeCwd) {
        McpServerConfig mcpCfg = McpServerConfig.builder()
                .serverId("test-playwright")
                .serverName("test-playwright")
                .serverPath("stdio://playwright")
                .clientType("stdio")
                .params(Map.of("cwd", runtimeCwd.toString()))
                .build();
        return new ScriptedBrowserService(
                "openai",
                "test-key",
                "https://example.invalid/v1",
                "test-model",
                mcpCfg,
                new BrowserRunGuardrails(3, 1, 30, retryOnce)
        );
    }

    private static Map<String, Object> response(boolean ok, String finalText, String error) {
        return response(ok, finalText, error, "https://example.com/form", "Form");
    }

    private static Map<String, Object> response(boolean ok, String finalText, String error, String url, String title) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("ok", ok);
        response.put("final", finalText);
        response.put("page", Map.of("url", url, "title", title));
        response.put("screenshot", ok ? null : "screenshots/form.png");
        response.put("error", error);
        return response;
    }

    private static Map<String, Object> progressResponse() {
        Map<String, Object> response = response(false, "Reached review page but coupon still not applied.",
                "max_iterations_reached", "https://example.com/review", "Review");
        response.put("status", "partial");
        response.put("progress", Map.of(
                "completed_steps", List.of("Opened cart", "Reached review page"),
                "remaining_steps", List.of("Apply coupon", "Submit order"),
                "next_step", "Open the coupon panel and apply the saved code",
                "missing_requirements", List.of("Coupon code not applied yet")
        ));
        return response;
    }

    private static final class ScriptedBrowserService extends BrowserService {
        private final ArrayDeque<Object> scriptedResponses = new ArrayDeque<>();
        private final List<String> observedTasks = new ArrayList<>();
        private final ArrayDeque<FakeDriver> createdDrivers = new ArrayDeque<>();
        private int restartCalls;
        private int resetCalls;
        private int refreshCalls;

        private ScriptedBrowserService(String provider, String apiKey, String apiBase, String modelName,
                                      McpServerConfig mcpCfg, BrowserRunGuardrails guardrails) {
            super(provider, apiKey, apiBase, modelName, mcpCfg, guardrails);
        }

        private void enqueue(Object response) {
            scriptedResponses.add(response);
        }

        private Map<String, Object> exposeRunTaskOnce(String task, String sessionId, String requestId) {
            return super.runTaskOnce(task, sessionId, requestId, null);
        }

        private boolean exposeEnsureManagedDriverStarted() {
            return super.ensureManagedDriverStarted();
        }

        private BrowserProfile exposeBuildManagedProfile() {
            return super.buildManagedProfile();
        }

        private void exposeInjectCdpEndpoint(String endpoint) {
            super.injectCdpEndpoint(endpoint);
        }

        @Override
        protected Map<String, Object> runTaskOnce(String task, String sessionId, String requestId, Integer timeoutS) {
            observedTasks.add(task);
            if (scriptedResponses.isEmpty()) {
                return super.runTaskOnce(task, sessionId, requestId, timeoutS);
            }
            Object next = scriptedResponses.removeFirst();
            if (next instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            return new LinkedHashMap<>((Map<String, Object>) next);
        }

        @Override
        protected ManagedBrowserDriver createManagedDriver(BrowserProfile profile) {
            return createdDrivers.isEmpty() ? new FakeDriver(false) : createdDrivers.removeFirst();
        }

        @Override
        protected void restart() {
            restartCalls++;
        }

        @Override
        protected void resetBrowserRuntime() {
            resetCalls++;
            super.resetBrowserRuntime();
        }

        @Override
        protected void refreshMcpServerBinding() {
            refreshCalls++;
            super.refreshMcpServerBinding();
        }
    }

    private static final class FakeDriver extends ManagedBrowserDriver {
        private boolean ready;
        private final String endpoint;
        private int startCalls;
        private int stopCalls;

        private FakeDriver(boolean ready) {
            this(ready, "http://127.0.0.1:9333");
        }

        private FakeDriver(boolean ready, String endpoint) {
            super(new com.openjiuwen.harness.tools.browser_move.drivers.BrowserProfile(
                    "jiuwenclaw",
                    "managed",
                    endpoint,
                    ".browser-profile",
                    9333,
                    "127.0.0.1"
            ));
            this.ready = ready;
            this.endpoint = endpoint;
        }

        @Override
        public boolean isEndpointReady() {
            return ready;
        }

        @Override
        public String start() {
            startCalls++;
            ready = true;
            return endpoint;
        }

        @Override
        public void stop() {
            stopCalls++;
            ready = false;
        }
    }
}

/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.browser_move.playwright_runtime;

import com.openjiuwen.core.foundation.tool.mcp.McpServerConfig;
import com.openjiuwen.harness.tools.browser_move.drivers.ManagedBrowserDriver;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.function.Executable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Supplemental parity tests for browser service continuation and managed driver behavior.
 *
 * <p>Mirrors Python's {@code tests/unit_tests/harness/tools/browser_move/test_service.py}.</p>
 */
class BrowserServiceMissingTest {

    private static final String SOURCE = "tests/unit_tests/harness/tools/browser_move/test_service.py";

    @TestFactory
    Collection<DynamicTest> pythonBrowserServiceCases() {
        return List.of(
                caseOf("test_failure_summary_is_reused_then_cleared",
                        BrowserServiceMissingTest::failureSummaryIsReusedThenCleared),
                caseOf("test_timeout_failure_generates_summary",
                        BrowserServiceMissingTest::timeoutFailureGeneratesSummary),
                caseOf("test_retryable_runtime_error_retries_once",
                        BrowserServiceMissingTest::retryableRuntimeErrorRetriesOnce),
                caseOf("test_max_iteration_failure_preserves_worker_output_without_progress_summary",
                        BrowserServiceMissingTest::maxIterationFailurePreservesWorkerOutputWithoutProgressSummary),
                caseOf("test_max_iteration_resume_requires_opt_in_guardrail",
                        BrowserServiceMissingTest::maxIterationResumeRequiresOptInGuardrail),
                caseOf("test_max_iteration_failure_includes_observed_tool_progress",
                        BrowserServiceMissingTest::maxIterationFailureIncludesObservedToolProgress),
                caseOf("test_structured_progress_is_reused_on_next_invocation",
                        BrowserServiceMissingTest::structuredProgressIsReusedOnNextInvocation),
                caseOf("test_completed_status_overrides_false_ok_when_evidence_is_present",
                        BrowserServiceMissingTest::completedStatusOverridesFalseOkWhenEvidenceIsPresent),
                caseOf("test_run_task_once_uses_fresh_worker_conversation_ids",
                        BrowserServiceMissingTest::runTaskOnceUsesFreshWorkerConversationIds),
                caseOf("test_ensure_managed_driver_started_reuses_healthy_existing_driver",
                        BrowserServiceMissingTest::ensureManagedDriverStartedReusesHealthyExistingDriver),
                caseOf("test_ensure_managed_driver_started_replaces_stale_driver",
                        BrowserServiceMissingTest::ensureManagedDriverStartedReplacesStaleDriver),
                caseOf("test_ensure_runtime_ready_refreshes_mcp_binding_after_managed_browser_restart",
                        BrowserServiceMissingTest::ensureRuntimeReadyRefreshesMcpBindingAfterManagedBrowserRestart),
                caseOf("test_profile_store_defaults_to_runtime_workspace",
                        BrowserServiceMissingTest::profileStoreDefaultsToRuntimeWorkspace),
                caseOf("test_build_managed_profile_defaults_user_data_dir_to_runtime_workspace",
                        BrowserServiceMissingTest::buildManagedProfileDefaultsUserDataDirToRuntimeWorkspace),
                caseOf("test_run_task_does_not_reset_browser_runtime_after_completion",
                        BrowserServiceMissingTest::runTaskDoesNotResetBrowserRuntimeAfterCompletion)
        );
    }

    private static DynamicTest caseOf(String pythonNode, Executable executable) {
        return dynamicTest(SOURCE + "::" + pythonNode, executable);
    }

    private static void failureSummaryIsReusedThenCleared() {
        RecordingBrowserService service = makeService(false, false);
        service.enqueue(response(false, "Opened dashboard and attempted submit.",
                page("https://example.com/form", "Form"), "screenshots/form.png", "submit button not found"));
        service.enqueue(response(true, "Submitted successfully.",
                page("https://example.com/done", "Done"), null, null));
        service.enqueue(response(true, "Confirmed completion.",
                page("https://example.com/done", "Done"), null, null));

        Map<String, Object> first = service.runTask("Submit onboarding form", "session-1", "req-1", null);
        assertThat(first.get("ok")).isEqualTo(false);
        assertThat(first.get("failure_summary")).asString().contains("submit button not found");
        assertThat(service.prompts.get(0)).doesNotContain("Previous failed attempt context:");

        Map<String, Object> second = service.runTask("Submit onboarding form", "session-1", "req-2", null);
        assertThat(second.get("ok")).isEqualTo(true);
        assertThat(second.get("failure_summary")).isNull();
        assertThat(service.prompts.get(1))
                .contains("Previous failed attempt context:")
                .contains("submit button not found");

        Map<String, Object> third = service.runTask("Submit onboarding form", "session-1", "req-3", null);
        assertThat(third.get("ok")).isEqualTo(true);
        assertThat(third.get("failure_summary")).isNull();
        assertThat(service.prompts.get(2)).doesNotContain("Previous failed attempt context:");
    }

    private static void timeoutFailureGeneratesSummary() {
        RecordingBrowserService service = makeService(false, false);
        service.enqueueTimeout();
        service.enqueue(response(true, "Recovered", page("https://example.com", "Example"), null, null));

        Map<String, Object> failed = service.runTask("Check status", "session-timeout", "req-timeout", null);
        assertThat(failed.get("ok")).isEqualTo(false);
        assertThat(failed.get("error")).asString().contains("task_timeout:");
        assertThat(failed.get("failure_summary")).asString().contains("task_timeout:");

        Map<String, Object> recovered = service.runTask("Check status", "session-timeout", "req-retry", null);
        assertThat(recovered.get("ok")).isEqualTo(true);
        assertThat(recovered.get("failure_summary")).isNull();
        assertThat(service.prompts).hasSize(2);
        assertThat(service.prompts.get(1)).contains("Previous failed attempt context:");
    }

    private static void retryableRuntimeErrorRetriesOnce() {
        RecordingBrowserService service = makeService(true, false);
        service.enqueue(response(false, "### Error\nError: page.goto: Frame has been detached.",
                page("https://www.lazada.sg", ""), null, "tool execution failed"));
        service.enqueue(response(true, "Navigation recovered and task completed.",
                page("https://www.lazada.sg", "Lazada"), null, null));

        Map<String, Object> result = service.runTask("Open Lazada homepage", "session-retry", "req-retry", null);

        assertThat(result.get("ok")).isEqualTo(true);
        assertThat(result.get("attempt")).isEqualTo(2);
        assertThat(result.get("failure_summary")).isNull();
        assertThat(service.prompts).hasSize(2);
        assertThat(service.prompts.get(1)).contains("Previous failed attempt context:");
        assertThat(service.restartCalls).isEqualTo(1);
    }

    private static void maxIterationFailurePreservesWorkerOutputWithoutProgressSummary() {
        RecordingBrowserService service = makeService(false, false);
        service.enqueue(response(false, BrowserService.MAX_ITERATION_MESSAGE,
                page("https://www.lazada.sg", "Lazada"), null, "max_iterations_reached"));

        Map<String, Object> result = service.runTask(
                "Add carbonara ingredients to cart",
                "session-max-iter",
                "req-max-iter",
                null
        );

        assertThat(result.get("ok")).isEqualTo(false);
        assertThat(result.get("final")).asString().contains(BrowserService.MAX_ITERATION_MESSAGE);
        assertThat(result.get("final")).asString().doesNotContain("Partial progress (recent tool steps):");
        assertThat(result.get("failure_summary")).asString()
                .doesNotContain("Partial progress (recent tool steps):");
        assertThat(service.prompts).hasSize(1);
    }

    private static void maxIterationResumeRequiresOptInGuardrail() {
        RecordingBrowserService service = makeService(false, true);
        service.enqueue(response(false, BrowserService.MAX_ITERATION_MESSAGE,
                page("https://www.baidu.com", "Baidu"), null, "max_iterations_reached"));
        service.enqueue(response(true, "Completed after continuation.",
                page("https://www.baidu.com", "Baidu"), null, null));

        Map<String, Object> result = service.runTask(
                "Open Baidu homepage",
                "session-max-iter-resume",
                "req-max-iter-resume",
                null
        );

        assertThat(result.get("ok")).isEqualTo(true);
        assertThat(result.get("attempt")).isEqualTo(2);
        assertThat(service.prompts).hasSize(2);
        assertThat(service.prompts.get(1)).contains("Continuation context:");
    }

    private static void maxIterationFailureIncludesObservedToolProgress() {
        RecordingBrowserService service = makeService(false, false);
        service.recordToolProgress(
                "session-progress",
                "req-progress-1",
                "browser_click",
                Map.of(
                        "message", "Clicked Add to cart",
                        "page", page("https://example.com/cart", "Cart")
                )
        );
        service.enqueue(response(false, BrowserService.MAX_ITERATION_MESSAGE,
                page("https://example.com/cart", "Cart"), null, "max_iterations_reached"));

        Map<String, Object> result = service.runTask("Add item to cart", "session-progress", "req-progress-1", null);

        assertThat(result.get("ok")).isEqualTo(false);
        assertThat(result.get("failure_summary")).asString()
                .contains("Known progress for continuation:")
                .contains("Clicked Add to cart");
        @SuppressWarnings("unchecked")
        Map<String, Object> progressState = (Map<String, Object>) result.get("progress_state");
        @SuppressWarnings("unchecked")
        List<String> recentToolSteps = (List<String>) progressState.get("recent_tool_steps");
        assertThat(recentToolSteps.get(recentToolSteps.size() - 1)).startsWith("browser_click:");
    }

    private static void structuredProgressIsReusedOnNextInvocation() {
        RecordingBrowserService service = makeService(false, false);
        service.enqueue(response(false, "Reached review page but coupon still not applied.",
                page("https://example.com/review", "Review"), null, "max_iterations_reached",
                "partial",
                Map.of(
                        "completed_steps", List.of("Opened cart", "Reached review page"),
                        "remaining_steps", List.of("Apply coupon", "Submit order"),
                        "next_step", "Open the coupon panel and apply the saved code",
                        "missing_requirements", List.of("Coupon code not applied yet")
                )));
        service.enqueue(response(true, "Coupon applied and order submitted.",
                page("https://example.com/done", "Done"), null, null));

        Map<String, Object> first = service.runTask("Checkout cart", "session-reuse", "req-1", null);
        Map<String, Object> second = service.runTask("Checkout cart", "session-reuse", "req-2", null);

        assertThat(first.get("ok")).isEqualTo(false);
        @SuppressWarnings("unchecked")
        Map<String, Object> progressState = (Map<String, Object>) first.get("progress_state");
        assertThat(progressState.get("status")).isEqualTo("partial");
        @SuppressWarnings("unchecked")
        List<String> completedSteps = (List<String>) progressState.get("completed_steps");
        assertTrue(completedSteps.contains("Opened cart"));
        assertThat(second.get("ok")).isEqualTo(true);
        assertThat(second.get("failure_summary")).isNull();
        assertThat(second.get("progress_state")).isNull();
        assertThat(service.prompts.get(1))
                .contains("Known progress for continuation:")
                .contains("Opened cart")
                .contains("Apply coupon");
    }

    private static void completedStatusOverridesFalseOkWhenEvidenceIsPresent() {
        RecordingBrowserService service = makeService(false, false);
        service.enqueue(response(false, "The confirmation page shows order #12345.",
                page("https://example.com/done", "Done"), null, "worker_marked_incomplete",
                "completed",
                Map.of(
                        "completion_evidence", List.of("Confirmation page shows order #12345"),
                        "missing_requirements", List.of()
                )));

        Map<String, Object> result = service.runTask("Place order", "session-complete", "req-complete", null);

        assertThat(result.get("ok")).isEqualTo(true);
        assertThat(result.get("error")).isNull();
        assertThat(result.get("failure_summary")).isNull();
    }

    private static void runTaskOnceUsesFreshWorkerConversationIds() {
        RecordingBrowserService service = makeService(false, false);
        service.enqueue(response(true, "done", page("", ""), null, null));
        service.enqueue(response(true, "done", page("", ""), null, null));

        Map<String, Object> first = service.runTaskOnce("Open page", "session-1", "req-1");
        Map<String, Object> second = service.runTaskOnce("Open page", "session-1", "req-1");

        assertThat(first.get("ok")).isEqualTo(true);
        assertThat(second.get("ok")).isEqualTo(true);
        assertThat(service.conversationIds).hasSize(2);
        assertThat(service.conversationIds.get(0)).isNotEqualTo(service.conversationIds.get(1));
        assertThat(service.conversationIds).allSatisfy(value -> assertThat(value).isNotBlank());
        assertThat(service.requestIds).containsExactly("req-1", "req-1");
    }

    private static void ensureManagedDriverStartedReusesHealthyExistingDriver() throws IOException {
        withSystemProperties(Map.of(
                "BROWSER_DRIVER", "managed",
                "BROWSER_PROFILE_STORE_PATH", tempPath("profiles-reuse").toString()
        ), () -> {
            RecordingBrowserService service = makeService(false, false);
            FakeManagedBrowserDriver healthyDriver = new FakeManagedBrowserDriver(profile(), true, "http://127.0.0.1:9333");
            service.setManagedDriver(healthyDriver);

            boolean rebound = service.ensureManagedDriverStartedForTest();

            assertThat(rebound).isFalse();
            assertThat(service.getManagedDriver()).isSameAs(healthyDriver);
            assertThat(service.createDriverCalls).isZero();
        });
    }

    private static void ensureManagedDriverStartedReplacesStaleDriver() throws IOException {
        withSystemProperties(Map.of(
                "BROWSER_DRIVER", "managed",
                "BROWSER_PROFILE_STORE_PATH", tempPath("profiles-stale").toString()
        ), () -> {
            RecordingBrowserService service = makeService(false, false);
            FakeManagedBrowserDriver staleDriver = new FakeManagedBrowserDriver(profile(), false, "http://127.0.0.1:9333");
            FakeManagedBrowserDriver newDriver = new FakeManagedBrowserDriver(profile(), true, "http://127.0.0.1:9333");
            service.setManagedDriver(staleDriver);
            service.nextDriver = newDriver;

            boolean rebound = service.ensureManagedDriverStartedForTest();

            assertThat(rebound).isTrue();
            assertThat(staleDriver.stopCalls).isEqualTo(1);
            assertThat(service.getManagedDriver()).isSameAs(newDriver);
            assertThat(newDriver.startCalls).isEqualTo(1);
        });
    }

    private static void ensureRuntimeReadyRefreshesMcpBindingAfterManagedBrowserRestart() throws IOException {
        withSystemProperties(Map.of(
                "BROWSER_DRIVER", "managed",
                "BROWSER_PROFILE_STORE_PATH", tempPath("profiles-refresh").toString()
        ), () -> {
            RecordingBrowserService service = makeService(false, false);
            service.setStarted(true);
            service.setRegisteredEndpoint("http://127.0.0.1:9333");
            service.injectEndpointForTest("http://127.0.0.1:9333");
            service.setBrowserAgent(new Object());
            FakeManagedBrowserDriver staleDriver = new FakeManagedBrowserDriver(profile(), false, "http://127.0.0.1:9333");
            FakeManagedBrowserDriver newDriver = new FakeManagedBrowserDriver(profile(), true, "http://127.0.0.1:9333");
            service.setManagedDriver(staleDriver);
            service.nextDriver = newDriver;

            service.ensureRuntimeReady();

            assertThat(service.refreshCalls).isEqualTo(1);
            assertThat(service.getManagedDriver()).isSameAs(newDriver);
            assertThat(service.getBrowserAgent()).isNull();
        });
    }

    private static void profileStoreDefaultsToRuntimeWorkspace() {
        Path runtimeRoot = tempDir("runtime-root");
        RecordingBrowserService service = makeService(false, false, runtimeRoot.toString());
        assertThat(service.getProfileStorePath()).isEqualTo(runtimeRoot.resolve(".browser").resolve("profiles.json"));
    }

    private static void buildManagedProfileDefaultsUserDataDirToRuntimeWorkspace() throws IOException {
        Path runtimeRoot = tempDir("runtime-root-managed");
        withSystemProperties(Map.of(
                "BROWSER_DRIVER", "managed",
                "BROWSER_PROFILE_STORE_PATH", tempPath("profiles-build").toString()
        ), () -> {
            RecordingBrowserService service = makeService(false, false, runtimeRoot.toString());

            BrowserProfile profile = service.buildManagedProfileForTest();

            assertThat(Path.of(profile.getUserDataDir()).toAbsolutePath().normalize())
                    .isEqualTo(runtimeRoot.resolve(".browser-profiles").resolve("jiuwenclaw")
                            .toAbsolutePath().normalize());
        });
    }

    private static void runTaskDoesNotResetBrowserRuntimeAfterCompletion() {
        RecordingBrowserService service = makeService(false, false);
        FakeManagedBrowserDriver managedDriver = new FakeManagedBrowserDriver(profile(), true, "http://127.0.0.1:9333");
        service.setManagedDriver(managedDriver);
        service.enqueue(response(true, "done", page("https://www.baidu.com", "Baidu"), null, null));

        Map<String, Object> result = service.runTask("Open Baidu", "keep-after-task", "req-keep", null);

        assertThat(result.get("ok")).isEqualTo(true);
        assertThat(service.restartCalls).isZero();
        assertThat(service.getManagedDriver()).isSameAs(managedDriver);
    }

    private static RecordingBrowserService makeService(boolean retryOnce, boolean resumeOnMaxIterations) {
        return makeService(retryOnce, resumeOnMaxIterations, tempPathUnchecked("runtime").toString());
    }

    private static RecordingBrowserService makeService(
            boolean retryOnce,
            boolean resumeOnMaxIterations,
            String runtimeCwd
    ) {
        return new RecordingBrowserService(new BrowserRunGuardrails(3, 1, 30, retryOnce, resumeOnMaxIterations),
                runtimeCwd);
    }

    private static Map<String, Object> response(
            boolean ok,
            String finalText,
            Map<String, Object> page,
            Object screenshot,
            String error
    ) {
        return response(ok, finalText, page, screenshot, error, null, null);
    }

    private static Map<String, Object> response(
            boolean ok,
            String finalText,
            Map<String, Object> page,
            Object screenshot,
            String error,
            String status,
            Map<String, Object> progress
    ) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ok", ok);
        result.put("final", finalText);
        result.put("page", page);
        result.put("screenshot", screenshot);
        result.put("error", error);
        if (status != null) {
            result.put("status", status);
        }
        if (progress != null) {
            result.put("progress", progress);
        }
        return result;
    }

    private static Map<String, Object> page(String url, String title) {
        Map<String, Object> page = new LinkedHashMap<>();
        page.put("url", url);
        page.put("title", title);
        return page;
    }

    private static BrowserProfile profile() {
        return new BrowserProfile(
                "jiuwenclaw",
                "managed",
                "http://127.0.0.1:9333",
                "",
                ".",
                9333,
                "127.0.0.1",
                List.of()
        );
    }

    private static Path tempPath(String prefix) throws IOException {
        return Files.createTempDirectory(prefix).resolve("profiles.json");
    }

    private static Path tempPathUnchecked(String prefix) {
        return tempDir(prefix);
    }

    private static Path tempDir(String prefix) {
        try {
            return Files.createTempDirectory(prefix);
        } catch (IOException ex) {
            throw new AssertionError(ex);
        }
    }

    private static void withSystemProperties(Map<String, String> values, ThrowingRunnable runnable) throws IOException {
        Map<String, String> previous = new LinkedHashMap<>();
        values.forEach((key, value) -> previous.put(key, System.getProperty(key)));
        try {
            values.forEach(System::setProperty);
            runnable.run();
        } finally {
            previous.forEach((key, value) -> {
                if (value == null) {
                    System.clearProperty(key);
                } else {
                    System.setProperty(key, value);
                }
            });
        }
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws IOException;
    }

    private static final class RecordingBrowserService extends BrowserService {

        private final ArrayDeque<Object> queued = new ArrayDeque<>();
        private final List<String> prompts = new ArrayList<>();
        private final List<String> conversationIds = new ArrayList<>();
        private final List<String> requestIds = new ArrayList<>();
        private int restartCalls;
        private int refreshCalls;
        private int createDriverCalls;
        private FakeManagedBrowserDriver nextDriver;

        private RecordingBrowserService(BrowserRunGuardrails guardrails, String runtimeCwd) {
            super(
                    "openai",
                    "test-key",
                    "https://example.invalid/v1",
                    "test-model",
                    McpServerConfig.builder()
                            .serverId("test-playwright")
                            .serverName("test-playwright")
                            .serverPath("stdio://playwright")
                            .clientType("stdio")
                            .params(Map.of("cwd", runtimeCwd))
                            .build(),
                    guardrails
            );
        }

        private void enqueue(Map<String, Object> response) {
            queued.add(response);
        }

        private void enqueueTimeout() {
            queued.add(new TaskTimeoutException("simulated timeout"));
        }

        @Override
        protected Map<String, Object> executeWorkerTask(
                String taskPrompt,
                String workerConversationId,
                String requestId
        ) {
            prompts.add(taskPrompt);
            conversationIds.add(workerConversationId);
            requestIds.add(requestId);
            Object item = queued.removeFirst();
            if (item instanceof TaskTimeoutException timeout) {
                throw timeout;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> response = (Map<String, Object>) item;
            return response;
        }

        @Override
        protected void restart() {
            restartCalls += 1;
        }

        @Override
        protected void refreshMcpServerBinding() {
            refreshCalls += 1;
            super.refreshMcpServerBinding();
        }

        @Override
        protected ManagedBrowserDriver createManagedDriver(BrowserProfile profile) {
            createDriverCalls += 1;
            return nextDriver == null
                    ? new FakeManagedBrowserDriver(profile, true, "http://127.0.0.1:9333")
                    : nextDriver;
        }

        private boolean ensureManagedDriverStartedForTest() {
            return ensureManagedDriverStarted();
        }

        private BrowserProfile buildManagedProfileForTest() {
            return buildManagedProfile();
        }

        private void setManagedDriver(ManagedBrowserDriver driver) {
            setManagedDriverForTest(driver);
        }

        private ManagedBrowserDriver getManagedDriver() {
            return getManagedDriverForTest();
        }

        private void setStarted(boolean started) {
            setStartedForTest(started);
        }

        private void setRegisteredEndpoint(String endpoint) {
            setRegisteredCdpEndpointForTest(endpoint);
        }

        private void injectEndpointForTest(String endpoint) {
            Map<String, Object> params = new LinkedHashMap<>(getMcpConfig().getParams());
            params.put("env", Map.of("PLAYWRIGHT_MCP_CDP_ENDPOINT", endpoint));
            getMcpConfig().setParams(params);
        }
    }

    private static final class FakeManagedBrowserDriver extends ManagedBrowserDriver {

        private boolean endpointReady;
        private final String endpoint;
        private int startCalls;
        private int stopCalls;

        private FakeManagedBrowserDriver(BrowserProfile profile, boolean endpointReady, String endpoint) {
            super(profile);
            this.endpointReady = endpointReady;
            this.endpoint = endpoint;
        }

        @Override
        public boolean isEndpointReady() {
            return endpointReady;
        }

        @Override
        public String start(double timeoutSeconds, boolean killExisting) {
            startCalls += 1;
            endpointReady = true;
            return endpoint;
        }

        @Override
        public void stop() {
            stopCalls += 1;
            endpointReady = false;
        }
    }
}

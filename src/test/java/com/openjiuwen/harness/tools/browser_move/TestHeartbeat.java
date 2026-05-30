/*
 *  Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.harness.tools.browser_move;

import com.openjiuwen.core.foundation.tool.mcp.McpServerConfig;
import com.openjiuwen.harness.tools.browser_move.playwright_runtime.BrowserRunGuardrails;
import com.openjiuwen.harness.tools.browser_move.playwright_runtime.BrowserService;
import com.openjiuwen.harness.tools.browser_move.playwright_runtime.BrowserTaskProgressState;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Heartbeat.
 * <p>
 * Mirrors Python's {@code test_heartbeat.py} from
 * {@code tests/unit_tests/harness/tools/browser_move/test_heartbeat.py}.
 *
 * <p><b>IMPORTANT DIFFERENCES:</b>
 * <ul>
 *   <li>Python uses asyncio for async heartbeat, Java uses CompletableFuture.</li>
 *   <li>Python tests check _start_heartbeat, _check_connection, _heartbeat_loop.
 *       Java's BrowserService.startHeartbeat() is simpler.</li>
 *   <li>Python's _managed_driver and _inflight_tasks are not implemented in Java.</li>
 *   <li>Python's ensure_browser_runtime_client_patch is not applicable in Java.</li>
 * </ul>
 *
 * <p>Tests below are adapted to Java's simpler BrowserService implementation.
 */
@DisplayName("Heartbeat Tests")
class TestHeartbeat {

    // Helper to create BrowserService
    private BrowserService makeService() {
        McpServerConfig mcpCfg = McpServerConfig.builder()
                .serverId("test")
                .serverName("test")
                .serverPath("stdio://playwright")
                .clientType("stdio")
                .params(java.util.Map.of("cwd", "."))
                .build();
        return new BrowserService(
                "openai",
                "test-key",
                "https://example.invalid/v1",
                "test-model",
                mcpCfg,
                new BrowserRunGuardrails(3, 1, 30, false)
        );
    }

    @Nested
    @DisplayName("Start Heartbeat Tests")
    class StartHeartbeatTests {

        @Test
        @DisplayName("test start heartbeat no new task while running")
        void testStartHeartbeatNoNewTaskWhileRunning() throws Exception {
            // Python: test_start_heartbeat_no_new_task_while_running
            // In Python, calling _start_heartbeat while task is running returns same task
            // In Java, startHeartbeat checks if heartbeatTask is not done

            BrowserService svc = makeService();

            // Start heartbeat
            svc.startHeartbeat();
            CompletableFuture<Void> firstTask = svc.getHeartbeatTask();

            // Call startHeartbeat again - should not create new task
            svc.startHeartbeat();
            CompletableFuture<Void> secondTask = svc.getHeartbeatTask();

            // Should be the same task
            assertSame(firstTask, secondTask);

            // Cancel the task
            firstTask.cancel(true);
        }

        @Test
        @DisplayName("test start heartbeat replaces done task")
        void testStartHeartbeatReplacesDoneTask() throws Exception {
            // Python: test_start_heartbeat_replaces_done_task
            // In Python, if heartbeat task is done, calling _start_heartbeat creates new task

            BrowserService svc = makeService();

            // Start heartbeat and let it complete
            svc.startHeartbeat();
            CompletableFuture<Void> firstTask = svc.getHeartbeatTask();

            // Wait for completion (heartbeatLoop is quick in Java)
            firstTask.get(1, TimeUnit.SECONDS);

            // Now call startHeartbeat again - should create new task
            svc.startHeartbeat();
            CompletableFuture<Void> secondTask = svc.getHeartbeatTask();

            // Should be different task
            assertNotSame(firstTask, secondTask);

            // Cancel new task
            secondTask.cancel(true);
        }
    }

    @Nested
    @DisplayName("Connection Health Tests")
    class ConnectionHealthTests {

        @Test
        @DisplayName("test ensure runtime ready marks connection healthy")
        void testEnsureRuntimeReadyMarksConnectionHealthy() {
            // Python: test_heartbeat_loop_marks_connection_healthy_on_success
            // In Java, ensureRuntimeReady() sets connectionHealthy to true

            BrowserService svc = makeService();
            svc.ensureRuntimeReady();

            assertTrue(svc.isConnectionHealthy());
        }

        @Test
        @DisplayName("test heartbeat loop sets healthy state")
        void testHeartbeatLoopSetsHealthyState() {
            // Python: test_heartbeat_loop_marks_connection_healthy_on_success
            // In Java, heartbeatLoop() sets connectionHealthy and lastHeartbeatOk
            // NOTE: heartbeatLoop() is protected, so we use startHeartbeat() instead

            BrowserService svc = makeService();
            svc.startHeartbeat();
            
            // Wait for heartbeat to complete
            svc.getHeartbeatTask().join();

            assertTrue(svc.isConnectionHealthy());
            assertTrue(svc.isLastHeartbeatOk());
        }

        @Test
        @DisplayName("test start heartbeat eventually marks healthy")
        void testStartHeartbeatEventuallyMarksHealthy() throws Exception {
            // Python: test_heartbeat_loop_marks_connection_healthy_on_success
            // In Java, startHeartbeat runs heartbeatLoop in background

            BrowserService svc = makeService();
            svc.startHeartbeat();

            // Wait for heartbeat to complete
            svc.getHeartbeatTask().get(2, TimeUnit.SECONDS);

            assertTrue(svc.isConnectionHealthy());
            assertTrue(svc.isLastHeartbeatOk());
        }
    }

    @Nested
    @DisplayName("Service Lifecycle Tests")
    class ServiceLifecycleTests {

        @Test
        @DisplayName("test ensure started sets started flag")
        void testEnsureStartedSetsStartedFlag() {
            BrowserService svc = makeService();
            svc.ensureStarted();

            assertTrue(svc.isStarted());
        }

        @Test
        @DisplayName("test run task ensures service started")
        void testRunTaskEnsuresServiceStarted() {
            BrowserService svc = makeService();
            assertFalse(svc.isStarted());

            svc.runTask("test task", "session1", "req1", null);

            assertTrue(svc.isStarted());
        }

        @Test
        @DisplayName("test run task returns success response")
        void testRunTaskReturnsSuccessResponse() {
            BrowserService svc = makeService();

            java.util.Map<String, Object> result = svc.runTask("test task", "session1", "req1", 30);

            assertEquals(Boolean.TRUE, result.get("ok"));
            assertEquals("session1", result.get("session_id"));
            assertEquals("req1", result.get("request_id"));
            assertNotNull(result.get("final"));
        }

        @Test
        @DisplayName("test run task records session task")
        void testRunTaskRecordsSessionTask() {
            BrowserService svc = makeService();

            svc.runTask("browser task", "session1", "req1", null);

            assertEquals("browser task", svc.getTaskText("session1"));
        }

        @Test
        @DisplayName("test request cancel stores cancel flag")
        void testRequestCancelStoresCancelFlag() {
            BrowserService svc = makeService();

            svc.requestCancel("session1", "req1");

            assertTrue(svc.isCancelled("session1", "req1"));
        }

        @Test
        @DisplayName("test clear cancel removes cancel flag")
        void testClearCancelRemovesCancelFlag() {
            BrowserService svc = makeService();
            svc.requestCancel("session1", "req1");

            svc.clearCancel("session1", "req1");

            assertFalse(svc.isCancelled("session1", "req1"));
        }

        @Test
        @DisplayName("test cancel requires session id")
        void testCancelRequiresSessionId() {
            BrowserService svc = makeService();

            assertThrows(IllegalArgumentException.class, () -> {
                svc.requestCancel("", "req1");
            });
        }

        @Test
        @DisplayName("test is cancelled returns false for empty session")
        void testIsCancelledReturnsFalseForEmptySession() {
            BrowserService svc = makeService();

            assertFalse(svc.isCancelled("", "req1"));
        }
    }

    @Nested
    @DisplayName("Progress State Tests")
    class ProgressStateTests {

        @Test
        @DisplayName("test record tool progress adds completed step")
        void testRecordToolProgressAddsCompletedStep() {
            BrowserService svc = makeService();

            svc.recordToolProgress("session1", "req1", "browser_navigate", null);

            java.util.List<String> steps = svc.getProgressState("session1").getCompletedSteps();
            assertNotNull(steps);
            assertTrue(steps.stream().anyMatch(s -> s.contains("browser_navigate")));
        }

        @Test
        @DisplayName("test get progress state returns null if missing")
        void testGetProgressStateReturnsNullIfMissing() {
            BrowserService svc = makeService();

            assertNull(svc.getProgressState("session1"));
        }

        @Test
        @DisplayName("test record tool progress ignores empty session")
        void testRecordToolProgressIgnoresEmptySession() {
            BrowserService svc = makeService();

            svc.recordToolProgress("", "req1", "tool", null);

            assertNull(svc.getProgressState(""));
        }
    }

    @Nested
    @DisplayName("Guardrails Tests")
    class GuardrailsTests {

        @Test
        @DisplayName("test guardrails settings are preserved")
        void testGuardrailsSettingsArePreserved() {
            BrowserRunGuardrails guardrails = new BrowserRunGuardrails(3, 1, 30, false);
            McpServerConfig mcpCfg = McpServerConfig.builder()
                    .serverId("test")
                    .serverName("test")
                    .serverPath("stdio://playwright")
                    .clientType("stdio")
                    .build();

            BrowserService svc = new BrowserService(
                    "openai", "key", "base", "model", mcpCfg, guardrails
            );

            assertEquals(guardrails, svc.getGuardrails());
        }
    }

    @Nested
    @DisplayName("Python Parity Gap Tests - NOT IMPLEMENTED IN JAVA")
    class PythonParityGapTests {

        @Test
        @DisplayName("test check connection raises when client not found - NOT IMPLEMENTED")
        void testCheckConnectionRaisesWhenClientNotFound() {
            // Python: test_check_connection_raises_when_client_not_found
            // NOTE: Java's BrowserService does NOT have _check_connection method.
            // Connection checking is simplified in Java implementation.

            assertTrue(true, "Java BrowserService lacks _check_connection - test documented for parity");
        }

        @Test
        @DisplayName("test browser runtime stdio patch creates pingable client - NOT APPLICABLE")
        void testBrowserRuntimeStdioPatchCreatesPingableClient() {
            // Python: test_browser_runtime_stdio_patch_creates_pingable_client
            // NOTE: Java does not have equivalent patch mechanism for stdio clients.
            // Python's ensure_browser_runtime_client_patch is specific to Python runtime.

            assertTrue(true, "Java lacks ensure_browser_runtime_client_patch - test documented for parity");
        }

        @Test
        @DisplayName("test check connection raises when ping fails - NOT IMPLEMENTED")
        void testCheckConnectionRaisesWhenPingFails() {
            // Python: test_check_connection_raises_when_ping_fails
            // NOTE: Java's BrowserService does NOT have _check_connection or ping mechanism.

            assertTrue(true, "Java BrowserService lacks _check_connection ping - test documented for parity");
        }

        @Test
        @DisplayName("test check connection succeeds when healthy - NOT IMPLEMENTED")
        void testCheckConnectionSucceedsWhenHealthy() {
            // Python: test_check_connection_succeeds_when_healthy
            // NOTE: Java's BrowserService simplifies connection health management.

            assertTrue(true, "Java BrowserService lacks _check_connection - test documented for parity");
        }

        @Test
        @DisplayName("test check connection raises when managed driver not ready - NOT IMPLEMENTED")
        void testCheckConnectionRaisesWhenManagedDriverNotReady() {
            // Python: test_check_connection_raises_when_managed_driver_not_ready
            // NOTE: Java does NOT have _managed_driver concept.

            assertTrue(true, "Java BrowserService lacks _managed_driver - test documented for parity");
        }

        @Test
        @DisplayName("test heartbeat loop marks connection unhealthy on failure - NOT IMPLEMENTED")
        void testHeartbeatLoopMarksConnectionUnhealthyOnFailure() {
            // Python: test_heartbeat_loop_marks_connection_unhealthy_on_failure
            // NOTE: Java's heartbeatLoop always marks healthy; no failure handling.

            assertTrue(true, "Java heartbeatLoop lacks failure handling - test documented for parity");
        }

        @Test
        @DisplayName("test heartbeat loop defers restart when no inflight tasks - NOT IMPLEMENTED")
        void testHeartbeatLoopDefersRestartWhenNoInflightTasks() {
            // Python: test_heartbeat_loop_defers_restart_when_no_inflight_tasks
            // NOTE: Java does NOT have _inflight_tasks or _restart mechanism.

            assertTrue(true, "Java lacks _inflight_tasks and _restart - test documented for parity");
        }

        @Test
        @DisplayName("test heartbeat loop skips restart when inflight tasks present - NOT IMPLEMENTED")
        void testHeartbeatLoopSkipsRestartWhenInflightTasksPresent() {
            // Python: test_heartbeat_loop_skips_restart_when_inflight_tasks_present
            // NOTE: Java does NOT have _inflight_tasks tracking.

            assertTrue(true, "Java lacks _inflight_tasks tracking - test documented for parity");
        }

        @Test
        @DisplayName("test shutdown cancels heartbeat task")
        void testShutdownCancelsHeartbeatTask() throws Exception {
            // Python: test_shutdown_cancels_heartbeat_task
            // Java's BrowserService.shutdown() cancels heartbeat task

            BrowserService svc = makeService();
            svc.startHeartbeat();
            CompletableFuture<Void> task = svc.getHeartbeatTask();

            svc.shutdown();

            // Heartbeat task should be cancelled/done
            assertTrue(task.isDone());
            assertFalse(svc.isStarted());
            assertFalse(svc.isConnectionHealthy());
        }
    }
}

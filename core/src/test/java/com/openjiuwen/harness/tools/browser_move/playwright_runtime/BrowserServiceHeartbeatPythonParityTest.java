/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.browser_move.playwright_runtime;

import com.openjiuwen.core.foundation.tool.mcp.McpServerConfig;
import com.openjiuwen.harness.tools.browser_move.clients.BrowserMoveStdioClient;
import com.openjiuwen.harness.tools.browser_move.drivers.ManagedBrowserDriver;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.function.Executable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

/**
 * Supplemental parity tests for BrowserService heartbeat lifecycle behavior.
 *
 * <p>Mirrors Python's heartbeat tests in
 * {@code tests/unit_tests/harness/tools/browser_move/test_heartbeat.py}.</p>
 */
class BrowserServiceHeartbeatPythonParityTest {

    private static final String SOURCE = "tests/unit_tests/harness/tools/browser_move/test_heartbeat.py";

    @TestFactory
    Collection<DynamicTest> pythonBrowserHeartbeatCases() {
        return List.of(
                caseOf("test_start_heartbeat_no_new_task_while_running",
                        BrowserServiceHeartbeatPythonParityTest::startHeartbeatNoNewTaskWhileRunning),
                caseOf("test_start_heartbeat_replaces_done_task",
                        BrowserServiceHeartbeatPythonParityTest::startHeartbeatReplacesDoneTask),
                caseOf("test_check_connection_raises_when_client_not_found",
                        BrowserServiceHeartbeatPythonParityTest::checkConnectionRaisesWhenClientNotFound),
                caseOf("test_browser_runtime_stdio_patch_creates_pingable_client",
                        BrowserServiceHeartbeatPythonParityTest::browserRuntimeStdioPatchCreatesPingableClient),
                caseOf("test_check_connection_raises_when_ping_fails",
                        BrowserServiceHeartbeatPythonParityTest::checkConnectionRaisesWhenPingFails),
                caseOf("test_check_connection_succeeds_when_healthy",
                        BrowserServiceHeartbeatPythonParityTest::checkConnectionSucceedsWhenHealthy),
                caseOf("test_check_connection_raises_when_managed_driver_not_ready",
                        BrowserServiceHeartbeatPythonParityTest::checkConnectionRaisesWhenManagedDriverNotReady),
                caseOf("test_heartbeat_loop_marks_connection_healthy_on_success",
                        BrowserServiceHeartbeatPythonParityTest::heartbeatLoopMarksConnectionHealthyOnSuccess),
                caseOf("test_heartbeat_loop_marks_connection_unhealthy_on_failure",
                        BrowserServiceHeartbeatPythonParityTest::heartbeatLoopMarksConnectionUnhealthyOnFailure),
                caseOf("test_heartbeat_loop_defers_restart_when_no_inflight_tasks",
                        BrowserServiceHeartbeatPythonParityTest::heartbeatLoopDefersRestartWhenNoInflightTasks),
                caseOf("test_heartbeat_loop_skips_restart_when_inflight_tasks_present",
                        BrowserServiceHeartbeatPythonParityTest::heartbeatLoopSkipsRestartWhenInflightTasksPresent),
                caseOf("test_shutdown_cancels_heartbeat_task",
                        BrowserServiceHeartbeatPythonParityTest::shutdownCancelsHeartbeatTask)
        );
    }

    private static DynamicTest caseOf(String pythonNode, Executable executable) {
        return dynamicTest(SOURCE + "::" + pythonNode, executable);
    }

    private static void startHeartbeatNoNewTaskWhileRunning() {
        TestBrowserService service = makeService(new PingClient(true));
        service.setHeartbeatIntervalMillisForTest(10_000L);

        service.startHeartbeat();
        Thread first = waitForHeartbeatThread(service);
        service.startHeartbeat();

        assertThat(service.getHeartbeatThread()).isSameAs(first);
        service.shutdown();
    }

    private static void startHeartbeatReplacesDoneTask() throws InterruptedException {
        ImmediateHeartbeatService service = new ImmediateHeartbeatService();

        service.startHeartbeat();
        Thread first = waitForHeartbeatThread(service);
        first.join(TimeUnit.SECONDS.toMillis(2));
        service.startHeartbeat();
        Thread second = waitForHeartbeatThread(service);

        assertThat(first.isAlive()).isFalse();
        assertThat(second).isNotSameAs(first);
        service.shutdown();
    }

    private static void checkConnectionRaisesWhenClientNotFound() {
        TestBrowserService service = makeService(null);

        assertThatThrownBy(service::check)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("client")
                .hasMessageContaining("not found");
    }

    private static void browserRuntimeStdioPatchCreatesPingableClient() {
        BrowserTools.ensureBrowserRuntimeClientPatch();
        McpServerConfig config = mcpConfig();

        BrowserMoveStdioClient client = new BrowserMoveStdioClient(config);

        assertThat(BrowserTools.isClientPatchApplied()).isTrue();
        assertThat(client.ping()).isTrue();
    }

    private static void checkConnectionRaisesWhenPingFails() {
        TestBrowserService service = makeService(new PingClient(false));

        assertThatThrownBy(service::check)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not responding");
    }

    private static void checkConnectionSucceedsWhenHealthy() {
        TestBrowserService service = makeService(new PingClient(true));

        service.check();
    }

    private static void checkConnectionRaisesWhenManagedDriverNotReady() {
        TestBrowserService service = makeService(new PingClient(true));
        service.setManagedDriverForTest(new FakeManagedBrowserDriver(false));

        assertThatThrownBy(service::check)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CDP")
                .hasMessageContaining("endpoint");
    }

    private static void heartbeatLoopMarksConnectionHealthyOnSuccess() {
        OneShotHeartbeatService service = new OneShotHeartbeatService(false);

        service.startHeartbeat();
        waitForHeartbeatThreadToStop(service);

        assertThat(service.isConnectionHealthy()).isTrue();
        assertThat(service.getLastHeartbeatOk()).isNotNull();
        assertThat(service.restartCalls()).isZero();
    }

    private static void heartbeatLoopMarksConnectionUnhealthyOnFailure() {
        OneShotHeartbeatService service = new OneShotHeartbeatService(true);

        service.startHeartbeat();
        waitForHeartbeatThreadToStop(service);

        assertThat(service.isConnectionHealthy()).isFalse();
        assertThat(service.restartCalls()).isZero();
    }

    private static void heartbeatLoopDefersRestartWhenNoInflightTasks() {
        OneShotHeartbeatService service = new OneShotHeartbeatService(true);

        service.startHeartbeat();
        waitForHeartbeatThreadToStop(service);

        assertThat(service.hasInflightTasks()).isFalse();
        assertThat(service.restartCalls()).isZero();
    }

    private static void heartbeatLoopSkipsRestartWhenInflightTasksPresent() {
        OneShotHeartbeatService service = new OneShotHeartbeatService(true);
        service.markInflightTaskForTest("session:req", new Object());

        service.startHeartbeat();
        waitForHeartbeatThreadToStop(service);

        assertThat(service.hasInflightTasks()).isTrue();
        assertThat(service.restartCalls()).isZero();
    }

    private static void shutdownCancelsHeartbeatTask() {
        TestBrowserService service = makeService(new PingClient(true));
        service.setHeartbeatIntervalMillisForTest(10_000L);

        service.startHeartbeat();
        Thread thread = waitForHeartbeatThread(service);
        service.shutdown();

        assertThat(thread.isAlive()).isFalse();
    }

    private static Thread waitForHeartbeatThread(TestBrowserService service) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        Thread thread;
        do {
            thread = service.getHeartbeatThread();
            if (thread != null) {
                return thread;
            }
            Thread.yield();
        } while (System.nanoTime() < deadline);
        throw new AssertionError("heartbeat thread was not created");
    }

    private static void waitForHeartbeatThreadToStop(TestBrowserService service) {
        Thread thread = waitForHeartbeatThread(service);
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (thread.isAlive() && System.nanoTime() < deadline) {
            Thread.yield();
        }
        assertThat(thread.isAlive()).isFalse();
    }

    private static TestBrowserService makeService(Object client) {
        return new TestBrowserService(client);
    }

    private static McpServerConfig mcpConfig() {
        return McpServerConfig.builder()
                .serverId("test-stdio-patch")
                .serverName("test-stdio-patch")
                .serverPath("stdio://playwright")
                .clientType("stdio")
                .params(Map.of("cwd", tempDir().toString()))
                .build();
    }

    private static Path tempDir() {
        try {
            return Files.createTempDirectory("browser-heartbeat-");
        } catch (Exception ex) {
            throw new AssertionError(ex);
        }
    }

    private static class TestBrowserService extends BrowserService {
        private final Object client;
        private int restartCalls;

        TestBrowserService(Object client) {
            super(
                    "openai",
                    "test-key",
                    "https://example.invalid/v1",
                    "test-model",
                    mcpConfig(),
                    new BrowserRunGuardrails(3, 1, 30, false, false)
            );
            this.client = client;
        }

        @Override
        protected Object getRegisteredBrowserRuntimeClient() {
            return client;
        }

        @Override
        protected void restart() {
            restartCalls += 1;
        }

        void check() {
            checkConnection();
        }

        Thread getHeartbeatThread() {
            return getHeartbeatThreadForTest();
        }

        boolean hasInflightTasks() {
            return hasInflightTasksForTest();
        }

        int restartCalls() {
            return restartCalls;
        }
    }

    private static final class ImmediateHeartbeatService extends TestBrowserService {
        private ImmediateHeartbeatService() {
            super(new PingClient(true));
        }

        @Override
        protected void heartbeatLoop() {
            requestHeartbeatStopForTest();
        }
    }

    private static final class OneShotHeartbeatService extends TestBrowserService {
        private final boolean failCheck;

        private OneShotHeartbeatService(boolean failCheck) {
            super(new PingClient(true));
            this.failCheck = failCheck;
        }

        @Override
        protected void checkConnection() {
            requestHeartbeatStopForTest();
            if (failCheck) {
                throw new IllegalStateException("down");
            }
        }
    }

    private record PingClient(boolean healthy) {
        @SuppressWarnings("unused")
        public boolean ping() {
            return healthy;
        }
    }

    private static final class FakeManagedBrowserDriver extends ManagedBrowserDriver {
        private boolean endpointReady;

        private FakeManagedBrowserDriver(boolean endpointReady) {
            super(new BrowserProfile("jiuwenclaw", "managed", "", "", ".", 9333, "127.0.0.1", List.of()));
            this.endpointReady = endpointReady;
        }

        @Override
        public boolean isEndpointReady() {
            return endpointReady;
        }

        @Override
        public String start(double timeoutSeconds, boolean killExisting) {
            endpointReady = true;
            return "http://127.0.0.1:9333";
        }

        @Override
        public void stop() {
            endpointReady = false;
        }
    }
}

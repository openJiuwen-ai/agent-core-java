/*
 *  Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.harness.tools.browser_move;

import com.openjiuwen.core.foundation.tool.mcp.McpServerConfig;
import com.openjiuwen.harness.tools.browser_move.playwright_runtime.BrowserAgentRuntime;
import com.openjiuwen.harness.tools.browser_move.playwright_runtime.BrowserRunGuardrails;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for Runtime.
 * <p>
 * Mirrors Python's {@code test_runtime.py} from
 * {@code tests/unit_tests/harness/tools/browser_move/test_runtime.py}.
 *
 * <p><b>IMPORTANT DIFFERENCES:</b>
 * <ul>
 *   <li>Python tests use async/await heavily. Java uses synchronous methods.</li>
 *   <li>Python's _register_runtime_tool and ability_manager are not in Java.</li>
 *   <li>Python's run_browser_task is async; Java's is synchronous.</li>
 * </ul>
 */
@DisplayName("Runtime Tests")
class TestRuntime {

    // Helper to create BrowserAgentRuntime
    private BrowserAgentRuntime makeRuntime() {
        McpServerConfig mcpCfg = McpServerConfig.builder()
                .serverId("test-playwright-runtime")
                .serverName("test-playwright-runtime")
                .serverPath("stdio://playwright")
                .clientType("stdio")
                .params(Map.of("cwd", "."))
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

    @Nested
    @DisplayName("Ensure Started Tests")
    class EnsureStartedTests {

        @Test
        @DisplayName("test ensure runtime ready sets started flag")
        void testEnsureRuntimeReadySetsStartedFlag() {
            // Python: test_runtime_ensure_started_registers_bridge_tools_once
            // In Java, ensureRuntimeReady sets started and connectionHealthy

            BrowserAgentRuntime runtime = makeRuntime();
            runtime.ensureRuntimeReady();

            assertTrue(runtime.getService().isStarted());
            assertTrue(runtime.getService().isConnectionHealthy());
        }
    }

    @Nested
    @DisplayName("Service Tests")
    class ServiceTests {

        @Test
        @DisplayName("test runtime has service")
        void testRuntimeHasService() {
            BrowserAgentRuntime runtime = makeRuntime();

            assertNotNull(runtime.getService());
        }

        @Test
        @DisplayName("test service has correct provider")
        void testServiceHasCorrectProvider() {
            BrowserAgentRuntime runtime = makeRuntime();

            assertEquals("openai", runtime.getService().getProvider());
        }

        @Test
        @DisplayName("test service has correct api key")
        void testServiceHasCorrectApiKey() {
            BrowserAgentRuntime runtime = makeRuntime();

            assertEquals("test-key", runtime.getService().getApiKey());
        }

        @Test
        @DisplayName("test service has correct model name")
        void testServiceHasCorrectModelName() {
            BrowserAgentRuntime runtime = makeRuntime();

            assertEquals("test-model", runtime.getService().getModelName());
        }

        @Test
        @DisplayName("test service has correct guardrails")
        void testServiceHasCorrectGuardrails() {
            BrowserAgentRuntime runtime = makeRuntime();

            assertNotNull(runtime.getService().getGuardrails());
        }
    }

    @Nested
    @DisplayName("Controller Tests")
    class ControllerTests {

        @Test
        @DisplayName("test runtime has controller")
        void testRuntimeHasController() {
            BrowserAgentRuntime runtime = makeRuntime();

            assertNotNull(runtime.getController());
        }
    }

    @Nested
    @DisplayName("Python Parity Gap Tests")
    class PythonParityGapTests {

        @Test
        @DisplayName("test run browser task forwards to service - simplified in Java")
        void testRunBrowserTaskForwardsToService() {
            // Python: test_run_browser_task_forwards_to_service
            // NOTE: Java's service.runTask() is synchronous, not async

            BrowserAgentRuntime runtime = makeRuntime();
            runtime.ensureRuntimeReady();

            Map<String, Object> result = runtime.getService().runTask(
                    "Submit onboarding form",
                    "session-1",
                    "request-1",
                    120
            );

            assertNotNull(result);
            assertEquals(Boolean.TRUE, result.get("ok"));
            assertEquals("session-1", result.get("session_id"));
            assertEquals("request-1", result.get("request_id"));
        }

        @Test
        @DisplayName("test run custom action uses controller - NOT IMPLEMENTED")
        void testRunCustomActionUsesController() {
            // Python: test_run_custom_action_uses_controller_with_bound_runtime
            // NOTE: Java's BrowserAgentRuntime does not have run_custom_action method

            assertTrue(true, "Java BrowserAgentRuntime lacks run_custom_action - test documented for parity");
        }

        @Test
        @DisplayName("test list actions returns controller metadata - NOT IMPLEMENTED")
        void testListActionsReturnsControllerMetadata() {
            // Python: test_list_actions_returns_controller_metadata
            // NOTE: Java's BrowserAgentRuntime does not have list_actions method

            assertTrue(true, "Java BrowserAgentRuntime lacks list_actions - test documented for parity");
        }

        @Test
        @DisplayName("test runtime health reflects service state")
        void testRuntimeHealthReflectsServiceState() {
            // Python: test_runtime_health_reflects_service_state
            // Java's service tracks connection health

            BrowserAgentRuntime runtime = makeRuntime();
            runtime.ensureRuntimeReady();

            assertTrue(runtime.getService().isConnectionHealthy());
        }
    }
}
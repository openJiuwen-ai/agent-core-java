/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.harness.tools.browser_move;

import com.openjiuwen.harness.tools.browser_move.controllers.ActionController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for browser_move controller action registry behavior.
 *
 * <p>Mirrors Python's {@code test_controller.py} in
 * {@code tests/unit_tests/harness/tools/browser_move/test_controller.py}.</p>
 */
@Tag("unit-test")
class TestController {

    @Test
    @DisplayName("register, list, and run action happy path")
    void testRegisterListAndRunActionHappyPath() {
        ActionController controller = new ActionController();
        controller.registerAction("  MyAction  ", (sessionId, requestId, params) -> {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("ok", true);
            result.put("value", ((Number) params.getOrDefault("value", 0)).intValue() + 1);
            result.put("handler_session_id", sessionId);
            result.put("handler_request_id", requestId);
            result.put("meta", Map.of("source", params.get("source")));
            return result;
        }, true);

        assertEquals(List.of("myaction"), controller.listActions());
        ActionController.ActionResult actionResult = controller.executeAction(
                "MYACTION",
                "session-1",
                "request-1",
                Map.of("value", 41, "source", "test")
        ).join();
        Map<?, ?> data = data(actionResult);

        assertTrue(actionResult.isOk());
        assertEquals("myaction", data.get("action"));
        assertEquals("session-1", data.get("session_id"));
        assertEquals("request-1", data.get("request_id"));
        assertEquals(42, data.get("value"));
        assertEquals("session-1", data.get("handler_session_id"));
        assertEquals("request-1", data.get("handler_request_id"));
        assertEquals(Map.of("source", "test"), data.get("meta"));
    }

    @Test
    @DisplayName("unknown action returns structured error")
    void testRunActionUnknownActionReturnsError() {
        ActionController.ActionResult result = new ActionController()
                .executeAction("missing_action", "s1", "r1", Map.of())
                .join();

        assertFalse(result.isOk());
        assertEquals("missing_action", result.getAction());
        assertEquals("s1", result.getSessionId());
        assertEquals("r1", result.getRequestId());
        assertTrue(result.getError().contains("unknown action: missing_action"));
    }

    @Test
    @DisplayName("handler exception is captured")
    void testRunActionHandlerExceptionIsCaptured() {
        ActionController controller = new ActionController();
        controller.registerAction("explode", (sessionId, requestId, params) -> {
            throw new RuntimeException("boom");
        }, true);

        ActionController.ActionResult result = controller.executeAction("explode", "s2", "r2", Map.of()).join();

        assertFalse(result.isOk());
        assertEquals("explode", result.getAction());
        assertEquals("s2", result.getSessionId());
        assertEquals("r2", result.getRequestId());
        assertTrue(result.getError().contains("boom"));
    }

    @Test
    @DisplayName("browser_task reports runtime_not_bound when runner is absent")
    void testBrowserTaskRuntimeNotBound() {
        ActionController controller = new ActionController();
        controller.registerExampleActions();
        controller.clearRuntimeRunner();

        ActionController.ActionResult result = controller.executeAction(
                "browser_task",
                "s3",
                "r3",
                Map.of("task", "open page")
        ).join();
        Map<?, ?> data = data(result);

        assertFalse(result.isOk());
        assertEquals("s3", data.get("session_id"));
        assertEquals("r3", data.get("request_id"));
        assertTrue(String.valueOf(data.get("error")).contains("runtime_not_bound"));
    }

    @Test
    @DisplayName("browser_task calls bound runner")
    void testBrowserTaskCallsBoundRunner() {
        ActionController controller = new ActionController();
        Map<String, Object> observed = new LinkedHashMap<>();
        Function<Map<String, Object>, Map<String, Object>> runner = args -> {
            observed.putAll(args);
            return Map.of("ok", true, "final", "done");
        };
        controller.registerExampleActions();
        controller.bindRuntimeRunner(runner);

        ActionController.ActionResult result = controller.executeAction(
                "browser_task",
                "s4",
                "r4",
                Map.of("task", "go to example.com", "timeout_s", "7")
        ).join();
        Map<?, ?> data = data(result);

        assertTrue(result.isOk());
        assertEquals("done", data.get("final"));
        assertEquals("go to example.com", observed.get("task"));
        assertEquals("s4", observed.get("session_id"));
        assertEquals("r4", observed.get("request_id"));
        assertEquals(7, observed.get("timeout_s"));
    }

    @Test
    @DisplayName("describe actions includes registered specs")
    void testDescribeActionsIncludesRegisteredSpecs() {
        ActionController controller = new ActionController();
        controller.registerExampleActions();

        Map<String, Map<String, Object>> details = controller.describeActions();
        assertTrue(details.containsKey("browser_drag_and_drop"));
        Map<String, Object> spec = details.get("browser_drag_and_drop");
        assertInstanceOf(String.class, spec.get("summary"));
        assertFalse(String.valueOf(spec.get("summary")).isBlank());
        assertInstanceOf(Map.class, spec.get("params"));
        assertFalse(((Map<?, ?>) spec.get("params")).isEmpty());
    }

    @Test
    @DisplayName("browser_get_element_coordinates parses fenced JSON output")
    void testBrowserGetElementCoordinatesParsesFencedJsonOutput() {
        ActionController controller = new ActionController();
        controller.registerExampleActions();
        controller.bindRuntimeRunner((Function<Map<String, Object>, Map<String, Object>>) args -> Map.of(
                "ok", true,
                "final", "```json\n{\"ok\": true, \"source\": {\"x\": 12, \"y\": 34}, \"target\": null, \"error\": null}\n```"
        ));

        ActionController.ActionResult result = controller.executeAction(
                "browser_get_element_coordinates",
                "s-json",
                "r-json",
                Map.of("element_source", "Example")
        ).join();
        Map<?, ?> data = data(result);

        assertTrue(result.isOk());
        assertEquals(Map.of("x", 12, "y", 34), data.get("source"));
        assertNull(data.get("target"));
        assertNull(data.get("error"));
    }

    @Test
    @DisplayName("ActionController instances are isolated")
    void testActionControllerInstanceIsolation() {
        ActionController ctrlA = new ActionController();
        ActionController ctrlB = new ActionController();
        ctrlA.registerAction("only_a", (sessionId, requestId, params) ->
                Map.of("ok", true, "who", "a", "session_id", sessionId, "request_id", requestId, "meta", params), true);
        assertEquals(List.of("only_a"), ctrlA.listActions());
        assertTrue(ctrlB.listActions().isEmpty());

        List<String> seen = new ArrayList<>();
        ctrlA.bindRuntimeRunner((Function<Map<String, Object>, Map<String, Object>>) args -> {
            seen.add("a:" + args.get("task") + ":" + args.get("session_id") + ":" + args.get("request_id"));
            return Map.of("ok", true, "final", "from_a");
        });
        ctrlB.bindRuntimeRunner((Function<Map<String, Object>, Map<String, Object>>) args -> {
            seen.add("b:" + args.get("task") + ":" + args.get("session_id") + ":" + args.get("request_id"));
            return Map.of("ok", true, "final", "from_b");
        });
        ctrlA.registerExampleActions();
        ctrlB.registerExampleActions();

        Map<?, ?> resultA = data(ctrlA.executeAction("browser_task", "sa", "ra", Map.of("task", "task-a")).join());
        Map<?, ?> resultB = data(ctrlB.executeAction("browser_task", "sb", "rb", Map.of("task", "task-b")).join());

        assertEquals("from_a", resultA.get("final"));
        assertEquals("from_b", resultB.get("final"));
        assertEquals(List.of("a:task-a:sa:ra", "b:task-b:sb:rb"), seen);
    }

    @Test
    @DisplayName("browser worker action context blocks recursive browser_task")
    void testBrowserWorkerActionContextBlocksRecursiveBrowserTask() throws Exception {
        ActionController controller = new ActionController();
        controller.registerExampleActions();

        ActionController.ActionResult result;
        try (AutoCloseable ignored = controller.browserWorkerActionContext()) {
            result = controller.executeAction(
                    "browser_task",
                    "worker-session",
                    "worker-request",
                    Map.of("task", "open baidu")
            ).join();
        }

        assertFalse(result.isOk());
        assertEquals("browser_task", result.getAction());
        assertTrue(result.getError().contains("recursive_browser_task_blocked"));
    }

    private static Map<?, ?> data(ActionController.ActionResult result) {
        assertInstanceOf(Map.class, result.getData());
        return (Map<?, ?>) result.getData();
    }
}

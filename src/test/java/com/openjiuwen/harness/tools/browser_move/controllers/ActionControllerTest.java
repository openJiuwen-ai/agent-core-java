/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.browser_move.controllers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.Test;

class ActionControllerTest {

    @Test
    void registerListAndRunActionHappyPath() {
        ActionController controller = new ActionController();
        controller.registerAction("  MyAction  ", (sessionId, requestId, params) -> Map.of(
                "ok", true,
                "value", Integer.parseInt(String.valueOf(params.getOrDefault("value", 0))) + 1,
                "handler_session_id", sessionId,
                "handler_request_id", requestId,
                "meta", Map.of("source", params.get("source"))));

        Map<String, Object> result = controller.runAction(
                "MYACTION",
                "session-1",
                "request-1",
                Map.of("value", 41, "source", "test"));

        assertEquals(List.of("myaction"), controller.listActions());
        assertTrue(Boolean.TRUE.equals(result.get("ok")));
        assertEquals("myaction", result.get("action"));
        assertEquals("session-1", result.get("session_id"));
        assertEquals("request-1", result.get("request_id"));
        assertEquals(42, result.get("value"));
        assertEquals("session-1", result.get("handler_session_id"));
        assertEquals("request-1", result.get("handler_request_id"));
    }

    @Test
    void runActionUnknownActionReturnsError() {
        ActionController controller = new ActionController();

        Map<String, Object> result = controller.runAction("missing_action", "s1", "r1", Map.of());

        assertFalse(Boolean.TRUE.equals(result.get("ok")));
        assertEquals("missing_action", result.get("action"));
        assertEquals("s1", result.get("session_id"));
        assertEquals("r1", result.get("request_id"));
        assertTrue(String.valueOf(result.get("error")).contains("unknown action: missing_action"));
    }

    @Test
    void runActionHandlerExceptionIsCaptured() {
        ActionController controller = new ActionController();
        controller.registerAction("explode", (sessionId, requestId, params) -> {
            throw new RuntimeException("boom");
        });

        Map<String, Object> result = controller.runAction("explode", "s2", "r2", Map.of());

        assertFalse(Boolean.TRUE.equals(result.get("ok")));
        assertEquals("explode", result.get("action"));
        assertEquals("s2", result.get("session_id"));
        assertEquals("r2", result.get("request_id"));
        assertTrue(String.valueOf(result.get("error")).contains("boom"));
    }

    @Test
    void browserTaskRuntimeNotBound() {
        ActionController controller = new ActionController();
        controller.registerExampleActions();
        controller.clearRuntimeRunner();

        Map<String, Object> result = controller.runAction("browser_task", "s3", "r3", Map.of("task", "open page"));

        assertFalse(Boolean.TRUE.equals(result.get("ok")));
        assertEquals("s3", result.get("session_id"));
        assertEquals("r3", result.get("request_id"));
        assertTrue(String.valueOf(result.get("error")).contains("runtime_not_bound"));
    }

    @Test
    void browserTaskCallsBoundRunner() {
        ActionController controller = new ActionController();
        controller.registerExampleActions();
        final var observed = new java.util.LinkedHashMap<String, Object>();
        controller.bindRuntimeRunner((task, sessionId, requestId, timeoutS) -> {
            observed.put("task", task);
            observed.put("session_id", sessionId);
            observed.put("request_id", requestId);
            observed.put("timeout_s", timeoutS);
            return CompletableFuture.completedFuture(Map.of("ok", true, "final", "done"));
        });

        Map<String, Object> result = controller.runAction(
                "browser_task",
                "s4",
                "r4",
                Map.of("task", "go to example.com", "timeout_s", "7"));

        assertTrue(Boolean.TRUE.equals(result.get("ok")));
        assertEquals("done", result.get("final"));
        assertEquals(Map.of(
                "task", "go to example.com",
                "session_id", "s4",
                "request_id", "r4",
                "timeout_s", 7), observed);
    }

    @Test
    void describeActionsIncludesRegisteredSpecs() {
        ActionController controller = new ActionController();
        controller.registerExampleActions();

        Map<String, Map<String, Object>> details = controller.describeActions();

        assertTrue(details.containsKey("browser_drag_and_drop"));
        Map<String, Object> spec = details.get("browser_drag_and_drop");
        assertTrue(spec.get("summary") instanceof String);
        assertNotNull(spec.get("params"));
    }

    @Test
    void browserGetElementCoordinatesParsesFencedJsonOutput() {
        ActionController controller = new ActionController();
        controller.registerExampleActions();
        controller.bindRuntimeRunner((task, sessionId, requestId, timeoutS) -> CompletableFuture.completedFuture(
                Map.of(
                        "ok", true,
                        "final", "```json\n{\"ok\": true, \"source\": {\"x\": 12, \"y\": 34}, \"target\": null, \"error\": null}\n```")));

        Map<String, Object> result = controller.runAction(
                "browser_get_element_coordinates",
                "s-json",
                "r-json",
                Map.of("element_source", "Example"));

        assertTrue(Boolean.TRUE.equals(result.get("ok")));
        assertEquals(Map.of("x", 12, "y", 34), result.get("source"));
        assertNull(result.get("target"));
        assertNull(result.get("error"));
    }

    @Test
    void browserGetElementCoordinatesBuildsNormalizedScriptForCodeExecutor() {
        ActionController controller = new ActionController();
        controller.registerExampleActions();
        final var captured = new java.util.ArrayList<String>();
        controller.bindCodeExecutor(jsCode -> {
            captured.add(jsCode);
            return CompletableFuture.completedFuture(
                    "{\"ok\":true,\"source\":{\"x\":18,\"y\":22},\"target\":{\"x\":30,\"y\":44},\"error\":null}");
        });

        Map<String, Object> result = controller.runAction(
                "browser_get_element_coordinates",
                "s-code",
                "r-code",
                Map.of(
                        "url", "https://example.com/ui",
                        "source", "Learn more",
                        "element_target", "#dropzone",
                        "element_source_offset", Map.of("x", 5, "y", 6)));

        assertTrue(Boolean.TRUE.equals(result.get("ok")));
        assertEquals(Map.of("x", 18, "y", 22), result.get("source"));
        assertEquals(Map.of("x", 30, "y", 44), result.get("target"));
        assertEquals(1, captured.size());
        String script = captured.getFirst();
        assertTrue(script.contains("\"element_source\":\"Learn more\""));
        assertTrue(script.contains("\"element_target\":\"#dropzone\""));
        assertTrue(script.contains("\"element_source_offset\":{\"x\":5,\"y\":6}"));
        assertTrue(script.contains("await page.goto(String(params.url).trim());"));
        assertTrue(script.contains("Failed to determine source coordinates from selector."));
    }

    @Test
    void browserDragAndDropBuildsScriptWithAliasesAndCustomSteps() {
        ActionController controller = new ActionController();
        controller.registerExampleActions();
        final var captured = new java.util.ArrayList<String>();
        controller.bindCodeExecutor(jsCode -> {
            captured.add(jsCode);
            Map<String, Object> payload = new java.util.LinkedHashMap<>();
            payload.put("ok", true);
            payload.put("message", "Dragged from (10, 20) to (30, 40)");
            payload.put("source", Map.of("x", 10, "y", 20));
            payload.put("target", Map.of("x", 30, "y", 40));
            payload.put("steps", 12);
            payload.put("delay_ms", 7);
            payload.put("error", null);
            return CompletableFuture.completedFuture(payload);
        });

        Map<String, Object> result = controller.runAction(
                "browser_drag_and_drop",
                "s-drag",
                "r-drag",
                Map.of(
                        "source_x", "10",
                        "source_y", "20",
                        "target_x", "30",
                        "target_y", "40",
                        "steps", 12,
                        "delay_ms", 7));

        assertTrue(Boolean.TRUE.equals(result.get("ok")));
        assertEquals("Dragged from (10, 20) to (30, 40)", result.get("message"));
        assertEquals(1, captured.size());
        String script = captured.getFirst();
        assertTrue(script.contains("\"coord_source_x\":10"));
        assertTrue(script.contains("\"coord_source_y\":20"));
        assertTrue(script.contains("\"coord_target_x\":30"));
        assertTrue(script.contains("\"coord_target_y\":40"));
        assertTrue(script.contains("\"steps\":12"));
        assertTrue(script.contains("\"delay_ms\":7"));
        assertTrue(script.contains("await page.mouse.down();"));
        assertTrue(script.contains("Dragged from (${source.x}, ${source.y}) to (${target.x}, ${target.y})"));
    }

    @Test
    void actionControllerInstanceIsolation() {
        ActionController first = new ActionController();
        ActionController second = new ActionController();
        final var seen = new java.util.ArrayList<String>();

        first.registerAction("only_a", (sessionId, requestId, params) -> Map.of("ok", true, "who", "a"));
        first.bindRuntimeRunner((task, sessionId, requestId, timeoutS) -> {
            seen.add("a:" + task + ":" + sessionId + ":" + requestId);
            return CompletableFuture.completedFuture(Map.of("ok", true, "final", "from_a"));
        });
        second.bindRuntimeRunner((task, sessionId, requestId, timeoutS) -> {
            seen.add("b:" + task + ":" + sessionId + ":" + requestId);
            return CompletableFuture.completedFuture(Map.of("ok", true, "final", "from_b"));
        });
        first.registerExampleActions();
        second.registerExampleActions();

        Map<String, Object> resultA = first.runAction("browser_task", "sa", "ra", Map.of("task", "task-a"));
        Map<String, Object> resultB = second.runAction("browser_task", "sb", "rb", Map.of("task", "task-b"));

        assertEquals(List.of("only_a"), first.listActions().stream().filter("only_a"::equals).toList());
        assertTrue(second.listActions().stream().noneMatch("only_a"::equals));
        assertTrue(Boolean.TRUE.equals(resultA.get("ok")));
        assertTrue(Boolean.TRUE.equals(resultB.get("ok")));
        assertEquals("from_a", resultA.get("final"));
        assertEquals("from_b", resultB.get("final"));
        assertEquals(List.of("a:task-a:sa:ra", "b:task-b:sb:rb"), seen);
    }

    @Test
    void browserWorkerActionContextBlocksRecursiveBrowserTask() {
        ActionController controller = new ActionController();
        controller.registerExampleActions();

        Map<String, Object> result;
        try (ActionController.BrowserWorkerActionContext ignored = controller.browserWorkerActionContext()) {
            result = controller.runAction(
                    "browser_task",
                    "worker-session",
                    "worker-request",
                    Map.of("task", "open baidu"));
        }

        assertFalse(Boolean.TRUE.equals(result.get("ok")));
        assertEquals("browser_task", result.get("action"));
        assertTrue(String.valueOf(result.get("error")).contains("recursive_browser_task_blocked"));
    }
}

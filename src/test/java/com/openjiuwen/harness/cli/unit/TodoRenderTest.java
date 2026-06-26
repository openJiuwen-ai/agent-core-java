/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.cli.unit;

import com.openjiuwen.harness.cli.ui.TodoRender;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's {@code tests.cli.unit.test_todo_render} in
 * {@code tests/cli/unit/test_todo_render.py}.
 */
class TodoRenderTest {

    @Test
    void completedShowsCheckmark() {
        String rendered = TodoRender.renderTodoItem("Task A", "completed");
        assertTrue(rendered.contains("\u2612"));
        assertTrue(rendered.contains("green"));
        assertTrue(rendered.contains("Task A"));
    }

    @Test
    void inProgressShowsHalf() {
        String rendered = TodoRender.renderTodoItem("Task B", "in_progress");
        assertTrue(rendered.contains("\u25fc"));
        assertTrue(rendered.contains("yellow"));
    }

    @Test
    void pendingShowsEmpty() {
        String rendered = TodoRender.renderTodoItem("Task C", "pending");
        assertTrue(rendered.contains("\u2610"));
        assertTrue(rendered.contains("dim"));
    }

    @Test
    void cancelledShowsCrossed() {
        String rendered = TodoRender.renderTodoItem("Task D", "cancelled");
        assertTrue(rendered.contains("\u2715"));
    }

    @Test
    void unknownStatusDefaultsToPending() {
        String rendered = TodoRender.renderTodoItem("Task E", "unknown_status");
        assertTrue(rendered.contains("\u2610"));
    }

    @Test
    void mixedStatusList() {
        List<Map<String, Object>> items = List.of(
                item("content", "completed", "status", "completed"),
                item("content", "running", "status", "in_progress"),
                item("content", "pending", "status", "pending")
        );

        List<String> lines = TodoRender.renderTodoList(items);

        assertEquals(3, lines.size());
        assertTrue(lines.get(0).contains("\u2612"));
        assertTrue(lines.get(1).contains("\u25fc"));
        assertTrue(lines.get(2).contains("\u2610"));
        for (String line : lines) {
            assertTrue(line.contains("\u23bf"));
        }
    }

    @Test
    void emptyListReturnsEmptyLines() {
        assertEquals(List.of(), TodoRender.renderTodoList(List.of()));
    }

    @Test
    void activeFormFallback() {
        List<String> lines = TodoRender.renderTodoList(List.of(
                item("activeForm", "Active task", "status", "in_progress")
        ));

        assertTrue(lines.getFirst().contains("Active task"));
    }

    @Test
    void mixedSummary() {
        String rendered = TodoRender.renderTodoSummary(List.of(
                item("status", "completed"),
                item("status", "completed"),
                item("status", "in_progress"),
                item("status", "pending")
        ));

        assertTrue(rendered.contains("\u2713"));
        assertTrue(rendered.contains("\u25fc"));
        assertTrue(rendered.contains("\u2610"));
    }

    @Test
    void emptySummary() {
        assertEquals("No tasks", TodoRender.renderTodoSummary(List.of()));
    }

    @Test
    void allCompletedSummaryOmitsOtherStates() {
        String rendered = TodoRender.renderTodoSummary(List.of(
                item("status", "completed"),
                item("status", "completed")
        ));

        assertTrue(rendered.contains("\u2713"));
        assertFalse(rendered.contains("\u25fc"));
        assertFalse(rendered.contains("\u2610"));
    }

    @Test
    void parseJsonList() {
        List<Map<String, Object>> items = TodoRender.parseTodoResult("[{\"content\":\"task1\",\"status\":\"pending\"}]");

        assertNotNull(items);
        assertEquals(1, items.size());
        assertEquals("task1", items.getFirst().get("content"));
    }

    @Test
    void parseJsonDictWithItems() {
        List<Map<String, Object>> items = TodoRender.parseTodoResult(
                "{\"items\":[{\"content\":\"a\",\"status\":\"completed\"}]}"
        );

        assertNotNull(items);
        assertEquals(1, items.size());
    }

    @Test
    void parseCreateToolText() {
        String text = "{'message': 'Successfully created 2 task(s):\\n"
                + "  [>] task_id: a1 , content: design\\n"
                + "  [ ] task_id: a2 , content: test'}";

        List<Map<String, Object>> items = TodoRender.parseTodoResult(text);
        assertNotNull(items);
        assertEquals(2, items.size());
        assertEquals("in_progress", items.getFirst().get("status"));
        assertEquals("pending", items.get(1).get("status"));
    }

    @Test
    void parseListToolText() {
        String text = "{'message': 'Todo List (Total: 3 items):\\n"
                + "\\n"
                + "[>] In Progress Task\\n"
                + " [abc-123] design\\n"
                + "\\n"
                + "[ ] Pending Tasks\\n"
                + " [def-456] implement\\n"
                + " [ghi-789] validate'}";

        List<Map<String, Object>> items = TodoRender.parseTodoResult(text);

        assertNotNull(items);
        assertEquals(3, items.size());
        assertEquals("in_progress", items.get(0).get("status"));
        assertEquals("pending", items.get(1).get("status"));
        assertEquals("pending", items.get(2).get("status"));
    }

    @Test
    void parseListToolWithCompleted() {
        String text = "{'message': 'Todo List (Total: 2 items):\\n"
                + "\\n"
                + "[\u221a] Completed Tasks\\n"
                + " [abc-123] done\\n"
                + "\\n"
                + "[>] In Progress Task\\n"
                + " [def-456] running'}";

        List<Map<String, Object>> items = TodoRender.parseTodoResult(text);

        assertNotNull(items);
        assertEquals(2, items.size());
        assertEquals("completed", items.get(0).get("status"));
        assertEquals("in_progress", items.get(1).get("status"));
    }

    @Test
    void parseEmptyString() {
        assertNull(TodoRender.parseTodoResult(""));
    }

    @Test
    void parsePlainText() {
        assertNull(TodoRender.parseTodoResult("just some text"));
    }

    @Test
    void parseModifyToolMessageReturnsNull() {
        assertNull(TodoRender.parseTodoResult("{'message': 'Successfully updated 1 task(s)'}"));
    }

    @Test
    void parseTodoToolArgsFromJson() {
        Map<String, Object> args = TodoRender.parseTodoToolArgs("{\"action\":\"delete\",\"ids\":[\"a\"]}");

        assertEquals("delete", args.get("action"));
        assertEquals(List.of("a"), args.get("ids"));
    }

    @Test
    void applyTodoModifyArgsUpdatesCachedItems() {
        List<Map<String, Object>> items = List.of(
                item("id", "a", "content", "Task A", "status", "in_progress"),
                item("id", "b", "content", "Task B", "status", "pending")
        );

        List<Map<String, Object>> updated = TodoRender.applyTodoModifyArgs(
                items,
                item("action", "update", "todos", List.of(
                        item("id", "a", "status", "completed"),
                        item("id", "b", "status", "in_progress", "activeForm", "Doing B")
                ))
        );

        assertNotNull(updated);
        assertEquals("completed", updated.getFirst().get("status"));
        assertEquals("Doing B", updated.get(1).get("content"));
    }

    @Test
    void applyTodoModifyArgsDeletesCachedItems() {
        List<Map<String, Object>> updated = TodoRender.applyTodoModifyArgs(
                List.of(
                        item("id", "a", "content", "Task A", "status", "pending"),
                        item("id", "b", "content", "Task B", "status", "pending")
                ),
                item("action", "delete", "ids", List.of("a"))
        );

        assertEquals(List.of(item("id", "b", "content", "Task B", "status", "pending")), updated);
    }

    @Test
    void applyTodoModifyArgsInsertAfterKeepsOrder() {
        List<Map<String, Object>> updated = TodoRender.applyTodoModifyArgs(
                List.of(
                        item("id", "a", "content", "Task A", "status", "in_progress"),
                        item("id", "b", "content", "Task B", "status", "pending")
                ),
                item("action", "insert_after", "todo_data", List.of(
                        "a",
                        List.of(item(
                                "id", "c",
                                "content", "Task C",
                                "activeForm", "Task C",
                                "status", "pending"
                        ))
                ))
        );

        assertNotNull(updated);
        assertEquals(List.of("a", "c", "b"), updated.stream().map(item -> item.get("id")).toList());
    }

    private static Map<String, Object> item(Object... entries) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int index = 0; index < entries.length; index += 2) {
            map.put(String.valueOf(entries[index]), entries[index + 1]);
        }
        return map;
    }
}

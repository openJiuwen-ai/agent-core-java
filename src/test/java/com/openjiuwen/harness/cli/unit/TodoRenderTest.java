/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.cli.unit;

import com.openjiuwen.harness.cli.ui.TodoRender;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for CLI todo rendering.
 * <p>
 * Mirrors Python's {@code test_todo_render} in
 * {@code tests.cli.unit.test_todo_render}.
 */
class TodoRenderTest {

    @Nested
    class TestRenderTodoItem {
        @Test
        void completedShowsCheckmark() {
            String result = TodoRender.renderTodoItem("任务A", "completed");
            assertTrue(result.contains("☑"));
            assertTrue(result.contains("green"));
            assertTrue(result.contains("任务A"));
        }

        @Test
        void inProgressShowsHalf() {
            String result = TodoRender.renderTodoItem("任务B", "in_progress");
            assertTrue(result.contains("◐"));
            assertTrue(result.contains("yellow"));
        }

        @Test
        void pendingShowsEmpty() {
            String result = TodoRender.renderTodoItem("任务C", "pending");
            assertTrue(result.contains("☐"));
            assertTrue(result.contains("dim"));
        }

        @Test
        void cancelledShowsCrossed() {
            String result = TodoRender.renderTodoItem("任务D", "cancelled");
            assertTrue(result.contains("☒"));
        }

        @Test
        void unknownStatusDefaultsToPending() {
            String result = TodoRender.renderTodoItem("任务E", "unknown_status");
            assertTrue(result.contains("☐"));
        }
    }

    @Nested
    class TestRenderTodoList {
        @Test
        void mixedStatusList() {
            List<Map<String, Object>> items = List.of(
                    item("content", "完成的", "status", "completed"),
                    item("content", "进行中", "status", "in_progress"),
                    item("content", "待处理", "status", "pending"));

            List<String> lines = TodoRender.renderTodoList(items);

            assertEquals(3, lines.size());
            assertTrue(lines.get(0).contains("☑"));
            assertTrue(lines.get(1).contains("◐"));
            assertTrue(lines.get(2).contains("☐"));
            for (String line : lines) {
                assertTrue(line.contains("⎿"));
            }
        }

        @Test
        void emptyList() {
            assertEquals(List.of(), TodoRender.renderTodoList(List.of()));
        }

        @Test
        void activeFormFallback() {
            List<Map<String, Object>> items = List.of(
                    item("activeForm", "进行中的活动", "status", "in_progress"));

            List<String> lines = TodoRender.renderTodoList(items);

            assertTrue(lines.get(0).contains("进行中的活动"));
        }
    }

    @Nested
    class TestRenderTodoSummary {
        @Test
        void mixedSummary() {
            List<Map<String, Object>> items = List.of(
                    item("status", "completed"),
                    item("status", "completed"),
                    item("status", "in_progress"),
                    item("status", "pending"));

            String result = TodoRender.renderTodoSummary(items);

            assertTrue(result.contains("✓2"));
            assertTrue(result.contains("◐1"));
            assertTrue(result.contains("☐1"));
        }

        @Test
        void emptySummary() {
            assertEquals("No tasks", TodoRender.renderTodoSummary(List.of()));
        }

        @Test
        void allCompleted() {
            List<Map<String, Object>> items = List.of(
                    item("status", "completed"),
                    item("status", "completed"));

            String result = TodoRender.renderTodoSummary(items);

            assertTrue(result.contains("✓2"));
            assertFalse(result.contains("◐"));
            assertFalse(result.contains("☐"));
        }
    }

    @Nested
    class TestParseTodoResult {
        @Test
        void parseJsonList() {
            String result = "[{\"content\": \"task1\", \"status\": \"pending\"}]";
            List<Map<String, Object>> items = TodoRender.parseTodoResult(result);
            assertNotNull(items);
            assertEquals(1, items.size());
            assertEquals("task1", items.get(0).get("content"));
        }

        @Test
        void parseJsonDictWithItems() {
            String result = "{\"items\": [{\"content\": \"a\", \"status\": \"completed\"}]}";
            List<Map<String, Object>> items = TodoRender.parseTodoResult(result);
            assertNotNull(items);
            assertEquals(1, items.size());
        }

        @Test
        void parseCreateToolRepr() {
            String result = "{'message': 'Successfully created 3 task(s):\\n"
                    + "  [>] task_id: abc-123 , content: 设计UI\\n"
                    + "  [ ] task_id: def-456 , content: 实现表单\\n"
                    + "  [ ] task_id: ghi-789 , content: 添加验证\\n"
                    + "\\nNext step: execute'}";

            List<Map<String, Object>> items = TodoRender.parseTodoResult(result);

            assertNotNull(items);
            assertEquals(3, items.size());
            assertEquals("设计UI", items.get(0).get("content"));
            assertEquals("in_progress", items.get(0).get("status"));
            assertEquals("pending", items.get(1).get("status"));
            assertEquals("pending", items.get(2).get("status"));
        }

        @Test
        void parseListToolRepr() {
            String result = "{'message': 'Todo List (Total: 3 items):\\n"
                    + "\\n"
                    + "[>] In Progress Task\\n"
                    + " [abc-123] 设计UI\\n"
                    + "\\n"
                    + "[ ] Pending Tasks\\n"
                    + " [def-456] 实现表单\\n"
                    + " [ghi-789] 添加验证'}";

            List<Map<String, Object>> items = TodoRender.parseTodoResult(result);

            assertNotNull(items);
            assertEquals(3, items.size());
            assertEquals("in_progress", items.get(0).get("status"));
            assertEquals("pending", items.get(1).get("status"));
            assertEquals("pending", items.get(2).get("status"));
        }

        @Test
        void parseListToolWithCompleted() {
            String result = "{'message': 'Todo List (Total: 2 items):\\n"
                    + "\\n"
                    + "[√] Completed Tasks\\n"
                    + " [abc-123] 已完成\\n"
                    + "\\n"
                    + "[>] In Progress Task\\n"
                    + " [def-456] 进行中'}";

            List<Map<String, Object>> items = TodoRender.parseTodoResult(result);

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
        void parseModifyToolRepr() {
            String result = "{'message': 'Successfully updated 1 task(s)'}";
            assertNull(TodoRender.parseTodoResult(result));
        }
    }

    @Nested
    class TestApplyTodoModifyArgs {
        @Test
        void parseTodoToolArgsFromJson() {
            Map<String, Object> args = TodoRender.parseTodoToolArgs("{\"action\":\"delete\",\"ids\":[\"a\"]}");
            assertEquals("delete", args.get("action"));
            assertEquals(List.of("a"), args.get("ids"));
        }

        @Test
        void updateRewritesCachedItems() {
            List<Map<String, Object>> items = List.of(
                    item("id", "a", "content", "Task A", "status", "in_progress"),
                    item("id", "b", "content", "Task B", "status", "pending"));
            Map<String, Object> args = item(
                    "action", "update",
                    "todos", List.of(
                            item("id", "a", "status", "completed"),
                            item("id", "b", "status", "in_progress", "activeForm", "Doing Task B")));

            List<Map<String, Object>> updated = TodoRender.applyTodoModifyArgs(items, args);

            assertNotNull(updated);
            assertEquals("completed", updated.get(0).get("status"));
            assertEquals("in_progress", updated.get(1).get("status"));
            assertEquals("Doing Task B", updated.get(1).get("content"));
        }

        @Test
        void deleteRemovesCachedItems() {
            List<Map<String, Object>> items = List.of(
                    item("id", "a", "content", "Task A", "status", "pending"),
                    item("id", "b", "content", "Task B", "status", "pending"));

            List<Map<String, Object>> updated = TodoRender.applyTodoModifyArgs(
                    items,
                    item("action", "delete", "ids", List.of("a")));

            assertEquals(List.of(item("id", "b", "content", "Task B", "status", "pending")), updated);
        }

        @Test
        void insertAfterKeepsOrder() {
            List<Map<String, Object>> items = List.of(
                    item("id", "a", "content", "Task A", "status", "in_progress"),
                    item("id", "b", "content", "Task B", "status", "pending"));

            List<Map<String, Object>> updated = TodoRender.applyTodoModifyArgs(
                    items,
                    item(
                            "action", "insert_after",
                            "todo_data", List.of(
                                    "a",
                                    List.of(item(
                                            "id", "c",
                                            "content", "Task C",
                                            "activeForm", "Task C",
                                            "status", "pending")))));

            assertNotNull(updated);
            assertEquals(List.of("a", "c", "b"), updated.stream().map(i -> i.get("id")).toList());
        }
    }

    private static Map<String, Object> item(Object... entries) {
        assertEquals(0, entries.length % 2);
        java.util.Map<String, Object> map = new java.util.LinkedHashMap<>();
        for (int index = 0; index < entries.length; index += 2) {
            map.put(String.valueOf(entries[index]), entries[index + 1]);
        }
        return map;
    }
}

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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TodoRenderTest {

    @Test
    void renderTodoItemIncludesStyleAndContent() {
        String rendered = TodoRender.renderTodoItem("task-a", "completed");
        assertTrue(rendered.contains("green"));
        assertTrue(rendered.contains("task-a"));
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
    void applyTodoModifyArgsUpdatesCachedItems() {
        List<Map<String, Object>> items = List.of(
                item("id", "a", "content", "Task A", "status", "pending"),
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

    private static Map<String, Object> item(Object... entries) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int index = 0; index < entries.length; index += 2) {
            map.put(String.valueOf(entries[index]), entries[index + 1]);
        }
        return map;
    }
}

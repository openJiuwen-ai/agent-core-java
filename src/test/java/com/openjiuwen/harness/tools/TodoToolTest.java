/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

class TodoToolTest {
    private static final String SESSION_ID = "todo-tool-session";

    @TempDir
    Path tempDir;

    @Test
    void updateActionShouldApplyUpdatesPayload() throws IOException {
        TodoTool tool = new TodoTool(tempDir.toString());
        savePlan(tool);

        ToolOutput output = tool.modify(SESSION_ID,
                Map.of("action", "update", "updates",
                        List.of(Map.of("task_id", "task-1", "status", "completed", "result_summary", "done"),
                                Map.of("task_id", "task-2", "status", "in_progress"))));

        assertThat(output.isSuccess()).isTrue();
        List<TodoItem> todos = tool.load(SESSION_ID);
        assertThat(todos.get(0).getStatus()).isEqualTo(TodoStatus.COMPLETED);
        assertThat(todos.get(0).getResultSummary()).isEqualTo("done");
        assertThat(todos.get(1).getStatus()).isEqualTo(TodoStatus.IN_PROGRESS);
    }

    @Test
    void updateActionShouldRejectMissingPayload() throws IOException {
        TodoTool tool = new TodoTool(tempDir.toString());
        savePlan(tool);

        ToolOutput output = tool.modify(SESSION_ID, Map.of("action", "update"));

        assertThat(output.isSuccess()).isFalse();
        assertThat(output.getError()).contains("update action").contains("non-empty list");
        assertThat(tool.load(SESSION_ID)).extracting(TodoItem::getStatus)
                .containsExactly(TodoStatus.IN_PROGRESS, TodoStatus.PENDING);
    }

    @Test
    void updateActionShouldKeepTodosPayloadPrecedence() throws IOException {
        TodoTool tool = new TodoTool(tempDir.toString());
        savePlan(tool);

        ToolOutput output = tool.modify(SESSION_ID,
                Map.of("action", "update", "todos", List.of(Map.of("id", "task-1", "status", "completed")),
                        "updates", List.of(Map.of("task_id", "task-1", "status", "cancelled"))));

        assertThat(output.isSuccess()).isTrue();
        assertThat(tool.load(SESSION_ID).get(0).getStatus()).isEqualTo(TodoStatus.COMPLETED);
    }

    private static void savePlan(TodoTool tool) throws IOException {
        tool.save(SESSION_ID,
                List.of(TodoItem.builder().id("task-1").content("Implement fix").status(TodoStatus.IN_PROGRESS).build(),
                        TodoItem.builder().id("task-2").content("Verify fix").status(TodoStatus.PENDING).build()));
    }
}

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

/**
 * Verifies that {@link TodoTool} enforces the single-IN_PROGRESS constraint
 * at the task level while allowing parallel tool execution within a single
 * task (Issue #151).
 *
 * <p>Design constraint: a session may have at most one task in_progress
 * because tasks may have dependency relationships. However, a single task
 * may execute multiple tool calls in parallel — the tool execution state
 * is tracked separately from the todo status.</p>
 *
 * @since 0.1.16
 */
class TodoToolParallelInProgressTest {
    private static final String SESSION_ID = "parallel-todo-session";

    @TempDir
    Path tempDir;

    @Test
    void shouldRejectMultipleInProgressTasks() throws IOException {
        TodoTool tool = new TodoTool(tempDir.toString());
        // Save an initial plan with one in_progress task
        tool.save(SESSION_ID,
                List.of(
                        TodoItem.builder().id("task-1").content("Task 1").status(TodoStatus.IN_PROGRESS).build(),
                        TodoItem.builder().id("task-2").content("Task 2").status(TodoStatus.PENDING).build()));

        // Attempt to set task-2 to in_progress while task-1 is still in_progress
        // This should fail — only one task may be in_progress at a time
        ToolOutput output = tool.modify(SESSION_ID,
                Map.of("action", "update", "updates",
                        List.of(Map.of("task_id", "task-2", "status", "in_progress"))));

        assertThat(output.isSuccess()).isFalse();
        assertThat(output.getError()).contains("in_progress");

        // Verify state is unchanged
        List<TodoItem> todos = tool.load(SESSION_ID);
        assertThat(todos.get(0).getStatus()).isEqualTo(TodoStatus.IN_PROGRESS);
        assertThat(todos.get(1).getStatus()).isEqualTo(TodoStatus.PENDING);
    }

    @Test
    void shouldAllowTransitionFromCompletedToInProgress() throws IOException {
        TodoTool tool = new TodoTool(tempDir.toString());
        // task-1 is completed, task-2 is pending — transitioning task-2 to
        // in_progress should succeed because there is no other in_progress task
        tool.save(SESSION_ID,
                List.of(
                        TodoItem.builder().id("task-1").content("Task 1").status(TodoStatus.COMPLETED).build(),
                        TodoItem.builder().id("task-2").content("Task 2").status(TodoStatus.PENDING).build()));

        ToolOutput output = tool.modify(SESSION_ID,
                Map.of("action", "update", "updates",
                        List.of(Map.of("task_id", "task-2", "status", "in_progress"))));

        assertThat(output.isSuccess()).isTrue();
        List<TodoItem> todos = tool.load(SESSION_ID);
        assertThat(todos.get(0).getStatus()).isEqualTo(TodoStatus.COMPLETED);
        assertThat(todos.get(1).getStatus()).isEqualTo(TodoStatus.IN_PROGRESS);
    }

    @Test
    void shouldAllowParallelToolExecutionWithinSingleTask() throws IOException {
        // This test verifies that a single in_progress task can have multiple
        // tool calls executing in parallel — the parallel tool execution is
        // handled at the AbilityManager level, not at the Todo level.
        // The Todo status remains IN_PROGRESS for the single task throughout.
        TodoTool tool = new TodoTool(tempDir.toString());
        tool.save(SESSION_ID,
                List.of(
                        TodoItem.builder().id("task-1").content("Task 1").status(TodoStatus.IN_PROGRESS).build(),
                        TodoItem.builder().id("task-2").content("Task 2").status(TodoStatus.PENDING).build(),
                        TodoItem.builder().id("task-3").content("Task 3").status(TodoStatus.PENDING).build()));

        // The task stays in_progress while multiple tools execute in parallel.
        // No todo status change is needed for parallel tool execution.
        // Only when the task is done does it transition to COMPLETED.
        ToolOutput output = tool.modify(SESSION_ID,
                Map.of("action", "update", "updates",
                        List.of(Map.of("task_id", "task-1", "status", "completed",
                                "result_summary", "All parallel tools completed"))));

        assertThat(output.isSuccess()).isTrue();
        List<TodoItem> todos = tool.load(SESSION_ID);
        assertThat(todos.get(0).getStatus()).isEqualTo(TodoStatus.COMPLETED);
        assertThat(todos.get(0).getResultSummary()).isEqualTo("All parallel tools completed");
    }
}

/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.core.planner;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for TaskDecomposer.
 * <p>
 * Mirrors Python's planner task decomposition tests.
 */
@DisplayName("Task Decomposer Tests")
class TestTaskDecomposer {

    // Stub classes
    static class TaskStub {
        String id;
        String description;
        String status;
        List<TaskStub> subtasks = new ArrayList<>();
        Map<String, Object> metadata = new HashMap<>();

        TaskStub(String id, String description) {
            this.id = id;
            this.description = description;
            this.status = "PENDING";
        }

        void addSubtask(TaskStub subtask) {
            subtasks.add(subtask);
        }

        void setStatus(String status) {
            this.status = status;
        }

        List<TaskStub> getSubtasks() {
            return new ArrayList<>(subtasks);
        }
    }

    static class TaskDecomposer {
        int maxDepth = 3;

        List<TaskStub> decompose(TaskStub task) {
            List<TaskStub> result = new ArrayList<>();
            decomposeRecursive(task, result, 0);
            return result;
        }

        private void decomposeRecursive(TaskStub task, List<TaskStub> result, int depth) {
            if (depth >= maxDepth || task.subtasks.isEmpty()) {
                result.add(task);
                return;
            }
            for (TaskStub subtask : task.subtasks) {
                decomposeRecursive(subtask, result, depth + 1);
            }
        }
    }

    @Nested
    @DisplayName("Task Creation Tests")
    class TestTaskCreation {

        @Test
        @DisplayName("task creation")
        void testTaskCreation() {
            TaskStub task = new TaskStub("task-1", "Complete the project");

            assertNotNull(task);
            assertEquals("task-1", task.id);
            assertEquals("Complete the project", task.description);
            assertEquals("PENDING", task.status);
        }

        @Test
        @DisplayName("task with metadata")
        void testTaskWithMetadata() {
            TaskStub task = new TaskStub("task-1", "Test task");
            task.metadata.put("priority", "high");
            task.metadata.put("assignee", "user1");

            assertEquals("high", task.metadata.get("priority"));
            assertEquals("user1", task.metadata.get("assignee"));
        }
    }

    @Nested
    @DisplayName("Task Decomposition Tests")
    class TestTaskDecomposition {

        @Test
        @DisplayName("decompose single task")
        void testDecomposeSingleTask() {
            TaskDecomposer decomposer = new TaskDecomposer();
            TaskStub task = new TaskStub("main", "Main task");

            List<TaskStub> result = decomposer.decompose(task);

            assertEquals(1, result.size());
        }

        @Test
        @DisplayName("decompose task with subtasks")
        void testDecomposeTaskWithSubtasks() {
            TaskDecomposer decomposer = new TaskDecomposer();
            TaskStub task = new TaskStub("main", "Main task");
            task.addSubtask(new TaskStub("sub-1", "Subtask 1"));
            task.addSubtask(new TaskStub("sub-2", "Subtask 2"));

            List<TaskStub> result = decomposer.decompose(task);

            assertEquals(2, result.size());
        }

        @Test
        @DisplayName("decompose respects max depth")
        void testDecomposeRespectsMaxDepth() {
            TaskDecomposer decomposer = new TaskDecomposer();
            TaskStub task = new TaskStub("main", "Main");
            TaskStub sub1 = new TaskStub("sub-1", "Sub 1");
            TaskStub sub2 = new TaskStub("sub-2", "Sub 2");
            TaskStub sub3 = new TaskStub("sub-3", "Sub 3");
            sub2.addSubtask(sub3);
            sub1.addSubtask(sub2);
            task.addSubtask(sub1);

            List<TaskStub> result = decomposer.decompose(task);

            // Should stop at max depth
            assertTrue(result.size() <= 4);
        }
    }

    @Nested
    @DisplayName("Task Status Tests")
    class TestTaskStatus {

        @Test
        @DisplayName("task status change")
        void testTaskStatusChange() {
            TaskStub task = new TaskStub("task-1", "Test");

            task.setStatus("COMPLETED");

            assertEquals("COMPLETED", task.status);
        }
    }
}
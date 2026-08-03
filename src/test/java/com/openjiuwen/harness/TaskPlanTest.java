/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.harness;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.openjiuwen.harness.schema.task.TaskPlan;
import com.openjiuwen.harness.schema.task.TodoItem;
import com.openjiuwen.harness.schema.task.TodoStatus;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for TaskPlan / TodoItem models.
 *
 * <p>Mirrors Python's {@code test_task_plan} in
 * {@code tests/unit_tests/harness/test_task_plan.py}.
 */
@Tag("unit-test")
class TaskPlanTest {

    @Test
    @DisplayName("TodoItem keeps Python-like defaults")
    void todoItemDefaults() {
        TodoItem item = new TodoItem(null, "");

        assertEquals(TodoStatus.PENDING, item.getStatus());
        assertTrue(item.getDependsOn().isEmpty());
        assertNull(item.getResultSummary());
        assertNull(item.getMetaData());
        assertEquals(36, item.getId().length());
    }

    @Test
    @DisplayName("TodoItem factory preserves existing Java call sites")
    void todoItemCreateFactoryWorks() {
        TodoItem item = TodoItem.create("test task", "smart");

        assertEquals("test task", item.getContent());
        assertEquals("Executing test task", item.getActiveForm());
        assertEquals("smart", item.getSelectedModelId());
    }

    @Test
    @DisplayName("TodoItem can keep selected model id")
    void todoItemWithSelectedModelId() {
        TodoItem item = new TodoItem("smart_task", "smart task", "", "", null, null, null, null, "smart");

        assertEquals("smart", item.getSelectedModelId());
        assertEquals(TodoStatus.PENDING, item.getStatus());
    }

    @Test
    @DisplayName("addTask and getTask round-trip")
    void taskPlanAddAndGet() {
        TaskPlan plan = new TaskPlan("test goal");
        TodoItem task = new TodoItem("a1", "step 1");

        plan.addTask(task);

        assertEquals(task, plan.getTask("a1"));
        assertNull(plan.getTask("missing"));
    }

    @Test
    @DisplayName("getNextTask respects dependencies")
    void getNextTaskRespectsDependencies() {
        TodoItem first = new TodoItem("t1", "first");
        TodoItem second = new TodoItem("t2", "second");
        second.getDependsOn().add("t1");
        TodoItem third = new TodoItem("t3", "third");

        TaskPlan plan = new TaskPlan("goal", Arrays.asList(first, second, third));

        TodoItem next = plan.getNextTask();
        assertNotNull(next);
        assertEquals("t1", next.getId());

        first.setStatus(TodoStatus.COMPLETED);
        next = plan.getNextTask();
        assertNotNull(next);
        assertEquals("t2", next.getId());
    }

    @Test
    @DisplayName("getNextTask skips completed and cancelled tasks")
    void getNextTaskSkipsCompletedAndCancelled() {
        TodoItem first = new TodoItem("t1", "first");
        first.setStatus(TodoStatus.COMPLETED);
        TodoItem second = new TodoItem("t2", "second");
        second.setStatus(TodoStatus.CANCELLED);
        TodoItem third = new TodoItem("t3", "third");

        TaskPlan plan = new TaskPlan("goal", Arrays.asList(first, second, third));

        TodoItem next = plan.getNextTask();
        assertNotNull(next);
        assertEquals("t3", next.getId());
    }

    @Test
    @DisplayName("progress summary counts only completed tasks")
    void progressSummaryMatchesPython() {
        TodoItem first = new TodoItem("a", "a");
        first.setStatus(TodoStatus.COMPLETED);
        TodoItem second = new TodoItem("b", "b");
        second.setStatus(TodoStatus.COMPLETED);
        TodoItem third = new TodoItem("c", "c");

        TaskPlan plan = new TaskPlan("goal", Arrays.asList(first, second, third));

        assertEquals("2/3 completed", plan.getProgressSummary());
    }

    @Test
    @DisplayName("toMarkdown renders status marks and summaries")
    void toMarkdownRendersChecklist() {
        TodoItem pending = new TodoItem("p", "pending");
        TodoItem active = new TodoItem("i", "active");
        active.setStatus(TodoStatus.IN_PROGRESS);
        TodoItem done = new TodoItem("d", "done");
        done.setStatus(TodoStatus.COMPLETED);
        done.setResultSummary("finished");
        TodoItem cancelled = new TodoItem("c", "cancelled");
        cancelled.setStatus(TodoStatus.CANCELLED);

        TaskPlan plan = new TaskPlan("goal", List.of(pending, active, done, cancelled));

        assertEquals(
                "## Goal: goal\n\n- [ ] pending\n- [>] active\n- [\u221a] done \u2014 finished\n- [\u00d7] cancelled",
                plan.toMarkdown()
        );
    }

    @Test
    @DisplayName("toMap and fromMap preserve Python field names")
    void toMapFromMapRoundTrip() {
        TodoItem completed = new TodoItem("r1", "first");
        completed.setStatus(TodoStatus.COMPLETED);
        TodoItem dependent = new TodoItem("r2", "second");
        dependent.getDependsOn().add("r1");
        dependent.setMetaData(Map.of("k", "v"));

        TaskPlan restored = TaskPlan.fromMap(new TaskPlan("roundtrip", List.of(completed, dependent)).toMap());

        assertEquals("roundtrip", restored.getGoal());
        assertEquals(2, restored.getTasks().size());
        assertEquals(TodoStatus.COMPLETED, restored.getTasks().get(0).getStatus());
        assertEquals(List.of("r1"), restored.getTasks().get(1).getDependsOn());
        assertEquals(Map.of("k", "v"), restored.getTasks().get(1).getMetaData());
    }

    @Test
    @DisplayName("fromMap with null or empty returns empty plan")
    void fromMapEmptyReturnsEmptyPlan() {
        assertEquals("", TaskPlan.fromMap(null).getGoal());
        assertTrue(TaskPlan.fromMap(null).getTasks().isEmpty());
        assertEquals("", TaskPlan.fromMap(Map.of()).getGoal());
        assertTrue(TaskPlan.fromMap(Map.of()).getTasks().isEmpty());
    }

    @Test
    @DisplayName("TodoItem toMap preserves Python field names")
    void todoItemToMapPreservesPythonFieldNames() {
        TodoItem item = new TodoItem(
                "test-id",
                "test",
                "Testing",
                "desc",
                TodoStatus.IN_PROGRESS,
                List.of(),
                null,
                null,
                "smart"
        );

        Map<String, Object> data = item.toMap();

        assertEquals("test-id", data.get("id"));
        assertEquals("test", data.get("content"));
        assertEquals("Testing", data.get("activeForm"));
        assertEquals("desc", data.get("description"));
        assertEquals("in_progress", data.get("status"));
        assertEquals("smart", data.get("selected_model_id"));
    }

    @Test
    @DisplayName("fromMap keeps meta_data null when omitted")
    void todoItemFromMapPreservesPythonFieldNames() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", "task-id");
        data.put("content", "test");
        data.put("activeForm", "Testing");
        data.put("description", "desc");
        data.put("status", "in_progress");
        data.put("depends_on", List.of("other-id"));
        data.put("selected_model_id", "smart");

        TodoItem item = TodoItem.fromMap(data);

        assertEquals("task-id", item.getId());
        assertEquals("test", item.getContent());
        assertEquals("Testing", item.getActiveForm());
        assertEquals("desc", item.getDescription());
        assertEquals(TodoStatus.IN_PROGRESS, item.getStatus());
        assertEquals(List.of("other-id"), item.getDependsOn());
        assertEquals("smart", item.getSelectedModelId());
        assertNull(item.getMetaData());
    }

    @Test
    @DisplayName("status icons match Python constants")
    void statusIconsMatchPythonConstants() {
        assertEquals("[ ]", TodoStatus.PENDING.getStatusIcon());
        assertEquals("[\u2192]", TodoStatus.IN_PROGRESS.getStatusIcon());
        assertEquals("[\u221a]", TodoStatus.COMPLETED.getStatusIcon());
        assertEquals("[\u00d7]", TodoStatus.CANCELLED.getStatusIcon());
        assertFalse(TodoStatus.PENDING.getStatusIcon().isEmpty());
    }
}

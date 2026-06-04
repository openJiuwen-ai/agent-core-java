/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.harness;

import com.openjiuwen.harness.schema.task.TaskPlan;
import com.openjiuwen.harness.schema.task.TodoItem;
import com.openjiuwen.harness.schema.task.TodoStatus;
import org.junit.jupiter.api.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for TaskPlan / TodoItem models.
 * <p>
 * Mirrors Python's {@code test_task_plan} in
 * {@code tests.unit_tests.harness.test_task_plan}.
 */
@Tag("unit-test")
class TaskPlanTest {

    @Test
    @DisplayName("TodoItem has sensible defaults")
    void testTodoItemDefaults() {
        TodoItem item = TodoItem.create("do something");
        assertEquals(TodoStatus.PENDING, item.getStatus());
        assertTrue(item.getDependsOn().isEmpty());
        assertNull(item.getResultSummary());
        assertEquals(36, item.getId().length());
    }

    @Test
    @DisplayName("TodoItem.create factory works")
    void testTodoItemCreate() {
        TodoItem item = TodoItem.create("test task");
        assertEquals("test task", item.getContent());
        assertEquals("Executing test task", item.getActiveForm());
        assertEquals(TodoStatus.PENDING, item.getStatus());
    }

    @Test
    @DisplayName("TodoItem can have selected_model_id")
    void testTodoItemWithModelId() {
        TodoItem item = TodoItem.create("smart task", "smart");
        assertEquals("smart", item.getSelectedModelId());
    }

    @Test
    @DisplayName("add_task / get_task round-trip")
    void testTaskPlanAddAndGet() {
        TaskPlan plan = new TaskPlan("test goal");
        TodoItem t = new TodoItem("a1", "step 1");
        plan.addTask(t);
        assertEquals(t, plan.getTask("a1"));
        assertNull(plan.getTask("missing"));
    }

    @Test
    @DisplayName("get_next_task skips tasks with unmet deps")
    void testGetNextTaskRespectsDeps() {
        TodoItem t1 = new TodoItem("t1", "first");
        t1.setStatus(TodoStatus.PENDING);
        
        TodoItem t2 = new TodoItem("t2", "second");
        t2.setStatus(TodoStatus.PENDING);
        t2.getDependsOn().add("t1");
        
        TodoItem t3 = new TodoItem("t3", "third");
        t3.setStatus(TodoStatus.PENDING);

        TaskPlan plan = new TaskPlan("g", Arrays.asList(t1, t2, t3));

        TodoItem next = plan.getNextTask();
        assertNotNull(next);
        assertEquals("t1", next.getId());

        t1.setStatus(TodoStatus.COMPLETED);
        next = plan.getNextTask();
        assertNotNull(next);
        assertEquals("t2", next.getId());
    }

    @Test
    @DisplayName("get_next_task skips completed and cancelled tasks")
    void testGetNextTaskSkipsCompleted() {
        TodoItem t1 = new TodoItem("t1", "first");
        t1.setStatus(TodoStatus.COMPLETED);
        
        TodoItem t2 = new TodoItem("t2", "second");
        t2.setStatus(TodoStatus.CANCELLED);
        
        TodoItem t3 = new TodoItem("t3", "third");
        t3.setStatus(TodoStatus.PENDING);

        TaskPlan plan = new TaskPlan("g", Arrays.asList(t1, t2, t3));

        TodoItem next = plan.getNextTask();
        assertNotNull(next);
        assertEquals("t3", next.getId());
    }

    @Test
    @DisplayName("get_progress_summary returns correct string")
    void testProgressSummary() {
        TodoItem t1 = new TodoItem("a", "a");
        t1.setStatus(TodoStatus.COMPLETED);
        
        TodoItem t2 = new TodoItem("b", "b");
        t2.setStatus(TodoStatus.COMPLETED);
        
        TodoItem t3 = new TodoItem("c", "c");
        t3.setStatus(TodoStatus.PENDING);

        TaskPlan plan = new TaskPlan("g", Arrays.asList(t1, t2, t3));
        assertEquals("2/3 completed", plan.getProgressSummary());
    }

    @Test
    @DisplayName("to_dict/from_dict round-trip preserves data")
    void testToDictFromDictRoundtrip() {
        TodoItem t1 = new TodoItem("r1", "first");
        t1.setStatus(TodoStatus.COMPLETED);
        TodoItem t2 = new TodoItem("r2", "second");
        t2.getDependsOn().add("r1");

        TaskPlan restored = TaskPlan.fromMap(new TaskPlan("roundtrip", List.of(t1, t2)).toMap());

        assertEquals("roundtrip", restored.getGoal());
        assertEquals(2, restored.getTasks().size());
        assertEquals(TodoStatus.COMPLETED, restored.getTasks().get(0).getStatus());
        assertEquals(List.of("r1"), restored.getTasks().get(1).getDependsOn());
    }

    @Test
    @DisplayName("from_dict with empty input returns empty plan")
    void testFromDictEmpty() {
        assertEquals("", TaskPlan.fromMap(null).getGoal());
        assertEquals("", TaskPlan.fromMap(Map.of()).getGoal());
    }

    @Test
    @DisplayName("TodoItem.to_dict works correctly")
    void testTodoItemToDict() {
        TodoItem item = new TodoItem(
                "test-id",
                "test",
                "Testing",
                "desc",
                TodoStatus.IN_PROGRESS,
                List.of(),
                null,
                Map.of(),
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
    @DisplayName("TodoItem.from_dict works correctly")
    void testTodoItemFromDict() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", "test-id");
        data.put("content", "test");
        data.put("activeForm", "Testing");
        data.put("description", "desc");
        data.put("status", "in_progress");
        data.put("depends_on", List.of("other-id"));
        data.put("selected_model_id", "smart");

        TodoItem item = TodoItem.fromMap(data);

        assertEquals("test-id", item.getId());
        assertEquals("test", item.getContent());
        assertEquals(TodoStatus.IN_PROGRESS, item.getStatus());
        assertEquals("smart", item.getSelectedModelId());
    }
}

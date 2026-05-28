/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.harness;

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

    /**
     * Placeholder TodoStatus enum.
     */
    enum TodoStatus {
        PENDING,
        IN_PROGRESS,
        COMPLETED,
        CANCELLED
    }

    /**
     * Placeholder TodoItem class.
     */
    static class TodoItem {
        private String id;
        private String content;
        private TodoStatus status;
        private List<String> dependsOn;
        private String resultSummary;
        private String activeForm;
        private String selectedModelId;

        public TodoItem(String id, String content) {
            this.id = id != null ? id : UUID.randomUUID().toString();
            this.content = content;
            this.status = TodoStatus.PENDING;
            this.dependsOn = new ArrayList<>();
            this.resultSummary = null;
        }

        public static TodoItem create(String content) {
            TodoItem item = new TodoItem(null, content);
            item.activeForm = "Executing " + content;
            return item;
        }

        public static TodoItem create(String content, String selectedModelId) {
            TodoItem item = create(content);
            item.selectedModelId = selectedModelId;
            return item;
        }

        public String getId() { return id; }
        public String getContent() { return content; }
        public TodoStatus getStatus() { return status; }
        public void setStatus(TodoStatus status) { this.status = status; }
        public List<String> getDependsOn() { return dependsOn; }
        public String getResultSummary() { return resultSummary; }
        public String getSelectedModelId() { return selectedModelId; }
        public String getActiveForm() { return activeForm; }
    }

    /**
     * Placeholder TaskPlan class.
     */
    static class TaskPlan {
        private String goal;
        private List<TodoItem> tasks;

        public TaskPlan(String goal) {
            this.goal = goal;
            this.tasks = new ArrayList<>();
        }

        public TaskPlan(String goal, List<TodoItem> tasks) {
            this.goal = goal;
            this.tasks = tasks != null ? tasks : new ArrayList<>();
        }

        public void addTask(TodoItem task) {
            tasks.add(task);
        }

        public TodoItem getTask(String id) {
            for (TodoItem t : tasks) {
                if (t.getId().equals(id)) {
                    return t;
                }
            }
            return null;
        }

        public TodoItem getNextTask() {
            for (TodoItem t : tasks) {
                if (t.getStatus() == TodoStatus.PENDING) {
                    // Check dependencies
                    boolean depsMet = true;
                    for (String depId : t.getDependsOn()) {
                        TodoItem dep = getTask(depId);
                        if (dep != null && dep.getStatus() != TodoStatus.COMPLETED) {
                            depsMet = false;
                            break;
                        }
                    }
                    if (depsMet) {
                        return t;
                    }
                }
            }
            return null;
        }

        public String getProgressSummary() {
            int completed = 0, pending = 0, inProgress = 0;
            for (TodoItem t : tasks) {
                switch (t.getStatus()) {
                    case COMPLETED: completed++; break;
                    case PENDING: pending++; break;
                    case IN_PROGRESS: inProgress++; break;
                    default: break;
                }
            }
            return String.format("Progress: %d/%d completed, %d pending, %d in progress",
                    completed, tasks.size(), pending, inProgress);
        }
    }

    @Test
    @DisplayName("TodoItem has sensible defaults")
    void testTodoItemDefaults() {
        TodoItem item = new TodoItem("id1", "do something");
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
        t2.setStatus(TodoStatus.PENDING);
        
        TodoItem t3 = new TodoItem("c", "c");
        t3.setStatus(TodoStatus.IN_PROGRESS);

        TaskPlan plan = new TaskPlan("g", Arrays.asList(t1, t2, t3));
        String summary = plan.getProgressSummary();
        assertTrue(summary.contains("1/3"));
        assertTrue(summary.contains("completed"));
    }
}
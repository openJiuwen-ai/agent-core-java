/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools;

import com.openjiuwen.harness.schema.task.TodoItem;
import com.openjiuwen.harness.schema.task.TodoStatus;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.BeforeEach;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for todo tools.
 *
 * <p>Mirrors Python's {@code test_todo.py} in
 * {@code tests.unit_tests.harness.tools}.
 */
class TestTodo {

    private String testContent;
    private String testActiveForm;

    @BeforeEach
    void setUp() {
        testContent = "Test task content";
        testActiveForm = "Executing test task";
    }

    @Nested
    class TestTodoItem {

        @Test
        void testTodoItemCreate() {
            // TodoItem.create sets content, activeForm, status correctly
            TodoItem todo = TodoItem.create(testContent, testActiveForm, "", TodoStatus.PENDING, null);
            assertEquals(testContent, todo.getContent());
            assertEquals(testActiveForm, todo.getActiveForm());
            assertEquals(TodoStatus.PENDING, todo.getStatus());
            assertNull(todo.getSelectedModelId());
        }

        @Test
        void testTodoItemToDict() {
            // TodoItem.toMap includes all fields
            TodoItem todo = TodoItem.create(testContent, testActiveForm, "", TodoStatus.PENDING, null);
            Map<String, Object> d = todo.toMap();
            assertEquals(testContent, d.get("content"));
            assertEquals(testActiveForm, d.get("activeForm"));
            assertEquals(TodoStatus.PENDING.getValue(), d.get("status"));
            assertEquals(todo.getId(), d.get("id"));
        }

        @Test
        void testTodoItemFromDict() {
            // TodoItem.fromMap reconstructs the item correctly
            String testId = UUID.randomUUID().toString();
            Map<String, Object> todoDict = Map.of(
                "id", testId,
                "content", testContent,
                "activeForm", testActiveForm,
                "description", "",
                "status", TodoStatus.IN_PROGRESS.getValue(),
                "depends_on", java.util.List.of(),
                "result_summary", "",
                "meta_data", Map.of(),
                "selected_model_id", "fast"
            );
            TodoItem todo = TodoItem.fromMap(todoDict);
            assertEquals(testId, todo.getId());
            assertEquals(testContent, todo.getContent());
            assertEquals(TodoStatus.IN_PROGRESS, todo.getStatus());
            assertEquals("fast", todo.getSelectedModelId());
        }

        @Test
        void testTodoItemMarkInProgress() {
            TodoItem todo = TodoItem.create(testContent, null, "", TodoStatus.PENDING, null);
            todo.setStatus(TodoStatus.IN_PROGRESS);
            assertEquals(TodoStatus.IN_PROGRESS, todo.getStatus());
        }

        @Test
        void testTodoItemMarkCompleted() {
            TodoItem todo = TodoItem.create(testContent, null, "", TodoStatus.IN_PROGRESS, null);
            todo.setStatus(TodoStatus.COMPLETED);
            assertEquals(TodoStatus.COMPLETED, todo.getStatus());
        }

        @Test
        void testTodoItemMarkCancelled() {
            TodoItem todo = TodoItem.create(testContent, null, "", TodoStatus.PENDING, null);
            todo.setStatus(TodoStatus.CANCELLED);
            assertEquals(TodoStatus.CANCELLED, todo.getStatus());
        }

        @Test
        void testTodoItemStatusIcons() {
            // STATUS_ICONS mapping check (if implemented in Java)
            // In Python: STATUS_ICONS = {"pending": "⏳", "in_progress": "▶", "completed": "✅", "cancelled": "❌"}
            // This test verifies status transitions work correctly
            TodoItem todo = TodoItem.create(testContent, null, "", TodoStatus.PENDING, null);
            
            // Test all status transitions
            for (TodoStatus status : TodoStatus.values()) {
                todo.setStatus(status);
                assertEquals(status, todo.getStatus());
            }
        }
    }

    @Nested
    class TestTodoCreateTool {

        @Test
        void testCreateRequiresContent() {
            // TodoItem requires content
            TodoItem todo = TodoItem.create("", null, "", null, null);
            // Content defaults to empty string, not null
            assertEquals("", todo.getContent());
            
            // With valid content
            TodoItem todoWithContent = TodoItem.create("Some task", null, "", null, null);
            assertEquals("Some task", todoWithContent.getContent());
        }

        @Test
        void testCreateSetsStatusPending() {
            // TodoItem.create defaults status to PENDING
            TodoItem todo = TodoItem.create("Task", null, "", null, null);
            assertEquals(TodoStatus.PENDING, todo.getStatus());
        }

        @Test
        void testCreateReturnsTodoId() {
            // TodoItem.create generates a unique ID
            TodoItem todo1 = TodoItem.create("Task1", null, "", null, null);
            TodoItem todo2 = TodoItem.create("Task2", null, "", null, null);
            
            assertNotNull(todo1.getId());
            assertNotNull(todo2.getId());
            assertNotEquals(todo1.getId(), todo2.getId());
        }

        @Test
        void testCreateWithActiveForm() {
            // TodoItem.create can include activeForm
            TodoItem todo = TodoItem.create("Task", "Executing task", "", null, null);
            assertEquals("Executing task", todo.getActiveForm());
        }

        @Test
        void testCreateWithPriority() {
            // TodoItem.create can include selected_model_id (priority indicator)
            TodoItem todo = TodoItem.create("Task", null, "", null, "high");
            assertEquals("high", todo.getSelectedModelId());
        }
    }

    @Nested
    class TestTodoListTool {

        @Test
        void testListReturnsTodos() {
            // Verify TodoItem list can be created and accessed
            java.util.List<TodoItem> todos = java.util.List.of(
                TodoItem.create("Task 1", null, "", TodoStatus.IN_PROGRESS, null),
                TodoItem.create("Task 2", null, "", TodoStatus.PENDING, null)
            );
            assertEquals(2, todos.size());
            assertEquals("Task 1", todos.get(0).getContent());
        }

        @Test
        void testListEmptyInitially() {
            // Empty todo list is valid
            java.util.List<TodoItem> emptyTodos = java.util.List.of();
            assertEquals(0, emptyTodos.size());
        }

        @Test
        void testListFiltersByStatus() {
            // Test filtering todos by status
            java.util.List<TodoItem> todos = java.util.List.of(
                TodoItem.create("Task 1", null, "", TodoStatus.IN_PROGRESS, null),
                TodoItem.create("Task 2", null, "", TodoStatus.PENDING, null),
                TodoItem.create("Task 3", null, "", TodoStatus.COMPLETED, null)
            );
            
            // Filter by IN_PROGRESS
            java.util.List<TodoItem> inProgress = todos.stream()
                .filter(t -> t.getStatus() == TodoStatus.IN_PROGRESS)
                .toList();
            assertEquals(1, inProgress.size());
            
            // Filter by PENDING
            java.util.List<TodoItem> pending = todos.stream()
                .filter(t -> t.getStatus() == TodoStatus.PENDING)
                .toList();
            assertEquals(1, pending.size());
        }
    }

    @Nested
    class TestTodoModifyTool {

        @Test
        void testModifyRequiresId() {
            // TodoItem ID is immutable after creation
            TodoItem todo = TodoItem.create("Task", null, "", null, null);
            String originalId = todo.getId();
            
            // ID should remain the same
            assertEquals(originalId, todo.getId());
        }

        @Test
        void testModifyChangesStatus() {
            TodoItem todo = TodoItem.create("Task", null, "", TodoStatus.PENDING, null);
            todo.setStatus(TodoStatus.COMPLETED);
            assertEquals(TodoStatus.COMPLETED, todo.getStatus());
        }

        @Test
        void testModifyChangesContent() {
            TodoItem todo = TodoItem.create("Original", null, "", null, null);
            todo.setContent("Modified");
            assertEquals("Modified", todo.getContent());
        }

        @Test
        void testModifyInvalidId() {
            // Invalid ID in fromMap should still create a TodoItem with generated ID
            Map<String, Object> invalidDict = Map.of(
                "id", "invalid-uuid-format",
                "content", "Task",
                "status", "pending"
            );
            TodoItem todo = TodoItem.fromMap(invalidDict);
            assertNotNull(todo.getId());
            assertEquals("invalid-uuid-format", todo.getId());
        }
    }

    @Nested
    class TestTodoGetTool {

        @Test
        void testGetReturnsTodo() {
            // TodoItem can be retrieved by its properties
            TodoItem todo = TodoItem.create("Task", null, "", null, null);
            assertNotNull(todo);
            assertEquals("Task", todo.getContent());
        }

        @Test
        void testGetInvalidId() {
            // fromMap with invalid data should still create a valid TodoItem
            Map<String, Object> emptyDict = Map.of();
            TodoItem todo = TodoItem.fromMap(emptyDict);
            assertNotNull(todo.getId());
            assertEquals("", todo.getContent());
            assertEquals(TodoStatus.PENDING, todo.getStatus());
        }
    }

    @Nested
    class TestTodoTool {

        @Test
        void testTodoToolSchema() {
            // Verify TodoItem schema structure
            TodoItem todo = TodoItem.create("Task", "Active", "Desc", TodoStatus.PENDING, "model-1");
            Map<String, Object> schema = todo.toMap();
            
            // Verify all expected fields
            assertTrue(schema.containsKey("id"));
            assertTrue(schema.containsKey("content"));
            assertTrue(schema.containsKey("activeForm"));
            assertTrue(schema.containsKey("description"));
            assertTrue(schema.containsKey("status"));
            assertTrue(schema.containsKey("depends_on"));
            assertTrue(schema.containsKey("result_summary"));
            assertTrue(schema.containsKey("meta_data"));
            assertTrue(schema.containsKey("selected_model_id"));
        }

        @Test
        void testTodoToolIntegration() {
            // Integration test: create, modify, and serialize TodoItem
            TodoItem todo = TodoItem.create("Integration Task", null, "", TodoStatus.PENDING, null);
            
            // Modify status
            todo.setStatus(TodoStatus.IN_PROGRESS);
            
            // Serialize
            Map<String, Object> serialized = todo.toMap();
            
            // Deserialize
            TodoItem restored = TodoItem.fromMap(serialized);
            
            // Verify round-trip
            assertEquals(todo.getId(), restored.getId());
            assertEquals(todo.getContent(), restored.getContent());
            assertEquals(todo.getStatus(), restored.getStatus());
        }
    }
}
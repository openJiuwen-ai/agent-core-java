/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools;

import com.openjiuwen.core.session.Session;
import com.openjiuwen.core.sysop.SysOperation;
import com.openjiuwen.harness.schema.task.TodoItem;
import com.openjiuwen.harness.schema.task.TodoStatus;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for todo tools.
 *
 * <p>Mirrors Python's {@code test_todo.py} in
 * {@code tests/unit_tests/harness/tools/test_todo.py}.
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
            TodoItem todo = TodoItem.create(testContent, testActiveForm, "", TodoStatus.PENDING, null);
            assertEquals(testContent, todo.getContent());
            assertEquals(testActiveForm, todo.getActiveForm());
            assertEquals(TodoStatus.PENDING, todo.getStatus());
            assertNull(todo.getSelectedModelId());
        }

        @Test
        void testTodoItemToDict() {
            TodoItem todo = TodoItem.create(testContent, testActiveForm, "", TodoStatus.PENDING, null);
            Map<String, Object> d = todo.toMap();
            assertEquals(testContent, d.get("content"));
            assertEquals(testActiveForm, d.get("activeForm"));
            assertEquals(TodoStatus.PENDING.getValue(), d.get("status"));
            assertEquals(todo.getId(), d.get("id"));
        }

        @Test
        void testTodoItemFromDict() {
            String testId = UUID.randomUUID().toString();
            Map<String, Object> todoDict = todoMap(
                    testId,
                    testContent,
                    testActiveForm,
                    "",
                    TodoStatus.IN_PROGRESS.getValue(),
                    List.of(),
                    "",
                    Map.of(),
                    "fast"
            );
            TodoItem todo = TodoItem.fromMap(todoDict);
            assertEquals(testId, todo.getId());
            assertEquals(testContent, todo.getContent());
            assertEquals(TodoStatus.IN_PROGRESS, todo.getStatus());
            assertEquals("fast", todo.getSelectedModelId());
        }

        @Test
        void testTodoItemCreateWithModelId() {
            TodoItem todo = TodoItem.create("task", "smart");
            assertEquals("smart", todo.getSelectedModelId());
        }
    }

    @Nested
    class TestTodoTool {

        @Test
        void testLoadTodosSuccess() {
            FakeSession session = new FakeSession();
            TodoItem todo = TodoItem.create("Task 1", "Doing Task 1", "Desc 1", TodoStatus.IN_PROGRESS, null);
            session.updateState(Map.of("harness.todos", List.of(todo.toMap())));

            ExposedTodoTool tool = new ExposedTodoTool(new FakeSysOperation());
            List<TodoItem> loaded = tool.load(session);

            assertEquals(1, loaded.size());
            assertEquals("Task 1", loaded.get(0).getContent());
            assertEquals(TodoStatus.IN_PROGRESS, loaded.get(0).getStatus());
        }

        @Test
        void testLoadTodosReadFail() {
            FakeSession session = new FakeSession();
            session.updateState(Map.of("harness.todos", "not-a-list"));

            ExposedTodoTool tool = new ExposedTodoTool(new FakeSysOperation());
            List<TodoItem> loaded = tool.load(session);

            assertTrue(loaded.isEmpty());
        }

        @Test
        void testSaveTodosSuccess() {
            FakeSession session = new FakeSession();
            TodoItem todo = TodoItem.create("Task 1", "Doing Task 1", "Desc 1", TodoStatus.IN_PROGRESS, null);

            ExposedTodoTool tool = new ExposedTodoTool(new FakeSysOperation());
            tool.save(session, List.of(todo));

            Object raw = session.getState("harness.todos");
            assertInstanceOf(List.class, raw);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> data = (List<Map<String, Object>>) raw;
            assertEquals(1, data.size());
            assertEquals("Task 1", data.get(0).get("content"));
        }

        @Test
        void testRequireSessionMissing() {
            ExposedTodoTool tool = new ExposedTodoTool(new FakeSysOperation());

            IllegalStateException error = assertThrows(
                    IllegalStateException.class,
                    () -> tool.requireSession(Map.of())
            );

            assertTrue(error.getMessage().contains("todo tools require session"));
        }
    }

    @Nested
    class TestTodoCreateTool {

        @Test
        void testInvokeCreateJsonArray() {
            TodoCreateTool tool = new TodoCreateTool(new FakeSysOperation());
            FakeSession session = new FakeSession();
            Map<String, Object> result = invoke(tool, Map.of(
                    "tasks", List.of(
                            task("Task 1", "Doing Task 1", "Desc 1", null),
                            task("Task 2", "Doing Task 2", "Desc 2", null),
                            task("Task 3", "Doing Task 3", "Desc 3", null)
                    )
            ), session);

            assertTrue(String.valueOf(result.get("message")).contains("Successfully created 3 task(s)"));
            List<Map<String, Object>> saved = savedTodos(session);
            assertEquals(3, saved.size());
            assertEquals(TodoStatus.IN_PROGRESS.getValue(), saved.get(0).get("status"));
            assertEquals(TodoStatus.PENDING.getValue(), saved.get(1).get("status"));
        }

        @Test
        void testInvokeCreateWithChineseContent() {
            TodoCreateTool tool = new TodoCreateTool(new FakeSysOperation());
            FakeSession session = new FakeSession();
            Map<String, Object> result = invoke(tool, Map.of(
                    "tasks", List.of(
                            task("\u9700\u6c42\u5206\u6790", "\u6b63\u5728\u5206\u6790\u9700\u6c42", "\u660e\u786e\u9879\u76ee\u76ee\u6807\u3001\u7528\u6237\u9700\u6c42\u53ca\u529f\u80fd\u8fb9\u754c", null),
                            task("\u6280\u672f\u9009\u578b", "\u6b63\u5728\u9009\u578b", "\u8bc4\u4f30\u5e76\u9009\u62e9\u5408\u9002\u7684\u6280\u672f\u67b6\u6784", null),
                            task("\u5b9e\u65bd\u65b9\u6848", "\u6b63\u5728\u5236\u5b9a\u65b9\u6848", "\u5236\u5b9a\u5f00\u53d1\u8ba1\u5212\u3001\u5206\u914d\u4efb\u52a1", null)
                    )
            ), session);

            assertTrue(String.valueOf(result.get("message")).contains("Successfully created 3 task(s)"));
            List<Map<String, Object>> saved = savedTodos(session);
            assertEquals(3, saved.size());
            assertTrue(String.valueOf(saved.get(0).get("description")).contains("\u76ee\u6807"));
            assertTrue(String.valueOf(saved.get(2).get("description")).contains("\u5206\u914d\u4efb\u52a1"));
        }

        @Test
        void testInvokeCreateWithSelectedModelId() {
            TodoCreateTool tool = new TodoCreateTool(new FakeSysOperation());
            FakeSession session = new FakeSession();
            Map<String, Object> result = invoke(tool, Map.of(
                    "tasks", List.of(
                            task("Translate doc", "Translating doc", "Translate document to English", "fast"),
                            task("Analyze code", "Analyzing code", "Analyze code architecture", "smart")
                    )
            ), session);

            assertTrue(String.valueOf(result.get("message")).contains("Successfully created 2 task(s)"));
            List<Map<String, Object>> saved = savedTodos(session);
            assertEquals("fast", saved.get(0).get("selected_model_id"));
            assertEquals("smart", saved.get(1).get("selected_model_id"));
            assertEquals(TodoStatus.IN_PROGRESS.getValue(), saved.get(0).get("status"));
        }

        @Test
        void testInvokeCreateEmptyTasks() {
            TodoCreateTool tool = new TodoCreateTool(new FakeSysOperation());
            FakeSession session = new FakeSession();

            assertThrows(IllegalArgumentException.class, () -> invoke(tool, Map.of("tasks", List.of()), session));
        }

        @Test
        void testInvokeMissingTasksParam() {
            TodoCreateTool tool = new TodoCreateTool(new FakeSysOperation());
            FakeSession session = new FakeSession();

            assertThrows(IllegalArgumentException.class, () -> invoke(tool, Map.of(), session));
        }

        @Test
        void testInvokeCreateInvalidJsonString() {
            TodoCreateTool tool = new TodoCreateTool(new FakeSysOperation());
            FakeSession session = new FakeSession();

            assertThrows(IllegalArgumentException.class,
                    () -> invoke(tool, Map.of("tasks", "not a valid json array"), session));
        }

        @Test
        void testInvokeCreateMissingRequiredField() {
            TodoCreateTool tool = new TodoCreateTool(new FakeSysOperation());
            FakeSession session = new FakeSession();

            assertThrows(IllegalArgumentException.class,
                    () -> invoke(tool, Map.of("tasks", List.of(Map.of("activeForm", "Doing"))), session));
            assertThrows(IllegalArgumentException.class,
                    () -> invoke(tool, Map.of("tasks", List.of(Map.of("content", "Task 1"))), session));
            assertThrows(IllegalArgumentException.class,
                    () -> invoke(tool, Map.of("tasks", List.of(Map.of("content", "Task 1", "activeForm", "Doing"))), session));
        }
    }

    @Nested
    class TestTodoListTool {

        @Test
        void testInvokeListSuccess() {
            FakeSession session = new FakeSession();
            session.updateState(Map.of("harness.todos", List.of(
                    todoMap(null, "In Progress Task", "Doing", "Desc", TodoStatus.IN_PROGRESS.getValue(), List.of(), null, Map.of(), null),
                    todoMap(null, "Pending Task", "Doing", "Desc", TodoStatus.PENDING.getValue(), List.of(), null, Map.of(), null),
                    todoMap(null, "Completed Task", "Doing", "Desc", TodoStatus.COMPLETED.getValue(), List.of(), null, Map.of(), null),
                    todoMap(null, "Cancelled Task", "Doing", "Desc", TodoStatus.CANCELLED.getValue(), List.of(), null, Map.of(), null)
            )));
            TodoListTool tool = new TodoListTool(new FakeSysOperation());

            Map<String, Object> result = invoke(tool, Map.of(), session);

            assertTrue(result.containsKey("tasks"));
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> tasks = (List<Map<String, Object>>) result.get("tasks");
            assertEquals(2, tasks.size());
            List<String> contents = tasks.stream().map(task -> String.valueOf(task.get("content"))).toList();
            assertTrue(contents.contains("In Progress Task"));
            assertTrue(contents.contains("Pending Task"));
            assertFalse(contents.contains("Completed Task"));
            assertFalse(contents.contains("Cancelled Task"));
            assertTrue(tasks.get(0).containsKey("id"));
            assertTrue(tasks.get(0).containsKey("depends_on"));
        }
    }

    @Nested
    class TestTodoModifyTool {

        @Test
        void testInvokeDeleteSuccess() {
            FakeSession session = seededSession();
            TodoModifyTool tool = new TodoModifyTool(new FakeSysOperation());
            String deleteId = savedTodos(session).get(1).get("id").toString();

            Map<String, Object> result = invoke(tool, Map.of("action", "delete", "ids", List.of(deleteId)), session);

            assertTrue(String.valueOf(result.get("message")).contains("Successfully deleted 1 task(s)"));
            assertEquals(2, savedTodos(session).size());
            assertEquals("Task 1", savedTodos(session).get(0).get("content"));
            assertEquals("Task 3", savedTodos(session).get(1).get("content"));
        }

        @Test
        void testInvokeDeleteNonexistent() {
            FakeSession session = seededSession();
            TodoModifyTool tool = new TodoModifyTool(new FakeSysOperation());

            Map<String, Object> result = invoke(tool, Map.of("action", "delete", "ids", List.of("nonexistent_id")), session);

            assertTrue(String.valueOf(result.get("message")).contains("No tasks deleted"));
        }

        @Test
        void testInvokeUpdateSuccess() {
            FakeSession session = seededSession();
            TodoModifyTool tool = new TodoModifyTool(new FakeSysOperation());
            String todoId = savedTodos(session).get(0).get("id").toString();

            Map<String, Object> result = invoke(tool, Map.of(
                    "action", "update",
                    "todos", List.of(Map.of(
                            "id", todoId,
                            "content", "Updated Task 1",
                            "activeForm", "Executing Updated Task 1",
                            "status", TodoStatus.COMPLETED.getValue()
                    ))
            ), session);

            assertTrue(String.valueOf(result.get("message")).contains("Successfully updated 1 task(s)"));
            Map<String, Object> updated = findById(savedTodos(session), todoId);
            assertEquals("Updated Task 1", updated.get("content"));
            assertEquals(TodoStatus.COMPLETED.getValue(), updated.get("status"));
        }

        @Test
        void testInvokeUpdatePartialFieldsSuccess() {
            FakeSession session = seededSession();
            TodoModifyTool tool = new TodoModifyTool(new FakeSysOperation());
            String todoId = savedTodos(session).get(0).get("id").toString();

            Map<String, Object> result = invoke(tool, Map.of(
                    "action", "update",
                    "todos", List.of(Map.of(
                            "id", todoId,
                            "status", TodoStatus.COMPLETED.getValue()
                    ))
            ), session);

            assertTrue(String.valueOf(result.get("message")).contains("Successfully updated 1 task(s)"));
            Map<String, Object> updated = findById(savedTodos(session), todoId);
            assertEquals(TodoStatus.COMPLETED.getValue(), updated.get("status"));
            assertEquals("Task 1", updated.get("content"));
            assertEquals("Executing Task 1", updated.get("activeForm"));
        }

        @Test
        void testInvokeUpdateSelectedModelId() {
            FakeSession session = seededSession();
            TodoModifyTool tool = new TodoModifyTool(new FakeSysOperation());
            String todoId = savedTodos(session).get(0).get("id").toString();

            Map<String, Object> result = invoke(tool, Map.of(
                    "action", "update",
                    "todos", List.of(Map.of(
                            "id", todoId,
                            "selected_model_id", "smart",
                            "status", TodoStatus.PENDING.getValue()
                    ))
            ), session);

            assertTrue(String.valueOf(result.get("message")).contains("Successfully updated 1 task(s)"));
            Map<String, Object> updated = findById(savedTodos(session), todoId);
            assertEquals("smart", updated.get("selected_model_id"));
        }

        @Test
        void testInvokeAppendSuccess() {
            FakeSession session = seededSession();
            TodoModifyTool tool = new TodoModifyTool(new FakeSysOperation());
            String newId = UUID.randomUUID().toString();

            Map<String, Object> result = invoke(tool, Map.of(
                    "action", "append",
                    "todos", List.of(todoMap(
                            newId,
                            "New Task 4",
                            "Executing New Task 4",
                            "description of New Task 4",
                            TodoStatus.PENDING.getValue(),
                            List.of(),
                            null,
                            Map.of(),
                            null
                    ))
            ), session);

            assertTrue(String.valueOf(result.get("message")).contains("Successfully appended 1 task(s)"));
            assertEquals(4, savedTodos(session).size());
            assertEquals(newId, savedTodos(session).get(3).get("id"));
        }

        @Test
        void testInvokeInsertAfterSuccess() {
            FakeSession session = seededSession();
            TodoModifyTool tool = new TodoModifyTool(new FakeSysOperation());
            String targetId = savedTodos(session).get(0).get("id").toString();
            String newId = UUID.randomUUID().toString();

            Map<String, Object> result = invoke(tool, Map.of(
                    "action", "insert_after",
                    "todo_data", Map.of(
                            "target_id", targetId,
                            "items", List.of(todoMap(
                                    newId,
                                    "Inserted Task",
                                    "Executing Inserted Task",
                                    "description of New Task 4",
                                    TodoStatus.PENDING.getValue(),
                                    List.of(),
                                    null,
                                    Map.of(),
                                    null
                            ))
                    )
            ), session);

            assertTrue(String.valueOf(result.get("message")).contains("Successfully inserted 1 task(s) after target task"));
            assertEquals(4, savedTodos(session).size());
            assertEquals(newId, savedTodos(session).get(1).get("id"));
        }

        @Test
        void testInvokeInsertBeforeSuccess() {
            FakeSession session = seededSession();
            TodoModifyTool tool = new TodoModifyTool(new FakeSysOperation());
            String targetId = savedTodos(session).get(1).get("id").toString();
            String newId = UUID.randomUUID().toString();

            Map<String, Object> result = invoke(tool, Map.of(
                    "action", "insert_before",
                    "todo_data", Map.of(
                            "target_id", targetId,
                            "items", List.of(todoMap(
                                    newId,
                                    "Inserted Before Task",
                                    "Executing",
                                    "description of New Task 4",
                                    TodoStatus.PENDING.getValue(),
                                    List.of(),
                                    null,
                                    Map.of(),
                                    null
                            ))
                    )
            ), session);

            assertTrue(String.valueOf(result.get("message")).contains("Successfully inserted 1 task(s) before target task"));
            assertEquals(4, savedTodos(session).size());
            assertEquals(newId, savedTodos(session).get(1).get("id"));
        }

        @Test
        void testInvokeCancelSuccess() {
            FakeSession session = seededSession();
            TodoModifyTool tool = new TodoModifyTool(new FakeSysOperation());
            String cancelId = savedTodos(session).get(1).get("id").toString();

            Map<String, Object> result = invoke(tool, Map.of("action", "cancel", "ids", List.of(cancelId)), session);

            assertTrue(String.valueOf(result.get("message")).contains("Successfully cancelled 1 task(s)"));
            Map<String, Object> cancelled = findById(savedTodos(session), cancelId);
            assertEquals(TodoStatus.CANCELLED.getValue(), cancelled.get("status"));
        }

        @Test
        void testInvokeCancelNonexistent() {
            FakeSession session = seededSession();
            TodoModifyTool tool = new TodoModifyTool(new FakeSysOperation());

            Map<String, Object> result = invoke(tool, Map.of("action", "cancel", "ids", List.of("nonexistent_id")), session);

            assertTrue(String.valueOf(result.get("message")).contains("No tasks cancelled"));
        }
    }

    @Nested
    class TestTodoGetTool {

        @Test
        void testInvokeGetSuccess() {
            FakeSession session = seededSession();
            TodoGetTool tool = new TodoGetTool(new FakeSysOperation());
            String todoId = savedTodos(session).get(0).get("id").toString();

            Map<String, Object> result = invoke(tool, Map.of("id", todoId), session);

            assertTrue(result.containsKey("todo"));
            Map<String, Object> todo = assertInstanceOf(Map.class, result.get("todo"));
            assertEquals(todoId, todo.get("id"));
            assertEquals("Task 1", todo.get("content"));
            assertEquals(TodoStatus.IN_PROGRESS.getValue(), todo.get("status"));
            assertEquals("Detailed description for Task 1", todo.get("description"));
            assertEquals("smart", todo.get("selected_model_id"));
        }

        @Test
        void testInvokeGetNotFound() {
            FakeSession session = seededSession();
            TodoGetTool tool = new TodoGetTool(new FakeSysOperation());

            IllegalArgumentException error = assertThrows(
                    IllegalArgumentException.class,
                    () -> invoke(tool, Map.of("id", "nonexistent_id"), session)
            );

            assertTrue(error.getMessage().contains("not found"));
        }

        @Test
        void testInvokeGetMissingId() {
            FakeSession session = seededSession();
            TodoGetTool tool = new TodoGetTool(new FakeSysOperation());

            IllegalArgumentException error = assertThrows(
                    IllegalArgumentException.class,
                    () -> invoke(tool, Map.of(), session)
            );

            assertTrue(error.getMessage().contains("Task ID is required"));
        }
    }

    private static Map<String, Object> invoke(TodoTool tool, Map<String, Object> inputs, Session session) {
        try {
            return (Map<String, Object>) tool.invoke(inputs, Map.of("session", session));
        } catch (RuntimeException e) {
            throw e;
        } catch (Throwable e) {
            throw new AssertionError(e);
        }
    }

    private static Map<String, Object> task(String content, String activeForm, String description, String modelId) {
        Map<String, Object> task = new LinkedHashMap<>();
        task.put("content", content);
        task.put("activeForm", activeForm);
        task.put("description", description);
        if (modelId != null) {
            task.put("selected_model_id", modelId);
        }
        return task;
    }

    private static Map<String, Object> todoMap(String id, String content, String activeForm, String description,
                                              String status, List<String> dependsOn, String resultSummary,
                                              Map<String, Object> metaData, String selectedModelId) {
        Map<String, Object> todo = new LinkedHashMap<>();
        todo.put("id", id);
        todo.put("content", content);
        todo.put("activeForm", activeForm);
        todo.put("description", description);
        todo.put("status", status);
        todo.put("depends_on", dependsOn != null ? new ArrayList<>(dependsOn) : List.of());
        todo.put("result_summary", resultSummary);
        todo.put("meta_data", metaData != null ? new LinkedHashMap<>(metaData) : Map.of());
        todo.put("selected_model_id", selectedModelId);
        return todo;
    }

    private static FakeSession seededSession() {
        FakeSession session = new FakeSession();
        List<Map<String, Object>> todos = List.of(
                todoMap(UUID.randomUUID().toString(), "Task 1", "Executing Task 1", "Detailed description for Task 1", TodoStatus.IN_PROGRESS.getValue(), List.of(), null, Map.of(), "smart"),
                todoMap(UUID.randomUUID().toString(), "Task 2", "Executing Task 2", "Detailed description for Task 2", TodoStatus.PENDING.getValue(), List.of(), null, Map.of(), null),
                todoMap(UUID.randomUUID().toString(), "Task 3", "Executing Task 3", "Detailed description for Task 3", TodoStatus.PENDING.getValue(), List.of(), null, Map.of(), null)
        );
        session.updateState(Map.of("harness.todos", todos));
        return session;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> savedTodos(FakeSession session) {
        return (List<Map<String, Object>>) session.getState("harness.todos");
    }

    private static Map<String, Object> findById(List<Map<String, Object>> todos, String id) {
        for (Map<String, Object> todo : todos) {
            if (id.equals(todo.get("id"))) {
                return todo;
            }
        }
        throw new IllegalArgumentException("todo not found: " + id);
    }

    private static final class ExposedTodoTool extends TodoTool {
        private ExposedTodoTool(SysOperation sysOperation) {
            super("todo_base", "todo_base", "todo base", sysOperation);
        }

        @Override
        public Object invoke(Map<String, Object> inputs, Map<String, Object> kwargs) {
            return Map.of();
        }

        List<TodoItem> load(Session session) {
            return loadTodos(session);
        }

        void save(Session session, List<TodoItem> todos) {
            saveTodos(session, todos);
        }

        @Override
        protected Session requireSession(Map<String, Object> kwargs) {
            return super.requireSession(kwargs);
        }
    }

    private static final class FakeSysOperation extends SysOperation {
        private FakeSysOperation() {
            super(com.openjiuwen.core.sysop.SysOperationCard.builder()
                    .id("todo-op")
                    .mode(com.openjiuwen.core.sysop.OperationMode.LOCAL)
                    .workConfig(com.openjiuwen.core.sysop.config.LocalWorkConfig.builder().shellAllowlist(List.of()).build())
                    .build());
        }
    }

    private static final class FakeSession implements Session {
        private final Map<String, Object> state = new LinkedHashMap<>();
        private final String sessionId = UUID.randomUUID().toString();

        @Override
        public String getSessionId() {
            return sessionId;
        }

        @Override
        public Object getState(String key) {
            return state.get(key);
        }

        @Override
        public void updateState(Map<String, Object> stateUpdate) {
            if (stateUpdate == null) {
                return;
            }
            for (Map.Entry<String, Object> entry : stateUpdate.entrySet()) {
                if (entry.getValue() == null) {
                    state.remove(entry.getKey());
                } else {
                    state.put(entry.getKey(), entry.getValue());
                }
            }
        }
    }
}

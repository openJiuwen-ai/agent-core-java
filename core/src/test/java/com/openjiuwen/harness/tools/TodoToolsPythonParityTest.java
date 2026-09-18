/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.harness.tools;

import com.openjiuwen.core.common.exception.FrameworkError;
import com.openjiuwen.core.common.exception.ValidationError;
import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.harness.schema.task.TodoItem;
import com.openjiuwen.harness.schema.task.TodoStatus;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.function.Executable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's {@code TestTodoItem}, {@code TestTodoTool}, {@code TestTodoCreateTool},
 * {@code TestTodoListTool}, {@code TestTodoModifyTool}, and {@code TestTodoGetTool} in
 * {@code tests/unit_tests/harness/tools/test_todo.py}.
 */
class TodoToolsPythonParityTest {

    private static final String TEST_CONTENT = "Test task content";
    private static final String TEST_ACTIVE_FORM = "Executing test task";

    @TestFactory
    Collection<DynamicTest> todoToolsPythonParity() {
        List<DynamicTest> tests = new ArrayList<>();
        add(tests, "TestTodoItem::test_todo_item_create", this::todoItemCreate);
        add(tests, "TestTodoItem::test_todo_item_create_with_model_id", this::todoItemCreateWithModelId);
        add(tests, "TestTodoItem::test_todo_item_from_dict", this::todoItemFromDict);
        add(tests, "TestTodoItem::test_todo_item_to_dict", this::todoItemToDict);
        add(tests, "TestTodoTool::test_load_todos_read_fail", this::loadTodosReadFail);
        add(tests, "TestTodoTool::test_load_todos_success", this::loadTodosSuccess);
        add(tests, "TestTodoTool::test_save_todos_success", this::saveTodosSuccess);
        add(tests, "TestTodoTool::test_save_todos_write_fail", this::saveTodosWriteFail);
        add(tests, "TestTodoCreateTool::test_invoke_create_empty_tasks", this::invokeCreateEmptyTasks);
        add(tests, "TestTodoCreateTool::test_invoke_create_invalid_json_string", this::invokeCreateInvalidJsonString);
        add(tests, "TestTodoCreateTool::test_invoke_create_json_array", this::invokeCreateJsonArray);
        add(tests, "TestTodoCreateTool::test_invoke_create_missing_required_field",
                this::invokeCreateMissingRequiredField);
        add(tests, "TestTodoCreateTool::test_invoke_create_with_chinese_content",
                this::invokeCreateWithChineseContent);
        add(tests, "TestTodoCreateTool::test_invoke_missing_tasks_param", this::invokeMissingTasksParam);
        add(tests, "TestTodoListTool::test_invoke_list_success", this::invokeListSuccess);
        add(tests, "TestTodoModifyTool::test_invoke_append_success", this::invokeAppendSuccess);
        add(tests, "TestTodoModifyTool::test_invoke_cancel_nonexistent", this::invokeCancelNonexistent);
        add(tests, "TestTodoModifyTool::test_invoke_cancel_success", this::invokeCancelSuccess);
        add(tests, "TestTodoModifyTool::test_invoke_delete_nonexistent", this::invokeDeleteNonexistent);
        add(tests, "TestTodoModifyTool::test_invoke_delete_success", this::invokeDeleteSuccess);
        add(tests, "TestTodoModifyTool::test_invoke_insert_after_success", this::invokeInsertAfterSuccess);
        add(tests, "TestTodoModifyTool::test_invoke_insert_before_success", this::invokeInsertBeforeSuccess);
        add(tests, "TestTodoModifyTool::test_invoke_update_partial_fields_success",
                this::invokeUpdatePartialFieldsSuccess);
        add(tests, "TestTodoModifyTool::test_invoke_update_selected_model_id",
                this::invokeUpdateSelectedModelId);
        add(tests, "TestTodoModifyTool::test_invoke_update_success", this::invokeUpdateSuccess);
        add(tests, "TestTodoGetTool::test_invoke_get_missing_id", this::invokeGetMissingId);
        add(tests, "TestTodoGetTool::test_invoke_get_not_found", this::invokeGetNotFound);
        add(tests, "TestTodoGetTool::test_invoke_get_success", this::invokeGetSuccess);
        return tests;
    }

    private static void add(List<DynamicTest> tests, String pythonName, Executable executable) {
        tests.add(DynamicTest.dynamicTest(pythonName, executable));
    }

    private void todoItemCreate() {
        TodoItem todo = new TodoItem("test_task", TEST_CONTENT, TEST_ACTIVE_FORM, "", null, null, null, null, null);

        assertEquals(TEST_CONTENT, todo.getContent());
        assertEquals(TEST_ACTIVE_FORM, todo.getActiveForm());
        assertEquals(TodoStatus.PENDING, todo.getStatus());
        assertNull(todo.getSelectedModelId());
    }

    private void todoItemCreateWithModelId() {
        TodoItem todo = new TodoItem("test_task", "task", "", "", null, null, null, null, "smart");

        assertEquals("smart", todo.getSelectedModelId());
    }

    private void todoItemFromDict() {
        String taskId = UUID.randomUUID().toString();
        TodoItem todo = TodoItem.fromMap(map(
                "id", taskId,
                "content", TEST_CONTENT,
                "activeForm", TEST_ACTIVE_FORM,
                "description", "",
                "status", "in_progress",
                "depends_on", List.of(),
                "result_summary", null,
                "meta_data", null,
                "selected_model_id", "fast"
        ));

        assertEquals(taskId, todo.getId());
        assertEquals(TEST_CONTENT, todo.getContent());
        assertEquals(TodoStatus.IN_PROGRESS, todo.getStatus());
        assertEquals("fast", todo.getSelectedModelId());
    }

    private void todoItemToDict() {
        TodoItem todo = new TodoItem("test_task", TEST_CONTENT, TEST_ACTIVE_FORM, "", null, null, null, null, null);
        Map<String, Object> data = todo.toMap();

        assertEquals(TEST_CONTENT, data.get("content"));
        assertEquals(TEST_ACTIVE_FORM, data.get("activeForm"));
        assertEquals("pending", data.get("status"));
        assertEquals(todo.getId(), data.get("id"));
    }

    private void loadTodosSuccess() {
        MemoryTodoStore store = new MemoryTodoStore(List.of(
                todo("task_1", "Task 1", TodoStatus.IN_PROGRESS),
                todo("task_2", "Task 2", TodoStatus.PENDING)
        ));
        InspectableTodoTool tool = new InspectableTodoTool(store);

        List<TodoItem> loaded = tool.loadForTest(kwargs());

        assertEquals(2, loaded.size());
        assertEquals("Task 1", loaded.get(0).getContent());
        assertEquals(TodoStatus.PENDING, loaded.get(1).getStatus());
    }

    private void loadTodosReadFail() {
        MemoryTodoStore store = new MemoryTodoStore(List.of());
        store.loadFailure = new IllegalStateException("read_file fail");
        InspectableTodoTool tool = new InspectableTodoTool(store);

        FrameworkError error = assertThrows(FrameworkError.class, () -> tool.loadForTest(kwargs()));
        assertTrue(error.getMessage().contains("todo tool loads failed"));
    }

    private void saveTodosSuccess() {
        MemoryTodoStore store = new MemoryTodoStore(List.of());
        InspectableTodoTool tool = new InspectableTodoTool(store);

        tool.saveForTest(List.of(todo("task_1", "Task 1", TodoStatus.PENDING)), kwargs());

        assertEquals(1, store.saveCount);
        assertEquals("Task 1", store.todos.get(0).getContent());
    }

    private void saveTodosWriteFail() {
        MemoryTodoStore store = new MemoryTodoStore(List.of());
        store.saveFailure = new IllegalStateException("write_file fail");
        InspectableTodoTool tool = new InspectableTodoTool(store);

        FrameworkError error = assertThrows(FrameworkError.class,
                () -> tool.saveForTest(List.of(todo("task_1", "Task 1", TodoStatus.PENDING)), kwargs()));
        assertTrue(error.getMessage().contains("Failed to save todo list"));
    }

    private void invokeCreateJsonArray() throws Exception {
        MemoryTodoStore store = new MemoryTodoStore(List.of());
        TodoTools.TodoCreateTool tool = new TodoTools.TodoCreateTool(store);
        List<Map<String, Object>> tasks = List.of(
                task("translate_doc", "Translate doc", "Translating doc", "Translate document to English", "fast"),
                task("analyze_code", "Analyze code", "Analyzing code", "Analyze code architecture", "smart")
        );

        Map<String, Object> result = invoke(tool, map("tasks", tasks));

        assertTrue(String.valueOf(result.get("message")).contains("Successfully created 2 task(s)"));
        assertEquals(2, store.todos.size());
        assertEquals("fast", store.todos.get(0).getSelectedModelId());
        assertEquals("smart", store.todos.get(1).getSelectedModelId());
        assertEquals(TodoStatus.IN_PROGRESS, store.todos.get(0).getStatus());
        assertEquals(TodoStatus.PENDING, store.todos.get(1).getStatus());
    }

    private void invokeCreateWithChineseContent() throws Exception {
        MemoryTodoStore store = new MemoryTodoStore(List.of());
        TodoTools.TodoCreateTool tool = new TodoTools.TodoCreateTool(store);
        List<Map<String, Object>> tasks = List.of(
                task("requirements", "\u9700\u6c42\u5206\u6790", "\u6b63\u5728\u5206\u6790\u9700\u6c42",
                        "\u660e\u786e\u9879\u76ee\u76ee\u6807\u3001\u7528\u6237\u9700\u6c42\u53ca\u529f\u80fd\u8fb9\u754c", null),
                task("tech", "\u6280\u672f\u9009\u578b", "\u6b63\u5728\u9009\u578b",
                        "\u8bc4\u4f30\u5e76\u9009\u62e9\u5408\u9002\u7684\u6280\u672f\u6808", null),
                task("plan", "\u5b9e\u65bd\u65b9\u6848", "\u6b63\u5728\u5236\u5b9a\u65b9\u6848",
                        "\u5236\u5b9a\u5f00\u53d1\u8ba1\u5212\u3001\u5206\u914d\u4efb\u52a1", null)
        );

        Map<String, Object> result = invoke(tool, map("tasks", tasks));

        assertTrue(String.valueOf(result.get("message")).contains("Successfully created 3 task(s)"));
        assertEquals(3, store.todos.size());
        assertTrue(store.todos.get(0).getDescription().contains("\u76ee\u6807\u3001\u7528\u6237\u9700\u6c42"));
        assertTrue(store.todos.get(2).getDescription().contains("\u5f00\u53d1\u8ba1\u5212\u3001\u5206\u914d\u4efb\u52a1"));
    }

    private void invokeCreateEmptyTasks() {
        MemoryTodoStore store = new MemoryTodoStore(List.of());
        TodoTools.TodoCreateTool tool = new TodoTools.TodoCreateTool(store);

        assertThrows(ValidationError.class, () -> invoke(tool, map("tasks", List.of())));
    }

    private void invokeMissingTasksParam() {
        MemoryTodoStore store = new MemoryTodoStore(List.of());
        TodoTools.TodoCreateTool tool = new TodoTools.TodoCreateTool(store);

        assertThrows(Exception.class, () -> invoke(tool, map()));
    }

    private void invokeCreateInvalidJsonString() {
        MemoryTodoStore store = new MemoryTodoStore(List.of());
        TodoTools.TodoCreateTool tool = new TodoTools.TodoCreateTool(store);

        assertThrows(Exception.class, () -> invoke(tool, map("tasks", "not a valid json array")));
    }

    private void invokeCreateMissingRequiredField() {
        MemoryTodoStore store = new MemoryTodoStore(List.of());
        TodoTools.TodoCreateTool tool = new TodoTools.TodoCreateTool(store);

        assertThrows(Exception.class, () -> invoke(tool, map("tasks", List.of(map("activeForm", "Doing")))));
        assertThrows(Exception.class, () -> invoke(tool, map("tasks", List.of(map("content", "Task 1")))));
        assertThrows(Exception.class, () -> invoke(tool, map("tasks", List.of(
                map("content", "Task 1", "activeForm", "Doing")))));
    }

    private void invokeListSuccess() throws Exception {
        MemoryTodoStore store = new MemoryTodoStore(List.of(
                todo("in_progress_task", "In Progress Task", TodoStatus.IN_PROGRESS),
                todo("pending_task", "Pending Task", TodoStatus.PENDING),
                todo("completed_task", "Completed Task", TodoStatus.COMPLETED),
                todo("cancelled_task", "Cancelled Task", TodoStatus.CANCELLED)
        ));
        TodoTools.TodoListTool tool = new TodoTools.TodoListTool(store);

        Map<String, Object> result = invoke(tool, map());

        List<Map<String, Object>> tasks = castListOfMaps(result.get("tasks"));
        assertEquals(2, tasks.size());
        List<String> contents = tasks.stream().map(task -> String.valueOf(task.get("content"))).toList();
        assertTrue(contents.contains("In Progress Task"));
        assertTrue(contents.contains("Pending Task"));
        assertTrue(tasks.get(0).containsKey("id"));
        assertTrue(tasks.get(0).containsKey("depends_on"));
    }

    private void invokeDeleteSuccess() throws Exception {
        MemoryTodoStore store = new MemoryTodoStore(testTodos());
        TodoTools.TodoModifyTool tool = new TodoTools.TodoModifyTool(store);

        Map<String, Object> result = invoke(tool, map("action", "delete", "ids", List.of("task_2")));

        assertTrue(String.valueOf(result.get("message")).contains("Successfully deleted 1 task(s)"));
        assertEquals(2, store.todos.size());
        assertEquals("Task 1", store.todos.get(0).getContent());
        assertEquals("Task 3", store.todos.get(1).getContent());
    }

    private void invokeDeleteNonexistent() throws Exception {
        MemoryTodoStore store = new MemoryTodoStore(testTodos());
        TodoTools.TodoModifyTool tool = new TodoTools.TodoModifyTool(store);

        Map<String, Object> result = invoke(tool, map("action", "delete", "ids", List.of("nonexistent_id")));

        assertTrue(String.valueOf(result.get("message")).contains("No tasks deleted"));
    }

    private void invokeUpdateSuccess() throws Exception {
        MemoryTodoStore store = new MemoryTodoStore(testTodos());
        TodoTools.TodoModifyTool tool = new TodoTools.TodoModifyTool(store);

        Map<String, Object> result = invoke(tool, map("action", "update", "todos", List.of(
                map("id", "task_1", "content", "Updated Task 1",
                        "activeForm", "Executing Updated Task 1", "status", "completed"))));

        assertTrue(String.valueOf(result.get("message")).contains("Successfully updated 1 task(s)"));
        TodoItem updated = find(store.todos, "task_1");
        assertEquals("Updated Task 1", updated.getContent());
        assertEquals(TodoStatus.COMPLETED, updated.getStatus());
    }

    private void invokeUpdatePartialFieldsSuccess() throws Exception {
        MemoryTodoStore store = new MemoryTodoStore(testTodos());
        TodoTools.TodoModifyTool tool = new TodoTools.TodoModifyTool(store);

        Map<String, Object> result = invoke(tool, map("action", "update", "todos", List.of(
                map("id", "task_1", "status", "completed"))));

        assertTrue(String.valueOf(result.get("message")).contains("Successfully updated 1 task(s)"));
        TodoItem updated = find(store.todos, "task_1");
        assertEquals(TodoStatus.COMPLETED, updated.getStatus());
        assertEquals("Task 1", updated.getContent());
        assertEquals("Executing Task 1", updated.getActiveForm());
    }

    private void invokeUpdateSelectedModelId() throws Exception {
        MemoryTodoStore store = new MemoryTodoStore(testTodos());
        TodoTools.TodoModifyTool tool = new TodoTools.TodoModifyTool(store);

        Map<String, Object> result = invoke(tool, map("action", "update", "todos", List.of(
                map("id", "task_1", "selected_model_id", "smart", "status", "pending"))));

        assertTrue(String.valueOf(result.get("message")).contains("Successfully updated 1 task(s)"));
        assertEquals("smart", find(store.todos, "task_1").getSelectedModelId());
    }

    private void invokeAppendSuccess() throws Exception {
        MemoryTodoStore store = new MemoryTodoStore(testTodos());
        TodoTools.TodoModifyTool tool = new TodoTools.TodoModifyTool(store);

        Map<String, Object> result = invoke(tool, map("action", "append", "todos", List.of(
                todoData("task_4", "New Task 4", "Executing New Task 4", "description of New Task 4", "pending"))));

        assertTrue(String.valueOf(result.get("message")).contains("Successfully appended 1 task(s)"));
        assertEquals(4, store.todos.size());
        assertEquals("task_4", store.todos.get(store.todos.size() - 1).getId());
    }

    private void invokeInsertAfterSuccess() throws Exception {
        MemoryTodoStore store = new MemoryTodoStore(testTodos());
        TodoTools.TodoModifyTool tool = new TodoTools.TodoModifyTool(store);

        Map<String, Object> result = invoke(tool, map("action", "insert_after", "todo_data", map(
                "target_id", "task_1",
                "items", List.of(todoData("task_4", "Inserted Task", "Executing Inserted Task",
                        "description of New Task 4", "pending")))));

        assertTrue(String.valueOf(result.get("message")).contains("Successfully inserted 1 task(s) after target task"));
        assertEquals(4, store.todos.size());
        assertEquals("task_4", store.todos.get(1).getId());
    }

    private void invokeInsertBeforeSuccess() throws Exception {
        MemoryTodoStore store = new MemoryTodoStore(testTodos());
        TodoTools.TodoModifyTool tool = new TodoTools.TodoModifyTool(store);

        Map<String, Object> result = invoke(tool, map("action", "insert_before", "todo_data", map(
                "target_id", "task_2",
                "items", List.of(todoData("task_4", "Inserted Before Task", "Executing",
                        "description of New Task 4", "pending")))));

        assertTrue(String.valueOf(result.get("message")).contains("Successfully inserted 1 task(s) before target task"));
        assertEquals(4, store.todos.size());
        assertEquals("task_4", store.todos.get(1).getId());
    }

    private void invokeCancelSuccess() throws Exception {
        MemoryTodoStore store = new MemoryTodoStore(testTodos());
        TodoTools.TodoModifyTool tool = new TodoTools.TodoModifyTool(store);

        Map<String, Object> result = invoke(tool, map("action", "cancel", "ids", List.of("task_2")));

        assertTrue(String.valueOf(result.get("message")).contains("Successfully cancelled 1 task(s)"));
        assertEquals(TodoStatus.CANCELLED, find(store.todos, "task_2").getStatus());
    }

    private void invokeCancelNonexistent() throws Exception {
        MemoryTodoStore store = new MemoryTodoStore(testTodos());
        TodoTools.TodoModifyTool tool = new TodoTools.TodoModifyTool(store);

        Map<String, Object> result = invoke(tool, map("action", "cancel", "ids", List.of("nonexistent_id")));

        assertTrue(String.valueOf(result.get("message")).contains("No tasks cancelled"));
    }

    private void invokeGetSuccess() throws Exception {
        MemoryTodoStore store = new MemoryTodoStore(List.of(
                new TodoItem("task_1", "Task 1", "Executing Task 1", "Detailed description for Task 1",
                        TodoStatus.IN_PROGRESS, List.of(), null, null, "smart"),
                new TodoItem("task_2", "Task 2", "Executing Task 2", "Detailed description for Task 2",
                        TodoStatus.PENDING, List.of(), null, null, null)
        ));
        TodoTools.TodoGetTool tool = new TodoTools.TodoGetTool(store);

        Map<String, Object> result = invoke(tool, map("id", "task_1"));

        Map<String, Object> todo = castMap(result.get("todo"));
        assertEquals("task_1", todo.get("id"));
        assertEquals("Task 1", todo.get("content"));
        assertEquals("in_progress", todo.get("status"));
        assertEquals("Detailed description for Task 1", todo.get("description"));
        assertEquals("smart", todo.get("selected_model_id"));
    }

    private void invokeGetNotFound() {
        MemoryTodoStore store = new MemoryTodoStore(testTodos());
        TodoTools.TodoGetTool tool = new TodoTools.TodoGetTool(store);

        assertThrows(FrameworkError.class, () -> invoke(tool, map("id", "nonexistent_id")));
    }

    private void invokeGetMissingId() {
        MemoryTodoStore store = new MemoryTodoStore(testTodos());
        TodoTools.TodoGetTool tool = new TodoTools.TodoGetTool(store);

        assertThrows(ValidationError.class, () -> invoke(tool, map()));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> invoke(Tool tool, Map<String, Object> inputs) throws Exception {
        Object result = tool.invoke(inputs, kwargs());
        assertInstanceOf(Map.class, result);
        return (Map<String, Object>) result;
    }

    private static Map<String, Object> kwargs() {
        return map("session_id", "test_session_id");
    }

    private static List<TodoItem> testTodos() {
        return List.of(
                new TodoItem("task_1", "Task 1", "Executing Task 1", "", TodoStatus.IN_PROGRESS,
                        List.of(), null, null, null),
                new TodoItem("task_2", "Task 2", "Executing Task 2", "", TodoStatus.PENDING,
                        List.of(), null, null, null),
                new TodoItem("task_3", "Task 3", "Executing Task 3", "", TodoStatus.PENDING,
                        List.of(), null, null, null)
        );
    }

    private static TodoItem todo(String id, String content, TodoStatus status) {
        return new TodoItem(id, content, "Executing " + content, "", status, List.of(), null, null, null);
    }

    private static Map<String, Object> task(
            String id,
            String content,
            String activeForm,
            String description,
            String selectedModelId
    ) {
        Map<String, Object> value = map(
                "id", id,
                "content", content,
                "activeForm", activeForm,
                "description", description
        );
        if (selectedModelId != null) {
            value.put("selected_model_id", selectedModelId);
        }
        return value;
    }

    private static Map<String, Object> todoData(
            String id,
            String content,
            String activeForm,
            String description,
            String status
    ) {
        return map(
                "id", id,
                "content", content,
                "activeForm", activeForm,
                "description", description,
                "status", status
        );
    }

    private static TodoItem find(List<TodoItem> todos, String taskId) {
        return todos.stream().filter(todo -> todo.getId().equals(taskId)).findFirst().orElseThrow();
    }

    private static Map<String, Object> map(Object... values) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int index = 0; index + 1 < values.length; index += 2) {
            map.put(String.valueOf(values[index]), values[index + 1]);
        }
        return map;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Object value) {
        assertInstanceOf(Map.class, value);
        return (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> castListOfMaps(Object value) {
        assertInstanceOf(List.class, value);
        return (List<Map<String, Object>>) value;
    }

    private static final class InspectableTodoTool extends TodoTools.TodoToolBase {
        private InspectableTodoTool(TodoTools.TodoStore store) {
            super("todo_test", "TodoTestTool", "Test todo persistence.", store);
        }

        private List<TodoItem> loadForTest(Map<String, Object> kwargs) {
            return load(kwargs);
        }

        private void saveForTest(List<TodoItem> todos, Map<String, Object> kwargs) {
            save(String.valueOf(kwargs.get("session_id")), todos, kwargs);
        }
    }

    private static final class MemoryTodoStore implements TodoTools.TodoStore {
        private List<TodoItem> todos;
        private RuntimeException loadFailure;
        private RuntimeException saveFailure;
        private int saveCount;

        private MemoryTodoStore(List<TodoItem> todos) {
            this.todos = new ArrayList<>(todos);
        }

        @Override
        public List<TodoItem> load(Map<String, Object> kwargs) {
            if (loadFailure != null) {
                throw loadFailure;
            }
            return new ArrayList<>(todos);
        }

        @Override
        public void save(List<TodoItem> todos, Map<String, Object> kwargs) {
            if (saveFailure != null) {
                throw saveFailure;
            }
            this.todos = new ArrayList<>(todos);
            saveCount++;
        }
    }
}

/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools;

import com.openjiuwen.core.common.exception.FrameworkError;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.common.exception.ValidationError;
import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.session.SessionContextHolder;
import com.openjiuwen.harness.schema.task.TodoItem;
import com.openjiuwen.harness.schema.task.TodoStatus;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Todo tool set and session-state adapter.
 *
 * <p>Mirrors Python's {@code TodoLockManager}, {@code TodoTool},
 * {@code TodoCreateTool}, {@code TodoListTool}, {@code TodoGetTool},
 * {@code TodoModifyTool}, and {@code create_todos_tool} in
 * {@code openjiuwen/harness/tools/todo.py}.</p>
 */
public final class TodoTools {

    public static final String TODO_STATE_KEY = "harness.todos";

    private TodoTools() {
    }

    public static List<Tool> createTodosTool(TodoStore store) {
        return List.of(
                new TodoCreateTool(store),
                new TodoListTool(store),
                new TodoGetTool(store),
                new TodoModifyTool(store)
        );
    }

    /**
     * Mirrors Python's todo session persistence in {@code openjiuwen/harness/tools/todo.py}.
     */
    public interface TodoStore {
        List<TodoItem> load(Map<String, Object> kwargs);

        void save(List<TodoItem> todos, Map<String, Object> kwargs);
    }

    /**
     * Shared todo-tool base for load/save/session validation.
     */
    abstract static class TodoToolBase extends AbstractHarnessTool {
        private final TodoStore store;

        TodoToolBase(String id, String name, String description, TodoStore store) {
            super(toolCard(id, name, description));
            this.store = store;
        }

        protected List<TodoItem> load(Map<String, Object> kwargs) {
            String sessionId = requireSessionId(kwargs);
            try {
                List<TodoItem> loaded = store == null ? List.of() : store.load(withSession(kwargs, sessionId));
                return copyTodos(loaded == null ? List.of() : loaded);
            } catch (RuntimeException exception) {
                throw frameworkError("todo tool loads failed: " + exception.getMessage(), exception);
            }
        }

        protected void save(String sessionId, List<TodoItem> todos, Map<String, Object> kwargs) {
            try {
                if (store != null) {
                    store.save(copyTodos(todos), withSession(kwargs, sessionId));
                }
            } catch (RuntimeException exception) {
                throw frameworkError("Failed to save todo list, because write_file fail", exception);
            }
        }

        protected String requireSessionId(Map<String, Object> kwargs) {
            String sessionId = sessionId(kwargs);
            if (sessionId == null || sessionId.isBlank()) {
                throw frameworkError("Session ID is required", null);
            }
            return sessionId;
        }

        protected TodoItem find(List<TodoItem> todos, String todoId) {
            for (TodoItem todo : todos) {
                if (todo.getId().equals(todoId)) {
                    return todo;
                }
            }
            throw frameworkError("Task with id '" + todoId + "' not found", null);
        }
    }

    /**
     * Mirrors Python's {@code TodoCreateTool} in {@code openjiuwen/harness/tools/todo.py}.
     */
    public static class TodoCreateTool extends TodoToolBase {
        public TodoCreateTool(TodoStore store) {
            super("todo_create", "TodoCreateTool", "Create todo items for the current session.", store);
        }

        @Override
        protected Object invokeInternal(Map<String, Object> inputs, Map<String, Object> kwargs) {
            String sessionId = requireSessionId(kwargs);
            Object tasksValue = inputs == null ? null : inputs.get("tasks");
            if (!(tasksValue instanceof List<?> rawTasks)) {
                throw frameworkError("'tasks' parameter is required and must be a JSON array", null);
            }
            List<Map<String, Object>> taskMaps = mapList(rawTasks, "tasks");
            if (taskMaps.isEmpty()) {
                throw validationError("Task list cannot be empty");
            }

            List<TodoItem> newTodos = new ArrayList<>();
            List<String> seenIds = new ArrayList<>();
            for (int index = 0; index < taskMaps.size(); index++) {
                Map<String, Object> taskData = taskMaps.get(index);
                String content = requiredTaskField(taskData, "content", index);
                String activeForm = requiredTaskField(taskData, "activeForm", index);
                String description = requiredTaskField(taskData, "description", index);
                String taskId = stringValue(taskData.get("id")).trim();
                if (taskId.isEmpty()) {
                    taskId = UUID.randomUUID().toString();
                }
                if (seenIds.contains(taskId)) {
                    throw validationError("Duplicate task id '" + taskId + "' at index " + index);
                }
                seenIds.add(taskId);
                newTodos.add(new TodoItem(
                        taskId,
                        content,
                        activeForm,
                        description,
                        index == 0 ? TodoStatus.IN_PROGRESS : TodoStatus.PENDING,
                        List.of(),
                        null,
                        null,
                        stringOrNull(taskData.get("selected_model_id"))
                ));
            }

            save(sessionId, newTodos, kwargs);
            return Map.of("message", formatCreateResult(newTodos));
        }

        private static String formatCreateResult(List<TodoItem> todos) {
            StringBuilder result = new StringBuilder("Successfully created ");
            result.append(todos.size()).append(" task(s):\n");
            for (TodoItem todo : todos) {
                String modelInfo = todo.getSelectedModelId() == null ? "" : " (model: " + todo.getSelectedModelId() + ")";
                result.append("  ")
                        .append(todo.getStatus().getStatusIcon())
                        .append(" task_id: ")
                        .append(todo.getId())
                        .append(" , content: ")
                        .append(todo.getContent())
                        .append(modelInfo)
                        .append("\n");
            }
            String firstTask = todos.isEmpty() ? "" : todos.get(0).getContent();
            result.append("\nNext step: Immediately execute task '").append(firstTask).append("'");
            return result.toString().strip();
        }
    }

    /**
     * Mirrors Python's {@code TodoListTool} in {@code openjiuwen/harness/tools/todo.py}.
     */
    public static class TodoListTool extends TodoToolBase {
        public TodoListTool(TodoStore store) {
            super("todo_list", "TodoListTool", "List active todo items.", store);
        }

        @Override
        protected Object invokeInternal(Map<String, Object> inputs, Map<String, Object> kwargs) {
            List<Map<String, Object>> activeTasks = new ArrayList<>();
            for (TodoItem todo : load(kwargs)) {
                if (todo.getStatus() == TodoStatus.CANCELLED || todo.getStatus() == TodoStatus.COMPLETED) {
                    continue;
                }
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", todo.getId());
                row.put("content", todo.getContent());
                row.put("status", todo.getStatus().getValue());
                row.put("depends_on", new ArrayList<>(todo.getDependsOn()));
                activeTasks.add(row);
            }
            return Map.of("tasks", activeTasks);
        }
    }

    /**
     * Mirrors Python's {@code TodoGetTool} in {@code openjiuwen/harness/tools/todo.py}.
     */
    public static class TodoGetTool extends TodoToolBase {
        public TodoGetTool(TodoStore store) {
            super("todo_get", "TodoGetTool", "Get one todo item.", store);
        }

        @Override
        protected Object invokeInternal(Map<String, Object> inputs, Map<String, Object> kwargs) {
            String taskId = stringValue(inputs == null ? null : inputs.get("id"));
            if (taskId.isEmpty()) {
                throw validationError("Task ID is required");
            }
            return Map.of("todo", find(load(kwargs), taskId).toMap());
        }
    }

    /**
     * Mirrors Python's {@code TodoModifyTool} in {@code openjiuwen/harness/tools/todo.py}.
     */
    public static class TodoModifyTool extends TodoToolBase {
        public TodoModifyTool(TodoStore store) {
            super("todo_modify", "TodoModifyTool", "Modify todo items for the current session.", store);
        }

        @Override
        protected Object invokeInternal(Map<String, Object> inputs, Map<String, Object> kwargs) {
            String sessionId = requireSessionId(kwargs);
            String action = stringValue(inputs == null ? null : inputs.get("action"));
            if (action.isEmpty()) {
                throw validationError("Invalid input: 'action' field is required");
            }
            List<TodoItem> currentTodos = load(kwargs);
            String message = switch (action) {
                case "delete" -> deleteTodos(sessionId, stringList(inputs.get("ids"), "delete"), currentTodos, kwargs);
                case "cancel" -> cancelTodos(sessionId, stringList(inputs.get("ids"), "cancel"), currentTodos, kwargs);
                case "update" -> updateTodos(sessionId, mapList(inputs.get("todos"), "todos"), currentTodos, kwargs);
                case "append" -> appendTodos(sessionId, mapList(inputs.get("todos"), "todos"), currentTodos, kwargs);
                case "insert_after" -> insertTodos(sessionId, objectMap(inputs.get("todo_data"), "todo_data"),
                        currentTodos, kwargs, true);
                case "insert_before" -> insertTodos(sessionId, objectMap(inputs.get("todo_data"), "todo_data"),
                        currentTodos, kwargs, false);
                default -> throw validationError("Invalid action: " + action);
            };
            return Map.of("message", message);
        }

        private String deleteTodos(
                String sessionId,
                List<String> ids,
                List<TodoItem> currentTodos,
                Map<String, Object> kwargs
        ) {
            int deletedCount = 0;
            List<TodoItem> remainingTodos = new ArrayList<>();
            for (TodoItem todo : currentTodos) {
                if (ids.contains(todo.getId())) {
                    deletedCount++;
                } else {
                    remainingTodos.add(todo);
                }
            }
            if (deletedCount == 0) {
                return "No tasks deleted: None of the provided IDs (" + String.join(", ", ids) + ") were found";
            }
            save(sessionId, remainingTodos, kwargs);
            return "Successfully deleted " + deletedCount + " task(s) (IDs: " + String.join(", ", ids) + ")";
        }

        private String cancelTodos(
                String sessionId,
                List<String> ids,
                List<TodoItem> currentTodos,
                Map<String, Object> kwargs
        ) {
            int cancelledCount = 0;
            List<String> cancelledIds = new ArrayList<>();
            for (TodoItem todo : currentTodos) {
                if (ids.contains(todo.getId())) {
                    todo.setStatus(TodoStatus.CANCELLED);
                    cancelledCount++;
                    cancelledIds.add(todo.getId());
                }
            }
            if (cancelledCount == 0) {
                return "No tasks cancelled: None of the provided IDs (" + String.join(", ", ids) + ") were found";
            }
            save(sessionId, currentTodos, kwargs);
            return "Successfully cancelled " + cancelledCount + " task(s) (IDs: " + String.join(", ", cancelledIds) + ")";
        }

        private String updateTodos(
                String sessionId,
                List<Map<String, Object>> todosData,
                List<TodoItem> currentTodos,
                Map<String, Object> kwargs
        ) {
            int updatedCount = 0;
            for (Map<String, Object> todoData : todosData) {
                String todoId = stringValue(todoData.get("id"));
                if (todoId.isEmpty()) {
                    throw validationError("Batch update failed: Missing required field: 'id'");
                }
                TodoItem currentTodo = find(currentTodos, todoId);
                if (todoData.containsKey("content")) {
                    currentTodo.setContent(stringValue(todoData.get("content")));
                }
                if (todoData.containsKey("activeForm")) {
                    currentTodo.setActiveForm(stringValue(todoData.get("activeForm")));
                }
                if (todoData.containsKey("description")) {
                    currentTodo.setDescription(stringValue(todoData.get("description")));
                }
                if (todoData.containsKey("status")) {
                    currentTodo.setStatus(parseStrictStatus(todoData.get("status")));
                }
                if (todoData.containsKey("selected_model_id")) {
                    currentTodo.setSelectedModelId(stringOrNull(todoData.get("selected_model_id")));
                }
                updatedCount++;
            }
            validateSingleInProgress(currentTodos);
            save(sessionId, currentTodos, kwargs);
            return "Successfully updated " + updatedCount + " task(s)";
        }

        private String appendTodos(
                String sessionId,
                List<Map<String, Object>> todosData,
                List<TodoItem> currentTodos,
                Map<String, Object> kwargs
        ) {
            List<String> existingIds = currentTodos.stream().map(TodoItem::getId).collect(ArrayList::new,
                    ArrayList::add, ArrayList::addAll);
            for (Map<String, Object> todoData : todosData) {
                validateSingleTodoItem(todoData);
                String todoId = stringValue(todoData.get("id"));
                if (existingIds.contains(todoId)) {
                    throw validationError("Batch append failed: Task with ID '" + todoId + "' is duplicated");
                }
                currentTodos.add(toTodoItem(todoData));
                existingIds.add(todoId);
            }
            validateSingleInProgress(currentTodos);
            save(sessionId, currentTodos, kwargs);
            return "Successfully appended " + todosData.size() + " task(s)";
        }

        private String insertTodos(
                String sessionId,
                Map<String, Object> todoData,
                List<TodoItem> currentTodos,
                Map<String, Object> kwargs,
                boolean after
        ) {
            String targetId = stringValue(todoData.get("target_id"));
            if (targetId.isEmpty()) {
                throw validationError("Invalid input: todo_data 'target_id' must be a non-empty string");
            }
            List<Map<String, Object>> insertTodosData = mapList(todoData.get("items"), "todo_data.items");
            if (insertTodosData.isEmpty()) {
                throw validationError("Invalid input: todo_data 'items' must be a non-empty list of todo objects");
            }
            int targetIndex = targetIndex(currentTodos, targetId, after);
            List<String> existingIds = currentTodos.stream().map(TodoItem::getId).collect(ArrayList::new,
                    ArrayList::add, ArrayList::addAll);
            List<TodoItem> insertTodos = new ArrayList<>();
            for (Map<String, Object> item : insertTodosData) {
                validateSingleTodoItem(item);
                String todoId = stringValue(item.get("id"));
                if (existingIds.contains(todoId)) {
                    throw validationError("Insert failed: Task with ID '" + todoId + "' already exists");
                }
                insertTodos.add(toTodoItem(item));
                existingIds.add(todoId);
            }
            List<TodoItem> updatedTodos = new ArrayList<>();
            updatedTodos.addAll(currentTodos.subList(0, after ? targetIndex + 1 : targetIndex));
            updatedTodos.addAll(insertTodos);
            updatedTodos.addAll(currentTodos.subList(after ? targetIndex + 1 : targetIndex, currentTodos.size()));
            validateSingleInProgress(updatedTodos);
            save(sessionId, updatedTodos, kwargs);
            return "Successfully inserted " + insertTodos.size() + " task(s) "
                    + (after ? "after" : "before") + " target task, id: '" + targetId + "'";
        }

        private int targetIndex(List<TodoItem> currentTodos, String targetId, boolean after) {
            for (int index = 0; index < currentTodos.size(); index++) {
                TodoItem todo = currentTodos.get(index);
                if (!todo.getId().equals(targetId)) {
                    continue;
                }
                if (after && todo.getStatus() != TodoStatus.IN_PROGRESS && todo.getStatus() != TodoStatus.PENDING) {
                    throw validationError("Target task status '" + todo.getStatus().getValue() + "' doesn't allow insertion.");
                }
                if (!after && todo.getStatus() != TodoStatus.PENDING) {
                    throw validationError("Target task status '" + todo.getStatus().getValue() + "' doesn't allow insertion.");
                }
                return index;
            }
            throw validationError("Target task with ID '" + targetId + "' not found in current todo list");
        }

        private static void validateSingleInProgress(List<TodoItem> todos) {
            long inProgressCount = todos.stream().filter(todo -> todo.getStatus() == TodoStatus.IN_PROGRESS).count();
            if (inProgressCount > 1) {
                throw validationError("More than one task is marked as 'in_progress' (only one allowed)");
            }
        }

        private static void validateSingleTodoItem(Map<String, Object> todoData) {
            List<String> missing = new ArrayList<>();
            for (String field : List.of("content", "activeForm", "description", "status", "id")) {
                if (!todoData.containsKey(field)) {
                    missing.add("Missing required field: '" + field + "'");
                }
            }
            if (!missing.isEmpty()) {
                throw validationError("Todo data validation error: " + String.join("; ", missing));
            }
            parseStrictStatus(todoData.get("status"));
        }

        private static TodoItem toTodoItem(Map<String, Object> todoData) {
            String todoId = stringValue(todoData.get("id")).trim();
            return new TodoItem(
                    todoId.isEmpty() ? UUID.randomUUID().toString() : todoId,
                    stringValue(todoData.get("content")),
                    stringValue(todoData.get("activeForm")),
                    stringValue(todoData.get("description")),
                    parseStrictStatus(todoData.get("status")),
                    List.of(),
                    null,
                    null,
                    stringOrNull(todoData.get("selected_model_id"))
            );
        }
    }

    /**
     * Resolve session id for todo operations.
     * <p>
     * Priority: thread-bound session ({@link SessionContextHolder}, set by
     * {@code LocalFunction} before tool lambdas) → explicit {@code session_id} /
     * nested session map → {@code "default"} last-resort fallback.
     * LLM tool calls typically omit optional {@code session_id}; without the
     * holder, todos collapse under a shared default key across conversations.
     */
    private static String sessionId(Map<String, Object> kwargs) {
        String fromHolder = SessionContextHolder.resolveSessionId(SessionContextHolder.getCurrentSession());
        if (fromHolder != null && !fromHolder.isBlank()) {
            return fromHolder;
        }
        if (kwargs == null) {
            return "default";
        }
        Object direct = kwargs.get("session_id");
        if (direct != null && !String.valueOf(direct).isBlank()) {
            return String.valueOf(direct);
        }
        Object session = kwargs.get("session");
        if (session instanceof Map<?, ?> sessionMap) {
            Object nested = sessionMap.get("session_id");
            if (nested != null && !String.valueOf(nested).isBlank()) {
                return String.valueOf(nested);
            }
        }
        String fromKwargsSession = SessionContextHolder.resolveSessionId(session);
        if (fromKwargsSession != null && !fromKwargsSession.isBlank()) {
            return fromKwargsSession;
        }
        return "default";
    }

    private static Map<String, Object> withSession(Map<String, Object> kwargs, String sessionId) {
        Map<String, Object> values = kwargs == null ? new LinkedHashMap<>() : new LinkedHashMap<>(kwargs);
        values.put("session_id", sessionId);
        return values;
    }

    private static String requiredTaskField(Map<String, Object> taskData, String field, int index) {
        String value = stringValue(taskData.get(field));
        if (value.isEmpty()) {
            throw validationError("Task at index " + index + " is missing a '" + field + "' field");
        }
        return value;
    }

    private static List<String> stringList(Object value, String action) {
        if (!(value instanceof List<?> rawList) || rawList.isEmpty()) {
            throw validationError("Invalid input for " + action + " action: 'ids' must be a non-empty list of task IDs");
        }
        List<String> values = new ArrayList<>();
        for (Object item : rawList) {
            if (!(item instanceof String text) || text.isEmpty()) {
                throw validationError("Invalid input for " + action + " action: 'ids' must be a non-empty list of task IDs");
            }
            values.add(text);
        }
        return values;
    }

    private static List<Map<String, Object>> mapList(Object value, String fieldName) {
        if (!(value instanceof List<?> rawList)) {
            throw validationError("Invalid input: '" + fieldName + "' must be a list");
        }
        return mapList(rawList, fieldName);
    }

    private static List<Map<String, Object>> mapList(List<?> rawList, String fieldName) {
        List<Map<String, Object>> values = new ArrayList<>();
        for (Object item : rawList) {
            values.add(objectMap(item, fieldName));
        }
        return values;
    }

    private static Map<String, Object> objectMap(Object value, String fieldName) {
        if (!(value instanceof Map<?, ?> rawMap)) {
            throw validationError("Invalid input: '" + fieldName + "' must be an object");
        }
        Map<String, Object> values = new LinkedHashMap<>();
        rawMap.forEach((key, mapValue) -> values.put(String.valueOf(key), mapValue));
        return values;
    }

    private static TodoStatus parseStrictStatus(Object value) {
        String text = stringValue(value);
        for (TodoStatus status : TodoStatus.values()) {
            if (status.getValue().equals(text)) {
                return status;
            }
        }
        throw validationError("Invalid status '" + text + "'. Valid values: "
                + List.of("pending", "in_progress", "completed", "cancelled"));
    }

    private static List<TodoItem> copyTodos(List<TodoItem> todos) {
        List<TodoItem> copies = new ArrayList<>();
        for (TodoItem todo : todos) {
            copies.add(copyTodo(todo));
        }
        return copies;
    }

    private static TodoItem copyTodo(TodoItem todo) {
        return new TodoItem(
                todo.getId(),
                todo.getContent(),
                todo.getActiveForm(),
                todo.getDescription(),
                todo.getStatus(),
                todo.getDependsOn(),
                todo.getResultSummary(),
                todo.getMetaData(),
                todo.getSelectedModelId()
        );
    }

    private static String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static String stringOrNull(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static FrameworkError frameworkError(String reason, Throwable cause) {
        return new FrameworkError(StatusCode.TOOL_EXECUTION_ERROR, reason, null, cause, Map.of(
                "card", "todo",
                "reason", reason == null ? "" : reason
        ));
    }

    private static ValidationError validationError(String reason) {
        return new ValidationError(StatusCode.TOOL_EXECUTION_ERROR, reason, null, null, Map.of(
                "card", "todo",
                "reason", reason == null ? "" : reason
        ));
    }
}

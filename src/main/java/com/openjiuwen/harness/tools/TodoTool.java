/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Public class TodoTool used by the Java parity implementation.
 * 
 * @since 0.1.7
 */
public class TodoTool {
    private final TodoStorage storage;

    public TodoTool(String workspace) {
        this.storage = new FileTodoStorage(Path.of(workspace != null ? workspace : "."));
    }

    public TodoTool(TodoStorage storage) {
        this.storage = Objects.requireNonNull(storage);
    }

    /**
     * fromConfig.
     *
     * @param storageType storageType
     * @param conf conf
     * @return the result
     * @since 0.1.7
     */
    public static TodoTool fromConfig(String storageType, Map<String, Object> conf) {
        if (TodoStorageFactory.hasProvider(storageType)) {
            return new TodoTool(TodoStorageFactory.create(storageType, conf));
        }
        String basePath = ".";
        if (conf != null) {
            Object raw = conf.getOrDefault("basePath", ".");
            if (raw instanceof String s) {
                basePath = s;
            }
        }
        return new TodoTool(basePath);
    }

    /**
     * create.
     * 
     * @param sessionId sessionId
     * @param tasks tasks
     * @return the result
     * @since 0.1.7
     */
    public ToolOutput create(String sessionId, List<?> tasks) {
        try {
            if (tasks == null || tasks.isEmpty()) {
                return ToolOutput.builder().success(false).error("Task list cannot be empty").build();
            }
            List<TodoItem> todos = new ArrayList<>();
            for (int i = 0; i < tasks.size(); i++) {
                todos.add(todoItemFrom(tasks.get(i), i));
            }
            // task-create represents a fresh task plan, matching Python's re-plan semantics.
            save(sessionId, todos);
            return ToolOutput.builder().success(true).data(todos).build();
        } catch (RuntimeException | IOException e) {
            return ToolOutput.builder().success(false).error(e.getMessage()).build();
        }
    }

    /**
     * load.
     *
     * @param sessionId sessionId
     * @return the result
     * @throws IOException IOException
     * @since 0.1.7
     */
    public List<TodoItem> load(String sessionId) throws IOException {
        return storage.load(sessionId);
    }

    /**
     * save.
     *
     * @param sessionId sessionId
     * @param todos todos
     * @throws IOException IOException
     * @since 0.1.7
     */
    public void save(String sessionId, List<TodoItem> todos) throws IOException {
        storage.save(sessionId, todos);
    }

    /**
     * list.
     * 
     * @param sessionId sessionId
     * @return the result
     * @since 0.1.7
     */
    public ToolOutput list(String sessionId) {
        try {
            List<TodoItem> active =
                load(sessionId).stream().filter(item -> item != null && !item.isTerminal()).toList();
            return ToolOutput.builder().success(true).data(active).build();
        } catch (RuntimeException | IOException e) {
            return ToolOutput.builder().success(false).error(e.getMessage()).build();
        }
    }

    /**
     * get.
     * 
     * @param sessionId sessionId
     * @return the result
     * @since 0.1.7
     */
    public ToolOutput get(String sessionId) {
        try {
            return ToolOutput.builder().success(true).data(load(sessionId)).build();
        } catch (RuntimeException | IOException e) {
            return ToolOutput.builder().success(false).error(e.getMessage()).build();
        }
    }

    /**
     * get.
     * 
     * @param sessionId sessionId
     * @param id id
     * @return the result
     * @since 0.1.7
     */
    public ToolOutput get(String sessionId, String id) {
        if (id == null || id.isBlank()) {
            return get(sessionId);
        }
        try {
            TodoItem item = findTodo(load(sessionId), id);
            if (item == null) {
                return ToolOutput.builder().success(false).error("Task with id '" + id + "' not found").build();
            }
            return ToolOutput.builder().success(true).data(item).build();
        } catch (RuntimeException | IOException e) {
            return ToolOutput.builder().success(false).error(e.getMessage()).build();
        }
    }

    /**
     * modify.
     * 
     * @param sessionId sessionId
     * @param updates updates
     * @return the result
     * @since 0.1.7
     */
    public ToolOutput modify(String sessionId, List<Map<String, Object>> updates) {
        try {
            List<TodoItem> todos = load(sessionId);
            applyUpdates(todos, updates);
            save(sessionId, todos);
            return ToolOutput.builder().success(true).data(todos).build();
        } catch (RuntimeException | IOException e) {
            return ToolOutput.builder().success(false).error(e.getMessage()).build();
        }
    }

    /**
     * modify.
     * 
     * @param sessionId sessionId
     * @param inputs inputs
     * @return the result
     * @since 0.1.7
     */
    public ToolOutput modify(String sessionId, Map<String, Object> inputs) {
        try {
            List<TodoItem> todos = load(sessionId);
            String action = string(inputs != null ? inputs.get("action") : null);
            if (action.isBlank() && inputs != null && inputs.containsKey("updates")) {
                applyUpdates(todos, mapList(inputs.get("updates")));
            } else {
                applyAction(todos, inputs == null ? Map.of() : inputs, action);
            }
            save(sessionId, todos);
            return ToolOutput.builder().success(true).data(todos).build();
        } catch (RuntimeException | IOException e) {
            return ToolOutput.builder().success(false).error(e.getMessage()).build();
        }
    }

    /**
     * applyAction.
     * 
     * @param todos todos
     * @param inputs inputs
     * @param action action
     * @since 0.1.7
     */
    private static void applyAction(List<TodoItem> todos, Map<String, Object> inputs, String action) {
        switch (action) {
            case "delete" -> deleteTodos(todos, stringList(inputs.get("ids")));
            case "cancel" -> cancelTodos(todos, stringList(inputs.get("ids")));
            case "update" -> applyUpdates(todos, resolveUpdates(inputs));
            case "append" -> appendTodos(todos, mapList(inputs.get("todos")));
            case "insert_after" -> insertTodos(todos, mapValue(inputs.get("todo_data")), true);
            case "insert_before" -> insertTodos(todos, mapValue(inputs.get("todo_data")), false);
            default -> throw new IllegalArgumentException("Invalid action: " + action);
        }
        validateSingleInProgress(todos);
    }

    /**
     * Resolves the update action payload while preserving the primary {@code todos} field precedence.
     *
     * @param inputs action inputs
     * @return todo item updates
     * @since 0.1.7
     */
    private static List<Map<String, Object>> resolveUpdates(Map<String, Object> inputs) {
        if (inputs.containsKey("todos")) {
            return mapList(inputs.get("todos"));
        }
        return mapList(inputs.get("updates"));
    }

    /**
     * applyUpdates.
     * 
     * @param todos todos
     * @param updates updates
     * @since 0.1.7
     */
    private static void applyUpdates(List<TodoItem> todos, List<Map<String, Object>> updates) {
        requireTodoMaps(updates, "update");
        for (Map<String, Object> update : updates) {
            if (update == null) {
                continue;
            }
            String taskId = string(update.getOrDefault("task_id", update.get("id")));
            TodoItem item = findTodo(todos, taskId);
            if (item == null) {
                throw new IllegalArgumentException("todo item not found: " + taskId);
            }
            applyFields(item, update);
        }
        validateSingleInProgress(todos);
    }

    /**
     * applyFields.
     * 
     * @param item item
     * @param update update
     * @since 0.1.7
     */
    private static void applyFields(TodoItem item, Map<String, Object> update) {
        if (update.containsKey("content")) {
            item.setContent(string(update.get("content")));
        }
        if (update.containsKey("activeForm")) {
            item.setActiveForm(string(update.get("activeForm")));
        }
        if (update.containsKey("active_form")) {
            item.setActiveForm(string(update.get("active_form")));
        }
        if (update.containsKey("description")) {
            item.setDescription(string(update.get("description")));
        }
        if (update.containsKey("status")) {
            item.setStatus(parseStatus(update.get("status")));
        }
        if (update.containsKey("priority")) {
            item.setPriority(parsePriority(update.get("priority")));
        }
        if (update.containsKey("selected_model_id")) {
            item.setSelectedModelId(string(update.get("selected_model_id")));
        }
        if (update.containsKey("depends_on")) {
            item.setDependsOn(stringList(update.get("depends_on")));
        }
        if (update.containsKey("result_summary")) {
            item.setResultSummary(string(update.get("result_summary")));
        }
        if (update.containsKey("meta_data")) {
            item.setMetaData(mapValue(update.get("meta_data")));
        }
    }

    /**
     * deleteTodos.
     * 
     * @param todos todos
     * @param ids ids
     * @since 0.1.7
     */
    private static void deleteTodos(List<TodoItem> todos, List<String> ids) {
        requireIds(ids, "delete");
        todos.removeIf(todo -> todo != null && ids.contains(todo.getId()));
    }

    /**
     * cancelTodos.
     * 
     * @param todos todos
     * @param ids ids
     * @since 0.1.7
     */
    private static void cancelTodos(List<TodoItem> todos, List<String> ids) {
        requireIds(ids, "cancel");
        for (TodoItem todo : todos) {
            if (todo != null && ids.contains(todo.getId())) {
                todo.setStatus(TodoStatus.CANCELLED);
            }
        }
    }

    /**
     * appendTodos.
     * 
     * @param todos todos
     * @param todoMaps todoMaps
     * @since 0.1.7
     */
    private static void appendTodos(List<TodoItem> todos, List<Map<String, Object>> todoMaps) {
        requireTodoMaps(todoMaps, "append");
        for (Map<String, Object> todoMap : todoMaps) {
            TodoItem item = fullTodoItemFrom(todoMap);
            ensureUniqueId(todos, item.getId());
            todos.add(item);
        }
    }

    /**
     * insertTodos.
     * 
     * @param todos todos
     * @param todoData todoData
     * @param isAfter isAfter
     * @since 0.1.7
     */
    private static void insertTodos(List<TodoItem> todos, Map<String, Object> todoData, boolean isAfter) {
        String targetId = string(todoData.get("target_id"));
        if (targetId.isBlank()) {
            throw new IllegalArgumentException("Invalid input: todo_data 'target_id' must be a non-empty string");
        }
        List<Map<String, Object>> itemMaps = mapList(todoData.get("items"));
        requireTodoMaps(itemMaps, "insert");
        int targetIndex = indexOfTodo(todos, targetId);
        if (targetIndex < 0) {
            throw new IllegalArgumentException("Target task with ID '" + targetId + "' not found in current todo list");
        }
        TodoStatus targetStatus = todos.get(targetIndex).getStatus();
        if (isAfter) {
            if (targetStatus != TodoStatus.IN_PROGRESS && targetStatus != TodoStatus.PENDING) {
                throw new IllegalArgumentException(
                        "Target task status '" + targetStatus + "' doesn't allow insertion.");
            }
        } else if (targetStatus != TodoStatus.PENDING) {
            throw new IllegalArgumentException("Target task status '" + targetStatus + "' doesn't allow insertion.");
        } else {
            // insertion allowed for pending target when not inserting after
        }
        List<TodoItem> inserts = new ArrayList<>();
        for (Map<String, Object> itemMap : itemMaps) {
            TodoItem item = fullTodoItemFrom(itemMap);
            ensureUniqueId(todos, item.getId());
            ensureUniqueId(inserts, item.getId());
            inserts.add(item);
        }
        todos.addAll(isAfter ? targetIndex + 1 : targetIndex, inserts);
    }

    /**
     * fullTodoItemFrom.
     * 
     * @param task task
     * @return the result
     * @since 0.1.7
     */
    private static TodoItem fullTodoItemFrom(Map<String, Object> task) {
        validateFullTodoItem(task);
        TodoItem item = todoItemFrom(task, 1);
        item.setId(string(task.get("id")));
        item.setStatus(parseStatus(task.get("status")));
        return item;
    }

    /**
     * validateFullTodoItem.
     * 
     * @param task task
     * @since 0.1.7
     */
    private static void validateFullTodoItem(Map<String, Object> task) {
        for (String field : List.of("id", "content", "activeForm", "description", "status")) {
            if (!task.containsKey(field) || string(task.get(field)).isBlank()) {
                throw new IllegalArgumentException("Missing required field: '" + field + "'");
            }
        }
    }

    /**
     * validateSingleInProgress.
     * 
     * @param todos todos
     * @since 0.1.7
     */
    private static void validateSingleInProgress(List<TodoItem> todos) {
        long count = todos.stream().filter(todo -> todo != null && todo.getStatus() == TodoStatus.IN_PROGRESS).count();
        if (count > 1) {
            throw new IllegalArgumentException("More than one task is marked as 'in_progress' (only one allowed)");
        }
    }

    /**
     * requireIds.
     * 
     * @param ids ids
     * @param action action
     * @since 0.1.7
     */
    private static void requireIds(List<String> ids, String action) {
        if (ids == null || ids.isEmpty() || ids.stream().anyMatch(String::isBlank)) {
            throw new IllegalArgumentException(
                    "Invalid input for " + action + " action: 'ids' must be a non-empty list of task IDs");
        }
    }

    /**
     * requireTodoMaps.
     * 
     * @param todoMaps todoMaps
     * @param action action
     * @since 0.1.7
     */
    private static void requireTodoMaps(List<Map<String, Object>> todoMaps, String action) {
        if (todoMaps == null || todoMaps.isEmpty()) {
            throw new IllegalArgumentException(
                    "Invalid input for " + action + " action: todo items must be a non-empty list");
        }
    }

    /**
     * ensureUniqueId.
     * 
     * @param todos todos
     * @param id id
     * @since 0.1.7
     */
    private static void ensureUniqueId(List<TodoItem> todos, String id) {
        if (todos.stream().anyMatch(todo -> todo != null && Objects.equals(todo.getId(), id))) {
            throw new IllegalArgumentException("Task with ID '" + id + "' is duplicated");
        }
    }

    /**
     * findTodo.
     * 
     * @param todos todos
     * @param taskId taskId
     * @return the result
     * @since 0.1.7
     */
    private static TodoItem findTodo(List<TodoItem> todos, String taskId) {
        return todos.stream().filter(todo -> Objects.equals(todo.getId(), taskId)).findFirst().orElse(null);
    }

    /**
     * indexOfTodo.
     * 
     * @param todos todos
     * @param taskId taskId
     * @return the result
     * @since 0.1.7
     */
    private static int indexOfTodo(List<TodoItem> todos, String taskId) {
        for (int i = 0; i < todos.size(); i++) {
            if (Objects.equals(todos.get(i).getId(), taskId)) {
                return i;
            }
        }
        return -1;
    }

    @SuppressWarnings("unchecked")
    /**
     * todoItemFrom.
     * 
     * @param raw raw
     * @param index index
     * @return the result
     * @since 0.1.7
     */
    private static TodoItem todoItemFrom(Object raw, int index) {
        TodoStatus status = index == 0 ? TodoStatus.IN_PROGRESS : TodoStatus.PENDING;
        if (raw instanceof Map<?, ?> map) {
            Map<String, Object> task = (Map<String, Object>) map;
            String content = string(task.get("content"));
            if (content.isBlank()) {
                throw new IllegalArgumentException("Task at index " + index + " is missing a 'content' field");
            }
            String activeForm = string(task.get("activeForm"));
            if (activeForm.isBlank()) {
                activeForm = string(task.get("active_form"));
            }
            if (activeForm.isBlank()) {
                throw new IllegalArgumentException("Task at index " + index + " is missing a 'activeForm' field");
            }
            String description = string(task.get("description"));
            if (description.isBlank()) {
                throw new IllegalArgumentException("Task at index " + index + " is missing a 'description' field");
            }
            return TodoItem.builder().id(nonBlank(task.get("id"), UUID.randomUUID().toString())).content(content)
                    .activeForm(activeForm).description(description).status(status)
                    .dependsOn(stringList(task.get("depends_on")))
                    .resultSummary(blankToNull(task.get("result_summary"))).metaData(mapValue(task.get("meta_data")))
                    .selectedModelId(blankToNull(task.get("selected_model_id")))
                    .priority(blankToNull(task.get("priority"))).build();
        }
        String content = string(raw);
        if (content.isBlank()) {
            throw new IllegalArgumentException("Task at index " + index + " is missing a 'content' field");
        }
        return TodoItem.create(content, "Executing " + content, "", status, null);
    }

    /**
     * parseStatus.
     * 
     * @param value value
     * @return the result
     * @since 0.1.7
     */
    private static TodoStatus parseStatus(Object value) {
        String raw = string(value).trim().toLowerCase(Locale.ROOT);
        return switch (raw) {
            case "todo" -> TodoStatus.TODO;
            case "pending" -> TodoStatus.PENDING;
            case "in_progress", "in-progress", "doing" -> TodoStatus.IN_PROGRESS;
            case "done" -> TodoStatus.DONE;
            case "completed", "complete" -> TodoStatus.COMPLETED;
            case "cancelled", "canceled" -> TodoStatus.CANCELLED;
            default -> throw new IllegalArgumentException("unsupported todo status: " + raw);
        };
    }

    /**
     * parsePriority.
     * 
     * @param value value
     * @return the result
     * @since 0.1.7
     */
    private static String parsePriority(Object value) {
        String raw = string(value).trim().toLowerCase(Locale.ROOT);
        return switch (raw) {
            case "", "low", "medium", "high" -> raw;
            default -> throw new IllegalArgumentException("unsupported todo priority: " + raw);
        };
    }

    /**
     * nonBlank.
     * 
     * @param value value
     * @param fallback fallback
     * @return the result
     * @since 0.1.7
     */
    private static String nonBlank(Object value, String fallback) {
        String text = string(value);
        return text.isBlank() ? fallback : text;
    }

    /**
     * blankToNull.
     * 
     * @param value value
     * @return the result
     * @since 0.1.7
     */
    private static String blankToNull(Object value) {
        String text = string(value);
        return text.isBlank() ? null : text;
    }

    /**
     * stringList.
     * 
     * @param value value
     * @return the result
     * @since 0.1.7
     */
    private static List<String> stringList(Object value) {
        if (value instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        if (value == null) {
            return new ArrayList<>();
        }
        return List.of(String.valueOf(value));
    }

    @SuppressWarnings("unchecked")
    /**
     * mapValue.
     * 
     * @param value value
     * @return the result
     * @since 0.1.7
     */
    private static Map<String, Object> mapValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            return new java.util.LinkedHashMap<>((Map<String, Object>) map);
        }
        return new java.util.LinkedHashMap<>();
    }

    @SuppressWarnings("unchecked")
    /**
     * mapList.
     * 
     * @param value value
     * @return the result
     * @since 0.1.7
     */
    private static List<Map<String, Object>> mapList(Object value) {
        if (value instanceof List<?> list) {
            List<Map<String, Object>> result = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    result.add((Map<String, Object>) map);
                }
            }
            return result;
        }
        if (value instanceof Map<?, ?> map) {
            return List.of((Map<String, Object>) map);
        }
        return List.of();
    }

    /**
     * string.
     * 
     * @param value value
     * @return the result
     * @since 0.1.7
     */
    private static String string(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}

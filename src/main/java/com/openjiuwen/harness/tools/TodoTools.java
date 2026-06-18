/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools;

import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.harness.schema.task.TodoItem;
import com.openjiuwen.harness.schema.task.TodoStatus;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Todo tool set and in-memory state adapter.
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
     * Mirrors Python's todo session state access in {@code openjiuwen/harness/tools/todo.py}.
     */
    public interface TodoStore {
        List<TodoItem> load(Map<String, Object> kwargs);

        void save(List<TodoItem> todos, Map<String, Object> kwargs);
    }

    private abstract static class TodoToolBase extends AbstractHarnessTool {
        private final TodoStore store;

        TodoToolBase(String id, String name, String description, TodoStore store) {
            super(toolCard(id, name, description));
            this.store = store;
        }

        protected List<TodoItem> load(Map<String, Object> kwargs) {
            return store == null ? new ArrayList<>() : new ArrayList<>(store.load(kwargs == null ? Map.of() : kwargs));
        }

        protected void save(List<TodoItem> todos, Map<String, Object> kwargs) {
            if (store != null) {
                store.save(todos, kwargs == null ? Map.of() : kwargs);
            }
        }

        protected TodoItem find(List<TodoItem> todos, String todoId) {
            return todos.stream()
                    .filter(todo -> todo.getId().equals(todoId))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("todo not found: " + todoId));
        }
    }

    /**
     * Mirrors Python's {@code TodoCreateTool} in {@code openjiuwen/harness/tools/todo.py}.
     */
    public static class TodoCreateTool extends TodoToolBase {
        public TodoCreateTool(TodoStore store) {
            super("todo_create", "TodoCreateTool", "Create a todo item.", store);
        }

        @Override
        protected Object invokeInternal(Map<String, Object> inputs, Map<String, Object> kwargs) {
            List<TodoItem> todos = load(kwargs);
            TodoItem item = TodoItem.create(
                    requiredString(inputs, "content"),
                    stringValue(inputs == null ? null : inputs.get("active_form")),
                    stringValue(inputs == null ? null : inputs.get("description")),
                    TodoStatus.fromValue(stringValue(inputs == null ? null : inputs.get("status"))),
                    stringValue(inputs == null ? null : inputs.get("selected_model_id"))
            );
            todos.add(item);
            save(todos, kwargs);
            return ToolOutput.success(Map.of("todo", item.toMap()));
        }
    }

    /**
     * Mirrors Python's {@code TodoListTool} in {@code openjiuwen/harness/tools/todo.py}.
     */
    public static class TodoListTool extends TodoToolBase {
        public TodoListTool(TodoStore store) {
            super("todo_list", "TodoListTool", "List todo items.", store);
        }

        @Override
        protected Object invokeInternal(Map<String, Object> inputs, Map<String, Object> kwargs) {
            List<Map<String, Object>> rows = load(kwargs).stream()
                    .sorted(Comparator.comparing(TodoItem::getContent))
                    .map(TodoItem::toMap)
                    .toList();
            return ToolOutput.success(Map.of("todos", rows));
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
            return ToolOutput.success(Map.of("todo", find(load(kwargs), requiredString(inputs, "id")).toMap()));
        }
    }

    /**
     * Mirrors Python's {@code TodoModifyTool} in {@code openjiuwen/harness/tools/todo.py}.
     */
    public static class TodoModifyTool extends TodoToolBase {
        public TodoModifyTool(TodoStore store) {
            super("todo_modify", "TodoModifyTool", "Modify a todo item.", store);
        }

        @Override
        protected Object invokeInternal(Map<String, Object> inputs, Map<String, Object> kwargs) {
            List<TodoItem> todos = load(kwargs);
            TodoItem item = find(todos, requiredString(inputs, "id"));
            if (inputs != null && inputs.containsKey("content")) {
                item.setContent(stringValue(inputs.get("content")));
            }
            if (inputs != null && inputs.containsKey("active_form")) {
                item.setActiveForm(stringValue(inputs.get("active_form")));
            }
            if (inputs != null && inputs.containsKey("description")) {
                item.setDescription(stringValue(inputs.get("description")));
            }
            if (inputs != null && inputs.containsKey("status")) {
                item.setStatus(TodoStatus.fromValue(stringValue(inputs.get("status"))));
            }
            if (inputs != null && inputs.containsKey("result_summary")) {
                item.setResultSummary(stringValue(inputs.get("result_summary")));
            }
            if (inputs != null && inputs.get("meta_data") instanceof Map<?, ?> meta) {
                Map<String, Object> values = new LinkedHashMap<>();
                meta.forEach((key, value) -> values.put(String.valueOf(key), value));
                item.setMetaData(values);
            }
            save(todos, kwargs);
            return ToolOutput.success(Map.of("todo", item.toMap()));
        }
    }
}

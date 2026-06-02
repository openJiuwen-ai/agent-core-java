package com.openjiuwen.harness.tools;

import com.openjiuwen.core.session.Session;
import com.openjiuwen.core.sysop.SysOperation;
import com.openjiuwen.harness.schema.task.TodoItem;
import com.openjiuwen.harness.schema.task.TodoStatus;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Mirrors Python's {@code TodoModifyTool} in {@code openjiuwen.harness.tools.todo}.
 */
public class TodoModifyTool extends TodoTool {

    public TodoModifyTool(SysOperation sysOperation) {
        super("todo_modify", "todo_modify", "Modify todo tasks in session state.", sysOperation);
    }

    @Override
    public Object invoke(Map<String, Object> inputs, Map<String, Object> kwargs) {
        Session session = requireSession(kwargs);
        List<TodoItem> todos = loadTodos(session);
        String action = stringValue(inputs.get("action"));
        if (action.isBlank()) {
            throw new IllegalArgumentException("Invalid input: 'action' field is required");
        }

        String message;
        switch (action) {
            case "delete" -> message = deleteTodos(session, ids(inputs.get("ids")), todos);
            case "cancel" -> message = cancelTodos(session, ids(inputs.get("ids")), todos);
            case "update" -> message = updateTodos(session, todoMaps(inputs.get("todos"), "todos"), todos);
            case "append" -> message = appendTodos(session, todoMaps(inputs.get("todos"), "todos"), todos);
            case "insert_after", "insert_before" -> {
                Map<String, Object> data = todoData(inputs.get("todo_data"));
                String targetId = stringValue(data.get("target_id"));
                List<Map<String, Object>> items = todoMaps(data.get("items"), "todo_data.items");
                message = "insert_after".equals(action)
                        ? insertTodos(session, targetId, items, todos, true)
                        : insertTodos(session, targetId, items, todos, false);
            }
            default -> throw new IllegalArgumentException("Invalid action: " + action);
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("message", message);
        return data;
    }

    private String deleteTodos(Session session, List<String> ids, List<TodoItem> todos) {
        Set<String> deleteIds = new HashSet<>(ids);
        List<TodoItem> remaining = new ArrayList<>();
        int deletedCount = 0;
        for (TodoItem todo : todos) {
            if (deleteIds.contains(todo.getId())) {
                deletedCount++;
            } else {
                remaining.add(todo);
            }
        }
        if (deletedCount == 0) {
            return "No tasks deleted: None of the provided IDs (" + String.join(", ", ids) + ") were found";
        }
        saveTodos(session, remaining);
        return "Successfully deleted " + deletedCount + " task(s) (IDs: " + String.join(", ", deleteIds) + ")";
    }

    private String cancelTodos(Session session, List<String> ids, List<TodoItem> todos) {
        int cancelledCount = 0;
        List<String> cancelledIds = new ArrayList<>();
        for (TodoItem todo : todos) {
            if (ids.contains(todo.getId())) {
                todo.setStatus(TodoStatus.CANCELLED);
                cancelledCount++;
                cancelledIds.add(todo.getId());
            }
        }
        if (cancelledCount == 0) {
            return "No tasks cancelled: None of the provided IDs (" + String.join(", ", ids) + ") were found";
        }
        saveTodos(session, todos);
        return "Successfully cancelled " + cancelledCount + " task(s) (IDs: " + String.join(", ", cancelledIds) + ")";
    }

    private String updateTodos(Session session, List<Map<String, Object>> updates, List<TodoItem> todos) {
        int updatedCount = 0;
        for (Map<String, Object> update : updates) {
            String id = stringValue(update.get("id"));
            if (id.isBlank()) {
                throw new IllegalArgumentException("Batch update failed: Missing required field: 'id'");
            }
            TodoItem todo = findById(todos, id);
            if (todo == null) {
                throw new IllegalArgumentException("Batch update failed: Task with ID '" + id + "' not found");
            }
            applyPartialUpdate(todo, update);
            updatedCount++;
        }
        validateSingleInProgress(todos);
        saveTodos(session, todos);
        return "Successfully updated " + updatedCount + " task(s)";
    }

    private String appendTodos(Session session, List<Map<String, Object>> items, List<TodoItem> todos) {
        Set<String> existingIds = idsOf(todos);
        for (Map<String, Object> itemData : items) {
            validateSingleTodoItem(itemData);
            String id = stringValue(itemData.get("id"));
            if (existingIds.contains(id)) {
                throw new IllegalArgumentException("Batch append failed: Task with ID '" + id + "' is duplicated");
            }
            todos.add(toTodoItem(itemData));
            existingIds.add(id);
        }
        validateSingleInProgress(todos);
        saveTodos(session, todos);
        return "Successfully appended " + items.size() + " task(s)";
    }

    private String insertTodos(Session session, String targetId, List<Map<String, Object>> items,
                               List<TodoItem> todos, boolean after) {
        if (targetId.isBlank()) {
            throw new IllegalArgumentException("Invalid input: todo_data 'target_id' must be a non-empty string");
        }
        int targetIndex = findIndex(todos, targetId);
        if (targetIndex < 0) {
            throw new IllegalArgumentException("Target task with ID '" + targetId + "' not found in current todo list");
        }
        TodoStatus targetStatus = todos.get(targetIndex).getStatus();
        if (after) {
            if (targetStatus != TodoStatus.IN_PROGRESS && targetStatus != TodoStatus.PENDING) {
                throw new IllegalArgumentException("Target task status '" + targetStatus + "' doesn't allow insertion.");
            }
        } else if (targetStatus != TodoStatus.PENDING) {
            throw new IllegalArgumentException("Target task status '" + targetStatus + "' doesn't allow insertion.");
        }

        Set<String> existingIds = idsOf(todos);
        List<TodoItem> inserts = new ArrayList<>();
        for (Map<String, Object> itemData : items) {
            validateSingleTodoItem(itemData);
            String id = stringValue(itemData.get("id"));
            if (existingIds.contains(id)) {
                throw new IllegalArgumentException("Insert failed: Task with ID '" + id + "' already exists");
            }
            inserts.add(toTodoItem(itemData));
            existingIds.add(id);
        }
        List<TodoItem> updated = new ArrayList<>(todos);
        updated.addAll(after ? targetIndex + 1 : targetIndex, inserts);
        validateSingleInProgress(updated);
        saveTodos(session, updated);
        return "Successfully inserted " + inserts.size() + " task(s) "
                + (after ? "after" : "before") + " target task, id: '" + targetId + "'";
    }

    private void applyPartialUpdate(TodoItem todo, Map<String, Object> update) {
        if (update.containsKey("content")) {
            todo.setContent(String.valueOf(update.get("content")));
        }
        if (update.containsKey("activeForm")) {
            todo.setActiveForm(String.valueOf(update.get("activeForm")));
        }
        if (update.containsKey("description")) {
            todo.setDescription(String.valueOf(update.get("description")));
        }
        if (update.containsKey("status")) {
            todo.setStatus(parseStatus(update.get("status")));
        }
        if (update.containsKey("selected_model_id")) {
            todo.setSelectedModelId(update.get("selected_model_id") == null ? null : String.valueOf(update.get("selected_model_id")));
        }
    }

    private TodoItem toTodoItem(Map<String, Object> data) {
        return new TodoItem(
                stringValue(data.get("id")),
                stringValue(data.get("content")),
                stringValue(data.get("activeForm")),
                stringValue(data.get("description")),
                parseStatus(data.get("status")),
                List.of(),
                null,
                Map.of(),
                data.get("selected_model_id") == null ? null : String.valueOf(data.get("selected_model_id"))
        );
    }

    private void validateSingleTodoItem(Map<String, Object> data) {
        List<String> required = List.of("content", "activeForm", "description", "status", "id");
        List<String> errors = new ArrayList<>();
        for (String field : required) {
            if (!data.containsKey(field) || stringValue(data.get(field)).isBlank()) {
                errors.add("Missing required field: '" + field + "'");
            }
        }
        if (data.containsKey("status") && !isValidStatus(data.get("status"))) {
            errors.add("Invalid status '" + data.get("status") + "'");
        }
        if (!errors.isEmpty()) {
            throw new IllegalArgumentException("Todo data validation error: " + String.join("; ", errors));
        }
    }

    private void validateSingleInProgress(List<TodoItem> todos) {
        long count = todos.stream().filter(todo -> todo.getStatus() == TodoStatus.IN_PROGRESS).count();
        if (count > 1) {
            throw new IllegalArgumentException("More than one task is marked as 'in_progress' (only one allowed)");
        }
    }

    private TodoItem findById(List<TodoItem> todos, String id) {
        return todos.stream().filter(todo -> todo.getId().equals(id)).findFirst().orElse(null);
    }

    private int findIndex(List<TodoItem> todos, String id) {
        for (int i = 0; i < todos.size(); i++) {
            if (todos.get(i).getId().equals(id)) {
                return i;
            }
        }
        return -1;
    }

    private Set<String> idsOf(List<TodoItem> todos) {
        Set<String> ids = new HashSet<>();
        for (TodoItem todo : todos) {
            ids.add(todo.getId());
        }
        return ids;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> todoMaps(Object value, String fieldName) {
        if (!(value instanceof List<?> list) || list.isEmpty()) {
            throw new IllegalArgumentException("Invalid input: '" + fieldName + "' must be a non-empty list");
        }
        return (List<Map<String, Object>>) list;
    }

    private Map<String, Object> todoData(Object value) {
        if (!(value instanceof Map<?, ?> raw)) {
            throw new IllegalArgumentException("Invalid input for insert action: 'todo_data' must be an object with 'target_id' and 'items'");
        }
        Map<String, Object> normalized = new LinkedHashMap<>();
        raw.forEach((key, val) -> normalized.put(String.valueOf(key), val));
        return normalized;
    }

    private List<String> ids(Object value) {
        if (!(value instanceof List<?> list) || list.isEmpty()) {
            throw new IllegalArgumentException("Invalid input: 'ids' must be a non-empty list of task IDs");
        }
        List<String> ids = list.stream().map(String::valueOf).toList();
        if (ids.stream().anyMatch(String::isBlank)) {
            throw new IllegalArgumentException("Invalid input: 'ids' must be a non-empty list of task IDs");
        }
        return ids;
    }

    private TodoStatus parseStatus(Object value) {
        if (!isValidStatus(value)) {
            throw new IllegalArgumentException("Invalid status '" + value + "'");
        }
        return TodoStatus.fromValue(String.valueOf(value));
    }

    private boolean isValidStatus(Object value) {
        if (value == null) {
            return false;
        }
        String text = String.valueOf(value);
        for (TodoStatus status : TodoStatus.values()) {
            if (status.getValue().equals(text) || status.name().equalsIgnoreCase(text)) {
                return true;
            }
        }
        return false;
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}

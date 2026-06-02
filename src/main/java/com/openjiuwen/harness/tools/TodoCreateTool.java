package com.openjiuwen.harness.tools;

import com.openjiuwen.core.session.Session;
import com.openjiuwen.core.sysop.SysOperation;
import com.openjiuwen.harness.schema.task.TodoItem;
import com.openjiuwen.harness.schema.task.TodoStatus;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Mirrors Python's {@code TodoCreateTool} in {@code openjiuwen.harness.tools.todo}.
 */
public class TodoCreateTool extends TodoTool {

    public TodoCreateTool(SysOperation sysOperation) {
        super("todo_create", "todo_create", "Create todo tasks in session state.", sysOperation);
    }

    @Override
    public Object invoke(Map<String, Object> inputs, Map<String, Object> kwargs) {
        Session session = requireSession(kwargs);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> tasks = inputs.get("tasks") instanceof List<?> list
                ? (List<Map<String, Object>>) list
                : null;
        if (tasks == null || tasks.isEmpty()) {
            throw new IllegalArgumentException("'tasks' parameter is required and must be a non-empty JSON array");
        }
        List<TodoItem> created = new ArrayList<>();
        for (int index = 0; index < tasks.size(); index++) {
            Map<String, Object> task = tasks.get(index);
            String content = stringValue(task.get("content"));
            String activeForm = stringValue(task.get("activeForm"));
            String description = stringValue(task.get("description"));
            if (content.isBlank()) {
                throw new IllegalArgumentException("Task at index " + index + " is missing a 'content' field");
            }
            if (activeForm.isBlank()) {
                throw new IllegalArgumentException("Task at index " + index + " is missing a 'activeForm' field");
            }
            if (description.isBlank()) {
                throw new IllegalArgumentException("Task at index " + index + " is missing a 'description' field");
            }
            TodoStatus status = index == 0 ? TodoStatus.IN_PROGRESS : TodoStatus.PENDING;
            TodoItem item = TodoItem.create(
                    content,
                    activeForm,
                    description,
                    status,
                    task.get("selected_model_id") == null ? null : String.valueOf(task.get("selected_model_id"))
            );
            created.add(item);
        }
        saveTodos(session, created);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("message", "Successfully created " + created.size() + " task(s)");
        data.put("tasks", created.stream().map(TodoItem::toMap).toList());
        return data;
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}

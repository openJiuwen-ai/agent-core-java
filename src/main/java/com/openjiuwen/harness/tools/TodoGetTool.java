package com.openjiuwen.harness.tools;

import com.openjiuwen.core.session.Session;
import com.openjiuwen.core.sysop.SysOperation;
import com.openjiuwen.harness.schema.task.TodoItem;

import java.util.List;
import java.util.Map;

/**
 * Mirrors Python's {@code TodoGetTool} in {@code openjiuwen.harness.tools.todo}.
 */
public class TodoGetTool extends TodoTool {

    public TodoGetTool(SysOperation sysOperation) {
        super("todo_get", "todo_get", "Get todo tasks from session state.", sysOperation);
    }

    @Override
    public Object invoke(Map<String, Object> inputs, Map<String, Object> kwargs) {
        Session session = requireSession(kwargs);
        String id = inputs.get("id") == null ? "" : String.valueOf(inputs.get("id"));
        if (id.isBlank()) {
            throw new IllegalArgumentException("Task ID is required");
        }
        List<TodoItem> todos = loadTodos(session);
        for (TodoItem todo : todos) {
            if (todo.getId().equals(id)) {
                return Map.of("todo", todo.toMap());
            }
        }
        throw new IllegalArgumentException("Task with id '" + id + "' not found");
    }
}

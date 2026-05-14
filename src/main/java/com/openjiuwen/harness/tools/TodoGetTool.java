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
        List<TodoItem> todos = loadTodos(session);
        if (id.isBlank()) {
            return Map.of("tasks", todos.stream().map(TodoItem::toMap).toList());
        }
        return Map.of("tasks", todos.stream().filter(todo -> todo.getId().equals(id)).map(TodoItem::toMap).toList());
    }
}

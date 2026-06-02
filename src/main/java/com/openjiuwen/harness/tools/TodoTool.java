package com.openjiuwen.harness.tools;

import com.openjiuwen.core.session.Session;
import com.openjiuwen.core.sysop.SysOperation;
import com.openjiuwen.harness.schema.task.TodoItem;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Mirrors Python's {@code TodoTool} base flow in {@code openjiuwen.harness.tools.todo}.
 */
public abstract class TodoTool extends AbstractHarnessTool {

    protected TodoTool(String id, String name, String description, SysOperation sysOperation) {
        super(toolCard(id, name, description), sysOperation);
    }

    protected Session requireSession(Map<String, Object> kwargs) {
        Object session = kwargs != null ? kwargs.get("session") : null;
        if (session instanceof Session typed) {
            return typed;
        }
        throw new IllegalStateException("todo tools require session");
    }

    protected List<TodoItem> loadTodos(Session session) {
        Object raw = session.getState("harness.todos");
        if (!(raw instanceof List<?> list)) {
            return new ArrayList<>();
        }
        List<TodoItem> todos = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof TodoItem todoItem) {
                todos.add(todoItem);
            } else if (item instanceof Map<?, ?> map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> typed = (Map<String, Object>) map;
                todos.add(TodoItem.fromMap(typed));
            }
        }
        return todos;
    }

    protected void saveTodos(Session session, List<TodoItem> todos) {
        session.updateState(Map.of("harness.todos", todos.stream().map(TodoItem::toMap).toList()));
    }
}

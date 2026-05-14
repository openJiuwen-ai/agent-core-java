package com.openjiuwen.harness.tools;

import com.openjiuwen.core.session.Session;
import com.openjiuwen.core.sysop.SysOperation;
import com.openjiuwen.harness.schema.task.TodoItem;
import com.openjiuwen.harness.schema.task.TodoStatus;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> updates = inputs.get("todos") instanceof List<?> list ? (List<Map<String, Object>>) list : List.of();
        int updatedCount = 0;
        for (Map<String, Object> update : updates) {
            String id = update.get("id") == null ? "" : String.valueOf(update.get("id"));
            for (TodoItem todo : todos) {
                if (!todo.getId().equals(id)) {
                    continue;
                }
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
                    todo.setStatus(TodoStatus.fromValue(String.valueOf(update.get("status"))));
                }
                updatedCount++;
            }
        }
        saveTodos(session, todos);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("message", "Successfully updated " + updatedCount + " task(s)");
        data.put("tasks", todos.stream().map(TodoItem::toMap).toList());
        return data;
    }
}

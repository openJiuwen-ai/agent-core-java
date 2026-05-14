package com.openjiuwen.harness.tools;

import com.openjiuwen.core.session.Session;
import com.openjiuwen.core.sysop.SysOperation;
import com.openjiuwen.harness.schema.task.TodoItem;
import com.openjiuwen.harness.schema.task.TodoStatus;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Mirrors Python's {@code TodoListTool} in {@code openjiuwen.harness.tools.todo}.
 */
public class TodoListTool extends TodoTool {

    public TodoListTool(SysOperation sysOperation) {
        super("todo_list", "todo_list", "List active todo tasks from session state.", sysOperation);
    }

    @Override
    public Object invoke(Map<String, Object> inputs, Map<String, Object> kwargs) {
        Session session = requireSession(kwargs);
        List<Map<String, Object>> tasks = loadTodos(session).stream()
                .filter(todo -> todo.getStatus() == TodoStatus.IN_PROGRESS || todo.getStatus() == TodoStatus.PENDING)
                .map(todo -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("id", todo.getId());
                    item.put("content", todo.getContent());
                    item.put("status", todo.getStatus().getValue());
                    item.put("depends_on", todo.getDependsOn());
                    return item;
                })
                .toList();
        return Map.of("tasks", tasks);
    }
}

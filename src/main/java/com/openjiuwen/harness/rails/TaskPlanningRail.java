/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.rails;

import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.harness.DeepAgent;
import com.openjiuwen.harness.prompts.sections.TodoSection;
import com.openjiuwen.harness.schema.task.ModelUsageRecord;
import com.openjiuwen.harness.schema.task.TaskPlan;
import com.openjiuwen.harness.schema.task.TodoItem;
import com.openjiuwen.harness.schema.task.TodoStatus;
import com.openjiuwen.harness.tools.TodoTools;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Synchronizes todo planning prompts and task progress reminders.
 *
 * <p>Mirrors Python's {@code TaskPlanningRail} in
 * {@code openjiuwen/harness/rails/task_planning_rail.py}.</p>
 */
public class TaskPlanningRail extends DeepAgentRail {

    private final boolean enableProgressRepeat;
    private final int listToolCallInterval;
    private final Map<String, Object> modelSelection = new LinkedHashMap<>();
    private final Map<String, Integer> toolCallCounts = new LinkedHashMap<>();
    private final Map<String, List<TodoItem>> todosCache = new LinkedHashMap<>();
    private final Map<String, ModelUsageRecord> usageRecords = new LinkedHashMap<>();
    private final List<Tool> tools = new ArrayList<>();
    private final TodoTools.TodoStore todoStore = new InMemoryTodoStore();
    private String defaultModelId;

    public TaskPlanningRail() {
        this(false, 20, Map.of());
    }

    public TaskPlanningRail(boolean enableProgressRepeat, int listToolCallInterval, Map<String, Object> modelSelection) {
        setPriority(90);
        this.enableProgressRepeat = enableProgressRepeat;
        this.listToolCallInterval = listToolCallInterval <= 0 ? 20 : listToolCallInterval;
        if (modelSelection != null) {
            this.modelSelection.putAll(modelSelection);
        }
    }

    @Override
    public void init(DeepAgent agent) {
        super.init(agent);
        if (agent == null) {
            return;
        }
        if (tools.isEmpty()) {
            tools.addAll(TodoTools.createTodosTool(todoStore));
        }
        tools.forEach(agent::registerTool);
    }

    @Override
    public void uninit(DeepAgent agent) {
        if (agent != null) {
            for (Tool tool : tools) {
                if (tool.getCard() != null) {
                    agent.unregisterTool(tool.getCard().getName());
                }
            }
        }
        tools.clear();
    }

    @Override
    public void beforeModelCall(CallbackContext ctx) {
        String language = stringValue(ctx.getValues().getOrDefault("language", "cn"));
        ctx.put("todo_section", TodoSection.buildTodoSection(language, modelSelection));
        if (modelSelection.isEmpty()) {
            return;
        }
        if (defaultModelId == null) {
            defaultModelId = stringValue(ctx.getValues().get("default_model_id"));
        }
        String selectedModelId = getInProgressModelId(ctx);
        String targetModelId = selectedModelId != null && modelSelection.containsKey(selectedModelId)
                ? selectedModelId
                : defaultModelId;
        if (targetModelId != null && !targetModelId.isBlank()) {
            ctx.put("target_model_id", targetModelId);
        }
    }

    @Override
    public void afterToolCall(CallbackContext ctx) {
        String sessionId = sessionId(ctx);
        Object toolName = ctx.getValues().get("tool_name");
        if (sessionId != null && toolName instanceof String name && name.startsWith("todo_")) {
            List<TodoItem> todos = todosFromContext(ctx);
            if (!todos.isEmpty()) {
                todosCache.put(sessionId, new ArrayList<>(todos));
            }
        }
        if (!enableProgressRepeat || sessionId == null || !ctx.getValues().containsKey("messages")) {
            return;
        }

        int count = toolCallCounts.getOrDefault(sessionId, 0) + 1;
        toolCallCounts.put(sessionId, count);
        if (count % listToolCallInterval != 0) {
            return;
        }

        List<TodoItem> todos = todosFromContext(ctx);
        if (todos.isEmpty()) {
            todos = todosCache.getOrDefault(sessionId, List.of());
        }
        if (todos.isEmpty()) {
            return;
        }

        FormattedTaskContent formatted = formatTaskContent(todos);
        String language = stringValue(ctx.getValues().getOrDefault("language", "cn"));
        String prompt = TodoSection.buildProgressReminderUserPrompt(
                language,
                formatted.tasks(),
                formatted.inProgressTask()
        );
        List<Object> messages = mutableMessages(ctx.getValues().get("messages"));
        messages.add(prompt);
        ctx.put("messages", messages);
        ctx.put("should_repeat_progress", true);
    }

    @Override
    public void afterModelCall(CallbackContext ctx) {
        String modelId = stringValue(ctx.getValues().get("model_id"));
        int inputTokens = intValue(ctx.getValues().get("input_tokens"));
        int outputTokens = intValue(ctx.getValues().get("output_tokens"));
        if (modelId == null || modelId.isBlank() || inputTokens == 0 && outputTokens == 0) {
            return;
        }
        usageRecords.computeIfAbsent(modelId, ModelUsageRecord::new).add(inputTokens, outputTokens);
    }

    @Override
    public void afterInvoke(CallbackContext ctx) {
        usageRecords.clear();
        String sessionId = sessionId(ctx);
        if (sessionId != null) {
            toolCallCounts.remove(sessionId);
            todosCache.remove(sessionId);
        }
    }

    @Override
    public void afterTaskIteration(CallbackContext ctx) {
        if (!(ctx.getValues().get("plan") instanceof TaskPlan plan) || plan.getTasks().isEmpty()) {
            return;
        }
        List<TodoItem> todos = todosFromContext(ctx);
        if (todos.isEmpty()) {
            return;
        }
        Map<String, TodoStatus> statusByTaskId = new LinkedHashMap<>();
        for (TodoItem task : plan.getTasks()) {
            statusByTaskId.put(task.getId(), task.getStatus());
        }
        boolean changed = false;
        for (TodoItem todo : todos) {
            TodoStatus desired = statusByTaskId.get(todo.getId());
            if (desired != null && todo.getStatus() != desired) {
                todo.setStatus(desired);
                changed = true;
            }
        }
        if (changed) {
            ctx.put("todos_changed", true);
            ctx.put("saved_todos", new ArrayList<>(todos));
        }
    }

    public boolean isEnableProgressRepeat() {
        return enableProgressRepeat;
    }

    public int getListToolCallInterval() {
        return listToolCallInterval;
    }

    public Map<String, Object> getModelSelection() {
        return new LinkedHashMap<>(modelSelection);
    }

    public List<Tool> getTools() {
        return new ArrayList<>(tools);
    }

    public int getToolCallCount() {
        return toolCallCounts.values().stream().mapToInt(Integer::intValue).sum();
    }

    public int getToolCallCount(String sessionId) {
        return toolCallCounts.getOrDefault(sessionId, 0);
    }

    public Map<String, ModelUsageRecord> getUsageRecords() {
        return new LinkedHashMap<>(usageRecords);
    }

    public Map<String, List<TodoItem>> getTodosCache() {
        return new LinkedHashMap<>(todosCache);
    }

    public String getInProgressModelId(CallbackContext ctx) {
        String sessionId = sessionId(ctx);
        List<TodoItem> todos = sessionId == null ? List.of() : todosCache.get(sessionId);
        if (todos == null || todos.isEmpty()) {
            todos = todosFromContext(ctx);
            if (sessionId != null && !todos.isEmpty()) {
                todosCache.put(sessionId, new ArrayList<>(todos));
            }
        }
        for (TodoItem todo : todos) {
            if (todo.getStatus() == TodoStatus.IN_PROGRESS) {
                return todo.getSelectedModelId();
            }
        }
        return null;
    }

    public FormattedTaskContent formatTaskContent(List<TodoItem> todos) {
        List<String> lines = new ArrayList<>();
        String inProgressTask = "";
        for (TodoItem todo : Objects.requireNonNullElse(todos, List.<TodoItem>of())) {
            if (todo.getStatus() == TodoStatus.IN_PROGRESS) {
                inProgressTask = todo.getContent();
            }
            lines.add("id: " + todo.getId()
                    + " |status: " + todo.getStatus().getValue()
                    + " |content: " + todo.getContent());
        }
        return new FormattedTaskContent(String.join("\n", lines), inProgressTask);
    }

    public record FormattedTaskContent(String tasks, String inProgressTask) {
    }

    private String sessionId(CallbackContext ctx) {
        String sessionId = stringValue(ctx.getValues().get("session_id"));
        return sessionId == null || sessionId.isBlank() ? null : sessionId;
    }

    private List<TodoItem> todosFromContext(CallbackContext ctx) {
        Object value = ctx.getValues().get("todos");
        if (!(value instanceof List<?> rawItems)) {
            return new ArrayList<>();
        }
        List<TodoItem> todos = new ArrayList<>();
        for (Object item : rawItems) {
            if (item instanceof TodoItem todoItem) {
                todos.add(todoItem);
            }
        }
        return todos;
    }

    private static List<Object> mutableMessages(Object value) {
        if (value instanceof List<?> rawValues) {
            return new ArrayList<>(rawValues);
        }
        return new ArrayList<>();
    }

    private static String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static int intValue(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value != null) {
            try {
                return Integer.parseInt(String.valueOf(value));
            } catch (NumberFormatException ignored) {
                return 0;
            }
        }
        return 0;
    }

    private final class InMemoryTodoStore implements TodoTools.TodoStore {
        @Override
        public List<TodoItem> load(Map<String, Object> kwargs) {
            String sessionId = stringValue(kwargs == null ? null : kwargs.get("session_id"));
            if (sessionId == null || sessionId.isBlank()) {
                sessionId = "__default__";
            }
            return new ArrayList<>(todosCache.getOrDefault(sessionId, List.of()));
        }

        @Override
        public void save(List<TodoItem> todos, Map<String, Object> kwargs) {
            String sessionId = stringValue(kwargs == null ? null : kwargs.get("session_id"));
            if (sessionId == null || sessionId.isBlank()) {
                sessionId = "__default__";
            }
            todosCache.put(sessionId, todos == null ? new ArrayList<>() : new ArrayList<>(todos));
        }
    }
}

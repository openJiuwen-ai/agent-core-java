/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.rails;

import com.openjiuwen.core.context.ModelContext;
import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.runner.base.TagMatchStrategy;
import com.openjiuwen.core.session.Session;
import com.openjiuwen.core.single_agent.prompts.SystemPromptBuilder;
import com.openjiuwen.core.singleagent.AbilityManager;
import com.openjiuwen.core.singleagent.BaseAgent;
import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.core.singleagent.rail.InvokeInputs;
import com.openjiuwen.core.singleagent.rail.ToolCallInputs;
import com.openjiuwen.harness.DeepAgent;
import com.openjiuwen.harness.DeepAgentConfig;
import com.openjiuwen.harness.prompts.sections.TodoSection;
import com.openjiuwen.harness.schema.DeepAgentState;
import com.openjiuwen.harness.schema.task.ModelUsageRecord;
import com.openjiuwen.harness.schema.task.TaskPlan;
import com.openjiuwen.harness.schema.task.TodoItem;
import com.openjiuwen.harness.schema.task.TodoStatus;
import com.openjiuwen.harness.tools.TodoCreateTool;
import com.openjiuwen.harness.tools.TodoGetTool;
import com.openjiuwen.harness.tools.TodoListTool;
import com.openjiuwen.harness.tools.TodoModifyTool;
import com.openjiuwen.harness.tools.TodoTool;
import com.openjiuwen.harness.workspace.Workspace;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Rail that registers todo tools, repeats progress reminders, and syncs todo state.
 *
 * <p>Mirrors Python's {@code TaskPlanningRail} in
 * {@code openjiuwen.harness.rails.task_planning_rail}.</p>
 */
public class TaskPlanningRail extends DeepAgentRail {

    private static final Logger LOG = LoggerFactory.getLogger(TaskPlanningRail.class);
    private static final String TODOS_STATE_KEY = "harness.todos";

    private final List<TodoTool> registeredTodoTools = new ArrayList<>();
    private final Map<String, Integer> toolCallCounts = new LinkedHashMap<>();
    private final Map<String, List<TodoItem>> todosCache = new LinkedHashMap<>();
    private final Map<Model, String> modelSelection;
    private final Map<String, Model> modelIdToModel = new LinkedHashMap<>();
    private final Map<String, ModelUsageRecord> usageRecords = new LinkedHashMap<>();

    private boolean enableProgressRepeat;
    private int listToolCallInterval;
    private SystemPromptBuilder systemPromptBuilder;
    private Object defaultLlm;

    public TaskPlanningRail() {
        this(false, 20, null);
    }

    public TaskPlanningRail(boolean enableProgressRepeat) {
        this(enableProgressRepeat, 20, null);
    }

    public TaskPlanningRail(boolean enableProgressRepeat, int listToolCallInterval) {
        this(enableProgressRepeat, listToolCallInterval, null);
    }

    public TaskPlanningRail(Map<Model, String> modelSelection) {
        this(false, 20, modelSelection);
    }

    public TaskPlanningRail(boolean enableProgressRepeat, int listToolCallInterval, Map<Model, String> modelSelection) {
        this.enableProgressRepeat = enableProgressRepeat;
        this.listToolCallInterval = listToolCallInterval > 0 ? listToolCallInterval : 20;
        this.modelSelection = modelSelection != null ? new LinkedHashMap<>(modelSelection) : new LinkedHashMap<>();
        for (Model model : this.modelSelection.keySet()) {
            String modelId = readModelId(model);
            if (modelId != null && !modelId.isBlank()) {
                modelIdToModel.put(modelId, model);
            }
        }
        setPriority(90);
    }

    @Override
    public void init(Object agent) {
        systemPromptBuilder = resolveSystemPromptBuilder(agent);
        if (agent instanceof DeepAgent deepAgent && deepAgent.getConfig() instanceof DeepAgentConfig config) {
            if (sysOperation == null) {
                sysOperation = config.getSysOperation();
            }
            if (workspace == null) {
                workspace = config.getWorkspace();
            }
        } else {
            Object config = readProperty(agent, "config", "deepConfig", "deep_config");
            if (sysOperation == null) {
                Object op = readProperty(config, "sysOperation", "sys_operation");
                if (op instanceof com.openjiuwen.core.sysop.SysOperation typed) {
                    sysOperation = typed;
                }
            }
            if (workspace == null) {
                Object ws = readProperty(config, "workspace");
                if (ws instanceof Workspace typed) {
                    workspace = typed;
                }
            }
        }
        if (workspace == null) {
            workspace = new Workspace("./", language());
        }

        AbilityManager abilityManager = resolveAbilityManager(agent);
        List<TodoTool> tools = buildTodoTools();
        registeredTodoTools.clear();
        for (TodoTool tool : tools) {
            registerToolInstance(tool, abilityManager);
            registeredTodoTools.add(tool);
        }
    }

    @Override
    public void uninit(Object agent) {
        if (systemPromptBuilder != null) {
            systemPromptBuilder.removeSection("todo");
        }
        AbilityManager abilityManager = resolveAbilityManager(agent);
        for (TodoTool tool : registeredTodoTools) {
            if (abilityManager != null && tool.getCard() != null) {
                abilityManager.remove(tool.getCard().getName());
            }
            if (tool.getCard() != null && tool.getCard().getId() != null) {
                Runner.resourceMgr().removeTool(tool.getCard().getId(), null, TagMatchStrategy.ALL, true);
            }
        }
        registeredTodoTools.clear();
        systemPromptBuilder = null;
    }

    @Override
    public void beforeInvoke(AgentCallbackContext ctx) {
        Session session = ctx != null ? ctx.getSession() : null;
        if (session == null) {
            return;
        }
        if (session.getState(TODOS_STATE_KEY) == null) {
            session.updateState(Map.of(TODOS_STATE_KEY, List.of()));
        }
    }

    @Override
    public void beforeModelCall(AgentCallbackContext ctx) {
        SystemPromptBuilder builder = systemPromptBuilder != null
                ? systemPromptBuilder
                : resolveSystemPromptBuilder(ctx != null ? ctx.getAgent() : null);
        if (builder != null) {
            builder.addSection(TodoSection.build(language(builder), buildModelList()));
        }
        if (builder == null) {
            return;
        }
        if (modelSelection.isEmpty() || ctx == null) {
            return;
        }
        if (defaultLlm == null) {
            defaultLlm = readAgentLlm(ctx.getAgent());
        }
        String selectedModelId = getInProgressModelId(ctx);
        Object targetModel = selectedModelId != null && modelIdToModel.containsKey(selectedModelId)
                ? modelIdToModel.get(selectedModelId)
                : defaultLlm;
        if (targetModel != null) {
            setAgentLlm(ctx.getAgent(), targetModel);
            updateAgentModelName(ctx.getAgent(), targetModel);
            LOG.debug("TaskPlanningRail: switched to model_id={}", selectedModelId);
        }
    }

    @Override
    public void afterToolCall(AgentCallbackContext ctx) {
        if (ctx == null || ctx.getSession() == null) {
            return;
        }
        if (findTodoTool() == null) {
            return;
        }
        Session session = ctx.getSession();
        String sessionId = session.getSessionId();
        String toolName = ctx.getInputs() instanceof ToolCallInputs inputs
                ? safeString(inputs.getToolName()).trim()
                : "";
        if (toolName.startsWith("todo_")) {
            todosCache.put(sessionId, loadTodos(session));
        }

        if (!enableProgressRepeat || ctx.getContext() == null) {
            return;
        }
        int count = toolCallCounts.getOrDefault(sessionId, 0) + 1;
        toolCallCounts.put(sessionId, count);
        if (count % listToolCallInterval != 0) {
            return;
        }

        List<TodoItem> todos = loadTodos(session);
        if (todos.isEmpty()) {
            return;
        }
        FormattedTaskContent formatted = formatTaskContent(todos);
        String prompt = TodoSection.buildProgressReminderUserPrompt(
                language(systemPromptBuilder),
                formatted.tasks(),
                formatted.inProgressTask());
        ModelContext context = ctx.getContext();
        List<com.openjiuwen.core.foundation.llm.schema.BaseMessage> messages = new ArrayList<>(context.getMessages());
        messages.add(new UserMessage(prompt));
        context.setMessages(messages);
    }

    @Override
    public void afterModelCall(AgentCallbackContext ctx) {
        if (ctx == null) {
            return;
        }
        Object useModel = readAgentLlm(ctx.getAgent());
        String modelId = readModelId(useModel);
        if (modelId == null || modelId.isBlank()) {
            return;
        }
        Object response = readProperty(ctx.getInputs(), "response");
        Object usage = readProperty(response, "usageMetadata", "usage_metadata");
        int inputTokens = readInt(usage, "inputTokens", "input_tokens");
        int outputTokens = readInt(usage, "outputTokens", "output_tokens");
        if (inputTokens == 0 && outputTokens == 0) {
            return;
        }
        usageRecords.computeIfAbsent(modelId, ModelUsageRecord::new).add(inputTokens, outputTokens);
    }

    @Override
    public void afterInvoke(AgentCallbackContext ctx) {
        if (!usageRecords.isEmpty()) {
            for (ModelUsageRecord record : usageRecords.values()) {
                LOG.info("TaskPlanningRail token usage: {}", record);
            }
            usageRecords.clear();
        }
        if (ctx == null || ctx.getSession() == null) {
            return;
        }
        String sessionId = ctx.getSession().getSessionId();
        todosCache.remove(sessionId);
        toolCallCounts.remove(sessionId);

        if (ctx.getInputs() instanceof InvokeInputs invokeInputs && loadTodos(ctx.getSession()).isEmpty()) {
            Map<String, Object> bootstrap = new LinkedHashMap<>();
            String query = invokeInputs.getQuery();
            bootstrap.put("content", query != null ? query : "task");
            bootstrap.put("status", "pending");
            bootstrap.put("priority", "high");
            ctx.getSession().updateState(Map.of(TODOS_STATE_KEY, List.of(bootstrap)));
        }
    }

    @Override
    public void afterTaskIteration(AgentCallbackContext ctx) {
        syncTodosFromPlan(ctx);
    }

    public List<TodoTool> getRegisteredTodoTools() {
        return List.copyOf(registeredTodoTools);
    }

    public boolean isEnableProgressRepeat() {
        return enableProgressRepeat;
    }

    public void setEnableProgressRepeat(boolean enableProgressRepeat) {
        this.enableProgressRepeat = enableProgressRepeat;
    }

    public int getListToolCallInterval() {
        return listToolCallInterval;
    }

    public void setListToolCallInterval(int listToolCallInterval) {
        this.listToolCallInterval = listToolCallInterval > 0 ? listToolCallInterval : 20;
    }

    public Map<String, Integer> getToolCallCounts() {
        return toolCallCounts;
    }

    public Map<String, List<TodoItem>> getTodosCache() {
        return todosCache;
    }

    public Map<Model, String> getModelSelection() {
        return modelSelection;
    }

    public Map<String, Model> getModelIdToModel() {
        return modelIdToModel;
    }

    public Map<String, ModelUsageRecord> getUsageRecords() {
        return usageRecords;
    }

    public void putUsageRecord(String modelId, ModelUsageRecord record) {
        usageRecords.put(modelId, record);
    }

    public SystemPromptBuilder getSystemPromptBuilder() {
        return systemPromptBuilder;
    }

    public void setSystemPromptBuilder(SystemPromptBuilder systemPromptBuilder) {
        this.systemPromptBuilder = systemPromptBuilder;
    }

    public Workspace getWorkspace() {
        return workspace;
    }

    public String buildModelList() {
        if (modelSelection.isEmpty()) {
            return null;
        }
        List<String> lines = new ArrayList<>();
        for (Map.Entry<Model, String> entry : modelSelection.entrySet()) {
            String modelId = readModelId(entry.getKey());
            if (modelId != null && !modelId.isBlank()) {
                lines.add("- " + modelId + ": " + entry.getValue());
            }
        }
        return String.join("\n", lines);
    }

    public FormattedTaskContent formatTaskContent(List<TodoItem> todos) {
        List<String> lines = new ArrayList<>();
        String inProgress = "";
        for (TodoItem todo : todos != null ? todos : List.<TodoItem>of()) {
            if (todo.getStatus() == TodoStatus.IN_PROGRESS) {
                inProgress = todo.getContent();
            }
            lines.add("id: " + todo.getId()
                    + " |status: " + todo.getStatus().getValue()
                    + " |content: " + todo.getContent());
        }
        return new FormattedTaskContent(String.join("\n", lines), inProgress);
    }

    public TodoTool findTodoTool() {
        return registeredTodoTools.isEmpty() ? null : registeredTodoTools.get(0);
    }

    private void syncTodosFromPlan(AgentCallbackContext ctx) {
        if (ctx == null || ctx.getSession() == null || ctx.getAgent() == null) {
            return;
        }
        if (findTodoTool() == null) {
            return;
        }
        TaskPlan plan = loadTaskPlan(ctx.getAgent(), ctx.getSession());
        if (plan == null || plan.getTasks().isEmpty()) {
            return;
        }
        List<TodoItem> todos = loadTodos(ctx.getSession());
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
            saveTodos(ctx.getSession(), todos);
            todosCache.put(ctx.getSession().getSessionId(), todos);
            LOG.info("TaskPlanningRail: synced {} todos from TaskPlan", todos.size());
        }
    }

    private TaskPlan loadTaskPlan(Object agent, Session session) {
        Object state = invokeOneArg(agent, "loadState", session);
        if (state == null) {
            state = invokeOneArg(agent, "load_state", session);
        }
        if (state instanceof DeepAgentState deepAgentState) {
            return deepAgentState.getTaskPlan();
        }
        Object rawPlan = readProperty(state, "taskPlan", "task_plan");
        if (rawPlan instanceof TaskPlan taskPlan) {
            return taskPlan;
        }
        if (rawPlan instanceof Map<?, ?> map) {
            return TaskPlan.fromMap(toStringMap(map));
        }
        return null;
    }

    private String getInProgressModelId(AgentCallbackContext ctx) {
        Session session = ctx.getSession();
        if (session == null) {
            return null;
        }
        String sessionId = session.getSessionId();
        List<TodoItem> todos = todosCache.computeIfAbsent(sessionId, ignored -> loadTodos(session));
        for (TodoItem todo : todos) {
            if (todo.getStatus() == TodoStatus.IN_PROGRESS) {
                return todo.getSelectedModelId();
            }
        }
        return null;
    }

    private List<TodoTool> buildTodoTools() {
        return List.of(
                new TodoCreateTool(sysOperation),
                new TodoListTool(sysOperation),
                new TodoGetTool(sysOperation),
                new TodoModifyTool(sysOperation)
        );
    }

    private void registerToolInstance(TodoTool tool, AbilityManager abilityManager) {
        if (tool == null || tool.getCard() == null) {
            return;
        }
        if (Runner.resourceMgr().getTool(tool.getCard().getId()) == null) {
            try {
                Runner.resourceMgr().addTool(tool, null);
            } catch (RuntimeException exc) {
                LOG.debug("TaskPlanningRail: todo tool already registered: {}", tool.getCard().getId());
            }
        }
        if (abilityManager != null && abilityManager.get(tool.getCard().getName()) == null) {
            abilityManager.add(tool.getCard());
        }
    }

    private List<TodoItem> loadTodos(Session session) {
        if (session == null) {
            return List.of();
        }
        Object raw = session.getState(TODOS_STATE_KEY);
        if (!(raw instanceof Iterable<?> iterable)) {
            return new ArrayList<>();
        }
        List<TodoItem> todos = new ArrayList<>();
        for (Object item : iterable) {
            if (item instanceof TodoItem todoItem) {
                todos.add(todoItem);
            } else if (item instanceof Map<?, ?> map) {
                todos.add(TodoItem.fromMap(toStringMap(map)));
            }
        }
        return todos;
    }

    private void saveTodos(Session session, List<TodoItem> todos) {
        if (session != null) {
            session.updateState(Map.of(TODOS_STATE_KEY, todos.stream().map(TodoItem::toMap).toList()));
        }
    }

    private static AbilityManager resolveAbilityManager(Object agent) {
        if (agent instanceof BaseAgent baseAgent) {
            return baseAgent.getAbilityManager();
        }
        Object value = readProperty(agent, "abilityManager", "ability_manager");
        return value instanceof AbilityManager abilityManager ? abilityManager : null;
    }

    private static SystemPromptBuilder resolveSystemPromptBuilder(Object agent) {
        Object value = readProperty(agent, "systemPromptBuilder", "system_prompt_builder", "builder");
        return value instanceof SystemPromptBuilder builder ? builder : null;
    }

    private String language() {
        return language(systemPromptBuilder);
    }

    private static String language(SystemPromptBuilder builder) {
        return builder != null && builder.getLanguage() != null ? builder.getLanguage() : "cn";
    }

    private static Object readAgentLlm(Object agent) {
        if (agent instanceof DeepAgent deepAgent) {
            Object llm = readProperty(deepAgent.getDelegate(), "llm");
            if (llm != null) {
                return llm;
            }
        }
        Object value = readProperty(agent, "llm", "_llm");
        return value;
    }

    private static void setAgentLlm(Object agent, Object model) {
        if (agent instanceof DeepAgent deepAgent && model instanceof Model typedModel) {
            deepAgent.getDelegate().setLlm(typedModel);
            return;
        }
        if (invokeOneArg(agent, "setLlm", model) != null) {
            return;
        }
        if (invokeOneArg(agent, "set_llm", model) != null) {
            return;
        }
        writeField(agent, "_llm", model);
    }

    private static void updateAgentModelName(Object agent, Object model) {
        Object modelConfig = readProperty(model, "modelConfig", "model_config");
        Object modelName = readProperty(modelConfig, "modelName", "model_name");
        Object config = readProperty(agent, "config");
        if (config != null && modelName != null) {
            writeField(config, "modelName", modelName);
        }
    }

    private static String readModelId(Object model) {
        Object clientConfig = readProperty(model, "modelClientConfig", "model_client_config");
        Object clientId = readProperty(clientConfig, "clientId", "client_id");
        return clientId != null ? String.valueOf(clientId) : null;
    }

    private static Object readProperty(Object target, String... names) {
        if (target == null) {
            return null;
        }
        for (String name : names) {
            Object value = invokeNoArg(target, getterName(name));
            if (value != null) {
                return value;
            }
            value = invokeNoArg(target, name);
            if (value != null) {
                return value;
            }
            value = readField(target, name);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static String getterName(String name) {
        if (name == null || name.isBlank()) {
            return name;
        }
        if (name.startsWith("_")) {
            return name;
        }
        return "get" + Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }

    private static Object invokeNoArg(Object target, String methodName) {
        if (target == null || methodName == null || methodName.isBlank()) {
            return null;
        }
        try {
            Method method = target.getClass().getMethod(methodName);
            method.setAccessible(true);
            return method.invoke(target);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static Object invokeOneArg(Object target, String methodName, Object arg) {
        if (target == null) {
            return null;
        }
        for (Method method : target.getClass().getMethods()) {
            if (!method.getName().equals(methodName) || method.getParameterCount() != 1) {
                continue;
            }
            try {
                method.setAccessible(true);
                return method.invoke(target, arg);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("Failed to invoke " + methodName, e);
            }
        }
        return null;
    }

    private static Object readField(Object target, String fieldName) {
        if (target == null || fieldName == null || fieldName.isBlank()) {
            return null;
        }
        Class<?> type = target.getClass();
        while (type != null) {
            try {
                Field field = type.getDeclaredField(fieldName);
                field.setAccessible(true);
                return field.get(target);
            } catch (NoSuchFieldException ignored) {
                type = type.getSuperclass();
            } catch (IllegalAccessException e) {
                throw new IllegalStateException("Failed to read field '" + fieldName + "'", e);
            }
        }
        return null;
    }

    private static void writeField(Object target, String fieldName, Object value) {
        if (target == null || fieldName == null || fieldName.isBlank()) {
            return;
        }
        Class<?> type = target.getClass();
        while (type != null) {
            try {
                Field field = type.getDeclaredField(fieldName);
                field.setAccessible(true);
                field.set(target, value);
                return;
            } catch (NoSuchFieldException ignored) {
                type = type.getSuperclass();
            } catch (IllegalAccessException e) {
                throw new IllegalStateException("Failed to write field '" + fieldName + "'", e);
            }
        }
    }

    private static int readInt(Object target, String... names) {
        Object value = readProperty(target, names);
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return value != null ? Integer.parseInt(String.valueOf(value)) : 0;
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private static Map<String, Object> toStringMap(Map<?, ?> map) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (entry.getKey() != null) {
                result.put(String.valueOf(entry.getKey()), entry.getValue());
            }
        }
        return result;
    }

    private static String safeString(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    public record FormattedTaskContent(String tasks, String inProgressTask) {
    }
}

/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.rails;

import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.foundation.tool.function.LocalFunction;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.SystemMessage;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.UsageMetadata;
import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.core.singleagent.rail.ModelCallInputs;
import com.openjiuwen.core.singleagent.rail.ToolCallInputs;
import com.openjiuwen.harness.deep_agent.DeepAgent;
import com.openjiuwen.harness.prompts.sections.tools.ToolMetadataRegistry;
import com.openjiuwen.harness.task_loop.TaskIterationContext;
import com.openjiuwen.harness.task_loop.TaskPlan;
import com.openjiuwen.harness.task_loop.TaskPlanSnapshot;
import com.openjiuwen.harness.tools.FileTodoStorage;
import com.openjiuwen.harness.tools.KvTodoStorage;
import com.openjiuwen.harness.tools.TodoStorage;
import com.openjiuwen.harness.tools.TodoStorageFactory;
import com.openjiuwen.harness.tools.TodoTool;
import com.openjiuwen.harness.tools.TodoItem;
import com.openjiuwen.harness.tools.TodoStatus;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.spi.store.BaseKVStore;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Public class TaskPlanningRail used by the Java parity implementation.
 * 
 * @since 0.1.7
 */
public class TaskPlanningRail extends DeepAgentRail implements TaskIterationRail {
    private static final String TASK_PLANNING_MODEL_ID = "task_planning.model_id";
    private static final String TODO_SECTION = "todo";
    private static final int TODO_SECTION_PRIORITY = 90;
    private final boolean isProgressRepeatEnabled;
    private final int listToolCallInterval;

    /**
     * LinkedHashMap<>.
     * 
     * @since 0.1.7
     */
    private final Map<String, String> modelSelection = new LinkedHashMap<>();

    /**
     * ArrayList<>.
     * 
     * @since 0.1.7
     */
    private final List<Tool> tools = new ArrayList<>();

    /**
     * HashMap<>.
     * 
     * @since 0.1.7
     */
    private final Map<String, Integer> toolCallCounts = new HashMap<>();

    /**
     * HashMap<>.
     * 
     * @since 0.1.7
     */
    private final Map<String, List<TodoItem>> todosCache = new HashMap<>();

    /**
     * LinkedHashMap<>.
     * 
     * @since 0.1.7
     */
    private final Map<String, ModelUsageRecord> usageRecords = new LinkedHashMap<>();
    private TodoTool todoTool;
    private DeepAgent owner;
    private String language = "cn";
    private Model defaultLlm;
    private boolean isDefaultLlmCaptured;

    /**
     * TaskPlanningRail.
     * 
     * @since 0.1.7
     */
    public TaskPlanningRail() {
        this(false, 20);
    }

    /**
     * TaskPlanningRail.
     * 
     * @param isProgressRepeatEnabled isProgressRepeatEnabled
     * @param listToolCallInterval listToolCallInterval
     * @since 0.1.7
     */
    public TaskPlanningRail(boolean isProgressRepeatEnabled, int listToolCallInterval) {
        this(isProgressRepeatEnabled, listToolCallInterval, null);
    }

    /**
     * TaskPlanningRail.
     * 
     * @param isProgressRepeatEnabled isProgressRepeatEnabled
     * @param listToolCallInterval listToolCallInterval
     * @param modelSelection modelSelection
     * @since 0.1.7
     */
    public TaskPlanningRail(boolean isProgressRepeatEnabled, int listToolCallInterval,
            Map<String, String> modelSelection) {
        this.isProgressRepeatEnabled = isProgressRepeatEnabled;
        this.listToolCallInterval = listToolCallInterval;
        if (modelSelection != null) {
            this.modelSelection.putAll(modelSelection);
        }
    }

    /**
     * priority.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Override
    public int priority() {
        return 90;
    }

    /**
     * init.
     * 
     * @param agent agent
     * @since 0.1.7
     */
    @Override
    public void init(Object agent) {
        if (!(agent instanceof DeepAgent deepAgent)) {
            return;
        }
        owner = deepAgent;
        String todoStorageType = deepAgent.getConfig().getTodoStorageType();
        TodoStorage todoStorage;
        if (TodoStorageFactory.hasProvider(todoStorageType)) {
            Map<String, Object> conf = new java.util.HashMap<>();
            if ("kv".equals(todoStorageType)) {
                BaseKVStore kvStore = deepAgent.getKvStore();
                if (kvStore != null) {
                    conf.put("kvStoreType", "shared");
                    conf.put("sharedKvStore", kvStore);
                } else {
                    Map<String, Object> kvConf = deepAgent.getConfig().getKvStoreConfig();
                    if (kvConf != null) {
                        conf.put("kvStoreConf", kvConf);
                    }
                }
            } else {
                conf.put("basePath", deepAgent.getWorkspace().root().resolve(".todo").toString());
            }
            todoStorage = TodoStorageFactory.create(todoStorageType, conf);
        } else {
            if ("kv".equals(todoStorageType)) {
                Loggers.TOOL.warning("todoStorageType is 'kv' but no provider registered, falling back to file storage");
            }
            todoStorage = new FileTodoStorage(deepAgent.getWorkspace().root().resolve(".todo"));
        }
        todoTool = new TodoTool(todoStorage);
        language = deepAgent.getWorkspace().getLanguage();
        tools.add(new LocalFunction(card("todo_create", deepAgent, language),
                inputs -> todoTool.create(sessionId(inputs), objectList(inputs.get("tasks")))));
        tools.add(
                new LocalFunction(card("todo_list", deepAgent, language), inputs -> todoTool.list(sessionId(inputs))));
        tools.add(new LocalFunction(card("todo_get", deepAgent, language),
                inputs -> todoTool.get(sessionId(inputs), string(inputs.get("id")))));
        tools.add(new LocalFunction(card("todo_modify", deepAgent, language),
                inputs -> todoTool.modify(sessionId(inputs), inputs)));
        for (Tool tool : tools) {
            deepAgent.registerHarnessTool(tool);
        }
    }

    /**
     * uninit.
     * 
     * @param agent agent
     * @since 0.1.7
     */
    @Override
    public void uninit(Object agent) {
        if (agent instanceof DeepAgent deepAgent) {
            for (Tool tool : tools) {
                deepAgent.unregisterHarnessTool(tool);
            }
            deepAgent.getAgent().getPromptBuilder().removeSection(TODO_SECTION);
        }
        tools.clear();
        owner = null;
        todoTool = null;
        toolCallCounts.clear();
        todosCache.clear();
        usageRecords.clear();
        defaultLlm = null;
        isDefaultLlmCaptured = false;
    }

    /**
     * isEnableProgressRepeat.
     * 
     * @return the result
     * @since 0.1.7
     */
    public boolean isEnableProgressRepeat() {
        return isProgressRepeatEnabled;
    }

    /**
     * getListToolCallInterval.
     * 
     * @return the result
     * @since 0.1.7
     */
    public int getListToolCallInterval() {
        return listToolCallInterval;
    }

    /**
     * getModelSelection.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Map<String, String> getModelSelection() {
        return new LinkedHashMap<>(modelSelection);
    }

    /**
     * getUsageRecords.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Map<String, ModelUsageRecord> getUsageRecords() {
        return new LinkedHashMap<>(usageRecords);
    }

    /**
     * cachedTodos.
     * 
     * @param sessionId sessionId
     * @return the result
     * @since 0.1.7
     */
    public List<TodoItem> cachedTodos(String sessionId) {
        List<TodoItem> todos = todosCache.get(sessionId);
        return todos == null ? List.of() : new ArrayList<>(todos);
    }

    /**
     * registeredToolNames.
     * 
     * @return the result
     * @since 0.1.7
     */
    public List<String> registeredToolNames() {
        return tools.stream().map(tool -> tool.getCard().getName()).toList();
    }

    /**
     * hasTodoPromptSection.
     * 
     * @return the result
     * @since 0.1.7
     */
    public boolean hasTodoPromptSection() {
        return owner != null && owner.getAgent().getPromptBuilder().hasSection(TODO_SECTION);
    }

    /**
     * toolCallCount.
     * 
     * @param sessionId sessionId
     * @return the result
     * @since 0.1.7
     */
    public int toolCallCount(String sessionId) {
        return toolCallCounts.getOrDefault(sessionId, 0);
    }

    /**
     * beforeModelCall.
     * 
     * @param ctx ctx
     * @since 0.1.7
     */
    @Override
    public void beforeModelCall(AgentCallbackContext ctx) {
        injectTodoPrompt(ctx);
        if (modelSelection.isEmpty() || todoTool == null || ctx == null || ctx.getSession() == null) {
            return;
        }
        if (!(ctx.getAgent() instanceof com.openjiuwen.core.singleagent.agents.ReActAgent reactAgent)) {
            return;
        }
        String sessionId = ctx.getSession().getSessionId();
        if (sessionId == null || sessionId.isBlank()) {
            return;
        }
        try {
            TodoItem inProgress =
                loadTodos(sessionId).stream().filter(item -> item != null && item.getStatus() == TodoStatus.IN_PROGRESS)
                        .findFirst().orElse(null);
            String modelId = inProgress != null ? inProgress.getSelectedModelId() : null;
            if (!isDefaultLlmCaptured) {
                defaultLlm = reactAgent.peekLlm();
                isDefaultLlmCaptured = true;
            }
            if (modelId == null || modelId.isBlank()) {
                reactAgent.setLlm(defaultLlm);
                if (ctx.getExtra() != null) {
                    ctx.getExtra().remove(TASK_PLANNING_MODEL_ID);
                }
                return;
            }
            if (!modelSelection.containsKey(modelId)) {
                return;
            }
            Object model = Runner.resourceMgr().getModel(modelId);
            if (model instanceof Model resolvedModel) {
                reactAgent.setLlm(resolvedModel);
                if (ctx.getExtra() != null) {
                    ctx.getExtra().put(TASK_PLANNING_MODEL_ID, modelId);
                }
            }
        } catch (RuntimeException | java.io.IOException ignored) {
            // Model selection is advisory; never fail the model call because todo state is unavailable.
        }
    }

    /**
     * afterModelCall.
     * 
     * @param ctx ctx
     * @since 0.1.7
     */
    @Override
    public void afterModelCall(AgentCallbackContext ctx) {
        if (modelSelection.isEmpty() || ctx == null || ctx.getInputs() == null) {
            return;
        }
        String modelId = ctx.getExtra() != null ? string(ctx.getExtra().get(TASK_PLANNING_MODEL_ID)) : "";
        if (modelId.isBlank()) {
            modelId = activeModelId(ctx);
        }
        if (modelId == null || modelId.isBlank()) {
            return;
        }
        UsageMetadata usage = responseUsage(ctx);
        if (usage == null) {
            return;
        }
        usageRecords.computeIfAbsent(modelId, id -> ModelUsageRecord.builder().modelId(id).build()).add(usage);
    }

    /**
     * taskPlanPath.
     * 
     * @param sessionId sessionId
     * @return the result
     * @since 0.1.7
     */
    public Path taskPlanPath(String sessionId) {
        String safeSession = sessionId == null || sessionId.isBlank() ? "default" : sessionId;
        return owner != null ? owner.getWorkspace().root().resolve(".task_plan").resolve(safeSession + ".json") : null;
    }

    /**
     * afterToolCall.
     * 
     * @param ctx ctx
     * @since 0.1.7
     */
    @Override
    public void afterToolCall(AgentCallbackContext ctx) {
        if (ctx == null || ctx.getSession() == null || todoTool == null) {
            return;
        }
        String sessionId = ctx.getSession().getSessionId();
        if (sessionId == null || sessionId.isBlank()) {
            return;
        }
        refreshTodosCacheAfterTodoToolCall(ctx, sessionId);
        if (!isProgressRepeatEnabled || ctx.getContext() == null) {
            return;
        }
        int count = toolCallCounts.getOrDefault(sessionId, 0) + 1;
        toolCallCounts.put(sessionId, count);
        if (listToolCallInterval <= 0 || count % listToolCallInterval != 0) {
            return;
        }
        List<TodoItem> todos;
        try {
            todos = loadTodos(sessionId);
        } catch (java.io.IOException ignored) {
            return;
        }
        if (todos == null || todos.isEmpty()) {
            return;
        }
        ctx.getContext().addMessages(new UserMessage(buildProgressReminder(todos)));
    }

    /**
     * afterInvoke.
     * 
     * @param ctx ctx
     * @since 0.1.7
     */
    @Override
    public void afterInvoke(AgentCallbackContext ctx) {
        if (ctx != null && ctx.getSession() != null && ctx.getSession().getSessionId() != null) {
            toolCallCounts.remove(ctx.getSession().getSessionId());
            todosCache.remove(ctx.getSession().getSessionId());
        }
        usageRecords.clear();
        defaultLlm = null;
        isDefaultLlmCaptured = false;
    }

    /**
     * afterTaskIteration.
     * 
     * @param ctx ctx
     * @since 0.1.7
     */
    @Override
    public void afterTaskIteration(TaskIterationContext ctx) {
        if (ctx == null || todoTool == null || owner == null) {
            return;
        }
        String sessionId = ctx.sessionId();
        if (sessionId == null || sessionId.isBlank()) {
            sessionId = "default";
        }
        try {
            TaskPlan plan = resolveTaskPlan(ctx);
            if (plan != null) {
                syncTodosFromTaskPlan(sessionId, plan);
            }
            List<TodoItem> todos = loadTodos(sessionId);
            writeTaskPlanSnapshot(TaskPlanSnapshot.from(ctx, todos));
        } catch (RuntimeException | java.io.IOException ignored) {
            // Task plan snapshots are best-effort and should never fail the task loop.
        }
    }

    /**
     * syncTodosFromTaskPlan.
     * 
     * @param sessionId sessionId
     * @param plan plan
     * @return the result
     * @throws java.io.IOException java.io.IOException
     * @since 0.1.7
     */
    public boolean syncTodosFromTaskPlan(String sessionId, TaskPlan plan) throws java.io.IOException {
        if (todoTool == null || plan == null || plan.getTasks() == null || plan.getTasks().isEmpty()) {
            return false;
        }
        List<TodoItem> todos = loadTodos(sessionId);
        if (todos == null || todos.isEmpty()) {
            return false;
        }
        Map<String, TodoStatus> statusByTaskId = new HashMap<>();
        Map<String, String> summaryByTaskId = new HashMap<>();
        for (TodoItem task : plan.getTasks()) {
            if (task == null || task.getId() == null) {
                continue;
            }
            statusByTaskId.put(task.getId(), task.getStatus());
            if (task.getResultSummary() != null) {
                summaryByTaskId.put(task.getId(), task.getResultSummary());
            }
        }
        boolean isChanged = false;
        for (TodoItem todo : todos) {
            if (todo == null || todo.getId() == null) {
                continue;
            }
            TodoStatus desired = statusByTaskId.get(todo.getId());
            if (desired != null && todo.getStatus() != desired) {
                todo.setStatus(desired);
                isChanged = true;
            }
            if (summaryByTaskId.containsKey(todo.getId())) {
                String desiredSummary = summaryByTaskId.get(todo.getId());
                if (!java.util.Objects.equals(todo.getResultSummary(), desiredSummary)) {
                    todo.setResultSummary(desiredSummary);
                    isChanged = true;
                }
            }
        }
        if (isChanged) {
            todoTool.save(sessionId, todos);
            todosCache.put(sessionId, new ArrayList<>(todos));
        }
        return isChanged;
    }

    /**
     * resolveTaskPlan.
     * 
     * @param ctx ctx
     * @return the result
     * @since 0.1.7
     */
    private TaskPlan resolveTaskPlan(TaskIterationContext ctx) {
        TaskPlan fromResult = TaskPlan.fromObject(firstNonNull(ctx.getResult(), new String[]{"task_plan", "taskPlan"}));
        if (fromResult != null) {
            return fromResult;
        }
        return TaskPlan.fromObject(firstNonNull(ctx.getInputs(), new String[]{"task_plan", "taskPlan"}));
    }

    /**
     * writeTaskPlanSnapshot.
     * 
     * @param snapshot snapshot
     * @throws java.io.IOException java.io.IOException
     * @since 0.1.7
     */
    public void writeTaskPlanSnapshot(TaskPlanSnapshot snapshot) throws java.io.IOException {
        if (snapshot == null || owner == null) {
            return;
        }
        Path file = taskPlanPath(snapshot.getSessionId());
        if (file == null) {
            return;
        }
        snapshot.save(file);
    }

    /**
     * loadTaskPlanSnapshot.
     * 
     * @param sessionId sessionId
     * @return the result
     * @throws java.io.IOException java.io.IOException
     * @since 0.1.7
     */
    public TaskPlanSnapshot loadTaskPlanSnapshot(String sessionId) throws java.io.IOException {
        Path file = taskPlanPath(sessionId);
        return TaskPlanSnapshot.load(file);
    }

    /**
     * loadPersistedTaskPlan.
     * 
     * @param sessionId sessionId
     * @return the result
     * @throws java.io.IOException java.io.IOException
     * @since 0.1.7
     */
    public TaskPlan loadPersistedTaskPlan(String sessionId) throws java.io.IOException {
        TaskPlanSnapshot snapshot = loadTaskPlanSnapshot(sessionId);
        if (snapshot == null) {
            return TaskPlan.builder().build();
        }
        TaskPlan plan = snapshot.toTaskPlan();
        if (plan.getTasks() == null || plan.getTasks().isEmpty()) {
            plan.setTasks(snapshot.getTodos() == null ? new ArrayList<>() : new ArrayList<>(snapshot.getTodos()));
        }
        return plan;
    }

    /**
     * buildProgressReminder.
     * 
     * @param todos todos
     * @return the result
     * @since 0.1.7
     */
    private String buildProgressReminder(List<TodoItem> todos) {
        StringBuilder tasks = new StringBuilder();
        String inProgressTask = "";
        for (TodoItem todo : todos) {
            if (todo == null) {
                continue;
            }
            if (todo.getStatus() == TodoStatus.IN_PROGRESS) {
                inProgressTask = todo.getContent() != null ? todo.getContent() : "";
            }
            tasks.append("id: ").append(todo.getId()).append(" |status: ").append(todo.getStatus())
                    .append(" |content: ").append(todo.getContent()).append('\n');
        }
        if ("en".equalsIgnoreCase(language)) {
            return "The following is the content and status of all tasks in the current task plan:\n\n" + tasks
                    + "\nThe task currently being executed is:\n\n" + inProgressTask
                    + "\n\nPlease review the above task progress to ensure the plan is being executed correctly.\n"
                    + "If any tasks are stuck or need adjustment, please update them promptly";
        }
        return "以下是当前任务规划中所有任务的内容和状态：\n\n" + tasks + "\n正在执行的任务为：\n\n" + inProgressTask
                + "\n\n请查看上述任务进度，确保计划正在正确执行。如果有任务卡住或需要调整，请及时更新";
    }

    /**
     * buildTodoPrompt.
     * 
     * @param language language
     * @return the result
     * @since 0.1.7
     */
    public String buildTodoPrompt(String language) {
        String prompt;
        if ("en".equalsIgnoreCase(language)) {
            prompt = """
                    Use the todo tools (todo_create, todo_modify, todo_list) to break down and manage your work.
                    These tools help track progress, organize complex tasks, and ensure all requirements are completed.

                    **When to create a task list — call todo_create immediately when:**
                    - User explicitly requests a todo list or provides multiple items to complete
                    - Task requires 3 or more distinct steps
                    - Task has planning nature (multi-step implementation, feature development, etc.)

                    Identify the planning need and call todo_create BEFORE starting execution.

                    **Task management rules:**
                    - Update status in real-time: call todo_modify the moment a task status changes
                    - Only one task can be in_progress at a time; complete it before starting the next
                    - Batch updates: consolidate multiple status changes into a single todo_modify call
                    - Cancel tasks that are no longer needed
                    - Can understand the current task planning progress by calling todo_list.

                    **Before marking a task completed:**
                    - Verify the work is fully done (e.g., run tests to confirm)
                    - Never mark completed if: partially implemented, tests failing, unresolved errors
                    - After completing, check if new follow-up tasks were discovered and append them via todo_modify
                    """;
        } else {
            prompt = """
                    使用 todo 工具（todo_create、todo_modify、todo_list）拆解和管理工作。这些工具用于跟踪进度、组织复杂任务，确保所有需求都被完成。

                    **何时创建任务列表 — 以下情况立即调用 todo_create：**
                    - 用户明确要求使用待办清单，或提供了多个待完成事项
                    - 任务需要 3 个或更多步骤
                    - 任务具有规划性质（多步骤实现、功能开发等）

                    **识别到规划需求后，在开始执行前立即调用 todo_create。**

                    **任务管理规则：**
                    - 实时更新状态：任务状态变化时立即调用 todo_modify
                    - 同一时间只能有一个任务处于 in_progress，完成后再开始下一个
                    - 批量更新：将多个状态变更合并为一次 todo_modify 调用
                    - 不再需要的任务用 todo_modify 标记为 cancelled
                    - 可通过调用 todo_list 了解当前任务规划进展

                    **将任务标记为已完成前：**
                    - 必须仔细验证工作已全部完成（如运行测试用例）
                    - 以下情况绝对不能标记为已完成：部分实现、测试失败、存在未解决的错误等
                    - 标记完成后，检查实现过程中是否发现新的后续任务，及时通过 todo_modify 追加
                    """;
        }
        return prompt.trim() + "\n" + buildModelSelectionPrompt(language);
    }

    /**
     * injectTodoPrompt.
     * 
     * @param ctx ctx
     * @since 0.1.7
     */
    private void injectTodoPrompt(AgentCallbackContext ctx) {
        if (owner == null) {
            return;
        }
        String prompt = buildTodoPrompt(language);
        owner.getAgent().addPromptBuilderSection(TODO_SECTION, prompt, TODO_SECTION_PRIORITY);
        if (ctx != null && ctx.getInputs() instanceof ModelCallInputs inputs) {
            injectTodoMessage(inputs, prompt);
        }
    }

    /**
     * buildModelSelectionPrompt.
     * 
     * @param language language
     * @return the result
     * @since 0.1.7
     */
    private String buildModelSelectionPrompt(String language) {
        if (modelSelection.isEmpty()) {
            if ("en".equalsIgnoreCase(language)) {
                return """

                        ## Model Selection Note

                        No model selection list is configured.
                        When creating and updating tasks, **do NOT use the selected_model_id field**.
                        All tasks will use the Agent's default model.
                        """.trim();
            }
            return """

                    ## 模型选择说明

                    当前未配置可选模型列表。创建和更新任务时，**不要使用 selected_model_id 字段**。
                    所有任务将使用 Agent 默认模型执行。
                    """.trim();
        }
        StringBuilder modelList = new StringBuilder();
        for (Map.Entry<String, String> entry : modelSelection.entrySet()) {
            modelList.append(" -selected_model_id: ").append(entry.getKey()).append(": ").append(entry.getValue())
                    .append('\n');
        }
        if ("en".equalsIgnoreCase(language)) {
            return """

                    ## Model Selection Strategy

                    Available models:
                    %s

                    Each model ID is configured by the user and maps to a specific model instance with a description.
                    The description explains the model's capability and best-fit scenarios. Use it as the primary
                    basis for selection.

                    ### Selection Principles
                    When creating subtasks, read each model's description and assign an appropriate model ID to
                    selected_model_id:
                    - Models described as suitable for simple tasks, low cost, or fast should be used for translation,
                      summarization, format conversion, and other tasks that do not require deep reasoning
                    - Models described as suitable for complex tasks, strong reasoning, or high quality should be used
                      for code generation, logical analysis, strategic planning, etc.
                    - Omit selected_model_id to use the Agent's default model

                    ### Quality Assurance
                    If a subtask produces poor results, use todo_modify to update that task's selected_model_id to a
                    model with a stronger description, then re-execute the task.
                    Do not proceed with downstream tasks that depend on low-quality results.
                    """.formatted(modelList.toString().trim()).trim();
        }
        return """

                ## 模型选择策略

                当前可用模型：
                %s

                每个模型 ID 由用户配置，对应一个具体的模型实例及其描述。描述说明了该模型的能力特点和适用场景，是你选择模型的主要依据。

                ### 选择原则
                创建子任务时，阅读每个模型的描述，根据任务复杂度为 selected_model_id 字段选择合适的模型 ID：
                - 描述中标注适合简单任务、成本低、速度快等的模型，用于翻译、摘要、格式转换等无需深度推理的任务
                - 描述中标注适合复杂任务、推理能力强、效果好等的模型，用于代码生成、逻辑分析、策略规划等任务
                - 不填则使用 Agent 默认模型

                ### 执行质量保障
                若某个子任务执行结果质量不佳，应通过 todo_modify 工具将该任务的 selected_model_id 修改为描述更强的模型 ID，然后重新执行该任务。
                不要在低质量结果上继续推进后续依赖任务。
                """.formatted(modelList.toString().trim()).trim();
    }

    /**
     * injectTodoMessage.
     * 
     * @param inputs inputs
     * @param prompt prompt
     * @since 0.1.7
     */
    private static void injectTodoMessage(ModelCallInputs inputs, String prompt) {
        List<Object> messages =
            inputs.getMessages() != null ? new ArrayList<>(inputs.getMessages()) : new ArrayList<>();
        for (Object message : messages) {
            if (message instanceof BaseMessage baseMessage && "system".equalsIgnoreCase(baseMessage.getRole())
                    && (String.valueOf(baseMessage.getContent()).contains("todo_create")
                            || String.valueOf(baseMessage.getContent()).contains("使用 todo 工具"))) {
                inputs.setMessages(messages);
                return;
            }
        }
        messages.add(0, new SystemMessage(prompt));
        inputs.setMessages(messages);
    }

    /**
     * loadTodos.
     * 
     * @param sessionId sessionId
     * @return the result
     * @throws java.io.IOException java.io.IOException
     * @since 0.1.7
     */
    private List<TodoItem> loadTodos(String sessionId) throws java.io.IOException {
        List<TodoItem> cached = todosCache.get(sessionId);
        if (cached != null) {
            return cached;
        }
        List<TodoItem> loaded = todoTool.load(sessionId);
        todosCache.put(sessionId, loaded);
        return loaded;
    }

    /**
     * refreshTodosCacheAfterTodoToolCall.
     * 
     * @param ctx ctx
     * @param sessionId sessionId
     * @since 0.1.7
     */
    private void refreshTodosCacheAfterTodoToolCall(AgentCallbackContext ctx, String sessionId) {
        if (!(ctx.getInputs() instanceof ToolCallInputs inputs)) {
            return;
        }
        String toolName = inputs.getToolName();
        if (toolName == null || !toolName.startsWith("todo_")) {
            return;
        }
        try {
            todosCache.put(sessionId, todoTool.load(sessionId));
        } catch (RuntimeException | java.io.IOException ignored) {
            // Cache refresh is best-effort; the next read can fall back to file loading.
            todosCache.remove(sessionId);
        }
    }

    /**
     * card.
     * 
     * @param name name
     * @param agent agent
     * @param language language
     * @return the result
     * @since 0.1.7
     */
    private static ToolCard card(String name, DeepAgent agent, String language) {
        return ToolMetadataRegistry.buildToolCard(name, agent.getCard().getId() + "." + name, language);
    }

    /**
     * sessionId.
     * 
     * @param inputs inputs
     * @return the result
     * @since 0.1.7
     */
    private static String sessionId(Map<String, Object> inputs) {
        Object value = inputs.get("session_id");
        return value != null && !String.valueOf(value).isBlank() ? String.valueOf(value) : "default";
    }

    /**
     * string.
     * 
     * @param value value
     * @return the result
     * @since 0.1.7
     */
    private static String string(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    /**
     * objectList.
     * 
     * @param value value
     * @return the result
     * @since 0.1.7
     */
    private static List<Object> objectList(Object value) {
        if (value instanceof List<?> list) {
            return new ArrayList<>(list);
        }
        if (value == null) {
            return List.of();
        }
        return List.of(value);
    }

    /**
     * firstNonNull.
     * 
     * @param source source
     * @param keys keys
     * @return the result
     * @since 0.1.7
     */
    private static Object firstNonNull(Map<String, Object> source, String[] keys) {
        if (source == null) {
            return null;
        }
        for (String key : keys) {
            Object value = source.get(key);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    /**
     * activeModelId.
     * 
     * @param ctx ctx
     * @return the result
     * @since 0.1.7
     */
    private String activeModelId(AgentCallbackContext ctx) {
        if (!(ctx.getAgent() instanceof com.openjiuwen.core.singleagent.agents.ReActAgent reactAgent)) {
            return "";
        }
        Model active = reactAgent.peekLlm();
        if (active == null) {
            return "";
        }
        for (String modelId : modelSelection.keySet()) {
            Object candidate = Runner.resourceMgr().getModel(modelId);
            if (Objects.equals(candidate, active)) {
                return modelId;
            }
        }
        return "";
    }

    /**
     * responseUsage.
     * 
     * @param ctx ctx
     * @return the result
     * @since 0.1.7
     */
    private static UsageMetadata responseUsage(AgentCallbackContext ctx) {
        if (!(ctx.getInputs() instanceof ModelCallInputs inputs)) {
            return null;
        }
        Object response = inputs.getResponse();
        if (response instanceof AssistantMessage assistantMessage) {
            return assistantMessage.getUsageMetadata();
        }
        if (response instanceof Map<?, ?> map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> typed = (Map<String, Object>) map;
            return TaskIterationContext.usageMetadataFrom(typed);
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    /**
     * mapList.
     * 
     * @param value value
     * @return the result
     * @since 0.1.7
     */
    private static List<Map<String, Object>> mapList(Object value) {
        if (value instanceof List<?> list) {
            List<Map<String, Object>> result = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    result.add((Map<String, Object>) map);
                }
            }
            return result;
        }
        if (value instanceof Map<?, ?> map) {
            return List.of((Map<String, Object>) map);
        }
        return List.of();
    }
}

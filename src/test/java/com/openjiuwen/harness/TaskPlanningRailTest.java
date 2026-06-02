/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.harness;

import com.openjiuwen.core.context.ContextStats;
import com.openjiuwen.core.context.ContextWindow;
import com.openjiuwen.core.context.ModelContext;
import com.openjiuwen.core.context.token.TokenCounter;
import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.foundation.llm.schema.UsageMetadata;
import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.foundation.tool.schema.ToolInfo;
import com.openjiuwen.core.session.Session;
import com.openjiuwen.core.single_agent.prompts.PromptSection;
import com.openjiuwen.core.single_agent.prompts.SystemPromptBuilder;
import com.openjiuwen.core.singleagent.AbilityManager;
import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.core.singleagent.rail.InvokeInputs;
import com.openjiuwen.core.singleagent.rail.ModelCallInputs;
import com.openjiuwen.core.singleagent.rail.ToolCallInputs;
import com.openjiuwen.harness.prompts.sections.TodoSection;
import com.openjiuwen.harness.rails.TaskPlanningRail;
import com.openjiuwen.harness.schema.DeepAgentState;
import com.openjiuwen.harness.schema.task.ModelUsageRecord;
import com.openjiuwen.harness.schema.task.TaskPlan;
import com.openjiuwen.harness.schema.task.TodoItem;
import com.openjiuwen.harness.schema.task.TodoStatus;
import com.openjiuwen.harness.workspace.Workspace;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for TaskPlanningRail init/uninit and lifecycle behavior.
 *
 * <p>Mirrors Python's {@code test_task_planning_rail} in
 * {@code tests.unit_tests.harness.test_task_planning_rail}.</p>
 */
@Tag("unit-test")
class TaskPlanningRailTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("init registers todo tools when workspace is set")
    void testInitRegistersToolsWithWorkspace() {
        TaskPlanningRail rail = new TaskPlanningRail();
        FakeAgent agent = makeAgent(new Workspace(tempDir.toString(), "en"));

        rail.init(agent);

        assertEquals(4, rail.getRegisteredTodoTools().size());
        assertSame(agent.config.getWorkspace(), rail.getWorkspace());
        assertNotNull(agent.abilityManager.get("todo_create"));
        assertNotNull(agent.abilityManager.get("todo_list"));
    }

    @Test
    @DisplayName("init registers tools even without explicit workspace")
    void testInitRegistersWithoutWorkspace() {
        TaskPlanningRail rail = new TaskPlanningRail();
        FakeAgent agent = makeAgent(null);

        rail.init(agent);

        assertEquals(4, rail.getRegisteredTodoTools().size());
        assertNotNull(rail.getWorkspace());
    }

    @Test
    @DisplayName("uninit is safe when no tools were registered")
    void testUninitSafeWithoutTools() {
        assertDoesNotThrow(() -> new TaskPlanningRail().uninit(makeAgent(null)));
    }

    @Test
    @DisplayName("uninit removes todo section from system prompt builder")
    void testUninitRemovesTodoSection() {
        TaskPlanningRail rail = new TaskPlanningRail();
        FakeAgent agent = makeAgent(new Workspace(tempDir.toString(), "en"));
        agent.systemPromptBuilder.addSection(TodoSection.build("en", null));
        assertTrue(agent.systemPromptBuilder.getSection("todo").isPresent());

        rail.init(agent);
        rail.uninit(agent);

        assertTrue(agent.systemPromptBuilder.getSection("todo").isEmpty());
        assertNull(agent.abilityManager.get("todo_create"));
    }

    @Test
    @DisplayName("TaskPlanningRail priority is 90")
    void testPriorityIs90() {
        assertEquals(90, new TaskPlanningRail().getPriority());
    }

    @Test
    @DisplayName("afterTaskIteration bridges TaskPlan status into todos")
    void testAfterTaskIterationBridgesTodos() {
        TaskPlanningRail rail = initializedRail();
        FakeSession session = new FakeSession("sess-bridge");
        session.updateState(Map.of("harness.todos", List.of(
                todoMap("task-id-a", "task-a", TodoStatus.PENDING, null),
                todoMap("task-id-b", "task-b", TodoStatus.PENDING, null),
                todoMap("task-id-c", "task-c", TodoStatus.PENDING, null)
        )));
        FakeAgent agent = makeAgent(new Workspace(tempDir.toString(), "en"));
        agent.state.setTaskPlan(new TaskPlan("test", List.of(
                todo("task-id-a", "task-a", TodoStatus.IN_PROGRESS, null),
                todo("task-id-b", "task-b", TodoStatus.PENDING, null),
                todo("task-id-c", "task-c", TodoStatus.PENDING, null)
        )));
        rail.init(agent);

        rail.afterTaskIteration(ctx(agent, session));

        assertEquals("in_progress", savedTodo(session, 0).get("status"));
    }

    @Test
    @DisplayName("afterTaskIteration syncs changed todo status from plan")
    void testAfterTaskIterationSyncsTodoStatusFromPlan() {
        TaskPlanningRail rail = initializedRail();
        FakeSession session = new FakeSession("sess-sync");
        session.updateState(Map.of("harness.todos", List.of(
                todoMap("task-id-a", "task-a", TodoStatus.IN_PROGRESS, null),
                todoMap("task-id-b", "task-b", TodoStatus.PENDING, null)
        )));
        FakeAgent agent = makeAgent(new Workspace(tempDir.toString(), "en"));
        agent.state.setTaskPlan(new TaskPlan("test", List.of(
                todo("task-id-a", "task-a", TodoStatus.COMPLETED, null),
                todo("task-id-b", "task-b", TodoStatus.PENDING, null)
        )));
        rail.init(agent);

        rail.afterTaskIteration(ctx(agent, session));

        assertEquals("completed", savedTodo(session, 0).get("status"));
        assertEquals("pending", savedTodo(session, 1).get("status"));
    }

    @Test
    @DisplayName("bridge skips when plan already matches todos")
    void testBridgeSkipsWhenPlanExists() {
        TaskPlanningRail rail = initializedRail();
        FakeSession session = new FakeSession("sess-existing");
        session.updateState(Map.of("harness.todos", List.of(todoMap("old", "old-task", TodoStatus.COMPLETED, null))));
        FakeAgent agent = makeAgent(new Workspace(tempDir.toString(), "en"));
        TaskPlan existingPlan = new TaskPlan("existing", List.of(todo("old", "old-task", TodoStatus.COMPLETED, null)));
        agent.state.setTaskPlan(existingPlan);
        rail.init(agent);

        rail.afterTaskIteration(ctx(agent, session));

        assertSame(existingPlan, agent.state.getTaskPlan());
        assertEquals("completed", savedTodo(session, 0).get("status"));
    }

    @Test
    @DisplayName("bridge skips when there are no todos")
    void testBridgeSkipsWhenNoTodos() {
        TaskPlanningRail rail = initializedRail();
        FakeAgent agent = makeAgent(new Workspace(tempDir.toString(), "en"));
        agent.state.setTaskPlan(new TaskPlan("test", List.of(todo("a", "a", TodoStatus.IN_PROGRESS, null))));

        assertDoesNotThrow(() -> rail.afterTaskIteration(ctx(agent, new FakeSession("sess-empty"))));
    }

    @Test
    @DisplayName("bridge skips when session is absent")
    void testBridgeSkipsWhenNoSession() {
        TaskPlanningRail rail = initializedRail();
        assertDoesNotThrow(() -> rail.afterTaskIteration(AgentCallbackContext.builder().agent(makeAgent(null)).build()));
    }

    @Test
    @DisplayName("bridge leaves existing plan untouched when no status changes are needed")
    void testBridgeSkipsWhenNoPending() {
        TaskPlanningRail rail = initializedRail();
        FakeSession session = new FakeSession("sess-no-pending");
        session.updateState(Map.of("harness.todos", List.of(
                todoMap("done-a", "done-a", TodoStatus.COMPLETED, null),
                todoMap("done-b", "done-b", TodoStatus.IN_PROGRESS, null)
        )));
        FakeAgent agent = makeAgent(new Workspace(tempDir.toString(), "en"));
        TaskPlan plan = new TaskPlan("existing", List.of(
                todo("done-a", "done-a", TodoStatus.COMPLETED, null),
                todo("done-b", "done-b", TodoStatus.IN_PROGRESS, null)
        ));
        agent.state.setTaskPlan(plan);
        rail.init(agent);

        rail.afterTaskIteration(ctx(agent, session));

        assertSame(plan, agent.state.getTaskPlan());
        assertEquals("in_progress", savedTodo(session, 1).get("status"));
    }

    @Test
    @DisplayName("bridge skips when todo tools are not registered")
    void testBridgeSkipsWhenNoTools() {
        TaskPlanningRail rail = new TaskPlanningRail();
        FakeAgent agent = makeAgent(null);
        FakeSession session = new FakeSession("sess-no-tools");
        session.updateState(Map.of("harness.todos", List.of(todoMap("a", "a", TodoStatus.PENDING, null))));
        agent.state.setTaskPlan(new TaskPlan("test", List.of(todo("a", "a", TodoStatus.COMPLETED, null))));

        rail.afterTaskIteration(ctx(agent, session));

        assertEquals("pending", savedTodo(session, 0).get("status"));
    }

    @Test
    @DisplayName("beforeModelCall adds todo prompt section")
    void testBeforeModelCallAddsSection() {
        TaskPlanningRail rail = initializedRail();
        FakeAgent agent = makeAgent(new Workspace(tempDir.toString(), "en"));
        rail.init(agent);

        rail.beforeModelCall(ctx(agent, new FakeSession("sess-prompt")));

        assertTrue(agent.systemPromptBuilder.getSection("todo").isPresent());
    }

    @Test
    @DisplayName("beforeModelCall returns safely without prompt builder")
    void testBeforeModelCallWithoutPromptBuilder() {
        TaskPlanningRail rail = new TaskPlanningRail();
        FakeAgent agent = makeAgent(null);
        agent.systemPromptBuilder = null;

        assertDoesNotThrow(() -> rail.beforeModelCall(ctx(agent, new FakeSession("sess-no-builder"))));
    }

    @Test
    @DisplayName("afterToolCall injects progress reminder at interval")
    void testAfterToolCallInjectsProgressReminder() {
        TaskPlanningRail rail = new TaskPlanningRail(true, 1);
        FakeAgent agent = makeAgent(new Workspace(tempDir.toString(), "en"));
        rail.init(agent);
        FakeSession session = new FakeSession("sess-reminder");
        session.updateState(Map.of("harness.todos", List.of(
                todoMap("a", "task-a", TodoStatus.PENDING, null),
                todoMap("b", "task-b", TodoStatus.IN_PROGRESS, null)
        )));
        FakeModelContext modelContext = new FakeModelContext();

        rail.afterToolCall(toolCtx(agent, session, modelContext, "todo_create"));

        assertEquals(1, rail.getToolCallCounts().get("sess-reminder"));
        assertEquals(1, modelContext.messages.size());
        assertInstanceOf(com.openjiuwen.core.foundation.llm.schema.UserMessage.class, modelContext.messages.get(0));
    }

    @Test
    @DisplayName("afterToolCall counts all tool calls when repeat is enabled")
    void testAfterToolCallCountsAllTools() {
        TaskPlanningRail rail = new TaskPlanningRail(true, 20);
        FakeAgent agent = makeAgent(new Workspace(tempDir.toString(), "en"));
        rail.init(agent);

        rail.afterToolCall(toolCtx(agent, new FakeSession("sess-count"), new FakeModelContext(), "shell"));

        assertEquals(1, rail.getToolCallCounts().get("sess-count"));
    }

    @Test
    @DisplayName("afterInvoke removes per-session tool call count")
    void testAfterInvokeRemovesToolCallCount() {
        TaskPlanningRail rail = new TaskPlanningRail(true, 20);
        FakeAgent agent = makeAgent(new Workspace(tempDir.toString(), "en"));
        rail.init(agent);
        FakeSession session = new FakeSession("sess-clean-count");
        rail.afterToolCall(toolCtx(agent, session, new FakeModelContext(), "todo_create"));
        assertTrue(rail.getToolCallCounts().containsKey("sess-clean-count"));

        rail.afterInvoke(ctx(agent, session));

        assertFalse(rail.getToolCallCounts().containsKey("sess-clean-count"));
    }

    @Test
    @DisplayName("afterToolCall respects custom interval")
    void testAfterToolCallCustomInterval() {
        TaskPlanningRail rail = new TaskPlanningRail(true, 3);
        FakeAgent agent = makeAgent(new Workspace(tempDir.toString(), "en"));
        rail.init(agent);
        FakeSession session = new FakeSession("sess-custom-interval");
        session.updateState(Map.of("harness.todos", List.of(todoMap("a", "task-a", TodoStatus.PENDING, null))));
        FakeModelContext modelContext = new FakeModelContext();

        rail.afterToolCall(toolCtx(agent, session, modelContext, "todo_create"));
        rail.afterToolCall(toolCtx(agent, session, modelContext, "todo_list"));
        assertTrue(modelContext.messages.isEmpty());
        rail.afterToolCall(toolCtx(agent, session, modelContext, "todo_get"));

        assertEquals(3, rail.getToolCallCounts().get("sess-custom-interval"));
        assertEquals(1, modelContext.messages.size());
    }

    @Test
    @DisplayName("afterToolCall skips when progress repeat is disabled")
    void testAfterToolCallSkipsWhenDisabled() {
        TaskPlanningRail rail = initializedRail();
        FakeAgent agent = makeAgent(new Workspace(tempDir.toString(), "en"));
        rail.init(agent);
        FakeModelContext context = new FakeModelContext();

        rail.afterToolCall(toolCtx(agent, new FakeSession("sess-disabled"), context, "todo_create"));

        assertFalse(rail.getToolCallCounts().containsKey("sess-disabled"));
        assertTrue(context.messages.isEmpty());
    }

    @Test
    @DisplayName("afterInvoke is safe when session is absent")
    void testAfterInvokeSafeWithoutSession() {
        assertDoesNotThrow(() -> new TaskPlanningRail(true).afterInvoke(AgentCallbackContext.builder().build()));
    }

    @Test
    @DisplayName("formatTaskContent extracts in-progress task")
    void testFormatTaskContentWithInProgress() {
        TaskPlanningRail.FormattedTaskContent formatted = new TaskPlanningRail().formatTaskContent(List.of(
                todo("a", "task-a", TodoStatus.PENDING, null),
                todo("b", "task-b", TodoStatus.IN_PROGRESS, null),
                todo("c", "task-c", TodoStatus.COMPLETED, null)
        ));

        assertEquals("task-b", formatted.inProgressTask());
        assertTrue(formatted.tasks().contains("task-a"));
        assertTrue(formatted.tasks().contains("task-b"));
        assertTrue(formatted.tasks().contains("task-c"));
    }

    @Test
    @DisplayName("formatTaskContent returns empty in-progress task when none")
    void testFormatTaskContentWithoutInProgress() {
        TaskPlanningRail.FormattedTaskContent formatted = new TaskPlanningRail().formatTaskContent(List.of(
                todo("a", "task-a", TodoStatus.PENDING, null),
                todo("b", "task-b", TodoStatus.COMPLETED, null)
        ));

        assertEquals("", formatted.inProgressTask());
        assertTrue(formatted.tasks().contains("task-a"));
        assertTrue(formatted.tasks().contains("task-b"));
    }

    @Test
    @DisplayName("TaskPlan tasks hold TodoItem instances directly")
    void testTaskPlanUsesTodoItem() {
        TaskPlan plan = new TaskPlan("test", List.of(
                todo("a", "task-a", TodoStatus.PENDING, null),
                todo("b", "task-b", TodoStatus.IN_PROGRESS, null)
        ));

        assertEquals(2, plan.getTasks().size());
        assertInstanceOf(TodoItem.class, plan.getTasks().get(0));
        assertEquals(TodoStatus.IN_PROGRESS, plan.getTasks().get(1).getStatus());
    }

    @Test
    @DisplayName("enableProgressRepeat defaults to false")
    void testEnableProgressRepeatDefault() {
        assertFalse(new TaskPlanningRail().isEnableProgressRepeat());
    }

    @Test
    @DisplayName("enableProgressRepeat can be enabled")
    void testEnableProgressRepeatTrue() {
        assertTrue(new TaskPlanningRail(true).isEnableProgressRepeat());
    }

    @Test
    @DisplayName("listToolCallInterval defaults to 20")
    void testListToolCallIntervalDefault() {
        assertEquals(20, new TaskPlanningRail().getListToolCallInterval());
    }

    @Test
    @DisplayName("buildTodoSystemPrompt returns Chinese task prompt")
    void testBuildTodoSystemPromptChinese() {
        String prompt = TodoSection.buildTodoSystemPrompt("cn");
        assertFalse(prompt.isBlank());
        assertTrue(prompt.contains("todo_create"));
    }

    @Test
    @DisplayName("buildTodoSystemPrompt returns English task prompt")
    void testBuildTodoSystemPromptEnglish() {
        String prompt = TodoSection.buildTodoSystemPrompt("en");
        assertTrue(prompt.toLowerCase().contains("task planning") || prompt.toLowerCase().contains("todo"));
    }

    @Test
    @DisplayName("buildProgressReminderUserPrompt returns Chinese prompt")
    void testBuildProgressReminderUserPromptChinese() {
        String prompt = TodoSection.buildProgressReminderUserPrompt("cn", "tasks", "task-a");
        assertTrue(prompt.contains("tasks"));
        assertTrue(prompt.contains("task-a"));
    }

    @Test
    @DisplayName("buildProgressReminderUserPrompt returns English prompt")
    void testBuildProgressReminderUserPromptEnglish() {
        String prompt = TodoSection.buildProgressReminderUserPrompt("en", "", "");
        assertTrue(prompt.contains("ensure the plan is being executed correctly"));
    }

    @Test
    @DisplayName("progress reminder includes English task content")
    void testBuildProgressReminderUserPromptWithTaskContent() {
        String tasks = "id: 1 |status: pending |content: task-a\nid: 2 |status: in_progress |content: task-b";
        String prompt = TodoSection.buildProgressReminderUserPrompt("en", tasks, "task-b");
        assertTrue(prompt.contains(tasks));
        assertTrue(prompt.contains("task-b"));
        assertTrue(prompt.contains("currently being executed"));
    }

    @Test
    @DisplayName("progress reminder includes Chinese task content")
    void testBuildProgressReminderUserPromptWithTaskContentChinese() {
        String tasks = "id: 1 |status: pending |content: task-a";
        String prompt = TodoSection.buildProgressReminderUserPrompt("cn", tasks, "task-a");
        assertTrue(prompt.contains(tasks));
        assertTrue(prompt.contains("task-a"));
    }

    @Test
    @DisplayName("model selection defaults to empty")
    void testModelSelectionDefaultIsNone() {
        assertTrue(new TaskPlanningRail().getModelSelection().isEmpty());
    }

    @Test
    @DisplayName("model selection is stored on init")
    void testModelSelectionStoredOnInit() {
        Model fast = mockModel("fast");
        Model smart = mockModel("smart");
        TaskPlanningRail rail = new TaskPlanningRail(Map.of(fast, "cheap model", smart, "premium model"));

        assertEquals(2, rail.getModelSelection().size());
        assertSame(fast, rail.getModelIdToModel().get("fast"));
        assertSame(smart, rail.getModelIdToModel().get("smart"));
    }

    @Test
    @DisplayName("beforeModelCall switches model for in-progress selected_model_id")
    void testBeforeModelCallSwitchesModelForInProgressTask() {
        Model fast = mockModel("fast");
        Model smart = mockModel("smart");
        TaskPlanningRail rail = new TaskPlanningRail(Map.of(fast, "cheap model", smart, "premium model"));
        FakeAgent agent = makeAgent(new Workspace(tempDir.toString(), "en"));
        agent.llm = smart;
        FakeSession session = new FakeSession("sess-model-switch");
        session.updateState(Map.of("harness.todos", List.of(
                todoMap("a", "task-a", TodoStatus.IN_PROGRESS, "fast"),
                todoMap("b", "task-b", TodoStatus.PENDING, null)
        )));

        rail.init(agent);
        rail.beforeModelCall(ctx(agent, session));

        assertSame(fast, agent.lastSetLlm);
    }

    @Test
    @DisplayName("beforeModelCall restores default model when no selected_model_id")
    void testBeforeModelCallRestoresDefaultWhenNoModelId() {
        Model fast = mockModel("fast");
        Model defaultModel = mockModel("default");
        TaskPlanningRail rail = new TaskPlanningRail(Map.of(fast, "cheap model"));
        FakeAgent agent = makeAgent(new Workspace(tempDir.toString(), "en"));
        agent.llm = defaultModel;
        FakeSession session = new FakeSession("sess-default-model");
        session.updateState(Map.of("harness.todos", List.of(todoMap("a", "task-a", TodoStatus.IN_PROGRESS, null))));

        rail.init(agent);
        rail.beforeModelCall(ctx(agent, session));

        assertSame(defaultModel, agent.lastSetLlm);
    }

    @Test
    @DisplayName("beforeModelCall does not switch when model selection is empty")
    void testBeforeModelCallNoSwitchWhenModelSelectionEmpty() {
        TaskPlanningRail rail = new TaskPlanningRail();
        FakeAgent agent = makeAgent(new Workspace(tempDir.toString(), "en"));
        agent.llm = mockModel("default");
        rail.init(agent);

        rail.beforeModelCall(ctx(agent, new FakeSession("sess-no-switch")));

        assertNull(agent.lastSetLlm);
    }

    @Test
    @DisplayName("afterModelCall accumulates token usage")
    void testAfterModelCallAccumulatesUsage() {
        Model fast = mockModel("fast");
        TaskPlanningRail rail = new TaskPlanningRail(Map.of(fast, "cheap model"));
        FakeAgent agent = makeAgent(new Workspace(tempDir.toString(), "en"));
        agent.llm = fast;

        rail.afterModelCall(ctxWithUsage(agent, 100, 50));
        rail.afterModelCall(ctxWithUsage(agent, 100, 50));

        assertEquals(200, rail.getUsageRecords().get("fast").getInputTokens());
        assertEquals(100, rail.getUsageRecords().get("fast").getOutputTokens());
    }

    @Test
    @DisplayName("afterInvoke resets usage records")
    void testAfterInvokeResetsUsageRecords() {
        TaskPlanningRail rail = new TaskPlanningRail();
        rail.putUsageRecord("fast", new ModelUsageRecord("fast", 200, 100));

        rail.afterInvoke(ctx(makeAgent(null), new FakeSession("sess-usage-reset")));

        assertTrue(rail.getUsageRecords().isEmpty());
    }

    @Test
    @DisplayName("todo section with model selection injects model prompt")
    void testBuildTodoSectionWithModelSelectionInjectsPrompt() {
        Model fast = mockModel("fast");
        String modelList = new TaskPlanningRail(Map.of(fast, "cheap model")).buildModelList();
        PromptSection section = TodoSection.build("en", modelList);
        String content = section.getContent().get("en");

        assertTrue(content.contains("fast"));
        assertTrue(content.contains("Model Selection"));
    }

    @Test
    @DisplayName("todo section without model selection warns selected_model_id should not be used")
    void testBuildTodoSectionWithoutModelSelectionNoModelPrompt() {
        PromptSection section = TodoSection.build("en", null);
        String content = section.getContent().get("en");

        assertTrue(content.contains("Model Selection Note"));
        assertTrue(content.contains("do NOT use the selected_model_id field"));
    }

    private TaskPlanningRail initializedRail() {
        return new TaskPlanningRail();
    }

    private FakeAgent makeAgent(Workspace workspace) {
        FakeAgent agent = new FakeAgent();
        agent.config.setWorkspace(workspace);
        return agent;
    }

    private static AgentCallbackContext ctx(FakeAgent agent, Session session) {
        return AgentCallbackContext.builder().agent(agent).session(session).build();
    }

    private static AgentCallbackContext toolCtx(FakeAgent agent, Session session, ModelContext modelContext, String toolName) {
        return AgentCallbackContext.builder()
                .agent(agent)
                .session(session)
                .context(modelContext)
                .inputs(ToolCallInputs.builder().toolName(toolName).build())
                .build();
    }

    private static AgentCallbackContext ctxWithUsage(FakeAgent agent, int inputTokens, int outputTokens) {
        UsageMetadata usageMetadata = UsageMetadata.builder()
                .inputTokens(inputTokens)
                .outputTokens(outputTokens)
                .build();
        AssistantMessage response = new AssistantMessage("ok");
        response.setUsageMetadata(usageMetadata);
        ModelCallInputs inputs = new ModelCallInputs();
        inputs.setResponse(response);
        return AgentCallbackContext.builder().agent(agent).inputs(inputs).build();
    }

    private static TodoItem todo(String id, String content, TodoStatus status, String selectedModelId) {
        return new TodoItem(id, content, "Executing " + content, "", status, List.of(), null, Map.of(), selectedModelId);
    }

    private static Map<String, Object> todoMap(String id, String content, TodoStatus status, String selectedModelId) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", id);
        row.put("content", content);
        row.put("activeForm", "Executing " + content);
        row.put("description", "");
        row.put("status", status.getValue());
        row.put("depends_on", List.of());
        row.put("selected_model_id", selectedModelId);
        return row;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> savedTodo(FakeSession session, int index) {
        return (Map<String, Object>) ((List<?>) session.getState("harness.todos")).get(index);
    }

    private static Model mockModel(String clientId) {
        return new Model(
                ModelClientConfig.builder()
                        .clientProvider("OpenAI")
                        .clientId(clientId)
                        .apiKey("mock-key")
                        .apiBase("https://example.invalid/v1")
                        .verifySsl(false)
                        .build(),
                ModelRequestConfig.builder().modelName("mock-model").build());
    }

    private static final class FakeAgent {
        private final AbilityManager abilityManager = new AbilityManager();
        private final DeepAgentConfig config = new DeepAgentConfig();
        private final DeepAgentState state = new DeepAgentState();
        private SystemPromptBuilder systemPromptBuilder = new SystemPromptBuilder("en");
        private Model llm;
        private Model lastSetLlm;

        public AbilityManager getAbilityManager() {
            return abilityManager;
        }

        public DeepAgentConfig getConfig() {
            return config;
        }

        public SystemPromptBuilder getSystemPromptBuilder() {
            return systemPromptBuilder;
        }

        public DeepAgentState loadState(Session session) {
            return state;
        }

        public void setLlm(Model model) {
            lastSetLlm = model;
            llm = model;
        }
    }

    private static final class FakeSession implements Session {
        private final String sessionId;
        private final Map<String, Object> state = new LinkedHashMap<>();

        private FakeSession(String sessionId) {
            this.sessionId = sessionId;
        }

        @Override
        public String getSessionId() {
            return sessionId;
        }

        @Override
        public Object getState(String key) {
            return state.get(key);
        }

        @Override
        public void updateState(Map<String, Object> update) {
            state.putAll(update);
        }
    }

    private static final class FakeModelContext extends ModelContext {
        private final List<BaseMessage> messages = new ArrayList<>();

        @Override
        public int size() {
            return messages.size();
        }

        @Override
        public List<BaseMessage> getMessages(Integer size, boolean withHistory) {
            return new ArrayList<>(messages);
        }

        @Override
        public void setMessages(List<BaseMessage> messages, boolean withHistory) {
            this.messages.clear();
            this.messages.addAll(messages);
        }

        @Override
        public List<BaseMessage> popMessages(int size, boolean withHistory) {
            return List.of();
        }

        @Override
        public void clearMessages(boolean withHistory) {
            messages.clear();
        }

        @Override
        public List<BaseMessage> addMessages(List<BaseMessage> messages) {
            this.messages.addAll(messages);
            return this.messages;
        }

        @Override
        public ContextWindow getContextWindow(List<BaseMessage> systemMessages, List<ToolInfo> tools,
                                              Integer windowSize, Integer dialogueRound, Map<String, Object> kwargs) {
            return null;
        }

        @Override
        public ContextStats statistic() {
            return null;
        }

        @Override
        public String sessionId() {
            return "fake-session";
        }

        @Override
        public String contextId() {
            return "fake-context";
        }

        @Override
        public TokenCounter tokenCounter() {
            return null;
        }

        @Override
        public Tool reloaderTool() {
            return null;
        }
    }
}

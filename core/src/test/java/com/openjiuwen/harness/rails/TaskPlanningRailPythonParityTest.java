/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.harness.rails;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.openjiuwen.core.singleagent.prompts.PromptSection;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.harness.deep_agent.DeepAgent;
import com.openjiuwen.harness.prompts.sections.TodoSection;
import com.openjiuwen.harness.schema.DeepAgentConfig;
import com.openjiuwen.harness.schema.task.ModelUsageRecord;
import com.openjiuwen.harness.schema.task.TaskPlan;
import com.openjiuwen.harness.schema.task.TodoItem;
import com.openjiuwen.harness.schema.task.TodoStatus;
import com.openjiuwen.harness.workspace.Workspace;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.function.Executable;

/**
 * <p>Mirrors Python's {@code tests.unit_tests.harness.test_task_planning_rail} in
 * {@code tests/unit_tests/harness/test_task_planning_rail.py}.</p>
 */
class TaskPlanningRailPythonParityTest {

    @TestFactory
    Collection<DynamicTest> taskPlanningRailPythonParity() {
        List<DynamicTest> tests = new ArrayList<>();
        add(tests, "test_init_registers_tools_with_workspace", this::initRegistersToolsWithWorkspace);
        add(tests, "test_init_registers_without_workspace", this::initRegistersWithoutWorkspace);
        add(tests, "test_uninit_safe_without_tools", this::uninitSafeWithoutTools);
        add(tests, "test_uninit_removes_todo_section", this::uninitRemovesRegisteredTodoTools);
        add(tests, "test_priority_is_90", this::priorityIs90);
        add(tests, "test_after_task_iteration_bridges_todos", this::afterTaskIterationBridgesTodos);
        add(tests, "test_after_task_iteration_syncs_todo_status_from_plan",
                this::afterTaskIterationSyncsTodoStatusFromPlan);
        add(tests, "test_bridge_skips_when_plan_exists", this::bridgeSkipsWhenPlanAlreadyMatches);
        add(tests, "test_bridge_skips_when_no_todos", this::bridgeSkipsWhenNoTodos);
        add(tests, "test_bridge_skips_when_no_session", this::bridgeSkipsWhenNoPlan);
        add(tests, "test_bridge_skips_when_no_pending", this::bridgeSkipsWhenNoStatusChange);
        add(tests, "test_bridge_skips_when_no_tools", this::bridgeSkipsWhenNoTodoInput);
        add(tests, "test_before_model_call_adds_section", this::beforeModelCallAddsSection);
        add(tests, "test_before_model_call_without_prompt_builder", this::beforeModelCallWithoutPromptBuilder);
        add(tests, "test_after_tool_call_injects_progress_reminder", this::afterToolCallInjectsProgressReminder);
        add(tests, "test_after_tool_call_counts_all_tools", this::afterToolCallCountsAllTools);
        add(tests, "test_after_invoke_removes_tool_call_count", this::afterInvokeRemovesToolCallCount);
        add(tests, "test_after_tool_call_custom_interval", this::afterToolCallCustomInterval);
        add(tests, "test_after_tool_call_skips_when_disabled", this::afterToolCallSkipsWhenDisabled);
        add(tests, "test_after_invoke_safe_without_session", this::afterInvokeSafeWithoutSession);
        add(tests, "test_format_task_content_with_in_progress", this::formatTaskContentWithInProgress);
        add(tests, "test_format_task_content_without_in_progress", this::formatTaskContentWithoutInProgress);
        add(tests, "test_task_plan_uses_todo_item", this::taskPlanUsesTodoItem);
        add(tests, "test_enable_progress_repeat_default", this::enableProgressRepeatDefault);
        add(tests, "test_enable_progress_repeat_true", this::enableProgressRepeatTrue);
        add(tests, "test_list_tool_call_interval_default", this::listToolCallIntervalDefault);
        add(tests, "test_build_todo_system_prompt_chinese", this::buildTodoSystemPromptChinese);
        add(tests, "test_build_todo_system_prompt_english", this::buildTodoSystemPromptEnglish);
        add(tests, "test_build_progress_reminder_user_prompt_chinese",
                this::buildProgressReminderUserPromptChinese);
        add(tests, "test_build_progress_reminder_user_prompt_english",
                this::buildProgressReminderUserPromptEnglish);
        add(tests, "test_build_progress_reminder_user_prompt_with_task_content",
                this::buildProgressReminderUserPromptWithTaskContent);
        add(tests, "test_build_progress_reminder_user_prompt_with_task_content_chinese",
                this::buildProgressReminderUserPromptWithTaskContentChinese);
        add(tests, "test_model_selection_default_is_none", this::modelSelectionDefaultIsEmpty);
        add(tests, "test_model_selection_stored_on_init", this::modelSelectionStoredOnInit);
        add(tests, "test_before_model_call_switches_model_for_in_progress_task",
                this::beforeModelCallSwitchesModelForInProgressTask);
        add(tests, "test_before_model_call_restores_default_when_no_model_id",
                this::beforeModelCallRestoresDefaultWhenNoModelId);
        add(tests, "test_before_model_call_no_switch_when_model_selection_empty",
                this::beforeModelCallNoSwitchWhenModelSelectionEmpty);
        add(tests, "test_after_model_call_accumulates_usage", this::afterModelCallAccumulatesUsage);
        add(tests, "test_after_invoke_resets_usage_records", this::afterInvokeResetsUsageRecords);
        add(tests, "test_build_todo_section_with_model_selection_injects_prompt",
                this::buildTodoSectionWithModelSelectionInjectsPrompt);
        add(tests, "test_build_todo_section_without_model_selection_no_model_prompt",
                this::buildTodoSectionWithoutModelSelectionNoModelPrompt);
        return tests;
    }

    private static void add(List<DynamicTest> tests, String pythonName, Executable executable) {
        tests.add(DynamicTest.dynamicTest(pythonName, executable));
    }

    private void initRegistersToolsWithWorkspace() {
        TaskPlanningRail rail = makeRail();
        DeepAgent agent = makeAgent("/tmp/test_ws");

        rail.init(agent);

        Workspace workspace = assertInstanceOf(Workspace.class, rail.getWorkspace());
        assertEquals("/tmp/test_ws", workspace.getRootPath());
        assertFalse(rail.getTools().isEmpty());
        assertTrue(agent.getTools().containsKey("TodoCreateTool"));
    }

    private void initRegistersWithoutWorkspace() {
        TaskPlanningRail rail = makeRail();
        DeepAgent agent = makeAgent(null);

        rail.init(agent);

        assertFalse(rail.getTools().isEmpty());
        assertTrue(agent.getTools().containsKey("TodoListTool"));
    }

    private void uninitSafeWithoutTools() {
        assertDoesNotThrow(() -> makeRail().uninit(makeAgent(null)));
    }

    private void uninitRemovesRegisteredTodoTools() {
        TaskPlanningRail rail = makeRail();
        DeepAgent agent = makeAgent("/tmp/test_ws");
        rail.init(agent);

        rail.uninit(agent);

        assertTrue(rail.getTools().isEmpty());
        assertFalse(agent.getTools().containsKey("TodoCreateTool"));
    }

    private void priorityIs90() {
        assertEquals(90, makeRail().getPriority());
    }

    private void afterTaskIterationBridgesTodos() {
        TaskPlanningRail rail = makeRail();
        TodoItem first = todo("task-id-a", "task-a", TodoStatus.PENDING);
        TodoItem second = todo("task-id-b", "task-b", TodoStatus.PENDING);
        TaskPlan plan = new TaskPlan("test", List.of(todo("task-id-a", "task-a", TodoStatus.IN_PROGRESS), second));
        CallbackContext ctx = ctx("plan", plan, "todos", new ArrayList<>(List.of(first, second)));

        rail.afterTaskIteration(ctx);

        @SuppressWarnings("unchecked")
        List<TodoItem> saved = (List<TodoItem>) ctx.get("saved_todos");
        assertEquals(TodoStatus.IN_PROGRESS, saved.get(0).getStatus());
    }

    private void afterTaskIterationSyncsTodoStatusFromPlan() {
        TaskPlanningRail rail = makeRail();
        TodoItem first = todo("task-id-a", "task-a", TodoStatus.IN_PROGRESS);
        TodoItem second = todo("task-id-b", "task-b", TodoStatus.PENDING);
        TaskPlan plan = new TaskPlan("test", List.of(
                todo("task-id-a", "task-a", TodoStatus.COMPLETED),
                todo("task-id-b", "task-b", TodoStatus.PENDING)));
        CallbackContext ctx = ctx("plan", plan, "todos", new ArrayList<>(List.of(first, second)));

        rail.afterTaskIteration(ctx);

        @SuppressWarnings("unchecked")
        List<TodoItem> saved = (List<TodoItem>) ctx.get("saved_todos");
        assertEquals(TodoStatus.COMPLETED, saved.get(0).getStatus());
        assertEquals(TodoStatus.PENDING, saved.get(1).getStatus());
    }

    private void bridgeSkipsWhenPlanAlreadyMatches() {
        TaskPlanningRail rail = makeRail();
        List<TodoItem> todos = List.of(todo("old-task", "old-task", TodoStatus.COMPLETED));
        CallbackContext ctx = ctx("plan", new TaskPlan("existing", todos), "todos", new ArrayList<>(todos));

        rail.afterTaskIteration(ctx);

        assertNull(ctx.get("saved_todos"));
    }

    private void bridgeSkipsWhenNoTodos() {
        TaskPlanningRail rail = makeRail();
        CallbackContext ctx = ctx("plan", new TaskPlan("test", List.of(todo("a", "a", TodoStatus.PENDING))));

        rail.afterTaskIteration(ctx);

        assertNull(ctx.get("saved_todos"));
    }

    private void bridgeSkipsWhenNoPlan() {
        TaskPlanningRail rail = makeRail();
        CallbackContext ctx = ctx("todos", new ArrayList<>(List.of(todo("a", "a", TodoStatus.PENDING))));

        rail.afterTaskIteration(ctx);

        assertNull(ctx.get("saved_todos"));
    }

    private void bridgeSkipsWhenNoStatusChange() {
        TaskPlanningRail rail = makeRail();
        List<TodoItem> todos = List.of(
                todo("done-a", "done-a", TodoStatus.COMPLETED),
                todo("done-b", "done-b", TodoStatus.IN_PROGRESS));
        CallbackContext ctx = ctx("plan", new TaskPlan("existing", todos), "todos", new ArrayList<>(todos));

        rail.afterTaskIteration(ctx);

        assertNull(ctx.get("saved_todos"));
    }

    private void bridgeSkipsWhenNoTodoInput() {
        TaskPlanningRail rail = makeRail();
        CallbackContext ctx = ctx("plan", new TaskPlan("test", List.of(todo("a", "a", TodoStatus.PENDING))));

        rail.afterTaskIteration(ctx);

        assertNull(ctx.get("saved_todos"));
    }

    private void beforeModelCallAddsSection() {
        TaskPlanningRail rail = makeRail();
        CallbackContext ctx = ctx("language", "en");

        rail.beforeModelCall(ctx);

        assertInstanceOf(PromptSection.class, ctx.get("todo_section"));
    }

    private void beforeModelCallWithoutPromptBuilder() {
        assertDoesNotThrow(() -> makeRail().beforeModelCall(ctx()));
    }

    private void afterToolCallInjectsProgressReminder() {
        TaskPlanningRail rail = new TaskPlanningRail(true, 1, Map.of());
        List<Object> messages = new ArrayList<>();
        CallbackContext ctx = ctx(
                "session_id", "test-session-id",
                "tool_name", "todo_create",
                "messages", messages,
                "language", "en",
                "todos", todos(
                        todo("task-a", "task-a", TodoStatus.PENDING),
                        todo("task-b", "task-b", TodoStatus.IN_PROGRESS)));

        rail.afterToolCall(ctx);

        @SuppressWarnings("unchecked")
        List<Object> updated = (List<Object>) ctx.get("messages");
        assertEquals(1, rail.getToolCallCount("test-session-id"));
        assertEquals(1, updated.size());
        assertTrue(String.valueOf(updated.get(0)).contains("ensure the plan is being executed correctly"));
    }

    private void afterToolCallCountsAllTools() {
        TaskPlanningRail rail = new TaskPlanningRail(true, 20, Map.of());
        CallbackContext ctx = ctx("session_id", "test-session-id", "tool_name", "not_todo", "messages",
                new ArrayList<>(), "todos", todos());

        rail.afterToolCall(ctx);

        assertEquals(1, rail.getToolCallCount("test-session-id"));
    }

    private void afterInvokeRemovesToolCallCount() {
        TaskPlanningRail rail = new TaskPlanningRail(true, 1, Map.of());
        CallbackContext ctx = ctx("session_id", "test-session-id", "tool_name", "todo_create", "messages",
                new ArrayList<>(), "todos", todos(todo("a", "a", TodoStatus.PENDING)));
        rail.afterToolCall(ctx);

        rail.afterInvoke(ctx("session_id", "test-session-id"));

        assertEquals(0, rail.getToolCallCount("test-session-id"));
    }

    private void afterToolCallCustomInterval() {
        TaskPlanningRail rail = new TaskPlanningRail(true, 3, Map.of());
        CallbackContext ctx = ctx("session_id", "test-session-id", "tool_name", "todo_create", "messages",
                new ArrayList<>(), "todos", todos(todo("task-a", "task-a", TodoStatus.PENDING)));

        rail.afterToolCall(ctx);
        rail.afterToolCall(ctx);
        assertNull(ctx.get("should_repeat_progress"));
        rail.afterToolCall(ctx);

        assertEquals(3, rail.getToolCallCount("test-session-id"));
        assertEquals(Boolean.TRUE, ctx.get("should_repeat_progress"));
    }

    private void afterToolCallSkipsWhenDisabled() {
        TaskPlanningRail rail = makeRail();
        CallbackContext ctx = ctx("session_id", "test-session-id", "tool_name", "todo_create", "messages",
                new ArrayList<>(), "todos", todos());

        rail.afterToolCall(ctx);

        assertEquals(0, rail.getToolCallCount("test-session-id"));
        assertNull(ctx.get("should_repeat_progress"));
    }

    private void afterInvokeSafeWithoutSession() {
        TaskPlanningRail rail = makeRail();
        rail.afterModelCall(ctx("model_id", "fast", "input_tokens", 100, "output_tokens", 50));

        assertDoesNotThrow(() -> rail.afterInvoke(ctx()));
        assertTrue(rail.getUsageRecords().isEmpty());
    }

    private void formatTaskContentWithInProgress() {
        TaskPlanningRail.FormattedTaskContent formatted = makeRail().formatTaskContent(todos(
                todo("task-a", "task-a", TodoStatus.PENDING),
                todo("task-b", "task-b", TodoStatus.IN_PROGRESS),
                todo("task-c", "task-c", TodoStatus.COMPLETED)));

        assertTrue(formatted.tasks().contains("task-a"));
        assertTrue(formatted.tasks().contains("task-b"));
        assertTrue(formatted.tasks().contains("task-c"));
        assertEquals("task-b", formatted.inProgressTask());
    }

    private void formatTaskContentWithoutInProgress() {
        TaskPlanningRail.FormattedTaskContent formatted = makeRail().formatTaskContent(todos(
                todo("task-a", "task-a", TodoStatus.PENDING),
                todo("task-b", "task-b", TodoStatus.COMPLETED)));

        assertTrue(formatted.tasks().contains("task-a"));
        assertTrue(formatted.tasks().contains("task-b"));
        assertEquals("", formatted.inProgressTask());
    }

    private void taskPlanUsesTodoItem() {
        TaskPlan plan = new TaskPlan("test", todos(
                todo("task-a", "task-a", TodoStatus.PENDING),
                todo("task-b", "task-b", TodoStatus.IN_PROGRESS)));

        assertEquals(2, plan.getTasks().size());
        assertInstanceOf(TodoItem.class, plan.getTasks().get(0));
        assertEquals(TodoStatus.IN_PROGRESS, plan.getTasks().get(1).getStatus());
    }

    private void enableProgressRepeatDefault() {
        assertFalse(makeRail().isEnableProgressRepeat());
    }

    private void enableProgressRepeatTrue() {
        assertTrue(new TaskPlanningRail(true, 20, Map.of()).isEnableProgressRepeat());
    }

    private void listToolCallIntervalDefault() {
        assertEquals(20, makeRail().getListToolCallInterval());
    }

    private void buildTodoSystemPromptChinese() {
        assertTrue(TodoSection.buildTodoSystemPrompt("cn").contains("任务规划"));
    }

    private void buildTodoSystemPromptEnglish() {
        assertTrue(TodoSection.buildTodoSystemPrompt("en").contains("task planning"));
    }

    private void buildProgressReminderUserPromptChinese() {
        assertTrue(TodoSection.buildProgressReminderUserPrompt("cn", "", "")
                .contains("确保计划正在正确执行"));
    }

    private void buildProgressReminderUserPromptEnglish() {
        assertTrue(TodoSection.buildProgressReminderUserPrompt("en", "", "")
                .contains("ensure the plan is being executed correctly"));
    }

    private void buildProgressReminderUserPromptWithTaskContent() {
        String tasks = "id: 1 |status: pending |content: task-a\nid: 2 |status: in_progress |content: task-b";
        String prompt = TodoSection.buildProgressReminderUserPrompt("en", tasks, "task-b");

        assertTrue(prompt.contains(tasks));
        assertTrue(prompt.contains("task-b"));
        assertTrue(prompt.contains("currently being executed"));
    }

    private void buildProgressReminderUserPromptWithTaskContentChinese() {
        String tasks = "id: 1 |status: pending |content: 任务一\nid: 2 |status: in_progress |content: 任务二";
        String prompt = TodoSection.buildProgressReminderUserPrompt("cn", tasks, "任务二");

        assertTrue(prompt.contains(tasks));
        assertTrue(prompt.contains("任务二"));
        assertTrue(prompt.contains("正在执行的任务"));
    }

    private void modelSelectionDefaultIsEmpty() {
        assertTrue(makeRail().getModelSelection().isEmpty());
    }

    private void modelSelectionStoredOnInit() {
        Map<String, Object> modelSelection = new LinkedHashMap<>();
        modelSelection.put("fast", "cheap model for simple tasks");
        modelSelection.put("smart", "premium model for complex tasks");

        TaskPlanningRail rail = new TaskPlanningRail(false, 20, modelSelection);

        assertEquals(modelSelection, rail.getModelSelection());
    }

    private void beforeModelCallSwitchesModelForInProgressTask() {
        TaskPlanningRail rail = new TaskPlanningRail(false, 20, Map.of("fast", "cheap", "smart", "premium"));
        CallbackContext ctx = ctx(
                "session_id", "sess-model-switch",
                "default_model_id", "smart",
                "todos", todos(todo("task-a", "task-a", TodoStatus.IN_PROGRESS, "fast")));

        rail.beforeModelCall(ctx);

        assertEquals("fast", ctx.get("target_model_id"));
    }

    private void beforeModelCallRestoresDefaultWhenNoModelId() {
        TaskPlanningRail rail = new TaskPlanningRail(false, 20, Map.of("fast", "cheap"));
        CallbackContext ctx = ctx(
                "session_id", "sess-default-restore",
                "default_model_id", "default",
                "todos", todos(todo("task-a", "task-a", TodoStatus.IN_PROGRESS)));

        rail.beforeModelCall(ctx);

        assertEquals("default", ctx.get("target_model_id"));
    }

    private void beforeModelCallNoSwitchWhenModelSelectionEmpty() {
        CallbackContext ctx = ctx("session_id", "sess-no-switch");

        makeRail().beforeModelCall(ctx);

        assertNull(ctx.get("target_model_id"));
    }

    private void afterModelCallAccumulatesUsage() {
        TaskPlanningRail rail = makeRail();
        CallbackContext ctx = ctx("model_id", "fast", "input_tokens", 100, "output_tokens", 50);

        rail.afterModelCall(ctx);
        rail.afterModelCall(ctx);

        ModelUsageRecord record = rail.getUsageRecords().get("fast");
        assertNotNull(record);
        assertEquals(200, record.getInputTokens());
        assertEquals(100, record.getOutputTokens());
    }

    private void afterInvokeResetsUsageRecords() {
        TaskPlanningRail rail = makeRail();
        rail.afterModelCall(ctx("model_id", "fast", "input_tokens", 200, "output_tokens", 100));

        rail.afterInvoke(ctx("session_id", "sess-reset"));

        assertTrue(rail.getUsageRecords().isEmpty());
    }

    private void buildTodoSectionWithModelSelectionInjectsPrompt() {
        PromptSection section = TodoSection.buildTodoSection("en", Map.of("fast", "cheap model"));
        String content = section.getContent().get("en");

        assertTrue(content.contains("fast"));
        assertTrue(content.contains("Model Selection"));
    }

    private void buildTodoSectionWithoutModelSelectionNoModelPrompt() {
        PromptSection section = TodoSection.buildTodoSection("en", Map.of());
        String content = section.getContent().get("en");

        assertTrue(content.contains("Model Selection Note"));
        assertTrue(content.contains("do NOT use the selected_model_id field"));
    }

    private static TaskPlanningRail makeRail() {
        return new TaskPlanningRail();
    }

    private static DeepAgent makeAgent(String workspacePath) {
        DeepAgent agent = new DeepAgent(new AgentCard("deep", "deep", "test"));
        DeepAgentConfig config = new DeepAgentConfig();
        config.setEnableTaskLoop(true);
        if (workspacePath != null) {
            config.setWorkspace(new Workspace(workspacePath, "cn"));
        }
        agent.configure(config);
        return agent;
    }

    private static CallbackContext ctx(Object... values) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < values.length; i += 2) {
            map.put(String.valueOf(values[i]), values[i + 1]);
        }
        return new CallbackContext(makeAgent(null), map);
    }

    private static TodoItem todo(String id, String content, TodoStatus status) {
        return todo(id, content, status, null);
    }

    private static TodoItem todo(String id, String content, TodoStatus status, String selectedModelId) {
        return new TodoItem(id, content, "Executing " + content, "", status, List.of(), null, null, selectedModelId);
    }

    private static List<TodoItem> todos(TodoItem... items) {
        return new ArrayList<>(List.of(items));
    }
}

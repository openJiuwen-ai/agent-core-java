/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.harness.rails;

import com.openjiuwen.core.singleagent.prompts.PromptSection;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.harness.deep_agent.DeepAgent;
import com.openjiuwen.harness.prompts.sections.TodoSection;
import com.openjiuwen.harness.schema.DeepAgentConfig;
import com.openjiuwen.harness.schema.task.ModelUsageRecord;
import com.openjiuwen.harness.schema.task.TodoItem;
import com.openjiuwen.harness.schema.task.TodoStatus;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * <p>Mirrors Python's {@code tests/system_tests/harness/rail/test_task_planning_rail_system.py}.</p>
 */
class TaskPlanningRailSystemPythonParityTest {

    @Test
    void modelSelectionSwitchesModelForInProgressTask() {
        TaskPlanningRail rail = railWithModels();
        CallbackContext ctx = ctx(
                "session_id", "sys-test-session",
                "default_model_id", "default",
                "todos", todos(
                        todo("translate_document", "Translate document", TodoStatus.IN_PROGRESS, "fast"),
                        todo("analyze_architecture", "Analyze architecture", TodoStatus.PENDING, "smart")
                )
        );

        rail.beforeModelCall(ctx);

        assertThat(ctx.get("target_model_id")).isEqualTo("fast");
    }

    @Test
    void modelSelectionSwitchesToSmartModel() {
        TaskPlanningRail rail = railWithModels();
        CallbackContext ctx = ctx(
                "session_id", "sys-test-session-2",
                "default_model_id", "default",
                "todos", todos(
                        todo("translate_document", "Translate document", TodoStatus.COMPLETED, "fast"),
                        todo("analyze_architecture", "Analyze architecture", TodoStatus.IN_PROGRESS, "smart")
                )
        );

        rail.beforeModelCall(ctx);

        assertThat(ctx.get("target_model_id")).isEqualTo("smart");
    }

    @Test
    void usageRecordsAccumulatedAcrossCalls() {
        TaskPlanningRail rail = railWithModels();

        rail.afterModelCall(ctx("session_id", "sys-usage-session", "model_id", "fast",
                "input_tokens", 100, "output_tokens", 50));
        rail.afterModelCall(ctx("session_id", "sys-usage-session", "model_id", "fast",
                "input_tokens", 200, "output_tokens", 80));
        rail.afterModelCall(ctx("session_id", "sys-usage-session", "model_id", "smart",
                "input_tokens", 500, "output_tokens", 300));

        ModelUsageRecord fast = rail.getUsageRecords().get("fast");
        ModelUsageRecord smart = rail.getUsageRecords().get("smart");
        assertThat(fast.getInputTokens()).isEqualTo(300);
        assertThat(fast.getOutputTokens()).isEqualTo(130);
        assertThat(smart.getInputTokens()).isEqualTo(500);
        assertThat(smart.getOutputTokens()).isEqualTo(300);
    }

    @Test
    void afterInvokeLogsAndResetsUsage() {
        TaskPlanningRail rail = railWithModels();
        rail.afterModelCall(ctx("model_id", "fast", "input_tokens", 300, "output_tokens", 130));
        rail.afterModelCall(ctx("model_id", "smart", "input_tokens", 500, "output_tokens", 300));

        rail.afterInvoke(ctx("session_id", "sys-invoke-session"));

        assertThat(rail.getUsageRecords()).isEmpty();
    }

    @Test
    void modelSelectionSystemPromptIncludesModelIds() {
        PromptSection section = TodoSection.buildTodoSection("en", Map.of(
                "fast", "cheap model",
                "smart", "premium model"
        ));

        String content = section.getContent().get("en");
        assertThat(content).contains("fast", "smart", "Model Selection");
    }

    @Test
    void noModelSwitchWhenNoInProgressTask() {
        TaskPlanningRail rail = railWithModels();
        CallbackContext ctx = ctx(
                "session_id", "sys-no-inprogress",
                "default_model_id", "default",
                "todos", todos(todo("task-a", "task-a", TodoStatus.PENDING, "fast"))
        );

        rail.beforeModelCall(ctx);

        assertThat(ctx.get("target_model_id")).isEqualTo("default");
    }

    private static TaskPlanningRail railWithModels() {
        return new TaskPlanningRail(false, 20, Map.of(
                "fast", "cheap model for simple tasks",
                "smart", "premium model for complex tasks"
        ));
    }

    private static CallbackContext ctx(Object... values) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int index = 0; index + 1 < values.length; index += 2) {
            map.put(String.valueOf(values[index]), values[index + 1]);
        }
        DeepAgent agent = new DeepAgent(new AgentCard("test-deep", "test-deep", "test"));
        agent.configure(new DeepAgentConfig());
        return new CallbackContext(agent, map);
    }

    private static TodoItem todo(String id, String content, TodoStatus status, String selectedModelId) {
        return new TodoItem(id, content, "Executing " + content, "", status, List.of(), null, null, selectedModelId);
    }

    private static List<TodoItem> todos(TodoItem... items) {
        return new ArrayList<>(List.of(items));
    }
}

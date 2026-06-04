/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.rail;

import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.foundation.llm.schema.UsageMetadata;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.single_agent.prompts.SystemPromptBuilder;
import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.harness.rails.TaskPlanningRail;
import com.openjiuwen.harness.schema.task.ModelUsageRecord;
import com.openjiuwen.harness.schema.task.TodoStatus;
import com.openjiuwen.harness.prompts.sections.TodoSection;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * System tests for TaskPlanningRail.
 * <p>
 * Mirrors Python's {@code test_task_planning_rail_system.py} in
 * {@code tests.system_tests.harness.rail}.
 */
@Tag("system-test")
class TestTaskPlanningRailSystem {

    @Test
    void testModelSelectionSwitchesModelForInProgressTask() {
        Model fastModel = mockModel("fast");
        Model smartModel = mockModel("smart");
        TaskPlanningRail rail = new TaskPlanningRail(Map.of(fastModel, "cheap model", smartModel, "premium model"));
        FakeAgent agent = new FakeAgent();
        agent._llm = smartModel;
        agent.builder = new SystemPromptBuilder("en");
        AgentSessionApi session = new AgentSessionApi("sys-test-session");
        session.updateState(Map.of("harness.todos", List.of(
                todo("Translate document", TodoStatus.IN_PROGRESS, "fast"),
                todo("Analyze architecture", TodoStatus.PENDING, "smart")
        )));

        rail.beforeModelCall(AgentCallbackContext.builder().agent(agent).session(session).build());

        assertThat(agent.lastSetLlm).isSameAs(fastModel);
    }

    @Test
    void testModelSelectionSwitchesToSmartModel() {
        Model fastModel = mockModel("fast");
        Model smartModel = mockModel("smart");
        TaskPlanningRail rail = new TaskPlanningRail(Map.of(fastModel, "cheap model", smartModel, "premium model"));
        FakeAgent agent = new FakeAgent();
        agent._llm = fastModel;
        agent.builder = new SystemPromptBuilder("en");
        AgentSessionApi session = new AgentSessionApi("sys-test-session-2");
        session.updateState(Map.of("harness.todos", List.of(
                todo("Translate document", TodoStatus.COMPLETED, "fast"),
                todo("Analyze architecture", TodoStatus.IN_PROGRESS, "smart")
        )));

        rail.beforeModelCall(AgentCallbackContext.builder().agent(agent).session(session).build());

        assertThat(agent.lastSetLlm).isSameAs(smartModel);
    }

    @Test
    void testUsageRecordsAccumulatedAcrossCalls() {
        Model fastModel = mockModel("fast");
        Model smartModel = mockModel("smart");
        TaskPlanningRail rail = new TaskPlanningRail(Map.of(fastModel, "cheap model", smartModel, "premium model"));
        FakeAgent agent = new FakeAgent();
        agent._llm = fastModel;

        rail.afterModelCall(ctxWithUsage(agent, 100, 50));
        rail.afterModelCall(ctxWithUsage(agent, 200, 80));
        agent._llm = smartModel;
        rail.afterModelCall(ctxWithUsage(agent, 500, 300));

        assertThat(rail.getUsageRecords().get("fast").getInputTokens()).isEqualTo(300);
        assertThat(rail.getUsageRecords().get("fast").getOutputTokens()).isEqualTo(130);
        assertThat(rail.getUsageRecords().get("smart").getInputTokens()).isEqualTo(500);
        assertThat(rail.getUsageRecords().get("smart").getOutputTokens()).isEqualTo(300);
    }

    @Test
    void testAfterInvokeLogsAndResetsUsage() {
        Model fastModel = mockModel("fast");
        Model smartModel = mockModel("smart");
        TaskPlanningRail rail = new TaskPlanningRail(Map.of(fastModel, "cheap model", smartModel, "premium model"));
        rail.putUsageRecord("fast", new ModelUsageRecord("fast", 300, 130));
        rail.putUsageRecord("smart", new ModelUsageRecord("smart", 500, 300));

        rail.afterInvoke(AgentCallbackContext.builder()
                .session(new AgentSessionApi("sys-invoke-session"))
                .build());

        assertThat(rail.getUsageRecords()).isEmpty();
    }

    @Test
    void testModelSelectionSystemPromptIncludesModelIds() {
        Model fastModel = mockModel("fast");
        Model smartModel = mockModel("smart");
        TaskPlanningRail rail = new TaskPlanningRail(Map.of(fastModel, "cheap model", smartModel, "premium model"));
        String modelList = rail.buildModelList();
        String content = TodoSection.build("en", modelList).render("en");

        assertThat(content).contains("fast");
        assertThat(content).contains("smart");
        assertThat(content).contains("Model Selection");
    }

    @Test
    void testNoModelSwitchWhenNoInProgressTask() {
        Model fastModel = mockModel("fast");
        Model smartModel = mockModel("smart");
        TaskPlanningRail rail = new TaskPlanningRail(Map.of(fastModel, "cheap model", smartModel, "premium model"));
        FakeAgent agent = new FakeAgent();
        agent._llm = fastModel;
        agent.builder = new SystemPromptBuilder("en");
        AgentSessionApi session = new AgentSessionApi("sys-no-inprogress");
        session.updateState(Map.of("harness.todos", List.of(
                todo("task-a", TodoStatus.PENDING, "fast")
        )));

        rail.beforeModelCall(AgentCallbackContext.builder().agent(agent).session(session).build());

        assertThat(agent.lastSetLlm).isSameAs(fastModel);
    }

    private static Model mockModel(String clientId) {
        return new Model(
                ModelClientConfig.builder()
                        .clientProvider("OpenAI")
                        .clientId(clientId)
                        .apiKey("mock-key")
                        .apiBase("https://example.com/v1")
                        .verifySsl(false)
                        .build(),
                ModelRequestConfig.builder().modelName("mock-model").build());
    }

    private static Map<String, Object> todo(String content, TodoStatus status, String selectedModelId) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", content.toLowerCase().replace(' ', '_'));
        row.put("content", content);
        row.put("status", status.getValue());
        row.put("selected_model_id", selectedModelId);
        return row;
    }

    private static AgentCallbackContext ctxWithUsage(FakeAgent agent, int inputTokens, int outputTokens) {
        UsageMetadata usageMetadata = UsageMetadata.builder()
                .inputTokens(inputTokens)
                .outputTokens(outputTokens)
                .build();
        AssistantMessage response = new AssistantMessage("ok");
        response.setUsageMetadata(usageMetadata);
        com.openjiuwen.core.singleagent.rail.ModelCallInputs modelCallInputs =
                new com.openjiuwen.core.singleagent.rail.ModelCallInputs();
        modelCallInputs.setResponse(response);
        return AgentCallbackContext.builder()
                .agent(agent)
                .inputs(modelCallInputs)
                .build();
    }

    private static final class FakeAgent {
        private Model _llm;
        private Model lastSetLlm;
        private SystemPromptBuilder builder;

        public void setLlm(Model model) {
            this.lastSetLlm = model;
            this._llm = model;
        }

        public SystemPromptBuilder getSystemPromptBuilder() {
            return builder;
        }
    }
}

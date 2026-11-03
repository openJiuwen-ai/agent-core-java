/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.application.workflow;

import com.openjiuwen.core.application.schema.WorkflowAgentConfig;
import com.openjiuwen.core.application.schema.WorkflowSchema;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.runner.base.TagMatchStrategy;
import com.openjiuwen.core.workflow.Workflow;
import com.openjiuwen.core.workflow.WorkflowCard;
import com.openjiuwen.core.workflow.component.End;
import com.openjiuwen.core.workflow.component.Start;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the BaseAgent reactive wrapper against the concrete WorkflowAgent path.
 */
class WorkflowAgentReactiveTest {

    private final List<String> registeredWorkflowIds = new ArrayList<>();

    @AfterEach
    void tearDown() {
        for (String workflowId : registeredWorkflowIds) {
            Runner.resourceMgr().removeWorkflow(workflowId, null, TagMatchStrategy.ALL, true);
        }
        registeredWorkflowIds.clear();
    }

    @Test
    void invokeDelegatesThroughConcreteWorkflowAgentInvoke() {
        WorkflowAgent agent = newWorkflowAgent("workflow-reactive-invoke");

        Object result = agent.invoke(Map.of(
                "query", "invoke payload",
                "conversation_id", "workflow-reactive-invoke-session"), null);

        assertThat(result).isNotNull();
        assertThat(String.valueOf(result))
                .contains("workflow reactive handled")
                .contains("invoke payload");
    }

    @Test
    void streamDelegatesThroughConcreteWorkflowAgentStream() {
        WorkflowAgent agent = newWorkflowAgent("workflow-reactive-stream");

        Object result = agent.invoke(Map.of(
                        "query", "stream payload",
                        "conversation_id", "workflow-reactive-stream-session"),
                null);

        assertThat(String.valueOf(result))
                .contains("workflow reactive handled")
                .contains("stream payload");
    }

    private WorkflowAgent newWorkflowAgent(String prefix) {
        String baseId = prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
        String version = "1";
        String resourceId = baseId + "_" + version;
        String workflowName = baseId + "-name";

        Workflow workflow = new Workflow(WorkflowCard.builder()
                .id(resourceId)
                .name(workflowName)
                .description("Reactive WorkflowAgent test workflow")
                .version(version)
                .inputParams(inputSchema())
                .build());
        workflow.setStartComp("start", new Start(), Map.of("query", "${query}"), null);
        workflow.setEndComp(
                "end",
                new End(Map.of("responseTemplate", "workflow reactive handled {{query}}")),
                Map.of("query", "${start.query}"),
                null);
        workflow.addConnection("start", "end");
        Runner.resourceMgr().addWorkflow(workflow.getCard(), () -> workflow, null);
        registeredWorkflowIds.add(resourceId);

        WorkflowSchema workflowSchema = WorkflowSchema.builder()
                .id(baseId)
                .name(workflowName)
                .version(version)
                .description("Reactive WorkflowAgent test schema")
                .inputParams(inputSchema())
                .build();

        return new WorkflowAgent(WorkflowAgentConfig.builder()
                .id(baseId + "-agent")
                .description("Reactive WorkflowAgent test agent")
                .workflows(List.of(workflowSchema))
                .build());
    }

    private static Map<String, Object> inputSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of("query", Map.of("type", "string")),
                "required", List.of("query"));
    }
}
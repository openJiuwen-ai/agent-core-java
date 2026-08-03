/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.application.workflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.openjiuwen.core.application.schema.WorkflowAgentConfig;
import com.openjiuwen.core.application.schema.WorkflowSchema;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.runner.base.TagMatchStrategy;
import com.openjiuwen.core.workflow.Workflow;
import com.openjiuwen.core.workflow.WorkflowCard;
import com.openjiuwen.core.workflow.WorkflowUtils;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

class WorkflowAgentRegistrationTest {
    @Test
    void addWorkflows_duplicateIdAndVersion_keepsFirstWorkflowConsistently() {
        String suffix = UUID.randomUUID().toString();
        String workflowId = "duplicate-workflow-" + suffix;
        String version = "1.0";
        String resourceId = WorkflowUtils.generateWorkflowKey(workflowId, version);
        Workflow first = workflow(workflowId, version, "first");
        Workflow duplicate = workflow(workflowId, version, "duplicate");
        WorkflowAgent agent = new WorkflowAgent(WorkflowAgentConfig.builder()
                .id("duplicate-workflow-agent-" + suffix)
                .build());

        try {
            agent.addWorkflows(List.of(first, duplicate));

            assertEquals(1, agent.getAgentConfig().getWorkflows().size());
            assertEquals("first", agent.getAgentConfig().getWorkflows().get(0).getName());
            assertSame(first, Runner.resourceMgr().getWorkflow(resourceId));
        } finally {
            Runner.resourceMgr().removeWorkflow(resourceId, null, TagMatchStrategy.ALL, true);
        }
    }

    @Test
    void addWorkflows_duplicateAcrossCalls_keepsFirstWorkflowConsistently() {
        String suffix = UUID.randomUUID().toString();
        String workflowId = "duplicate-workflow-across-calls-" + suffix;
        String version = "1.0";
        String resourceId = WorkflowUtils.generateWorkflowKey(workflowId, version);
        Workflow first = workflow(workflowId, version, "shared-name", "first");
        Workflow duplicate = workflow(workflowId, version, "shared-name", "duplicate");
        WorkflowAgent agent = new WorkflowAgent(WorkflowAgentConfig.builder()
                .id("duplicate-workflow-across-calls-agent-" + suffix)
                .build());

        try {
            agent.addWorkflows(List.of(first));
            agent.addWorkflows(List.of(duplicate));

            assertEquals(1, agent.getAgentConfig().getWorkflows().size());
            assertEquals("first", agent.getAgentConfig().getWorkflows().get(0).getDescription());
            assertSame(first.getCard(), agent.getAbilityManager().get("shared-name"));
            assertSame(first, Runner.resourceMgr().getWorkflow(resourceId));
        } finally {
            Runner.resourceMgr().removeWorkflow(resourceId, null, TagMatchStrategy.ALL, true);
        }
    }

    @Test
    void addWorkflows_preconfiguredSchema_registersProviderWithoutAppendingSchema() {
        String suffix = UUID.randomUUID().toString();
        String workflowId = "preconfigured-workflow-" + suffix;
        String version = "1.0";
        String resourceId = WorkflowUtils.generateWorkflowKey(workflowId, version);
        WorkflowSchema preconfiguredSchema = WorkflowSchema.builder()
                .id(workflowId)
                .version(version)
                .name("preconfigured-name")
                .description("preconfigured")
                .build();
        Workflow workflow = workflow(workflowId, version, "runtime-name", "runtime");
        WorkflowAgent agent = new WorkflowAgent(WorkflowAgentConfig.builder()
                .id("preconfigured-workflow-agent-" + suffix)
                .workflows(List.of(preconfiguredSchema))
                .build());

        try {
            agent.addWorkflows(List.of(workflow));

            assertEquals(1, agent.getAgentConfig().getWorkflows().size());
            assertSame(preconfiguredSchema, agent.getAgentConfig().getWorkflows().get(0));
            assertSame(workflow.getCard(), agent.getAbilityManager().get("runtime-name"));
            assertSame(workflow, Runner.resourceMgr().getWorkflow(resourceId));
        } finally {
            Runner.resourceMgr().removeWorkflow(resourceId, null, TagMatchStrategy.ALL, true);
        }
    }

    private static Workflow workflow(String id, String version, String name) {
        return workflow(id, version, name, name);
    }

    private static Workflow workflow(String id, String version, String name, String description) {
        return new Workflow(WorkflowCard.builder()
                .id(id)
                .version(version)
                .name(name)
                .description(description)
                .build());
    }
}

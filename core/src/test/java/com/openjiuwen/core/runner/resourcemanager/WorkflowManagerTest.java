/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.resourcemanager;

import com.openjiuwen.core.workflow.Workflow;
import com.openjiuwen.core.workflow.WorkflowCard;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * <p>Mirrors Python's {@code WorkflowMgr} behavior in
 * {@code openjiuwen/core/runner/resources_manager/workflow_manager.py}.</p>
 */
class WorkflowManagerTest {

    @Test
    void providerResultIsReturnedAndMissingIdReturnsNull() {
        WorkflowManager manager = new WorkflowManager();
        Workflow workflow = workflow("workflow-1");

        manager.addWorkflow("workflow-1", () -> workflow);

        assertSame(workflow, manager.getWorkflow("workflow-1").toCompletableFuture().join());
        assertNull(manager.getWorkflow("missing").toCompletableFuture().join());
    }

    @Test
    void asyncProviderResultIsAwaitedByResourceLookup() {
        WorkflowManager manager = new WorkflowManager();
        Workflow workflow = workflow("workflow-1");

        manager.addWorkflow("workflow-1", () -> CompletableFuture.completedFuture(workflow));

        assertSame(workflow, manager.getWorkflow("workflow-1").toCompletableFuture().join());
    }

    @Test
    void addWorkflowsNoopsForNullOrEmptyListAndRegistersEntriesInOrder() {
        WorkflowManager manager = new WorkflowManager();
        Workflow first = workflow("workflow-1");
        Workflow second = workflow("workflow-2");

        manager.addWorkflows(null);
        manager.addWorkflows(List.of());
        manager.addWorkflows(List.of(
                new WorkflowManager.WorkflowEntry("workflow-1", () -> first),
                new WorkflowManager.WorkflowEntry("workflow-2", () -> second)
        ));

        assertSame(first, manager.getWorkflow("workflow-1").toCompletableFuture().join());
        assertSame(second, manager.getWorkflow("workflow-2").toCompletableFuture().join());
    }

    @Test
    void duplicateProviderIsRejected() {
        WorkflowManager manager = new WorkflowManager();
        manager.addWorkflow("workflow-1", () -> workflow("workflow-1"));

        assertThrows(IllegalArgumentException.class,
                () -> manager.addWorkflow("workflow-1", () -> workflow("workflow-duplicate")));
    }

    @Test
    void removeWorkflowReturnsProviderAndClearsIt() {
        WorkflowManager manager = new WorkflowManager();
        Supplier<Workflow> provider = () -> workflow("workflow-1");
        manager.addWorkflow("workflow-1", provider);

        assertSame(provider, manager.removeWorkflow("workflow-1"));
        assertNull(manager.getWorkflow("workflow-1").toCompletableFuture().join());
    }

    private static Workflow workflow(String id) {
        return new Workflow(new WorkflowCard(id, "workflow", "demo", "", null));
    }
}

/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.schema;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class TaskSchemaModelsTest {

    @Test
    void taskOpResultFactoriesPreserveReason() {
        TaskOpResult success = TaskOpResult.success();
        TaskOpResult fail = TaskOpResult.fail("cycle");

        assertTrue(success.ok());
        assertEquals("", success.reason());
        assertFalse(fail.ok());
        assertEquals("cycle", fail.reason());
    }

    @Test
    void taskCreateResultExposesWrappedTaskProperties() {
        TaskSummary summary = new TaskSummary();
        summary.setTaskId("T1");
        summary.setTitle("Review");

        TaskCreateResult result = TaskCreateResult.success(summary);

        assertTrue(result.ok());
        assertEquals("Review", result.getTaskProperty("title"));
        assertEquals("T1", result.getTaskProperty("taskId"));
    }

    @Test
    void taskCreateResultFailureKeepsReason() {
        TaskCreateResult result = TaskCreateResult.fail("duplicate task_id");

        assertFalse(result.ok());
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> result.getTaskProperty("title"));
        assertTrue(ex.getMessage().contains("duplicate task_id"));
    }

    @Test
    void taskSummaryAndDetailDefaultListsAreMutableEmptyLists() {
        TaskSummary summary = new TaskSummary();
        TaskDetail detail = new TaskDetail();

        assertEquals(List.of(), summary.getBlockedBy());
        assertEquals(List.of(), detail.getBlocks());

        summary.getBlockedBy().add("T0");
        detail.getBlocks().add("T2");

        assertEquals(List.of("T0"), summary.getBlockedBy());
        assertEquals(List.of("T2"), detail.getBlocks());
    }

    @Test
    void graphMutationResultFactoriesHandleDefaults() {
        GraphMutationResult success = GraphMutationResult.success(List.of("T1", "T2"));
        GraphMutationResult fail = GraphMutationResult.fail("cycle");

        assertTrue(success.ok());
        assertEquals(List.of("T1", "T2"), success.refreshedTasks());
        assertFalse(fail.ok());
        assertEquals("cycle", fail.reason());
        assertEquals(List.of(), fail.refreshedTasks());
    }
}

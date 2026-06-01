/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.agent_teams;

import com.openjiuwen.agent_teams.schema.task.TaskDetail;
import com.openjiuwen.agent_teams.schema.task.TaskRecord;
import com.openjiuwen.agent_teams.schema.task.TaskStatus;
import com.openjiuwen.agent_teams.schema.task.TaskSummary;
import com.openjiuwen.agent_teams.tools.TeamTaskManager;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python tests in {@code tests.unit_tests.agent_teams.test_task_manager}.
 */
class TeamTaskManagerTest {

    @Test
    void hardcodedReturnBranchesRepresentPythonFailureAndSuccessStates() {
        TeamTaskManager manager = new TeamTaskManager("team", "member1");
        TaskRecord upstream = manager.add("Upstream", "first", "up", List.of());
        TaskRecord blocked = manager.add("Blocked", "second", "blocked", List.of(upstream.getTaskId()));

        assertFalse(manager.claim("missing", "member1"));
        assertFalse(manager.claim(blocked.getTaskId(), "member1"));
        assertFalse(manager.addBlockedBy("missing", List.of(upstream.getTaskId())));
        assertFalse(manager.addBlockedBy(upstream.getTaskId(), List.of(blocked.getTaskId())));
        assertFalse(manager.complete("missing"));
        assertFalse(manager.complete(blocked.getTaskId()));
        assertFalse(manager.cancel("missing"));
        assertFalse(manager.update("missing", "title", null));

        assertTrue(manager.claim(upstream.getTaskId(), "member1"));
        assertTrue(manager.complete(upstream.getTaskId()));
        assertEquals(TaskStatus.PENDING, manager.get(blocked.getTaskId()).getStatus());
        assertTrue(manager.update(blocked.getTaskId(), "updated", "content"));
        assertEquals("updated", manager.get(blocked.getTaskId()).getTitle());
    }

    @Test
    void pythonStyleTaskManagerMethodsExposeEquivalentViewsAndTransitions() {
        TeamTaskManager manager = new TeamTaskManager("team", "member1");
        TaskRecord first = manager.add("First", "content", "task-1", List.of());
        TaskRecord second = manager.add("Second", "content", "task-2", List.of());
        TaskRecord blocked = manager.add("Blocked", "content", "task-3", List.of(first.getTaskId()));

        assertEquals(3, manager.listTasks().size());
        assertEquals(2, manager.listTasks(TaskStatus.PENDING).size());

        List<TaskSummary> summaries = manager.listTasksWithDeps(TaskStatus.BLOCKED);
        assertEquals(1, summaries.size());
        assertEquals(List.of(first.getTaskId()), summaries.get(0).getBlockedBy());

        TaskDetail detail = manager.getTaskDetail(blocked.getTaskId());
        assertNotNull(detail);
        assertEquals(List.of(first.getTaskId()), detail.getBlockedBy());
        assertEquals(List.of(blocked.getTaskId()), manager.getTaskDetail(first.getTaskId()).getBlocks());

        assertTrue(manager.claim(second.getTaskId()));
        assertEquals("member1", manager.getTaskDetail(second.getTaskId()).getAssignee());
        assertEquals(List.of(second), manager.getTasksByAssignee("member1"));
        assertEquals(List.of(second), manager.getTasksByAssignee("member1", TaskStatus.CLAIMED));

        assertTrue(manager.reset(second.getTaskId()));
        assertNull(manager.getTaskDetail(second.getTaskId()).getAssignee());
        assertEquals(TaskStatus.PENDING, manager.getTaskDetail(second.getTaskId()).getStatus());

        assertTrue(manager.claim(second.getTaskId(), "member1"));
        assertTrue(manager.approvePlan(second.getTaskId()));
        assertEquals(TaskStatus.PLAN_APPROVED, manager.getTaskDetail(second.getTaskId()).getStatus());
        assertFalse(manager.reset(second.getTaskId()));

        assertTrue(manager.updateTask(first.getTaskId(), "First updated", null));
        assertEquals("First updated", manager.getTaskDetail(first.getTaskId()).getTitle());
        assertTrue(manager.addDependencies(first.getTaskId(), List.of()));

        List<TaskRecord> claimable = manager.getClaimableTasks();
        assertEquals(List.of(first), claimable);

        List<TaskRecord> cancelled = manager.cancelAllTasks(Set.of("member1"));
        assertEquals(List.of(first, blocked), cancelled);
        assertEquals(TaskStatus.CANCELLED, manager.getTaskDetail(first.getTaskId()).getStatus());
        assertEquals(TaskStatus.PLAN_APPROVED, manager.getTaskDetail(second.getTaskId()).getStatus());
    }
}

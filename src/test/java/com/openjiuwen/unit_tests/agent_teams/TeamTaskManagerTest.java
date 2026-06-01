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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's {@code tests/unit_tests/agent_teams/test_task_manager.py}.
 */
class TeamTaskManagerTest {

    private TeamTaskManager newManager() {
        return newManager("member1", Set.of("member1", "m1", "m2"));
    }

    private TeamTaskManager newManager(String memberName, Set<String> members) {
        TeamTaskManager manager = new TeamTaskManager("test_team", memberName);
        manager.setMemberExistsPredicate(members::contains);
        return manager;
    }

    private TeamTaskManager newManager(String memberName, Map<String, TaskRecord> sharedTasks, Set<String> members) {
        TeamTaskManager manager = new TeamTaskManager("test_team", memberName, sharedTasks);
        manager.setMemberExistsPredicate(members::contains);
        return manager;
    }

    @Test
    void testAddTaskSuccess() {
        TeamTaskManager manager = newManager();

        TaskRecord task = manager.add("Test Task", "Test content", null, List.of());

        assertNotNull(task);
        assertEquals("Test Task", task.getTitle());
        assertEquals("Test content", task.getContent());
        assertEquals(TaskStatus.PENDING, task.getStatus());
        assertEquals(1, manager.size());
    }

    @Test
    void testAddTaskWithDependencies() {
        TeamTaskManager manager = newManager();
        TaskRecord task1 = manager.add("Task 1", "Content 1", "task-1", List.of());
        TaskRecord task2 = manager.add("Task 2", "Content 2", "task-2", List.of());

        TaskRecord task3 = manager.add("Task 3", "Content 3", "task-3", List.of(task1.getTaskId(), task2.getTaskId()));

        assertNotNull(task3);
        assertEquals(TaskStatus.BLOCKED, task3.getStatus());
        assertEquals(List.of("task-1", "task-2"), manager.getDependencies("task-3"));
        assertEquals(List.of("task-3"), manager.getTaskDetail("task-1").getBlocks());
        assertEquals(List.of("task-3"), manager.getTaskDetail("task-2").getBlocks());
    }

    @Test
    void testAddAsTopPriorityBlocksAllPending() {
        TeamTaskManager manager = newManager();
        TaskRecord task1 = manager.add("Task 1", "Content 1", "task-1", List.of());
        TaskRecord task2 = manager.add("Task 2", "Content 2", "task-2", List.of());
        TaskRecord task3 = manager.add("Task 3", "Content 3", "task-3", List.of());

        TaskRecord topTask = manager.addAsTopPriority("Top Priority Task", "Urgent content", "top");

        assertNotNull(topTask);
        assertEquals(TaskStatus.PENDING, topTask.getStatus());
        assertEquals(TaskStatus.BLOCKED, manager.get(task1.getTaskId()).getStatus());
        assertEquals(TaskStatus.BLOCKED, manager.get(task2.getTaskId()).getStatus());
        assertEquals(TaskStatus.BLOCKED, manager.get(task3.getTaskId()).getStatus());
        assertEquals(List.of("task-1", "task-2", "task-3"), topTask.getBlocks());
    }

    @Test
    void testAddDuplicateTaskIdReturnsReason() {
        TeamTaskManager manager = newManager();

        TeamTaskManager.TaskCreateResult first = manager.addResult("Original", "Content", "dup-1", List.of());
        TeamTaskManager.TaskCreateResult second = manager.addResult("Conflict", "Content", "dup-1", List.of());

        assertTrue(first.ok());
        assertFalse(second.ok());
        assertTrue(second.reason().contains("dup-1"));
        assertEquals("Original", manager.get("dup-1").getTitle());
    }

    @Test
    void testAddWithPriorityCircularDepReturnsReason() {
        TeamTaskManager manager = newManager();
        assertTrue(manager.addResult("A", "ca", "a", List.of()).ok());
        assertTrue(manager.addWithPriorityResult("B", "cb", "b", List.of("a"), List.of()).ok());

        TeamTaskManager.TaskCreateResult result =
                manager.addWithPriorityResult("C", "cc", "c", List.of("b"), List.of("a"));

        assertFalse(result.ok());
        assertTrue(result.reason().contains("Circular dependency"));
        assertNull(manager.get("c"));
    }

    @Test
    void testClaimBySecondMemberReportsConflictNotTransitionError() {
        Map<String, TaskRecord> sharedTasks = new LinkedHashMap<>();
        TeamTaskManager m1 = newManager("m1", sharedTasks, Set.of("m1", "m2"));
        TeamTaskManager m2 = newManager("m2", sharedTasks, Set.of("m1", "m2"));
        TaskRecord task = m1.add("Shared Task", "Work", "task", List.of());

        assertTrue(m1.claimResult(task.getTaskId(), "m1").ok());
        TeamTaskManager.TaskOpResult second = m2.claimResult(task.getTaskId(), "m2");

        assertFalse(second.ok());
        assertTrue(second.reason().contains("already claimed by m1"));
        assertEquals(TaskStatus.CLAIMED, m1.get("task").getStatus());
        assertEquals("m1", m1.get("task").getAssignee());
    }

    @Test
    void testCompleteTaskSetsUpdatedAt() throws InterruptedException {
        TeamTaskManager manager = newManager();
        TaskRecord task = manager.add("Test Task", "Content", "task", List.of());
        assertTrue(manager.claim(task.getTaskId()));
        long claimedAt = manager.get(task.getTaskId()).getUpdatedAt();
        Thread.sleep(2);

        assertTrue(manager.complete(task.getTaskId()));

        TaskDetail taskUpdated = manager.get(task.getTaskId());
        assertTrue(taskUpdated.getUpdatedAt() >= claimedAt);
        assertEquals(TaskStatus.COMPLETED, taskUpdated.getStatus());
    }

    @Test
    void testCompleteTaskUnblocksDependentTasks() {
        TeamTaskManager manager = newManager();
        TaskRecord task1 = manager.add("Task 1", "Content 1", "task-1", List.of());
        TaskRecord task2 = manager.add("Task 2", "Content 2", "task-2", List.of(task1.getTaskId()));
        TaskRecord task3 = manager.add("Task 3", "Content 3", "task-3", List.of(task1.getTaskId()));

        assertTrue(manager.claim(task1.getTaskId()));
        assertEquals(TaskStatus.BLOCKED, task2.getStatus());
        assertEquals(TaskStatus.BLOCKED, task3.getStatus());

        assertTrue(manager.complete(task1.getTaskId()));

        assertEquals(TaskStatus.PENDING, manager.get("task-2").getStatus());
        assertEquals(TaskStatus.PENDING, manager.get("task-3").getStatus());
    }

    @Test
    void testCompleteTaskUnblocksUnderConcurrentSessions() {
        TeamTaskManager manager = newManager();
        manager.add("c1", "", "count-1", List.of());
        for (int i = 2; i <= 5; i++) {
            manager.add("c" + i, "", "count-" + i, List.of("count-" + (i - 1)));
        }
        assertTrue(manager.claim("count-1"));
        assertFalse(manager.listTasks().isEmpty());

        assertTrue(manager.complete("count-1"));

        assertEquals(TaskStatus.PENDING, manager.get("count-2").getStatus());
        assertEquals(List.of(), manager.getDependencies("count-2"));
    }

    @Test
    void testAddWithPriorityBasic() {
        TeamTaskManager manager = newManager();

        TaskRecord task = manager.addWithPriority("Priority Task", "Priority content", null, List.of(), List.of());

        assertNotNull(task);
        assertEquals("Priority Task", task.getTitle());
        assertEquals(TaskStatus.PENDING, task.getStatus());
    }

    @Test
    void testAddWithPriorityDependencies() {
        TeamTaskManager manager = newManager();
        TaskRecord task1 = manager.add("Task 1", "Content 1", "task-1", List.of());
        TaskRecord task2 = manager.add("Task 2", "Content 2", "task-2", List.of());

        TaskRecord newTask = manager.addWithPriority(
                "Dependent Task", "Depends on task1 and task2", "task-3", List.of(task1.getTaskId(), task2.getTaskId()), List.of());

        assertNotNull(newTask);
        assertEquals(TaskStatus.BLOCKED, newTask.getStatus());
        assertEquals(List.of("task-1", "task-2"), manager.getDependencies(newTask.getTaskId()));
    }

    @Test
    void testAddWithPriorityDependentTasks() {
        TeamTaskManager manager = newManager();
        TaskRecord task1 = manager.add("Task 1", "Content 1", "task-1", List.of());
        TaskRecord task2 = manager.add("Task 2", "Content 2", "task-2", List.of());

        TaskRecord priorityTask = manager.addWithPriority(
                "High Priority Task", "Critical task", "priority", List.of(), List.of(task1.getTaskId(), task2.getTaskId()));

        assertNotNull(priorityTask);
        assertEquals(TaskStatus.PENDING, priorityTask.getStatus());
        assertEquals(TaskStatus.BLOCKED, manager.get("task-1").getStatus());
        assertEquals(TaskStatus.BLOCKED, manager.get("task-2").getStatus());
        assertEquals(List.of(priorityTask.getTaskId()), manager.getDependencies("task-1"));
        assertEquals(List.of(priorityTask.getTaskId()), manager.getDependencies("task-2"));
    }

    @Test
    void testAddWithPriorityBidirectional() {
        TeamTaskManager manager = newManager();
        TaskRecord taskA = manager.add("Task A", "Content A", "a", List.of());
        TaskRecord taskC = manager.add("Task C", "Content C", "c", List.of());

        TaskRecord taskB = manager.addWithPriority(
                "Task B", "Inserted task", "b", List.of(taskA.getTaskId()), List.of(taskC.getTaskId()));

        assertNotNull(taskB);
        assertEquals(TaskStatus.BLOCKED, taskB.getStatus());
        assertEquals(List.of(taskA.getTaskId()), manager.getDependencies("b"));
        assertEquals(List.of(taskB.getTaskId()), manager.getDependencies("c"));
    }

    @Test
    void testAddWithPriorityCustomTaskId() {
        TeamTaskManager manager = newManager();

        TaskRecord task = manager.addWithPriority("Custom ID Task", "Content", "custom-task-123", List.of(), List.of());

        assertNotNull(task);
        assertEquals("custom-task-123", task.getTaskId());
        assertEquals("custom-task-123", manager.get("custom-task-123").getTaskId());
    }

    @Test
    void testCancelPendingTask() {
        TeamTaskManager manager = newManager();
        TaskRecord task = manager.add("Task 1", "Content 1", "task", List.of());

        TaskRecord result = manager.cancelResult(task.getTaskId());

        assertNotNull(result);
        assertEquals(TaskStatus.CANCELLED, result.getStatus());
    }

    @Test
    void testCancelClaimedTask() {
        TeamTaskManager manager = newManager();
        TaskRecord task = manager.add("Task 1", "Content 1", "task", List.of());
        assertTrue(manager.claim(task.getTaskId()));

        TaskRecord result = manager.cancelResult(task.getTaskId());

        assertNotNull(result);
        assertEquals(TaskStatus.CANCELLED, result.getStatus());
    }

    @Test
    void testCancelNonexistentTask() {
        TeamTaskManager manager = newManager();

        assertNull(manager.cancelResult("missing"));
        assertFalse(manager.cancel("missing"));
    }

    @Test
    void testCancelCompletedTaskFails() {
        TeamTaskManager manager = newManager();
        TaskRecord task = manager.add("Task 1", "Content 1", "task", List.of());
        assertTrue(manager.claim(task.getTaskId()));
        assertTrue(manager.complete(task.getTaskId()));

        TaskRecord result = manager.cancelResult(task.getTaskId());

        assertNull(result);
        assertEquals(TaskStatus.COMPLETED, manager.get(task.getTaskId()).getStatus());
    }

    @Test
    void testCancelAlreadyCancelledTaskIsIdempotent() {
        TeamTaskManager manager = newManager();
        TaskRecord task = manager.add("Task 1", "Content 1", "task", List.of());

        TaskRecord first = manager.cancelResult(task.getTaskId());
        TaskRecord second = manager.cancelResult(task.getTaskId());

        assertNotNull(first);
        assertSame(first, second);
        assertEquals(TaskStatus.CANCELLED, second.getStatus());
    }

    @Test
    void testGetClaimableTasksEmpty() {
        assertEquals(List.of(), newManager().getClaimableTasks());
    }

    @Test
    void testGetClaimableTasksPending() {
        TeamTaskManager manager = newManager();
        TaskRecord task = manager.add("Task 1", "Content 1", "task", List.of());

        assertEquals(List.of(task), manager.getClaimableTasks());
    }

    @Test
    void testGetClaimableTasksExcludesBlocked() {
        TeamTaskManager manager = newManager();
        TaskRecord task1 = manager.add("Task 1", "Content 1", "task-1", List.of());
        manager.add("Task 2", "Content 2", "task-2", List.of(task1.getTaskId()));

        assertEquals(List.of(task1), manager.getClaimableTasks());
    }

    @Test
    void testGetClaimableTasksExcludesClaimed() {
        TeamTaskManager manager = newManager();
        TaskRecord task1 = manager.add("Task 1", "Content 1", "task-1", List.of());
        TaskRecord task2 = manager.add("Task 2", "Content 2", "task-2", List.of());
        assertTrue(manager.claim(task1.getTaskId()));

        assertEquals(List.of(task2), manager.getClaimableTasks());
    }

    @Test
    void testGetClaimableTasksExcludesCompleted() {
        TeamTaskManager manager = newManager();
        TaskRecord task1 = manager.add("Task 1", "Content 1", "task-1", List.of());
        TaskRecord task2 = manager.add("Task 2", "Content 2", "task-2", List.of());
        assertTrue(manager.claim(task1.getTaskId()));
        assertTrue(manager.complete(task1.getTaskId()));

        assertEquals(List.of(task2), manager.getClaimableTasks());
    }

    @Test
    void testGetClaimableTasksExcludesCancelled() {
        TeamTaskManager manager = newManager();
        TaskRecord task1 = manager.add("Task 1", "Content 1", "task-1", List.of());
        TaskRecord task2 = manager.add("Task 2", "Content 2", "task-2", List.of());
        assertNotNull(manager.cancelResult(task1.getTaskId()));

        assertEquals(List.of(task2), manager.getClaimableTasks());
    }

    @Test
    void testUpdateTaskTitleOnly() {
        TeamTaskManager manager = newManager();
        TaskRecord task = manager.add("Original Title", "Content", "task", List.of());

        TeamTaskManager.TaskOpResult result = manager.updateTaskResult(task.getTaskId(), "Updated Title", null);

        assertTrue(result.ok());
        assertEquals("Updated Title", manager.get(task.getTaskId()).getTitle());
        assertEquals("Content", manager.get(task.getTaskId()).getContent());
    }

    @Test
    void testUpdateTaskContentOnly() {
        TeamTaskManager manager = newManager();
        TaskRecord task = manager.add("Title", "Original Content", "task", List.of());

        TeamTaskManager.TaskOpResult result = manager.updateTaskResult(task.getTaskId(), null, "Updated Content");

        assertTrue(result.ok());
        assertEquals("Title", manager.get(task.getTaskId()).getTitle());
        assertEquals("Updated Content", manager.get(task.getTaskId()).getContent());
    }

    @Test
    void testUpdateTaskBothTitleAndContent() {
        TeamTaskManager manager = newManager();
        TaskRecord task = manager.add("Original Title", "Original Content", "task", List.of());

        TeamTaskManager.TaskOpResult result =
                manager.updateTaskResult(task.getTaskId(), "Updated Title", "Updated Content");

        assertTrue(result.ok());
        assertEquals("Updated Title", manager.get(task.getTaskId()).getTitle());
        assertEquals("Updated Content", manager.get(task.getTaskId()).getContent());
    }

    @Test
    void testUpdateTaskNotFound() {
        TeamTaskManager.TaskOpResult result = newManager().updateTaskResult("nonexistent-task", "New Title", null);

        assertFalse(result.ok());
        assertTrue(result.reason().contains("not found"));
    }

    @Test
    void testUpdateTaskNoneParameters() {
        TeamTaskManager manager = newManager();
        TaskRecord task = manager.add("Title", "Content", "task", List.of());

        TeamTaskManager.TaskOpResult result = manager.updateTaskResult(task.getTaskId(), null, null);

        assertTrue(result.ok());
        assertEquals("Title", manager.get(task.getTaskId()).getTitle());
        assertEquals("Content", manager.get(task.getTaskId()).getContent());
    }

    @Test
    void testAddBatchSuccess() {
        TeamTaskManager manager = newManager();

        List<TaskRecord> createdTasks = manager.addBatch(List.of(
                Map.of("title", "Task 1", "content", "Content 1"),
                Map.of("title", "Task 2", "content", "Content 2"),
                Map.of("title", "Task 3", "content", "Content 3")));

        assertEquals(3, createdTasks.size());
        assertEquals("Task 1", createdTasks.get(0).getTitle());
        assertEquals("Task 2", createdTasks.get(1).getTitle());
        assertEquals("Task 3", createdTasks.get(2).getTitle());
    }

    @Test
    void testAddBatchWithDependencies() {
        TeamTaskManager manager = newManager();
        TaskRecord depTask = manager.add("Dependency Task", "Dep content", "dep", List.of());

        List<TaskRecord> createdTasks = manager.addBatch(List.of(
                Map.of("title", "Task 1", "content", "Content 1"),
                Map.of("title", "Task 2", "content", "Content 2", "dependencies", List.of(depTask.getTaskId())),
                Map.of("title", "Task 3", "content", "Content 3")));

        assertEquals(3, createdTasks.size());
        assertEquals(TaskStatus.PENDING, createdTasks.get(0).getStatus());
        assertEquals(TaskStatus.BLOCKED, createdTasks.get(1).getStatus());
        assertEquals(TaskStatus.PENDING, createdTasks.get(2).getStatus());
    }

    @Test
    void testAddBatchWithCustomTaskIds() {
        TeamTaskManager manager = newManager();

        List<TaskRecord> createdTasks = manager.addBatch(List.of(
                Map.of("title", "Task 1", "content", "Content 1", "task_id", "custom-task-1"),
                Map.of("title", "Task 2", "content", "Content 2", "task_id", "custom-task-2")));

        assertEquals(2, createdTasks.size());
        assertEquals("custom-task-1", createdTasks.get(0).getTaskId());
        assertEquals("custom-task-2", createdTasks.get(1).getTaskId());
    }

    @Test
    void testAddBatchWithInvalidTasks() {
        TeamTaskManager manager = newManager();

        List<TaskRecord> createdTasks = manager.addBatch(List.of(
                Map.of("title", "Valid Task", "content", "Valid content"),
                Map.of("title", "Missing content"),
                Map.of("content", "Missing title"),
                Map.of("title", "Another Valid Task", "content", "Valid content")));

        assertEquals(2, createdTasks.size());
        assertEquals("Valid Task", createdTasks.get(0).getTitle());
        assertEquals("Another Valid Task", createdTasks.get(1).getTitle());
    }

    @Test
    void testAddBatchEmpty() {
        assertEquals(List.of(), newManager().addBatch(List.of()));
    }

    @Test
    void testAddBatchSingleTask() {
        List<TaskRecord> createdTasks =
                newManager().addBatch(List.of(Map.of("title", "Single Task", "content", "Single content")));

        assertEquals(1, createdTasks.size());
        assertEquals("Single Task", createdTasks.get(0).getTitle());
    }

    @Test
    void testCancelAllMultipleTasks() {
        TeamTaskManager manager = newManager();
        manager.add("Task 1", "Content 1", "task-1", List.of());
        manager.add("Task 2", "Content 2", "task-2", List.of());
        manager.add("Task 3", "Content 3", "task-3", List.of());

        List<TaskRecord> cancelled = manager.cancelAllTasks();

        assertEquals(3, cancelled.size());
        assertTrue(cancelled.stream().allMatch(task -> task.getStatus() == TaskStatus.CANCELLED));
    }

    @Test
    void testCancelAllMixedStatus() {
        TeamTaskManager manager = newManager();
        manager.add("Pending", "Content", "pending", List.of());
        TaskRecord claimed = manager.add("Claimed", "Content", "claimed", List.of());
        assertTrue(manager.claim(claimed.getTaskId()));
        TaskRecord cancelledTask = manager.add("Cancelled", "Content", "cancelled", List.of());
        assertNotNull(manager.cancelResult(cancelledTask.getTaskId()));
        TaskRecord completed = manager.add("Completed", "Content", "completed", List.of());
        assertTrue(manager.claim(completed.getTaskId()));
        assertTrue(manager.complete(completed.getTaskId()));

        List<TaskRecord> cancelled = manager.cancelAllTasks();

        assertEquals(2, cancelled.size());
        assertEquals(TaskStatus.CANCELLED, manager.get("pending").getStatus());
        assertEquals(TaskStatus.CANCELLED, manager.get("claimed").getStatus());
        assertEquals(TaskStatus.COMPLETED, manager.get("completed").getStatus());
    }

    @Test
    void testCancelAllNoActiveTasks() {
        TeamTaskManager manager = newManager();
        TaskRecord cancelledTask = manager.add("Cancelled", "Content", "cancelled", List.of());
        assertNotNull(manager.cancelResult(cancelledTask.getTaskId()));
        TaskRecord completed = manager.add("Completed", "Content", "completed", List.of());
        assertTrue(manager.claim(completed.getTaskId()));
        assertTrue(manager.complete(completed.getTaskId()));

        assertEquals(List.of(), manager.cancelAllTasks());
    }

    @Test
    void testCancelAllEmptyTeam() {
        assertEquals(List.of(), newManager().cancelAllTasks());
    }

    @Test
    void testResetClaimedTask() {
        TeamTaskManager manager = newManager();
        TaskRecord task = manager.add("Test Task", "Content", "task", List.of());
        assertTrue(manager.claim(task.getTaskId()));

        TeamTaskManager.TaskOpResult result = manager.resetResult(task.getTaskId());

        assertTrue(result.ok());
        assertEquals(TaskStatus.PENDING, manager.get(task.getTaskId()).getStatus());
        assertNull(manager.get(task.getTaskId()).getAssignee());
    }

    @Test
    void testResetNonexistentTask() {
        TeamTaskManager.TaskOpResult result = newManager().resetResult("nonexistent-task-id");

        assertFalse(result.ok());
        assertTrue(result.reason().contains("not found"));
    }

    @Test
    void testResetPendingTaskFails() {
        TeamTaskManager manager = newManager();
        TaskRecord task = manager.add("Test Task", "Content", "task", List.of());

        TeamTaskManager.TaskOpResult result = manager.resetResult(task.getTaskId());

        assertFalse(result.ok());
        assertTrue(result.reason().contains("pending"));
        assertEquals(TaskStatus.PENDING, manager.get(task.getTaskId()).getStatus());
    }

    @Test
    void testResetCompletedTaskFails() {
        TeamTaskManager manager = newManager();
        TaskRecord task = manager.add("Test Task", "Content", "task", List.of());
        assertTrue(manager.claim(task.getTaskId()));
        assertTrue(manager.complete(task.getTaskId()));

        TeamTaskManager.TaskOpResult result = manager.resetResult(task.getTaskId());

        assertFalse(result.ok());
        assertTrue(result.reason().contains("completed"));
        assertEquals(TaskStatus.COMPLETED, manager.get(task.getTaskId()).getStatus());
    }

    @Test
    void testResetCancelledTaskFails() {
        TeamTaskManager manager = newManager();
        TaskRecord task = manager.add("Test Task", "Content", "task", List.of());
        assertNotNull(manager.cancelResult(task.getTaskId()));

        TeamTaskManager.TaskOpResult result = manager.resetResult(task.getTaskId());

        assertFalse(result.ok());
        assertTrue(result.reason().contains("cancelled"));
        assertEquals(TaskStatus.CANCELLED, manager.get(task.getTaskId()).getStatus());
    }

    @Test
    void testGetTasksByAssigneeEmpty() {
        assertEquals(List.of(), newManager().getTasksByAssignee("member1"));
    }

    @Test
    void testGetTasksByAssigneeWithClaimedTasks() {
        TeamTaskManager manager = newManager();
        TaskRecord task1 = manager.add("Task 1", "Content 1", "task-1", List.of());
        TaskRecord task2 = manager.add("Task 2", "Content 2", "task-2", List.of());
        assertTrue(manager.claim(task1.getTaskId()));
        assertTrue(manager.claim(task2.getTaskId()));

        List<TaskRecord> tasks = manager.getTasksByAssignee("member1");

        assertEquals(2, tasks.size());
        assertTrue(tasks.stream().map(TaskRecord::getTaskId).toList().containsAll(List.of("task-1", "task-2")));
    }

    @Test
    void testGetTasksByAssigneeWithStatusFilter() {
        TeamTaskManager manager = newManager();
        TaskRecord task1 = manager.add("Task 1", "Content 1", "task-1", List.of());
        assertTrue(manager.claim(task1.getTaskId()));
        assertTrue(manager.reset(task1.getTaskId()));
        TaskRecord task2 = manager.add("Task 2", "Content 2", "task-2", List.of());
        assertTrue(manager.claim(task2.getTaskId()));

        List<TaskRecord> claimedTasks = manager.getTasksByAssignee("member1", TaskStatus.CLAIMED);
        assertEquals(1, claimedTasks.size());
        assertEquals(task2.getTaskId(), claimedTasks.get(0).getTaskId());

        assertTrue(manager.complete(task2.getTaskId()));
        List<TaskRecord> completedTasks = manager.getTasksByAssignee("member1", TaskStatus.COMPLETED);
        assertEquals(1, completedTasks.size());
        assertEquals(task2.getTaskId(), completedTasks.get(0).getTaskId());
    }

    @Test
    void testGetTasksByAssigneeDifferentMembers() {
        TeamTaskManager manager = newManager();
        TaskRecord task = manager.add("Task 1", "Content 1", "task", List.of());
        assertTrue(manager.claim(task.getTaskId()));

        assertEquals(1, manager.getTasksByAssignee("member1").size());
        assertEquals(0, manager.getTasksByAssignee("member2").size());
    }

    @Test
    void testAddDependenciesRejectsCycle() {
        TeamTaskManager manager = newManager();
        TaskRecord a = manager.add("A", "c", "a", List.of());
        TaskRecord b = manager.add("B", "c", "b", List.of(a.getTaskId()));

        TeamTaskManager.TaskOpResult result = manager.addDependenciesResult(a.getTaskId(), List.of(b.getTaskId()));

        assertFalse(result.ok());
        assertTrue(result.reason().contains("Circular dependency"));
        assertEquals(List.of(), manager.getDependencies(a.getTaskId()));
    }

    @Test
    void testAddDependenciesRefreshesStatus() {
        TeamTaskManager manager = newManager();
        TaskRecord upstream = manager.add("Up", "c", "up", List.of());
        TaskRecord downstream = manager.add("Down", "c", "down", List.of());
        assertEquals(TaskStatus.PENDING, downstream.getStatus());

        TeamTaskManager.TaskOpResult result =
                manager.addDependenciesResult(downstream.getTaskId(), List.of(upstream.getTaskId()));

        assertTrue(result.ok());
        assertEquals(TaskStatus.BLOCKED, manager.get(downstream.getTaskId()).getStatus());
    }

    @Test
    void testCancelUnblocksDownstreamAtManagerLayer() {
        TeamTaskManager manager = newManager();
        TaskRecord upstream = manager.add("Up", "c", "up", List.of());
        TaskRecord downstream = manager.add("Down", "c", "down", List.of(upstream.getTaskId()));
        assertEquals(TaskStatus.BLOCKED, downstream.getStatus());

        TaskRecord result = manager.cancelResult(upstream.getTaskId());

        assertNotNull(result);
        assertEquals(TaskStatus.CANCELLED, result.getStatus());
        assertEquals(TaskStatus.PENDING, manager.get(downstream.getTaskId()).getStatus());
    }

    @Test
    void testAssignToExistingMemberSucceeds() {
        TeamTaskManager manager = newManager();
        TaskRecord task = manager.add("T", "c", "task", List.of());

        TeamTaskManager.TaskOpResult result = manager.assignResult(task.getTaskId(), "member1");

        assertTrue(result.ok());
        assertEquals(TaskStatus.CLAIMED, manager.get(task.getTaskId()).getStatus());
        assertEquals("member1", manager.get(task.getTaskId()).getAssignee());
    }

    @Test
    void testAssignToUnknownMemberFailsAtManagerLayer() {
        TeamTaskManager manager = newManager();
        TaskRecord task = manager.add("T", "c", "task", List.of());

        TeamTaskManager.TaskOpResult result = manager.assignResult(task.getTaskId(), "ghost");

        assertFalse(result.ok());
        assertTrue(result.reason().contains("ghost"));
        assertEquals(TaskStatus.PENDING, manager.get(task.getTaskId()).getStatus());
        assertNull(manager.get(task.getTaskId()).getAssignee());
    }

    @Test
    void pythonStyleViewsExposeEquivalentData() {
        TeamTaskManager manager = newManager();
        TaskRecord first = manager.add("First", "content", "task-1", List.of());
        manager.add("Second", "content", "task-2", List.of());
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
    }
}

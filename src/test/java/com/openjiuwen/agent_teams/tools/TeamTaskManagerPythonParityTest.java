/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.tools;

import static org.assertj.core.api.Assertions.assertThat;

import com.openjiuwen.agent_teams.AgentTeamsContext;
import com.openjiuwen.agent_teams.messager.Messager;
import com.openjiuwen.agent_teams.messager.MessagerHandler;
import com.openjiuwen.agent_teams.schema.TaskCreateResult;
import com.openjiuwen.agent_teams.schema.TaskListResult;
import com.openjiuwen.agent_teams.schema.TaskOpResult;
import com.openjiuwen.agent_teams.schema.TeamEvent;
import com.openjiuwen.agent_teams.schema.TeamRole;
import com.openjiuwen.agent_teams.schema.events.EventMessage;
import com.openjiuwen.agent_teams.schema.status.ExecutionStatus;
import com.openjiuwen.agent_teams.schema.status.MemberMode;
import com.openjiuwen.agent_teams.schema.status.MemberStatus;
import com.openjiuwen.agent_teams.schema.status.TaskStatus;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Supplemental parity tests for {@link TeamTaskManager}.
 *
 * <p>Mirrors Python's {@code tests.unit_tests.agent_teams.test_task_manager} in
 * {@code tests/unit_tests/agent_teams/test_task_manager.py}.</p>
 */
class TeamTaskManagerPythonParityTest {

    private static final String SOURCE = "tests/unit_tests/agent_teams/test_task_manager.py";
    private static final String TEAM = "test_team";
    private static final String MEMBER = "member1";
    private static final String LEADER = "leader1";

    @TempDir
    private Path tempDir;

    @AfterEach
    void resetContext() {
        AgentTeamsContext.resetSessionId(null);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("pythonTestNodes")
    void mirrorsPythonTaskManagerTests(String pythonNodeId, Scenario scenario) throws Exception {
        scenario.run(new Fixture(tempDir));
    }

    private static Stream<Arguments> pythonTestNodes() {
        return Stream.of(
                arg("TestTeamTaskManager::test_add_task_success", TeamTaskManagerPythonParityTest::addTaskSuccess),
                arg("TestTeamTaskManager::test_add_task_with_dependencies",
                        TeamTaskManagerPythonParityTest::addTaskWithDependencies),
                arg("TestAddAsTopPriority::test_add_as_top_priority_blocks_all_pending",
                        TeamTaskManagerPythonParityTest::addAsTopPriorityBlocksAllPending),
                arg("TestAddFailureReasons::test_add_duplicate_task_id_returns_reason",
                        TeamTaskManagerPythonParityTest::addDuplicateTaskIdReturnsReason),
                arg("TestAddFailureReasons::test_add_with_priority_circular_dep_returns_reason",
                        TeamTaskManagerPythonParityTest::addWithPriorityCircularDepReturnsReason),
                arg("TestClaimConflict::test_claim_by_second_member_reports_conflict_not_transition_error",
                        TeamTaskManagerPythonParityTest::claimBySecondMemberReportsConflict),
                arg("TestTaskCompletionWithDependencyResolution::test_complete_task_sets_updated_at",
                        TeamTaskManagerPythonParityTest::completeTaskSetsUpdatedAt),
                arg("TestTaskCompletionWithDependencyResolution::test_complete_task_unblocks_dependent_tasks",
                        TeamTaskManagerPythonParityTest::completeTaskUnblocksDependentTasks),
                arg("TestTaskCompletionWithDependencyResolution::test_complete_task_unblocks_under_concurrent_sessions",
                        TeamTaskManagerPythonParityTest::completeTaskUnblocksUnderConcurrentSessions),
                arg("TestAddWithPriority::test_add_with_priority_basic",
                        TeamTaskManagerPythonParityTest::addWithPriorityBasic),
                arg("TestAddWithPriority::test_add_with_priority_dependencies",
                        TeamTaskManagerPythonParityTest::addWithPriorityDependencies),
                arg("TestAddWithPriority::test_add_with_priority_dependent_tasks",
                        TeamTaskManagerPythonParityTest::addWithPriorityDependentTasks),
                arg("TestAddWithPriority::test_add_with_priority_bidirectional",
                        TeamTaskManagerPythonParityTest::addWithPriorityBidirectional),
                arg("TestAddWithPriority::test_add_with_priority_custom_task_id",
                        TeamTaskManagerPythonParityTest::addWithPriorityCustomTaskId),
                arg("TestCancel::test_cancel_pending_task", TeamTaskManagerPythonParityTest::cancelPendingTask),
                arg("TestCancel::test_cancel_claimed_task", TeamTaskManagerPythonParityTest::cancelClaimedTask),
                arg("TestCancel::test_cancel_nonexistent_task", TeamTaskManagerPythonParityTest::cancelNonexistentTask),
                arg("TestCancel::test_cancel_completed_task_fails",
                        TeamTaskManagerPythonParityTest::cancelCompletedTaskFails),
                arg("TestCancel::test_cancel_already_cancelled_task_is_idempotent",
                        TeamTaskManagerPythonParityTest::cancelAlreadyCancelledTaskIsIdempotent),
                arg("TestGetClaimableTasks::test_get_claimable_tasks_empty",
                        TeamTaskManagerPythonParityTest::getClaimableTasksEmpty),
                arg("TestGetClaimableTasks::test_get_claimable_tasks_pending",
                        TeamTaskManagerPythonParityTest::getClaimableTasksPending),
                arg("TestGetClaimableTasks::test_get_claimable_tasks_excludes_blocked",
                        TeamTaskManagerPythonParityTest::getClaimableTasksExcludesBlocked),
                arg("TestGetClaimableTasks::test_get_claimable_tasks_excludes_claimed",
                        TeamTaskManagerPythonParityTest::getClaimableTasksExcludesClaimed),
                arg("TestGetClaimableTasks::test_get_claimable_tasks_excludes_completed",
                        TeamTaskManagerPythonParityTest::getClaimableTasksExcludesCompleted),
                arg("TestGetClaimableTasks::test_get_claimable_tasks_excludes_cancelled",
                        TeamTaskManagerPythonParityTest::getClaimableTasksExcludesCancelled),
                arg("TestUpdateTask::test_update_task_title_only",
                        TeamTaskManagerPythonParityTest::updateTaskTitleOnly),
                arg("TestUpdateTask::test_update_task_content_only",
                        TeamTaskManagerPythonParityTest::updateTaskContentOnly),
                arg("TestUpdateTask::test_update_task_both_title_and_content",
                        TeamTaskManagerPythonParityTest::updateTaskBothTitleAndContent),
                arg("TestUpdateTask::test_update_task_not_found",
                        TeamTaskManagerPythonParityTest::updateTaskNotFound),
                arg("TestUpdateTask::test_update_task_none_parameters",
                        TeamTaskManagerPythonParityTest::updateTaskNoneParameters),
                arg("TestAddBatch::test_add_batch_success", TeamTaskManagerPythonParityTest::addBatchSuccess),
                arg("TestAddBatch::test_add_batch_with_dependencies",
                        TeamTaskManagerPythonParityTest::addBatchWithDependencies),
                arg("TestAddBatch::test_add_batch_with_custom_task_ids",
                        TeamTaskManagerPythonParityTest::addBatchWithCustomTaskIds),
                arg("TestAddBatch::test_add_batch_with_invalid_tasks",
                        TeamTaskManagerPythonParityTest::addBatchWithInvalidTasks),
                arg("TestAddBatch::test_add_batch_empty", TeamTaskManagerPythonParityTest::addBatchEmpty),
                arg("TestAddBatch::test_add_batch_single_task",
                        TeamTaskManagerPythonParityTest::addBatchSingleTask),
                arg("TestCancelAllTasks::test_cancel_all_multiple_tasks",
                        TeamTaskManagerPythonParityTest::cancelAllMultipleTasks),
                arg("TestCancelAllTasks::test_cancel_all_mixed_status",
                        TeamTaskManagerPythonParityTest::cancelAllMixedStatus),
                arg("TestCancelAllTasks::test_cancel_all_no_active_tasks",
                        TeamTaskManagerPythonParityTest::cancelAllNoActiveTasks),
                arg("TestCancelAllTasks::test_cancel_all_empty_team",
                        TeamTaskManagerPythonParityTest::cancelAllEmptyTeam),
                arg("TestResetTask::test_reset_claimed_task", TeamTaskManagerPythonParityTest::resetClaimedTask),
                arg("TestResetTask::test_reset_nonexistent_task",
                        TeamTaskManagerPythonParityTest::resetNonexistentTask),
                arg("TestResetTask::test_reset_pending_task_fails",
                        TeamTaskManagerPythonParityTest::resetPendingTaskFails),
                arg("TestResetTask::test_reset_completed_task_fails",
                        TeamTaskManagerPythonParityTest::resetCompletedTaskFails),
                arg("TestResetTask::test_reset_cancelled_task_fails",
                        TeamTaskManagerPythonParityTest::resetCancelledTaskFails),
                arg("TestGetTasksByAssignee::test_get_tasks_by_assignee_empty",
                        TeamTaskManagerPythonParityTest::getTasksByAssigneeEmpty),
                arg("TestGetTasksByAssignee::test_get_tasks_by_assignee_with_claimed_tasks",
                        TeamTaskManagerPythonParityTest::getTasksByAssigneeWithClaimedTasks),
                arg("TestGetTasksByAssignee::test_get_tasks_by_assignee_with_status_filter",
                        TeamTaskManagerPythonParityTest::getTasksByAssigneeWithStatusFilter),
                arg("TestGetTasksByAssignee::test_get_tasks_by_assignee_different_members",
                        TeamTaskManagerPythonParityTest::getTasksByAssigneeDifferentMembers),
                arg("test_add_dependencies_rejects_cycle",
                        TeamTaskManagerPythonParityTest::addDependenciesRejectsCycle),
                arg("test_add_dependencies_refreshes_status",
                        TeamTaskManagerPythonParityTest::addDependenciesRefreshesStatus),
                arg("test_cancel_unblocks_downstream_at_manager_layer",
                        TeamTaskManagerPythonParityTest::cancelUnblocksDownstreamAtManagerLayer),
                arg("TestAssign::test_assign_to_existing_member_succeeds",
                        TeamTaskManagerPythonParityTest::assignToExistingMemberSucceeds),
                arg("TestAssign::test_assign_to_unknown_member_fails_at_manager_layer",
                        TeamTaskManagerPythonParityTest::assignToUnknownMemberFailsAtManagerLayer),
                arg("test_complete_last_task_emits_task_list_drained",
                        TeamTaskManagerPythonParityTest::completeLastTaskEmitsTaskListDrained),
                arg("test_complete_non_last_task_does_not_drain",
                        TeamTaskManagerPythonParityTest::completeNonLastTaskDoesNotDrain),
                arg("test_cancel_last_task_emits_task_list_drained",
                        TeamTaskManagerPythonParityTest::cancelLastTaskEmitsTaskListDrained),
                arg("test_cancel_all_tasks_emits_task_list_drained",
                        TeamTaskManagerPythonParityTest::cancelAllTasksEmitsTaskListDrained),
                arg("test_empty_task_list_never_drains",
                        TeamTaskManagerPythonParityTest::emptyTaskListNeverDrains),
                arg("test_plan_mode_submit_approve_and_complete",
                        TeamTaskManagerPythonParityTest::planModeSubmitApproveAndComplete)
        );
    }

    private static Arguments arg(String pythonNodeId, Scenario scenario) {
        return Arguments.of(SOURCE + "::" + pythonNodeId, scenario);
    }

    private static void addTaskSuccess(Fixture fixture) {
        TeamTask task = created(join(fixture.manager.add("Test Task", "Test content")));

        assertThat(task.getTitle()).isEqualTo("Test Task");
        assertThat(task.getContent()).isEqualTo("Test content");
        assertThat(task.getStatus()).isEqualTo(TaskStatus.PENDING.value());
        assertThat(task.getTeamName()).isEqualTo(TEAM);
    }

    private static void addTaskWithDependencies(Fixture fixture) {
        TeamTask task1 = created(join(fixture.manager.add("Task 1", "Content 1")));
        TeamTask task2 = created(join(fixture.manager.add("Task 2", "Content 2")));

        TeamTask task3 = created(join(fixture.manager.add(
                "Task 3", "Content 3", null, List.of(task1.getTaskId(), task2.getTaskId()))));

        assertThat(task3.getStatus()).isEqualTo(TaskStatus.BLOCKED.value());
        assertThat(join(fixture.manager.getDependencies(task3.getTaskId())))
                .extracting(TeamTaskDependency::getDependsOnTaskId)
                .containsExactlyInAnyOrder(task1.getTaskId(), task2.getTaskId());
    }

    private static void addAsTopPriorityBlocksAllPending(Fixture fixture) {
        TeamTask task1 = created(join(fixture.manager.add("Task 1", "Content 1")));
        TeamTask task2 = created(join(fixture.manager.add("Task 2", "Content 2")));
        TeamTask task3 = created(join(fixture.manager.add("Task 3", "Content 3")));

        TeamTask topTask = created(join(fixture.manager.addAsTopPriority("Top Priority Task", "Urgent content", null)));

        assertThat(topTask.getStatus()).isEqualTo(TaskStatus.PENDING.value());
        assertThat(fixture.task(task1.getTaskId()).getStatus()).isEqualTo(TaskStatus.BLOCKED.value());
        assertThat(fixture.task(task2.getTaskId()).getStatus()).isEqualTo(TaskStatus.BLOCKED.value());
        assertThat(fixture.task(task3.getTaskId()).getStatus()).isEqualTo(TaskStatus.BLOCKED.value());
    }

    private static void addDuplicateTaskIdReturnsReason(Fixture fixture) {
        TaskCreateResult first = join(fixture.manager.add("Original", "Content", "dup-1", null));
        TaskCreateResult second = join(fixture.manager.add("Conflict", "Content", "dup-1", null));

        assertThat(first.ok()).isTrue();
        assertThat(second.ok()).isFalse();
        assertThat(second.reason()).contains("dup-1");
    }

    private static void addWithPriorityCircularDepReturnsReason(Fixture fixture) {
        assertThat(join(fixture.manager.add("A", "ca", "a", null)).ok()).isTrue();
        assertThat(join(fixture.manager.addWithPriority("B", "cb", "b", List.of("a"), null)).ok()).isTrue();

        TaskCreateResult result = join(fixture.manager.addWithPriority("C", "cc", "c", List.of("b"), List.of("a")));

        assertThat(result.ok()).isFalse();
        assertThat(result.reason()).contains("c");
    }

    private static void claimBySecondMemberReportsConflict(Fixture ignored) {
        InMemoryTeamDatabase db = new InMemoryTeamDatabase();
        RecordingMessager messager = new RecordingMessager();
        db.createTeam("conflict_team", "Conflict Team", "leader", null, null).join();
        createMember(db, "m1", "conflict_team", MemberMode.BUILD_MODE);
        createMember(db, "m2", "conflict_team", MemberMode.BUILD_MODE);
        TeamTaskManager m1 = new TeamTaskManager("conflict_team", "m1", db, messager);
        TeamTaskManager m2 = new TeamTaskManager("conflict_team", "m2", db, messager);

        TeamTask task = created(join(m1.add("Shared Task", "Work")));
        TaskOpResult first = join(m1.claim(task.getTaskId()));
        TaskOpResult second = join(m2.claim(task.getTaskId()));

        assertThat(first.ok()).isTrue();
        assertThat(second.ok()).isFalse();
        assertThat(second.reason()).contains("already claimed by m1");
        TeamTask state = join(m1.get(task.getTaskId())).orElseThrow();
        assertThat(state.getStatus()).isEqualTo(TaskStatus.CLAIMED.value());
        assertThat(state.getAssignee()).isEqualTo("m1");
    }

    private static void completeTaskSetsUpdatedAt(Fixture fixture) {
        TeamTask task = created(join(fixture.manager.add("Test Task", "Content")));
        assertThat(join(fixture.manager.claim(task.getTaskId())).ok()).isTrue();
        Long claimedAt = fixture.task(task.getTaskId()).getUpdatedAt();

        assertThat(join(fixture.manager.complete(task.getTaskId())).ok()).isTrue();

        TeamTask updated = fixture.task(task.getTaskId());
        assertThat(updated.getUpdatedAt()).isGreaterThanOrEqualTo(claimedAt);
        assertThat(updated.getStatus()).isEqualTo(TaskStatus.COMPLETED.value());
    }

    private static void completeTaskUnblocksDependentTasks(Fixture fixture) {
        TeamTask task1 = created(join(fixture.manager.add("Task 1", "Content 1")));
        TeamTask task2 = created(join(fixture.manager.add("Task 2", "Content 2", null, List.of(task1.getTaskId()))));
        TeamTask task3 = created(join(fixture.manager.add("Task 3", "Content 3", null, List.of(task1.getTaskId()))));

        assertThat(join(fixture.manager.claim(task1.getTaskId())).ok()).isTrue();
        assertThat(task2.getStatus()).isEqualTo(TaskStatus.BLOCKED.value());
        assertThat(fixture.task(task3.getTaskId()).getStatus()).isEqualTo(TaskStatus.BLOCKED.value());

        assertThat(join(fixture.manager.complete(task1.getTaskId())).ok()).isTrue();

        assertThat(fixture.task(task2.getTaskId()).getStatus()).isEqualTo(TaskStatus.PENDING.value());
        assertThat(fixture.task(task3.getTaskId()).getStatus()).isEqualTo(TaskStatus.PENDING.value());
    }

    private static void completeTaskUnblocksUnderConcurrentSessions(Fixture fixture) {
        created(join(fixture.manager.add("c1", "", "count-1", null)));
        for (int index = 2; index <= 5; index++) {
            created(join(fixture.manager.add(
                    "c" + index,
                    "",
                    "count-" + index,
                    List.of("count-" + (index - 1)))));
        }
        assertThat(join(fixture.manager.claim("count-1")).ok()).isTrue();
        AtomicBoolean stop = new AtomicBoolean(false);
        CompletableFuture<Void> hammer = CompletableFuture.runAsync(() -> {
            while (!stop.get()) {
                join(fixture.db.getTeamTasks(TEAM, null));
                join(fixture.db.getTeam(TEAM));
                Thread.yield();
            }
        });
        try {
            assertThat(join(fixture.manager.complete("count-1")).ok()).isTrue();
        } finally {
            stop.set(true);
            hammer.join();
        }

        assertThat(fixture.task("count-2").getStatus()).isEqualTo(TaskStatus.PENDING.value());
        assertThat(join(fixture.db.getUnresolvedDependenciesCount("count-2"))).isZero();
    }

    private static void addWithPriorityBasic(Fixture fixture) {
        TeamTask task = created(join(fixture.manager.addWithPriority("Priority Task", "Priority content", null, null, null)));

        assertThat(task.getTitle()).isEqualTo("Priority Task");
        assertThat(task.getStatus()).isEqualTo(TaskStatus.PENDING.value());
    }

    private static void addWithPriorityDependencies(Fixture fixture) {
        TeamTask task1 = created(join(fixture.manager.add("Task 1", "Content 1")));
        TeamTask task2 = created(join(fixture.manager.add("Task 2", "Content 2")));

        TeamTask newTask = created(join(fixture.manager.addWithPriority(
                "Dependent Task",
                "Depends on task1 and task2",
                null,
                List.of(task1.getTaskId(), task2.getTaskId()),
                null)));

        assertThat(newTask.getStatus()).isEqualTo(TaskStatus.BLOCKED.value());
        assertThat(join(fixture.manager.getDependencies(newTask.getTaskId())))
                .extracting(TeamTaskDependency::getDependsOnTaskId)
                .containsExactlyInAnyOrder(task1.getTaskId(), task2.getTaskId());
    }

    private static void addWithPriorityDependentTasks(Fixture fixture) {
        TeamTask task1 = created(join(fixture.manager.add("Task 1", "Content 1")));
        TeamTask task2 = created(join(fixture.manager.add("Task 2", "Content 2")));

        TeamTask priorityTask = created(join(fixture.manager.addWithPriority(
                "High Priority Task",
                "Critical task",
                null,
                null,
                List.of(task1.getTaskId(), task2.getTaskId()))));

        assertThat(priorityTask.getStatus()).isEqualTo(TaskStatus.PENDING.value());
        assertThat(fixture.task(task1.getTaskId()).getStatus()).isEqualTo(TaskStatus.BLOCKED.value());
        assertThat(fixture.task(task2.getTaskId()).getStatus()).isEqualTo(TaskStatus.BLOCKED.value());
        assertThat(join(fixture.manager.getDependencies(task1.getTaskId())))
                .extracting(TeamTaskDependency::getDependsOnTaskId)
                .contains(priorityTask.getTaskId());
        assertThat(join(fixture.manager.getDependencies(task2.getTaskId())))
                .extracting(TeamTaskDependency::getDependsOnTaskId)
                .contains(priorityTask.getTaskId());
    }

    private static void addWithPriorityBidirectional(Fixture fixture) {
        TeamTask taskA = created(join(fixture.manager.add("Task A", "Content A")));
        TeamTask taskC = created(join(fixture.manager.add("Task C", "Content C")));

        TeamTask taskB = created(join(fixture.manager.addWithPriority(
                "Task B",
                "Inserted task",
                null,
                List.of(taskA.getTaskId()),
                List.of(taskC.getTaskId()))));

        assertThat(taskB.getStatus()).isEqualTo(TaskStatus.BLOCKED.value());
        assertThat(join(fixture.manager.getDependencies(taskB.getTaskId())))
                .extracting(TeamTaskDependency::getDependsOnTaskId)
                .contains(taskA.getTaskId());
        assertThat(join(fixture.manager.getDependencies(taskC.getTaskId())))
                .extracting(TeamTaskDependency::getDependsOnTaskId)
                .contains(taskB.getTaskId());
    }

    private static void addWithPriorityCustomTaskId(Fixture fixture) {
        TeamTask task = created(join(fixture.manager.addWithPriority(
                "Custom ID Task", "Content", "custom-task-123", null, null)));

        assertThat(task.getTaskId()).isEqualTo("custom-task-123");
        assertThat(fixture.task("custom-task-123").getTaskId()).isEqualTo("custom-task-123");
    }

    private static void cancelPendingTask(Fixture fixture) {
        TeamTask task = created(join(fixture.manager.add("Task 1", "Content 1")));

        TeamTask result = join(fixture.manager.cancel(task.getTaskId()));

        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo(TaskStatus.CANCELLED.value());
    }

    private static void cancelClaimedTask(Fixture fixture) {
        TeamTask task = created(join(fixture.manager.add("Task 1", "Content 1")));
        assertThat(join(fixture.manager.claim(task.getTaskId())).ok()).isTrue();

        TeamTask result = join(fixture.manager.cancel(task.getTaskId()));

        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo(TaskStatus.CANCELLED.value());
    }

    private static void cancelNonexistentTask(Fixture fixture) {
        assertThat(join(fixture.manager.cancel("nonexistent-task-id"))).isNull();
    }

    private static void cancelCompletedTaskFails(Fixture fixture) {
        TeamTask task = created(join(fixture.manager.add("Task 1", "Content 1")));
        assertThat(join(fixture.manager.claim(task.getTaskId())).ok()).isTrue();
        assertThat(join(fixture.manager.complete(task.getTaskId())).ok()).isTrue();

        TeamTask result = join(fixture.manager.cancel(task.getTaskId()));

        assertThat(result).isNull();
        assertThat(fixture.task(task.getTaskId()).getStatus()).isEqualTo(TaskStatus.COMPLETED.value());
    }

    private static void cancelAlreadyCancelledTaskIsIdempotent(Fixture fixture) {
        TeamTask task = created(join(fixture.manager.add("Task 1", "Content 1")));
        join(fixture.manager.cancel(task.getTaskId()));

        TeamTask result = join(fixture.manager.cancel(task.getTaskId()));

        assertThat(result).isNotNull();
        assertThat(result.getTaskId()).isEqualTo(task.getTaskId());
        assertThat(result.getStatus()).isEqualTo(TaskStatus.CANCELLED.value());
        assertThat(fixture.task(task.getTaskId()).getStatus()).isEqualTo(TaskStatus.CANCELLED.value());
    }

    private static void getClaimableTasksEmpty(Fixture fixture) {
        assertThat(join(fixture.manager.getClaimableTasks())).isEmpty();
    }

    private static void getClaimableTasksPending(Fixture fixture) {
        TeamTask task1 = created(join(fixture.manager.add("Task 1", "Content 1")));
        TeamTask task2 = created(join(fixture.manager.add("Task 2", "Content 2")));

        assertThat(join(fixture.manager.getClaimableTasks()))
                .extracting(TeamTask::getTaskId)
                .containsExactly(task1.getTaskId(), task2.getTaskId());
    }

    private static void getClaimableTasksExcludesBlocked(Fixture fixture) {
        TeamTask task1 = created(join(fixture.manager.add("Task 1", "Content 1")));
        created(join(fixture.manager.add("Task 2", "Content 2", null, List.of(task1.getTaskId()))));

        assertThat(join(fixture.manager.getClaimableTasks()))
                .extracting(TeamTask::getTaskId)
                .containsExactly(task1.getTaskId());
    }

    private static void getClaimableTasksExcludesClaimed(Fixture fixture) {
        TeamTask task1 = created(join(fixture.manager.add("Task 1", "Content 1")));
        TeamTask task2 = created(join(fixture.manager.add("Task 2", "Content 2")));
        join(fixture.manager.claim(task1.getTaskId()));

        assertThat(join(fixture.manager.getClaimableTasks()))
                .extracting(TeamTask::getTaskId)
                .containsExactly(task2.getTaskId());
    }

    private static void getClaimableTasksExcludesCompleted(Fixture fixture) {
        TeamTask task1 = created(join(fixture.manager.add("Task 1", "Content 1")));
        TeamTask task2 = created(join(fixture.manager.add("Task 2", "Content 2")));
        join(fixture.manager.claim(task1.getTaskId()));
        join(fixture.manager.complete(task1.getTaskId()));

        assertThat(join(fixture.manager.getClaimableTasks()))
                .extracting(TeamTask::getTaskId)
                .containsExactly(task2.getTaskId());
    }

    private static void getClaimableTasksExcludesCancelled(Fixture fixture) {
        TeamTask task1 = created(join(fixture.manager.add("Task 1", "Content 1")));
        TeamTask task2 = created(join(fixture.manager.add("Task 2", "Content 2")));
        join(fixture.manager.cancel(task1.getTaskId()));

        assertThat(join(fixture.manager.getClaimableTasks()))
                .extracting(TeamTask::getTaskId)
                .containsExactly(task2.getTaskId());
    }

    private static void updateTaskTitleOnly(Fixture fixture) {
        TeamTask task = created(join(fixture.manager.add("Original Title", "Content")));

        assertThat(join(fixture.manager.updateTask(task.getTaskId(), "Updated Title", null)).ok()).isTrue();

        TeamTask updated = fixture.task(task.getTaskId());
        assertThat(updated.getTitle()).isEqualTo("Updated Title");
        assertThat(updated.getContent()).isEqualTo("Content");
    }

    private static void updateTaskContentOnly(Fixture fixture) {
        TeamTask task = created(join(fixture.manager.add("Title", "Original Content")));

        assertThat(join(fixture.manager.updateTask(task.getTaskId(), null, "Updated Content")).ok()).isTrue();

        TeamTask updated = fixture.task(task.getTaskId());
        assertThat(updated.getTitle()).isEqualTo("Title");
        assertThat(updated.getContent()).isEqualTo("Updated Content");
    }

    private static void updateTaskBothTitleAndContent(Fixture fixture) {
        TeamTask task = created(join(fixture.manager.add("Original Title", "Original Content")));

        assertThat(join(fixture.manager.updateTask(task.getTaskId(), "Updated Title", "Updated Content")).ok()).isTrue();

        TeamTask updated = fixture.task(task.getTaskId());
        assertThat(updated.getTitle()).isEqualTo("Updated Title");
        assertThat(updated.getContent()).isEqualTo("Updated Content");
    }

    private static void updateTaskNotFound(Fixture fixture) {
        TaskOpResult result = join(fixture.manager.updateTask("nonexistent-task", "New Title", null));

        assertThat(result.ok()).isFalse();
        assertThat(result.reason()).contains("not found");
    }

    private static void updateTaskNoneParameters(Fixture fixture) {
        TeamTask task = created(join(fixture.manager.add("Title", "Content")));

        assertThat(join(fixture.manager.updateTask(task.getTaskId(), null, null)).ok()).isTrue();

        TeamTask updated = fixture.task(task.getTaskId());
        assertThat(updated.getTitle()).isEqualTo("Title");
        assertThat(updated.getContent()).isEqualTo("Content");
    }

    private static void addBatchSuccess(Fixture fixture) {
        List<TeamTask> created = createdTasks(join(fixture.manager.addBatch(List.of(
                taskSpec("Task 1", "Content 1"),
                taskSpec("Task 2", "Content 2"),
                taskSpec("Task 3", "Content 3")))));

        assertThat(created).extracting(TeamTask::getTitle).containsExactly("Task 1", "Task 2", "Task 3");
    }

    private static void addBatchWithDependencies(Fixture fixture) {
        TeamTask depTask = created(join(fixture.manager.add("Dependency Task", "Dep content")));

        List<TeamTask> created = createdTasks(join(fixture.manager.addBatch(List.of(
                taskSpec("Task 1", "Content 1"),
                taskSpec("Task 2", "Content 2", null, List.of(depTask.getTaskId())),
                taskSpec("Task 3", "Content 3")))));

        assertThat(created).hasSize(3);
        assertThat(created.get(1).getStatus()).isEqualTo(TaskStatus.BLOCKED.value());
        assertThat(created.get(0).getStatus()).isEqualTo(TaskStatus.PENDING.value());
        assertThat(created.get(2).getStatus()).isEqualTo(TaskStatus.PENDING.value());
    }

    private static void addBatchWithCustomTaskIds(Fixture fixture) {
        List<TeamTask> created = createdTasks(join(fixture.manager.addBatch(List.of(
                taskSpec("Task 1", "Content 1", "custom-task-1", null),
                taskSpec("Task 2", "Content 2", "custom-task-2", null)))));

        assertThat(created).extracting(TeamTask::getTaskId).containsExactly("custom-task-1", "custom-task-2");
    }

    private static void addBatchWithInvalidTasks(Fixture fixture) {
        List<TeamTask> created = createdTasks(join(fixture.manager.addBatch(List.of(
                taskSpec("Valid Task", "Valid content"),
                taskSpec("Missing content", ""),
                taskSpec("", "Missing title"),
                taskSpec("Another Valid Task", "Valid content")))));

        assertThat(created).extracting(TeamTask::getTitle).containsExactly("Valid Task", "Another Valid Task");
    }

    private static void addBatchEmpty(Fixture fixture) {
        assertThat(join(fixture.manager.addBatch(List.of()))).isEmpty();
    }

    private static void addBatchSingleTask(Fixture fixture) {
        List<TeamTask> created = createdTasks(join(fixture.manager.addBatch(List.of(
                taskSpec("Single Task", "Single content")))));

        assertThat(created).singleElement().extracting(TeamTask::getTitle).isEqualTo("Single Task");
    }

    private static void cancelAllMultipleTasks(Fixture fixture) {
        created(join(fixture.manager.add("Task 1", "Content 1")));
        created(join(fixture.manager.add("Task 2", "Content 2")));
        created(join(fixture.manager.add("Task 3", "Content 3")));

        List<TeamTask> cancelled = join(fixture.manager.cancelAllTasks(null));

        assertThat(cancelled).hasSize(3);
        assertThat(cancelled).allMatch(task -> ObjectsCompat.equals(task.getStatus(), TaskStatus.CANCELLED.value()));
    }

    private static void cancelAllMixedStatus(Fixture fixture) {
        created(join(fixture.manager.add("Pending", "Content")));
        TeamTask claimed = created(join(fixture.manager.add("Claimed", "Content")));
        join(fixture.manager.claim(claimed.getTaskId()));
        TeamTask cancelled = created(join(fixture.manager.add("Cancelled", "Content")));
        join(fixture.manager.cancel(cancelled.getTaskId()));
        TeamTask completed = created(join(fixture.manager.add("Completed", "Content")));
        join(fixture.manager.claim(completed.getTaskId()));
        join(fixture.manager.complete(completed.getTaskId()));

        assertThat(join(fixture.manager.cancelAllTasks(null))).hasSize(2);
    }

    private static void cancelAllNoActiveTasks(Fixture fixture) {
        TeamTask cancelled = created(join(fixture.manager.add("Cancelled", "Content")));
        join(fixture.manager.cancel(cancelled.getTaskId()));
        TeamTask completed = created(join(fixture.manager.add("Completed", "Content")));
        join(fixture.manager.claim(completed.getTaskId()));
        join(fixture.manager.complete(completed.getTaskId()));

        assertThat(join(fixture.manager.cancelAllTasks(null))).isEmpty();
    }

    private static void cancelAllEmptyTeam(Fixture fixture) {
        assertThat(join(fixture.manager.cancelAllTasks(null))).isEmpty();
    }

    private static void resetClaimedTask(Fixture fixture) {
        TeamTask task = created(join(fixture.manager.add("Test Task", "Content")));
        join(fixture.manager.claim(task.getTaskId()));
        assertThat(fixture.task(task.getTaskId()).getStatus()).isEqualTo(TaskStatus.CLAIMED.value());
        assertThat(fixture.task(task.getTaskId()).getAssignee()).isEqualTo(MEMBER);

        assertThat(join(fixture.manager.reset(task.getTaskId())).ok()).isTrue();

        TeamTask reset = fixture.task(task.getTaskId());
        assertThat(reset.getStatus()).isEqualTo(TaskStatus.PENDING.value());
        assertThat(reset.getAssignee()).isNull();
    }

    private static void resetNonexistentTask(Fixture fixture) {
        TaskOpResult result = join(fixture.manager.reset("nonexistent-task-id"));

        assertThat(result.ok()).isFalse();
        assertThat(result.reason()).contains("not found");
    }

    private static void resetPendingTaskFails(Fixture fixture) {
        TeamTask task = created(join(fixture.manager.add("Test Task", "Content")));

        TaskOpResult result = join(fixture.manager.reset(task.getTaskId()));

        assertThat(result.ok()).isFalse();
        assertThat(result.reason()).contains("pending");
        assertThat(fixture.task(task.getTaskId()).getStatus()).isEqualTo(TaskStatus.PENDING.value());
    }

    private static void resetCompletedTaskFails(Fixture fixture) {
        TeamTask task = created(join(fixture.manager.add("Test Task", "Content")));
        join(fixture.manager.claim(task.getTaskId()));
        join(fixture.manager.complete(task.getTaskId()));

        TaskOpResult result = join(fixture.manager.reset(task.getTaskId()));

        assertThat(result.ok()).isFalse();
        assertThat(result.reason()).contains("completed");
        assertThat(fixture.task(task.getTaskId()).getStatus()).isEqualTo(TaskStatus.COMPLETED.value());
    }

    private static void resetCancelledTaskFails(Fixture fixture) {
        TeamTask task = created(join(fixture.manager.add("Test Task", "Content")));
        join(fixture.manager.cancel(task.getTaskId()));

        TaskOpResult result = join(fixture.manager.reset(task.getTaskId()));

        assertThat(result.ok()).isFalse();
        assertThat(result.reason()).contains("cancelled");
        assertThat(fixture.task(task.getTaskId()).getStatus()).isEqualTo(TaskStatus.CANCELLED.value());
    }

    private static void getTasksByAssigneeEmpty(Fixture fixture) {
        assertThat(join(fixture.manager.getTasksByAssignee(MEMBER, null))).isEmpty();
    }

    private static void getTasksByAssigneeWithClaimedTasks(Fixture fixture) {
        TeamTask task1 = created(join(fixture.manager.add("Task 1", "Content 1")));
        join(fixture.manager.claim(task1.getTaskId()));
        TeamTask task2 = created(join(fixture.manager.add("Task 2", "Content 2")));
        join(fixture.manager.claim(task2.getTaskId()));

        assertThat(join(fixture.manager.getTasksByAssignee(MEMBER, null)))
                .extracting(TeamTask::getTaskId)
                .containsExactly(task1.getTaskId(), task2.getTaskId());
    }

    private static void getTasksByAssigneeWithStatusFilter(Fixture fixture) {
        TeamTask task1 = created(join(fixture.manager.add("Task 1", "Content 1")));
        join(fixture.manager.claim(task1.getTaskId()));
        join(fixture.manager.reset(task1.getTaskId()));
        TeamTask task2 = created(join(fixture.manager.add("Task 2", "Content 2")));
        join(fixture.manager.claim(task2.getTaskId()));

        assertThat(join(fixture.manager.getTasksByAssignee(MEMBER, TaskStatus.CLAIMED.value())))
                .extracting(TeamTask::getTaskId)
                .containsExactly(task2.getTaskId());

        join(fixture.manager.complete(task2.getTaskId()));
        assertThat(join(fixture.manager.getTasksByAssignee(MEMBER, TaskStatus.COMPLETED.value())))
                .extracting(TeamTask::getTaskId)
                .containsExactly(task2.getTaskId());
    }

    private static void getTasksByAssigneeDifferentMembers(Fixture fixture) {
        TeamTask task1 = created(join(fixture.manager.add("Task 1", "Content 1")));
        join(fixture.manager.claim(task1.getTaskId()));

        assertThat(join(fixture.manager.getTasksByAssignee(MEMBER, null))).hasSize(1);
        assertThat(join(fixture.manager.getTasksByAssignee("member2", null))).isEmpty();
    }

    private static void addDependenciesRejectsCycle(Fixture fixture) {
        TeamTask a = created(join(fixture.manager.add("A", "c")));
        TeamTask b = created(join(fixture.manager.add("B", "c", null, List.of(a.getTaskId()))));

        TaskOpResult result = join(fixture.manager.addDependencies(a.getTaskId(), List.of(b.getTaskId())));

        assertThat(result.ok()).isFalse();
        assertThat(result.reason()).contains("Circular dependency");
        assertThat(join(fixture.manager.getDependencies(a.getTaskId()))).isEmpty();
    }

    private static void addDependenciesRefreshesStatus(Fixture fixture) {
        TeamTask upstream = created(join(fixture.manager.add("Up", "c")));
        TeamTask downstream = created(join(fixture.manager.add("Down", "c")));
        assertThat(downstream.getStatus()).isEqualTo(TaskStatus.PENDING.value());

        assertThat(join(fixture.manager.addDependencies(downstream.getTaskId(), List.of(upstream.getTaskId()))).ok())
                .isTrue();

        assertThat(fixture.task(downstream.getTaskId()).getStatus()).isEqualTo(TaskStatus.BLOCKED.value());
    }

    private static void cancelUnblocksDownstreamAtManagerLayer(Fixture fixture) {
        TeamTask upstream = created(join(fixture.manager.add("Up", "c")));
        TeamTask downstream = created(join(fixture.manager.add("Down", "c", null, List.of(upstream.getTaskId()))));
        assertThat(downstream.getStatus()).isEqualTo(TaskStatus.BLOCKED.value());

        TeamTask result = join(fixture.manager.cancel(upstream.getTaskId()));

        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo(TaskStatus.CANCELLED.value());
        assertThat(fixture.task(downstream.getTaskId()).getStatus()).isEqualTo(TaskStatus.PENDING.value());
    }

    private static void assignToExistingMemberSucceeds(Fixture fixture) {
        TeamTask task = created(join(fixture.manager.add("T", "c")));

        TaskOpResult result = join(fixture.manager.assign(task.getTaskId(), MEMBER));

        assertThat(result.ok()).isTrue();
        TeamTask refreshed = fixture.task(task.getTaskId());
        assertThat(refreshed.getStatus()).isEqualTo(TaskStatus.CLAIMED.value());
        assertThat(refreshed.getAssignee()).isEqualTo(MEMBER);
    }

    private static void assignToUnknownMemberFailsAtManagerLayer(Fixture fixture) {
        TeamTask task = created(join(fixture.manager.add("T", "c")));

        TaskOpResult result = join(fixture.manager.assign(task.getTaskId(), "ghost"));

        assertThat(result.ok()).isFalse();
        assertThat(result.reason()).contains("ghost");
        TeamTask refreshed = fixture.task(task.getTaskId());
        assertThat(refreshed.getStatus()).isEqualTo(TaskStatus.PENDING.value());
        assertThat(refreshed.getAssignee()).isNull();
    }

    private static void completeLastTaskEmitsTaskListDrained(Fixture fixture) {
        TeamTask task = created(join(fixture.manager.add("T", "c")));
        assertThat(join(fixture.manager.claim(task.getTaskId())).ok()).isTrue();

        join(fixture.manager.complete(task.getTaskId()));

        List<Map<String, Object>> drained = fixture.eventPayloads(TeamEvent.TASK_LIST_DRAINED);
        assertThat(drained).hasSize(1);
        assertThat(drained.get(0)).containsEntry("task_count", 1).containsEntry("team_name", TEAM);
    }

    private static void completeNonLastTaskDoesNotDrain(Fixture fixture) {
        TeamTask task1 = created(join(fixture.manager.add("T1", "c")));
        created(join(fixture.manager.add("T2", "c")));
        assertThat(join(fixture.manager.claim(task1.getTaskId())).ok()).isTrue();

        join(fixture.manager.complete(task1.getTaskId()));

        assertThat(fixture.eventPayloads(TeamEvent.TASK_LIST_DRAINED)).isEmpty();
    }

    private static void cancelLastTaskEmitsTaskListDrained(Fixture fixture) {
        TeamTask task = created(join(fixture.manager.add("T", "c")));

        join(fixture.manager.cancel(task.getTaskId()));

        List<Map<String, Object>> drained = fixture.eventPayloads(TeamEvent.TASK_LIST_DRAINED);
        assertThat(drained).hasSize(1);
        assertThat(drained.get(0)).containsEntry("task_count", 1);
    }

    private static void cancelAllTasksEmitsTaskListDrained(Fixture fixture) {
        created(join(fixture.manager.add("T1", "c")));
        created(join(fixture.manager.add("T2", "c")));

        join(fixture.manager.cancelAllTasks(null));

        List<Map<String, Object>> drained = fixture.eventPayloads(TeamEvent.TASK_LIST_DRAINED);
        assertThat(drained).hasSize(1);
        assertThat(drained.get(0)).containsEntry("task_count", 2);
    }

    private static void emptyTaskListNeverDrains(Fixture fixture) {
        join(fixture.manager.cancelAllTasks(null));

        assertThat(fixture.eventPayloads(TeamEvent.TASK_LIST_DRAINED)).isEmpty();
    }

    private static void planModeSubmitApproveAndComplete(Fixture fixture) throws Exception {
        InMemoryTeamDatabase db = new InMemoryTeamDatabase();
        RecordingMessager messager = new RecordingMessager();
        db.createTeam("plan_team", "Plan Team", "leader", null, null).join();
        createMember(db, "leader", "plan_team", MemberMode.BUILD_MODE);
        createMember(db, MEMBER, "plan_team", MemberMode.PLAN_MODE);
        TeamTaskManager manager = new TeamTaskManager(
                "plan_team",
                MEMBER,
                db,
                messager,
                fixture.tempDir.resolve("plans"),
                "plan_1",
                "leader");

        TeamTask task = created(join(manager.add("Plan task", "Do work")));
        assertThat(task.getStatus()).isEqualTo(TaskStatus.PENDING.value());
        assertThat(join(manager.claim(task.getTaskId())).ok()).isFalse();

        TaskOpResult assign = join(manager.assign(task.getTaskId(), MEMBER));
        assertThat(assign.ok()).isTrue();
        TaskOpResult approvalWithoutPlan = join(manager.approvePlan("missing-plan", true, "too early", "leader"));
        assertThat(approvalWithoutPlan.ok()).isFalse();
        assertThat(approvalWithoutPlan.reason()).contains("not found");

        TeamTask beforeMissingPlan = join(manager.get(task.getTaskId())).orElseThrow();
        Map<String, Object> missingPlan = join(manager.submitPlan(
                task.getTaskId(),
                fixture.tempDir.resolve("missing_member_plan.md").toString(),
                null,
                null));
        assertThat(missingPlan).containsEntry("success", false);
        assertThat(join(manager.get(task.getTaskId())).orElseThrow().getStatus()).isEqualTo(beforeMissingPlan.getStatus());
        Path planIndexPath = fixture.tempDir.resolve("plans").resolve("index.json");
        if (Files.exists(planIndexPath)) {
            assertThat(Files.readString(planIndexPath)).doesNotContain(task.getTaskId());
        }

        Path draftPlan = fixture.tempDir.resolve("draft_member_plan.md");
        Files.writeString(draftPlan, "1. inspect\n2. implement\n");
        Map<String, Object> submit = join(manager.submitPlan(task.getTaskId(), draftPlan.toString(), null, null));
        assertThat(submit).containsEntry("success", true);
        assertThat(submit).containsEntry("status", TaskStatus.CLAIMED.value());
        String firstPlanId = String.valueOf(submit.get("plan_id"));
        assertThat(String.valueOf(submit.get("leader_message_id"))).isNotBlank();
        List<TeamMessage> leaderMessages = join(db.getMessages("plan_team", "leader", false, null));
        assertThat(leaderMessages).hasSize(1);
        assertThat(leaderMessages.get(0).getFromMemberName()).isEqualTo(MEMBER);
        assertThat(leaderMessages.get(0).getContent()).contains("Plan ID: " + firstPlanId);
        assertThat(leaderMessages.get(0).getContent()).contains(String.valueOf(submit.get("member_plan_md")));
        Path memberPlanPath = Path.of(String.valueOf(submit.get("member_plan_md")));
        assertThat(Files.readString(memberPlanPath)).startsWith("1. inspect");
        assertThat(memberPlanPath.toAbsolutePath().normalize()).isNotEqualTo(draftPlan.toAbsolutePath().normalize());

        TeamTask claimed = join(manager.get(task.getTaskId())).orElseThrow();
        assertThat(claimed.getStatus()).isEqualTo(TaskStatus.CLAIMED.value());
        assertThat(claimed.getAssignee()).isEqualTo(MEMBER);
        assertThat(join(manager.complete(task.getTaskId())).ok()).isFalse();

        TaskOpResult rejection = join(manager.approvePlan(firstPlanId, false, "revise", "leader"));
        assertThat(rejection.ok()).isTrue();
        assertThat(join(manager.get(task.getTaskId())).orElseThrow().getStatus()).isEqualTo(TaskStatus.CLAIMED.value());
        assertThat(manager.getPlanRecord(firstPlanId)).containsEntry("decision", "reject");
        assertThat(join(manager.complete(task.getTaskId())).ok()).isFalse();
        TaskOpResult staleApproval = join(manager.approvePlan(firstPlanId, true, "ok", "leader"));
        assertThat(staleApproval.ok()).isFalse();
        assertThat(staleApproval.reason()).contains("already reject");

        Path revisedPlan = fixture.tempDir.resolve("revised_member_plan.md");
        Files.writeString(revisedPlan, "1. inspect deeper\n2. implement\n");
        Map<String, Object> resubmit = join(manager.submitPlan(task.getTaskId(), revisedPlan.toString(), null, null));
        assertThat(resubmit).containsEntry("success", true);
        assertThat(resubmit).containsEntry("status", TaskStatus.CLAIMED.value());
        String secondPlanId = String.valueOf(resubmit.get("plan_id"));
        assertThat(secondPlanId).isNotEqualTo(firstPlanId);

        TaskOpResult approval = join(manager.approvePlan(secondPlanId, true, "ok", "leader"));
        assertThat(approval.ok()).isTrue();
        assertThat(manager.getPlanRecord(secondPlanId)).containsEntry("decision", "approve");
        TeamTask approved = join(manager.get(task.getTaskId())).orElseThrow();
        assertThat(approved.getStatus()).isEqualTo(TaskStatus.PLAN_APPROVED.value());
        assertThat(approved.getAssignee()).isEqualTo(MEMBER);

        assertThat(join(manager.complete(task.getTaskId())).ok()).isTrue();
        assertThat(join(manager.get(task.getTaskId())).orElseThrow().getStatus())
                .isEqualTo(TaskStatus.COMPLETED.value());
        assertThat(Files.readString(planIndexPath)).contains("\"status\" : \"completed\"");
    }

    private static TeamTask created(TaskCreateResult result) {
        assertThat(result.ok()).as(result.reason()).isTrue();
        assertThat(result.task()).isInstanceOf(TeamTask.class);
        return (TeamTask) result.task();
    }

    private static List<TeamTask> createdTasks(List<TaskCreateResult> results) {
        List<TeamTask> tasks = new ArrayList<>();
        for (TaskCreateResult result : results) {
            tasks.add(created(result));
        }
        return tasks;
    }

    private static Map<String, Object> taskSpec(String title, String content) {
        return taskSpec(title, content, null, null);
    }

    private static Map<String, Object> taskSpec(
            String title,
            String content,
            String taskId,
            List<String> dependencies) {
        Map<String, Object> spec = new LinkedHashMap<>();
        spec.put("title", title);
        spec.put("content", content);
        if (taskId != null) {
            spec.put("task_id", taskId);
        }
        if (dependencies != null) {
            spec.put("dependencies", dependencies);
        }
        return spec;
    }

    private static void createMember(
            InMemoryTeamDatabase db,
            String memberName,
            String teamName,
            MemberMode mode) {
        db.createMember(
                memberName,
                teamName,
                memberName,
                "{}",
                MemberStatus.BUSY.value(),
                TeamRole.TEAMMATE.value(),
                "desc",
                ExecutionStatus.IDLE.value(),
                mode.value(),
                "prompt",
                "{}"
        ).join();
    }

    private static <T> T join(CompletionStage<T> stage) {
        return stage.toCompletableFuture().join();
    }

    @FunctionalInterface
    private interface Scenario {
        void run(Fixture fixture) throws Exception;
    }

    private static final class Fixture {
        private final Path tempDir;
        private final InMemoryTeamDatabase db = new InMemoryTeamDatabase();
        private final RecordingMessager messager = new RecordingMessager();
        private final TeamTaskManager manager;

        private Fixture(Path tempDir) {
            this.tempDir = tempDir;
            db.createTeam(TEAM, "Test Team", LEADER, null, null).join();
            createMember(db, MEMBER, TEAM, MemberMode.BUILD_MODE);
            manager = new TeamTaskManager(TEAM, MEMBER, db, messager, tempDir.resolve("plans"), "plan_1", LEADER);
        }

        private TeamTask task(String taskId) {
            return join(manager.get(taskId)).orElseThrow();
        }

        private List<Map<String, Object>> eventPayloads(String eventType) {
            return messager.publishedMessages.stream()
                    .filter(message -> eventType.equals(message.getEventType()))
                    .map(EventMessage::getPayloadData)
                    .toList();
        }
    }

    /**
     * Recording messager collaborator for task manager parity checks.
     *
     * <p>Mirrors Python's {@code AsyncMock(spec=Messager)} fixture in
     * {@code tests/unit_tests/agent_teams/test_task_manager.py}.</p>
     */
    private static final class RecordingMessager implements Messager {
        private final List<EventMessage> publishedMessages = new ArrayList<>();

        @Override
        public CompletionStage<Void> start() {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Void> stop() {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Void> publish(String topicId, EventMessage message) {
            publishedMessages.add(message);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Void> subscribe(String topicId, MessagerHandler handler) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Void> unsubscribe(String topicId) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Void> send(String agentId, EventMessage message) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Void> registerDirectMessageHandler(MessagerHandler handler) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Void> unregisterDirectMessageHandler() {
            return CompletableFuture.completedFuture(null);
        }
    }

    private static final class ObjectsCompat {
        private ObjectsCompat() {
        }

        private static boolean equals(Object left, Object right) {
            return left == null ? right == null : left.equals(right);
        }
    }
}

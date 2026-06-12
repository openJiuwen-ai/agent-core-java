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
import com.openjiuwen.agent_teams.schema.TeamRole;
import com.openjiuwen.agent_teams.schema.events.EventMessage;
import com.openjiuwen.agent_teams.schema.status.ExecutionStatus;
import com.openjiuwen.agent_teams.schema.status.MemberMode;
import com.openjiuwen.agent_teams.schema.status.MemberStatus;
import com.openjiuwen.agent_teams.schema.status.TaskStatus;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Focused parity tests for {@link TeamTaskManager}.
 *
 * <p>Mirrors Python's {@code TeamTaskManager} in
 * {@code openjiuwen/agent_teams/tools/task_manager.py}.</p>
 */
class TeamTaskManagerTest {

    @TempDir
    private Path tempDir;

    @AfterEach
    void resetContext() {
        AgentTeamsContext.resetSessionId(null);
    }

    @Test
    void addWithDependenciesListsBlockedByAndPublishesCreatedEvents() {
        AgentTeamsContext.setSessionId("task-session");
        InMemoryTeamDatabase database = database();
        RecordingMessager messager = new RecordingMessager();
        TeamTaskManager manager = new TeamTaskManager("team-a", "worker", database, messager, tempDir, "plan/a", null);

        TaskCreateResult first = manager.add("First", "do first", "task-1", null).toCompletableFuture().join();
        TaskCreateResult second = manager.add("Second", "do second", "task-2", List.of("task-1"))
                .toCompletableFuture()
                .join();
        TaskListResult listed = manager.listTasksWithDeps(null).toCompletableFuture().join();

        assertThat(first.ok()).isTrue();
        assertThat(second.ok()).isTrue();
        assertThat(database.getTask("task-2").join()).get()
                .extracting(TeamTask::getStatus)
                .isEqualTo(TaskStatus.BLOCKED.value());
        assertThat(listed.getTasks()).extracting("taskId").containsExactly("task-1", "task-2");
        assertThat(listed.getTasks().get(1).getBlockedBy()).containsExactly("task-1");
        assertThat(messager.eventTypes()).containsExactly("task_created", "task_created");
        assertThat(messager.publishedTopics)
                .containsOnly("session:task-session:team:team-a:task");
    }

    @Test
    void claimRejectsPlanModeAndCompleteUnblocksThenPublishesDrained() {
        InMemoryTeamDatabase database = database();
        RecordingMessager messager = new RecordingMessager();
        TeamTaskManager planManager = new TeamTaskManager("team-a", "planner", database, messager, tempDir, null, null);
        TeamTaskManager buildManager = new TeamTaskManager("team-a", "worker", database, messager, tempDir, null, null);
        buildManager.add("First", "do first", "task-1", null).toCompletableFuture().join();
        buildManager.add("Second", "do second", "task-2", List.of("task-1")).toCompletableFuture().join();

        TaskOpResult planClaim = planManager.claim("task-1").toCompletableFuture().join();
        TaskOpResult buildClaim = buildManager.claim("task-1").toCompletableFuture().join();
        TaskOpResult firstComplete = buildManager.complete("task-1").toCompletableFuture().join();
        buildManager.claim("task-2").toCompletableFuture().join();
        TaskOpResult secondComplete = buildManager.complete("task-2").toCompletableFuture().join();

        assertThat(planClaim.ok()).isFalse();
        assertThat(planClaim.reason()).contains("PLAN_MODE members must call submit_plan");
        assertThat(buildClaim.ok()).isTrue();
        assertThat(firstComplete.ok()).isTrue();
        assertThat(secondComplete.ok()).isTrue();
        assertThat(database.getTask("task-2").join()).get()
                .extracting(TeamTask::getStatus)
                .isEqualTo(TaskStatus.COMPLETED.value());
        assertThat(messager.eventTypes())
                .contains("task_claimed", "task_completed", "task_unblocked", "task_list_drained");
    }

    @Test
    void submitPlanCopiesPlanClaimsTaskNotifiesLeaderAndApprovalControlsDecision() throws Exception {
        InMemoryTeamDatabase database = database();
        RecordingMessager messager = new RecordingMessager();
        TeamTaskManager planner = new TeamTaskManager("team-a", "planner", database, messager, tempDir, "team plan", null);
        TeamTaskManager leader = new TeamTaskManager("team-a", "leader", database, messager, tempDir, "team plan", null);
        leader.add("Plan task", "write a plan", "task-plan", null).toCompletableFuture().join();
        Path sourcePlan = tempDir.resolve("source-plan.md");
        Files.writeString(sourcePlan, "# Plan\n\nDo it.");

        Map<String, Object> submit = planner.submitPlan("task-plan", sourcePlan.toString(), "plan:one", "tool-1")
                .toCompletableFuture()
                .join();
        TaskOpResult reject = leader.approvePlan("plan_one", false, "revise", "leader").toCompletableFuture().join();
        Map<String, Object> resubmit = planner.submitPlan("task-plan", sourcePlan.toString(), "plan:two", "tool-2")
                .toCompletableFuture()
                .join();
        TaskOpResult approve = leader.approvePlan("plan_two", true, "ok", "leader").toCompletableFuture().join();

        assertThat(submit).containsEntry("success", true);
        assertThat(submit).containsEntry("plan_id", "plan_one");
        assertThat(Files.isRegularFile(Path.of(String.valueOf(submit.get("member_plan_md"))))).isTrue();
        assertThat(database.getTask("task-plan").join()).get()
                .extracting(TeamTask::getAssignee)
                .isEqualTo("planner");
        assertThat(database.getMessages("team-a", "leader", false, null).join())
                .extracting(TeamMessage::getToMemberName)
                .contains("leader");
        assertThat(reject.ok()).isTrue();
        assertThat(resubmit).containsEntry("success", true);
        assertThat(approve.ok()).isTrue();
        assertThat(database.getTask("task-plan").join()).get()
                .extracting(TeamTask::getStatus)
                .isEqualTo(TaskStatus.PLAN_APPROVED.value());
        assertThat(leader.getPlanRecord("plan_one")).containsEntry("decision", "reject");
        assertThat(leader.getPlanRecord("plan_two")).containsEntry("decision", "approve");
        assertThat(messager.eventTypes()).contains("task_plan_request", "task_plan_response");
    }

    @Test
    void cancelAllTasksHonorsSkipAssigneesAndPublishesCancellationEvents() {
        InMemoryTeamDatabase database = database();
        RecordingMessager messager = new RecordingMessager();
        TeamTaskManager manager = new TeamTaskManager("team-a", "worker", database, messager, tempDir, null, null);
        manager.add("A", "content", "task-a", null).toCompletableFuture().join();
        manager.add("B", "content", "task-b", null).toCompletableFuture().join();
        manager.claim("task-a").toCompletableFuture().join();

        List<TeamTask> cancelled = manager.cancelAllTasks(Set.of("worker")).toCompletableFuture().join();

        assertThat(cancelled).extracting(TeamTask::getTaskId).containsExactly("task-b");
        assertThat(database.getTask("task-a").join()).get()
                .extracting(TeamTask::getStatus)
                .isEqualTo(TaskStatus.CLAIMED.value());
        assertThat(database.getTask("task-b").join()).get()
                .extracting(TeamTask::getStatus)
                .isEqualTo(TaskStatus.CANCELLED.value());
        assertThat(messager.eventTypes()).contains("task_cancelled");
    }

    private static InMemoryTeamDatabase database() {
        InMemoryTeamDatabase database = new InMemoryTeamDatabase();
        database.createTeam("team-a", "Team A", "leader", "desc", "prompt").join();
        createMember(database, "leader", TeamRole.LEADER, MemberMode.BUILD_MODE);
        createMember(database, "worker", TeamRole.TEAMMATE, MemberMode.BUILD_MODE);
        createMember(database, "planner", TeamRole.TEAMMATE, MemberMode.PLAN_MODE);
        return database;
    }

    private static void createMember(
            InMemoryTeamDatabase database,
            String memberName,
            TeamRole role,
            MemberMode mode) {
        database.createMember(
                memberName,
                "team-a",
                memberName,
                "{}",
                MemberStatus.READY.value(),
                role.value(),
                "desc",
                ExecutionStatus.IDLE.value(),
                mode.value(),
                "prompt",
                "{}"
        ).join();
    }

    /**
     * Recording messager collaborator for {@link TeamTaskManager} tests.
     *
     * <p>Mirrors Python's {@code Messager} dependency used by
     * {@code openjiuwen/agent_teams/tools/task_manager.py}.</p>
     */
    private static final class RecordingMessager implements Messager {
        private final List<String> publishedTopics = new java.util.ArrayList<>();
        private final List<EventMessage> publishedMessages = new java.util.ArrayList<>();

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
            publishedTopics.add(topicId);
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

        private List<String> eventTypes() {
            return publishedMessages.stream().map(EventMessage::getEventType).toList();
        }
    }
}

/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.tools;

import static org.assertj.core.api.Assertions.assertThat;

import com.openjiuwen.agent_teams.messager.Messager;
import com.openjiuwen.agent_teams.messager.MessagerHandler;
import com.openjiuwen.agent_teams.schema.TaskOpResult;
import com.openjiuwen.agent_teams.schema.TeamRole;
import com.openjiuwen.agent_teams.schema.events.EventMessage;
import com.openjiuwen.agent_teams.schema.status.ExecutionStatus;
import com.openjiuwen.agent_teams.schema.status.MemberMode;
import com.openjiuwen.agent_teams.schema.status.MemberStatus;
import com.openjiuwen.agent_teams.schema.status.TaskStatus;
import com.openjiuwen.agent_teams.tools.TeamTools.MemberCompleteTaskTool;
import com.openjiuwen.agent_teams.tools.locales.TeamToolLocales;
import com.openjiuwen.harness.tools.ToolOutput;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.junit.jupiter.api.Test;

/**
 * Self-only member task completion parity tests.
 *
 * <p>Mirrors Python's {@code tests/unit_tests/agent_teams/tools/test_member_complete_task.py}.</p>
 */
class MemberCompleteTaskToolPythonParityTest {

    private static final String TEAM_NAME = "test_team";
    private static final String HUMAN_NAME = "human_alice";
    private static final String OTHER_NAME = "teammate_bob";

    @Test
    void completeOwnAssignedTask() {
        Fixture fixture = fixture();
        fixture.humanManager.add("Done by human", "...", "task-done", null).toCompletableFuture().join();
        TaskOpResult assignResult = fixture.humanManager.assign("task-done", HUMAN_NAME).toCompletableFuture().join();

        ToolOutput result = fixture.tool.invoke(Map.of("task_id", "task-done", "note", "done"))
                .toCompletableFuture()
                .join();

        assertThat(assignResult.ok()).isTrue();
        assertThat(result.isSuccess()).isTrue();
        assertThat(dataMap(result))
                .containsEntry("task_id", "task-done")
                .containsEntry("status", "completed")
                .containsEntry("note", "done");
        assertThat(fixture.database.getTask("task-done").join()).get()
                .extracting(TeamTask::getStatus)
                .isEqualTo(TaskStatus.COMPLETED.value());
    }

    @Test
    void rejectWhenAssigneeIsSomeoneElse() {
        Fixture fixture = fixture();
        fixture.humanManager.add("For Bob", "...", "task-bob", null).toCompletableFuture().join();
        fixture.humanManager.assign("task-bob", OTHER_NAME).toCompletableFuture().join();

        ToolOutput result = fixture.tool.invoke(Map.of("task_id", "task-bob")).toCompletableFuture().join();

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getError()).contains(OTHER_NAME).contains(HUMAN_NAME);
        assertThat(fixture.database.getTask("task-bob").join()).get()
                .extracting(TeamTask::getStatus)
                .isEqualTo(TaskStatus.CLAIMED.value());
    }

    @Test
    void rejectWhenTaskUnassigned() {
        Fixture fixture = fixture();
        fixture.humanManager.add("Floating", "...", "task-floating", null).toCompletableFuture().join();

        ToolOutput result = fixture.tool.invoke(Map.of("task_id", "task-floating")).toCompletableFuture().join();

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getError()).contains("<unassigned>");
    }

    @Test
    void missingTaskIdRejected() {
        Fixture fixture = fixture();

        ToolOutput result = fixture.tool.invoke(Map.of("task_id", "")).toCompletableFuture().join();

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getError()).contains("task_id");
    }

    @Test
    void unknownTaskIdReturnsNotFound() {
        Fixture fixture = fixture();

        ToolOutput result = fixture.tool.invoke(Map.of("task_id", "does-not-exist")).toCompletableFuture().join();

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getError()).contains("does-not-exist");
        assertThat(result.getError().toLowerCase()).contains("not found");
    }

    @Test
    void mapResultIncludesNote() {
        Fixture fixture = fixture();
        fixture.humanManager.add("With note", "...", "task-note", null).toCompletableFuture().join();
        fixture.humanManager.assign("task-note", HUMAN_NAME).toCompletableFuture().join();

        ToolOutput result = fixture.tool.invoke(Map.of("task_id", "task-note", "note", "patched config"))
                .toCompletableFuture()
                .join();
        String text = fixture.tool.mapResult(result);

        assertThat(text).contains("task-note");
        assertThat(text.toLowerCase()).contains("completed");
        assertThat(text).contains("patched config");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> dataMap(ToolOutput output) {
        return (Map<String, Object>) output.getData();
    }

    private static Fixture fixture() {
        InMemoryTeamDatabase database = new InMemoryTeamDatabase();
        database.createTeam(TEAM_NAME, "Test Team", "leader1", "desc", "prompt").join();
        createMember(database, "leader1", TeamRole.LEADER);
        createMember(database, HUMAN_NAME, TeamRole.TEAMMATE);
        createMember(database, OTHER_NAME, TeamRole.TEAMMATE);
        NoopMessager messager = new NoopMessager();
        TeamTaskManager humanManager = new TeamTaskManager(TEAM_NAME, HUMAN_NAME, database, messager);
        TeamTaskManager otherManager = new TeamTaskManager(TEAM_NAME, OTHER_NAME, database, messager);
        MemberCompleteTaskTool tool = new MemberCompleteTaskTool(
                humanManager,
                HUMAN_NAME,
                TeamToolLocales.makeTranslator("en")
        );
        return new Fixture(database, humanManager, otherManager, tool);
    }

    private static void createMember(InMemoryTeamDatabase database, String memberName, TeamRole role) {
        database.createMember(
                memberName,
                TEAM_NAME,
                memberName,
                "{}",
                MemberStatus.READY.value(),
                role.value(),
                "desc",
                ExecutionStatus.IDLE.value(),
                MemberMode.BUILD_MODE.value(),
                "prompt",
                "{}"
        ).join();
    }

    private record Fixture(
            InMemoryTeamDatabase database,
            TeamTaskManager humanManager,
            TeamTaskManager otherManager,
            MemberCompleteTaskTool tool) {
    }

    private static final class NoopMessager implements Messager {
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
}

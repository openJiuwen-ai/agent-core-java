/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.tools;

import static org.assertj.core.api.Assertions.assertThat;

import com.openjiuwen.agent_teams.schema.GraphMutationResult;
import com.openjiuwen.agent_teams.schema.NewTaskSpec;
import com.openjiuwen.agent_teams.schema.TeamRole;
import com.openjiuwen.agent_teams.schema.status.ExecutionStatus;
import com.openjiuwen.agent_teams.schema.status.MemberMode;
import com.openjiuwen.agent_teams.schema.status.MemberStatus;
import com.openjiuwen.agent_teams.schema.status.TaskStatus;
import com.openjiuwen.agent_teams.tools.database.TaskDao;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Focused parity tests for {@link InMemoryTeamDatabase}.
 *
 * <p>Mirrors Python's {@code InMemoryTeamDatabase} in
 * {@code openjiuwen/agent_teams/tools/memory_database.py}.</p>
 */
class InMemoryTeamDatabaseTest {

    @Test
    void configAndDaoFacadeDefaultsMatchMemoryBackendShape() {
        InMemoryTeamDatabase database = new InMemoryTeamDatabase();

        assertThat(database.getConfig().getDbType()).isEqualTo("memory");
        assertThat(database.getConfig().getConnectionString()).isEmpty();
        assertThat(database.isInitialized()).isTrue();
        assertThat(database.getTeam()).isSameAs(database);
        assertThat(database.getMember()).isSameAs(database);
        assertThat(database.getTask()).isSameAs(database);
        assertThat(database.getMessage()).isSameAs(database);
    }

    @Test
    void teamAndMemberOperationsPreserveRoleAndTransitionRules() {
        InMemoryTeamDatabase database = new InMemoryTeamDatabase();

        assertThat(database.createTeam("team-a", "Team A", "leader", "desc", "prompt").join()).isTrue();
        assertThat(database.createTeam("team-a", "Team A", "leader", "desc", "prompt").join()).isFalse();
        assertThat(database.getTeamUpdatedAt("team-a").join()).isPositive();

        assertThat(database.createMember(
                "alice",
                "team-a",
                "Alice",
                "{}",
                MemberStatus.UNSTARTED.value(),
                TeamRole.HUMAN_AGENT.value(),
                "desc",
                ExecutionStatus.IDLE.value(),
                MemberMode.BUILD_MODE.value(),
                "prompt",
                "{}"
        ).join()).isTrue();

        assertThat(database.isHumanAgent("team-a", "alice").join()).isTrue();
        assertThat(database.listHumanAgentNames("team-a").join()).containsExactly("alice");
        assertThat(database.updateMemberStatus("alice", "team-a", MemberStatus.READY.value()).join()).isTrue();
        assertThat(database.updateMemberStatus("alice", "team-a", MemberStatus.UNSTARTED.value()).join()).isFalse();
        assertThat(database.updateMemberExecutionStatus("alice", "team-a", ExecutionStatus.STARTING.value()).join())
                .isTrue();
        assertThat(database.getMember("alice", "team-a").join()).get()
                .extracting(TeamMember::getRole)
                .isEqualTo(TeamRole.HUMAN_AGENT.value());
        assertThat(database.getMembersMaxUpdatedAt("team-a").join()).isPositive();
    }

    @Test
    void dependencyGraphBlocksDetectsCyclesAndUnblocksOnCompletion() {
        InMemoryTeamDatabase database = new InMemoryTeamDatabase();

        GraphMutationResult result = database.mutateDependencyGraph(
                "team-a",
                List.of(
                        new NewTaskSpec("task-a", "A", "A content", TaskStatus.PENDING.value()),
                        new NewTaskSpec("task-b", "B", "B content", TaskStatus.PENDING.value())
                ),
                List.of(new TaskDao.DependencyEdge("task-b", "task-a"))
        ).join();

        assertThat(result.ok()).isTrue();
        assertThat(database.getTask("task-b").join()).get()
                .extracting(TeamTask::getStatus)
                .isEqualTo(TaskStatus.BLOCKED.value());
        assertThat(database.getUnresolvedDependenciesCount("task-b").join()).isEqualTo(1);

        GraphMutationResult cycle = database.mutateDependencyGraph(
                "team-a",
                List.of(),
                List.of(new TaskDao.DependencyEdge("task-a", "task-b"))
        ).join();
        assertThat(cycle.ok()).isFalse();
        assertThat(cycle.reason()).contains("Circular dependency detected");

        assertThat(database.claimTask("task-a", "alice").join()).isTrue();
        TaskDao.TaskTerminationResult completion = database.completeTask("task-a").join().orElseThrow();
        assertThat(completion.task().getStatus()).isEqualTo(TaskStatus.COMPLETED.value());
        assertThat(completion.unblockedTasks()).extracting(TeamTask::getTaskId).containsExactly("task-b");
        assertThat(database.getTask("task-b").join()).get()
                .extracting(TeamTask::getStatus)
                .isEqualTo(TaskStatus.PENDING.value());
    }

    @Test
    void taskClaimResetCancelAndBulkCancelFollowTransitionRules() {
        InMemoryTeamDatabase database = new InMemoryTeamDatabase();
        database.createTask("task-a", "team-a", "A", "A content", TaskStatus.PENDING.value()).join();
        database.createTask("task-b", "team-a", "B", "B content", TaskStatus.PENDING.value()).join();

        assertThat(database.claimTask("task-a", "alice").join()).isTrue();
        assertThat(database.claimTask("task-a", "bob").join()).isFalse();
        assertThat(database.resetTask("task-a").join()).get()
                .extracting(TeamTask::getStatus)
                .isEqualTo(TaskStatus.PENDING.value());
        assertThat(database.assignTask("task-a", "alice").join()).isTrue();

        TaskDao.TaskBulkCancellationResult result = database.cancelAllTasks("team-a", Set.of("alice")).join();

        assertThat(result.cancelledTasks()).extracting(TeamTask::getTaskId).containsExactly("task-b");
        assertThat(database.getTask("task-a").join()).get()
                .extracting(TeamTask::getStatus)
                .isEqualTo(TaskStatus.CLAIMED.value());
    }

    @Test
    void directAndBroadcastMessagesUseReadFlagsAndWatermarks() {
        InMemoryTeamDatabase database = new InMemoryTeamDatabase();
        database.createMember("alice", "team-a", "Alice", "{}", MemberStatus.READY.value()).join();
        database.createMember("bob", "team-a", "Bob", "{}", MemberStatus.READY.value()).join();

        assertThat(database.createMessage("direct-1", "team-a", "alice", "hello", "bob", false, false).join())
                .isTrue();
        assertThat(database.hasUnreadMessages("team-a", false).join()).isTrue();
        assertThat(database.getMessages("team-a", "bob", true, null).join()).extracting(TeamMessage::getMessageId)
                .containsExactly("direct-1");
        assertThat(database.markMessageRead("direct-1", "bob").join()).isTrue();
        assertThat(database.hasUnreadMessages("team-a", false).join()).isFalse();

        assertThat(database.createMessage("broadcast-1", "team-a", "alice", "all", null, true, false).join())
                .isTrue();
        assertThat(database.getBroadcastMessages("team-a", "bob", true, null).join())
                .extracting(TeamMessage::getMessageId)
                .containsExactly("broadcast-1");
        assertThat(database.markMessageRead("broadcast-1", "bob").join()).isTrue();
        assertThat(database.getBroadcastMessages("team-a", "bob", true, null).join()).isEmpty();
        assertThat(database.hasUnreadMessages("team-a", true).join()).isFalse();
    }

    @Test
    void cleanupAndForceDeleteClearExpectedState() {
        InMemoryTeamDatabase database = new InMemoryTeamDatabase();
        database.createTeam("team-a", "Team A", "leader", "desc", "prompt").join();
        database.createMember("alice", "team-a", "Alice", "{}", MemberStatus.READY.value()).join();
        database.createTask("task-a", "team-a", "A", "A content", TaskStatus.PENDING.value()).join();
        database.createMessage("msg-1", "team-a", "alice", "hello", "alice", false, false).join();

        InMemoryTeamDatabase.RuntimeCleanupResult result = database.cleanupAllRuntimeState().join();

        assertThat(result.deletedTables()).containsExactly("memory_dynamic_state");
        assertThat(result.clearedTables()).containsExactly("team_info", "team_member");
        assertThat(database.getTeam("team-a").join()).isEmpty();
        assertThat(database.getTask("task-a").join()).isEmpty();

        database.createTeam("team-b", "Team B", "leader", "desc", "prompt").join();
        assertThat(database.forceDeleteTeamSession("team-b").join()).isTrue();
        assertThat(database.forceDeleteTeamSession("missing").join()).isFalse();
    }
}

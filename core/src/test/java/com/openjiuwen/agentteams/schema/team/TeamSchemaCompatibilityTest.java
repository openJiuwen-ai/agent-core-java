/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentteams.schema.team;

import static org.assertj.core.api.Assertions.assertThat;

import com.openjiuwen.agentteams.schema.status.ExecutionStatus;
import com.openjiuwen.agentteams.schema.status.MemberStatus;
import com.openjiuwen.agentteams.tools.TaskOpResult;
import com.openjiuwen.agentteams.tools.TeamMessage;
import com.openjiuwen.agentteams.tools.TeamTask;

import org.junit.jupiter.api.Test;

/**
 * Mirrors Python 0.1.15 {@code test_member.py} status/execution transitions
 * and {@code test_team.py} TeamRole value mapping.
 */
class TeamSchemaCompatibilityTest {

    // --- TeamRole value mapping (mirrors Python str(Enum)) ---

    @Test
    void teamRole_leaderValue() {
        assertThat(TeamRole.LEADER.value()).isEqualTo("leader");
    }

    @Test
    void teamRole_memberValue() {
        assertThat(TeamRole.MEMBER.value()).isEqualTo("teammate");
    }

    @Test
    void teamRole_humanAgentValue() {
        assertThat(TeamRole.HUMAN_AGENT.value()).isEqualTo("human_agent");
    }

    @Test
    void teamRole_userValue() {
        assertThat(TeamRole.USER.value()).isEqualTo("user");
    }

    // --- TeamMemberSpec defaults ---

    @Test
    void memberSpec_defaults() {
        TeamMemberSpec spec = TeamMemberSpec.builder().name("dev-1").build();
        assertThat(spec.getName()).isEqualTo("dev-1");
        assertThat(spec.getRole()).isEqualTo(TeamRole.MEMBER);
        assertThat(spec.getDescription()).isEmpty();
        assertThat(spec.getAgentId()).isEmpty();
    }

    @Test
    void memberSpec_leaderRole() {
        TeamMemberSpec spec = TeamMemberSpec.builder()
                .name("leader1").role(TeamRole.LEADER).description("Team lead").build();
        assertThat(spec.getRole()).isEqualTo(TeamRole.LEADER);
        assertThat(spec.getDescription()).isEqualTo("Team lead");
    }

    @Test
    void memberSpec_humanAgentRole() {
        TeamMemberSpec spec = TeamMemberSpec.builder()
                .name("human_pm").role(TeamRole.HUMAN_AGENT).build();
        assertThat(spec.getRole()).isEqualTo(TeamRole.HUMAN_AGENT);
    }

    // --- TeamRuntimeContext defaults ---

    @Test
    void runtimeContext_defaults() {
        TeamRuntimeContext ctx = TeamRuntimeContext.builder().build();
        assertThat(ctx.getRole()).isEqualTo(TeamRole.LEADER);
        assertThat(ctx.getLifecycle()).isEqualTo(TeamLifecycle.CREATED);
        assertThat(ctx.getMetadata()).isNotNull();
    }

    @Test
    void runtimeContext_builderSetsFields() {
        TeamRuntimeContext ctx = TeamRuntimeContext.builder()
                .teamId("team-1").memberName("leader").role(TeamRole.LEADER)
                .lifecycle(TeamLifecycle.RUNNING).build();
        assertThat(ctx.getTeamId()).isEqualTo("team-1");
        assertThat(ctx.getMemberName()).isEqualTo("leader");
        assertThat(ctx.getLifecycle()).isEqualTo(TeamLifecycle.RUNNING);
    }

    // --- TeamLifecycle values ---

    @Test
    void teamLifecycle_values() {
        assertThat(TeamLifecycle.CREATED).isNotNull();
        assertThat(TeamLifecycle.RUNNING).isNotNull();
        assertThat(TeamLifecycle.PAUSED).isNotNull();
        assertThat(TeamLifecycle.COMPLETED).isNotNull();
    }

    // --- ExecutionStatus transitions (mirrors Python member state machine) ---

    @Test
    void executionStatus_idleToStartingIsValid() {
        assertThat(ExecutionStatus.IDLE.canTransitionTo(ExecutionStatus.STARTING)).isTrue();
    }

    @Test
    void executionStatus_startingToRunningIsValid() {
        assertThat(ExecutionStatus.STARTING.canTransitionTo(ExecutionStatus.RUNNING)).isTrue();
    }

    @Test
    void executionStatus_runningToCompletingIsValid() {
        assertThat(ExecutionStatus.RUNNING.canTransitionTo(ExecutionStatus.COMPLETING)).isTrue();
    }

    @Test
    void executionStatus_completingToCompletedIsValid() {
        assertThat(ExecutionStatus.COMPLETING.canTransitionTo(ExecutionStatus.COMPLETED)).isTrue();
    }

    @Test
    void executionStatus_completedToIdleIsValid() {
        assertThat(ExecutionStatus.COMPLETED.canTransitionTo(ExecutionStatus.IDLE)).isTrue();
    }

    @Test
    void executionStatus_runningToCancelRequestedIsValid() {
        assertThat(ExecutionStatus.RUNNING.canTransitionTo(ExecutionStatus.CANCEL_REQUESTED)).isTrue();
    }

    @Test
    void executionStatus_cancelRequestedToCancellingIsValid() {
        assertThat(ExecutionStatus.CANCEL_REQUESTED.canTransitionTo(ExecutionStatus.CANCELLING)).isTrue();
    }

    @Test
    void executionStatus_cancellingToCancelledIsValid() {
        assertThat(ExecutionStatus.CANCELLING.canTransitionTo(ExecutionStatus.CANCELLED)).isTrue();
    }

    @Test
    void executionStatus_cancelledToIdleIsValid() {
        assertThat(ExecutionStatus.CANCELLED.canTransitionTo(ExecutionStatus.IDLE)).isTrue();
    }

    @Test
    void executionStatus_failedToIdleIsValid() {
        assertThat(ExecutionStatus.FAILED.canTransitionTo(ExecutionStatus.IDLE)).isTrue();
    }

    @Test
    void executionStatus_timedOutToIdleIsValid() {
        assertThat(ExecutionStatus.TIMED_OUT.canTransitionTo(ExecutionStatus.IDLE)).isTrue();
    }

    // --- MemberStatus transitions (mirrors Python member status machine) ---

    @Test
    void memberStatus_readyToBusyIsValid() {
        assertThat(MemberStatus.READY.canTransitionTo(MemberStatus.BUSY)).isTrue();
    }

    @Test
    void memberStatus_busyToReadyIsValid() {
        assertThat(MemberStatus.BUSY.canTransitionTo(MemberStatus.READY)).isTrue();
    }

    @Test
    void memberStatus_readyToShutdownRequestedIsValid() {
        assertThat(MemberStatus.READY.canTransitionTo(MemberStatus.SHUTDOWN_REQUESTED)).isTrue();
    }

    @Test
    void memberStatus_shutdownRequestedToShutdownIsValid() {
        assertThat(MemberStatus.SHUTDOWN_REQUESTED.canTransitionTo(MemberStatus.SHUTDOWN)).isTrue();
    }

    @Test
    void memberStatus_readyToErrorIsValid() {
        assertThat(MemberStatus.READY.canTransitionTo(MemberStatus.ERROR)).isTrue();
    }

    @Test
    void memberStatus_errorToReadyIsValid() {
        assertThat(MemberStatus.ERROR.canTransitionTo(MemberStatus.READY)).isTrue();
    }

    @Test
    void memberStatus_noTransitionFromShutdown() {
        assertThat(MemberStatus.SHUTDOWN.canTransitionTo(MemberStatus.READY)).isFalse();
        assertThat(MemberStatus.SHUTDOWN.canTransitionTo(MemberStatus.BUSY)).isFalse();
    }

    // --- TeamTask defaults ---

    @Test
    void teamTask_defaults() {
        TeamTask task = TeamTask.builder().taskId("t-1").teamName("team").build();
        assertThat(task.getStatus()).isEqualTo("pending");
        assertThat(task.getAssignee()).isNull();
        assertThat(task.getDependencies()).isEmpty();
    }

    // --- TeamMessage defaults ---

    @Test
    void teamMessage_defaults() {
        TeamMessage msg = TeamMessage.builder()
                .messageId("m-1").teamName("team").fromMemberName("leader")
                .toMemberName("dev-1").content("hello").timestamp(1000L).build();
        assertThat(msg.isBroadcast()).isFalse();
        assertThat(msg.isRead()).isFalse();
    }

    @Test
    void teamMessage_broadcastBuilder() {
        TeamMessage msg = TeamMessage.builder()
                .messageId("m-2").teamName("team").fromMemberName("leader")
                .content("broadcast msg").timestamp(1000L).broadcast(true).build();
        assertThat(msg.isBroadcast()).isTrue();
    }

    // --- TaskOpResult ---

    @Test
    void taskOpResult_success() {
        TaskOpResult result = TaskOpResult.success();
        assertThat(result.isOk()).isTrue();
        assertThat(result.getReason()).isEmpty();
    }

    @Test
    void taskOpResult_fail() {
        TaskOpResult result = TaskOpResult.fail("already claimed by m1");
        assertThat(result.isOk()).isFalse();
        assertThat(result.getReason()).contains("already claimed");
    }
}

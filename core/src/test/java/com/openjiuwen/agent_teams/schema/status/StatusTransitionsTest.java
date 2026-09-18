package com.openjiuwen.agent_teams.schema.status;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StatusTransitionsTest {

    @Test
    void memberTransitionsIncludeNewPausedAndStoppedStates() {
        assertThat(StatusTransitions.isValidTransition(
                MemberStatus.READY,
                MemberStatus.PAUSED,
                StatusTransitions.MEMBER_TRANSITIONS
        )).isTrue();
        assertThat(StatusTransitions.isValidTransition(
                MemberStatus.PAUSED,
                MemberStatus.READY,
                StatusTransitions.MEMBER_TRANSITIONS
        )).isTrue();
        assertThat(StatusTransitions.MEMBER_SETTLED_STATUSES)
                .contains(MemberStatus.PAUSED.value(), MemberStatus.STOPPED.value());
    }

    @Test
    void taskTransitionsPreservePlanApprovalFlow() {
        assertThat(StatusTransitions.isValidTransition(
                TaskStatus.CLAIMED,
                TaskStatus.PLAN_APPROVED,
                StatusTransitions.TASK_TRANSITIONS
        )).isTrue();
        assertThat(StatusTransitions.isValidTransition(
                TaskStatus.COMPLETED,
                TaskStatus.CLAIMED,
                StatusTransitions.TASK_TRANSITIONS
        )).isFalse();
    }
}

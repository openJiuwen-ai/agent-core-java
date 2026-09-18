/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.schema.status;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * State transition tables and validation helpers.
 * <p>
 * Mirrors Python's transition constants and {@code is_valid_transition} in
 * {@code openjiuwen/agent_teams/schema/status.py}.
 */
public final class StatusTransitions {

    public static final Map<MemberStatus, List<MemberStatus>> MEMBER_TRANSITIONS = Map.of(
            MemberStatus.UNSTARTED, List.of(
                    MemberStatus.STARTING,
                    MemberStatus.READY,
                    MemberStatus.SHUTDOWN,
                    MemberStatus.ERROR
            ),
            MemberStatus.STARTING, List.of(
                    MemberStatus.READY,
                    MemberStatus.UNSTARTED,
                    MemberStatus.SHUTDOWN,
                    MemberStatus.ERROR
            ),
            MemberStatus.READY, List.of(
                    MemberStatus.READY,
                    MemberStatus.BUSY,
                    MemberStatus.PAUSED,
                    MemberStatus.STOPPED,
                    MemberStatus.SHUTDOWN_REQUESTED,
                    MemberStatus.SHUTDOWN,
                    MemberStatus.ERROR
            ),
            MemberStatus.BUSY, List.of(
                    MemberStatus.READY,
                    MemberStatus.PAUSED,
                    MemberStatus.STOPPED,
                    MemberStatus.SHUTDOWN_REQUESTED,
                    MemberStatus.ERROR
            ),
            MemberStatus.PAUSED, List.of(
                    MemberStatus.READY,
                    MemberStatus.RESTARTING,
                    MemberStatus.STOPPED,
                    MemberStatus.SHUTDOWN_REQUESTED,
                    MemberStatus.SHUTDOWN,
                    MemberStatus.ERROR
            ),
            MemberStatus.STOPPED, List.of(
                    MemberStatus.READY,
                    MemberStatus.RESTARTING,
                    MemberStatus.SHUTDOWN_REQUESTED,
                    MemberStatus.SHUTDOWN,
                    MemberStatus.ERROR
            ),
            MemberStatus.RESTARTING, List.of(
                    MemberStatus.READY,
                    MemberStatus.STOPPED,
                    MemberStatus.ERROR,
                    MemberStatus.SHUTDOWN
            ),
            MemberStatus.SHUTDOWN_REQUESTED, List.of(
                    MemberStatus.SHUTDOWN,
                    MemberStatus.ERROR
            ),
            MemberStatus.SHUTDOWN, List.of(MemberStatus.RESTARTING),
            MemberStatus.ERROR, List.of(
                    MemberStatus.RESTARTING,
                    MemberStatus.READY,
                    MemberStatus.STOPPED,
                    MemberStatus.SHUTDOWN_REQUESTED,
                    MemberStatus.SHUTDOWN
            )
    );

    public static final Set<String> MEMBER_SETTLED_STATUSES = Set.of(
            MemberStatus.READY.value(),
            MemberStatus.PAUSED.value(),
            MemberStatus.STOPPED.value(),
            MemberStatus.SHUTDOWN.value()
    );

    public static final Map<ExecutionStatus, List<ExecutionStatus>> EXECUTION_TRANSITIONS = Map.of(
            ExecutionStatus.IDLE, List.of(ExecutionStatus.STARTING),
            ExecutionStatus.STARTING, List.of(
                    ExecutionStatus.RUNNING,
                    ExecutionStatus.CANCEL_REQUESTED,
                    ExecutionStatus.CANCELLING,
                    ExecutionStatus.FAILED,
                    ExecutionStatus.TIMED_OUT
            ),
            ExecutionStatus.RUNNING, List.of(
                    ExecutionStatus.CANCEL_REQUESTED,
                    ExecutionStatus.CANCELLING,
                    ExecutionStatus.COMPLETING,
                    ExecutionStatus.FAILED,
                    ExecutionStatus.TIMED_OUT
            ),
            ExecutionStatus.CANCEL_REQUESTED, List.of(
                    ExecutionStatus.CANCELLING,
                    ExecutionStatus.CANCELLED,
                    ExecutionStatus.FAILED,
                    ExecutionStatus.TIMED_OUT
            ),
            ExecutionStatus.CANCELLING, List.of(
                    ExecutionStatus.CANCELLED,
                    ExecutionStatus.FAILED,
                    ExecutionStatus.TIMED_OUT
            ),
            ExecutionStatus.CANCELLED, List.of(ExecutionStatus.IDLE),
            ExecutionStatus.COMPLETING, List.of(
                    ExecutionStatus.COMPLETED,
                    ExecutionStatus.FAILED,
                    ExecutionStatus.TIMED_OUT
            ),
            ExecutionStatus.COMPLETED, List.of(ExecutionStatus.IDLE),
            ExecutionStatus.FAILED, List.of(ExecutionStatus.IDLE),
            ExecutionStatus.TIMED_OUT, List.of(ExecutionStatus.IDLE)
    );

    public static final Map<TaskStatus, List<TaskStatus>> TASK_TRANSITIONS = Map.of(
            TaskStatus.PENDING, List.of(
                    TaskStatus.CLAIMED,
                    TaskStatus.BLOCKED,
                    TaskStatus.CANCELLED
            ),
            TaskStatus.CLAIMED, List.of(
                    TaskStatus.PLAN_APPROVED,
                    TaskStatus.COMPLETED,
                    TaskStatus.CANCELLED,
                    TaskStatus.BLOCKED,
                    TaskStatus.PENDING
            ),
            TaskStatus.PLAN_APPROVED, List.of(
                    TaskStatus.COMPLETED,
                    TaskStatus.PENDING,
                    TaskStatus.CANCELLED
            ),
            TaskStatus.BLOCKED, List.of(
                    TaskStatus.PENDING,
                    TaskStatus.CANCELLED
            ),
            TaskStatus.COMPLETED, List.of(),
            TaskStatus.CANCELLED, List.of()
    );

    private StatusTransitions() {
    }

    public static <E extends Enum<E>> boolean isValidTransition(
            E currentStatus,
            E newStatus,
            Map<E, List<E>> transitions
    ) {
        if (currentStatus == null || newStatus == null || transitions == null) {
            return false;
        }
        List<E> allowed = transitions.get(currentStatus);
        return allowed != null && allowed.contains(newStatus);
    }
}

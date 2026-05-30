// Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.

package com.openjiuwen.agent_teams.schema;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Status transitions module - defines state transitions for team members and tasks.
 * 
 * Mirrors Python's {@code is_valid_transition} and transition tables in
 * {@code openjiuwen.agent_teams.schema.status}.
 * 
 * @since 0.1.12
 */
public final class StatusTransitions {
    
    /**
     * State transition table for MemberStatus.
     */
    public static final Map<MemberStatus, List<MemberStatus>> MEMBER_TRANSITIONS = createMemberTransitions();
    
    /**
     * State transition table for ExecutionStatus.
     */
    public static final Map<ExecutionStatus, List<ExecutionStatus>> EXECUTION_TRANSITIONS = createExecutionTransitions();
    
    /**
     * State transition table for TaskStatus.
     */
    public static final Map<TaskStatus, List<TaskStatus>> TASK_TRANSITIONS = createTaskTransitions();
    
    private static Map<MemberStatus, List<MemberStatus>> createMemberTransitions() {
        EnumMap<MemberStatus, List<MemberStatus>> transitions = new EnumMap<>(MemberStatus.class);
        transitions.put(MemberStatus.UNSTARTED, Arrays.asList(
            MemberStatus.READY,
            MemberStatus.SHUTDOWN,
            MemberStatus.ERROR
        ));
        transitions.put(MemberStatus.READY, Arrays.asList(
            MemberStatus.READY,
            MemberStatus.BUSY,
            MemberStatus.SHUTDOWN_REQUESTED,
            MemberStatus.SHUTDOWN,
            MemberStatus.ERROR
        ));
        transitions.put(MemberStatus.BUSY, Arrays.asList(
            MemberStatus.READY,
            MemberStatus.SHUTDOWN_REQUESTED,
            MemberStatus.ERROR
        ));
        transitions.put(MemberStatus.RESTARTING, Arrays.asList(
            MemberStatus.READY,
            MemberStatus.ERROR,
            MemberStatus.SHUTDOWN
        ));
        transitions.put(MemberStatus.SHUTDOWN_REQUESTED, Arrays.asList(
            MemberStatus.SHUTDOWN,
            MemberStatus.ERROR
        ));
        transitions.put(MemberStatus.SHUTDOWN, Arrays.asList(
            MemberStatus.RESTARTING
        ));
        transitions.put(MemberStatus.ERROR, Arrays.asList(
            MemberStatus.RESTARTING,
            MemberStatus.READY,
            MemberStatus.SHUTDOWN_REQUESTED,
            MemberStatus.SHUTDOWN
        ));
        return Collections.unmodifiableMap(transitions);
    }
    
    private static Map<ExecutionStatus, List<ExecutionStatus>> createExecutionTransitions() {
        EnumMap<ExecutionStatus, List<ExecutionStatus>> transitions = new EnumMap<>(ExecutionStatus.class);
        transitions.put(ExecutionStatus.IDLE, Arrays.asList(ExecutionStatus.STARTING));
        transitions.put(ExecutionStatus.STARTING, Arrays.asList(
            ExecutionStatus.RUNNING,
            ExecutionStatus.CANCEL_REQUESTED,
            ExecutionStatus.CANCELLING,
            ExecutionStatus.FAILED,
            ExecutionStatus.TIMED_OUT
        ));
        transitions.put(ExecutionStatus.RUNNING, Arrays.asList(
            ExecutionStatus.CANCEL_REQUESTED,
            ExecutionStatus.CANCELLING,
            ExecutionStatus.COMPLETING,
            ExecutionStatus.FAILED,
            ExecutionStatus.TIMED_OUT
        ));
        transitions.put(ExecutionStatus.CANCEL_REQUESTED, Arrays.asList(
            ExecutionStatus.CANCELLING,
            ExecutionStatus.CANCELLED,
            ExecutionStatus.FAILED,
            ExecutionStatus.TIMED_OUT
        ));
        transitions.put(ExecutionStatus.CANCELLING, Arrays.asList(
            ExecutionStatus.CANCELLED,
            ExecutionStatus.FAILED,
            ExecutionStatus.TIMED_OUT
        ));
        transitions.put(ExecutionStatus.CANCELLED, Arrays.asList(ExecutionStatus.IDLE));
        transitions.put(ExecutionStatus.COMPLETING, Arrays.asList(
            ExecutionStatus.COMPLETED,
            ExecutionStatus.FAILED,
            ExecutionStatus.TIMED_OUT
        ));
        transitions.put(ExecutionStatus.COMPLETED, Arrays.asList(ExecutionStatus.IDLE));
        transitions.put(ExecutionStatus.FAILED, Arrays.asList(ExecutionStatus.IDLE));
        transitions.put(ExecutionStatus.TIMED_OUT, Arrays.asList(ExecutionStatus.IDLE));
        return Collections.unmodifiableMap(transitions);
    }
    
    private static Map<TaskStatus, List<TaskStatus>> createTaskTransitions() {
        EnumMap<TaskStatus, List<TaskStatus>> transitions = new EnumMap<>(TaskStatus.class);
        transitions.put(TaskStatus.PENDING, Arrays.asList(
            TaskStatus.CLAIMED,
            TaskStatus.BLOCKED,
            TaskStatus.CANCELLED
        ));
        transitions.put(TaskStatus.CLAIMED, Arrays.asList(
            TaskStatus.PLAN_APPROVED,
            TaskStatus.COMPLETED,
            TaskStatus.CANCELLED,
            TaskStatus.BLOCKED,
            TaskStatus.PENDING
        ));
        transitions.put(TaskStatus.PLAN_APPROVED, Arrays.asList(
            TaskStatus.COMPLETED,
            TaskStatus.PENDING,
            TaskStatus.CANCELLED
        ));
        transitions.put(TaskStatus.BLOCKED, Arrays.asList(
            TaskStatus.PENDING,
            TaskStatus.CANCELLED
        ));
        transitions.put(TaskStatus.COMPLETED, Collections.emptyList());
        transitions.put(TaskStatus.CANCELLED, Collections.emptyList());
        return Collections.unmodifiableMap(transitions);
    }
    
    /**
     * Check if a state transition is valid.
     * 
     * @param currentStatus Current status
     * @param newStatus Target status
     * @param transitions Transition table for the status type
     * @return True if transition is valid, False otherwise
     */
    public static <E extends Enum<E>> boolean isValidTransition(
            E currentStatus,
            E newStatus,
            Map<E, List<E>> transitions) {
        if (!transitions.containsKey(currentStatus)) {
            return false;
        }
        return transitions.get(currentStatus).contains(newStatus);
    }
    
    // Private constructor to prevent instantiation
    private StatusTransitions() {
        throw new AssertionError("StatusTransitions class should not be instantiated");
    }
}

/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentteams.schema.events;

/**
 * Team event type constants for cross-process communication.
 *
 * <p>Mirrors Python {@code schema/events.py:TeamEvent}. Events are published via
 * {@code Messager.publish(topic, EventMessage)} where {@code eventType} carries
 * one of these constants. Plain string constants (not enum) to match Python's
 * bare-str class — they serve as both event_type values and framework
 * registration keys.
 *
 * @since 2026/7/9
 */
public final class TeamEvent {
    /** Team created. */
    public static final String CREATED = "team_created";

    /** Team cleaned (all resources released). */
    public static final String CLEANED = "team_cleaned";

    /** Team put on standby (leader pauses polling). */
    public static final String STANDBY = "team_standby";

    /** All tasks completed and members settled. */
    public static final String TEAM_COMPLETED = "team_completed";

    /** Member spawned and added to the team. */
    public static final String MEMBER_SPAWNED = "member_spawned";

    /** Member restarted after failure. */
    public static final String MEMBER_RESTARTED = "member_restarted";

    /** Member status transitioned (e.g., READY -> BUSY). */
    public static final String MEMBER_STATUS_CHANGED = "member_status_changed";

    /** Member execution mode changed. */
    public static final String MEMBER_EXECUTION_CHANGED = "member_execution_changed";

    /** Member shut down gracefully. */
    public static final String MEMBER_SHUTDOWN = "member_shutdown";

    /** Member spawn or execution canceled. */
    public static final String MEMBER_CANCELED = "member_canceled";

    /** Plan submitted for leader approval. */
    public static final String PLAN_APPROVAL = "plan_approval";

    /** Tool-call approval result delivered to teammate. */
    public static final String TOOL_APPROVAL_RESULT = "tool_approval_result";

    /** Anomaly detected in member execution. */
    public static final String ANOMALY_DETECTED = "anomaly_detected";

    /** Direct message between members. */
    public static final String MESSAGE = "message";

    /** Broadcast message to all members. */
    public static final String BROADCAST = "broadcast";

    /** New task created on the board. */
    public static final String TASK_CREATED = "task_created";

    /** Teammate requests leader approval for a plan. */
    public static final String TASK_PLAN_REQUEST = "task_plan_request";

    /** Leader approves or rejects a plan. */
    public static final String TASK_PLAN_RESPONSE = "task_plan_response";

    /** Task metadata updated. */
    public static final String TASK_UPDATED = "task_updated";

    /** Task claimed by a member. */
    public static final String TASK_CLAIMED = "task_claimed";

    /** Task completed by assignee. */
    public static final String TASK_COMPLETED = "task_completed";

    /** Task cancelled. */
    public static final String TASK_CANCELLED = "task_cancelled";

    /** Blocked task unblocked after dependencies completed. */
    public static final String TASK_UNBLOCKED = "task_unblocked";

    /** All terminal tasks drained; team-completion evaluation triggered. */
    public static final String TASK_LIST_DRAINED = "task_list_drained";

    /** Swarmflow orchestration progress milestone. */
    public static final String WORKFLOW_PROGRESS = "workflow_progress";

    /** Git worktree created for a member. */
    public static final String WORKTREE_CREATED = "worktree_created";

    /** Git worktree removed. */
    public static final String WORKTREE_REMOVED = "worktree_removed";

    /** Workspace artifact file updated. */
    public static final String WORKSPACE_ARTIFACT_UPDATED = "workspace_artifact_updated";

    /** Workspace file conflict detected. */
    public static final String WORKSPACE_CONFLICT = "workspace_conflict";

    /** Workspace lock requested by a member. */
    public static final String WORKSPACE_LOCK_REQUEST = "workspace_lock_request";

    /** Workspace lock response (granted or denied). */
    public static final String WORKSPACE_LOCK_RESPONSE = "workspace_lock_response";

    private TeamEvent() {
    }
}

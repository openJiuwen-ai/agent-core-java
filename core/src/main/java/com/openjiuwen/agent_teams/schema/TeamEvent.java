/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.schema;

/**
 * Team event types for cross-process communication.
 * <p>
 * Mirrors Python's {@code TeamEvent} in
 * {@code openjiuwen/agent_teams/schema/events.py}.
 */
public final class TeamEvent {

    public static final String CREATED = "team_created";
    public static final String CLEANED = "team_cleaned";
    public static final String STANDBY = "team_standby";
    public static final String TEAM_COMPLETED = "team_completed";

    public static final String MEMBER_SPAWNED = "member_spawned";
    public static final String MEMBER_RESTARTED = "member_restarted";
    public static final String MEMBER_STATUS_CHANGED = "member_status_changed";
    public static final String MEMBER_EXECUTION_CHANGED = "member_execution_changed";
    public static final String MEMBER_SHUTDOWN = "member_shutdown";
    public static final String MEMBER_CANCELED = "member_canceled";

    public static final String PLAN_APPROVAL = "plan_approval";
    public static final String TOOL_APPROVAL_RESULT = "tool_approval_result";

    public static final String MESSAGE = "message";
    public static final String BROADCAST = "broadcast";

    public static final String TASK_CREATED = "task_created";
    public static final String TASK_PLAN_REQUEST = "task_plan_request";
    public static final String TASK_PLAN_RESPONSE = "task_plan_response";
    public static final String TASK_UPDATED = "task_updated";
    public static final String TASK_CLAIMED = "task_claimed";
    public static final String TASK_COMPLETED = "task_completed";
    public static final String TASK_CANCELLED = "task_cancelled";
    public static final String TASK_UNBLOCKED = "task_unblocked";
    public static final String TASK_LIST_DRAINED = "task_list_drained";

    public static final String WORKTREE_CREATED = "worktree_created";
    public static final String WORKTREE_REMOVED = "worktree_removed";

    public static final String WORKSPACE_ARTIFACT_UPDATED = "workspace_artifact_updated";
    public static final String WORKSPACE_CONFLICT = "workspace_conflict";
    public static final String WORKSPACE_LOCK_REQUEST = "workspace_lock_request";
    public static final String WORKSPACE_LOCK_RESPONSE = "workspace_lock_response";

    private TeamEvent() {
    }
}

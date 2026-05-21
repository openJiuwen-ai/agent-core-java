// Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.

package com.openjiuwen.agent_teams.schema;

/**
 * Team event types for cross-process communication.
 * 
 * These events are published via Messager.publish() where:
 * - event_type is used as topic_id
 * - team_name is used as session_id for team isolation
 * 
 * Mirrors Python's agent_teams.schema.events.TeamEvent
 * 
 * @since 0.1.12
 */
public final class TeamEvent {
    
    // Team lifecycle events
    public static final String CREATED = "team_created";
    public static final String CLEANED = "team_cleaned";
    public static final String STANDBY = "team_standby";
    
    // Member lifecycle events
    public static final String MEMBER_SPAWNED = "member_spawned";
    public static final String MEMBER_RESTARTED = "member_restarted";
    public static final String MEMBER_STATUS_CHANGED = "member_status_changed";
    public static final String MEMBER_EXECUTION_CHANGED = "member_execution_changed";
    public static final String MEMBER_SHUTDOWN = "member_shutdown";
    public static final String MEMBER_CANCELED = "member_canceled";
    
    // Collaboration events
    public static final String PLAN_APPROVAL = "plan_approval";
    public static final String TOOL_APPROVAL_RESULT = "tool_approval_result";
    
    // Messaging events
    public static final String MESSAGE = "message";
    public static final String BROADCAST = "broadcast";
    
    // Task events
    public static final String TASK_CREATED = "task_created";
    public static final String TASK_UPDATED = "task_updated";
    public static final String TASK_CLAIMED = "task_claimed";
    public static final String TASK_COMPLETED = "task_completed";
    public static final String TASK_CANCELLED = "task_cancelled";
    public static final String TASK_UNBLOCKED = "task_unblocked";
    
    // Worktree events
    public static final String WORKTREE_CREATED = "worktree_created";
    public static final String WORKTREE_REMOVED = "worktree_removed";
    
    // Workspace events
    public static final String WORKSPACE_ARTIFACT_UPDATED = "workspace_artifact_updated";
    public static final String WORKSPACE_CONFLICT = "workspace_conflict";
    public static final String WORKSPACE_LOCK_REQUEST = "workspace_lock_request";
    public static final String WORKSPACE_LOCK_RESPONSE = "workspace_lock_response";
    
    // Private constructor to prevent instantiation
    private TeamEvent() {
        throw new AssertionError("TeamEvent class should not be instantiated");
    }
}
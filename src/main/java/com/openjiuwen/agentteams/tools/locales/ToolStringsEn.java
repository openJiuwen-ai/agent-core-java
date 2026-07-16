/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentteams.tools.locales;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * English tool description strings.
 * Mirrors Python tools/locales/en.py.
 */
final class ToolStringsEn {
    static final Map<String, String> STRINGS;

    static {
        Map<String, String> s = new LinkedHashMap<>();
        s.put("build_team.display_name", "Team display name");
        s.put("build_team.team_desc", "Team goal and description");
        s.put("build_team.leader_display_name", "Leader display name");
        s.put("build_team.leader_desc", "Leader description");
        s.put("build_team.enable_hitt", "Enable human-in-the-team (HITT) feature");
        s.put("spawn_member.member_name", "Member name");
        s.put("spawn_member.display_name", "Member display name");
        s.put("spawn_member.desc", "Member description");
        s.put("spawn_member.prompt", "Member system prompt");
        s.put("spawn_member.model_name", "Model name to use");
        s.put("shutdown_member.member_name", "Name of the member to shut down");
        s.put("shutdown_member.force", "Whether to force shutdown");
        s.put("approve_plan.member_name", "Name of the member whose plan is pending approval");
        s.put("approve_plan.approved", "Whether to approve the plan (true: approve, false: reject)");
        s.put("approve_plan.feedback", "Approval feedback (explain reason if rejected)");
        s.put("approve_tool.member_name", "Member name of the tool call to approve");
        s.put("approve_tool.tool_call_id", "The tool call ID to approve");
        s.put("approve_tool.approved", "Whether to approve the tool call (true: approve, false: reject)");
        s.put("approve_tool.feedback", "Approval feedback (explain reason if rejected)");
        s.put("approve_tool.auto_confirm", "Whether to auto-approve subsequent same tool calls");
        s.put("create_task.tasks", "Task list (JSON array)");
        s.put("create_task.tasks.id", "Unique ID of the task");
        s.put("create_task.tasks.title", "Task title");
        s.put("create_task.tasks.content", "Detailed task description");
        s.put("create_task.tasks.depends_on", "List of task IDs this task depends on");
        s.put("create_task.tasks.depended_by", "Depended-on by other tasks");
        s.put("view_task.action", "Action type (list_all/list_pending/list_claimed/query_detail)");
        s.put("view_task.task_id", "Task ID (required for query_detail)");
        s.put("view_task.status", "Filter task list by status");
        s.put("update_task.task_id", "Task ID to update");
        s.put("update_task.status", "New task status (cancelled/completed etc.)");
        s.put("update_task.title", "New task title");
        s.put("update_task.content", "New task description content");
        s.put("update_task.assignee", "Member name to assign the task to");
        s.put("update_task.add_blocked_by", "Task IDs to add as new dependencies");
        s.put("update_task.human_agent_error", "Cannot cancel or reassign human member's tasks");
        s.put("claim_task.task_id", "Task ID to claim");
        s.put("claim_task.status", "Current task status");
        s.put("send_message.to", "Member name of the message recipient");
        s.put("send_message.content", "Message content");
        s.put("send_message.summary", "Message summary (optional)");
        s.put("enter_worktree.name", "Worktree name/tag (alphanumeric with separators, length<=64)");
        s.put("exit_worktree.action", "keep (retain worktree for later) or remove (delete worktree and branch)");
        s.put("exit_worktree.discard_changes",
                "Only when action=remove: true=discard uncommitted changes and force delete");
        s.put("workspace_meta.action", "Action type (list/review/stats etc.)");
        s.put("workspace_meta.path", "Relative path to file/directory");
        STRINGS = Collections.unmodifiableMap(s);
    }

    /**
     * ToolStringsEn.
     * 
     * @since 0.1.7
     */
    private ToolStringsEn() {
    }
}

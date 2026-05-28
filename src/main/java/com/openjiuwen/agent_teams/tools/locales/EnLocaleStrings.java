/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.tools.locales;

import java.util.HashMap;
import java.util.Map;

/**
 * English (en) locale strings for agent team tools.
 *
 * <p>Key convention:</p>
 * <ul>
 *   <li>{@code tool_name._desc} — ToolCard description (lives in {@code descs/en/<tool>.md})</li>
 *   <li>{@code tool_name.param} — top-level param description</li>
 *   <li>{@code tool_name.nested.param} — nested schema param (e.g. task item)</li>
 * </ul>
 *
 * <p>Mirrors Python's {@code en} in {@code openjiuwen.agent_teams.tools.locales.en}.</p>
 */
public final class EnLocaleStrings {

    private static final Map<String, String> STRINGS = new HashMap<>();

    static {
        // ===== build_team ==========================================================
        STRINGS.put("build_team.display_name", "Human-readable display label for the team (e.g. 'Backend Platform Squad')");
        STRINGS.put("build_team.team_desc",
            "Team goal, delivery scope, and global directives. "
            + "All members see this — state collaboration goals and constraints clearly");
        STRINGS.put("build_team.leader_display_name", "Human-readable display label for the leader member");
        STRINGS.put("build_team.leader_desc",
            "Leader persona (professional background, domain expertise); "
            + "influences how members trust and communicate with you");
        STRINGS.put("build_team.enable_hitt",
            "Enable Human in the Team (HITT). When true, registers the "
            + "reserved human_agent member as a first-class teammate. Set to "
            + "true when the user signals intent to join the team "
            + "(e.g. 'I want to join', 'Count me in'); default false");

        // ===== spawn_member ========================================================
        STRINGS.put("spawn_member.member_name",
            "Unique member name (semantic slug, e.g. 'backend-dev-1'); "
            + "serves as primary identifier and routing key "
            + "for all message/approval/task operations");
        STRINGS.put("spawn_member.display_name",
            "Human-readable display label for the member (e.g. 'Backend Expert'). "
            + "Purely presentational — not used for routing");
        STRINGS.put("spawn_member.desc",
            "Long-term role profile of the member, including professional background, "
            + "core expertise, preferred task types, collaboration style, "
            + "and boundaries the member should not own");
        STRINGS.put("spawn_member.prompt",
            "The first instruction the member receives at startup. "
            + "Use it to define initial priorities, task selection guidance, constraints, "
            + "or coordination expectations; give clear direction, "
            + "avoid generic startup filler, "
            + "and do not repeat the generic workflow");
        STRINGS.put("spawn_member.model_name",
            "Optional. Suggested model name for this member "
            + "(e.g. gpt-4, claude-sonnet-4); "
            + "the system picks an appropriate model when omitted");

        // ===== shutdown_member =====================================================
        STRINGS.put("shutdown_member.member_name",
            "member_name of the member to request shutdown for (semantic slug, not display label)");
        STRINGS.put("shutdown_member.force",
            "Whether to force shutdown, default false. "
            + "Use only when the member is stuck, unresponsive, "
            + "or cannot complete a normal shutdown sequence");

        // ===== approve_plan ========================================================
        STRINGS.put("approve_plan.member_name", "member_name of the member who submitted the plan (semantic slug, not display label)");
        STRINGS.put("approve_plan.approved",
            "Whether to approve the current plan. true means proceed to implementation; false means revise it");
        STRINGS.put("approve_plan.feedback",
            "Review feedback. When rejecting, explain the "
            + "reason and revision direction; when approving, "
            + "you may add constraints, reminders, or extra requirements");

        // ===== approve_tool ========================================================
        STRINGS.put("approve_tool.member_name",
            "member_name of the member who initiated the tool approval request (semantic slug, not display label)");
        STRINGS.put("approve_tool.tool_call_id",
            "The interrupted tool_call_id to resume; it should match the tool call in the current approval request");
        STRINGS.put("approve_tool.approved",
            "Whether to approve this tool call. "
            + "true means allow it to continue; "
            + "false means reject it and require an adjusted approach");
        STRINGS.put("approve_tool.feedback",
            "Review feedback. When rejecting, explain the "
            + "reason and an alternative direction; when approving, "
            + "you may add boundaries, risk reminders, or extra constraints");
        STRINGS.put("approve_tool.auto_confirm",
            "Whether to auto-approve future calls to the same tool. "
            + "Default false; enable only when you explicitly "
            + "accept continued use of that tool type");

        // ===== create_task ========================================================
        STRINGS.put("create_task.tasks", "Task list — wrap single tasks in an array too");
        STRINGS.put("create_task.task.task_id", "Custom task ID for dependency reference (auto-generated if omitted)");
        STRINGS.put("create_task.task.title", "Task title — concise description of the goal");
        STRINGS.put("create_task.task.content", "Task details including goals and acceptance criteria");
        STRINGS.put("create_task.task.depends_on", "Prerequisite task IDs that must complete first");
        STRINGS.put("create_task.task.depended_by", "Existing task IDs that should wait for this task (reverse dependency)");

        // ===== view_task ===========================================================
        STRINGS.put("view_task.action",
            "View mode: 'list' (default, summary of all tasks), "
            + "'get' (single task detail, requires task_id), "
            + "'claimable' (pending tasks ready to claim)");
        STRINGS.put("view_task.task_id", "Task ID — required when action=get, ignored otherwise");
        STRINGS.put("view_task.status",
            "Status filter for action=list only: "
            + "pending/claimed/plan_approved/completed/cancelled/blocked. "
            + "Omit to list all.");

        // ===== update_task =========================================================
        STRINGS.put("update_task.task_id", "Task ID to update, or '*' to cancel all tasks");
        STRINGS.put("update_task.status", "Set to 'cancelled' to cancel the task");
        STRINGS.put("update_task.title", "New task title");
        STRINGS.put("update_task.content", "New task content");
        STRINGS.put("update_task.assignee",
            "member_name to assign this task to (only when currently unassigned). A notification is sent to the assignee");
        STRINGS.put("update_task.add_blocked_by",
            "Task IDs to add as new dependencies (this task will be blocked until those tasks complete)");
        STRINGS.put("update_task.error_human_agent_locked_cancel",
            "Task {task_id} is claimed by a human member; this task cannot "
            + "be cancelled. Use send_message to coordinate with that human "
            + "member instead");
        STRINGS.put("update_task.error_human_agent_locked_reassign",
            "Task {task_id} is claimed by a human member; it cannot be "
            + "reassigned to {new_assignee}. Tasks locked by a human member "
            + "must be completed by that human");

        // ===== claim_task =========================================================
        STRINGS.put("claim_task.task_id", "The ID of the task to claim or complete");
        STRINGS.put("claim_task.status", "New status: 'claimed' (start work) or 'completed' (mark done)");

        // ===== send_message ========================================================
        STRINGS.put("send_message.to",
            "Recipient: member_name for point-to-point (e.g. \"backend-dev-1\"); "
            + "\"user\" (teammates only, to reply to the user); "
            + "\"*\" for broadcast");
        STRINGS.put("send_message.content", "Message content with clear action guidance or information");
        STRINGS.put("send_message.summary", "5-10 word summary for message preview and logging");

        // ===== enter_worktree =====================================================
        STRINGS.put("enter_worktree.name",
            "Optional name for the worktree. "
            + "Each \"/\"-separated segment may contain only letters, "
            + "digits, dots, underscores, and dashes; max 64 chars total. "
            + "A random name is generated if not provided");

        // ===== exit_worktree ======================================================
        STRINGS.put("exit_worktree.action", "\"keep\" leaves the worktree and branch on disk; \"remove\" deletes both");
        STRINGS.put("exit_worktree.discard_changes",
            "Required true when action is \"remove\" and "
            + "the worktree has uncommitted files or unmerged commits. "
            + "The tool will refuse and list them otherwise");

        // ===== workspace_meta =====================================================
        STRINGS.put("workspace_meta.action",
            "Operation type: lock (acquire file lock), "
            + "unlock (release file lock), "
            + "locks (list all active locks), "
            + "history (view file version history)");
        STRINGS.put("workspace_meta.path", "Relative path of the target file (required for lock/unlock/history)");
    }

    private EnLocaleStrings() {
        // Utility class - prevent instantiation
    }

    /**
     * Get the locale string for the given key.
     *
     * @param key The locale key (e.g., "build_team.display_name").
     * @return The English locale string, or null if not found.
     */
    public static String get(String key) {
        return STRINGS.get(key);
    }

    /**
     * Get all locale strings.
     *
     * @return A map of all English locale strings.
     */
    public static Map<String, String> getAll() {
        return new HashMap<>(STRINGS);
    }
}
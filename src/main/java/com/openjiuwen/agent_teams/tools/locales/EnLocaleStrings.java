/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.tools.locales;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * English (en) locale strings for agent team tools.
 *
 * <p>Mirrors Python's {@code en} in
 * {@code openjiuwen/agent_teams/tools/locales/en.py}.</p>
 */
public final class EnLocaleStrings {
    private static final Map<String, String> STRINGS = buildStrings();

    private EnLocaleStrings() {
    }

    private static Map<String, String> buildStrings() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("build_team.display_name", "Human-readable display label for the team (e.g. 'Backend Platform Squad')");
        map.put("build_team.team_desc", "Team goal, delivery scope, and global directives. All members see this \u2014 state collaboration goals and constraints clearly");
        map.put("build_team.leader_display_name", "Human-readable display label for the leader member");
        map.put("build_team.leader_desc", "Leader persona (professional background, domain expertise); influences how members trust and communicate with you");
        map.put("build_team.enable_hitt", "Per-instance HITT (Human in the Team) switch; accepts true / false / omitted. Omitted: inherit TeamAgentSpec.enable_hitt (the spec-level capability ceiling). true: explicitly enable for this build \u2014 requires spec.enable_hitt=True or it errors. false: explicitly disable \u2014 every spawn_human_agent request is rejected and any predefined HUMAN_AGENT members in the spec are skipped. Use true when the user wants to join the team; false when human collaboration is explicitly out of scope");
        map.put("spawn_member.member_name", "[PUBLIC] Unique member name (semantic slug, e.g. 'backend-dev-1', DNS-label-style kebab-case). **Must start with a lowercase ASCII letter (a-z); the rest may be lowercase letters, digits (0-9) or hyphen (-)** \u2014 no uppercase, underscore, whitespace, CJK or any other non-ASCII characters. Serves as the primary identifier and the routing key for all message/approval/task operations; must be unique within the team");
        map.put("spawn_member.display_name", "[PUBLIC] Human-readable display label for the member (e.g. 'Backend Expert'); purely presentational, not used for routing. Injected into every other member's system prompt and returned by list_members \u2014 do not put private content here");
        map.put("spawn_member.desc", "[PUBLIC] Long-term role profile of the member, including professional background, core expertise, preferred task types, collaboration style, and boundaries the member should not own. Injected into every other member's system prompt and returned by list_members \u2014 never put your internal assessment of the member, sensitive goals, or confidential strategy here");
        map.put("spawn_member.role_type", "[INTERNAL] Optional. Member role type \u2014 drives framework wiring, never rendered into any member's prompt text. 'teammate' (default) = regular LLM teammate, requires model_name/prompt to drive its avatar. 'human_agent' = human member driven by the real user via HumanAgentInbox; **rejects** model_name and prompt (the framework template manages them) \u2014 passing those fields raises an error. Choosing 'human_agent' requires spec.enable_hitt=True and the current build_team instance must not have disabled HITT. 'bridge_agent' = bridge to an external independent agent (e.g. claudecode / codex / hermes). Behaves as a full teammate locally (claims tasks, sends/receives messages); concrete work output is produced by the remote agent reached over a pure-text protocol, and the local LLM only schedules \u2014 it passes the remote's output through verbatim. Choosing 'bridge_agent' requires non-empty 'desc' (used both as the teammate persona and the remote's connect briefing) and optional mailbox_inject_mode / protocol / adapter_config / model_name. Requires spec.enable_bridge=True and the current build_team instance must not have disabled Bridge. 'external_cli' = launch a third-party CLI agent (claudecode / codex / ...) directly as a teammate; its brain is the CLI subprocess rather than a local LLM, and it sends messages / claims tasks through the auto-injected team MCP tools. Choosing 'external_cli' requires 'cli_agent' (the CLI kind) and non-empty 'desc' (the member persona), and rejects model_name/prompt (the model and config live on the CLI side). 'cli_agent' must name a CLI kind pre-declared in spec.external_cli_agents");
        map.put("spawn_member.cli_agent", "Only used when role_type='external_cli'. Identifier of the third-party CLI agent kind to launch, e.g. 'claude' (claudecode) or 'codex'. Must match a static config entry pre-declared in spec.external_cli_agents \u2014 the launch command, working directory and MCP injection all live in that entry; this field only references it by name");
        map.put("spawn_member.prompt", "[PRIVATE, visible only to this member] Long-term working conventions, injected only into this member's own system prompt: stable working style, technical preferences, collaboration constraints, and any hidden goals or sensitive directives meant only for this member. Do not write current-batch tasks or generic startup filler such as 'start working' or 'check the task list'. Forbidden when role_type='human_agent'");
        map.put("spawn_member.model_name", "Optional. Suggested model name for this member (e.g. gpt-4, claude-sonnet-4); the system picks an appropriate model when omitted. Forbidden when role_type='human_agent'; for role_type='bridge_agent' it selects the local scheduler LLM");
        map.put("spawn_member.mailbox_inject_mode", "Only used when role_type='bridge_agent'. Controls how team-side mailbox messages are wrapped before being relayed to the remote agent. 'passthrough' (default) prefixes only the sender label; 'rephrase' wraps full sender context (role, persona, optional task hint)");
        map.put("spawn_member.protocol", "Only used when role_type='bridge_agent'. Protocol identifier (e.g. 'a2a' / 'acp' / 'claudecode'). Reserved for future BridgeProtocolAdapter lookup; empty string means no adapter is wired yet (bridge degrades to a normal teammate)");
        map.put("spawn_member.adapter_config", "Only used when role_type='bridge_agent'. Free-form adapter configuration (endpoint, auth, relay_timeout_s, ...). Passed verbatim to BridgeProtocolAdapter.connect \u2014 schema is up to the concrete adapter implementation");
        map.put("shutdown_member.member_name", "member_name of the member to request shutdown for (semantic slug, not display label)");
        map.put("shutdown_member.force", "Whether to force shutdown, default false. Use only when the member is stuck, unresponsive, or cannot complete a normal shutdown sequence");
        map.put("approve_plan.plan_id", "Member plan submission ID to approve or reject one exact plan revision.");
        map.put("approve_plan.approved", "Whether to approve the current plan. true means proceed to implementation; false means revise it");
        map.put("approve_plan.feedback", "Review feedback. When rejecting, explain the reason and revision direction; when approving, you may add constraints, reminders, or extra requirements");
        map.put("submit_plan._desc", "Submit a prepared execution-plan Markdown file for a plan-mode task before implementation");
        map.put("submit_plan.task_id", "Task ID to plan before execution");
        map.put("submit_plan.plan_id", "Optional member plan ID; the system generates one when omitted. The Leader uses this plan_id for review");
        map.put("submit_plan.plan_path", "Path to the member-authored Markdown plan file; the system copies it to a managed snapshot for Leader review");
        map.put("approve_tool.member_name", "member_name of the member who initiated the tool approval request (semantic slug, not display label)");
        map.put("approve_tool.tool_call_id", "The interrupted tool_call_id to resume; it should match the tool call in the current approval request");
        map.put("approve_tool.approved", "Whether to approve this tool call. true means allow it to continue; false means reject it and require an adjusted approach");
        map.put("approve_tool.feedback", "Review feedback. When rejecting, explain the reason and an alternative direction; when approving, you may add boundaries, risk reminders, or extra constraints");
        map.put("approve_tool.auto_confirm", "Whether to auto-approve future calls to the same tool. Default false; enable only when you explicitly accept continued use of that tool type");
        map.put("create_task.tasks", "Task list \u2014 wrap single tasks in an array too");
        map.put("create_task.task.task_id", "Custom task ID for dependency reference (auto-generated if omitted)");
        map.put("create_task.task.title", "Task title \u2014 concise description of the goal");
        map.put("create_task.task.content", "Task details including goals and acceptance criteria");
        map.put("create_task.task.depends_on", "Prerequisite task IDs that must complete first");
        map.put("create_task.task.depended_by", "Existing task IDs that should wait for this task (reverse dependency)");
        map.put("view_task.action", "View mode: 'list' (default, summary of all tasks), 'get' (single task detail, requires task_id), 'claimable' (pending tasks ready to claim)");
        map.put("view_task.task_id", "Task ID \u2014 required when action=get, ignored otherwise");
        map.put("view_task.status", "Status filter for action=list only: pending/claimed/plan_approved/completed/cancelled/blocked. Omit to list all.");
        map.put("update_task.task_id", "Task ID to update, or '*' to cancel all tasks");
        map.put("update_task.status", "Set to 'cancelled' to cancel the task");
        map.put("update_task.title", "New task title");
        map.put("update_task.content", "New task content");
        map.put("update_task.assignee", "member_name to assign this task to (only when currently unassigned). A notification is sent to the assignee");
        map.put("update_task.add_blocked_by", "Task IDs to add as new dependencies (this task will be blocked until those tasks complete)");
        map.put("update_task.error_human_agent_locked_cancel", "Task {task_id} is claimed by a human member; this task cannot be cancelled. Use send_message to coordinate with that human member instead");
        map.put("update_task.error_human_agent_locked_reassign", "Task {task_id} is claimed by a human member; it cannot be reassigned to {new_assignee}. Tasks locked by a human member must be completed by that human");
        map.put("claim_task.task_id", "The ID of the task to claim or complete");
        map.put("claim_task.status", "New status: 'claimed' (start work) or 'completed' (mark done)");
        map.put("member_complete_task.task_id", "ID of the task to mark completed (must be a task the leader has assigned to you)");
        map.put("member_complete_task.note", "Optional completion note describing your result or any follow-up the team should know about");
        map.put("send_message.to", "Recipient: member_name for a point-to-point DM (e.g. \"backend-dev-1\"), visible only to you and that member; array of member names (e.g. [\"m1\",\"m2\"]) for multicast \u2014 same content sent as separate messages to each member, cost is linear in recipient count and MORE expensive than broadcast for the same audience, use only when truly needed and cannot mix with \"*\"/\"user\"; \"user\" (teammates only, to reply to the user); \"*\" to broadcast on the team channel, visible to all members");
        map.put("send_message.content", "Message content with clear action guidance or information");
        map.put("send_message.summary", "5-10 word summary for message preview and logging");
        map.put("workspace_meta.action", "Operation type: lock (acquire file lock), unlock (release file lock), locks (list all active locks), history (view file version history)");
        map.put("workspace_meta.path", "Relative path of the target file (required for lock/unlock/history)");
        return Map.copyOf(map);
    }

    public static String get(String key) {
        return STRINGS.get(key);
    }

    public static Map<String, String> getAll() {
        return STRINGS;
    }
}

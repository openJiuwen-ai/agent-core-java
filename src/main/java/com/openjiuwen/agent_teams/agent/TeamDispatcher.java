/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.agent;

import com.openjiuwen.agent_teams.schema.TeamRole;
import com.openjiuwen.agent_teams.schema.message.MessageRecord;
import com.openjiuwen.agent_teams.schema.task.TaskSummary;
import com.openjiuwen.agent_teams.tools.TeamBackend;

import java.util.List;
import java.util.Map;

/**
 * Minimal event dispatcher for team coordination.
 *
 * <p>Mirrors Python's dispatcher intent in
 * {@code openjiuwen.agent_teams.agent.dispatcher}.
 */
public class TeamDispatcher {

    private final TeamRole role;
    private final String memberName;
    private final String teamName;
    private final TeamBackend backend;

    public TeamDispatcher(TeamRole role, String memberName, String teamName, TeamBackend backend) {
        this.role = role;
        this.memberName = memberName;
        this.teamName = teamName;
        this.backend = backend;
    }

    public String dispatch(CoordinationEvent event) {
        String type = event.getEventType();
        Map<String, Object> payload = event.getPayload();
        if ("message".equals(type)) {
            return "Team message for " + memberName + " in " + teamName + ": "
                    + String.valueOf(payload.getOrDefault("content", ""));
        }
        if ("broadcast".equals(type)) {
            return "Broadcast in team " + teamName + " from "
                    + String.valueOf(payload.getOrDefault("from", "leader")) + ": "
                    + String.valueOf(payload.getOrDefault("content", ""));
        }
        if ("task_created".equals(type)) {
            return "Task created in team " + teamName + ": #"
                    + String.valueOf(payload.getOrDefault("task_id", "")) + " "
                    + String.valueOf(payload.getOrDefault("title", ""));
        }
        if ("task_updated".equals(type)) {
            return "Task updated in team " + teamName + ": #"
                    + String.valueOf(payload.getOrDefault("task_id", ""));
        }
        if ("task_claimed".equals(type)) {
            return "Task claimed in team " + teamName + ": #"
                    + String.valueOf(payload.getOrDefault("task_id", "")) + " by "
                    + String.valueOf(payload.getOrDefault("member_name", payload.getOrDefault("assignee", "unknown")));
        }
        if ("task_completed".equals(type)) {
            return "Task completed in team " + teamName + ": #"
                    + String.valueOf(payload.getOrDefault("task_id", ""));
        }
        if ("task_cancelled".equals(type)) {
            return "Task cancelled in team " + teamName + ": #"
                    + String.valueOf(payload.getOrDefault("task_id", ""));
        }
        if ("task_unblocked".equals(type)) {
            return "Task unblocked in team " + teamName + ": #"
                    + String.valueOf(payload.getOrDefault("task_id", ""));
        }
        if ("member_spawned".equals(type)) {
            return "Member spawned in team " + teamName + ": "
                    + String.valueOf(payload.getOrDefault("member_name", ""));
        }
        if ("member_restarted".equals(type)) {
            return "Member restarted in team " + teamName + ": "
                    + String.valueOf(payload.getOrDefault("member_name", ""))
                    + " reason=" + String.valueOf(payload.getOrDefault("reason", "unknown"));
        }
        if ("member_status_changed".equals(type)) {
            return "Member status changed in team " + teamName + ": "
                    + String.valueOf(payload.getOrDefault("member_name", ""))
                    + " " + String.valueOf(payload.getOrDefault("old_status", ""))
                    + " -> " + String.valueOf(payload.getOrDefault("new_status", ""));
        }
        if ("member_execution_changed".equals(type)) {
            return "Member execution changed in team " + teamName + ": "
                    + String.valueOf(payload.getOrDefault("member_name", ""))
                    + " " + String.valueOf(payload.getOrDefault("old_status", ""))
                    + " -> " + String.valueOf(payload.getOrDefault("new_status", ""));
        }
        if ("member_shutdown".equals(type)) {
            return "Member shutdown in team " + teamName + ": "
                    + String.valueOf(payload.getOrDefault("member_name", ""));
        }
        if ("member_canceled".equals(type)) {
            return "Member canceled in team " + teamName + ": "
                    + String.valueOf(payload.getOrDefault("member_name", ""));
        }
        if ("tool_approval_result".equals(type)) {
            return "Tool approval result in team " + teamName + ": member="
                    + String.valueOf(payload.getOrDefault("member_name", ""))
                    + " approved=" + String.valueOf(payload.getOrDefault("approved", false));
        }
        if ("standby".equals(type) || "team_standby".equals(type)) {
            return "Team " + teamName + " entered standby.";
        }
        if ("cleaned".equals(type) || "team_cleaned".equals(type)) {
            return "Team " + teamName + " cleaned.";
        }
        if ("user_input".equals(type)) {
            return "User input for team " + teamName + ": "
                    + String.valueOf(payload.getOrDefault("content", payload.getOrDefault("query", "")));
        }
        if ("coordination_poll_task".equals(type) || "poll_task".equals(type)) {
            return buildTaskPollSummary();
        }
        if ("coordination_poll_mailbox".equals(type) || "poll_mailbox".equals(type)) {
            return buildMailboxPollSummary();
        }
        if ("shutdown".equals(type)) {
            return "Shutdown requested for team " + teamName + ".";
        }
        return "Coordination event for " + role.name().toLowerCase() + " in team " + teamName + ": " + type;
    }

    private String buildTaskPollSummary() {
        if (backend == null) {
            return "Poll task state for team " + teamName + ".";
        }
        List<TaskSummary> tasks = backend.listTasks();
        long pending = tasks.stream().filter(task -> "pending".equalsIgnoreCase(task.getStatus().name())).count();
        long claimed = tasks.stream().filter(task -> "claimed".equalsIgnoreCase(task.getStatus().name())).count();
        long blocked = tasks.stream().filter(task -> "blocked".equalsIgnoreCase(task.getStatus().name())).count();
        return "Task poll for team " + teamName + ": total=" + tasks.size()
                + ", pending=" + pending
                + ", claimed=" + claimed
                + ", blocked=" + blocked + ".";
    }

    private String buildMailboxPollSummary() {
        if (backend == null) {
            return "Poll mailbox state for team " + teamName + ".";
        }
        List<MessageRecord> direct = backend.getMessages(memberName, true, null);
        List<MessageRecord> broadcast = backend.getBroadcastMessages(true, null);
        return "Mailbox poll for " + memberName + " in team " + teamName
                + ": unread_direct=" + direct.size()
                + ", unread_broadcast=" + broadcast.size() + ".";
    }
}

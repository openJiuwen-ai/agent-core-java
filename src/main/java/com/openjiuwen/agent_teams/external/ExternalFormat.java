/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.external;

import com.openjiuwen.agent_teams.AgentTeamI18n;
import com.openjiuwen.agent_teams.AgentTeamTimefmt;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Pure-function rendering of inbound team context for external agents.
 *
 * <p>Mirrors Python's render helpers in
 * {@code openjiuwen/agent_teams/external/format.py}.
 */
public final class ExternalFormat {

    private static final Set<String> TERMINAL_TASK_STATUSES = Set.of("completed", "cancelled");

    private ExternalFormat() {
    }

    public static String renderMessage(MessageLike message, long nowMs) {
        String msgType = message.broadcast()
                ? AgentTeamI18n.t("dispatcher.msg_type_broadcast")
                : AgentTeamI18n.t("dispatcher.msg_type_direct");
        return AgentTeamI18n.t(
                "dispatcher.msg_received",
                "msg_type", msgType,
                "message_id", message.messageId(),
                "sender", message.fromMemberName(),
                "content", message.content(),
                "time_info", AgentTeamTimefmt.formatTimeContext(message.timestamp(), nowMs)
        );
    }

    public static String renderMessages(List<? extends MessageLike> messages, long nowMs) {
        List<String> rendered = new ArrayList<>();
        for (MessageLike message : messages) {
            rendered.add(renderMessage(message, nowMs));
        }
        return String.join("\n\n", rendered);
    }

    public static String renderTaskLine(TaskLike task, long nowMs) {
        String assignee = task.assignee() != null
                ? " → " + task.assignee()
                : AgentTeamI18n.t("dispatcher.task_unassigned_marker");
        String timeInfo = AgentTeamTimefmt.formatTimeContext(task.updatedAt(), nowMs);
        return "- [" + task.taskId() + "] [" + task.status() + "] "
                + task.title() + ": " + task.content() + assignee + " (" + timeInfo + ")";
    }

    public static String renderTaskBoard(List<? extends TaskLike> tasks, boolean isLeader, long nowMs) {
        List<TaskLike> incomplete = new ArrayList<>();
        for (TaskLike task : tasks) {
            if (!TERMINAL_TASK_STATUSES.contains(task.status())) {
                incomplete.add(task);
            }
        }
        if (incomplete.isEmpty()) {
            return "";
        }

        String header = isLeader
                ? AgentTeamI18n.t("dispatcher.leader_task_board")
                : AgentTeamI18n.t("dispatcher.teammate_task_list");
        List<String> lines = new ArrayList<>();
        lines.add(header);
        for (TaskLike task : incomplete) {
            lines.add(renderTaskLine(task, nowMs));
        }
        return String.join("\n", lines);
    }

    /**
     * Mirrors the structural message protocol used by the Python formatter.
     */
    public interface MessageLike {
        String messageId();

        String fromMemberName();

        String content();

        boolean broadcast();

        long timestamp();
    }

    /**
     * Mirrors the structural task protocol used by the Python formatter.
     */
    public interface TaskLike {
        String taskId();

        String title();

        String content();

        String status();

        String assignee();

        Long updatedAt();
    }
}

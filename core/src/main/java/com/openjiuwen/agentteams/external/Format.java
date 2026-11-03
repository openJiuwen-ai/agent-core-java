/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentteams.external;

import com.openjiuwen.agentteams.I18n;
import com.openjiuwen.agentteams.timefmt.TimeFormat;
import com.openjiuwen.agentteams.tools.TeamMessage;
import com.openjiuwen.agentteams.tools.TeamTask;

import java.util.List;

/**
 * Pure-function rendering of inbound team context.
 *
 * <p>Mirrors Python {@code external/format.py}. Turns raw team models (messages,
 * tasks) into the same human-readable text an in-process member would receive
 * through its coordination handlers, reusing shared {@link I18n} strings so
 * external agents see identical wording. No I/O, no LLM calls — fully
 * deterministic and unit-testable.
 *
 * @since 2026/7/9
 */
public final class Format {
    /**
     * Utility class, no instances.
     */
    private Format() {
    }

    /**
     * Render one task line for a task board listing.
     *
     * @param task the task row
     * @param nowMs current millisecond UTC epoch (relative-time anchor)
     * @return {@code "- [taskId] [status] title: content -> assignee (timeInfo)"}
     */
    public static String renderTaskLine(TeamTask task, long nowMs) {
        String timeInfo = TimeFormat.formatTimeContext(task.getUpdatedAt(), nowMs);
        String assignee = task.getAssignee();
        String assigneeMarker = (assignee == null || assignee.isBlank())
                ? I18n.t("dispatcher.task_unassigned_marker")
                : " -> " + assignee;
        return "- [" + task.getTaskId() + "] [" + task.getStatus() + "] "
                + task.getTitle() + ": " + task.getContent()
                + assigneeMarker + " (" + timeInfo + ")";
    }

    /**
     * Render one inbound message exactly like the in-process dispatcher.
     *
     * @param message the message row (direct or broadcast)
     * @param isHumanAgent whether the recipient is a human-agent avatar
     * @param nowMs current millisecond UTC epoch (relative-time anchor)
     * @return localized text mirroring {@code dispatcher.msg_received} or
     *         {@code hitt.msg_received_for_human}
     */
    public static String renderMessage(TeamMessage message, boolean isHumanAgent, long nowMs) {
        String msgType = message.isBroadcast()
                ? I18n.t("dispatcher.msg_type_broadcast")
                : I18n.t("dispatcher.msg_type_direct");
        String timeInfo = TimeFormat.formatTimeContext(message.getTimestamp(), nowMs);
        String key = isHumanAgent
                ? "hitt.msg_received_for_human"
                : "dispatcher.msg_received";
        return I18n.t(key, msgType, message.getMessageId(),
                message.getFromMemberName(), timeInfo, message.getContent());
    }

    /**
     * Render a full task board for an idle agent nudge.
     *
     * @param incomplete non-terminal tasks
     * @param isLeader whether the recipient is the leader (selects board header)
     * @param nowMs current millisecond UTC epoch
     * @return multi-line board text ending in newline-separated task lines
     */
    public static String renderTaskBoard(List<TeamTask> incomplete, boolean isLeader, long nowMs) {
        StringBuilder sb = new StringBuilder();
        sb.append(isLeader
                ? I18n.t("dispatcher.leader_task_board")
                : I18n.t("dispatcher.teammate_task_list"));
        for (TeamTask task : incomplete) {
            sb.append("\n").append(renderTaskLine(task, nowMs));
        }
        return sb.toString();
    }
}

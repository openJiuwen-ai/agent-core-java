/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentteams.agent.coordination.handlers;

import com.openjiuwen.agentteams.I18n;
import com.openjiuwen.agentteams.agent.coordination.DispatcherHost;
import com.openjiuwen.agentteams.agent.coordination.PollController;
import com.openjiuwen.agentteams.agent.coordination.TeamAgentBlueprint;
import com.openjiuwen.agentteams.agent.coordination.TeamInfra;
import com.openjiuwen.agentteams.external.Format;
import com.openjiuwen.agentteams.schema.events.CoordinationEvent;
import com.openjiuwen.agentteams.schema.events.EventMessage;
import com.openjiuwen.agentteams.schema.events.TeamEvent;
import com.openjiuwen.agentteams.schema.team.TeamRole;
import com.openjiuwen.agentteams.tools.TeamBackend;
import com.openjiuwen.agentteams.tools.TeamTask;
import com.openjiuwen.agentteams.tools.TeamTaskManager;
import com.openjiuwen.core.common.logging.Loggers;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Handle TASK_CLAIMED + 5 task-board state-transition events.
 *
 * <p>Mirrors Python {@code handlers/task_board.py}. Owns {@code TASK_CLAIMED}
 * (targeted assignment to one member) and the five board-state events
 * ({@code TASK_CREATED} / {@code TASK_UPDATED} / {@code TASK_COMPLETED} /
 * {@code TASK_CANCELLED} / {@code TASK_UNBLOCKED}) that nudge an idle agent to
 * re-evaluate the board. Stale-task sweeping on poll ticks lives in
 * {@link StaleTaskHandler}.
 *
 * @since 2026/7/9
 */
public class TaskBoardHandler extends BaseCoordinationHandler {
    /**
     * Construct and register event bindings.
     *
     * @param host the owning TeamAgent
     * @param blueprint static config
     * @param infra per-process services
     * @param pollCtrl poll control surface
     */
    public TaskBoardHandler(DispatcherHost host, TeamAgentBlueprint blueprint,
                            TeamInfra infra, PollController pollCtrl) {
        super(host, blueprint, infra, pollCtrl);
        callbacks.put(TeamEvent.TASK_CLAIMED, this::onTaskClaimed);
        callbacks.put(TeamEvent.TASK_CREATED, this::onTaskBoardEvent);
        callbacks.put(TeamEvent.TASK_PLAN_REQUEST, this::onTaskBoardEvent);
        callbacks.put(TeamEvent.TASK_PLAN_RESPONSE, this::onTaskPlanDecision);
        callbacks.put(TeamEvent.TASK_UPDATED, this::onTaskBoardEvent);
        callbacks.put(TeamEvent.TASK_COMPLETED, this::onTaskBoardEvent);
        callbacks.put(TeamEvent.TASK_CANCELLED, this::onTaskBoardEvent);
        callbacks.put(TeamEvent.TASK_UNBLOCKED, this::onTaskBoardEvent);
    }

    /**
     * Directed assignment from another node.
     *
     * <p>Self-claims are filtered upstream via {@code sender_id}. When the
     * claim targets self, route through {@code deliver_input} and skip the
     * board nudge — the targeted message already names the task. When the
     * claim targets someone else, fall through to {@link #onTaskBoardEvent}
     * so the local idle agent (typically the leader observing teammate claims)
     * still gets nudged. A human-agent avatar never autonomously surveys the
     * board for claimable work, so it ignores other members' claims.
     *
     * <p>Self-assignment rendering is role-aware: teammate/leader sees
     * {@code dispatcher.task_assigned_to_self}; human_agent avatar sees the
     * HITT-specific {@code hitt.task_assigned_to_self_human} prompt with
     * best-effort title lookup.
     *
     * @param event the task claimed coordination event
     */
    public void onTaskClaimed(CoordinationEvent event) {
        String memberName = blueprint.memberName().orElse(null);
        if (memberName == null || memberName.isBlank()) {
            return;
        }
        if (!(event instanceof EventMessage msg)) {
            return;
        }
        Map<String, Object> payload = msg.getPayload() != null ? msg.getPayload() : Map.of();
        String claimedMember = str(payload, "member_name");
        boolean isSelfHuman = isSelfHumanAgent(memberName);
        if (claimedMember == null || !claimedMember.equals(memberName)) {
            // Claim targeting someone else — nudge idle with refreshed board.
            // Human-agent avatar never autonomously surveys the board, so skip.
            if (isSelfHuman) {
                return;
            }
            onTaskBoardEvent(event);
            return;
        }
        poll.resumePolls();
        String taskId = str(payload, "task_id");
        String content = isSelfHuman
                ? renderHumanSelfClaim(taskId)
                : I18n.t("dispatcher.task_assigned_to_self", taskId != null ? taskId : "");
        Loggers.AGENT.info("[{}] received TASK_CLAIMED for self, task_id={}, human_agent={}",
                memberName, taskId, isSelfHuman);
        round.deliverInput(content);
    }

    private String renderHumanSelfClaim(String taskId) {
        String title = "";
        Object tm = infra.taskManager();
        if (tm instanceof TeamTaskManager taskManager && taskId != null) {
            try {
                Optional<TeamTask> taskOpt = taskManager.get(taskId);
                if (taskOpt.isPresent() && taskOpt.get().getTitle() != null) {
                    title = taskOpt.get().getTitle();
                }
            } catch (NullPointerException | IllegalStateException e) {
                Loggers.AGENT.warn("task_assigned_to_human_agent: title lookup failed for {}: {}",
                        taskId, e.getMessage());
            }
        }
        return I18n.t("hitt.task_assigned_to_self_human",
                taskId != null ? taskId : "", title);
    }

    private boolean isSelfHumanAgent(String memberName) {
        Object backend = infra.teamBackend();
        return backend instanceof TeamBackend tb && tb.isHumanAgent(memberName);
    }

    /**
     * Notify a member when the leader approves or rejects its plan.
     *
     * @param event the task plan decision event
     */
    public void onTaskPlanDecision(CoordinationEvent event) {
        String memberName = blueprint.memberName().orElse(null);
        if (memberName == null || memberName.isBlank()) {
            return;
        }
        if (!(event instanceof EventMessage msg)) {
            return;
        }
        Map<String, Object> payload = msg.getPayload() != null ? msg.getPayload() : Map.of();
        String targetMember = str(payload, "member_name");
        if (targetMember == null || !targetMember.equals(memberName)) {
            onTaskBoardEvent(event);
            return;
        }
        poll.resumePolls();
        String toolCallId = str(payload, "tool_call_id");
        if (toolCallId != null && !toolCallId.isBlank()) {
            Loggers.AGENT.debug(
                    "[{}] task plan decision resumes pending interrupt, skip extra deliver_input",
                    memberName);
            return;
        }
        boolean isApproved = bool(payload, "approved");
        String taskId = str(payload, "task_id");
        if (taskId == null) {
            taskId = "";
        }
        String feedback = str(payload, "feedback");
        if (feedback == null) {
            feedback = "";
        }
        String key = isApproved
                ? "dispatcher.task_plan_approved_to_self"
                : "dispatcher.task_plan_rejected_to_self";
        round.deliverInput(I18n.t(key, taskId, feedback));
    }

    /**
     * Nudge idle agent on TASK_CREATED/UPDATED/COMPLETED/CANCELLED/UNBLOCKED.
     *
     * <p>Gates on the task-level check, not {@code is_agent_running}: nudging
     * during the pre-stream or finalize window would call {@code start_agent}
     * and overwrite the still-live agent task.
     *
     * @param event the task board state-transition event
     */
    public void onTaskBoardEvent(CoordinationEvent event) {
        String memberName = blueprint.memberName().orElse(null);
        if (memberName == null || memberName.isBlank()) {
            return;
        }

        // Short-circuit when the team has been cleaned/deleted: task events
        // may still arrive from in-flight dispatch or stale queues, and
        // nudging the leader here would re-feed the all_done_* prompt and
        // loop the leader on a non-existent team. The StreamController will
        // close the stream via the onTeamCleaned callback.
        Object backend = infra.teamBackend();
        if (backend instanceof com.openjiuwen.agentteams.tools.TeamBackend tb
                && tb.getTeamInfo() == null) {
            Loggers.AGENT.debug("[{}] onTaskBoardEvent: team already deleted, skipping nudge", memberName);
            return;
        }
        poll.resumePolls();
        Loggers.AGENT.debug("task trigger detected, nudging idle agent: member_name={}", memberName);
        nudgeIdleAgent(memberName, false);
    }

    /**
     * Feed task context to an idle agent.
     *
     * <p>Leader: reviews full task board to decide whether to re-plan or
     * conclude. Teammate: reviews claimable tasks to pick one, plus all tasks
     * for coordination context.
     *
     * @param memberName the calling member's own name
     * @param isFromPoll {@code true} when the nudge originates from a routine
     *     POLL_TASK tick — an idle leader with no incomplete tasks returns
     *     silently (real task-completion prompts arrive via TASK_EVENTS path)
     */
    public void nudgeIdleAgent(String memberName, boolean isFromPoll) {
        Object tm = infra.taskManager();
        if (!(tm instanceof TeamTaskManager taskManager)) {
            return;
        }
        List<TeamTask> allTasks = taskManager.list();
        List<TeamTask> incomplete = allTasks.stream()
                .filter(task -> !"completed".equals(task.getStatus()))
                .filter(task -> !"cancelled".equals(task.getStatus()))
                .toList();
        boolean isLeader = blueprint.role().orElse(null) == TeamRole.LEADER;
        if (isFromPoll && isLeader && incomplete.isEmpty()) {
            return;
        }
        long nowMs = System.currentTimeMillis();
        if (isLeader) {
            if (incomplete.isEmpty()) {
                String lifecycle = blueprint.lifecycle().orElse(null);
                String prompt = "persistent".equalsIgnoreCase(lifecycle)
                        ? I18n.t("dispatcher.all_done_persistent")
                        : I18n.t("dispatcher.all_done_temporary");
                round.deliverInput(prompt);
                return;
            }
            round.deliverInput(Format.renderTaskBoard(incomplete, true, nowMs));
            return;
        }
        List<TeamTask> claimable = incomplete.stream()
                .filter(task -> "pending".equals(task.getStatus()))
                .filter(task -> task.getAssignee() == null || task.getAssignee().isBlank())
                .toList();
        if (claimable.isEmpty() && incomplete.isEmpty()) {
            return;
        }
        round.deliverInput(Format.renderTaskBoard(incomplete, false, nowMs));
    }
}

/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.agent.coordination.handlers;

import com.openjiuwen.agent_teams.AgentTeamI18n;
import com.openjiuwen.agent_teams.agent.AgentConfigurator.TeamInfra;
import com.openjiuwen.agent_teams.agent.AgentConfigurator.TeamRole;
import com.openjiuwen.agent_teams.agent.TeamAgentBlueprint;
import com.openjiuwen.agent_teams.agent.coordination.EventDispatcher.CoordinationEvent;
import com.openjiuwen.agent_teams.agent.coordination.EventDispatcher.DispatcherHost;
import com.openjiuwen.agent_teams.agent.coordination.EventDispatcher.PollController;
import com.openjiuwen.agent_teams.agent.coordination.EventDispatcher.TransportEvent;
import com.openjiuwen.agent_teams.external.ExternalFormat;
import com.openjiuwen.agent_teams.schema.BaseEventMessage;
import com.openjiuwen.agent_teams.schema.TaskClaimedEvent;
import com.openjiuwen.agent_teams.schema.TaskPlanResponseEvent;
import com.openjiuwen.agent_teams.schema.TeamEvent;
import com.openjiuwen.agent_teams.schema.events.EventMessage;
import com.openjiuwen.agent_teams.schema.status.TaskStatus;
import com.openjiuwen.agent_teams.tools.TeamTask;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handles task-board coordination events.
 *
 * <p>Mirrors Python's {@code TaskBoardHandler} in
 * {@code openjiuwen/agent_teams/agent/coordination/handlers/task_board.py}.</p>
 */
public class TaskBoardHandler extends BaseCoordinationHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(TaskBoardHandler.class);
    private static final Set<String> TERMINAL_STATUSES =
            Set.of(TaskStatus.COMPLETED.value(), TaskStatus.CANCELLED.value());

    public TaskBoardHandler(
            DispatcherHost host,
            TeamAgentBlueprint blueprint,
            TeamInfra infra,
            PollController pollController
    ) {
        super(host, blueprint, infra, pollController);
    }

    @Override
    public Map<String, String> getEventMethodMap() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put(TeamEvent.TASK_CLAIMED, "onTaskClaimed");
        map.put(TeamEvent.TASK_CREATED, "onTaskBoardEvent");
        map.put(TeamEvent.TASK_PLAN_REQUEST, "onTaskBoardEvent");
        map.put(TeamEvent.TASK_PLAN_RESPONSE, "onTaskPlanDecision");
        map.put(TeamEvent.TASK_UPDATED, "onTaskBoardEvent");
        map.put(TeamEvent.TASK_COMPLETED, "onTaskBoardEvent");
        map.put(TeamEvent.TASK_CANCELLED, "onTaskBoardEvent");
        map.put(TeamEvent.TASK_UNBLOCKED, "onTaskBoardEvent");
        return map;
    }

    @Override
    protected EventCallback resolveCallback(String methodName) {
        return switch (methodName) {
            case "onTaskClaimed" -> this::onTaskClaimed;
            case "onTaskBoardEvent" -> this::onTaskBoardEvent;
            case "onTaskPlanDecision" -> this::onTaskPlanDecision;
            default -> throw new IllegalArgumentException("Unknown method: " + methodName);
        };
    }

    public CompletionStage<Void> onTaskClaimed(CoordinationEvent event) {
        String memberName = blueprint.getMemberName();
        if (memberName == null || memberName.isEmpty() || !(infra.getTaskManager() instanceof TaskManager taskManager)) {
            return CompletableFuture.completedFuture(null);
        }
        BaseEventMessage payload = messageOf(event).getPayload();
        if (!(payload instanceof TaskClaimedEvent claimed)) {
            return CompletableFuture.completedFuture(null);
        }

        return isHumanAgent(memberName).thenCompose(isSelfHuman -> {
            if (!Objects.equals(claimed.getMemberName(), memberName)) {
                if (isSelfHuman) {
                    return CompletableFuture.completedFuture(null);
                }
                return onTaskBoardEvent(event);
            }
            return poll.resumePolls()
                    .thenCompose(ignored -> claimedContent(taskManager, claimed, isSelfHuman))
                    .thenCompose(content -> round.deliverInput(content, false))
                    .thenRun(() -> LOGGER.info(
                            "[{}] received TASK_CLAIMED for self, task_id={}, human_agent={}",
                            memberName,
                            claimed.getTaskId(),
                            isSelfHuman
                    ));
        });
    }

    public CompletionStage<Void> onTaskPlanDecision(CoordinationEvent event) {
        String memberName = blueprint.getMemberName();
        if (memberName == null || memberName.isEmpty() || !(infra.getTaskManager() instanceof TaskManager)) {
            return CompletableFuture.completedFuture(null);
        }
        BaseEventMessage payload = messageOf(event).getPayload();
        if (!(payload instanceof TaskPlanResponseEvent response)) {
            return CompletableFuture.completedFuture(null);
        }
        if (!Objects.equals(response.getMemberName(), memberName)) {
            return onTaskBoardEvent(event);
        }
        return poll.resumePolls().thenCompose(ignored -> {
            if (response.getToolCallId() != null && !response.getToolCallId().isEmpty()) {
                LOGGER.debug(
                        "[{}] task plan decision resumes pending interrupt, skip extra deliver_input",
                        memberName
                );
                return CompletableFuture.completedFuture(null);
            }
            String key = response.isApproved()
                    ? "dispatcher.task_plan_approved_to_self"
                    : "dispatcher.task_plan_rejected_to_self";
            String content = AgentTeamI18n.t(
                    key,
                    "task_id",
                    response.getTaskId(),
                    "feedback",
                    response.getFeedback() == null ? "" : response.getFeedback()
            );
            return round.deliverInput(content, false);
        });
    }

    public CompletionStage<Void> onTaskBoardEvent(CoordinationEvent event) {
        String memberName = blueprint.getMemberName();
        if (memberName == null || memberName.isEmpty() || !(infra.getTaskManager() instanceof TaskManager)) {
            return CompletableFuture.completedFuture(null);
        }
        return poll.resumePolls()
                .thenRun(() -> LOGGER.debug("task trigger detected, nudging idle agent: member_name={}", memberName))
                .thenCompose(ignored -> nudgeIdleAgent(memberName, false));
    }

    public CompletionStage<Void> nudgeIdleAgent(String memberName, boolean fromPoll) {
        if (!(infra.getTaskManager() instanceof TaskManager taskManager)) {
            return CompletableFuture.completedFuture(null);
        }
        return taskManager.listTasks().thenCompose(allTasks -> {
            List<TeamTask> incomplete = incompleteTasks(allTasks);
            if (fromPoll && blueprint.getRole() == TeamRole.LEADER && incomplete.isEmpty()) {
                return CompletableFuture.completedFuture(null);
            }

            LOGGER.debug("[{}] nudge_idle_agent: {} incomplete tasks", memberName, incomplete.size());
            if (blueprint.getRole() == TeamRole.LEADER) {
                if (incomplete.isEmpty()) {
                    String key = "persistent".equals(blueprint.getLifecycle())
                            ? "dispatcher.all_done_persistent"
                            : "dispatcher.all_done_temporary";
                    return round.deliverInput(AgentTeamI18n.t(key), false);
                }
                return deliverTaskBoard(incomplete, true);
            }

            boolean hasClaimable = incomplete.stream()
                    .anyMatch(task -> TaskStatus.PENDING.value().equals(task.getStatus()) && task.getAssignee() == null);
            if (!hasClaimable && incomplete.isEmpty()) {
                return CompletableFuture.completedFuture(null);
            }
            return deliverTaskBoard(incomplete, false);
        });
    }

    private CompletionStage<String> claimedContent(
            TaskManager taskManager,
            TaskClaimedEvent claimed,
            boolean selfHuman
    ) {
        if (!selfHuman) {
            return CompletableFuture.completedFuture(
                    AgentTeamI18n.t("dispatcher.task_assigned_to_self", "task_id", claimed.getTaskId())
            );
        }
        return taskManager.getTask(claimed.getTaskId())
                .handle((task, exception) -> {
                    if (exception != null) {
                        LOGGER.warn(
                                "task_assigned_to_human_agent: title lookup failed for {}: {}",
                                claimed.getTaskId(),
                                exception.toString()
                        );
                        return "";
                    }
                    return task == null ? "" : task.map(TeamTask::getTitle).orElse("");
                })
                .thenApply(title -> AgentTeamI18n.t(
                        "hitt.task_assigned_to_self_human",
                        "task_id",
                        claimed.getTaskId(),
                        "title",
                        title == null ? "" : title
                ));
    }

    private CompletionStage<Boolean> isHumanAgent(String memberName) {
        if (infra.getTeamBackend() instanceof TeamBackendView backend) {
            return backend.isHumanAgent(memberName);
        }
        return CompletableFuture.completedFuture(false);
    }

    private CompletionStage<Void> deliverTaskBoard(List<TeamTask> incomplete, boolean leader) {
        List<String> lines = new ArrayList<>();
        lines.add(AgentTeamI18n.t(leader ? "dispatcher.leader_task_board" : "dispatcher.teammate_task_list"));
        long nowMs = System.currentTimeMillis();
        for (TeamTask task : incomplete) {
            lines.add(ExternalFormat.renderTaskLine(new TeamTaskView(task), nowMs));
        }
        return round.deliverInput(String.join("\n", lines), false);
    }

    private static List<TeamTask> incompleteTasks(List<TeamTask> allTasks) {
        List<TeamTask> incomplete = new ArrayList<>();
        for (TeamTask task : allTasks == null ? List.<TeamTask>of() : allTasks) {
            if (!TERMINAL_STATUSES.contains(task.getStatus())) {
                incomplete.add(task);
            }
        }
        return incomplete;
    }

    private static EventMessage messageOf(CoordinationEvent event) {
        return ((TransportEvent) event).getMessage();
    }

    private record TeamTaskView(TeamTask task) implements ExternalFormat.TaskLike {
        @Override
        public String taskId() {
            return task.getTaskId();
        }

        @Override
        public String title() {
            return task.getTitle();
        }

        @Override
        public String content() {
            return task.getContent();
        }

        @Override
        public String status() {
            return task.getStatus();
        }

        @Override
        public String assignee() {
            return task.getAssignee();
        }

        @Override
        public Long updatedAt() {
            return task.getUpdatedAt();
        }
    }

    /**
     * Task manager surface used by task-board events.
     *
     * <p>Mirrors Python's task-manager calls in
     * {@code openjiuwen/agent_teams/agent/coordination/handlers/task_board.py}.</p>
     */
    public interface TaskManager {
        CompletionStage<List<TeamTask>> listTasks();

        CompletionStage<Optional<TeamTask>> getTask(String taskId);
    }

    /**
     * Team backend surface used to detect human-agent avatars.
     *
     * <p>Mirrors Python's backend calls in
     * {@code openjiuwen/agent_teams/agent/coordination/handlers/task_board.py}.</p>
     */
    public interface TeamBackendView {
        CompletionStage<Boolean> isHumanAgent(String memberName);
    }
}

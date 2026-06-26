/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.agent.coordination.handlers;

import com.openjiuwen.agent_teams.AgentTeamI18n;
import com.openjiuwen.agent_teams.AgentTeamTimefmt;
import com.openjiuwen.agent_teams.agent.AgentConfigurator.TeamInfra;
import com.openjiuwen.agent_teams.agent.AgentConfigurator.TeamRole;
import com.openjiuwen.agent_teams.agent.TeamAgentBlueprint;
import com.openjiuwen.agent_teams.agent.coordination.EventDispatcher.CoordinationEvent;
import com.openjiuwen.agent_teams.agent.coordination.EventDispatcher.DispatcherHost;
import com.openjiuwen.agent_teams.agent.coordination.EventDispatcher.InnerEventType;
import com.openjiuwen.agent_teams.agent.coordination.EventDispatcher.PollController;
import com.openjiuwen.agent_teams.schema.status.TaskStatus;
import com.openjiuwen.agent_teams.tools.TeamTask;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Sweeps stale claimed and pending tasks on poll-task ticks.
 *
 * <p>Mirrors Python's {@code StaleTaskHandler} in
 * {@code openjiuwen/agent_teams/agent/coordination/handlers/stale_task.py}.</p>
 */
public class StaleTaskHandler extends BaseCoordinationHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(StaleTaskHandler.class);
    private static final double STALE_CLAIM_SECONDS = 10 * 60.0d;
    private static final double STALE_PENDING_SECONDS = 10 * 60.0d;

    private final Map<String, Double> lastStaleNudge;
    private final Map<String, Double> lastPendingNudge = new LinkedHashMap<>();

    public StaleTaskHandler(
            DispatcherHost host,
            TeamAgentBlueprint blueprint,
            TeamInfra infra,
            PollController pollController,
            Map<String, Double> staleClaimThrottle
    ) {
        super(host, blueprint, infra, pollController);
        this.lastStaleNudge = Objects.requireNonNull(staleClaimThrottle, "staleClaimThrottle");
    }

    @Override
    public Map<String, String> getEventMethodMap() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put(InnerEventType.POLL_TASK.value(), "onPollTask");
        return map;
    }

    @Override
    protected EventCallback resolveCallback(String methodName) {
        if ("onPollTask".equals(methodName)) {
            return this::onPollTask;
        }
        throw new IllegalArgumentException("Unknown method: " + methodName);
    }

    public CompletionStage<Void> onPollTask(CoordinationEvent event) {
        String memberName = blueprint.getMemberName();
        LOGGER.debug("poll task: member_name={}, agent_running={}", memberName, round.isAgentRunning());
        if (memberName == null || memberName.isEmpty() || !(infra.getTaskManager() instanceof TaskManager)) {
            return CompletableFuture.completedFuture(null);
        }
        return checkStaleClaimedTasks().thenCompose(ignored -> checkStalePendingTasks());
    }

    public CompletionStage<Void> checkStaleClaimedTasks() {
        if (!(infra.getTaskManager() instanceof TaskManager taskManager)) {
            return CompletableFuture.completedFuture(null);
        }
        return taskManager.listTasks(TaskStatus.CLAIMED.value())
                .thenCompose(claimed -> {
                    List<TeamTask> relevant = relevantClaimedTasks(claimed);
                    Set<String> currentIds = relevant.stream()
                            .map(TeamTask::getTaskId)
                            .collect(java.util.stream.Collectors.toSet());
                    lastStaleNudge.keySet().removeIf(taskId -> !currentIds.contains(taskId));
                    if (relevant.isEmpty()) {
                        return CompletableFuture.completedFuture(null);
                    }
                    return nudgeStaleClaims(relevant);
                });
    }

    public CompletionStage<Void> checkStalePendingTasks() {
        if (blueprint.getRole() != TeamRole.LEADER || !(infra.getTaskManager() instanceof TaskManager taskManager)) {
            return CompletableFuture.completedFuture(null);
        }
        return taskManager.listTasks(TaskStatus.PENDING.value())
                .thenCompose(pending -> {
                    double nowSeconds = System.currentTimeMillis() / 1000.0d;
                    long thresholdMs = Math.round(STALE_PENDING_SECONDS * 1000.0d);
                    Set<String> staleIds = pending.stream()
                            .filter(task -> task.getUpdatedAt() != null)
                            .filter(task -> System.currentTimeMillis() - task.getUpdatedAt() >= thresholdMs)
                            .map(TeamTask::getTaskId)
                            .collect(java.util.stream.Collectors.toSet());
                    lastPendingNudge.keySet().removeIf(taskId -> !staleIds.contains(taskId));

                    List<TeamTask> fresh = new ArrayList<>();
                    for (TeamTask task : pending) {
                        if (!staleIds.contains(task.getTaskId())) {
                            continue;
                        }
                        double last = lastPendingNudge.getOrDefault(task.getTaskId(), 0.0d);
                        if (nowSeconds - last < STALE_PENDING_SECONDS) {
                            continue;
                        }
                        fresh.add(task);
                    }
                    if (fresh.isEmpty()) {
                        return CompletableFuture.completedFuture(null);
                    }
                    for (TeamTask task : fresh) {
                        lastPendingNudge.put(task.getTaskId(), nowSeconds);
                    }
                    return selfPromptStalePending(fresh, System.currentTimeMillis());
                });
    }

    public Map<String, Double> getLastStaleNudge() {
        return lastStaleNudge;
    }

    public Map<String, Double> getLastPendingNudge() {
        return lastPendingNudge;
    }

    private List<TeamTask> relevantClaimedTasks(List<TeamTask> claimed) {
        String ownName = blueprint.getMemberName();
        boolean leader = blueprint.getRole() == TeamRole.LEADER;
        List<TeamTask> relevant = new ArrayList<>();
        for (TeamTask task : claimed == null ? List.<TeamTask>of() : claimed) {
            String assignee = task.getAssignee();
            if (assignee != null && (assignee.equals(ownName) || leader)) {
                relevant.add(task);
            }
        }
        return relevant;
    }

    private CompletionStage<Void> nudgeStaleClaims(List<TeamTask> relevant) {
        double nowSeconds = System.currentTimeMillis() / 1000.0d;
        long nowMs = System.currentTimeMillis();
        long thresholdMs = Math.round(STALE_CLAIM_SECONDS * 1000.0d);
        CompletionStage<Void> chain = CompletableFuture.completedFuture(null);
        for (TeamTask task : relevant) {
            chain = chain.thenCompose(ignored -> {
                if (task.getUpdatedAt() == null || nowMs - task.getUpdatedAt() < thresholdMs) {
                    return CompletableFuture.completedFuture(null);
                }
                double lastNudge = lastStaleNudge.getOrDefault(task.getTaskId(), 0.0d);
                if (nowSeconds - lastNudge < STALE_CLAIM_SECONDS) {
                    return CompletableFuture.completedFuture(null);
                }
                lastStaleNudge.put(task.getTaskId(), nowSeconds);
                return nudgeStaleClaim(task, nowMs);
            });
        }
        return chain;
    }

    private CompletionStage<Void> nudgeStaleClaim(TeamTask task, long nowMs) {
        String assignee = task.getAssignee();
        if (assignee != null && assignee.equals(blueprint.getMemberName())) {
            return selfNudgeStaleClaim(task, nowMs);
        }
        if (blueprint.getRole() == TeamRole.LEADER && assignee != null) {
            return leaderNudgeStaleClaim(task, nowMs);
        }
        return CompletableFuture.completedFuture(null);
    }

    public String formatStaleClaimNudge(TeamTask task, long nowMs) {
        return AgentTeamI18n.t(
                "dispatcher.stale_claim_self",
                "task_id",
                task.getTaskId(),
                "title",
                task.getTitle(),
                "content",
                task.getContent(),
                "time_info",
                AgentTeamTimefmt.formatTimeContext(task.getUpdatedAt(), nowMs)
        );
    }

    private CompletionStage<Void> selfNudgeStaleClaim(TeamTask task, long nowMs) {
        String content = formatStaleClaimNudge(task, nowMs);
        return round.deliverInput(content, false)
                .thenRun(() -> LOGGER.info(
                        "[{}] self-nudged stale claimed task {}",
                        blueprint.getMemberName(),
                        task.getTaskId()
                ));
    }

    private CompletionStage<Void> leaderNudgeStaleClaim(TeamTask task, long nowMs) {
        if (!(infra.getMessageManager() instanceof MessageManager messageManager)) {
            return CompletableFuture.completedFuture(null);
        }
        String content = formatStaleClaimNudge(task, nowMs);
        return messageManager.sendMessage(content, task.getAssignee())
                .thenRun(() -> LOGGER.info(
                        "[leader] nudged {} about stale claimed task {}",
                        task.getAssignee(),
                        task.getTaskId()
                ));
    }

    private CompletionStage<Void> selfPromptStalePending(List<TeamTask> fresh, long nowMs) {
        List<String> lines = new ArrayList<>();
        lines.add(AgentTeamI18n.t("dispatcher.stale_pending_header"));
        for (TeamTask task : fresh) {
            String timeInfo = AgentTeamTimefmt.formatTimeContext(task.getUpdatedAt(), nowMs);
            lines.add("- [" + task.getTaskId() + "] " + task.getTitle()
                    + ": " + task.getContent() + " (" + timeInfo + ")");
        }
        String content = String.join("\n", lines);
        return round.deliverInput(content, false)
                .thenRun(() -> LOGGER.info(
                        "[leader] self-prompted about {} stale pending task(s)",
                        fresh.size()
                ));
    }

    /**
     * Task manager surface used for stale-task sweeps.
     *
     * <p>Mirrors Python's task-manager calls in
     * {@code openjiuwen/agent_teams/agent/coordination/handlers/stale_task.py}.</p>
     */
    public interface TaskManager {
        CompletionStage<List<TeamTask>> listTasks(String status);
    }

    /**
     * Message manager surface used by leader stale-claim nudges.
     *
     * <p>Mirrors Python's message-manager calls in
     * {@code openjiuwen/agent_teams/agent/coordination/handlers/stale_task.py}.</p>
     */
    public interface MessageManager {
        CompletionStage<Void> sendMessage(String content, String targetId);
    }
}

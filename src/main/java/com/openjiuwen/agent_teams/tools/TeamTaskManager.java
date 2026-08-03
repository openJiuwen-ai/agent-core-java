/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.tools;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.agent_teams.AgentTeamPaths;
import com.openjiuwen.agent_teams.AgentTeamsContext;
import com.openjiuwen.agent_teams.messager.Messager;
import com.openjiuwen.agent_teams.schema.BaseEventMessage;
import com.openjiuwen.agent_teams.schema.GraphMutationResult;
import com.openjiuwen.agent_teams.schema.NewTaskSpec;
import com.openjiuwen.agent_teams.schema.TaskCancelledEvent;
import com.openjiuwen.agent_teams.schema.TaskClaimedEvent;
import com.openjiuwen.agent_teams.schema.TaskCompletedEvent;
import com.openjiuwen.agent_teams.schema.TaskCreateResult;
import com.openjiuwen.agent_teams.schema.TaskCreatedEvent;
import com.openjiuwen.agent_teams.schema.TaskDetail;
import com.openjiuwen.agent_teams.schema.TaskListDrainedEvent;
import com.openjiuwen.agent_teams.schema.TaskListResult;
import com.openjiuwen.agent_teams.schema.TaskOpResult;
import com.openjiuwen.agent_teams.schema.TaskPlanRequestEvent;
import com.openjiuwen.agent_teams.schema.TaskPlanResponseEvent;
import com.openjiuwen.agent_teams.schema.TaskSummary;
import com.openjiuwen.agent_teams.schema.TaskUnblockedEvent;
import com.openjiuwen.agent_teams.schema.TaskUpdatedEvent;
import com.openjiuwen.agent_teams.schema.TeamTopic;
import com.openjiuwen.agent_teams.schema.events.EventMessage;
import com.openjiuwen.agent_teams.schema.status.MemberMode;
import com.openjiuwen.agent_teams.schema.status.StatusTransitions;
import com.openjiuwen.agent_teams.schema.status.TaskStatus;
import com.openjiuwen.agent_teams.tools.database.MemberDao;
import com.openjiuwen.agent_teams.tools.database.TaskDao;
import com.openjiuwen.agent_teams.tools.database.TeamDao;
import com.openjiuwen.agent_teams.tools.database.TeamDatabase;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.common.logging.LoggerProtocol;
import com.openjiuwen.core.sys_operation.Cwd;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * Manager for team tasks.
 *
 * <p>Mirrors Python's {@code TeamTaskManager} in
 * {@code openjiuwen/agent_teams/tools/task_manager.py}.</p>
 */
public class TeamTaskManager {

    private static final LoggerProtocol TEAM_LOGGER = Loggers.TEAM;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };
    private static final Pattern UNSAFE_TOKEN_PATTERN = Pattern.compile("[^A-Za-z0-9_.-]+");
    private static final Set<String> TASK_TERMINAL_STATUSES = Set.of(
            TaskStatus.COMPLETED.value(),
            TaskStatus.CANCELLED.value()
    );

    private final String teamName;
    private final String memberName;
    private final TeamTaskDatabase database;
    private final Messager messager;
    private final MessageNotifier messageNotifier;

    private Path plansDir;
    private String teamPlanId;
    private String leaderMemberName;

    public TeamTaskManager(String teamName, String memberName, TeamDatabase database, Messager messager) {
        this(teamName, memberName, database, messager, null, null, null);
    }

    public TeamTaskManager(
            String teamName,
            String memberName,
            TeamDatabase database,
            Messager messager,
            Path plansDir,
            String teamPlanId,
            String leaderMemberName) {
        this(
                teamName,
                memberName,
                databaseFrom(database),
                messager,
                (content, toMemberName) -> new TeamMessageManager(teamName, memberName, database, messager)
                        .sendMessage(content, toMemberName),
                plansDir,
                teamPlanId,
                leaderMemberName
        );
    }

    public TeamTaskManager(String teamName, String memberName, InMemoryTeamDatabase database, Messager messager) {
        this(teamName, memberName, database, messager, null, null, null);
    }

    public TeamTaskManager(
            String teamName,
            String memberName,
            InMemoryTeamDatabase database,
            Messager messager,
            Path plansDir,
            String teamPlanId,
            String leaderMemberName) {
        this(
                teamName,
                memberName,
                databaseFrom(database),
                messager,
                (content, toMemberName) -> new TeamMessageManager(teamName, memberName, database, messager)
                        .sendMessage(content, toMemberName),
                plansDir,
                teamPlanId,
                leaderMemberName
        );
    }

    public TeamTaskManager(
            String teamName,
            String memberName,
            TeamTaskDatabase database,
            Messager messager,
            MessageNotifier messageNotifier,
            Path plansDir,
            String teamPlanId,
            String leaderMemberName) {
        this.teamName = teamName;
        this.memberName = memberName;
        this.database = Objects.requireNonNull(database, "database");
        this.messager = messager;
        this.messageNotifier = messageNotifier;
        this.plansDir = plansDir == null
                ? AgentTeamPaths.teamHome(teamName).resolve("team-workspace").resolve("plans")
                : plansDir;
        this.teamPlanId = safeToken(
                firstNonBlank(teamPlanId, AgentTeamsContext.getSessionId(), teamName),
                "team_plan"
        );
        this.leaderMemberName = trimToEmpty(leaderMemberName);
    }

    public void configurePlanStorage(Path plansDir, String teamPlanId) {
        if (plansDir != null) {
            this.plansDir = plansDir;
        }
        if (teamPlanId != null) {
            this.teamPlanId = safeToken(teamPlanId, "team_plan");
        }
    }

    public CompletionStage<List<TaskCreateResult>> addBatch(List<Map<String, ?>> tasks) {
        return supplyStage(() -> {
            List<TaskCreateResult> createdTasks = new ArrayList<>();
            if (tasks == null) {
                return createdTasks;
            }
            for (Map<String, ?> taskSpec : tasks) {
                String title = stringValue(taskSpec.get("title"));
                String content = stringValue(taskSpec.get("content"));
                String taskId = emptyToNull(stringValue(taskSpec.get("task_id")));
                List<String> dependencies = stringList(taskSpec.get("dependencies"));
                if (title.isEmpty() || content.isEmpty()) {
                    TEAM_LOGGER.warning("Skipping invalid task: %s", taskSpec);
                    continue;
                }
                TaskCreateResult result = addSync(title, content, taskId, dependencies);
                if (result.ok()) {
                    createdTasks.add(result);
                } else {
                    TEAM_LOGGER.warning("Batch add skipped task %s: %s",
                            firstNonBlank(taskId, title),
                            result.reason());
                }
            }
            TEAM_LOGGER.info("Batch added %d tasks", createdTasks.size());
            return createdTasks;
        });
    }

    public CompletionStage<TaskCreateResult> add(String title, String content) {
        return add(title, content, null, null);
    }

    public CompletionStage<TaskCreateResult> add(
            String title,
            String content,
            String taskId,
            List<String> dependencies) {
        return supplyStage(() -> addSync(title, content, taskId, dependencies));
    }

    public CompletionStage<TaskCreateResult> addWithPriority(
            String title,
            String content,
            String taskId,
            List<String> dependencies,
            List<String> dependentTaskIds) {
        return supplyStage(() -> {
            String nextTaskId = firstNonBlank(taskId, UUID.randomUUID().toString());
            String status = dependencies != null && !dependencies.isEmpty()
                    ? TaskStatus.BLOCKED.value()
                    : TaskStatus.PENDING.value();
            boolean success = join(database.addTaskWithBidirectionalDependencies(
                    nextTaskId,
                    teamName,
                    title,
                    content,
                    status,
                    dependencies,
                    dependentTaskIds
            ));
            if (!success) {
                return TaskCreateResult.fail(
                        "Failed to create prioritized task " + nextTaskId
                                + " (circular dependency, missing dependent task, or task_id collision)"
                );
            }
            publishTaskEvent(taskCreatedEvent(nextTaskId, status), "Task created event for " + nextTaskId, true);
            return TaskCreateResult.success(new TeamTask(nextTaskId, teamName, title, content, status, null, null));
        });
    }

    public CompletionStage<TaskCreateResult> addAsTopPriority(String title, String content, String taskId) {
        return supplyStage(() -> {
            String nextTaskId = firstNonBlank(taskId, UUID.randomUUID().toString());
            List<TeamTask> pendingTasks = join(listTasks(TaskStatus.PENDING.value()));
            List<String> dependentTaskIds = pendingTasks.stream().map(TeamTask::getTaskId).toList();
            String status = TaskStatus.PENDING.value();
            boolean success = join(database.addTaskWithBidirectionalDependencies(
                    nextTaskId,
                    teamName,
                    title,
                    content,
                    status,
                    null,
                    dependentTaskIds.isEmpty() ? null : dependentTaskIds
            ));
            if (!success) {
                return TaskCreateResult.fail(
                        "Failed to create top priority task " + nextTaskId
                                + " (circular dependency or task_id collision)"
                );
            }
            TEAM_LOGGER.info("Added top priority task %s, blocking %d existing tasks",
                    nextTaskId,
                    dependentTaskIds.size());
            publishTaskEvent(taskCreatedEvent(nextTaskId, status), "Task created event for " + nextTaskId, true);
            return TaskCreateResult.success(new TeamTask(nextTaskId, teamName, title, content, status, null, null));
        });
    }

    public CompletionStage<TaskListResult> listTasksWithDeps(String status) {
        return supplyStage(() -> {
            List<TeamTask> tasks = join(listTasks(status));
            List<TaskSummary> summaries = new ArrayList<>();
            for (TeamTask task : tasks) {
                List<TeamTaskDependency> dependencies = join(getDependencies(task.getTaskId()));
                List<String> unresolved = dependencies.stream()
                        .filter(dep -> !Boolean.TRUE.equals(dep.getResolved()))
                        .map(TeamTaskDependency::getDependsOnTaskId)
                        .toList();
                summaries.add(new TaskSummary(
                        task.getTaskId(),
                        task.getTitle(),
                        task.getStatus(),
                        task.getAssignee(),
                        unresolved,
                        task.getUpdatedAt()
                ));
            }
            return new TaskListResult(summaries, summaries.size());
        });
    }

    public CompletionStage<TaskDetail> getTaskDetail(String taskId) {
        return supplyStage(() -> {
            TeamTask task = join(get(taskId)).orElse(null);
            if (task == null) {
                return null;
            }
            List<TeamTaskDependency> dependencies = join(getDependencies(taskId));
            List<String> blockedBy = dependencies.stream()
                    .filter(dep -> !Boolean.TRUE.equals(dep.getResolved()))
                    .map(TeamTaskDependency::getDependsOnTaskId)
                    .toList();
            List<String> blocks = join(database.getTasksDependingOn(taskId)).stream()
                    .map(TeamTask::getTaskId)
                    .toList();

            TaskDetail detail = new TaskDetail();
            detail.setTaskId(task.getTaskId());
            detail.setTitle(task.getTitle());
            detail.setContent(task.getContent());
            detail.setStatus(task.getStatus());
            detail.setAssignee(task.getAssignee());
            detail.setBlockedBy(blockedBy);
            detail.setBlocks(blocks);
            detail.setUpdatedAt(task.getUpdatedAt());
            return detail;
        });
    }

    public CompletionStage<Optional<TeamTask>> get(String taskId) {
        return database.getTask(taskId);
    }

    public CompletionStage<TaskOpResult> assign(String taskId, String assignee) {
        return supplyStage(() -> {
            TeamTask task = join(get(taskId)).orElse(null);
            if (task == null) {
                return TaskOpResult.fail("Task " + taskId + " not found");
            }
            Optional<TeamMember> member = join(database.getMember(assignee, teamName));
            if (member.isEmpty()) {
                return TaskOpResult.fail("Member " + assignee + " not found in team " + teamName);
            }
            if (Objects.equals(task.getAssignee(), assignee)
                    && Objects.equals(task.getStatus(), TaskStatus.CLAIMED.value())) {
                TEAM_LOGGER.debug("Task %s already assigned to %s; no-op", taskId, assignee);
                return TaskOpResult.success();
            }
            if (task.getAssignee() != null && !Objects.equals(task.getAssignee(), assignee)) {
                return TaskOpResult.fail(
                        "Task " + taskId + " is already claimed by " + task.getAssignee()
                                + "; reset the task before reassigning to " + assignee
                );
            }
            boolean success = join(database.claimTask(taskId, assignee));
            if (!success) {
                return TaskOpResult.fail(
                        "Database rejected assign for task " + taskId
                                + " (invalid state transition from " + task.getStatus() + ")"
                );
            }
            publishTaskEventStrict(taskClaimedEvent(taskId, assignee));
            TEAM_LOGGER.info("Task %s assigned to %s, notification sent", taskId, assignee);
            return TaskOpResult.success();
        });
    }

    public CompletionStage<TaskOpResult> addDependencies(String taskId, List<String> dependsOnIds) {
        return supplyStage(() -> {
            if (dependsOnIds == null || dependsOnIds.isEmpty()) {
                return TaskOpResult.success();
            }
            List<TaskDao.DependencyEdge> edges = dependsOnIds.stream()
                    .map(depId -> new TaskDao.DependencyEdge(taskId, depId))
                    .toList();
            GraphMutationResult mutation = join(database.mutateDependencyGraph(teamName, null, edges));
            if (!mutation.ok()) {
                return TaskOpResult.fail(mutation.reason());
            }
            return TaskOpResult.success();
        });
    }

    public CompletionStage<TaskOpResult> claim(String taskId) {
        return supplyStage(() -> {
            TeamTask task = join(get(taskId)).orElse(null);
            if (task == null) {
                return TaskOpResult.fail("Task " + taskId + " not found");
            }
            Optional<TeamMember> member = join(database.getMember(memberName, teamName));
            if (member.isEmpty()) {
                return TaskOpResult.fail("Member " + memberName + " not found in team " + teamName);
            }
            if (Objects.equals(member.get().getMode(), MemberMode.PLAN_MODE.value())) {
                return TaskOpResult.fail(
                        "PLAN_MODE members must call submit_plan first; "
                                + "leader approval moves the task from claimed to plan_approved"
                );
            }
            if (Objects.equals(task.getAssignee(), memberName)
                    && Objects.equals(task.getStatus(), TaskStatus.CLAIMED.value())) {
                TEAM_LOGGER.debug("Task %s already claimed by %s; no-op", taskId, memberName);
                return TaskOpResult.success();
            }
            if (task.getAssignee() != null) {
                return TaskOpResult.fail(
                        "Task " + taskId + " is already claimed by " + task.getAssignee()
                                + ", " + memberName + " cannot claim it"
                );
            }
            if (!isValidTaskTransition(task.getStatus(), TaskStatus.CLAIMED)) {
                return TaskOpResult.fail(
                        "Task " + taskId + " cannot be claimed from status '" + task.getStatus()
                                + "' (only pending tasks are claimable)"
                );
            }
            boolean success = join(database.claimTask(taskId, memberName));
            if (!success) {
                return TaskOpResult.fail("Database rejected claim for task " + taskId
                        + " (likely a concurrent claim race)");
            }
            TEAM_LOGGER.info("Task %s claimed by member %s", taskId, memberName);
            publishTaskEvent(taskClaimedEvent(taskId, memberName), "Task claimed event for " + taskId, true);
            return TaskOpResult.success();
        });
    }

    public CompletionStage<TaskOpResult> complete(String taskId) {
        return supplyStage(() -> {
            Optional<TeamMember> member = join(database.getMember(memberName, teamName));
            if (member.isEmpty()) {
                return TaskOpResult.fail("Member " + memberName + " not found in team " + teamName);
            }
            if (Objects.equals(member.get().getMode(), MemberMode.PLAN_MODE.value())) {
                TeamTask task = join(database.getTask(taskId)).orElse(null);
                if (task == null) {
                    return TaskOpResult.fail("Task " + taskId + " not found");
                }
                if (!Objects.equals(task.getStatus(), TaskStatus.PLAN_APPROVED.value())) {
                    return TaskOpResult.fail(
                            "PLAN_MODE member cannot complete task " + taskId + " in status '"
                                    + task.getStatus() + "'; only plan_approved tasks can be completed"
                    );
                }
            }

            Optional<TaskDao.TaskTerminationResult> result = join(database.completeTask(taskId));
            if (result.isEmpty()) {
                TeamTask current = join(database.getTask(taskId)).orElse(null);
                if (current == null) {
                    return TaskOpResult.fail("Task " + taskId + " not found");
                }
                return TaskOpResult.fail(
                        "Task " + taskId + " cannot be completed from status '" + current.getStatus()
                                + "' (must be claimed or plan_approved)"
                );
            }

            TeamTask completedTask = result.get().task();
            List<TeamTask> unblockedTasks = result.get().unblockedTasks();
            TEAM_LOGGER.info("Task %s completed", taskId);
            publishTaskEvent(
                    taskCompletedEvent(taskId, completedTask == null ? null : completedTask.getAssignee()),
                    "Task completed event for " + taskId,
                    true
            );
            if (Objects.equals(member.get().getMode(), MemberMode.PLAN_MODE.value())) {
                Map<String, Object> planIndex = readTaskPlanIndex(taskId);
                String latestPlanId = stringValue(planIndex.get("latest_plan_id"));
                Map<String, Object> update = new LinkedHashMap<>();
                update.put("task_id", taskId);
                update.put("plan_id", latestPlanId);
                update.put("team_plan_id", teamPlanId);
                update.put("member_name", completedTask == null ? null : completedTask.getAssignee());
                update.put("status", TaskStatus.COMPLETED.value());
                update.put("completed_at", nowIso());
                update.put("updated_at", nowIso());
                writeTaskPlanIndex(taskId, update);
            }
            publishUnblockedEvents(unblockedTasks);
            maybePublishTaskListDrained();
            return TaskOpResult.success();
        });
    }

    public CompletionStage<Map<String, Object>> submitPlan(
            String taskId,
            String planPath,
            String planId,
            String toolCallId) {
        return supplyStage(() -> submitPlanSync(taskId, planPath, planId, toolCallId));
    }

    public CompletionStage<TeamTask> cancel(String taskId) {
        return supplyStage(() -> {
            Optional<TaskDao.TaskTerminationResult> result = join(database.cancelTask(taskId));
            if (result.isEmpty()) {
                return null;
            }
            TeamTask task = result.get().task();
            List<TeamTask> unblockedTasks = result.get().unblockedTasks();
            TEAM_LOGGER.info("Task %s cancelled", taskId);
            publishTaskEvent(taskCancelledEvent(taskId), "Task cancelled event for " + taskId, true);
            publishUnblockedEvents(unblockedTasks);
            maybePublishTaskListDrained();
            return task;
        });
    }

    public CompletionStage<List<TeamTask>> cancelAllTasks(Set<String> skipAssignees) {
        return supplyStage(() -> {
            TaskDao.TaskBulkCancellationResult result = join(database.cancelAllTasks(teamName, skipAssignees));
            List<TeamTask> cancelledTasks = result.cancelledTasks();
            List<TeamTask> unblockedTasks = result.unblockedTasks();
            if (cancelledTasks == null || cancelledTasks.isEmpty()) {
                TEAM_LOGGER.info("No tasks to cancel in team %s", teamName);
                return List.of();
            }
            for (TeamTask task : cancelledTasks) {
                publishTaskEvent(
                        taskCancelledEvent(task.getTaskId()),
                        "Task cancelled event for " + task.getTaskId(),
                        true
                );
            }
            publishUnblockedEvents(unblockedTasks);
            maybePublishTaskListDrained();
            return cancelledTasks;
        });
    }

    public CompletionStage<List<TeamTask>> listTasks() {
        return listTasks(null);
    }

    public CompletionStage<List<TeamTask>> listTasks(String status) {
        return database.getTeamTasks(teamName, status);
    }

    public CompletionStage<List<TeamTaskDependency>> getDependencies(String taskId) {
        return database.getTaskDependencies(taskId);
    }

    public CompletionStage<List<TeamTask>> getClaimableTasks() {
        return listTasks(TaskStatus.PENDING.value());
    }

    public CompletionStage<List<TeamTask>> getTasksByAssignee(String targetMemberName, String status) {
        return database.getTasksByAssignee(teamName, targetMemberName, status);
    }

    public CompletionStage<TaskOpResult> updateTask(String taskId, String title, String content) {
        return supplyStage(() -> {
            TeamTask task = join(get(taskId)).orElse(null);
            if (task == null) {
                return TaskOpResult.fail("Task " + taskId + " not found");
            }
            boolean success = join(database.updateTask(taskId, title, content));
            if (!success) {
                return TaskOpResult.fail(
                        "Task " + taskId + " cannot be edited while in status '" + task.getStatus()
                                + "'; content updates are only allowed on pending / blocked tasks"
                );
            }
            TEAM_LOGGER.info("Task %s updated", taskId);
            publishTaskEvent(taskUpdatedEvent(taskId), "Task updated event for " + taskId, true);
            return TaskOpResult.success();
        });
    }

    public CompletionStage<TaskOpResult> reset(String taskId) {
        return supplyStage(() -> {
            TeamTask existing = join(database.getTask(taskId)).orElse(null);
            if (existing == null) {
                return TaskOpResult.fail("Task " + taskId + " not found");
            }
            Optional<TeamTask> result = join(database.resetTask(taskId));
            if (result.isEmpty()) {
                return TaskOpResult.fail(
                        "Task " + taskId + " cannot be reset from status '" + existing.getStatus()
                                + "'; only claimed tasks can be reset"
                );
            }
            TEAM_LOGGER.info("Task %s reset successfully", taskId);
            return TaskOpResult.success();
        });
    }

    public CompletionStage<TaskOpResult> approvePlan(String planId) {
        return approvePlan(planId, true, "", null);
    }

    public CompletionStage<TaskOpResult> approvePlan(
            String planId,
            boolean approved,
            String feedback,
            String leaderName) {
        return supplyStage(() -> approvePlanSync(planId, approved, feedback, leaderName));
    }

    public Map<String, Object> getPlanRecord(String planId) {
        return readPlanIndex(planId);
    }

    private TaskCreateResult addSync(String title, String content, String taskId, List<String> dependencies) {
        String nextTaskId = firstNonBlank(taskId, UUID.randomUUID().toString());
        String status = TaskStatus.PENDING.value();
        if (dependencies != null && !dependencies.isEmpty()) {
            List<NewTaskSpec> newTasks = List.of(new NewTaskSpec(nextTaskId, title, content, status));
            List<TaskDao.DependencyEdge> edges = dependencies.stream()
                    .map(depId -> new TaskDao.DependencyEdge(nextTaskId, depId))
                    .toList();
            GraphMutationResult mutation = join(database.mutateDependencyGraph(teamName, newTasks, edges));
            if (!mutation.ok()) {
                return TaskCreateResult.fail("Failed to create task " + nextTaskId + ": " + mutation.reason());
            }
            for (Object refreshed : mutation.refreshedTasks()) {
                if (refreshed instanceof TeamTask task && Objects.equals(task.getTaskId(), nextTaskId)) {
                    status = task.getStatus();
                    break;
                }
            }
            TEAM_LOGGER.debug("Added task %s with dependencies: %s", nextTaskId, dependencies);
        } else {
            boolean success = join(database.createTask(nextTaskId, teamName, title, content, status));
            if (!success) {
                return TaskCreateResult.fail(
                        "Failed to create task " + nextTaskId + " (likely a task_id collision)"
                );
            }
        }

        publishTaskEvent(taskCreatedEvent(nextTaskId, status), "Task created event for " + nextTaskId, true);
        return TaskCreateResult.success(new TeamTask(nextTaskId, teamName, title, content, status, null, null));
    }

    private Map<String, Object> submitPlanSync(
            String taskId,
            String planPath,
            String planId,
            String toolCallId) {
        String effectiveToolCallId = toolCallId == null ? "" : toolCallId;
        Optional<TeamMember> member = join(database.getMember(memberName, teamName));
        if (member.isEmpty()) {
            return resultMap(false, taskId, "Member " + memberName + " not found");
        }
        if (!Objects.equals(member.get().getMode(), MemberMode.PLAN_MODE.value())) {
            return resultMap(false, taskId, "submit_plan is only for PLAN_MODE");
        }

        TeamTask task = join(get(taskId)).orElse(null);
        if (task == null) {
            return resultMap(false, taskId, "Task " + taskId + " not found");
        }
        if (task.getAssignee() != null && !Objects.equals(task.getAssignee(), memberName)) {
            return resultMap(false, taskId, "Task " + taskId + " is assigned to "
                    + task.getAssignee() + ", not " + memberName);
        }
        if (!Objects.equals(task.getStatus(), TaskStatus.PENDING.value())
                && !Objects.equals(task.getStatus(), TaskStatus.CLAIMED.value())) {
            Map<String, Object> result = resultMap(
                    false,
                    taskId,
                    "Task " + taskId + " cannot accept a member plan from status '" + task.getStatus() + "'"
            );
            result.put("status", task.getStatus());
            return result;
        }

        String effectivePlanId = safeToken(firstNonBlank(planId, newPlanId()), "plan");
        if (!readPlanIndex(effectivePlanId).isEmpty()) {
            Map<String, Object> result = resultMap(
                    false,
                    taskId,
                    "Plan ID " + effectivePlanId + " already exists; use a new plan_id"
            );
            result.put("plan_id", effectivePlanId);
            return result;
        }

        Path submittedPlanPath;
        try {
            submittedPlanPath = resolveSubmittedPlanPath(planPath);
        } catch (IOException | IllegalArgumentException exception) {
            Map<String, Object> result = resultMap(false, taskId, exception.getMessage());
            result.put("plan_id", effectivePlanId);
            return result;
        }

        if (Objects.equals(task.getStatus(), TaskStatus.PENDING.value())) {
            boolean claimed = join(database.claimTask(taskId, memberName));
            if (!claimed) {
                return resultMap(false, taskId, "Failed to reserve task for planning");
            }
        } else if (!Objects.equals(task.getAssignee(), memberName)) {
            return resultMap(false, taskId, "Task " + taskId + " is assigned to "
                    + task.getAssignee() + ", not " + memberName);
        }

        Path memberPlanPath = taskPlanPath(taskId, effectivePlanId);
        try {
            Files.createDirectories(memberPlanPath.getParent());
            if (!submittedPlanPath.equals(memberPlanPath.toAbsolutePath().normalize())) {
                Files.copy(submittedPlanPath, memberPlanPath, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            Map<String, Object> result = resultMap(false, taskId, exception.getMessage());
            result.put("plan_id", effectivePlanId);
            return result;
        }

        Map<String, Object> planRecord = new LinkedHashMap<>();
        planRecord.put("task_id", taskId);
        planRecord.put("plan_id", effectivePlanId);
        planRecord.put("team_plan_id", teamPlanId);
        planRecord.put("latest_plan_id", effectivePlanId);
        planRecord.put("member_name", memberName);
        planRecord.put("status", TaskStatus.CLAIMED.value());
        planRecord.put("member_plan_md", memberPlanPath.toString());
        planRecord.put("source_plan_path", submittedPlanPath.toString());
        planRecord.put("tool_call_id", effectiveToolCallId);
        planRecord.put("decision", "pending");
        planRecord.put("submitted_at", nowIso());
        Map<String, Object> indexRecord = new LinkedHashMap<>(planRecord);
        indexRecord.put("updated_at", nowIso());
        writeTaskPlanIndex(taskId, indexRecord);

        publishTaskEvent(
                taskPlanRequestEvent(taskId, memberName, effectivePlanId, memberPlanPath.toString(), effectiveToolCallId),
                "Task plan request event for " + taskId,
                true
        );
        String leaderMessageId = notifyLeaderOfPlan(planRecord);
        if (leaderMessageId != null && !leaderMessageId.isEmpty()) {
            Map<String, Object> leaderUpdate = new LinkedHashMap<>();
            leaderUpdate.put("plan_id", effectivePlanId);
            leaderUpdate.put("leader_message_id", leaderMessageId);
            leaderUpdate.put("updated_at", nowIso());
            writeTaskPlanIndex(taskId, leaderUpdate);
        }

        Map<String, Object> result = resultMap(
                true,
                taskId,
                "Member plan submitted. Wait for leader approval before execution."
        );
        result.put("plan_id", effectivePlanId);
        result.put("status", TaskStatus.CLAIMED.value());
        result.put("member_plan_md", memberPlanPath.toString());
        result.put("leader_message_id", leaderMessageId);
        return result;
    }

    private TaskOpResult approvePlanSync(String planId, boolean approved, String feedback, String leaderName) {
        if (planId == null || planId.isEmpty()) {
            return TaskOpResult.fail("approve_plan requires plan_id");
        }
        Map<String, Object> planIndex = readPlanIndex(planId);
        if (planIndex.isEmpty()) {
            return TaskOpResult.fail("Plan " + planId + " not found");
        }
        String taskId = stringValue(planIndex.get("task_id")).trim();
        if (taskId.isEmpty()) {
            return TaskOpResult.fail("Plan " + planId + " has no task_id");
        }
        TeamTask existing = join(database.getTask(taskId)).orElse(null);
        if (existing == null) {
            return TaskOpResult.fail("Task " + taskId + " not found");
        }
        if (!Objects.equals(existing.getStatus(), TaskStatus.CLAIMED.value())) {
            return TaskOpResult.fail(
                    "Task " + taskId + " cannot be plan-approved from status '" + existing.getStatus()
                            + "'; only claimed tasks can be approved or rejected"
            );
        }
        if (existing.getAssignee() == null || existing.getAssignee().isEmpty()) {
            return TaskOpResult.fail("Task " + taskId + " has no assignee");
        }
        Map<String, Object> taskPlanIndex = readTaskPlanIndex(taskId);
        String latestPlanId = stringValue(taskPlanIndex.get("latest_plan_id"));
        if (!latestPlanId.isEmpty() && !Objects.equals(planId, latestPlanId)) {
            return TaskOpResult.fail("Plan " + planId + " is stale; review latest plan_id " + latestPlanId);
        }
        if (!Objects.equals(planIndex.get("decision"), "pending")) {
            return TaskOpResult.fail(
                    "Plan " + planId + " was already " + planIndex.get("decision")
                            + "; the member must call submit_plan again before another approval decision"
            );
        }
        String planPathRaw = stringValue(planIndex.get("member_plan_md")).trim();
        Path planPath = planPathRaw.isEmpty() ? taskPlanPath(taskId, planId) : Path.of(planPathRaw);
        if (!Files.isRegularFile(planPath)) {
            return TaskOpResult.fail(
                    "Plan " + planId + " for task " + taskId + " has no submitted plan file; "
                            + "the member must call submit_plan first"
            );
        }
        String toolCallId = stringValue(planIndex.get("tool_call_id"));
        String effectiveFeedback = feedback == null ? "" : feedback;
        String nextStatus = approved ? TaskStatus.PLAN_APPROVED.value() : TaskStatus.CLAIMED.value();
        try {
            Files.createDirectories(planPath.getParent());
        } catch (IOException exception) {
            throw new CompletionException(exception);
        }

        Map<String, Object> approval = new LinkedHashMap<>();
        approval.put("task_id", taskId);
        approval.put("plan_id", planId);
        approval.put("team_plan_id", teamPlanId);
        approval.put("latest_plan_id", planId);
        approval.put("decision", approved ? "approve" : "reject");
        approval.put("status", nextStatus);
        approval.put("feedback", effectiveFeedback);
        approval.put("leader_name", firstNonBlank(leaderName, memberName, "leader"));
        approval.put("member_name", existing.getAssignee());
        approval.put("member_plan_md", planPath.toString());
        approval.put("decided_at", nowIso());
        approval.put("tool_call_id", toolCallId);
        approval.put("updated_at", nowIso());

        if (!approved) {
            writeTaskPlanIndex(taskId, approval);
            publishTaskEvent(
                    taskPlanResponseEvent(
                            taskId,
                            existing.getAssignee(),
                            false,
                            TaskStatus.CLAIMED.value(),
                            planId,
                            effectiveFeedback,
                            toolCallId
                    ),
                    "Task plan response event for " + taskId,
                    true
            );
            TEAM_LOGGER.info("Task %s plan rejected; task remains claimed", taskId);
            return TaskOpResult.success();
        }

        TeamTask task = join(database.approvePlanTask(taskId)).orElse(null);
        if (task == null) {
            return TaskOpResult.fail("Task " + taskId + " could not transition to plan_approved");
        }
        writeTaskPlanIndex(taskId, approval);
        publishTaskEvent(
                taskPlanResponseEvent(
                        taskId,
                        task.getAssignee(),
                        true,
                        TaskStatus.PLAN_APPROVED.value(),
                        planId,
                        effectiveFeedback,
                        toolCallId
                ),
                "Task plan response event for " + taskId,
                true
        );
        TEAM_LOGGER.info("Task %s approved successfully", taskId);
        return TaskOpResult.success();
    }

    private void publishUnblockedEvents(List<TeamTask> unblockedTasks) {
        if (unblockedTasks == null || unblockedTasks.isEmpty()) {
            return;
        }
        for (TeamTask task : unblockedTasks) {
            publishTaskEvent(
                    taskUnblockedEvent(task.getTaskId()),
                    "Task unblocked event for " + task.getTaskId(),
                    true
            );
        }
        TEAM_LOGGER.info("Unblocked %d tasks", unblockedTasks.size());
    }

    private void maybePublishTaskListDrained() {
        List<TeamTask> tasks = join(listTasks());
        if (tasks.isEmpty()) {
            return;
        }
        boolean allTerminal = tasks.stream().allMatch(task -> TASK_TERMINAL_STATUSES.contains(task.getStatus()));
        if (!allTerminal) {
            return;
        }
        publishTaskEvent(
                taskListDrainedEvent(tasks.size()),
                "Task list drained event for team " + teamName,
                true
        );
    }

    private void publishTaskEvent(BaseEventMessage event, String errorLabel, boolean swallow) {
        try {
            publishTaskEventStrict(event);
            TEAM_LOGGER.debug("Published: %s", errorLabel);
        } catch (Exception exception) {
            if (!swallow) {
                throw exception;
            }
            TEAM_LOGGER.error("Failed to publish %s: %s", errorLabel, exception.getMessage());
        }
    }

    private void publishTaskEventStrict(BaseEventMessage event) {
        if (messager == null) {
            throw new NullPointerException("messager");
        }
        CompletionStage<Void> stage = messager.publish(
                TeamTopic.TASK.build(AgentTeamsContext.getSessionId(), teamName),
                EventMessage.fromEvent(event)
        );
        if (stage != null) {
            join(stage);
        }
    }

    private TaskCreatedEvent taskCreatedEvent(String taskId, String status) {
        TaskCreatedEvent event = new TaskCreatedEvent();
        event.setTeamName(teamName);
        event.setTaskId(taskId);
        event.setStatus(status);
        return event;
    }

    private TaskClaimedEvent taskClaimedEvent(String taskId, String assignee) {
        TaskClaimedEvent event = new TaskClaimedEvent();
        event.setTeamName(teamName);
        event.setTaskId(taskId);
        event.setMemberName(assignee);
        return event;
    }

    private TaskCompletedEvent taskCompletedEvent(String taskId, String assignee) {
        TaskCompletedEvent event = new TaskCompletedEvent();
        event.setTeamName(teamName);
        event.setTaskId(taskId);
        event.setMemberName(assignee);
        return event;
    }

    private TaskCancelledEvent taskCancelledEvent(String taskId) {
        TaskCancelledEvent event = new TaskCancelledEvent();
        event.setTeamName(teamName);
        event.setTaskId(taskId);
        return event;
    }

    private TaskUpdatedEvent taskUpdatedEvent(String taskId) {
        TaskUpdatedEvent event = new TaskUpdatedEvent();
        event.setTeamName(teamName);
        event.setTaskId(taskId);
        return event;
    }

    private TaskUnblockedEvent taskUnblockedEvent(String taskId) {
        TaskUnblockedEvent event = new TaskUnblockedEvent();
        event.setTeamName(teamName);
        event.setTaskId(taskId);
        return event;
    }

    private TaskListDrainedEvent taskListDrainedEvent(int taskCount) {
        TaskListDrainedEvent event = new TaskListDrainedEvent();
        event.setTeamName(teamName);
        event.setTaskCount(taskCount);
        return event;
    }

    private TaskPlanRequestEvent taskPlanRequestEvent(
            String taskId,
            String requestMemberName,
            String planId,
            String memberPlanMd,
            String toolCallId) {
        TaskPlanRequestEvent event = new TaskPlanRequestEvent();
        event.setTeamName(teamName);
        event.setTaskId(taskId);
        event.setMemberName(requestMemberName);
        event.setStatus(TaskStatus.CLAIMED.value());
        event.setPlanId(planId);
        event.setMemberPlanMd(memberPlanMd);
        event.setToolCallId(toolCallId);
        return event;
    }

    private TaskPlanResponseEvent taskPlanResponseEvent(
            String taskId,
            String responseMemberName,
            boolean approved,
            String status,
            String planId,
            String feedback,
            String toolCallId) {
        TaskPlanResponseEvent event = new TaskPlanResponseEvent();
        event.setTeamName(teamName);
        event.setTaskId(taskId);
        event.setMemberName(responseMemberName);
        event.setApproved(approved);
        event.setStatus(status);
        event.setPlanId(planId);
        event.setFeedback(feedback);
        event.setToolCallId(toolCallId);
        return event;
    }

    private Path taskPlanDir(String taskId) {
        return plansDir.resolve(teamPlanId).resolve("tasks").resolve(safeToken(taskId, "task"));
    }

    private static String newPlanId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private Path taskPlanPath(String taskId, String planId) {
        return taskPlanDir(taskId).resolve("plans").resolve(safeToken(planId, "plan") + ".md");
    }

    private static Path resolveSubmittedPlanPath(String planPath) throws IOException {
        String rawPath = trimToEmpty(planPath);
        if (rawPath.isEmpty()) {
            throw new IllegalArgumentException("submit_plan requires plan_path");
        }
        Path submittedPath = Path.of(rawPath).toAbsolutePath();
        if (!Path.of(rawPath).isAbsolute()) {
            Path baseDir;
            try {
                baseDir = Path.of(Cwd.getCwd()).toAbsolutePath();
            } catch (Exception exception) {
                baseDir = Path.of("").toAbsolutePath();
            }
            submittedPath = baseDir.resolve(rawPath);
        }
        submittedPath = submittedPath.normalize();
        if (!Files.isRegularFile(submittedPath)) {
            throw new IOException("submit_plan plan_path does not exist or is not a file: " + submittedPath);
        }
        return submittedPath;
    }

    private String resolveLeaderMemberName() {
        if (!leaderMemberName.isEmpty()) {
            return leaderMemberName;
        }
        Optional<Team> team = join(database.getTeam(teamName));
        String resolved = team.map(Team::getLeaderMemberName).orElse("");
        leaderMemberName = trimToEmpty(resolved);
        return leaderMemberName;
    }

    private String notifyLeaderOfPlan(Map<String, Object> planRecord) {
        String leader = resolveLeaderMemberName();
        if (leader.isEmpty()) {
            TEAM_LOGGER.warning(
                    "submit_plan could not notify leader: team=%s task_id=%s plan_id=%s has no leader_member_name",
                    teamName,
                    planRecord.get("task_id"),
                    planRecord.get("plan_id")
            );
            return null;
        }
        if (Objects.equals(leader, memberName) || messageNotifier == null) {
            return null;
        }
        try {
            return join(messageNotifier.sendMessage(renderPlanReviewMessage(planRecord), leader));
        } catch (Exception exception) {
            TEAM_LOGGER.warning(
                    "submit_plan failed to notify leader %s for task %s plan %s: %s",
                    leader,
                    planRecord.get("task_id"),
                    planRecord.get("plan_id"),
                    exception.getMessage()
            );
            return null;
        }
    }

    private static String renderPlanReviewMessage(Map<String, Object> planRecord) {
        List<String> lines = new ArrayList<>();
        lines.add("Member task plan approval request.");
        lines.add("Member: " + planRecord.get("member_name"));
        lines.add("Task ID: " + planRecord.get("task_id"));
        lines.add("Plan ID: " + planRecord.get("plan_id"));
        lines.add("Plan file: " + planRecord.get("member_plan_md"));
        String toolCallId = trimToEmpty(planRecord.get("tool_call_id"));
        if (!toolCallId.isEmpty()) {
            lines.add("Tool Call ID: " + toolCallId);
        }
        lines.add("");
        lines.add("Please review the plan file and call approve_plan with this plan_id.");
        return String.join("\n", lines);
    }

    private void writeTaskPlanIndex(String taskId, Map<String, Object> update) {
        Path indexPath = plansDir.resolve("index.json");
        Map<String, Object> index = jsonRead(indexPath);
        Map<String, Object> tasks = mapCopy(index.get("tasks"));
        Map<String, Object> current = mapCopy(tasks.get(taskId));
        String planId = trimToEmpty(update.get("plan_id"));
        if (!planId.isEmpty()) {
            List<Object> knownIds = listCopy(current.get("plan_ids"));
            if (!knownIds.contains(planId)) {
                knownIds.add(planId);
            }
            current.put("plan_ids", knownIds);
        }
        current.putAll(update);
        tasks.put(taskId, current);

        Map<String, Object> taskPlans = mapCopy(index.get("task_plans"));
        if (!planId.isEmpty()) {
            Map<String, Object> currentPlan = mapCopy(taskPlans.get(planId));
            currentPlan.putAll(update);
            currentPlan.put("task_id", taskId);
            taskPlans.put(planId, currentPlan);
        }

        index.put("team_name", teamName);
        index.put("team_plan_id", teamPlanId);
        index.put("plans_dir", plansDir.toString());
        index.put("updated_at", nowIso());
        index.put("tasks", tasks);
        index.put("task_plans", taskPlans);
        jsonWrite(indexPath, index);
    }

    private Map<String, Object> readTaskPlanIndex(String taskId) {
        Map<String, Object> index = jsonRead(plansDir.resolve("index.json"));
        Map<String, Object> tasks = mapCopy(index.get("tasks"));
        return mapCopy(tasks.get(taskId));
    }

    private Map<String, Object> readPlanIndex(String planId) {
        Map<String, Object> index = jsonRead(plansDir.resolve("index.json"));
        Map<String, Object> taskPlans = mapCopy(index.get("task_plans"));
        return mapCopy(taskPlans.get(planId));
    }

    private static Map<String, Object> jsonRead(Path path) {
        if (!Files.isRegularFile(path)) {
            return new LinkedHashMap<>();
        }
        try {
            Map<String, Object> loaded = OBJECT_MAPPER.readValue(Files.readString(path, StandardCharsets.UTF_8), MAP_TYPE);
            return loaded == null ? new LinkedHashMap<>() : new LinkedHashMap<>(loaded);
        } catch (Exception exception) {
            TEAM_LOGGER.warning("Failed to read team plan json %s: %s", path, exception.getMessage());
            return new LinkedHashMap<>();
        }
    }

    private static void jsonWrite(Path path, Map<String, Object> data) {
        try {
            Files.createDirectories(path.getParent());
            String content = OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(data);
            Files.writeString(path, content, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new CompletionException(exception);
        }
    }

    private static Map<String, Object> resultMap(boolean success, String taskId, String message) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", success);
        result.put("task_id", taskId);
        result.put("message", message);
        return result;
    }

    private static Map<String, Object> mapCopy(Object value) {
        Map<String, Object> copy = new LinkedHashMap<>();
        if (value instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() != null) {
                    copy.put(String.valueOf(entry.getKey()), entry.getValue());
                }
            }
        }
        return copy;
    }

    private static List<Object> listCopy(Object value) {
        if (!(value instanceof List<?> list)) {
            return new ArrayList<>();
        }
        return new ArrayList<>(list);
    }

    private static List<String> stringList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (Object item : list) {
            if (item != null) {
                result.add(String.valueOf(item));
            }
        }
        return result;
    }

    private static boolean isValidTaskTransition(String currentStatus, TaskStatus nextStatus) {
        try {
            return StatusTransitions.isValidTransition(
                    TaskStatus.fromValue(currentStatus),
                    nextStatus,
                    StatusTransitions.TASK_TRANSITIONS
            );
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private static String safeToken(String value, String fallback) {
        String normalized = UNSAFE_TOKEN_PATTERN.matcher(trimToEmpty(value)).replaceAll("_");
        normalized = normalized.replaceAll("^[._-]+", "").replaceAll("[._-]+$", "");
        if (normalized.length() > 96) {
            normalized = normalized.substring(0, 96);
        }
        return normalized.isEmpty() ? fallback : normalized;
    }

    private static String nowIso() {
        return OffsetDateTime.now(ZoneOffset.UTC)
                .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                .replace("Z", "+00:00");
    }

    private static String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static String trimToEmpty(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static String emptyToNull(String value) {
        return value == null || value.isEmpty() ? null : value;
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private static <T> CompletionStage<T> supplyStage(Supplier<T> supplier) {
        try {
            return CompletableFuture.completedFuture(supplier.get());
        } catch (Throwable throwable) {
            CompletableFuture<T> failed = new CompletableFuture<>();
            failed.completeExceptionally(throwable);
            return failed;
        }
    }

    private static <T> T join(CompletionStage<T> stage) {
        try {
            return stage.toCompletableFuture().join();
        } catch (CompletionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw exception;
        }
    }

    private static TeamTaskDatabase databaseFrom(TeamDatabase database) {
        Objects.requireNonNull(database, "database");
        if (database.getTask() == null || database.getMember() == null || database.getTeam() == null) {
            database.initialize().join();
        }
        TaskDao taskDao = Objects.requireNonNull(database.getTask(), "taskDao");
        MemberDao memberDao = Objects.requireNonNull(database.getMember(), "memberDao");
        TeamDao teamDao = Objects.requireNonNull(database.getTeam(), "teamDao");
        return new TeamTaskDatabase() {
            @Override
            public CompletionStage<Boolean> createTask(
                    String taskId,
                    String teamName,
                    String title,
                    String content,
                    String status) {
                return taskDao.createTask(taskId, teamName, title, content, status);
            }

            @Override
            public CompletionStage<GraphMutationResult> mutateDependencyGraph(
                    String teamName,
                    List<NewTaskSpec> newTasks,
                    List<TaskDao.DependencyEdge> addEdges) {
                return taskDao.mutateDependencyGraph(teamName, newTasks, addEdges);
            }

            @Override
            public CompletionStage<Boolean> addTaskWithBidirectionalDependencies(
                    String taskId,
                    String teamName,
                    String title,
                    String content,
                    String status,
                    List<String> dependencies,
                    List<String> dependentTaskIds) {
                return taskDao.addTaskWithBidirectionalDependencies(
                        taskId,
                        teamName,
                        title,
                        content,
                        status,
                        dependencies,
                        dependentTaskIds
                );
            }

            @Override
            public CompletionStage<Optional<TeamTask>> getTask(String taskId) {
                return taskDao.getTask(taskId);
            }

            @Override
            public CompletionStage<Boolean> claimTask(String taskId, String memberName) {
                return taskDao.claimTask(taskId, memberName);
            }

            @Override
            public CompletionStage<Optional<TeamTask>> resetTask(String taskId) {
                return taskDao.resetTask(taskId);
            }

            @Override
            public CompletionStage<Optional<TeamTask>> approvePlanTask(String taskId) {
                return taskDao.approvePlanTask(taskId);
            }

            @Override
            public CompletionStage<Boolean> updateTask(String taskId, String title, String content) {
                return taskDao.updateTask(taskId, title, content);
            }

            @Override
            public CompletionStage<Optional<TaskDao.TaskTerminationResult>> completeTask(String taskId) {
                return taskDao.completeTask(taskId);
            }

            @Override
            public CompletionStage<Optional<TaskDao.TaskTerminationResult>> cancelTask(String taskId) {
                return taskDao.cancelTask(taskId);
            }

            @Override
            public CompletionStage<TaskDao.TaskBulkCancellationResult> cancelAllTasks(
                    String teamName,
                    Set<String> skipAssignees) {
                return taskDao.cancelAllTasks(teamName, skipAssignees);
            }

            @Override
            public CompletionStage<List<TeamTask>> getTeamTasks(String teamName, String status) {
                return taskDao.getTeamTasks(teamName, status);
            }

            @Override
            public CompletionStage<List<TeamTaskDependency>> getTaskDependencies(String taskId) {
                return taskDao.getTaskDependencies(taskId);
            }

            @Override
            public CompletionStage<List<TeamTask>> getTasksByAssignee(
                    String teamName,
                    String memberName,
                    String status) {
                return taskDao.getTasksByAssignee(teamName, memberName, status);
            }

            @Override
            public CompletionStage<List<TeamTask>> getTasksDependingOn(String dependsOnTaskId) {
                return taskDao.getTasksDependingOn(dependsOnTaskId);
            }

            @Override
            public CompletionStage<Optional<TeamMember>> getMember(String memberName, String teamName) {
                return memberDao.getMember(memberName, teamName);
            }

            @Override
            public CompletionStage<Optional<Team>> getTeam(String teamName) {
                return teamDao.getTeam(teamName);
            }
        };
    }

    private static TeamTaskDatabase databaseFrom(InMemoryTeamDatabase database) {
        Objects.requireNonNull(database, "database");
        return new TeamTaskDatabase() {
            @Override
            public CompletionStage<Boolean> createTask(
                    String taskId,
                    String teamName,
                    String title,
                    String content,
                    String status) {
                return database.createTask(taskId, teamName, title, content, status);
            }

            @Override
            public CompletionStage<GraphMutationResult> mutateDependencyGraph(
                    String teamName,
                    List<NewTaskSpec> newTasks,
                    List<TaskDao.DependencyEdge> addEdges) {
                return database.mutateDependencyGraph(teamName, newTasks, addEdges);
            }

            @Override
            public CompletionStage<Boolean> addTaskWithBidirectionalDependencies(
                    String taskId,
                    String teamName,
                    String title,
                    String content,
                    String status,
                    List<String> dependencies,
                    List<String> dependentTaskIds) {
                return database.addTaskWithBidirectionalDependencies(
                        taskId,
                        teamName,
                        title,
                        content,
                        status,
                        dependencies,
                        dependentTaskIds
                );
            }

            @Override
            public CompletionStage<Optional<TeamTask>> getTask(String taskId) {
                return database.getTask(taskId);
            }

            @Override
            public CompletionStage<Boolean> claimTask(String taskId, String memberName) {
                return database.claimTask(taskId, memberName);
            }

            @Override
            public CompletionStage<Optional<TeamTask>> resetTask(String taskId) {
                return database.resetTask(taskId);
            }

            @Override
            public CompletionStage<Optional<TeamTask>> approvePlanTask(String taskId) {
                return database.approvePlanTask(taskId);
            }

            @Override
            public CompletionStage<Boolean> updateTask(String taskId, String title, String content) {
                return database.updateTask(taskId, title, content);
            }

            @Override
            public CompletionStage<Optional<TaskDao.TaskTerminationResult>> completeTask(String taskId) {
                return database.completeTask(taskId);
            }

            @Override
            public CompletionStage<Optional<TaskDao.TaskTerminationResult>> cancelTask(String taskId) {
                return database.cancelTask(taskId);
            }

            @Override
            public CompletionStage<TaskDao.TaskBulkCancellationResult> cancelAllTasks(
                    String teamName,
                    Set<String> skipAssignees) {
                return database.cancelAllTasks(teamName, skipAssignees);
            }

            @Override
            public CompletionStage<List<TeamTask>> getTeamTasks(String teamName, String status) {
                return database.getTeamTasks(teamName, status);
            }

            @Override
            public CompletionStage<List<TeamTaskDependency>> getTaskDependencies(String taskId) {
                return database.getTaskDependencies(taskId);
            }

            @Override
            public CompletionStage<List<TeamTask>> getTasksByAssignee(
                    String teamName,
                    String memberName,
                    String status) {
                return database.getTasksByAssignee(teamName, memberName, status);
            }

            @Override
            public CompletionStage<List<TeamTask>> getTasksDependingOn(String dependsOnTaskId) {
                return database.getTasksDependingOn(dependsOnTaskId);
            }

            @Override
            public CompletionStage<Optional<TeamMember>> getMember(String memberName, String teamName) {
                return database.getMember(memberName, teamName);
            }

            @Override
            public CompletionStage<Optional<Team>> getTeam(String teamName) {
                return database.getTeam(teamName);
            }
        };
    }

    /**
     * Narrow task database surface consumed by {@link TeamTaskManager}.
     *
     * <p>Mirrors Python's {@code TeamDatabase.team/member/task} usage in
     * {@code openjiuwen/agent_teams/tools/task_manager.py}.</p>
     */
    public interface TeamTaskDatabase {
        CompletionStage<Boolean> createTask(
                String taskId,
                String teamName,
                String title,
                String content,
                String status);

        CompletionStage<GraphMutationResult> mutateDependencyGraph(
                String teamName,
                List<NewTaskSpec> newTasks,
                List<TaskDao.DependencyEdge> addEdges);

        CompletionStage<Boolean> addTaskWithBidirectionalDependencies(
                String taskId,
                String teamName,
                String title,
                String content,
                String status,
                List<String> dependencies,
                List<String> dependentTaskIds);

        CompletionStage<Optional<TeamTask>> getTask(String taskId);

        CompletionStage<Boolean> claimTask(String taskId, String memberName);

        CompletionStage<Optional<TeamTask>> resetTask(String taskId);

        CompletionStage<Optional<TeamTask>> approvePlanTask(String taskId);

        CompletionStage<Boolean> updateTask(String taskId, String title, String content);

        CompletionStage<Optional<TaskDao.TaskTerminationResult>> completeTask(String taskId);

        CompletionStage<Optional<TaskDao.TaskTerminationResult>> cancelTask(String taskId);

        CompletionStage<TaskDao.TaskBulkCancellationResult> cancelAllTasks(
                String teamName,
                Set<String> skipAssignees);

        CompletionStage<List<TeamTask>> getTeamTasks(String teamName, String status);

        CompletionStage<List<TeamTaskDependency>> getTaskDependencies(String taskId);

        CompletionStage<List<TeamTask>> getTasksByAssignee(String teamName, String memberName, String status);

        CompletionStage<List<TeamTask>> getTasksDependingOn(String dependsOnTaskId);

        CompletionStage<Optional<TeamMember>> getMember(String memberName, String teamName);

        CompletionStage<Optional<Team>> getTeam(String teamName);
    }

    /**
     * Narrow leader-notification surface consumed by plan submission.
     *
     * <p>Mirrors Python's {@code TeamMessageManager.send_message} call in
     * {@code openjiuwen/agent_teams/tools/task_manager.py}.</p>
     */
    @FunctionalInterface
    public interface MessageNotifier {
        CompletionStage<String> sendMessage(String content, String toMemberName);
    }
}

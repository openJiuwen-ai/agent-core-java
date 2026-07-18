/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentteams.tools;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.agentteams.TeamPaths;
import com.openjiuwen.agentteams.messager.Messager;
import com.openjiuwen.agentteams.schema.events.EventMessage;
import com.openjiuwen.agentteams.schema.events.TeamEvent;
import com.openjiuwen.agentteams.schema.events.TeamTopic;
import com.openjiuwen.agentteams.spawn.SpawnContext;
import com.openjiuwen.agentteams.tools.database.MemberRecord;
import com.openjiuwen.agentteams.tools.database.TeamDatabase;
import com.openjiuwen.agentteams.tools.database.TeamRecord;
import com.openjiuwen.agentteams.tools.database.TaskMutationResult;
import com.openjiuwen.agentteams.tools.database.TaskRecord;
import com.openjiuwen.core.common.logging.Loggers;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.regex.Pattern;

/**
 * Public class TeamTaskManager used by the Java parity implementation.
 *
 * <p>Mirrors Python 0.1.15 {@code tools/task_manager.py:TeamTaskManager}.
 * Carries the plan-mode artifact storage ({@code plans_dir/index.json},
 * {@code submit_plan}, {@code get_plan_record}, {@code approve_plan} by
 * {@code plan_id}) so leader-teammate plan-mode collaboration matches the
 * Python 0.1.15 flow exactly.</p>
 *
 * @since 1.0
 */
public class TeamTaskManager {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Pattern SAFE_TOKEN_STRIP = Pattern.compile("[^A-Za-z0-9_.-]+");

    private final String teamName;
    private final String memberName;
    private final TeamDatabase db;
    private final Messager messager;
    private String teamSessionId;
    private Path plansDir;
    private String teamPlanId;
    private String leaderMemberName;

    /**
     * Construct without a shared database (in-memory only).
     *
     * @param teamName team id
     * @param memberName local member name
     * @param messager messager for event publishing
     */
    public TeamTaskManager(String teamName, String memberName, Messager messager) {
        this(teamName, memberName, null, messager);
    }

    /**
     * Construct with a shared team database.
     *
     * @param teamName team id
     * @param memberName local member name
     * @param db shared team database
     * @param messager messager for event publishing
     */
    public TeamTaskManager(String teamName, String memberName, TeamDatabase db, Messager messager) {
        this(teamName, memberName, db, messager, null, null, null);
    }

    /**
     * Construct with a pinned team-level session id and default plan-mode storage.
     *
     * @param teamName team id
     * @param memberName local member name
     * @param db shared team database
     * @param messager messager
     * @param teamSessionId team-level session id pinned at construction time
     * @since 0.1.13
     */
    public TeamTaskManager(String teamName, String memberName, TeamDatabase db, Messager messager,
                           String teamSessionId) {
        this(teamName, memberName, db, messager, null, null, null, teamSessionId);
    }

    /**
     * Construct with plan-mode storage configuration.
     *
     * <p>Mirrors Python {@code task_manager.__init__(plans_dir, team_plan_id,
     * leader_member_name)}. When any plan param is null/blank it resolves
     * to the Python default ({@code team_home(team)/team-workspace/plans},
     * {@code safe_token(session_id or team, "team_plan")}, {@code ""}).</p>
     *
     * @param teamName team id
     * @param memberName local member name
     * @param db shared team database
     * @param messager messager for event publishing
     * @param plansDir plans directory; null resolves to default
     * @param teamPlanId team plan id; null/blank resolves via session/team
     * @param leaderMemberName leader member name
     */
    public TeamTaskManager(
            String teamName,
            String memberName,
            TeamDatabase db,
            Messager messager,
            Path plansDir,
            String teamPlanId,
            String leaderMemberName) {
        this(teamName, memberName, db, messager, plansDir, teamPlanId, leaderMemberName,
                SpawnContext.getSessionId());
    }

    /**
     * Construct with plan-mode storage configuration and a pinned team-level session id.
     *
     * @param teamName team id
     * @param memberName local member name
     * @param db shared team database
     * @param messager messager
     * @param plansDir plans directory; null resolves to default
     * @param teamPlanId team plan id; null/blank resolves via session/team
     * @param leaderMemberName leader member name
     * @param teamSessionId team-level session id pinned at construction time
     * @since 0.1.13
     */
    public TeamTaskManager(
            String teamName,
            String memberName,
            TeamDatabase db,
            Messager messager,
            Path plansDir,
            String teamPlanId,
            String leaderMemberName,
            String teamSessionId) {
        this.teamName = teamName;
        this.memberName = memberName;
        this.db = db;
        this.messager = messager;
        this.teamSessionId = teamSessionId != null ? teamSessionId : "";
        this.plansDir = plansDir != null ? plansDir
                : TeamPaths.teamHome(teamName).resolve("team-workspace").resolve("plans");
        String planIdSeed = (teamPlanId != null && !teamPlanId.isBlank())
                ? teamPlanId
                : (!this.teamSessionId.isBlank()
                ? this.teamSessionId
                : teamName);
        this.teamPlanId = safeToken(planIdSeed, "team_plan");
        this.leaderMemberName = leaderMemberName != null ? leaderMemberName.trim() : "";
    }

    /**
     * Latch the team-level session id after construction.
     *
     * @param sessionId team-level session id; {@code null} is ignored
     * @since 0.1.13
     */
    public void setTeamSessionId(String sessionId) {
        if (sessionId != null) {
            this.teamSessionId = sessionId;
        }
    }

    /**
     * Configure where member plan files and approvals are persisted.
     *
     * <p>Mirrors Python {@code task_manager.configure_plan_storage}. Allows
     * the hosting TeamAgent to override the default plan storage location
     * after construction (used when the actual user-customized workspace
     * path is resolved later than the manager constructor).</p>
     *
     * @param plansDir plans directory; null is ignored
     * @param teamPlanId team plan id; null/blank is ignored
     */
    public void configurePlanStorage(Path plansDir, String teamPlanId) {
        if (plansDir != null) {
            this.plansDir = plansDir;
        }
        if (teamPlanId != null && !teamPlanId.isBlank()) {
            this.teamPlanId = safeToken(teamPlanId, "team_plan");
        }
    }

    /**
     * Set the leader member name used for member plan review notifications.
     *
     * <p>Mirrors the lazy {@code _resolve_leader_member_name} fallback in
     * Python {@code task_manager}. Setter form so callers that learn the
     * leader name late (e.g. after a DB row read) can wire it without
     * reconstructing the manager.</p>
     *
     * @param leaderMemberName leader member name; null is treated as empty
     */
    public void setLeaderMemberName(String leaderMemberName) {
        this.leaderMemberName = leaderMemberName != null ? leaderMemberName.trim() : "";
    }

    /**
     * Create a task with no explicit id or dependencies.
     *
     * @param title task title
     * @param content task content
     * @return CompletableFuture with the created TeamTask
     */
    public CompletableFuture<TeamTask> add(String title, String content) {
        return add(title, content, null, List.of());
    }

    /**
     * Create a task. Mirrors Python {@code task_manager.add} — no assignee at
     * creation; assignee is only set via {@code claim_task} / {@code assign}.
     *
     * @param title task title
     * @param content task content
     * @param taskId task id; null generates a random UUID
     * @param dependencies task ids this task depends on
     * @return CompletableFuture with the created TeamTask
     */
    public CompletableFuture<TeamTask> add(String title, String content, String taskId,
                                           List<String> dependencies) {
        return add(title, content, taskId, dependencies, null);
    }

    /**
     * Create a single task with optional assignee.
     * If assignee is provided and valid, the task is created in "claimed" status
     * and only the assigned member can claim or complete it.
     *
     * @param title task title
     * @param content task description
     * @param taskId task id; null generates a random UUID
     * @param dependencies task ids this task depends on
     * @param assignee optional member name to assign the task to
     * @return CompletableFuture with the created TeamTask
     */
    public CompletableFuture<TeamTask> add(String title, String content, String taskId,
                                           List<String> dependencies, String assignee) {
        String resolvedTaskId = taskId != null ? taskId : UUID.randomUUID().toString();
        TeamTask task = TeamTask.builder()
                .taskId(resolvedTaskId)
                .teamName(teamName)
                .title(title)
                .content(content)
                .status(dependencies != null && !dependencies.isEmpty() ? "blocked" : "pending")
                .dependencies(new ArrayList<>(dependencies != null ? dependencies : List.of()))
                .build();
        // Handle assignee: if provided, must already be a spawned member.
        // Refuses silently creating a task with null assignee when the caller
        // asked for one — that leaves the task unclaimable and is the root
        // cause of stuck FINAL tasks.
        if (assignee != null && !assignee.isBlank()) {
            if (db != null && shouldValidateMembers() && db.member.getMember(assignee, teamName) == null) {
                throw new CompletionException(new IllegalStateException(
                        "assignee " + assignee + " not found in team " + teamName
                        + "; spawn_member must run before create_task with assignee (task_id=" + resolvedTaskId + ")"));
            }
            task.setAssignee(assignee);
            // Only set claimed if no dependencies block the task
            if (!"blocked".equals(task.getStatus())) {
                task.setStatus("claimed");
            }
        }
        if (db != null) {
            Loggers.TOOL.info("TeamTaskManager.add: creating task {} team={} db={} session={} assignee={}",
                    resolvedTaskId, teamName, Integer.toHexString(System.identityHashCode(db)),
                    teamSessionId, task.getAssignee());
            db.task.createTask(resolvedTaskId, teamName, title, content, task.getStatus());
            if (dependencies != null) {
                for (String dependency : dependencies) {
                    db.task.addDependency(resolvedTaskId, dependency);
                }
            }
            // Persist assignee if set
            if (task.getAssignee() != null) {
                db.task.assignTask(resolvedTaskId, task.getAssignee());
            }
            Optional<TeamTask> reloadedOpt = get(resolvedTaskId);
            if (reloadedOpt.isPresent()) {
                task.setStatus(reloadedOpt.get().getStatus());
            }
        }
        return messager.publish(taskTopic(), EventMessage.builder()
                        .eventType(task.getAssignee() != null ? TeamEvent.TASK_CLAIMED : TeamEvent.TASK_CREATED)
                        .payload(task.getAssignee() != null
                                ? java.util.Map.of("task_id", resolvedTaskId, "member_name", task.getAssignee())
                                : java.util.Map.of("task_id", resolvedTaskId))
                        .build())
                .thenApply(ignored -> task);
    }

    /**
     * Create multiple tasks in sequence. Mirrors Python {@code task_manager.add_batch}.
     *
     * @param tasks list of task specification maps with keys title, content, task_id, dependencies, assignee
     * @return CompletableFuture with list of created TeamTask instances
     */
    public CompletableFuture<List<TeamTask>> addBatch(List<Map<String, Object>> tasks) {
        if (tasks == null || tasks.isEmpty()) {
            return CompletableFuture.completedFuture(List.of());
        }
        CompletableFuture<List<TeamTask>> chain = CompletableFuture.completedFuture(new ArrayList<>());
        for (Map<String, Object> taskSpec : tasks) {
            chain = chain.thenCompose(created -> {
                String title = stringValue(taskSpec.get("title"));
                String content = stringValue(taskSpec.get("content"));
                if (title == null || title.isBlank() || content == null || content.isBlank()) {
                    return CompletableFuture.completedFuture(created);
                }
                String taskId = stringValue(taskSpec.get("task_id"));
                List<String> dependencies = stringList(taskSpec.get("dependencies"));
                String assignee = stringValue(taskSpec.get("assignee"));
                return add(title, content, taskId, dependencies, assignee).thenApply(task -> {
                    if (task != null) {
                        created.add(task);
                    }
                    return created;
                });
            });
        }
        return chain;
    }

    /**
     * Create a task with forward and reverse dependency edges.
     * Mirrors Python {@code task_manager.add_with_priority}.
     *
     * @param title task title
     * @param content task content
     * @param taskId task id; null generates a random UUID
     * @param dependsOn task ids this task depends on
     * @param dependedBy task ids that depend on this new task
     * @return CompletableFuture with the created TeamTask
     */
    public CompletableFuture<TeamTask> addWithPriority(
            String title, String content, String taskId,
            List<String> dependsOn, List<String> dependedBy) {
        String resolvedId = taskId != null ? taskId : UUID.randomUUID().toString();
        List<String> allDeps = new ArrayList<>();
        if (dependsOn != null) {
            allDeps.addAll(dependsOn);
        }

        // dependedBy: other tasks that depend on this new task
        // (mirrors Python add_with_priority depended_by parameter)
        if (dependedBy != null && !dependedBy.isEmpty()) {
            for (String depTaskId : dependedBy) {
                // Add dependency edges: depTaskId -> this task
                if (db != null) {
                    db.task.addDependency(depTaskId, resolvedId);
                }
            }
        }
        return add(title, content, resolvedId, allDeps);
    }

    /**
     * Create a task and make all existing pending tasks depend on it.
     *
     * @param title task title
     * @param content task content
     * @return CompletableFuture with the created top-priority TeamTask
     */
    public CompletableFuture<TeamTask> addAsTopPriority(String title, String content) {
        return addAsTopPriority(title, content, null);
    }

    /**
     * Create a top-priority task with an explicit task id.
     *
     * @param title task title
     * @param content task content
     * @param taskId task id; null generates a random UUID
     * @return CompletableFuture with the created top-priority TeamTask
     */
    public CompletableFuture<TeamTask> addAsTopPriority(String title, String content, String taskId) {
        List<String> pendingTaskIds = getClaimableTasks().stream()
                .map(TeamTask::getTaskId)
                .toList();
        return add(title, content, taskId, List.of()).thenCompose(topTask -> {
            if (topTask == null) {
                return CompletableFuture.completedFuture(null);
            }
            CompletableFuture<Void> chain = CompletableFuture.completedFuture(null);
            for (String pendingTaskId : pendingTaskIds) {
                chain = chain.thenCompose(ignored -> addDependencies(pendingTaskId, List.of(topTask.getTaskId()))
                        .thenApply(none -> null));
            }
            return chain.thenApply(ignored -> get(topTask.getTaskId()).orElse(null));
        });
    }

    /**
     * Claim a pending task for the current member.
     *
     * @param taskId task id to claim
     * @return CompletableFuture with true if claim succeeded, false otherwise
     */
    public CompletableFuture<Boolean> claim(String taskId) {
        return claimResult(taskId).thenApply(TaskOpResult::isOk);
    }

    /**
     * Claim a pending task and return a detailed result with failure reason.
     *
     * @param taskId task id to claim
     * @return CompletableFuture with TaskOpResult indicating success or failure reason
     */
    public CompletableFuture<TaskOpResult> claimResult(String taskId) {
        Optional<TeamTask> taskOpt = get(taskId);
        if (taskOpt.isEmpty()) {
            Loggers.TOOL.info("claimResult: task {} not found, db={} member={} team={}",
                    taskId,
                    db != null ? Integer.toHexString(System.identityHashCode(db)) : "null",
                    memberName, teamName);
            return CompletableFuture.completedFuture(TaskOpResult.fail("Task " + taskId + " not found"));
        }
        TeamTask task = taskOpt.get();
        if (db != null && shouldValidateMembers() && db.member.getMember(memberName, teamName) == null) {
            return CompletableFuture.completedFuture(
                    TaskOpResult.fail("Member " + memberName + " not found in team " + teamName));
        }
        if (memberName.equals(task.getAssignee()) && "claimed".equals(task.getStatus())) {
            return CompletableFuture.completedFuture(TaskOpResult.success());
        }
        if (task.getAssignee() != null) {
            return CompletableFuture.completedFuture(TaskOpResult.fail(
                    "Task " + taskId + " is already claimed by " + task.getAssignee()
                            + ", " + memberName + " cannot claim it"));
        }
        if (!"pending".equals(task.getStatus())) {
            return CompletableFuture.completedFuture(TaskOpResult.fail(
                    "Task " + taskId + " cannot be claimed from status '" + task.getStatus()
                            + "' (only pending tasks are claimable)"));
        }

        // Prevent task hogging: a member can only hold one claimed task at a time.
        // They must complete their current task before claiming another.
        long myClaimedCount = getTasksByAssignee(memberName, "claimed").size();
        if (myClaimedCount > 0) {
            return CompletableFuture.completedFuture(TaskOpResult.fail(
                    "Member " + memberName + " already has an active claimed task."
                            + " Complete it before claiming another."));
        }
        task.setStatus("claimed");
        task.setAssignee(memberName);
        if (db != null && !db.task.claimTask(taskId, memberName)) {
            return CompletableFuture.completedFuture(
                    TaskOpResult.fail("Database rejected claim for task " + taskId
                            + " (likely a concurrent claim race)"));
        }
        return messager.publish(taskTopic(), EventMessage.builder()
                        .eventType(TeamEvent.TASK_CLAIMED)
                        .payload(java.util.Map.of("task_id", taskId))
                        .build())
                .thenApply(ignored -> TaskOpResult.success());
    }

    /**
     * Mark a claimed task as completed.
     *
     * @param taskId task id to complete
     * @return CompletableFuture with true if completion succeeded, false otherwise
     */
    public CompletableFuture<Boolean> complete(String taskId) {
        return completeResult(taskId).thenApply(TaskOpResult::isOk);
    }

    /**
     * Mark a claimed task as completed and return a detailed result with failure reason.
     *
     * @param taskId task id to complete
     * @return CompletableFuture with TaskOpResult indicating success or failure reason
     */
    public CompletableFuture<TaskOpResult> completeResult(String taskId) {
        Optional<TeamTask> taskOpt = get(taskId);
        if (taskOpt.isEmpty()) {
            return CompletableFuture.completedFuture(TaskOpResult.fail("Task " + taskId + " not found"));
        }
        TeamTask task = taskOpt.get();
        if (db != null) {
            MemberRecord member = db.member.getMember(memberName, teamName);
            if (member == null && shouldValidateMembers()) {
                return CompletableFuture.completedFuture(
                        TaskOpResult.fail("Member " + memberName + " not found in team " + teamName));
            }
            if (member != null && "plan_mode".equals(member.getMode()) && !"plan_approved".equals(task.getStatus())) {
                return CompletableFuture.completedFuture(TaskOpResult.fail(
                        "PLAN_MODE member cannot complete task " + taskId + " in status '" + task.getStatus()
                                + "'; only plan_approved tasks can be completed"));
            }
        }
        if (!"claimed".equals(task.getStatus()) && !"plan_approved".equals(task.getStatus())) {
            return CompletableFuture.completedFuture(TaskOpResult.fail(
                    "Task " + taskId + " cannot be completed from status '" + task.getStatus()
                            + "' (must be claimed or plan_approved)"));
        }

        // Only the member who owns the task can complete it
        if (task.getAssignee() != null && !memberName.equals(task.getAssignee())) {
            return CompletableFuture.completedFuture(TaskOpResult.fail(
                    "Task " + taskId + " is owned by " + task.getAssignee()
                            + ", " + memberName + " cannot complete it"));
        }
        TaskMutationResult mutation = db != null ? db.task.completeTaskResult(taskId) : null;
        if (db != null && mutation == null) {
            Optional<TeamTask> currentOpt = get(taskId);
            String status = currentOpt.map(TeamTask::getStatus).orElse(task.getStatus());
            return CompletableFuture.completedFuture(TaskOpResult.fail(
                    "Task " + taskId + " cannot be completed from status '" + status
                            + "' (must be claimed or plan_approved)"));
        }
        task.setStatus("completed");
        return messager.publish(taskTopic(), EventMessage.builder()
                        .eventType(TeamEvent.TASK_COMPLETED)
                        .payload(java.util.Map.of("task_id", taskId))
                        .build())
                .thenCompose(ignored -> publishUnblockedEvents(mutation))
                .thenApply(ignored -> TaskOpResult.success());
    }

    /**
     * Cancel a task. Only the assignee or leader (when unassigned) can cancel.
     *
     * @param taskId task id to cancel
     * @return CompletableFuture with the cancelled TeamTask, or null if not found or not allowed
     */
    public CompletableFuture<TeamTask> cancel(String taskId) {
        Optional<TeamTask> taskOpt = get(taskId);
        if (taskOpt.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }
        TeamTask task = taskOpt.get();

        // Only the assignee (or leader when unassigned) can cancel.
        // This prevents non-leader members from cancelling leader tasks
        // and creating a cancel/recreate loop.
        if (task.getAssignee() != null && !memberName.equals(task.getAssignee())) {
            Loggers.AGENT.info("TeamTaskManager.cancel: member={} is not assignee={} of task [{}], rejecting",
                    memberName, task.getAssignee(), taskId);
            return CompletableFuture.completedFuture(null);
        }
        TaskMutationResult mutation = db != null ? db.task.cancelTaskResult(taskId) : null;
        if (db == null || mutation == null) {
            return CompletableFuture.completedFuture(null);
        }
        return messager.publish(taskTopic(), EventMessage.builder()
                        .eventType(TeamEvent.TASK_CANCELLED)
                        .payload(java.util.Map.of("task_id", taskId))
                        .build())
                .thenCompose(ignored -> publishUnblockedEvents(mutation))
                .thenApply(ignored -> get(taskId).orElse(null));
    }

    /**
     * Cancel all tasks in the team without skipping any assignee.
     *
     * @return CompletableFuture with list of all cancelled TeamTask instances
     */
    public CompletableFuture<List<TeamTask>> cancelAllTasks() {
        return cancelAllTasks(List.of());
    }

    /**
     * Cancel all tasks, optionally skipping tasks assigned to specific members.
     * Mirrors Python cancel_all_tasks(skip_assignees).
     *
     * @param skipAssignees member names whose claimed tasks should NOT be cancelled
     * @return CompletableFuture with list of cancelled TeamTask instances
     */
    public CompletableFuture<List<TeamTask>> cancelAllTasks(List<String> skipAssignees) {
        if (db == null) {
            return CompletableFuture.completedFuture(List.of());
        }
        TaskMutationResult mutation = db.task.cancelAllTasksResult(teamName);
        List<TeamTask> cancelled = mutation.getCancelledTasks().stream()
                .map(record -> get(record.getTaskId()))
                .flatMap(Optional::stream)
                .filter(task -> skipAssignees == null || skipAssignees.isEmpty()
                        || !skipAssignees.contains(task.getAssignee()))
                .toList();
        CompletableFuture<Void> published = CompletableFuture.completedFuture(null);
        for (TeamTask task : cancelled) {
            published = published.thenCompose(ignored -> messager.publish(taskTopic(), EventMessage.builder()
                    .eventType(TeamEvent.TASK_CANCELLED)
                    .payload(java.util.Map.of("task_id", task.getTaskId()))
                    .build()).thenApply(none -> null));
        }
        return published
                .thenCompose(ignored -> publishUnblockedEvents(mutation))
                .thenApply(ignored -> cancelled);
    }

    private CompletableFuture<Void> publishUnblockedEvents(TaskMutationResult mutation) {
        if (mutation == null || mutation.getUnblockedTasks() == null || mutation.getUnblockedTasks().isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }
        CompletableFuture<Void> published = CompletableFuture.completedFuture(null);
        for (TaskRecord task : mutation.getUnblockedTasks()) {
            published = published.thenCompose(ignored -> messager.publish(taskTopic(), EventMessage.builder()
                    .eventType(TeamEvent.TASK_UNBLOCKED)
                    .payload(java.util.Map.of("task_id", task.getTaskId()))
                    .build()).thenApply(none -> null));
        }
        return published;
    }

    /**
     * Reset a claimed task back to pending.
     *
     * @param taskId task id to reset
     * @return CompletableFuture with true if reset succeeded, false otherwise
     */
    public CompletableFuture<Boolean> reset(String taskId) {
        return resetResult(taskId).thenApply(TaskOpResult::isOk);
    }

    /**
     * Reset a claimed task back to pending and return a detailed result with failure reason.
     *
     * @param taskId task id to reset
     * @return CompletableFuture with TaskOpResult indicating success or failure reason
     */
    public CompletableFuture<TaskOpResult> resetResult(String taskId) {
        Optional<TeamTask> existingOpt = get(taskId);
        if (existingOpt.isEmpty()) {
            return CompletableFuture.completedFuture(TaskOpResult.fail("Task " + taskId + " not found"));
        }
        TeamTask existing = existingOpt.get();
        if (db == null || !db.task.resetTask(taskId)) {
            return CompletableFuture.completedFuture(TaskOpResult.fail(
                    "Task " + taskId + " cannot be reset from status '" + existing.getStatus()
                            + "'; only claimed tasks can be reset"));
        }
        return messager.publish(taskTopic(), EventMessage.builder()
                        .eventType(TeamEvent.TASK_UPDATED)
                        .payload(java.util.Map.of("task_id", taskId))
                        .build())
                .thenApply(ignored -> TaskOpResult.success());
    }

    /**
     * Assign a pending task to a specific member.
     *
     * @param taskId task id to assign
     * @param assignee member name to assign the task to
     * @return CompletableFuture with true if assignment succeeded, false otherwise
     */
    public CompletableFuture<Boolean> assign(String taskId, String assignee) {
        return assignResult(taskId, assignee).thenApply(TaskOpResult::isOk);
    }

    /**
     * Assign a pending task to a specific member and return a detailed result with failure reason.
     *
     * @param taskId task id to assign
     * @param assignee member name to assign the task to
     * @return CompletableFuture with TaskOpResult indicating success or failure reason
     */
    public CompletableFuture<TaskOpResult> assignResult(String taskId, String assignee) {
        Optional<TeamTask> taskOpt = get(taskId);
        if (taskOpt.isEmpty()) {
            return CompletableFuture.completedFuture(TaskOpResult.fail("Task " + taskId + " not found"));
        }
        TeamTask task = taskOpt.get();
        if (db == null || assignee == null || assignee.isBlank()) {
            return CompletableFuture.completedFuture(
                    TaskOpResult.fail("Member " + assignee + " not found in team " + teamName));
        }
        if (db.member.getMember(assignee, teamName) == null) {
            return CompletableFuture.completedFuture(
                    TaskOpResult.fail("Member " + assignee + " not found in team " + teamName));
        }
        if (assignee.equals(task.getAssignee()) && "claimed".equals(task.getStatus())) {
            return CompletableFuture.completedFuture(TaskOpResult.success());
        }
        if (task.getAssignee() != null && !assignee.equals(task.getAssignee())) {
            return CompletableFuture.completedFuture(TaskOpResult.fail(
                    "Task " + taskId + " is already claimed by " + task.getAssignee()
                            + "; reset the task before reassigning to " + assignee));
        }
        if (!db.task.assignTask(taskId, assignee)) {
            return CompletableFuture.completedFuture(TaskOpResult.fail(
                    "Database rejected assign for task " + taskId
                            + " (invalid state transition from " + task.getStatus() + ")"));
        }
        return messager.publish(taskTopic(), EventMessage.builder()
                        .eventType(TeamEvent.TASK_CLAIMED)
                        .payload(java.util.Map.of("task_id", taskId, "member_name", assignee))
                        .build())
                .thenApply(ignored -> TaskOpResult.success());
    }

    /**
     * Add dependency edges to a task.
     *
     * @param taskId task id to add dependencies to
     * @param dependsOnIds task ids that this task depends on
     * @return CompletableFuture with true if dependencies were added, false otherwise
     */
    public CompletableFuture<Boolean> addDependencies(String taskId, List<String> dependsOnIds) {
        return addDependenciesResult(taskId, dependsOnIds).thenApply(TaskOpResult::isOk);
    }

    /**
     * Add dependency edges to a task and return a detailed result with failure reason.
     *
     * @param taskId task id to add dependencies to
     * @param dependsOnIds task ids that this task depends on
     * @return CompletableFuture with TaskOpResult indicating success or failure reason
     */
    public CompletableFuture<TaskOpResult> addDependenciesResult(String taskId, List<String> dependsOnIds) {
        if (dependsOnIds == null || dependsOnIds.isEmpty()) {
            return CompletableFuture.completedFuture(TaskOpResult.success());
        }
        if (db == null || get(taskId).isEmpty()) {
            return CompletableFuture.completedFuture(TaskOpResult.fail("Task " + taskId + " not found"));
        }
        List<List<String>> edges = dependsOnIds.stream()
                .map(dependsOnId -> List.of(taskId, dependsOnId))
                .toList();
        com.openjiuwen.agentteams.tools.database.GraphMutationResult mutation =
                db.task.mutateDependencyGraph(teamName, edges);
        if (!mutation.isOk()) {
            return CompletableFuture.completedFuture(TaskOpResult.fail(mutation.getReason()));
        }
        return messager.publish(taskTopic(), EventMessage.builder()
                        .eventType(TeamEvent.TASK_UPDATED)
                        .payload(java.util.Map.of("task_id", taskId))
                        .build())
                .thenApply(ignored -> TaskOpResult.success());
    }

    /**
     * Update a task's title and content.
     *
     * @param taskId task id to update
     * @param title new task title
     * @param content new task content
     * @return CompletableFuture with true if update succeeded, false otherwise
     */
    public CompletableFuture<Boolean> updateTask(String taskId, String title, String content) {
        return updateTaskResult(taskId, title, content).thenApply(TaskOpResult::isOk);
    }

    /**
     * Update a task's title and content and return a detailed result with failure reason.
     *
     * @param taskId task id to update
     * @param title new task title
     * @param content new task content
     * @return CompletableFuture with TaskOpResult indicating success or failure reason
     */
    public CompletableFuture<TaskOpResult> updateTaskResult(String taskId, String title, String content) {
        Optional<TeamTask> taskOpt = get(taskId);
        if (taskOpt.isEmpty()) {
            return CompletableFuture.completedFuture(TaskOpResult.fail("Task " + taskId + " not found"));
        }
        TeamTask task = taskOpt.get();
        if (db == null || !db.task.updateTask(taskId, title, content)) {
            return CompletableFuture.completedFuture(TaskOpResult.fail(
                    "Task " + taskId + " cannot be edited while in status '" + task.getStatus()
                            + "'; content updates are only allowed on pending / blocked tasks"));
        }
        return messager.publish(taskTopic(), EventMessage.builder()
                        .eventType(TeamEvent.TASK_UPDATED)
                        .payload(java.util.Map.of("task_id", taskId))
                        .build())
                .thenApply(ignored -> TaskOpResult.success());
    }

    /**
     * Approve or reject a member plan submission by {@code plan_id}.
     *
     * <p>Mirrors Python 0.1.15 {@code task_manager.approve_plan(plan_id,
     * approved, feedback, leader_name)}. Plan-mode reuses the existing task
     * state machine: PENDING &rarr; CLAIMED when a member submits a plan,
     * then CLAIMED &rarr; PLAN_APPROVED when the leader approves. A rejection
     * keeps the task in CLAIMED so the member can revise and resubmit a new
     * plan id.</p>
     *
     * @param planId plan id to approve or reject
     * @param isApproved true to approve, false to reject
     * @param feedback optional feedback for the member
     * @param leaderName leader name; null/blank resolves to current member
     * @return CompletableFuture with true if approval/rejection succeeded, false otherwise
     */
    public CompletableFuture<Boolean> approvePlan(
            String planId, boolean isApproved, String feedback, String leaderName) {
        return approvePlanResult(planId, isApproved, feedback, leaderName)
                .thenApply(TaskOpResult::isOk);
    }

    /**
     * Approve or reject a member plan and return a detailed result with failure reason.
     *
     * @param planId plan id to approve or reject
     * @param isApproved true to approve, false to reject
     * @param feedback optional feedback for the member
     * @param leaderName leader name; null/blank resolves to current member
     * @return CompletableFuture with TaskOpResult indicating success or failure reason
     */
    public CompletableFuture<TaskOpResult> approvePlanResult(
            String planId, boolean isApproved, String feedback, String leaderName) {
        if (planId == null || planId.isBlank()) {
            return CompletableFuture.completedFuture(TaskOpResult.fail("approve_plan requires plan_id"));
        }
        Map<String, Object> planIndex = readPlanIndexById(planId);
        if (planIndex.isEmpty()) {
            return CompletableFuture.completedFuture(TaskOpResult.fail("Plan " + planId + " not found"));
        }
        String taskId = stringFromPlan(planIndex, "task_id");
        if (taskId.isBlank()) {
            return CompletableFuture.completedFuture(
                    TaskOpResult.fail("Plan " + planId + " has no task_id"));
        }
        Optional<TeamTask> taskOpt = get(taskId);
        if (taskOpt.isEmpty()) {
            return CompletableFuture.completedFuture(
                    TaskOpResult.fail("Task " + taskId + " not found"));
        }
        TeamTask task = taskOpt.get();
        Optional<TaskOpResult> validationError = validatePlanForApproval(planId, planIndex, task);
        if (validationError.isPresent()) {
            return CompletableFuture.completedFuture(validationError.get());
        }
        String planPathRaw = stringFromPlan(planIndex, "member_plan_md");
        Path planPath = !planPathRaw.isBlank() ? Path.of(planPathRaw) : taskPlanPath(taskId, planId);
        if (!Files.isRegularFile(planPath)) {
            return CompletableFuture.completedFuture(TaskOpResult.fail(
                    "Plan " + planId + " for task " + taskId
                            + " has no submitted plan file; the member must call submit_plan first"));
        }
        String toolCallId = stringFromPlan(planIndex, "tool_call_id");
        PlanApprovalParams params = PlanApprovalParams.builder()
                .planId(planId).isApproved(isApproved).feedback(feedback)
                .leaderName(leaderName).task(task).planPath(planPath)
                .toolCallId(toolCallId).build();
        return applyPlanDecision(params);
    }

    /**
     * Validate that a plan is eligible for approval/rejection.
     *
     * @param planId the plan ID being decided
     * @param planIndex the plan's index data
     * @param task the associated task
     * @return a failure result if validation fails, or {@link Optional#empty()} if valid
     */
    private Optional<TaskOpResult> validatePlanForApproval(
            String planId, Map<String, Object> planIndex, TeamTask task) {
        String taskId = task.getTaskId();
        if (!"claimed".equals(task.getStatus())) {
            return Optional.of(TaskOpResult.fail(
                    "Task " + taskId + " cannot be plan-approved from status '"
                            + task.getStatus() + "'; only claimed tasks can be approved or rejected"));
        }
        if (task.getAssignee() == null || task.getAssignee().isBlank()) {
            return Optional.of(TaskOpResult.fail("Task " + taskId + " has no assignee"));
        }
        Map<String, Object> taskPlanIndex = readTaskPlanIndex(taskId);
        String latestPlanId = stringFromPlan(taskPlanIndex, "latest_plan_id");
        if (!latestPlanId.isBlank() && !planId.equals(latestPlanId)) {
            return Optional.of(TaskOpResult.fail(
                    "Plan " + planId + " is stale; review latest plan_id " + latestPlanId));
        }
        String currentDecision = stringFromPlan(planIndex, "decision");
        if (!"pending".equals(currentDecision)) {
            return Optional.of(TaskOpResult.fail(
                    "Plan " + planId + " was already " + currentDecision
                            + "; the member must call submit_plan again before another approval decision"));
        }
        return Optional.empty();
    }

    /**
     * Parameters for applying a plan approval/rejection decision.
     */
    private static final class PlanApprovalParams {
        private final String planId;
        private final boolean isApproved;
        private final String feedback;
        private final String leaderName;
        private final TeamTask task;
        private final Path planPath;
        private final String toolCallId;

        private PlanApprovalParams(Builder builder) {
            this.planId = builder.planId;
            this.isApproved = builder.isApproved;
            this.feedback = builder.feedback;
            this.leaderName = builder.leaderName;
            this.task = builder.task;
            this.planPath = builder.planPath;
            this.toolCallId = builder.toolCallId;
        }

        String planId() {
            return planId;
        }

        boolean isApproved() {
            return isApproved;
        }

        String feedback() {
            return feedback;
        }

        String leaderName() {
            return leaderName;
        }

        TeamTask task() {
            return task;
        }

        Path planPath() {
            return planPath;
        }

        String toolCallId() {
            return toolCallId;
        }

        static Builder builder() {
            return new Builder();
        }

        static final class Builder {
            private String planId;
            private boolean isApproved;
            private String feedback;
            private String leaderName;
            private TeamTask task;
            private Path planPath;
            private String toolCallId;

            Builder planId(String val) {
                this.planId = val;
                return this;
            }

            Builder isApproved(boolean isApproved) {
                this.isApproved = isApproved;
                return this;
            }

            Builder feedback(String val) {
                this.feedback = val;
                return this;
            }

            Builder leaderName(String val) {
                this.leaderName = val;
                return this;
            }

            Builder task(TeamTask val) {
                this.task = val;
                return this;
            }

            Builder planPath(Path val) {
                this.planPath = val;
                return this;
            }

            Builder toolCallId(String val) {
                this.toolCallId = val;
                return this;
            }

            PlanApprovalParams build() {
                return new PlanApprovalParams(this);
            }
        }
    }

    /**
     * Apply the approval/rejection decision to the plan and publish the event.
     *
     * @param params the plan approval parameters
     * @return CompletableFuture with TaskOpResult indicating success or failure reason
     */
    private CompletableFuture<TaskOpResult> applyPlanDecision(PlanApprovalParams params) {
        String planId = params.planId();
        boolean isApproved = params.isApproved();
        String feedback = params.feedback();
        String leaderName = params.leaderName();
        TeamTask task = params.task();
        String taskId = task.getTaskId();
        String nextStatus = isApproved ? "plan_approved" : "claimed";
        String decision = isApproved ? "approve" : "reject";
        String resolvedLeader = (leaderName != null && !leaderName.isBlank())
                ? leaderName
                : (!this.memberName.isBlank() ? this.memberName : "leader");
        PlanDecisionContext ctx = PlanDecisionContext.builder()
                .taskId(taskId).planId(planId).decision(decision)
                .nextStatus(nextStatus).feedback(feedback)
                .resolvedLeader(resolvedLeader).assignee(task.getAssignee())
                .planPath(params.planPath()).toolCallId(params.toolCallId())
                .build();
        Map<String, Object> approval = buildApprovalMap(ctx);

        if (!isApproved) {
            return publishPlanRejection(ctx, approval);
        }
        if (db == null || !db.task.approvePlanTask(taskId)) {
            return CompletableFuture.completedFuture(TaskOpResult.fail(
                    "Task " + taskId + " could not transition to plan_approved"));
        }
        writeTaskPlanIndex(taskId, approval);
        return publishPlanApproval(ctx);
    }

    private Map<String, Object> buildApprovalMap(PlanDecisionContext ctx) {
        Map<String, Object> approval = new LinkedHashMap<>();
        approval.put("task_id", ctx.taskId);
        approval.put("plan_id", ctx.planId);
        approval.put("team_plan_id", this.teamPlanId);
        approval.put("latest_plan_id", ctx.planId);
        approval.put("decision", ctx.decision);
        approval.put("status", ctx.nextStatus);
        approval.put("feedback", ctx.feedback != null ? ctx.feedback : "");
        approval.put("leader_name", ctx.resolvedLeader);
        approval.put("member_name", ctx.assignee);
        approval.put("member_plan_md", ctx.planPath.toString());
        approval.put("decided_at", nowIso());
        approval.put("tool_call_id", ctx.toolCallId);
        approval.put("updated_at", nowIso());
        return approval;
    }

    private CompletableFuture<TaskOpResult> publishPlanRejection(
            PlanDecisionContext ctx, Map<String, Object> approval) {
        writeTaskPlanIndex(ctx.taskId, approval);
        return publishTaskEvent(TeamEvent.TASK_PLAN_RESPONSE, Map.of(
                "team_name", teamName,
                "task_id", ctx.taskId,
                "member_name", ctx.assignee,
                "approved", false,
                "status", "claimed",
                "plan_id", ctx.planId,
                "feedback", ctx.feedback != null ? ctx.feedback : "",
                "tool_call_id", ctx.toolCallId))
                .thenApply(ignored -> TaskOpResult.success());
    }

    private CompletableFuture<TaskOpResult> publishPlanApproval(PlanDecisionContext ctx) {
        return publishTaskEvent(TeamEvent.TASK_PLAN_RESPONSE, Map.of(
                "team_name", teamName,
                "task_id", ctx.taskId,
                "member_name", ctx.assignee,
                "approved", true,
                "status", "plan_approved",
                "plan_id", ctx.planId,
                "feedback", ctx.feedback != null ? ctx.feedback : "",
                "tool_call_id", ctx.toolCallId))
                .thenApply(ignored -> TaskOpResult.success());
    }

    /**
     * Context for plan decision operations.
     */
    private static final class PlanDecisionContext {
        private final String taskId;
        private final String planId;
        private final String decision;
        private final String nextStatus;
        private final String feedback;
        private final String resolvedLeader;
        private final String assignee;
        private final Path planPath;
        private final String toolCallId;

        private PlanDecisionContext(Builder builder) {
            this.taskId = builder.taskId;
            this.planId = builder.planId;
            this.decision = builder.decision;
            this.nextStatus = builder.nextStatus;
            this.feedback = builder.feedback;
            this.resolvedLeader = builder.resolvedLeader;
            this.assignee = builder.assignee;
            this.planPath = builder.planPath;
            this.toolCallId = builder.toolCallId;
        }

        static Builder builder() {
            return new Builder();
        }

        static final class Builder {
            private String taskId;
            private String planId;
            private String decision;
            private String nextStatus;
            private String feedback;
            private String resolvedLeader;
            private String assignee;
            private Path planPath;
            private String toolCallId;

            Builder taskId(String val) {
                this.taskId = val;
                return this;
            }

            Builder planId(String val) {
                this.planId = val;
                return this;
            }

            Builder decision(String val) {
                this.decision = val;
                return this;
            }

            Builder nextStatus(String val) {
                this.nextStatus = val;
                return this;
            }

            Builder feedback(String val) {
                this.feedback = val;
                return this;
            }

            Builder resolvedLeader(String val) {
                this.resolvedLeader = val;
                return this;
            }

            Builder assignee(String val) {
                this.assignee = val;
                return this;
            }

            Builder planPath(Path val) {
                this.planPath = val;
                return this;
            }

            Builder toolCallId(String val) {
                this.toolCallId = val;
                return this;
            }

            PlanDecisionContext build() {
                return new PlanDecisionContext(this);
            }
        }
    }

    /**
     * Snapshot a member execution plan file and reserve the task as CLAIMED.
     *
     * <p>Mirrors Python 0.1.15 {@code task_manager.submit_plan}. Validates
     * the member is in PLAN_MODE, the task is claimable, then writes the
     * submitted plan file into {@code plans_dir}, appends to
     * {@code index.json}, publishes {@code task_plan_request}, and pings
     * the leader with a review message carrying the new {@code plan_id}.</p>
     *
     * @param taskId task id to submit a plan for
     * @param planPathStr path to the member plan markdown file
     * @param planIdIn plan id; null/blank generates a safe token id
     * @param toolCallId tool call id for correlation with the leader response
     * @return CompletableFuture with result map containing success, task_id, plan_id, status, member_plan_md
     */
    public CompletableFuture<Map<String, Object>> submitPlan(
            String taskId, String planPathStr, String planIdIn, String toolCallId) {
        if (db == null) {
            return CompletableFuture.completedFuture(failPlanResult(taskId, "Database not available"));
        }
        MemberRecord member = db.member.getMember(memberName, teamName);
        if (member == null) {
            return CompletableFuture.completedFuture(failPlanResult(
                    taskId, "Member " + memberName + " not found"));
        }
        if (!"plan_mode".equals(member.getMode())) {
            return CompletableFuture.completedFuture(failPlanResult(
                    taskId, "submit_plan is only for PLAN_MODE"));
        }
        Optional<TeamTask> taskOpt = get(taskId);
        if (taskOpt.isEmpty()) {
            return CompletableFuture.completedFuture(failPlanResult(
                    taskId, "Task " + taskId + " not found"));
        }
        TeamTask task = taskOpt.get();
        Map<String, Object> validationError = validateSubmitPlan(taskId, task);
        if (!validationError.isEmpty()) {
            return CompletableFuture.completedFuture(validationError);
        }
        String planId = safeToken(planIdIn != null && !planIdIn.isBlank() ? planIdIn
                : UUID.randomUUID().toString().replace("-", ""), "plan");
        if (!readPlanIndexById(planId).isEmpty()) {
            return CompletableFuture.completedFuture(failPlanResult(
                    taskId, "Plan ID " + planId + " already exists; use a new plan_id", planId));
        }
        Path submittedPlanPath;
        try {
            submittedPlanPath = resolveSubmittedPlanPath(planPathStr);
        } catch (IllegalArgumentException e) {
            return CompletableFuture.completedFuture(failPlanResult(
                    taskId, e.getMessage(), planId));
        }
        if (!"pending".equals(task.getStatus()) && !memberName.equals(task.getAssignee())) {
            return CompletableFuture.completedFuture(failPlanResult(
                    taskId, "Task " + taskId + " is assigned to "
                            + task.getAssignee() + ", not " + memberName));
        }
        if ("pending".equals(task.getStatus())
                && !db.task.claimTask(taskId, memberName)) {
            return CompletableFuture.completedFuture(failPlanResult(
                    taskId, "Failed to reserve task for planning"));
        }
        return persistAndPublishPlan(taskId, planId, submittedPlanPath, toolCallId);
    }

    /**
     * Validate that a task is eligible for plan submission.
     *
     * @param taskId the task ID
     * @param task the task to validate
     * @return a failure result map if validation fails, or an empty map if valid
     */
    private Map<String, Object> validateSubmitPlan(String taskId, TeamTask task) {
        if (task.getAssignee() != null && !task.getAssignee().isBlank()
                && !task.getAssignee().equals(memberName)) {
            return failPlanResult(
                    taskId, "Task " + taskId + " is assigned to "
                            + task.getAssignee() + ", not " + memberName);
        }
        if (!"pending".equals(task.getStatus()) && !"claimed".equals(task.getStatus())) {
            return failPlanResult(
                    taskId, "Task " + taskId
                            + " cannot accept a member plan from status '" + task.getStatus() + "'");
        }
        return Map.of();
    }

    /**
     * Persist the plan file, write the plan index, publish the event, and notify the leader.
     *
     * @param taskId task id the plan belongs to
     * @param planId plan id for this submission
     * @param submittedPlanPath source path of the submitted plan file
     * @param toolCallId tool call id for correlation with the leader response
     * @return CompletableFuture with result map containing success, task_id, plan_id, status, member_plan_md
     */
    private CompletableFuture<Map<String, Object>> persistAndPublishPlan(
            String taskId, String planId, Path submittedPlanPath, String toolCallId) {
        Path memberPlanPath = taskPlanPath(taskId, planId);
        Optional<Map<String, Object>> copyError = copyPlanFile(taskId, planId, submittedPlanPath, memberPlanPath);
        if (copyError.isPresent()) {
            return CompletableFuture.completedFuture(copyError.get());
        }
        Map<String, Object> planRecord = new LinkedHashMap<>();
        planRecord.put("task_id", taskId);
        planRecord.put("plan_id", planId);
        planRecord.put("team_plan_id", this.teamPlanId);
        planRecord.put("latest_plan_id", planId);
        planRecord.put("member_name", memberName);
        planRecord.put("status", "claimed");
        planRecord.put("member_plan_md", memberPlanPath.toString());
        planRecord.put("source_plan_path", submittedPlanPath.toString());
        planRecord.put("tool_call_id", toolCallId != null ? toolCallId : "");
        planRecord.put("decision", "pending");
        planRecord.put("submitted_at", nowIso());
        planRecord.put("updated_at", nowIso());
        writeTaskPlanIndex(taskId, planRecord);
        CompletableFuture<Void> publishFuture = publishTaskEvent(
                TeamEvent.TASK_PLAN_REQUEST,
                Map.of(
                        "team_name", teamName,
                        "task_id", taskId,
                        "member_name", memberName,
                        "status", "claimed",
                        "plan_id", planId,
                        "member_plan_md", memberPlanPath.toString(),
                        "tool_call_id", toolCallId != null ? toolCallId : ""));
        return publishFuture.thenCompose(ignored -> {
            Optional<String> leaderMessageId = notifyLeaderOfPlan(planRecord, memberPlanPath);
            if (leaderMessageId.isPresent()) {
                Map<String, Object> update = new LinkedHashMap<>();
                update.put("plan_id", planId);
                update.put("leader_message_id", leaderMessageId.get());
                update.put("updated_at", nowIso());
                writeTaskPlanIndex(taskId, update);
            }
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", true);
            result.put("task_id", taskId);
            result.put("plan_id", planId);
            result.put("status", "claimed");
            result.put("member_plan_md", memberPlanPath.toString());
            result.put("leader_message_id", leaderMessageId.orElse(""));
            result.put("message", "Member plan submitted. Wait for leader approval before execution.");
            return CompletableFuture.completedFuture(result);
        });
    }

    /**
     * Copy the submitted plan file to the team plan directory.
     *
     * @param taskId task id the plan belongs to
     * @param planId plan id for this submission
     * @param submittedPlanPath source path of the submitted plan file
     * @param memberPlanPath target path in the team plan directory
     * @return an error result map if the copy fails, or {@link Optional#empty()} on success
     */
    private Optional<Map<String, Object>> copyPlanFile(
            String taskId, String planId, Path submittedPlanPath, Path memberPlanPath) {
        try {
            Files.createDirectories(memberPlanPath.getParent());
            if (!submittedPlanPath.toAbsolutePath().equals(memberPlanPath.toAbsolutePath())) {
                Files.copy(submittedPlanPath, memberPlanPath, StandardCopyOption.REPLACE_EXISTING);
            }
            return Optional.empty();
        } catch (IOException e) {
            Loggers.TOOL.error("submitPlan: failed to copy plan file for task={} plan={}", taskId, planId, e);
            return Optional.of(failPlanResult(
                    taskId, "Failed to persist plan file: " + e.getMessage(), planId));
        }
    }

    /**
     * Return persisted metadata for one member plan submission.
     *
     * <p>Mirrors Python {@code task_manager.get_plan_record}. Returns an
     * empty map when the plan id is unknown so callers can branch on
     * {@code .isEmpty()}.</p>
     *
     * @param planId plan id to look up
     * @return map with plan metadata, or empty map if plan id is unknown
     */
    public Map<String, Object> getPlanRecord(String planId) {
        return readPlanIndexById(planId);
    }

    // ------------------------------------------------------------------
    // Plan-mode storage helpers (mirror task_manager.py:60-1016)
    // ------------------------------------------------------------------

    private Path taskPlanDir(String taskId) {
        return plansDir.resolve(teamPlanId).resolve("tasks").resolve(safeToken(taskId, "task"));
    }

    private Path taskPlanPath(String taskId, String planId) {
        return taskPlanDir(taskId).resolve("plans").resolve(safeToken(planId, "plan") + ".md");
    }

    private Path planIndexPath() {
        return plansDir.resolve("index.json");
    }

    private Map<String, Object> readPlanIndexJson() {
        Path path = planIndexPath();
        if (!Files.isRegularFile(path)) {
            return new LinkedHashMap<>();
        }
        try {
            byte[] bytes = Files.readAllBytes(path);
            Map<String, Object> loaded = OBJECT_MAPPER.readValue(
                    new String(bytes, StandardCharsets.UTF_8),
                    new TypeReference<Map<String, Object>>() {
                    });
            return loaded != null ? new LinkedHashMap<>(loaded) : new LinkedHashMap<>();
        } catch (IOException e) {
            Loggers.TOOL.warn("Failed to read team plan json {}: {}", path, e.getMessage());
            return new LinkedHashMap<>();
        }
    }

    @SuppressWarnings("unchecked")
    private void writePlanIndexJson(Map<String, Object> index) {
        try {
            Files.createDirectories(planIndexPath().getParent());
            Files.write(planIndexPath(),
                    OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsBytes(index));
        } catch (IOException e) {
            Loggers.TOOL.error("Failed to write team plan json {}: {}", planIndexPath(), e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private void writeTaskPlanIndex(String taskId, Map<String, Object> update) {
        Map<String, Object> index = readPlanIndexJson();
        Object tasksRaw = index.get("tasks");
        Map<String, Object> tasks = tasksRaw instanceof Map ? new LinkedHashMap<>((Map<String, Object>) tasksRaw)
                : new LinkedHashMap<>();
        Object currentRaw = tasks.get(taskId);
        Map<String, Object> current = currentRaw instanceof Map ? new LinkedHashMap<>((Map<String, Object>) currentRaw)
                : new LinkedHashMap<>();
        String planId = stringFromPlan(update, "plan_id");
        if (!planId.isBlank()) {
            Object knownIdsRaw = current.get("plan_ids");
            List<String> knownIds;
            if (knownIdsRaw instanceof List) {
                knownIds = new ArrayList<>((List<String>) knownIdsRaw);
            } else {
                knownIds = new ArrayList<>();
            }
            if (!knownIds.contains(planId)) {
                knownIds.add(planId);
            }
            current.put("plan_ids", knownIds);
        }
        current.putAll(update);
        tasks.put(taskId, current);
        index.put("tasks", tasks);
        Object taskPlansRaw = index.get("task_plans");
        Map<String, Object> taskPlans = taskPlansRaw instanceof Map
                ? new LinkedHashMap<>((Map<String, Object>) taskPlansRaw)
                : new LinkedHashMap<>();
        if (!planId.isBlank()) {
            Object currentPlanRaw = taskPlans.get(planId);
            Map<String, Object> currentPlan = currentPlanRaw instanceof Map
                    ? new LinkedHashMap<>((Map<String, Object>) currentPlanRaw)
                    : new LinkedHashMap<>();
            currentPlan.putAll(update);
            currentPlan.put("task_id", taskId);
            taskPlans.put(planId, currentPlan);
        }
        index.put("team_name", teamName);
        index.put("team_plan_id", teamPlanId);
        index.put("plans_dir", plansDir.toString());
        index.put("updated_at", nowIso());
        index.put("task_plans", taskPlans);
        writePlanIndexJson(index);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readTaskPlanIndex(String taskId) {
        Map<String, Object> index = readPlanIndexJson();
        Object tasksRaw = index.get("tasks");
        if (tasksRaw instanceof Map) {
            Object currentRaw = ((Map<String, Object>) tasksRaw).get(taskId);
            if (currentRaw instanceof Map) {
                return new LinkedHashMap<>((Map<String, Object>) currentRaw);
            }
        }
        return new LinkedHashMap<>();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readPlanIndexById(String planId) {
        Map<String, Object> index = readPlanIndexJson();
        Object taskPlansRaw = index.get("task_plans");
        if (taskPlansRaw instanceof Map) {
            Object currentRaw = ((Map<String, Object>) taskPlansRaw).get(planId);
            if (currentRaw instanceof Map) {
                return new LinkedHashMap<>((Map<String, Object>) currentRaw);
            }
        }
        return new LinkedHashMap<>();
    }

    private Optional<String> notifyLeaderOfPlan(Map<String, Object> planRecord, Path memberPlanPath) {
        String leader = resolveLeaderMemberName();
        if (leader.isBlank() || leader.equals(memberName)) {
            return Optional.empty();
        }
        try {
            TeamMessageManager messageManager =
                    new TeamMessageManager(teamName, memberName, db, messager);
            String content = renderPlanReviewMessage(planRecord, memberPlanPath);
            return Optional.ofNullable(messageManager.sendMessage(content, leader).join());
        } catch (CompletionException e) {
            Loggers.TOOL.warn("submit_plan failed to notify leader {} for task {} plan {}: {}",
                    leader, planRecord.get("task_id"), planRecord.get("plan_id"), e.getMessage());
            return Optional.empty();
        }
    }

    private String resolveLeaderMemberName() {
        if (leaderMemberName != null && !leaderMemberName.isBlank()) {
            return leaderMemberName;
        }
        if (db != null) {
            TeamRecord team = db.team.getTeam(teamName);
            if (team != null && team.getLeaderMemberName() != null) {
                this.leaderMemberName = team.getLeaderMemberName().trim();
                return this.leaderMemberName;
            }
        }
        return "";
    }

    private static String renderPlanReviewMessage(Map<String, Object> planRecord, Path memberPlanPath) {
        List<String> lines = new ArrayList<>();
        lines.add("Member task plan approval request.");
        lines.add("Member: " + planRecord.getOrDefault("member_name", ""));
        lines.add("Task ID: " + planRecord.getOrDefault("task_id", ""));
        lines.add("Plan ID: " + planRecord.getOrDefault("plan_id", ""));
        lines.add("Plan file: " + memberPlanPath);
        Object toolCallId = planRecord.get("tool_call_id");
        if (toolCallId != null && !toolCallId.toString().isBlank()) {
            lines.add("Tool Call ID: " + toolCallId);
        }
        lines.add("");
        lines.add("Please review the plan file and call approve_plan with this plan_id.");
        return String.join("\n", lines);
    }

    private Path resolveSubmittedPlanPath(String planPathStr) {
        String raw = planPathStr == null ? "" : planPathStr.trim();
        if (raw.isEmpty()) {
            throw new IllegalArgumentException("submit_plan requires plan_path");
        }
        Path submitted = Path.of(raw);
        if (!submitted.isAbsolute()) {
            submitted = Path.of(System.getProperty("user.dir")).resolve(submitted);
        }
        submitted = submitted.normalize();
        if (!Files.isRegularFile(submitted)) {
            throw new IllegalArgumentException(
                    "submit_plan plan_path does not exist or is not a file: " + submitted);
        }
        return submitted;
    }

    private static String safeToken(String value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String normalized = SAFE_TOKEN_STRIP.matcher(value.trim()).replaceAll("_");
        normalized = normalized.replaceAll("^[._-]+|[._-]+$", "");
        if (normalized.length() > 96) {
            normalized = normalized.substring(0, 96);
        }
        return normalized.isEmpty() ? fallback : normalized;
    }

    private static String nowIso() {
        return OffsetDateTime.now(ZoneOffset.UTC).toString();
    }

    private static String stringFromPlan(Map<String, Object> map, String key) {
        Object value = map == null ? null : map.get(key);
        return value == null ? "" : value.toString().trim();
    }

    private static Map<String, Object> failPlanResult(String taskId, String message) {
        return failPlanResult(taskId, message, null);
    }

    private static Map<String, Object> failPlanResult(String taskId, String message, String planId) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", false);
        result.put("task_id", taskId);
        if (planId != null && !planId.isBlank()) {
            result.put("plan_id", planId);
        }
        result.put("message", message);
        return result;
    }

    private CompletableFuture<Void> publishTaskEvent(String eventType, Map<String, Object> payload) {
        return messager.publish(taskTopic(),
                EventMessage.builder().eventType(eventType).payload(payload).build());
    }

    /**
     * Look up a single task by id.
     *
     * @param taskId the task id to look up
     * @return an {@link Optional} containing the {@link TeamTask} if found,
     *     or {@link Optional#empty()} when the task does not exist or the database is unavailable
     */
    public Optional<TeamTask> get(String taskId) {
        if (db != null) {
            TaskRecord record = db.task.getTask(taskId);
            if (record == null) {
                Loggers.TOOL.info("TeamTaskManager.get: task {} not found in db={} team={}",
                        taskId, Integer.toHexString(System.identityHashCode(db)), teamName);
                return Optional.empty();
            }
            return Optional.of(TeamTask.builder()
                    .taskId(record.getTaskId())
                    .teamName(record.getTeamName())
                    .title(record.getTitle())
                    .content(record.getContent())
                    .status(record.getStatus())
                    .assignee(record.getAssignee())
                    .updatedAt(record.getUpdatedAt())
                    .dependencies(new ArrayList<>(record.getDependencies()))
                    .build());
        }
        return Optional.empty();
    }

    /**
     * List all tasks for this team.
     *
     * @return list of all TeamTask instances for this team, or empty list if database is unavailable
     */
    public List<TeamTask> list() {
        if (db == null) {
            Loggers.TOOL.debug("TeamTaskManager.list(): db is null for team={}", teamName);
            return List.of();
        }
        List<TeamTask> tasks = db.getTeamTasks(teamName).stream().map(record -> TeamTask.builder()
                .taskId(record.getTaskId())
                .teamName(record.getTeamName())
                .title(record.getTitle())
                .content(record.getContent())
                .status(record.getStatus())
                .assignee(record.getAssignee())
                .updatedAt(record.getUpdatedAt())
                .dependencies(new ArrayList<>(record.getDependencies()))
                .build()).toList();
        Loggers.TOOL.debug("TeamTaskManager.list(): team={} db={} session={} returned {} task(s)",
                teamName, Integer.toHexString(System.identityHashCode(db)),
                teamSessionId,
                tasks.size());
        return tasks;
    }

    /**
     * List tasks assigned to a specific member, optionally filtered by status.
     *
     * @param assignee member name to filter by
     * @param status optional status filter; null matches all statuses
     * @return list of TeamTask instances assigned to the given member with matching status
     */
    public List<TeamTask> getTasksByAssignee(String assignee, String status) {
        if (assignee == null || assignee.isBlank()) {
            return List.of();
        }
        return list().stream()
                .filter(task -> assignee.equals(task.getAssignee()))
                .filter(task -> status == null || status.equals(task.getStatus()))
                .toList();
    }

    /**
     * List pending tasks that can be claimed by a member.
     *
     * @return list of pending TeamTask instances that can be claimed
     */
    public List<TeamTask> getClaimableTasks() {
        return list().stream()
                .filter(task -> "pending".equals(task.getStatus()))
                .toList();
    }

    /**
     * Get the list of task ids that a given task depends on.
     *
     * @param taskId task id to get dependencies for
     * @return list of task ids this task depends on, or empty list if database is unavailable
     */
    public List<String> getDependencies(String taskId) {
        if (db == null) {
            return List.of();
        }
        return db.task.getDependencies(taskId);
    }

    /**
     * List tasks with dependency information.
     * Mirrors Python list_tasks_with_deps(status).
     *
     * @param status optional status filter
     * @return list of task maps with depends_on and depended_by info
     */
    public List<Map<String, Object>> listTasksWithDeps(String status) {
        List<TeamTask> tasks = list();
        List<Map<String, Object>> result = new ArrayList<>();
        for (TeamTask task : tasks) {
            if (status != null && !status.equals(task.getStatus())) {
                continue;
            }
            Map<String, Object> entry = new java.util.LinkedHashMap<>();
            entry.put("task_id", task.getTaskId());
            entry.put("title", task.getTitle());
            entry.put("status", task.getStatus());
            entry.put("assignee", task.getAssignee());
            entry.put("content", task.getContent());

            // depends_on: what this task depends on
            List<String> deps = getDependencies(task.getTaskId());
            entry.put("depends_on", deps);

            // depended_by: tasks that depend on this task
            List<String> dependedBy = db != null
                    ? db.task.getTasksDependingOn(task.getTaskId()).stream()
                    .map(com.openjiuwen.agentteams.tools.database.TaskRecord::getTaskId)
                    .toList()
                    : List.of();
            entry.put("depended_by", dependedBy);
            result.add(entry);
        }
        return result;
    }

    /**
     * Get detailed task information including dependency graph.
     * Mirrors Python get_task_detail(task_id).
     *
     * @param taskId the task ID
     * @return an {@link Optional} containing the detailed task map,
     *     or {@link Optional#empty()} if the task is not found
     */
    public Optional<Map<String, Object>> getTaskDetail(String taskId) {
        Optional<TeamTask> taskOpt = get(taskId);
        if (taskOpt.isEmpty()) {
            return Optional.empty();
        }
        TeamTask task = taskOpt.get();
        Map<String, Object> detail = new java.util.LinkedHashMap<>();
        detail.put("task_id", task.getTaskId());
        detail.put("team_name", task.getTeamName());
        detail.put("title", task.getTitle());
        detail.put("content", task.getContent());
        detail.put("status", task.getStatus());
        detail.put("assignee", task.getAssignee());
        detail.put("updated_at", task.getUpdatedAt());

        // depends_on details
        List<String> deps = getDependencies(taskId);
        List<String> depStatuses = new ArrayList<>();
        for (String depId : deps) {
            Optional<TeamTask> depOpt = get(depId);
            if (depOpt.isPresent()) {
                depStatuses.add(depId + ":" + depOpt.get().getStatus());
            } else {
                depStatuses.add(depId + ":unknown");
            }
        }
        detail.put("depends_on", deps);
        detail.put("depends_on_statuses", depStatuses);

        // tasks that depend on this (blocks)
        List<String> blocks = db != null
                ? db.task.getTasksDependingOn(taskId).stream()
                .map(com.openjiuwen.agentteams.tools.database.TaskRecord::getTaskId)
                .toList()
                : List.of();
        detail.put("blocks", blocks);
        return Optional.of(detail);
    }

    private static String stringValue(Object value) {
        return value != null ? String.valueOf(value) : null;
    }

    private static List<String> stringList(Object value) {
        if (value instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        return List.of();
    }

    /**
     * Check whether member validation should be performed.
     *
     * @return true if the database is available and has registered members, false otherwise
     */
    private boolean shouldValidateMembers() {
        return db != null && !db.member.getTeamMembers(teamName).isEmpty();
    }

    /**
     * Build the TASK topic string, mirroring Python {@code TeamTopic.TASK.build(get_session_id(), team_name)}.
     *
     * @return the constructed topic string
     */
    private String taskTopic() {
        return TeamTopic.TASK.build(teamSessionId, teamName);
    }
}

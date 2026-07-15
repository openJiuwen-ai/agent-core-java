/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentteams.tools;

import com.openjiuwen.agentteams.messager.Messager;
import com.openjiuwen.agentteams.schema.events.EventMessage;
import com.openjiuwen.agentteams.tools.database.MemberRecord;
import com.openjiuwen.agentteams.tools.database.TeamDatabase;
import com.openjiuwen.agentteams.tools.database.TaskMutationResult;
import com.openjiuwen.agentteams.tools.database.TaskRecord;
import com.openjiuwen.core.common.logging.Loggers;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Public class TeamTaskManager used by the Java parity implementation.
 * 
 * @since 0.1.7
 */
public class TeamTaskManager {
    private final String teamName;
    private final String memberName;
    private final TeamDatabase db;
    private final Messager messager;

    /**
     * TeamTaskManager.
     * 
     * @param teamName teamName
     * @param memberName memberName
     * @param messager messager
     * @since 0.1.7
     */
    public TeamTaskManager(String teamName, String memberName, Messager messager) {
        this(teamName, memberName, null, messager);
    }

    /**
     * TeamTaskManager.
     * 
     * @param teamName teamName
     * @param memberName memberName
     * @param db db
     * @param messager messager
     * @since 0.1.7
     */
    public TeamTaskManager(String teamName, String memberName, TeamDatabase db, Messager messager) {
        this.teamName = teamName;
        this.memberName = memberName;
        this.db = db;
        this.messager = messager;
    }

    /**
     * add.
     * 
     * @param title title
     * @param content content
     * @return the result
     * @since 0.1.7
     */
    public CompletableFuture<TeamTask> add(String title, String content) {
        return add(title, content, null, List.of());
    }

    /**
     * add.
     * 
     * @param title title
     * @param content content
     * @param taskId taskId
     * @param dependencies dependencies
     * @return the result
     * @since 0.1.7
     */
    public CompletableFuture<TeamTask> add(String title, String content, String taskId, List<String> dependencies) {
        return add(title, content, taskId, dependencies, null);
    }

    /**
     * Create a task with an optional assignee.
     * 
     * @param title title
     * @param content content
     * @param taskId taskId
     * @param dependencies dependencies
     * @param assignee assignee
     * @return the result
     * @since 0.1.7
     */
    public CompletableFuture<TeamTask> add(String title, String content, String taskId, List<String> dependencies,
            String assignee) {
        String resolvedTaskId = taskId != null ? taskId : UUID.randomUUID().toString();
        TeamTask task = TeamTask.builder().taskId(resolvedTaskId).teamName(teamName).title(title).content(content)
                .status(dependencies != null && !dependencies.isEmpty() ? "blocked" : "pending")
                .dependencies(new ArrayList<>(dependencies != null ? dependencies : List.of())).assignee(assignee)
                .build();
        if (db != null) {
            Loggers.TOOL.info("TeamTaskManager.add: creating task {} team={} db={} session={}", resolvedTaskId,
                    teamName, Integer.toHexString(System.identityHashCode(db)),
                    com.openjiuwen.agentteams.spawn.SpawnContext.getSessionId());
            db.task.createTask(resolvedTaskId, teamName, title, content, task.getStatus());
            if (dependencies != null) {
                for (String dependency : dependencies) {
                    db.task.addDependency(resolvedTaskId, dependency);
                }
            }
            TeamTask reloaded = get(resolvedTaskId);
            if (reloaded != null) {
                task.setStatus(reloaded.getStatus());
            }
        }
        return messager
                .publish("team:task",
                        EventMessage.builder().eventType("task_created")
                                .payload(java.util.Map.of("task_id", resolvedTaskId)).build())
                .thenApply(ignored -> task);
    }

    /**
     * addBatch.
     * 
     * @param tasks tasks
     * @return the result
     * @since 0.1.7
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
                if (assignee != null && assignee.isBlank()) {
                    assignee = null;
                }
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
     * addWithPriority.
     * 
     * @param title title
     * @param content content
     * @param taskId taskId
     * @param dependsOn dependsOn
     * @param dependedBy dependedBy
     * @return the result
     * @since 0.1.7
     */
    public CompletableFuture<TeamTask> addWithPriority(String title, String content, String taskId,
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
     * addAsTopPriority.
     * 
     * @param title title
     * @param content content
     * @return the result
     * @since 0.1.7
     */
    public CompletableFuture<TeamTask> addAsTopPriority(String title, String content) {
        return addAsTopPriority(title, content, null);
    }

    /**
     * addAsTopPriority.
     * 
     * @param title title
     * @param content content
     * @param taskId taskId
     * @return the result
     * @since 0.1.7
     */
    public CompletableFuture<TeamTask> addAsTopPriority(String title, String content, String taskId) {
        List<String> pendingTaskIds = getClaimableTasks().stream().map(TeamTask::getTaskId).toList();
        return add(title, content, taskId, List.of()).thenCompose(topTask -> {
            if (topTask == null) {
                return CompletableFuture.completedFuture(null);
            }
            CompletableFuture<Void> chain = CompletableFuture.completedFuture(null);
            for (String pendingTaskId : pendingTaskIds) {
                chain = chain.thenCompose(ignored -> addDependencies(pendingTaskId, List.of(topTask.getTaskId()))
                        .thenApply(none -> null));
            }
            return chain.thenApply(ignored -> get(topTask.getTaskId()));
        });
    }

    /**
     * claim.
     * 
     * @param taskId taskId
     * @return the result
     * @since 0.1.7
     */
    public CompletableFuture<Boolean> claim(String taskId) {
        return claimResult(taskId).thenApply(TaskOpResult::isOk);
    }

    /**
     * claimResult.
     * 
     * @param taskId taskId
     * @return the result
     * @since 0.1.7
     */
    public CompletableFuture<TaskOpResult> claimResult(String taskId) {
        TeamTask task = get(taskId);
        if (task == null) {
            Loggers.TOOL.info("claimResult: task {} not found, db={} member={} team={}", taskId,
                    db != null ? Integer.toHexString(System.identityHashCode(db)) : "null", memberName, teamName);
            return CompletableFuture.completedFuture(TaskOpResult.fail("Task " + taskId + " not found"));
        }
        if (db != null && shouldValidateMembers() && db.member.getMember(memberName, teamName) == null) {
            return CompletableFuture
                    .completedFuture(TaskOpResult.fail("Member " + memberName + " not found in team " + teamName));
        }
        if (memberName.equals(task.getAssignee()) && "claimed".equals(task.getStatus())) {
            return CompletableFuture.completedFuture(TaskOpResult.success());
        }
        if (task.getAssignee() != null) {
            return CompletableFuture.completedFuture(TaskOpResult.fail("Task " + taskId + " is already claimed by "
                    + task.getAssignee() + ", " + memberName + " cannot claim it"));
        }
        if (!"pending".equals(task.getStatus())) {
            return CompletableFuture.completedFuture(TaskOpResult.fail("Task " + taskId
                    + " cannot be claimed from status '" + task.getStatus() + "' (only pending tasks are claimable)"));
        }
        // Prevent task hogging: a member can only hold one claimed task at a time.
        // They must complete their current task before claiming another.
        long myClaimedCount = getTasksByAssignee(memberName, "claimed").size();
        if (myClaimedCount > 0) {
            return CompletableFuture.completedFuture(TaskOpResult.fail("Member " + memberName
                    + " already has an active claimed task." + " Complete it before claiming another."));
        }
        task.setStatus("claimed");
        task.setAssignee(memberName);
        if (db != null && !db.task.claimTask(taskId, memberName)) {
            return CompletableFuture.completedFuture(TaskOpResult
                    .fail("Database rejected claim for task " + taskId + " (likely a concurrent claim race)"));
        }
        return messager
                .publish("team:task", EventMessage.builder().eventType("task_claimed")
                        .payload(java.util.Map.of("task_id", taskId)).build())
                .thenApply(ignored -> TaskOpResult.success());
    }

    /**
     * complete.
     * 
     * @param taskId taskId
     * @return the result
     * @since 0.1.7
     */
    public CompletableFuture<Boolean> complete(String taskId) {
        return completeResult(taskId).thenApply(TaskOpResult::isOk);
    }

    /**
     * completeResult.
     * 
     * @param taskId taskId
     * @return the result
     * @since 0.1.7
     */
    public CompletableFuture<TaskOpResult> completeResult(String taskId) {
        TeamTask task = get(taskId);
        if (task == null) {
            return CompletableFuture.completedFuture(TaskOpResult.fail("Task " + taskId + " not found"));
        }
        if (db != null) {
            MemberRecord member = db.member.getMember(memberName, teamName);
            if (member == null && shouldValidateMembers()) {
                return CompletableFuture
                        .completedFuture(TaskOpResult.fail("Member " + memberName + " not found in team " + teamName));
            }
            if (member != null && "plan_mode".equals(member.getMode()) && !"plan_approved".equals(task.getStatus())) {
                return CompletableFuture.completedFuture(TaskOpResult.fail("PLAN_MODE member cannot complete task "
                        + taskId + " in status '" + task.getStatus() + "'; only plan_approved tasks can be completed"));
            }
        }
        if (!"claimed".equals(task.getStatus()) && !"plan_approved".equals(task.getStatus())) {
            return CompletableFuture
                    .completedFuture(TaskOpResult.fail("Task " + taskId + " cannot be completed from status '"
                            + task.getStatus() + "' (must be claimed or plan_approved)"));
        }
        // Only the member who owns the task can complete it
        if (task.getAssignee() != null && !memberName.equals(task.getAssignee())) {
            return CompletableFuture.completedFuture(TaskOpResult.fail("Task " + taskId + " is owned by "
                    + task.getAssignee() + ", " + memberName + " cannot complete it"));
        }
        TaskMutationResult mutation = db != null ? db.task.completeTaskResult(taskId) : null;
        if (db != null && mutation == null) {
            TeamTask current = get(taskId);
            String status = current != null ? current.getStatus() : task.getStatus();
            return CompletableFuture.completedFuture(TaskOpResult.fail("Task " + taskId
                    + " cannot be completed from status '" + status + "' (must be claimed or plan_approved)"));
        }
        task.setStatus("completed");
        return messager
                .publish("team:task",
                        EventMessage.builder().eventType("task_completed").payload(java.util.Map.of("task_id", taskId))
                                .build())
                .thenCompose(ignored -> publishUnblockedEvents(mutation)).thenApply(ignored -> TaskOpResult.success());
    }

    /**
     * cancel.
     * 
     * @param taskId taskId
     * @return the result
     * @since 0.1.7
     */
    public CompletableFuture<TeamTask> cancel(String taskId) {
        TeamTask task = get(taskId);
        if (task == null) {
            return CompletableFuture.completedFuture(null);
        }
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
        return messager
                .publish("team:task",
                        EventMessage.builder().eventType("task_cancelled").payload(java.util.Map.of("task_id", taskId))
                                .build())
                .thenCompose(ignored -> publishUnblockedEvents(mutation)).thenApply(ignored -> get(taskId));
    }

    /**
     * cancelAllTasks.
     * 
     * @return the result
     * @since 0.1.7
     */
    public CompletableFuture<List<TeamTask>> cancelAllTasks() {
        return cancelAllTasks(List.of());
    }

    /**
     * Cancel all tasks, optionally skipping tasks assigned to specific members.
     * Mirrors Python cancel_all_tasks(skip_assignees).
     * 
     * @param skipAssignees member names whose claimed tasks should NOT be cancelled
     * @return the result
     * @since 0.1.7
     */
    public CompletableFuture<List<TeamTask>> cancelAllTasks(List<String> skipAssignees) {
        if (db == null) {
            return CompletableFuture.completedFuture(List.of());
        }
        TaskMutationResult mutation = db.task.cancelAllTasksResult(teamName);
        List<TeamTask> cancelled = mutation.getCancelledTasks().stream().map(record -> get(record.getTaskId()))
                .filter(task -> task != null).filter(task -> skipAssignees == null || skipAssignees.isEmpty()
                        || !skipAssignees.contains(task.getAssignee()))
                .toList();
        CompletableFuture<Void> published = CompletableFuture.completedFuture(null);
        for (TeamTask task : cancelled) {
            published = published.thenCompose(ignored -> messager
                    .publish("team:task",
                            EventMessage.builder().eventType("task_cancelled")
                                    .payload(java.util.Map.of("task_id", task.getTaskId())).build())
                    .thenApply(none -> null));
        }
        return published.thenCompose(ignored -> publishUnblockedEvents(mutation)).thenApply(ignored -> cancelled);
    }

    /**
     * publishUnblockedEvents.
     * 
     * @param mutation mutation
     * @return the result
     * @since 0.1.7
     */
    private CompletableFuture<Void> publishUnblockedEvents(TaskMutationResult mutation) {
        if (mutation == null || mutation.getUnblockedTasks() == null || mutation.getUnblockedTasks().isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }
        CompletableFuture<Void> published = CompletableFuture.completedFuture(null);
        for (TaskRecord task : mutation.getUnblockedTasks()) {
            published = published.thenCompose(ignored -> messager
                    .publish("team:task",
                            EventMessage.builder().eventType("task_unblocked")
                                    .payload(java.util.Map.of("task_id", task.getTaskId())).build())
                    .thenApply(none -> null));
        }
        return published;
    }

    /**
     * reset.
     * 
     * @param taskId taskId
     * @return the result
     * @since 0.1.7
     */
    public CompletableFuture<Boolean> reset(String taskId) {
        return resetResult(taskId).thenApply(TaskOpResult::isOk);
    }

    /**
     * resetResult.
     * 
     * @param taskId taskId
     * @return the result
     * @since 0.1.7
     */
    public CompletableFuture<TaskOpResult> resetResult(String taskId) {
        TeamTask existing = get(taskId);
        if (existing == null) {
            return CompletableFuture.completedFuture(TaskOpResult.fail("Task " + taskId + " not found"));
        }
        if (db == null || !db.task.resetTask(taskId)) {
            return CompletableFuture.completedFuture(TaskOpResult.fail("Task " + taskId
                    + " cannot be reset from status '" + existing.getStatus() + "'; only claimed tasks can be reset"));
        }
        return messager
                .publish("team:task", EventMessage.builder().eventType("task_updated")
                        .payload(java.util.Map.of("task_id", taskId)).build())
                .thenApply(ignored -> TaskOpResult.success());
    }

    /**
     * assign.
     * 
     * @param taskId taskId
     * @param assignee assignee
     * @return the result
     * @since 0.1.7
     */
    public CompletableFuture<Boolean> assign(String taskId, String assignee) {
        return assignResult(taskId, assignee).thenApply(TaskOpResult::isOk);
    }

    /**
     * assignResult.
     * 
     * @param taskId taskId
     * @param assignee assignee
     * @return the result
     * @since 0.1.7
     */
    public CompletableFuture<TaskOpResult> assignResult(String taskId, String assignee) {
        TeamTask task = get(taskId);
        if (task == null) {
            return CompletableFuture.completedFuture(TaskOpResult.fail("Task " + taskId + " not found"));
        }
        if (db == null || assignee == null || assignee.isBlank()) {
            return CompletableFuture
                    .completedFuture(TaskOpResult.fail("Member " + assignee + " not found in team " + teamName));
        }
        if (db.member.getMember(assignee, teamName) == null) {
            return CompletableFuture
                    .completedFuture(TaskOpResult.fail("Member " + assignee + " not found in team " + teamName));
        }
        if (assignee.equals(task.getAssignee()) && "claimed".equals(task.getStatus())) {
            return CompletableFuture.completedFuture(TaskOpResult.success());
        }
        if (task.getAssignee() != null && !assignee.equals(task.getAssignee())) {
            return CompletableFuture.completedFuture(TaskOpResult.fail("Task " + taskId + " is already claimed by "
                    + task.getAssignee() + "; reset the task before reassigning to " + assignee));
        }
        if (!db.task.assignTask(taskId, assignee)) {
            return CompletableFuture.completedFuture(TaskOpResult.fail("Database rejected assign for task " + taskId
                    + " (invalid state transition from " + task.getStatus() + ")"));
        }
        return messager
                .publish("team:task",
                        EventMessage.builder().eventType("task_claimed")
                                .payload(java.util.Map.of("task_id", taskId, "member_name", assignee)).build())
                .thenApply(ignored -> TaskOpResult.success());
    }

    /**
     * addDependencies.
     * 
     * @param taskId taskId
     * @param dependsOnIds dependsOnIds
     * @return the result
     * @since 0.1.7
     */
    public CompletableFuture<Boolean> addDependencies(String taskId, List<String> dependsOnIds) {
        return addDependenciesResult(taskId, dependsOnIds).thenApply(TaskOpResult::isOk);
    }

    /**
     * addDependenciesResult.
     * 
     * @param taskId taskId
     * @param dependsOnIds dependsOnIds
     * @return the result
     * @since 0.1.7
     */
    public CompletableFuture<TaskOpResult> addDependenciesResult(String taskId, List<String> dependsOnIds) {
        if (dependsOnIds == null || dependsOnIds.isEmpty()) {
            return CompletableFuture.completedFuture(TaskOpResult.success());
        }
        if (db == null || get(taskId) == null) {
            return CompletableFuture.completedFuture(TaskOpResult.fail("Task " + taskId + " not found"));
        }
        List<List<String>> edges = dependsOnIds.stream().map(dependsOnId -> List.of(taskId, dependsOnId)).toList();
        com.openjiuwen.agentteams.tools.database.GraphMutationResult mutation =
            db.task.mutateDependencyGraph(teamName, edges);
        if (!mutation.isOk()) {
            return CompletableFuture.completedFuture(TaskOpResult.fail(mutation.getReason()));
        }
        return messager
                .publish("team:task", EventMessage.builder().eventType("task_updated")
                        .payload(java.util.Map.of("task_id", taskId)).build())
                .thenApply(ignored -> TaskOpResult.success());
    }

    /**
     * updateTask.
     * 
     * @param taskId taskId
     * @param title title
     * @param content content
     * @return the result
     * @since 0.1.7
     */
    public CompletableFuture<Boolean> updateTask(String taskId, String title, String content) {
        return updateTaskResult(taskId, title, content).thenApply(TaskOpResult::isOk);
    }

    /**
     * updateTaskResult.
     * 
     * @param taskId taskId
     * @param title title
     * @param content content
     * @return the result
     * @since 0.1.7
     */
    public CompletableFuture<TaskOpResult> updateTaskResult(String taskId, String title, String content) {
        TeamTask task = get(taskId);
        if (task == null) {
            return CompletableFuture.completedFuture(TaskOpResult.fail("Task " + taskId + " not found"));
        }
        if (db == null || !db.task.updateTask(taskId, title, content)) {
            return CompletableFuture
                    .completedFuture(TaskOpResult.fail("Task " + taskId + " cannot be edited while in status '"
                            + task.getStatus() + "'; content updates are only allowed on pending / blocked tasks"));
        }
        return messager
                .publish("team:task", EventMessage.builder().eventType("task_updated")
                        .payload(java.util.Map.of("task_id", taskId)).build())
                .thenApply(ignored -> TaskOpResult.success());
    }

    /**
     * approvePlan.
     * 
     * @param taskId taskId
     * @return the result
     * @since 0.1.7
     */
    public CompletableFuture<Boolean> approvePlan(String taskId) {
        return approvePlanResult(taskId).thenApply(TaskOpResult::isOk);
    }

    /**
     * approvePlanResult.
     * 
     * @param taskId taskId
     * @return the result
     * @since 0.1.7
     */
    public CompletableFuture<TaskOpResult> approvePlanResult(String taskId) {
        TeamTask task = get(taskId);
        if (task == null) {
            return CompletableFuture.completedFuture(TaskOpResult.fail("Task " + taskId + " not found"));
        }
        if (db == null || !db.task.approvePlanTask(taskId)) {
            return CompletableFuture
                    .completedFuture(TaskOpResult.fail("Task " + taskId + " cannot be plan-approved from status '"
                            + task.getStatus() + "'; only claimed tasks can be approved"));
        }
        return messager
                .publish("team:task", EventMessage.builder().eventType("task_updated")
                        .payload(java.util.Map.of("task_id", taskId)).build())
                .thenApply(ignored -> TaskOpResult.success());
    }

    /**
     * get.
     * 
     * @param taskId taskId
     * @return the result
     * @since 0.1.7
     */
    public TeamTask get(String taskId) {
        if (db != null) {
            TaskRecord record = db.task.getTask(taskId);
            if (record == null) {
                Loggers.TOOL.info("TeamTaskManager.get: task {} not found in db={} team={}", taskId,
                        Integer.toHexString(System.identityHashCode(db)), teamName);
                return null;
            }
            return TeamTask.builder().taskId(record.getTaskId()).teamName(record.getTeamName()).title(record.getTitle())
                    .content(record.getContent()).status(record.getStatus()).assignee(record.getAssignee())
                    .updatedAt(record.getUpdatedAt()).dependencies(new ArrayList<>(record.getDependencies())).build();
        }
        return null;
    }

    /**
     * list.
     * 
     * @return the result
     * @since 0.1.7
     */
    public List<TeamTask> list() {
        if (db == null) {
            Loggers.TOOL.debug("TeamTaskManager.list(): db is null for team={}", teamName);
            return List.of();
        }
        List<TeamTask> tasks = db.getTeamTasks(teamName).stream()
                .map(record -> TeamTask.builder().taskId(record.getTaskId()).teamName(record.getTeamName())
                        .title(record.getTitle()).content(record.getContent()).status(record.getStatus())
                        .assignee(record.getAssignee()).updatedAt(record.getUpdatedAt())
                        .dependencies(new ArrayList<>(record.getDependencies())).build())
                .toList();
        Loggers.TOOL.debug("TeamTaskManager.list(): team={} db={} session={} returned {} task(s)", teamName,
                Integer.toHexString(System.identityHashCode(db)),
                com.openjiuwen.agentteams.spawn.SpawnContext.getSessionId(), tasks.size());
        return tasks;
    }

    /**
     * getTasksByAssignee.
     * 
     * @param assignee assignee
     * @param status status
     * @return the result
     * @since 0.1.7
     */
    public List<TeamTask> getTasksByAssignee(String assignee, String status) {
        if (assignee == null || assignee.isBlank()) {
            return List.of();
        }
        return list().stream().filter(task -> assignee.equals(task.getAssignee()))
                .filter(task -> status == null || status.equals(task.getStatus())).toList();
    }

    /**
     * getClaimableTasks.
     * 
     * @return the result
     * @since 0.1.7
     */
    public List<TeamTask> getClaimableTasks() {
        return list().stream().filter(task -> "pending".equals(task.getStatus())).toList();
    }

    /**
     * getDependencies.
     * 
     * @param taskId taskId
     * @return the result
     * @since 0.1.7
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
     * @since 0.1.7
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
                            .map(com.openjiuwen.agentteams.tools.database.TaskRecord::getTaskId).toList()
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
     * @return detailed task map or null if not found
     * @since 0.1.7
     */
    public Map<String, Object> getTaskDetail(String taskId) {
        TeamTask task = get(taskId);
        if (task == null) {
            return null;
        }
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
            TeamTask dep = get(depId);
            if (dep != null) {
                depStatuses.add(depId + ":" + dep.getStatus());
            } else {
                depStatuses.add(depId + ":unknown");
            }
        }
        detail.put("depends_on", deps);
        detail.put("depends_on_statuses", depStatuses);
        // tasks that depend on this (blocks)
        List<String> blocks = db != null
                ? db.task.getTasksDependingOn(taskId).stream()
                        .map(com.openjiuwen.agentteams.tools.database.TaskRecord::getTaskId).toList()
                : List.of();
        detail.put("blocks", blocks);
        return detail;
    }

    /**
     * stringValue.
     * 
     * @param value value
     * @return the result
     * @since 0.1.7
     */
    private static String stringValue(Object value) {
        return value != null ? String.valueOf(value) : null;
    }

    /**
     * stringList.
     * 
     * @param value value
     * @return the result
     * @since 0.1.7
     */
    private static List<String> stringList(Object value) {
        if (value instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        return List.of();
    }

    /**
     * shouldValidateMembers.
     * 
     * @return the result
     * @since 0.1.7
     */
    private boolean shouldValidateMembers() {
        return db != null && !db.member.getTeamMembers(teamName).isEmpty();
    }
}

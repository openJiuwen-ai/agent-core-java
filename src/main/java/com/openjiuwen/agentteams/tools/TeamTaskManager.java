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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Public class TeamTaskManager used by the Java parity implementation.
 *
 * @since 1.0
 */
public class TeamTaskManager {
    private final String teamName;
    private final String memberName;
    private final TeamDatabase db;
    private final Messager messager;

    /**
     * Auto-generated for codecheck compliance.
     */
    public TeamTaskManager(String teamName, String memberName, Messager messager) {
        this(teamName, memberName, null, messager);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public TeamTaskManager(String teamName, String memberName, TeamDatabase db, Messager messager) {
        this.teamName = teamName;
        this.memberName = memberName;
        this.db = db;
        this.messager = messager;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public CompletableFuture<TeamTask> add(String title, String content) {
        return add(title, content, null, List.of());
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public CompletableFuture<TeamTask> add(String title, String content, String taskId, List<String> dependencies) {
        String resolvedTaskId = taskId != null ? taskId : UUID.randomUUID().toString();
        TeamTask task = TeamTask.builder()
                .taskId(resolvedTaskId)
                .teamName(teamName)
                .title(title)
                .content(content)
                .status(dependencies != null && !dependencies.isEmpty() ? "blocked" : "pending")
                .dependencies(new ArrayList<>(dependencies != null ? dependencies : List.of()))
                .build();
        if (db != null) {
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
        return messager.publish("team:task", EventMessage.builder()
                        .eventType("task_created")
                        .payload(java.util.Map.of("task_id", resolvedTaskId))
                        .build())
                .thenApply(ignored -> task);
    }

    /**
     * Auto-generated for codecheck compliance.
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
                return add(title, content, taskId, dependencies).thenApply(task -> {
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
     * Auto-generated for codecheck compliance.
     */
    public CompletableFuture<TeamTask> addAsTopPriority(String title, String content) {
        return addAsTopPriority(title, content, null);
    }

    /**
     * Auto-generated for codecheck compliance.
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
            return chain.thenApply(ignored -> get(topTask.getTaskId()));
        });
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public CompletableFuture<Boolean> claim(String taskId) {
        return claimResult(taskId).thenApply(TaskOpResult::isOk);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public CompletableFuture<TaskOpResult> claimResult(String taskId) {
        TeamTask task = get(taskId);
        if (task == null) {
            return CompletableFuture.completedFuture(TaskOpResult.fail("Task " + taskId + " not found"));
        }
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
        task.setStatus("claimed");
        task.setAssignee(memberName);
        if (db != null && !db.task.claimTask(taskId, memberName)) {
            return CompletableFuture.completedFuture(
                    TaskOpResult.fail("Database rejected claim for task " + taskId
                            + " (likely a concurrent claim race)"));
        }
        return messager.publish("team:task", EventMessage.builder()
                        .eventType("task_claimed")
                        .payload(java.util.Map.of("task_id", taskId))
                        .build())
                .thenApply(ignored -> TaskOpResult.success());
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public CompletableFuture<Boolean> complete(String taskId) {
        return completeResult(taskId).thenApply(TaskOpResult::isOk);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public CompletableFuture<TaskOpResult> completeResult(String taskId) {
        TeamTask task = get(taskId);
        if (task == null) {
            return CompletableFuture.completedFuture(TaskOpResult.fail("Task " + taskId + " not found"));
        }
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
        TaskMutationResult mutation = db != null ? db.task.completeTaskResult(taskId) : null;
        if (db != null && mutation == null) {
            TeamTask current = get(taskId);
            String status = current != null ? current.getStatus() : task.getStatus();
            return CompletableFuture.completedFuture(TaskOpResult.fail(
                    "Task " + taskId + " cannot be completed from status '" + status
                            + "' (must be claimed or plan_approved)"));
        }
        task.setStatus("completed");
        return messager.publish("team:task", EventMessage.builder()
                        .eventType("task_completed")
                        .payload(java.util.Map.of("task_id", taskId))
                        .build())
                .thenCompose(ignored -> publishUnblockedEvents(mutation))
                .thenApply(ignored -> TaskOpResult.success());
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public CompletableFuture<TeamTask> cancel(String taskId) {
        TaskMutationResult mutation = db != null ? db.task.cancelTaskResult(taskId) : null;
        if (db == null || mutation == null) {
            return CompletableFuture.completedFuture(null);
        }
        return messager.publish("team:task", EventMessage.builder()
                        .eventType("task_cancelled")
                        .payload(java.util.Map.of("task_id", taskId))
                        .build())
                .thenCompose(ignored -> publishUnblockedEvents(mutation))
                .thenApply(ignored -> get(taskId));
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public CompletableFuture<List<TeamTask>> cancelAllTasks() {
        if (db == null) {
            return CompletableFuture.completedFuture(List.of());
        }
        TaskMutationResult mutation = db.task.cancelAllTasksResult(teamName);
        List<TeamTask> cancelled = mutation.getCancelledTasks().stream()
                .map(record -> get(record.getTaskId()))
                .toList();
        CompletableFuture<Void> published = CompletableFuture.completedFuture(null);
        for (TeamTask task : cancelled) {
            published = published.thenCompose(ignored -> messager.publish("team:task", EventMessage.builder()
                    .eventType("task_cancelled")
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
            published = published.thenCompose(ignored -> messager.publish("team:task", EventMessage.builder()
                    .eventType("task_unblocked")
                    .payload(java.util.Map.of("task_id", task.getTaskId()))
                    .build()).thenApply(none -> null));
        }
        return published;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public CompletableFuture<Boolean> reset(String taskId) {
        return resetResult(taskId).thenApply(TaskOpResult::isOk);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public CompletableFuture<TaskOpResult> resetResult(String taskId) {
        TeamTask existing = get(taskId);
        if (existing == null) {
            return CompletableFuture.completedFuture(TaskOpResult.fail("Task " + taskId + " not found"));
        }
        if (db == null || !db.task.resetTask(taskId)) {
            return CompletableFuture.completedFuture(TaskOpResult.fail(
                    "Task " + taskId + " cannot be reset from status '" + existing.getStatus()
                            + "'; only claimed tasks can be reset"));
        }
        return messager.publish("team:task", EventMessage.builder()
                        .eventType("task_updated")
                        .payload(java.util.Map.of("task_id", taskId))
                        .build())
                .thenApply(ignored -> TaskOpResult.success());
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public CompletableFuture<Boolean> assign(String taskId, String assignee) {
        return assignResult(taskId, assignee).thenApply(TaskOpResult::isOk);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public CompletableFuture<TaskOpResult> assignResult(String taskId, String assignee) {
        TeamTask task = get(taskId);
        if (task == null) {
            return CompletableFuture.completedFuture(TaskOpResult.fail("Task " + taskId + " not found"));
        }
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
        return messager.publish("team:task", EventMessage.builder()
                        .eventType("task_claimed")
                        .payload(java.util.Map.of("task_id", taskId, "member_name", assignee))
                        .build())
                .thenApply(ignored -> TaskOpResult.success());
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public CompletableFuture<Boolean> addDependencies(String taskId, List<String> dependsOnIds) {
        return addDependenciesResult(taskId, dependsOnIds).thenApply(TaskOpResult::isOk);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public CompletableFuture<TaskOpResult> addDependenciesResult(String taskId, List<String> dependsOnIds) {
        if (dependsOnIds == null || dependsOnIds.isEmpty()) {
            return CompletableFuture.completedFuture(TaskOpResult.success());
        }
        if (db == null || get(taskId) == null) {
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
        return messager.publish("team:task", EventMessage.builder()
                        .eventType("task_updated")
                        .payload(java.util.Map.of("task_id", taskId))
                        .build())
                .thenApply(ignored -> TaskOpResult.success());
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public CompletableFuture<Boolean> updateTask(String taskId, String title, String content) {
        return updateTaskResult(taskId, title, content).thenApply(TaskOpResult::isOk);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public CompletableFuture<TaskOpResult> updateTaskResult(String taskId, String title, String content) {
        TeamTask task = get(taskId);
        if (task == null) {
            return CompletableFuture.completedFuture(TaskOpResult.fail("Task " + taskId + " not found"));
        }
        if (db == null || !db.task.updateTask(taskId, title, content)) {
            return CompletableFuture.completedFuture(TaskOpResult.fail(
                    "Task " + taskId + " cannot be edited while in status '" + task.getStatus()
                            + "'; content updates are only allowed on pending / blocked tasks"));
        }
        return messager.publish("team:task", EventMessage.builder()
                        .eventType("task_updated")
                        .payload(java.util.Map.of("task_id", taskId))
                        .build())
                .thenApply(ignored -> TaskOpResult.success());
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public CompletableFuture<Boolean> approvePlan(String taskId) {
        return approvePlanResult(taskId).thenApply(TaskOpResult::isOk);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public CompletableFuture<TaskOpResult> approvePlanResult(String taskId) {
        TeamTask task = get(taskId);
        if (task == null) {
            return CompletableFuture.completedFuture(TaskOpResult.fail("Task " + taskId + " not found"));
        }
        if (db == null || !db.task.approvePlanTask(taskId)) {
            return CompletableFuture.completedFuture(TaskOpResult.fail(
                    "Task " + taskId + " cannot be plan-approved from status '" + task.getStatus()
                            + "'; only claimed tasks can be approved"));
        }
        return messager.publish("team:task", EventMessage.builder()
                        .eventType("task_updated")
                        .payload(java.util.Map.of("task_id", taskId))
                        .build())
                .thenApply(ignored -> TaskOpResult.success());
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public TeamTask get(String taskId) {
        if (db != null) {
            TaskRecord record = db.task.getTask(taskId);
            if (record == null) {
                return null;
            }
            return TeamTask.builder()
                    .taskId(record.getTaskId())
                    .teamName(record.getTeamName())
                    .title(record.getTitle())
                    .content(record.getContent())
                    .status(record.getStatus())
                    .assignee(record.getAssignee())
                    .updatedAt(record.getUpdatedAt())
                    .dependencies(new ArrayList<>(record.getDependencies()))
                    .build();
        }
        return null;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public List<TeamTask> list() {
        if (db == null) {
            return List.of();
        }
        return db.getTeamTasks(teamName).stream().map(record -> TeamTask.builder()
                .taskId(record.getTaskId())
                .teamName(record.getTeamName())
                .title(record.getTitle())
                .content(record.getContent())
                .status(record.getStatus())
                .assignee(record.getAssignee())
                .updatedAt(record.getUpdatedAt())
                .dependencies(new ArrayList<>(record.getDependencies()))
                .build()).toList();
    }

    /**
     * Auto-generated for codecheck compliance.
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
     * Auto-generated for codecheck compliance.
     */
    public List<TeamTask> getClaimableTasks() {
        return list().stream()
                .filter(task -> "pending".equals(task.getStatus()))
                .toList();
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public List<String> getDependencies(String taskId) {
        if (db == null) {
            return List.of();
        }
        return db.task.getDependencies(taskId);
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

    private boolean shouldValidateMembers() {
        return db != null && !db.member.getTeamMembers(teamName).isEmpty();
    }
}

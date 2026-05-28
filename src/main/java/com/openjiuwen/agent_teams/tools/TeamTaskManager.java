/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.tools;

import com.openjiuwen.agent_teams.schema.task.TaskDetail;
import com.openjiuwen.agent_teams.schema.task.TaskRecord;
import com.openjiuwen.agent_teams.schema.task.TaskStatus;
import com.openjiuwen.agent_teams.schema.task.TaskSummary;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * Team task manager for managing tasks within a team.
 * <p>
 * Provides methods to add, claim, complete, and manage tasks.
 * Supports both synchronous and asynchronous operations.
 * <p>
 * Mirrors Python's {@code TeamTaskManager} in
 * {@code openjiuwen.agent_teams.tools.task_manager}.
 */
public class TeamTaskManager {

    private static final Logger logger = Logger.getLogger(TeamTaskManager.class.getName());

    private final String teamName;
    private final String memberName;
    private final Map<String, TaskRecord> tasks;
    private Consumer<TaskEvent> eventPublisher;

    public TeamTaskManager(String teamName, String memberName) {
        this(teamName, memberName, new LinkedHashMap<>());
    }

    public TeamTaskManager(String teamName, String memberName, Map<String, TaskRecord> tasks) {
        this.teamName = teamName;
        this.memberName = memberName;
        this.tasks = tasks != null ? tasks : new LinkedHashMap<>();
    }

    /**
     * Set an event publisher callback for task events.
     * This replaces the Python Messager.publish mechanism.
     *
     * @param publisher Callback to receive task events
     */
    public void setEventPublisher(Consumer<TaskEvent> publisher) {
        this.eventPublisher = publisher;
    }

    // ── Add Methods ───────────────────────────────────────

    /**
     * Add a task to the team (synchronous).
     *
     * @param title       Task title
     * @param content     Task content
     * @param taskId      Optional custom task ID (auto-generated if null)
     * @param dependencies List of task IDs this task depends on
     * @return The created TaskRecord
     */
    public TaskRecord add(String title, String content, String taskId, List<String> dependencies) {
        String id = taskId != null && !taskId.isBlank() ? taskId : UUID.randomUUID().toString();
        TaskStatus status = dependencies != null && !dependencies.isEmpty() ? TaskStatus.BLOCKED : TaskStatus.PENDING;
        TaskRecord record = new TaskRecord(id, title, content, status);
        if (dependencies != null) {
            record.getBlockedBy().addAll(dependencies);
            for (String dep : dependencies) {
                TaskRecord upstream = tasks.get(dep);
                if (upstream != null && !upstream.getBlocks().contains(id)) {
                    upstream.getBlocks().add(id);
                }
            }
        }
        tasks.put(id, record);
        publishEvent(TaskEvent.created(teamName, id, status.name().toLowerCase()));
        return record;
    }

    /**
     * Add a task to the team (async version).
     *
     * @param title       Task title
     * @param content     Task content
     * @param taskId      Optional custom task ID
     * @param dependencies List of task IDs this task depends on
     * @return CompletableFuture containing the created TaskRecord
     */
    public CompletableFuture<TaskRecord> addAsync(String title, String content, String taskId, List<String> dependencies) {
        return CompletableFuture.supplyAsync(() -> add(title, content, taskId, dependencies));
    }

    /**
     * Add a task with bidirectional dependency support (prioritized task).
     * <p>
     * This method allows creating a task that can:
     * 1. Depend on existing tasks (dependencies parameter)
     * 2. Have existing tasks depend on it (dependentTaskIds parameter)
     * 3. Both of the above (inserting the task between other tasks)
     * <p>
     * Mirrors Python's {@code add_with_priority} method.
     *
     * @param title           Task title
     * @param content         Task content
     * @param taskId          Optional custom task ID
     * @param dependencies    List of existing task IDs the new task depends on
     * @param dependentTaskIds List of existing task IDs that should depend on the new task
     * @return The created TaskRecord, or null if creation failed (circular dependency)
     */
    public TaskRecord addWithPriority(String title, String content, String taskId,
                                       List<String> dependencies, List<String> dependentTaskIds) {
        String id = taskId != null && !taskId.isBlank() ? taskId : UUID.randomUUID().toString();

        // Check for circular dependencies
        if (wouldCreateCircularDependency(id, dependencies, dependentTaskIds)) {
            logger.warning("Circular dependency detected for task " + id);
            return null;
        }

        // Determine initial status
        TaskStatus status = dependencies != null && !dependencies.isEmpty() ? TaskStatus.BLOCKED : TaskStatus.PENDING;

        TaskRecord record = new TaskRecord(id, title, content, status);

        // Add dependencies (task depends on these)
        if (dependencies != null) {
            record.getBlockedBy().addAll(dependencies);
            for (String dep : dependencies) {
                TaskRecord upstream = tasks.get(dep);
                if (upstream != null && !upstream.getBlocks().contains(id)) {
                    upstream.getBlocks().add(id);
                }
            }
        }

        // Add reverse dependencies (these tasks depend on the new task)
        if (dependentTaskIds != null) {
            for (String depId : dependentTaskIds) {
                TaskRecord dependent = tasks.get(depId);
                if (dependent != null) {
                    // Update dependent task to be blocked by the new task
                    if (!dependent.getBlockedBy().contains(id)) {
                        dependent.getBlockedBy().add(id);
                    }
                    if (!record.getBlocks().contains(depId)) {
                        record.getBlocks().add(depId);
                    }
                    // Update status to BLOCKED if it was PENDING or CLAIMED
                    if (dependent.getStatus() == TaskStatus.PENDING || dependent.getStatus() == TaskStatus.CLAIMED) {
                        dependent.setStatus(TaskStatus.BLOCKED);
                        publishEvent(TaskEvent.unblocked(teamName, depId, TaskStatus.BLOCKED.name().toLowerCase()));
                    }
                }
            }
        }

        tasks.put(id, record);
        publishEvent(TaskEvent.created(teamName, id, status.name().toLowerCase()));
        return record;
    }

    /**
     * Add a task as top priority (blocks all existing pending/blockable tasks).
     * <p>
     * Creates a new task and makes all existing tasks that can be blocked
     * (pending or claimed status) depend on it. This ensures the new task
     * is executed before those tasks.
     * <p>
     * Mirrors Python's {@code add_as_top_priority} method.
     *
     * @param title   Task title
     * @param content Task content
     * @param taskId  Optional custom task ID
     * @return The created TaskRecord
     */
    public TaskRecord addAsTopPriority(String title, String content, String taskId) {
        String id = taskId != null && !taskId.isBlank() ? taskId : UUID.randomUUID().toString();

        // Get all tasks that can be blocked (pending or claimed)
        List<String> dependentTaskIds = new ArrayList<>();
        for (TaskRecord task : tasks.values()) {
            if (task.getStatus() == TaskStatus.PENDING || task.getStatus() == TaskStatus.CLAIMED) {
                dependentTaskIds.add(task.getTaskId());
            }
        }

        // Top priority task has no dependencies, starts as PENDING
        TaskRecord record = new TaskRecord(id, title, content, TaskStatus.PENDING);

        // Make existing tasks depend on the new task
        for (String depId : dependentTaskIds) {
            TaskRecord dependent = tasks.get(depId);
            if (dependent != null) {
                if (!dependent.getBlockedBy().contains(id)) {
                    dependent.getBlockedBy().add(id);
                }
                if (!record.getBlocks().contains(depId)) {
                    record.getBlocks().add(depId);
                }
                dependent.setStatus(TaskStatus.BLOCKED);
                publishEvent(TaskEvent.unblocked(teamName, depId, TaskStatus.BLOCKED.name().toLowerCase()));
            }
        }

        tasks.put(id, record);
        logger.info("Added top priority task " + id + ", blocking " + dependentTaskIds.size() + " existing tasks");
        publishEvent(TaskEvent.created(teamName, id, TaskStatus.PENDING.name().toLowerCase()));
        return record;
    }

    // ── Batch Methods ───────────────────────────────────────

    public List<TaskRecord> addBatch(List<Map<String, Object>> specs) {
        List<TaskRecord> created = new ArrayList<>();
        for (Map<String, Object> spec : specs) {
            String title = stringValue(spec.get("title"));
            String content = stringValue(spec.get("content"));
            if (title.isBlank() || content.isBlank()) {
                continue;
            }
            @SuppressWarnings("unchecked")
            List<String> deps = spec.get("dependencies") instanceof List<?> list
                    ? (List<String>) list : List.of();
            created.add(add(title, content, stringValue(spec.get("task_id")), deps));
        }
        return created;
    }

    /**
     * Add multiple tasks asynchronously.
     *
     * @param specs List of task specifications
     * @return CompletableFuture containing list of created tasks
     */
    public CompletableFuture<List<TaskRecord>> addBatchAsync(List<Map<String, Object>> specs) {
        return CompletableFuture.supplyAsync(() -> addBatch(specs));
    }

    // ── List/Get Methods ───────────────────────────────────────

    public List<TaskSummary> list() {
        return tasks.values().stream().map(TaskRecord::toSummary).toList();
    }

    /**
     * List tasks filtered by status.
     *
     * @param status Status filter (null for all)
     * @return List of task summaries
     */
    public List<TaskSummary> listByStatus(TaskStatus status) {
        return tasks.values().stream()
                .filter(t -> status == null || t.getStatus() == status)
                .map(TaskRecord::toSummary)
                .toList();
    }

    /**
     * List tasks with dependency info (async version).
     *
     * @param status Status filter (null for all)
     * @return CompletableFuture containing task summaries
     */
    public CompletableFuture<List<TaskSummary>> listAsync(TaskStatus status) {
        return CompletableFuture.supplyAsync(() -> listByStatus(status));
    }

    public TaskDetail get(String taskId) {
        TaskRecord record = tasks.get(taskId);
        return record != null ? record.toDetail() : null;
    }

    /**
     * Get task dependencies.
     *
     * @param taskId Task ID
     * @return List of task IDs this task depends on
     */
    public List<String> getDependencies(String taskId) {
        TaskRecord record = tasks.get(taskId);
        return record != null ? new ArrayList<>(record.getBlockedBy()) : List.of();
    }

    // ── State Transition Methods ───────────────────────────────────────

    public boolean claim(String taskId, String assignee) {
        TaskRecord record = tasks.get(taskId);
        if (record == null) {
            return false;
        }
        if (record.getStatus() != TaskStatus.PENDING) {
            return false;  // Only PENDING tasks can be claimed
        }
        record.setAssignee(assignee != null && !assignee.isBlank() ? assignee : memberName);
        record.setStatus(TaskStatus.CLAIMED);
        publishEvent(TaskEvent.claimed(teamName, taskId, record.getAssignee()));
        return true;
    }

    /**
     * Claim a task asynchronously.
     *
     * @param taskId  Task ID
     * @param assignee Assignee name
     * @return CompletableFuture indicating success
     */
    public CompletableFuture<Boolean> claimAsync(String taskId, String assignee) {
        return CompletableFuture.supplyAsync(() -> claim(taskId, assignee));
    }

    public boolean complete(String taskId) {
        TaskRecord record = tasks.get(taskId);
        if (record == null) {
            return false;
        }
        if (record.getStatus() != TaskStatus.CLAIMED && record.getStatus() != TaskStatus.PENDING) {
            return false;  // Only CLAIMED or PENDING tasks can be completed
        }
        record.setStatus(TaskStatus.COMPLETED);

        // Unblock dependent tasks
        for (String blockedTaskId : record.getBlocks()) {
            TaskRecord blocked = tasks.get(blockedTaskId);
            if (blocked == null) {
                continue;
            }
            blocked.getBlockedBy().remove(taskId);
            if (blocked.getBlockedBy().isEmpty() && blocked.getStatus() == TaskStatus.BLOCKED) {
                blocked.setStatus(TaskStatus.PENDING);
                publishEvent(TaskEvent.unblocked(teamName, blockedTaskId, TaskStatus.PENDING.name().toLowerCase()));
            }
        }
        publishEvent(TaskEvent.completed(teamName, taskId));
        return true;
    }

    /**
     * Complete a task asynchronously.
     *
     * @param taskId Task ID
     * @return CompletableFuture indicating success
     */
    public CompletableFuture<Boolean> completeAsync(String taskId) {
        return CompletableFuture.supplyAsync(() -> complete(taskId));
    }

    public boolean cancel(String taskId) {
        TaskRecord record = tasks.get(taskId);
        if (record == null) {
            return false;
        }
        record.setStatus(TaskStatus.CANCELLED);
        publishEvent(TaskEvent.cancelled(teamName, taskId));
        return true;
    }

    /**
     * Cancel a task asynchronously.
     *
     * @param taskId Task ID
     * @return CompletableFuture indicating success
     */
    public CompletableFuture<Boolean> cancelAsync(String taskId) {
        return CompletableFuture.supplyAsync(() -> cancel(taskId));
    }

    public boolean update(String taskId, String title, String content) {
        TaskRecord record = tasks.get(taskId);
        if (record == null) {
            return false;
        }
        if (title != null && !title.isBlank()) {
            record.setTitle(title);
        }
        if (content != null && !content.isBlank()) {
            record.setContent(content);
        }
        publishEvent(TaskEvent.updated(teamName, taskId));
        return true;
    }

    // ── Helper Methods ───────────────────────────────────────

    public String getTeamName() {
        return teamName;
    }

    public String getMemberName() {
        return memberName;
    }

    public int size() {
        return tasks.size();
    }

    private void publishEvent(TaskEvent event) {
        if (eventPublisher != null) {
            try {
                eventPublisher.accept(event);
            } catch (Exception e) {
                logger.warning("Failed to publish event: " + e.getMessage());
            }
        }
    }

    /**
     * Check if adding a task with given dependencies would create a circular dependency.
     *
     * @param newTaskId       The new task ID
     * @param dependencies    Tasks the new task depends on
     * @param dependentTaskIds Tasks that will depend on the new task
     * @return true if circular dependency would be created
     */
    private boolean wouldCreateCircularDependency(String newTaskId, List<String> dependencies, List<String> dependentTaskIds) {
        // Check if any dependency transitively depends on any dependent task
        if (dependencies == null || dependentTaskIds == null) {
            return false;
        }

        for (String dep : dependencies) {
            if (dependentTaskIds.contains(dep)) {
                return true;  // Direct cycle
            }
            // Check transitive dependencies
            if (transitivelyDependsOn(dep, dependentTaskIds)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Check if a task transitively depends on any of the given tasks.
     *
     * @param taskId   Task to check
     * @param targetIds Target task IDs
     * @return true if transitive dependency exists
     */
    private boolean transitivelyDependsOn(String taskId, List<String> targetIds) {
        TaskRecord record = tasks.get(taskId);
        if (record == null) {
            return false;
        }
        for (String dep : record.getBlockedBy()) {
            if (targetIds.contains(dep)) {
                return true;
            }
            if (transitivelyDependsOn(dep, targetIds)) {
                return true;
            }
        }
        return false;
    }

    private static String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    // ── Task Event Class ───────────────────────────────────────

    /**
     * Simple task event class for event publishing.
     */
    public static class TaskEvent {
        private final String type;
        private final String teamName;
        private final String taskId;
        private final String status;
        private final String assignee;

        private TaskEvent(String type, String teamName, String taskId, String status, String assignee) {
            this.type = type;
            this.teamName = teamName;
            this.taskId = taskId;
            this.status = status;
            this.assignee = assignee;
        }

        public static TaskEvent created(String teamName, String taskId, String status) {
            return new TaskEvent("created", teamName, taskId, status, null);
        }

        public static TaskEvent claimed(String teamName, String taskId, String assignee) {
            return new TaskEvent("claimed", teamName, taskId, null, assignee);
        }

        public static TaskEvent completed(String teamName, String taskId) {
            return new TaskEvent("completed", teamName, taskId, "completed", null);
        }

        public static TaskEvent cancelled(String teamName, String taskId) {
            return new TaskEvent("cancelled", teamName, taskId, "cancelled", null);
        }

        public static TaskEvent updated(String teamName, String taskId) {
            return new TaskEvent("updated", teamName, taskId, null, null);
        }

        public static TaskEvent unblocked(String teamName, String taskId, String status) {
            return new TaskEvent("unblocked", teamName, taskId, status, null);
        }

        public String getType() { return type; }
        public String getTeamName() { return teamName; }
        public String getTaskId() { return taskId; }
        public String getStatus() { return status; }
        public String getAssignee() { return assignee; }
    }
}

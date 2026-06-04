/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.tools.database;

import com.openjiuwen.agent_teams.schema.task.GraphMutationResult;
import com.openjiuwen.agent_teams.schema.task.NewTaskSpec;
import com.openjiuwen.agent_teams.tools.TaskDatabaseEdge;
import com.openjiuwen.agent_teams.tools.TeamTask;
import com.openjiuwen.agent_teams.tools.TeamTaskDependency;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

/**
 * Task and task-dependency data access object.
 *
 * <p>Mirrors Python's {@code TaskDao} in
 * {@code openjiuwen.agent_teams.tools.database.task_dao}.</p>
 */
public class TaskDao {

    private static final Logger teamLogger = Logger.getLogger(TaskDao.class.getName());
    private static final Set<String> TASK_TERMINAL_STATUSES = Set.of("completed", "cancelled");
    private static final Set<String> TASK_DEPENDENCY_REJECT_STATUSES =
            Set.of("completed", "cancelled", "claimed", "plan_approved");
    private static final Map<String, Set<String>> TASK_TRANSITIONS = Map.of(
            "pending", Set.of("claimed", "blocked", "cancelled"),
            "claimed", Set.of("plan_approved", "completed", "cancelled", "blocked", "pending"),
            "plan_approved", Set.of("completed", "pending", "cancelled"),
            "blocked", Set.of("pending", "cancelled"),
            "completed", Set.of(),
            "cancelled", Set.of()
    );

    private final TeamDatabaseState state;

    public TaskDao() {
        this(new TeamDatabaseState(DatabaseConfig.inMemory()));
        this.state.createCurrentSessionTables();
    }

    public TaskDao(TeamDatabaseState state) {
        this.state = state;
    }

    public CompletableFuture<Optional<TeamTask>> getTask(String taskId) {
        try {
            return CompletableFuture.completedFuture(Optional.ofNullable(session().tasks().get(taskId)));
        } catch (RuntimeException e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    public CompletableFuture<Boolean> createTask(
            String taskId,
            String teamName,
            String title,
            String content,
            String status) {
        try {
            TeamDatabaseState.SessionData session = session();
            if (session.tasks().containsKey(taskId)) {
                teamLogger.severe(String.format("Task %s already exists", taskId));
                return CompletableFuture.completedFuture(false);
            }
            TeamTask task = new TeamTask(taskId, teamName, title, content, status, null, now());
            boolean created = session.tasks().putIfAbsent(taskId, task) == null;
            if (created) {
                teamLogger.info(String.format("Task %s created", taskId));
            }
            return CompletableFuture.completedFuture(created);
        } catch (RuntimeException e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    public CompletableFuture<List<TeamTask>> listTasks(String teamName, String status) {
        return getTeamTasks(teamName, status);
    }

    public CompletableFuture<List<TeamTask>> getTeamTasks(String teamName, String status) {
        try {
            String normalizedStatus = normalize(status);
            List<TeamTask> tasks = session().tasks().values().stream()
                    .filter(task -> teamName.equals(task.getTeamName()))
                    .filter(task -> normalizedStatus == null || normalizedStatus.equals(normalize(task.getStatus())))
                    .sorted(Comparator.comparing(TeamTask::getTaskId))
                    .toList();
            return CompletableFuture.completedFuture(tasks);
        } catch (RuntimeException e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    public CompletableFuture<List<TeamTask>> getTasksByAssignee(String teamName, String assigneeId, String status) {
        try {
            String normalizedStatus = normalize(status);
            List<TeamTask> tasks = session().tasks().values().stream()
                    .filter(task -> teamName.equals(task.getTeamName()))
                    .filter(task -> Objects.equals(assigneeId, task.getAssignee()))
                    .filter(task -> normalizedStatus == null || normalizedStatus.equals(normalize(task.getStatus())))
                    .sorted(Comparator.comparing(TeamTask::getTaskId))
                    .toList();
            return CompletableFuture.completedFuture(tasks);
        } catch (RuntimeException e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    public CompletableFuture<Boolean> claimTask(String taskId, String assignee) {
        try {
            TeamTask task = session().tasks().get(taskId);
            if (task == null) {
                teamLogger.severe(String.format("Task %s not found", taskId));
                return CompletableFuture.completedFuture(false);
            }
            if (task.getAssignee() != null) {
                teamLogger.warning(String.format("Task %s is already claimed by member %s", taskId, task.getAssignee()));
                return CompletableFuture.completedFuture(false);
            }
            if (!isValidTransition(task.getStatus(), "claimed")) {
                return CompletableFuture.completedFuture(false);
            }
            task.setStatus("claimed");
            task.setAssignee(assignee);
            task.setUpdatedAt(now());
            return CompletableFuture.completedFuture(true);
        } catch (RuntimeException e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    public CompletableFuture<Optional<TeamTask>> resetTask(String taskId) {
        try {
            TeamTask task = session().tasks().get(taskId);
            if (task == null || !"claimed".equals(normalize(task.getStatus())) || !isValidTransition(task.getStatus(), "pending")) {
                return CompletableFuture.completedFuture(Optional.empty());
            }
            task.setStatus("pending");
            task.setAssignee(null);
            task.setUpdatedAt(now());
            return CompletableFuture.completedFuture(Optional.of(task));
        } catch (RuntimeException e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    public CompletableFuture<Optional<TeamTask>> approvePlanTask(String taskId) {
        try {
            TeamTask task = session().tasks().get(taskId);
            if (task == null || !isValidTransition(task.getStatus(), "plan_approved")) {
                return CompletableFuture.completedFuture(Optional.empty());
            }
            task.setStatus("plan_approved");
            task.setUpdatedAt(now());
            return CompletableFuture.completedFuture(Optional.of(task));
        } catch (RuntimeException e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    public CompletableFuture<Boolean> updateTaskStatus(String taskId, String newStatus) {
        try {
            TeamDatabaseState.SessionData session = session();
            TeamTask task = session.tasks().get(taskId);
            if (task == null || !isValidTransition(task.getStatus(), newStatus)) {
                return CompletableFuture.completedFuture(false);
            }
            long now = now();
            task.setStatus(newStatus);
            task.setUpdatedAt(now);
            if ("completed".equals(normalize(newStatus))) {
                resolveDependenciesAndRefresh(session, taskId, now);
            }
            return CompletableFuture.completedFuture(true);
        } catch (RuntimeException e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    public CompletableFuture<Boolean> updateTask(String taskId, String title, String content) {
        try {
            TeamTask task = session().tasks().get(taskId);
            if (task == null) {
                return CompletableFuture.completedFuture(false);
            }
            String status = normalize(task.getStatus());
            if ("claimed".equals(status) || "plan_approved".equals(status)) {
                return CompletableFuture.completedFuture(false);
            }
            if (title != null && !title.equals(task.getTitle())) {
                task.setTitle(title);
            }
            if (content != null && !content.equals(task.getContent())) {
                task.setContent(content);
            }
            return CompletableFuture.completedFuture(true);
        } catch (RuntimeException e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    public GraphMutationResult mutateDependencyGraph(
            String teamName,
            List<NewTaskSpec> newTasks,
            List<TaskDatabaseEdge> addEdges) {
        TeamDatabaseState.SessionData session = session();
        List<NewTaskSpec> tasksToCreate = newTasks != null ? newTasks : List.of();
        List<TaskDatabaseEdge> edgesToAdd = addEdges != null ? addEdges : List.of();
        if (tasksToCreate.isEmpty() && edgesToAdd.isEmpty()) {
            return GraphMutationResult.success();
        }

        Map<String, TeamTask> taskSnapshot = new HashMap<>(session.tasks());
        Set<String> seenNewIds = new HashSet<>();
        long now = now();
        for (NewTaskSpec spec : tasksToCreate) {
            if (!seenNewIds.add(spec.taskId())) {
                return GraphMutationResult.fail("Duplicate task_id " + spec.taskId() + " in new_tasks");
            }
            if (taskSnapshot.containsKey(spec.taskId())) {
                return GraphMutationResult.fail("Integrity error: task " + spec.taskId() + " already exists");
            }
            taskSnapshot.put(spec.taskId(), new TeamTask(
                    spec.taskId(), teamName, spec.title(), spec.content(), spec.initialStatus(), null, now));
        }

        for (TaskDatabaseEdge edge : edgesToAdd) {
            TeamTask source = taskSnapshot.get(edge.taskId());
            TeamTask target = taskSnapshot.get(edge.dependsOnTaskId());
            if (source == null) {
                return GraphMutationResult.fail("Task " + edge.taskId() + " not found");
            }
            if (target == null) {
                return GraphMutationResult.fail("Dependency target " + edge.dependsOnTaskId() + " not found");
            }
            if (TASK_DEPENDENCY_REJECT_STATUSES.contains(normalize(source.getStatus()))) {
                return GraphMutationResult.fail(
                        "Cannot add dependency to " + edge.taskId()
                                + " in terminal or executing status: " + source.getStatus());
            }
        }

        Set<TeamDatabaseState.DependencyKey> existingEdges = new HashSet<>(session.dependencies().keySet());
        Set<TeamDatabaseState.DependencyKey> newEdges = new LinkedHashSet<>();
        Map<String, List<String>> adjacency = new HashMap<>();
        for (TeamTaskDependency dep : session.dependencies().values()) {
            if (teamName.equals(dep.getTeamName())) {
                adjacency.computeIfAbsent(dep.getTaskId(), ignored -> new ArrayList<>()).add(dep.getDependsOnTaskId());
            }
        }
        for (TaskDatabaseEdge edge : edgesToAdd) {
            TeamDatabaseState.DependencyKey key =
                    new TeamDatabaseState.DependencyKey(edge.taskId(), edge.dependsOnTaskId());
            if (existingEdges.contains(key) || !newEdges.add(key)) {
                continue;
            }
            adjacency.computeIfAbsent(edge.taskId(), ignored -> new ArrayList<>()).add(edge.dependsOnTaskId());
        }
        List<String> cycle = detectCycle(adjacency);
        if (cycle != null) {
            return GraphMutationResult.fail("Circular dependency detected: " + String.join(" -> ", cycle));
        }

        for (NewTaskSpec spec : tasksToCreate) {
            session.tasks().put(spec.taskId(), taskSnapshot.get(spec.taskId()));
        }
        for (TeamDatabaseState.DependencyKey key : newEdges) {
            TeamTask dependencyTarget = taskSnapshot.get(key.dependsOnTaskId());
            boolean resolved = TASK_TERMINAL_STATUSES.contains(normalize(dependencyTarget.getStatus()));
            session.dependencies().put(key, new TeamTaskDependency(key.taskId(), key.dependsOnTaskId(), teamName, resolved));
        }

        Set<String> affectedIds = new LinkedHashSet<>();
        tasksToCreate.forEach(spec -> affectedIds.add(spec.taskId()));
        newEdges.forEach(key -> affectedIds.add(key.taskId()));
        List<TeamTask> refreshed = refreshStatusInSession(session, affectedIds, now);
        return GraphMutationResult.success(refreshed);
    }

    public CompletableFuture<Boolean> addTaskWithBidirectionalDependencies(
            String taskId,
            String teamName,
            String title,
            String content,
            String status,
            List<String> dependencies,
            List<String> dependentTaskIds) {
        List<TaskDatabaseEdge> edges = new ArrayList<>();
        if (dependencies != null) {
            dependencies.forEach(depId -> edges.add(new TaskDatabaseEdge(taskId, depId)));
        }
        if (dependentTaskIds != null) {
            dependentTaskIds.forEach(dependentId -> edges.add(new TaskDatabaseEdge(dependentId, taskId)));
        }
        GraphMutationResult result = mutateDependencyGraph(
                teamName,
                List.of(new NewTaskSpec(taskId, title, content, status)),
                edges);
        return CompletableFuture.completedFuture(result.ok());
    }

    public CompletableFuture<List<TeamTaskDependency>> getTaskDependencies(String taskId) {
        try {
            List<TeamTaskDependency> deps = session().dependencies().values().stream()
                    .filter(dep -> taskId.equals(dep.getTaskId()))
                    .sorted(Comparator.comparing(TeamTaskDependency::getDependsOnTaskId))
                    .toList();
            return CompletableFuture.completedFuture(deps);
        } catch (RuntimeException e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    public CompletableFuture<Integer> getUnresolvedDependenciesCount(String taskId) {
        try {
            long count = session().dependencies().values().stream()
                    .filter(dep -> taskId.equals(dep.getTaskId()))
                    .filter(dep -> !Boolean.TRUE.equals(dep.getResolved()))
                    .count();
            return CompletableFuture.completedFuture(Math.toIntExact(count));
        } catch (RuntimeException e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    public CompletableFuture<List<TeamTask>> getTasksDependingOn(String dependsOnTaskId) {
        try {
            TeamDatabaseState.SessionData session = session();
            List<TeamTask> tasks = session.dependencies().values().stream()
                    .filter(dep -> dependsOnTaskId.equals(dep.getDependsOnTaskId()))
                    .map(dep -> session.tasks().get(dep.getTaskId()))
                    .filter(Objects::nonNull)
                    .sorted(Comparator.comparing(TeamTask::getTaskId))
                    .toList();
            return CompletableFuture.completedFuture(tasks);
        } catch (RuntimeException e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    public CompletableFuture<Boolean> deleteTask(String taskId) {
        try {
            TeamDatabaseState.SessionData session = session();
            TeamTask removed = session.tasks().remove(taskId);
            if (removed == null) {
                return CompletableFuture.completedFuture(false);
            }
            session.dependencies().keySet().removeIf(
                    key -> taskId.equals(key.taskId()) || taskId.equals(key.dependsOnTaskId()));
            return CompletableFuture.completedFuture(true);
        } catch (RuntimeException e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    public CompletableFuture<Map<String, Object>> cancelTask(String taskId) {
        try {
            return CompletableFuture.completedFuture(terminateTask(session(), taskId, "cancelled", now()));
        } catch (RuntimeException e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    public CompletableFuture<Map<String, Object>> completeTask(String taskId) {
        try {
            return CompletableFuture.completedFuture(terminateTask(session(), taskId, "completed", now()));
        } catch (RuntimeException e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    public CompletableFuture<Map<String, Object>> cancelAllTasks(String teamName, Set<String> skipAssignees) {
        try {
            TeamDatabaseState.SessionData session = session();
            Set<String> skips = skipAssignees != null ? skipAssignees : Set.of();
            long now = now();
            List<TeamTask> cancelled = new ArrayList<>();
            Map<String, TeamTask> unblockedById = new HashMap<>();
            for (TeamTask task : new ArrayList<>(session.tasks().values())) {
                if (!teamName.equals(task.getTeamName()) || TASK_TERMINAL_STATUSES.contains(normalize(task.getStatus()))) {
                    continue;
                }
                if (task.getAssignee() != null && skips.contains(task.getAssignee())) {
                    continue;
                }
                Map<String, Object> outcome = terminateTask(session, task.getTaskId(), "cancelled", now);
                Object cancelledTask = outcome.get("task");
                if (cancelledTask instanceof TeamTask typedTask) {
                    cancelled.add(typedTask);
                }
                Object unblocked = outcome.get("unblocked_tasks");
                if (unblocked instanceof List<?> list) {
                    for (Object item : list) {
                        if (item instanceof TeamTask refreshed) {
                            unblockedById.put(refreshed.getTaskId(), refreshed);
                        }
                    }
                }
            }
            Set<String> cancelledIds = new HashSet<>();
            cancelled.forEach(task -> cancelledIds.add(task.getTaskId()));
            List<TeamTask> unblocked = unblockedById.values().stream()
                    .filter(task -> !cancelledIds.contains(task.getTaskId()))
                    .toList();
            return CompletableFuture.completedFuture(Map.of(
                    "cancelled_tasks", cancelled,
                    "unblocked_tasks", unblocked));
        } catch (RuntimeException e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    public CompletableFuture<List<TeamTask>> verifyAndFixTaskConsistency(String teamName) {
        try {
            TeamDatabaseState.SessionData session = session();
            List<String> blockedIds = session.tasks().values().stream()
                    .filter(task -> teamName.equals(task.getTeamName()))
                    .filter(task -> "blocked".equals(normalize(task.getStatus())))
                    .map(TeamTask::getTaskId)
                    .toList();
            return CompletableFuture.completedFuture(refreshStatusInSession(session, blockedIds, now()));
        } catch (RuntimeException e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    private Map<String, Object> terminateTask(
            TeamDatabaseState.SessionData session,
            String taskId,
            String newStatus,
            long now) {
        TeamTask task = session.tasks().get(taskId);
        if (task == null) {
            return null;
        }
        if (newStatus.equals(normalize(task.getStatus()))) {
            return Map.of("task", task, "unblocked_tasks", List.of());
        }
        if (!isValidTransition(task.getStatus(), newStatus)) {
            return null;
        }
        task.setStatus(newStatus);
        task.setUpdatedAt(now);
        List<TeamTask> unblocked = resolveDependenciesAndRefresh(session, taskId, now);
        return Map.of("task", task, "unblocked_tasks", unblocked);
    }

    private List<TeamTask> resolveDependenciesAndRefresh(
            TeamDatabaseState.SessionData session,
            String completedTaskId,
            long now) {
        Set<String> downstreamIds = new HashSet<>();
        for (TeamTaskDependency dep : session.dependencies().values()) {
            if (completedTaskId.equals(dep.getDependsOnTaskId())) {
                if (!Boolean.TRUE.equals(dep.getResolved())) {
                    dep.setResolved(true);
                }
                downstreamIds.add(dep.getTaskId());
            }
        }
        return refreshStatusInSession(session, downstreamIds, now);
    }

    private List<TeamTask> refreshStatusInSession(
            TeamDatabaseState.SessionData session,
            Iterable<String> taskIds,
            long now) {
        List<TeamTask> refreshed = new ArrayList<>();
        Set<String> uniqueIds = new LinkedHashSet<>();
        for (String taskId : taskIds) {
            if (taskId != null && !taskId.isEmpty()) {
                uniqueIds.add(taskId);
            }
        }
        for (String taskId : uniqueIds) {
            TeamTask task = session.tasks().get(taskId);
            if (task == null) {
                continue;
            }
            String status = normalize(task.getStatus());
            if (!"pending".equals(status) && !"blocked".equals(status)) {
                continue;
            }
            long unresolved = session.dependencies().values().stream()
                    .filter(dep -> taskId.equals(dep.getTaskId()))
                    .filter(dep -> !Boolean.TRUE.equals(dep.getResolved()))
                    .count();
            if ("pending".equals(status) && unresolved > 0) {
                task.setStatus("blocked");
                task.setUpdatedAt(now);
                refreshed.add(task);
            } else if ("blocked".equals(status) && unresolved == 0) {
                task.setStatus("pending");
                task.setUpdatedAt(now);
                refreshed.add(task);
            }
        }
        return refreshed;
    }

    private List<String> detectCycle(Map<String, List<String>> adjacency) {
        Set<String> nodes = new LinkedHashSet<>();
        adjacency.forEach((node, deps) -> {
            nodes.add(node);
            nodes.addAll(deps);
        });
        Map<String, Integer> color = new HashMap<>();
        nodes.forEach(node -> color.put(node, 0));
        for (String root : nodes) {
            if (color.getOrDefault(root, 0) != 0) {
                continue;
            }
            List<String> path = new ArrayList<>();
            path.add(root);
            color.put(root, 1);
            ArrayDeque<Frame> stack = new ArrayDeque<>();
            stack.push(new Frame(root, new ArrayList<>(adjacency.getOrDefault(root, List.of()))));
            while (!stack.isEmpty()) {
                Frame frame = stack.peek();
                if (frame.children().isEmpty()) {
                    stack.pop();
                    color.put(frame.node(), 2);
                    path.remove(path.size() - 1);
                    continue;
                }
                String next = frame.children().remove(frame.children().size() - 1);
                int nextColor = color.getOrDefault(next, 0);
                if (nextColor == 1) {
                    int index = path.indexOf(next);
                    List<String> cycle = new ArrayList<>(path.subList(index, path.size()));
                    cycle.add(next);
                    return cycle;
                }
                if (nextColor == 0) {
                    color.put(next, 1);
                    path.add(next);
                    stack.push(new Frame(next, new ArrayList<>(adjacency.getOrDefault(next, List.of()))));
                }
            }
        }
        return null;
    }

    private TeamDatabaseState.SessionData session() {
        return state.currentSession();
    }

    private boolean isValidTransition(String currentStatus, String newStatus) {
        String current = normalize(currentStatus);
        String next = normalize(newStatus);
        return current != null && next != null && TASK_TRANSITIONS.getOrDefault(current, Set.of()).contains(next);
    }

    private static String normalize(String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        return value.toLowerCase(Locale.ROOT);
    }

    private static long now() {
        return DatabaseEngine.getCurrentTime();
    }

    private record Frame(String node, List<String> children) {
    }
}

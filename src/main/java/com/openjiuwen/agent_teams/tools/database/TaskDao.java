/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.tools.database;

import com.openjiuwen.agent_teams.AgentTeamsContext;
import com.openjiuwen.agent_teams.schema.GraphMutationResult;
import com.openjiuwen.agent_teams.schema.NewTaskSpec;
import com.openjiuwen.agent_teams.schema.status.StatusTransitions;
import com.openjiuwen.agent_teams.schema.status.TaskStatus;
import com.openjiuwen.agent_teams.tools.TeamTask;
import com.openjiuwen.agent_teams.tools.TeamTaskDependency;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.common.logging.LoggerProtocol;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * Task and task-dependency data access object.
 *
 * <p>Mirrors Python's {@code TaskDao} in
 * {@code openjiuwen/agent_teams/tools/database/task_dao.py}.</p>
 */
public class TaskDao {

    private static final LoggerProtocol TEAM_LOGGER = Loggers.TEAM;

    private final DatabaseEngine engine;

    public TaskDao() {
        this(createDefaultEngine());
    }

    public TaskDao(DatabaseEngine engine) {
        this.engine = engine;
        ensureInitialized();
    }

    /**
     * Mirrors Python's internal dependency-edge tuple in
     * {@code openjiuwen/agent_teams/tools/database/task_dao.py}.
     */
    public record DependencyEdge(String taskId, String dependsOnTaskId) {
    }

    /**
     * Mirrors Python's task terminate result dict in
     * {@code openjiuwen/agent_teams/tools/database/task_dao.py}.
     */
    public record TaskTerminationResult(TeamTask task, List<TeamTask> unblockedTasks) {
    }

    /**
     * Mirrors Python's bulk cancel result dict in
     * {@code openjiuwen/agent_teams/tools/database/task_dao.py}.
     */
    public record TaskBulkCancellationResult(List<TeamTask> cancelledTasks, List<TeamTask> unblockedTasks) {
    }

    /**
     * Mirrors Python's internal {@code _MutationFailure} in
     * {@code openjiuwen/agent_teams/tools/database/task_dao.py}.
     */
    private static final class MutationFailure extends Exception {

        private final String reason;

        private MutationFailure(String reason) {
            super(reason);
            this.reason = reason;
        }
    }

    @FunctionalInterface
    private interface SqlSupplier<T> {
        T get() throws SQLException, MutationFailure;
    }

    public CompletableFuture<Boolean> createTask(
            String taskId,
            String teamName,
            String title,
            String content,
            String status) {
        return supplyAsyncWithSessionContext(() -> {
            synchronized (engine) {
                try (PreparedStatement statement = connection().prepareStatement(
                        "INSERT INTO " + currentTaskTable()
                                + " (task_id, team_name, title, content, status, assignee, updated_at) "
                                + "VALUES (?, ?, ?, ?, ?, ?, ?)")) {
                    statement.setString(1, taskId);
                    statement.setString(2, teamName);
                    statement.setString(3, title);
                    statement.setString(4, content);
                    statement.setString(5, status);
                    statement.setString(6, null);
                    statement.setLong(7, DatabaseEngine.getCurrentTime());
                    statement.executeUpdate();
                    TEAM_LOGGER.info("Task %s created", taskId);
                    return true;
                } catch (SQLException exception) {
                    if (isIntegrityViolation(exception)) {
                        TEAM_LOGGER.error("Task %s already exists: %s", taskId, exception.getMessage());
                        return false;
                    }
                    throw new RuntimeException("Failed to create task " + taskId, exception);
                }
            }
        });
    }

    public CompletableFuture<Optional<TeamTask>> getTask(String taskId) {
        return supplyAsyncWithSessionContext(() -> {
            synchronized (engine) {
                try (PreparedStatement statement = connection().prepareStatement(
                        "SELECT task_id, team_name, title, content, status, assignee, updated_at "
                                + "FROM " + currentTaskTable() + " WHERE task_id = ?")) {
                    statement.setString(1, taskId);
                    try (ResultSet resultSet = statement.executeQuery()) {
                        if (!resultSet.next()) {
                            return Optional.empty();
                        }
                        return Optional.of(mapTask(resultSet));
                    }
                } catch (SQLException exception) {
                    throw new RuntimeException("Failed to get task " + taskId, exception);
                }
            }
        });
    }

    public CompletableFuture<List<TeamTask>> getTeamTasks(String teamName, String status) {
        return supplyAsyncWithSessionContext(() -> {
            synchronized (engine) {
                try (PreparedStatement statement = connection().prepareStatement(
                        "SELECT task_id, team_name, title, content, status, assignee, updated_at "
                                + "FROM " + currentTaskTable()
                                + " WHERE team_name = ? ORDER BY task_id")) {
                    statement.setString(1, teamName);
                    try (ResultSet resultSet = statement.executeQuery()) {
                        List<TeamTask> tasks = new ArrayList<>();
                        while (resultSet.next()) {
                            TeamTask task = mapTask(resultSet);
                            if (status == null || Objects.equals(task.getStatus(), status)) {
                                tasks.add(task);
                            }
                        }
                        return tasks;
                    }
                } catch (SQLException exception) {
                    throw new RuntimeException("Failed to list tasks for team " + teamName, exception);
                }
            }
        });
    }

    public CompletableFuture<List<TeamTask>> getTasksByAssignee(String teamName, String assigneeId, String status) {
        return supplyAsyncWithSessionContext(() -> {
            synchronized (engine) {
                try (PreparedStatement statement = connection().prepareStatement(
                        "SELECT task_id, team_name, title, content, status, assignee, updated_at "
                                + "FROM " + currentTaskTable()
                                + " WHERE team_name = ? AND assignee = ? ORDER BY task_id")) {
                    statement.setString(1, teamName);
                    statement.setString(2, assigneeId);
                    try (ResultSet resultSet = statement.executeQuery()) {
                        List<TeamTask> tasks = new ArrayList<>();
                        while (resultSet.next()) {
                            TeamTask task = mapTask(resultSet);
                            if (status == null || Objects.equals(task.getStatus(), status)) {
                                tasks.add(task);
                            }
                        }
                        return tasks;
                    }
                } catch (SQLException exception) {
                    throw new RuntimeException(
                            "Failed to list tasks for assignee " + assigneeId + " in team " + teamName,
                            exception
                    );
                }
            }
        });
    }

    public CompletableFuture<Boolean> claimTask(String taskId, String memberName) {
        return supplyAsyncWithSessionContext(() -> {
            synchronized (engine) {
                try {
                    TeamTask task = fetchTask(connection(), taskId);
                    if (task == null) {
                        TEAM_LOGGER.error("Task %s not found", taskId);
                        return false;
                    }
                    if (task.getAssignee() != null) {
                        TEAM_LOGGER.warning("Task %s is already claimed by member %s", taskId, task.getAssignee());
                        return false;
                    }
                    if (!isValidTransition(task.getStatus(), TaskStatus.CLAIMED)) {
                        TEAM_LOGGER.error(
                                "Invalid state transition for task %s: %s -> %s",
                                taskId,
                                task.getStatus(),
                                TaskStatus.CLAIMED.value()
                        );
                        return false;
                    }
                    try (PreparedStatement statement = connection().prepareStatement(
                            "UPDATE " + currentTaskTable() + " SET status = ?, assignee = ?, updated_at = ? "
                                    + "WHERE task_id = ?")) {
                        statement.setString(1, TaskStatus.CLAIMED.value());
                        statement.setString(2, memberName);
                        statement.setLong(3, DatabaseEngine.getCurrentTime());
                        statement.setString(4, taskId);
                        statement.executeUpdate();
                    }
                    TEAM_LOGGER.info("Task %s claimed by member %s", taskId, memberName);
                    return true;
                } catch (SQLException exception) {
                    throw new RuntimeException("Failed to claim task " + taskId, exception);
                }
            }
        });
    }

    public CompletableFuture<Optional<TeamTask>> resetTask(String taskId) {
        return supplyAsyncWithSessionContext(() -> {
            synchronized (engine) {
                try {
                    TeamTask task = fetchTask(connection(), taskId);
                    if (task == null) {
                        TEAM_LOGGER.error("Task %s not found", taskId);
                        return Optional.empty();
                    }
                    if (!Objects.equals(task.getStatus(), TaskStatus.CLAIMED.value())) {
                        TEAM_LOGGER.error(
                                "Cannot reset task %s with status %s, only CLAIMED tasks can be reset",
                                taskId,
                                task.getStatus()
                        );
                        return Optional.empty();
                    }
                    if (!isValidTransition(task.getStatus(), TaskStatus.PENDING)) {
                        TEAM_LOGGER.error(
                                "Invalid state transition for task %s: %s -> %s",
                                taskId,
                                task.getStatus(),
                                TaskStatus.PENDING.value()
                        );
                        return Optional.empty();
                    }
                    try (PreparedStatement statement = connection().prepareStatement(
                            "UPDATE " + currentTaskTable() + " SET status = ?, assignee = ?, updated_at = ? "
                                    + "WHERE task_id = ?")) {
                        statement.setString(1, TaskStatus.PENDING.value());
                        statement.setString(2, null);
                        statement.setLong(3, DatabaseEngine.getCurrentTime());
                        statement.setString(4, taskId);
                        statement.executeUpdate();
                    }
                    return Optional.of(fetchTask(connection(), taskId));
                } catch (SQLException exception) {
                    throw new RuntimeException("Failed to reset task " + taskId, exception);
                }
            }
        });
    }

    public CompletableFuture<Optional<TeamTask>> approvePlanTask(String taskId) {
        return supplyAsyncWithSessionContext(() -> {
            synchronized (engine) {
                try {
                    TeamTask task = fetchTask(connection(), taskId);
                    if (task == null) {
                        TEAM_LOGGER.error("Task %s not found", taskId);
                        return Optional.empty();
                    }
                    if (!isValidTransition(task.getStatus(), TaskStatus.PLAN_APPROVED)) {
                        TEAM_LOGGER.error(
                                "Invalid state transition for task %s: %s -> %s",
                                taskId,
                                task.getStatus(),
                                TaskStatus.PLAN_APPROVED.value()
                        );
                        return Optional.empty();
                    }
                    try (PreparedStatement statement = connection().prepareStatement(
                            "UPDATE " + currentTaskTable() + " SET status = ?, updated_at = ? WHERE task_id = ?")) {
                        statement.setString(1, TaskStatus.PLAN_APPROVED.value());
                        statement.setLong(2, DatabaseEngine.getCurrentTime());
                        statement.setString(3, taskId);
                        statement.executeUpdate();
                    }
                    return Optional.of(fetchTask(connection(), taskId));
                } catch (SQLException exception) {
                    throw new RuntimeException("Failed to approve plan task " + taskId, exception);
                }
            }
        });
    }

    public CompletableFuture<Boolean> updateTaskStatus(String taskId, String status) {
        return supplyAsyncWithSessionContext(() -> {
            synchronized (engine) {
                try {
                    return inTransaction(() -> {
                        Connection connection = connection();
                        TeamTask task = fetchTask(connection, taskId);
                        if (task == null) {
                            TEAM_LOGGER.error("Task %s not found", taskId);
                            return false;
                        }
                        TaskStatus newStatus = TaskStatus.fromValue(status);
                        if (!isValidTransition(task.getStatus(), newStatus)) {
                            TEAM_LOGGER.error(
                                    "Invalid state transition for task %s: %s -> %s",
                                    taskId,
                                    task.getStatus(),
                                    status
                            );
                            return false;
                        }
                        long now = DatabaseEngine.getCurrentTime();
                        updateTaskState(connection, taskId, status, task.getAssignee(), now);
                        if (TaskStatus.COMPLETED == newStatus || TaskStatus.CANCELLED == newStatus) {
                            resolveDependenciesAndRefresh(connection, taskId, now);
                        }
                        TEAM_LOGGER.info("Task %s status updated to %s", taskId, status);
                        return true;
                    });
                } catch (SQLException | MutationFailure exception) {
                    throw new RuntimeException("Failed to update task status for " + taskId, exception);
                }
            }
        });
    }

    public CompletableFuture<Boolean> updateTask(String taskId, String title, String content) {
        return supplyAsyncWithSessionContext(() -> {
            synchronized (engine) {
                try {
                    TeamTask task = fetchTask(connection(), taskId);
                    if (task == null) {
                        TEAM_LOGGER.error("Task %s not found", taskId);
                        return false;
                    }
                    if (Objects.equals(task.getStatus(), TaskStatus.CLAIMED.value())
                            || Objects.equals(task.getStatus(), TaskStatus.PLAN_APPROVED.value())) {
                        TEAM_LOGGER.error(
                                "Cannot update task %s because it is currently %s",
                                taskId,
                                task.getStatus()
                        );
                        return false;
                    }
                    boolean updated = false;
                    String nextTitle = task.getTitle();
                    String nextContent = task.getContent();
                    if (title != null && !Objects.equals(title, task.getTitle())) {
                        nextTitle = title;
                        updated = true;
                    }
                    if (content != null && !Objects.equals(content, task.getContent())) {
                        nextContent = content;
                        updated = true;
                    }
                    if (!updated) {
                        return true;
                    }
                    try (PreparedStatement statement = connection().prepareStatement(
                            "UPDATE " + currentTaskTable() + " SET title = ?, content = ? WHERE task_id = ?")) {
                        statement.setString(1, nextTitle);
                        statement.setString(2, nextContent);
                        statement.setString(3, taskId);
                        statement.executeUpdate();
                    }
                    TEAM_LOGGER.info("Task %s updated", taskId);
                    return true;
                } catch (SQLException exception) {
                    throw new RuntimeException("Failed to update task " + taskId, exception);
                }
            }
        });
    }

    public CompletableFuture<GraphMutationResult> mutateDependencyGraph(
            String teamName,
            List<NewTaskSpec> newTasks,
            List<DependencyEdge> addEdges) {
        return supplyAsyncWithSessionContext(() -> {
            synchronized (engine) {
                try {
                    return inTransaction(() -> mutateDependencyGraphInternal(teamName, newTasks, addEdges));
                } catch (MutationFailure exception) {
                    return GraphMutationResult.fail(exception.reason);
                } catch (SQLException exception) {
                    TEAM_LOGGER.error("mutate_dependency_graph unexpected error: %s", exception.getMessage());
                    return GraphMutationResult.fail("Unexpected error: " + exception.getMessage());
                }
            }
        });
    }

    public CompletableFuture<Boolean> addTaskWithBidirectionalDependencies(
            String taskId,
            String teamName,
            String title,
            String content,
            String status,
            List<String> dependencies,
            List<String> dependentTaskIds) {
        List<DependencyEdge> edges = new ArrayList<>();
        if (dependencies != null) {
            for (String dependency : dependencies) {
                edges.add(new DependencyEdge(taskId, dependency));
            }
        }
        if (dependentTaskIds != null) {
            for (String dependentTaskId : dependentTaskIds) {
                edges.add(new DependencyEdge(dependentTaskId, taskId));
            }
        }
        return mutateDependencyGraph(
                teamName,
                List.of(new NewTaskSpec(taskId, title, content, status)),
                edges
        ).thenApply(GraphMutationResult::ok);
    }

    public CompletableFuture<List<TeamTaskDependency>> getTaskDependencies(String taskId) {
        return supplyAsyncWithSessionContext(() -> {
            synchronized (engine) {
                try (PreparedStatement statement = connection().prepareStatement(
                        "SELECT task_id, depends_on_task_id, team_name, resolved "
                                + "FROM " + currentDependencyTable()
                                + " WHERE task_id = ? ORDER BY depends_on_task_id")) {
                    statement.setString(1, taskId);
                    try (ResultSet resultSet = statement.executeQuery()) {
                        List<TeamTaskDependency> dependencies = new ArrayList<>();
                        while (resultSet.next()) {
                            dependencies.add(mapDependency(resultSet));
                        }
                        return dependencies;
                    }
                } catch (SQLException exception) {
                    throw new RuntimeException("Failed to load dependencies for task " + taskId, exception);
                }
            }
        });
    }

    public CompletableFuture<Integer> getUnresolvedDependenciesCount(String taskId) {
        return supplyAsyncWithSessionContext(() -> {
            synchronized (engine) {
                try (PreparedStatement statement = connection().prepareStatement(
                        "SELECT COUNT(*) FROM " + currentDependencyTable()
                                + " WHERE task_id = ? AND resolved = ?")) {
                    statement.setString(1, taskId);
                    statement.setBoolean(2, false);
                    try (ResultSet resultSet = statement.executeQuery()) {
                        if (!resultSet.next()) {
                            return 0;
                        }
                        return resultSet.getInt(1);
                    }
                } catch (SQLException exception) {
                    throw new RuntimeException("Failed to count unresolved dependencies for " + taskId, exception);
                }
            }
        });
    }

    public CompletableFuture<List<TeamTask>> getTasksDependingOn(String dependsOnTaskId) {
        return supplyAsyncWithSessionContext(() -> {
            synchronized (engine) {
                try (PreparedStatement statement = connection().prepareStatement(
                        "SELECT t.task_id, t.team_name, t.title, t.content, t.status, t.assignee, t.updated_at "
                                + "FROM " + currentTaskTable() + " t "
                                + "JOIN " + currentDependencyTable() + " d ON t.task_id = d.task_id "
                                + "WHERE d.depends_on_task_id = ? ORDER BY t.task_id")) {
                    statement.setString(1, dependsOnTaskId);
                    try (ResultSet resultSet = statement.executeQuery()) {
                        List<TeamTask> tasks = new ArrayList<>();
                        while (resultSet.next()) {
                            tasks.add(mapTask(resultSet));
                        }
                        return tasks;
                    }
                } catch (SQLException exception) {
                    throw new RuntimeException("Failed to list tasks depending on " + dependsOnTaskId, exception);
                }
            }
        });
    }

    public CompletableFuture<Boolean> deleteTask(String taskId) {
        return supplyAsyncWithSessionContext(() -> {
            synchronized (engine) {
                try {
                    return inTransaction(() -> {
                        Connection connection = connection();
                        TeamTask task = fetchTask(connection, taskId);
                        if (task == null) {
                            TEAM_LOGGER.debug("Task %s not found for deletion", taskId);
                            return false;
                        }
                        deleteDependencyRowsForTask(connection, taskId);
                        try (PreparedStatement deleteTask = connection.prepareStatement(
                                "DELETE FROM " + currentTaskTable() + " WHERE task_id = ?")) {
                            deleteTask.setString(1, taskId);
                            deleteTask.executeUpdate();
                        }
                        TEAM_LOGGER.info("Task %s deleted", taskId);
                        return true;
                    });
                } catch (SQLException | MutationFailure exception) {
                    throw new RuntimeException("Failed to delete task " + taskId, exception);
                }
            }
        });
    }

    public CompletableFuture<Optional<TaskTerminationResult>> cancelTask(String taskId) {
        return terminateTask(taskId, TaskStatus.CANCELLED);
    }

    public CompletableFuture<Optional<TaskTerminationResult>> completeTask(String taskId) {
        return terminateTask(taskId, TaskStatus.COMPLETED);
    }

    public CompletableFuture<TaskBulkCancellationResult> cancelAllTasks(String teamName, Set<String> skipAssignees) {
        return supplyAsyncWithSessionContext(() -> {
            synchronized (engine) {
                try {
                    return inTransaction(() -> cancelAllTasksInternal(teamName, skipAssignees));
                } catch (SQLException | MutationFailure exception) {
                    throw new RuntimeException("Failed to cancel tasks for team " + teamName, exception);
                }
            }
        });
    }

    public CompletableFuture<List<TeamTask>> verifyAndFixTaskConsistency(String teamName) {
        return supplyAsyncWithSessionContext(() -> {
            synchronized (engine) {
                try {
                    return inTransaction(() -> {
                        List<String> blockedIds = new ArrayList<>();
                        try (PreparedStatement statement = connection().prepareStatement(
                                "SELECT task_id FROM " + currentTaskTable()
                                        + " WHERE team_name = ? AND status = ?")) {
                            statement.setString(1, teamName);
                            statement.setString(2, TaskStatus.BLOCKED.value());
                            try (ResultSet resultSet = statement.executeQuery()) {
                                while (resultSet.next()) {
                                    blockedIds.add(resultSet.getString("task_id"));
                                }
                            }
                        }
                        if (blockedIds.isEmpty()) {
                            return List.of();
                        }
                        return refreshStatusInTransaction(connection(), blockedIds, DatabaseEngine.getCurrentTime());
                    });
                } catch (SQLException | MutationFailure exception) {
                    throw new RuntimeException("Failed to verify task consistency for team " + teamName, exception);
                }
            }
        });
    }

    private CompletableFuture<Optional<TaskTerminationResult>> terminateTask(String taskId, TaskStatus newStatus) {
        return supplyAsyncWithSessionContext(() -> {
            synchronized (engine) {
                try {
                    return inTransaction(() -> terminateTaskInternal(taskId, newStatus, DatabaseEngine.getCurrentTime()));
                } catch (SQLException | MutationFailure exception) {
                    throw new RuntimeException("Failed to terminate task " + taskId, exception);
                }
            }
        });
    }

    private GraphMutationResult mutateDependencyGraphInternal(
            String teamName,
            List<NewTaskSpec> newTasks,
            List<DependencyEdge> addEdges) throws SQLException, MutationFailure {
        List<NewTaskSpec> tasksToCreate = newTasks == null ? List.of() : List.copyOf(newTasks);
        List<DependencyEdge> edgesToAdd = addEdges == null ? List.of() : List.copyOf(addEdges);
        if (tasksToCreate.isEmpty() && edgesToAdd.isEmpty()) {
            return GraphMutationResult.success(List.of());
        }

        long now = DatabaseEngine.getCurrentTime();
        stageNewTasks(connection(), teamName, tasksToCreate, now);
        Map<String, TeamTask> endpointTasks = loadEndpointsAndValidate(connection(), edgesToAdd);
        Set<DependencyEdge> newEdgeSet = checkCycleAndComputeNewEdges(connection(), teamName, edgesToAdd);
        applyNewEdges(connection(), teamName, newEdgeSet, endpointTasks);

        Set<String> affectedIds = new LinkedHashSet<>();
        for (NewTaskSpec spec : tasksToCreate) {
            affectedIds.add(spec.taskId());
        }
        for (DependencyEdge edge : newEdgeSet) {
            affectedIds.add(edge.taskId());
        }
        List<TeamTask> refreshed = refreshStatusInTransaction(connection(), affectedIds, now);
        TEAM_LOGGER.info(
                "Created %d task(s); added %d edge(s); refreshed %d task(s)",
                tasksToCreate.size(),
                newEdgeSet.size(),
                refreshed.size()
        );
        return GraphMutationResult.success(new ArrayList<>(refreshed));
    }

    private TaskBulkCancellationResult cancelAllTasksInternal(String teamName, Set<String> skipAssignees)
            throws SQLException {
        Set<String> skips = skipAssignees == null ? Set.of() : skipAssignees;
        List<TeamTask> candidates = new ArrayList<>();
        try (PreparedStatement statement = connection().prepareStatement(
                "SELECT task_id, team_name, title, content, status, assignee, updated_at "
                        + "FROM " + currentTaskTable() + " WHERE team_name = ?")) {
            statement.setString(1, teamName);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    TeamTask task = mapTask(resultSet);
                    if (!TaskStatus.CANCELLED.value().equals(task.getStatus())
                            && !TaskStatus.COMPLETED.value().equals(task.getStatus())) {
                        candidates.add(task);
                    }
                }
            }
        }

        if (candidates.isEmpty()) {
            TEAM_LOGGER.info("No active tasks to cancel for team %s", teamName);
            return new TaskBulkCancellationResult(List.of(), List.of());
        }

        long now = DatabaseEngine.getCurrentTime();
        List<TeamTask> cancelledTasks = new ArrayList<>();
        Map<String, TeamTask> unblockedById = new LinkedHashMap<>();
        for (TeamTask task : candidates) {
            if (task.getAssignee() != null && skips.contains(task.getAssignee())) {
                TEAM_LOGGER.debug(
                        "Skipping task %s: assignee '%s' in skipAssignees",
                        task.getTaskId(),
                        task.getAssignee()
                );
                continue;
            }
            Optional<TaskTerminationResult> outcome = terminateTaskInternal(task.getTaskId(), TaskStatus.CANCELLED, now);
            if (outcome.isEmpty()) {
                continue;
            }
            cancelledTasks.add(outcome.get().task());
            for (TeamTask unblocked : outcome.get().unblockedTasks()) {
                unblockedById.put(unblocked.getTaskId(), unblocked);
            }
        }
        Set<String> cancelledIds = new HashSet<>();
        for (TeamTask cancelled : cancelledTasks) {
            cancelledIds.add(cancelled.getTaskId());
        }
        List<TeamTask> unblockedTasks = unblockedById.values().stream()
                .filter(task -> !cancelledIds.contains(task.getTaskId()))
                .toList();
        TEAM_LOGGER.info(
                "Cancelled %d tasks for team %s; unblocked %d",
                cancelledTasks.size(),
                teamName,
                unblockedTasks.size()
        );
        return new TaskBulkCancellationResult(cancelledTasks, unblockedTasks);
    }

    private Optional<TaskTerminationResult> terminateTaskInternal(String taskId, TaskStatus newStatus, long now)
            throws SQLException {
        TeamTask task = fetchTask(connection(), taskId);
        if (task == null) {
            TEAM_LOGGER.error("Task %s not found", taskId);
            return Optional.empty();
        }
        if (Objects.equals(task.getStatus(), newStatus.value())) {
            return Optional.of(new TaskTerminationResult(task, List.of()));
        }
        if (!isValidTransition(task.getStatus(), newStatus)) {
            TEAM_LOGGER.error(
                    "Invalid state transition for task %s: %s -> %s",
                    taskId,
                    task.getStatus(),
                    newStatus.value()
            );
            return Optional.empty();
        }

        updateTaskState(connection(), taskId, newStatus.value(), task.getAssignee(), now);
        List<TeamTask> refreshed = resolveDependenciesAndRefresh(connection(), taskId, now);
        TeamTask updatedTask = fetchTask(connection(), taskId);
        return Optional.of(new TaskTerminationResult(updatedTask, refreshed));
    }

    private void stageNewTasks(Connection connection, String teamName, List<NewTaskSpec> newTasks, long now)
            throws SQLException, MutationFailure {
        if (newTasks.isEmpty()) {
            return;
        }
        Set<String> seenIds = new HashSet<>();
        for (NewTaskSpec spec : newTasks) {
            if (!seenIds.add(spec.taskId())) {
                throw new MutationFailure("Duplicate task_id " + spec.taskId() + " in new_tasks");
            }
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO " + currentTaskTable()
                            + " (task_id, team_name, title, content, status, assignee, updated_at) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?)")) {
                statement.setString(1, spec.taskId());
                statement.setString(2, teamName);
                statement.setString(3, spec.title());
                statement.setString(4, spec.content());
                statement.setString(5, spec.initialStatus());
                statement.setString(6, null);
                statement.setLong(7, now);
                statement.executeUpdate();
            } catch (SQLException exception) {
                if (isIntegrityViolation(exception)) {
                    throw new MutationFailure("Integrity error: " + exception.getMessage());
                }
                throw exception;
            }
        }
    }

    private Map<String, TeamTask> loadEndpointsAndValidate(Connection connection, List<DependencyEdge> addEdges)
            throws SQLException, MutationFailure {
        if (addEdges.isEmpty()) {
            return Map.of();
        }
        Set<String> endpointIds = new LinkedHashSet<>();
        for (DependencyEdge edge : addEdges) {
            endpointIds.add(edge.taskId());
            endpointIds.add(edge.dependsOnTaskId());
        }

        Map<String, TeamTask> endpointTasks = fetchTasksByIds(connection, endpointIds);
        for (DependencyEdge edge : addEdges) {
            TeamTask source = endpointTasks.get(edge.taskId());
            TeamTask dependencyTarget = endpointTasks.get(edge.dependsOnTaskId());
            if (source == null) {
                throw new MutationFailure("Task " + edge.taskId() + " not found");
            }
            if (dependencyTarget == null) {
                throw new MutationFailure("Dependency target " + edge.dependsOnTaskId() + " not found");
            }
            if (GraphDatabase.TASK_DEPENDENCY_REJECT_STATUSES.contains(source.getStatus())) {
                throw new MutationFailure(
                        "Cannot add dependency to " + edge.taskId()
                                + " in terminal or executing status: " + source.getStatus()
                );
            }
        }
        return endpointTasks;
    }

    private Set<DependencyEdge> checkCycleAndComputeNewEdges(
            Connection connection,
            String teamName,
            List<DependencyEdge> addEdges) throws SQLException, MutationFailure {
        Set<DependencyEdge> existingEdgeSet = new LinkedHashSet<>();
        Map<String, List<String>> adjacency = new LinkedHashMap<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT task_id, depends_on_task_id FROM " + currentDependencyTable() + " WHERE team_name = ?")) {
            statement.setString(1, teamName);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    DependencyEdge edge = new DependencyEdge(
                            resultSet.getString("task_id"),
                            resultSet.getString("depends_on_task_id")
                    );
                    existingEdgeSet.add(edge);
                    adjacency.computeIfAbsent(edge.taskId(), ignored -> new ArrayList<>()).add(edge.dependsOnTaskId());
                }
            }
        }

        Set<DependencyEdge> newEdgeSet = new LinkedHashSet<>();
        for (DependencyEdge edge : addEdges) {
            if (existingEdgeSet.contains(edge) || newEdgeSet.contains(edge)) {
                continue;
            }
            newEdgeSet.add(edge);
            adjacency.computeIfAbsent(edge.taskId(), ignored -> new ArrayList<>()).add(edge.dependsOnTaskId());
        }

        List<String> cycle = GraphDatabase.detectCycleInAdjacency(adjacency);
        if (cycle != null) {
            throw new MutationFailure("Circular dependency detected: " + String.join(" -> ", cycle));
        }
        return newEdgeSet;
    }

    private void applyNewEdges(
            Connection connection,
            String teamName,
            Set<DependencyEdge> newEdgeSet,
            Map<String, TeamTask> endpointTasks) throws SQLException {
        if (newEdgeSet.isEmpty()) {
            return;
        }
        for (DependencyEdge edge : newEdgeSet) {
            TeamTask dependencyTarget = endpointTasks.get(edge.dependsOnTaskId());
            boolean resolved = GraphDatabase.TASK_TERMINAL_STATUSES.contains(dependencyTarget.getStatus());
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO " + currentDependencyTable()
                            + " (task_id, depends_on_task_id, team_name, resolved) VALUES (?, ?, ?, ?)")) {
                statement.setString(1, edge.taskId());
                statement.setString(2, edge.dependsOnTaskId());
                statement.setString(3, teamName);
                statement.setBoolean(4, resolved);
                statement.executeUpdate();
            }
        }
    }

    private List<TeamTask> resolveDependenciesAndRefresh(Connection connection, String taskId, long now)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE " + currentDependencyTable() + " SET resolved = ? "
                        + "WHERE depends_on_task_id = ? AND resolved = ?")) {
            statement.setBoolean(1, true);
            statement.setString(2, taskId);
            statement.setBoolean(3, false);
            statement.executeUpdate();
        }

        Set<String> downstreamIds = new LinkedHashSet<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT DISTINCT task_id FROM " + currentDependencyTable() + " WHERE depends_on_task_id = ?")) {
            statement.setString(1, taskId);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    downstreamIds.add(resultSet.getString("task_id"));
                }
            }
        }
        return refreshStatusInTransaction(connection, downstreamIds, now);
    }

    private List<TeamTask> refreshStatusInTransaction(Connection connection, Iterable<String> taskIds, long now)
            throws SQLException {
        List<String> uniqueIds = new ArrayList<>();
        for (String taskId : taskIds) {
            if (taskId != null && !taskId.isEmpty() && !uniqueIds.contains(taskId)) {
                uniqueIds.add(taskId);
            }
        }
        if (uniqueIds.isEmpty()) {
            return List.of();
        }

        Map<String, TeamTask> candidates = fetchTasksByIds(connection, uniqueIds);
        if (candidates.isEmpty()) {
            return List.of();
        }

        Map<String, Integer> unresolvedByTask = new HashMap<>();
        String placeholders = placeholders(uniqueIds.size());
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT task_id, COUNT(*) AS unresolved FROM " + currentDependencyTable()
                        + " WHERE task_id IN (" + placeholders + ") AND resolved = ? GROUP BY task_id")) {
            int index = 1;
            for (String taskId : uniqueIds) {
                statement.setString(index++, taskId);
            }
            statement.setBoolean(index, false);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    unresolvedByTask.put(resultSet.getString("task_id"), resultSet.getInt("unresolved"));
                }
            }
        }

        List<TeamTask> refreshed = new ArrayList<>();
        for (String taskId : uniqueIds) {
            TeamTask task = candidates.get(taskId);
            if (task == null) {
                continue;
            }
            TaskStatus currentStatus = TaskStatus.fromValue(task.getStatus());
            if (currentStatus != TaskStatus.PENDING && currentStatus != TaskStatus.BLOCKED) {
                continue;
            }
            int unresolved = unresolvedByTask.getOrDefault(taskId, 0);
            if (currentStatus == TaskStatus.PENDING && unresolved > 0) {
                updateTaskState(connection, taskId, TaskStatus.BLOCKED.value(), task.getAssignee(), now);
                TeamTask updated = fetchTask(connection, taskId);
                refreshed.add(updated);
                TEAM_LOGGER.info("Task %s blocked (%d unresolved deps)", taskId, unresolved);
            } else if (currentStatus == TaskStatus.BLOCKED && unresolved == 0) {
                updateTaskState(connection, taskId, TaskStatus.PENDING.value(), task.getAssignee(), now);
                TeamTask updated = fetchTask(connection, taskId);
                refreshed.add(updated);
                TEAM_LOGGER.info("Task %s unblocked (all deps resolved)", taskId);
            }
        }
        return refreshed;
    }

    private Map<String, TeamTask> fetchTasksByIds(Connection connection, Set<String> taskIds) throws SQLException {
        return fetchTasksByIds(connection, new ArrayList<>(taskIds));
    }

    private Map<String, TeamTask> fetchTasksByIds(Connection connection, List<String> taskIds) throws SQLException {
        if (taskIds.isEmpty()) {
            return Map.of();
        }
        Map<String, TeamTask> tasks = new LinkedHashMap<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT task_id, team_name, title, content, status, assignee, updated_at "
                        + "FROM " + currentTaskTable() + " WHERE task_id IN (" + placeholders(taskIds.size()) + ")")) {
            int index = 1;
            for (String taskId : taskIds) {
                statement.setString(index++, taskId);
            }
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    TeamTask task = mapTask(resultSet);
                    tasks.put(task.getTaskId(), task);
                }
            }
        }
        return tasks;
    }

    private TeamTask fetchTask(Connection connection, String taskId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT task_id, team_name, title, content, status, assignee, updated_at "
                        + "FROM " + currentTaskTable() + " WHERE task_id = ?")) {
            statement.setString(1, taskId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return null;
                }
                return mapTask(resultSet);
            }
        }
    }

    private void updateTaskState(Connection connection, String taskId, String status, String assignee, long updatedAt)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE " + currentTaskTable() + " SET status = ?, assignee = ?, updated_at = ? WHERE task_id = ?")) {
            statement.setString(1, status);
            statement.setString(2, assignee);
            statement.setLong(3, updatedAt);
            statement.setString(4, taskId);
            statement.executeUpdate();
        }
    }

    private void deleteDependencyRowsForTask(Connection connection, String taskId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "DELETE FROM " + currentDependencyTable() + " WHERE task_id = ? OR depends_on_task_id = ?")) {
            statement.setString(1, taskId);
            statement.setString(2, taskId);
            statement.executeUpdate();
        }
    }

    private TeamTask mapTask(ResultSet resultSet) throws SQLException {
        return new TeamTask(
                resultSet.getString("task_id"),
                resultSet.getString("team_name"),
                resultSet.getString("title"),
                resultSet.getString("content"),
                resultSet.getString("status"),
                resultSet.getString("assignee"),
                getNullableLong(resultSet, "updated_at")
        );
    }

    private TeamTaskDependency mapDependency(ResultSet resultSet) throws SQLException {
        return new TeamTaskDependency(
                resultSet.getString("task_id"),
                resultSet.getString("depends_on_task_id"),
                resultSet.getString("team_name"),
                getNullableBoolean(resultSet, "resolved")
        );
    }

    private boolean isValidTransition(String currentStatus, TaskStatus newStatus) {
        try {
            TaskStatus current = TaskStatus.fromValue(currentStatus);
            return StatusTransitions.isValidTransition(current, newStatus, StatusTransitions.TASK_TRANSITIONS);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private <T> CompletableFuture<T> supplyAsyncWithSessionContext(Supplier<T> supplier) {
        String sessionId = AgentTeamsContext.getSessionId();
        return CompletableFuture.supplyAsync(() -> {
            AgentTeamsContext.SessionIdToken token = AgentTeamsContext.setSessionId(sessionId);
            try {
                ensureCurrentSessionTables();
                return supplier.get();
            } finally {
                AgentTeamsContext.resetSessionId(token);
            }
        });
    }

    private <T> T inTransaction(SqlSupplier<T> action) throws SQLException, MutationFailure {
        Connection connection = connection();
        boolean originalAutoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            T result = action.get();
            connection.commit();
            return result;
        } catch (SQLException | MutationFailure exception) {
            connection.rollback();
            throw exception;
        } catch (RuntimeException exception) {
            connection.rollback();
            throw exception;
        } finally {
            connection.setAutoCommit(originalAutoCommit);
        }
    }

    private static DatabaseEngine createDefaultEngine() {
        DatabaseConfig config = new DatabaseConfig();
        config.setConnectionString(":memory:");
        DatabaseEngine engine = new DatabaseEngine(config);
        engine.initialize().join();
        return engine;
    }

    private void ensureInitialized() {
        if (!engine.isInitialized()) {
            engine.initialize().join();
        }
        ensureCurrentSessionTables();
    }

    private void ensureCurrentSessionTables() {
        String sessionId = AgentTeamsContext.getSessionId();
        if (sessionId == null || sessionId.isEmpty()) {
            return;
        }
        String suffix = DatabaseEngine.sanitizeSessionIdForTable(sessionId);
        synchronized (engine) {
            try (Statement statement = connection().createStatement()) {
                statement.execute("""
                        CREATE TABLE IF NOT EXISTS "team_task_%s" (
                            task_id VARCHAR(255) PRIMARY KEY,
                            team_name VARCHAR(255) NOT NULL,
                            title CLOB NOT NULL,
                            content CLOB NOT NULL,
                            status VARCHAR(255) NOT NULL,
                            assignee VARCHAR(255),
                            updated_at BIGINT
                        )
                        """.formatted(suffix));
                statement.execute("""
                        CREATE TABLE IF NOT EXISTS "team_task_dependency_%s" (
                            task_id VARCHAR(255) NOT NULL,
                            depends_on_task_id VARCHAR(255) NOT NULL,
                            team_name VARCHAR(255) NOT NULL,
                            resolved BOOLEAN DEFAULT FALSE,
                            PRIMARY KEY (task_id, depends_on_task_id)
                        )
                        """.formatted(suffix));
            } catch (SQLException exception) {
                throw new RuntimeException("Failed to ensure task session tables", exception);
            }
        }
    }

    private Connection connection() {
        Connection connection = engine.getConnection();
        if (connection == null) {
            throw new IllegalStateException("Database engine is not initialized");
        }
        return connection;
    }

    private String currentTaskTable() {
        return quotedTableName("team_task_" + currentSessionSuffix());
    }

    private String currentDependencyTable() {
        return quotedTableName("team_task_dependency_" + currentSessionSuffix());
    }

    private String currentSessionSuffix() {
        String sessionId = AgentTeamsContext.getSessionId();
        if (sessionId == null || sessionId.isEmpty()) {
            throw new IllegalStateException("No session_id in context");
        }
        return DatabaseEngine.sanitizeSessionIdForTable(sessionId);
    }

    private String quotedTableName(String rawTableName) {
        return "\"" + rawTableName.replace("\"", "\"\"") + "\"";
    }

    private boolean isIntegrityViolation(SQLException exception) {
        String sqlState = exception.getSQLState();
        if (sqlState != null && sqlState.startsWith("23")) {
            return true;
        }
        String message = exception.getMessage();
        return message != null && message.toLowerCase().contains("unique");
    }

    private String placeholders(int count) {
        return String.join(", ", java.util.Collections.nCopies(count, "?"));
    }

    private Long getNullableLong(ResultSet resultSet, String columnName) throws SQLException {
        long value = resultSet.getLong(columnName);
        return resultSet.wasNull() ? null : value;
    }

    private Boolean getNullableBoolean(ResultSet resultSet, String columnName) throws SQLException {
        boolean value = resultSet.getBoolean(columnName);
        return resultSet.wasNull() ? null : value;
    }
}

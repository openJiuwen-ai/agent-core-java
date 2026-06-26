/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.tools;

import com.openjiuwen.agent_teams.schema.GraphMutationResult;
import com.openjiuwen.agent_teams.schema.NewTaskSpec;
import com.openjiuwen.agent_teams.schema.TeamRole;
import com.openjiuwen.agent_teams.schema.status.ExecutionStatus;
import com.openjiuwen.agent_teams.schema.status.MemberMode;
import com.openjiuwen.agent_teams.schema.status.MemberStatus;
import com.openjiuwen.agent_teams.schema.status.StatusTransitions;
import com.openjiuwen.agent_teams.schema.status.TaskStatus;
import com.openjiuwen.agent_teams.tools.database.GraphDatabase;
import com.openjiuwen.agent_teams.tools.database.TaskDao;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.common.logging.LoggerProtocol;

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

/**
 * In-memory team database that mirrors the TeamDatabase DAO surface.
 *
 * <p>Mirrors Python's {@code InMemoryTeamDatabase} in
 * {@code openjiuwen/agent_teams/tools/memory_database.py}.</p>
 */
public class InMemoryTeamDatabase {

    private static final LoggerProtocol TEAM_LOGGER = Loggers.TEAM;

    private final Object lock = new Object();
    private final MemoryDatabaseConfig config;
    private final Map<String, Team> teams = new LinkedHashMap<>();
    private final Map<String, TeamMember> members = new LinkedHashMap<>();
    private final Map<String, TeamTask> tasks = new LinkedHashMap<>();
    private final List<TeamTaskDependency> taskDeps = new ArrayList<>();
    private final List<TeamMessage> messages = new ArrayList<>();
    private final Map<ReadStatusKey, MessageReadStatus> readStatus = new LinkedHashMap<>();

    private boolean initialized = true;

    public InMemoryTeamDatabase() {
        this(new MemoryDatabaseConfig());
    }

    public InMemoryTeamDatabase(MemoryDatabaseConfig config) {
        this.config = config == null ? new MemoryDatabaseConfig() : config;
    }

    public MemoryDatabaseConfig getConfig() {
        return config;
    }

    public boolean isInitialized() {
        return initialized;
    }

    public InMemoryTeamDatabase getTeam() {
        return this;
    }

    public InMemoryTeamDatabase getMember() {
        return this;
    }

    public InMemoryTeamDatabase getTask() {
        return this;
    }

    public InMemoryTeamDatabase getMessage() {
        return this;
    }

    public static long getCurrentTime() {
        return System.currentTimeMillis();
    }

    public CompletableFuture<Void> initialize() {
        initialized = true;
        return CompletableFuture.completedFuture(null);
    }

    public CompletableFuture<Void> createCurSessionTables() {
        return CompletableFuture.completedFuture(null);
    }

    public CompletableFuture<Void> dropCurSessionTables() {
        synchronized (lock) {
            clearDynamicState();
        }
        return CompletableFuture.completedFuture(null);
    }

    public CompletableFuture<RuntimeCleanupResult> cleanupAllRuntimeState() {
        RuntimeCleanupResult result;
        synchronized (lock) {
            boolean hadDynamicState = !tasks.isEmpty() || !taskDeps.isEmpty()
                    || !messages.isEmpty() || !readStatus.isEmpty();
            boolean hadStaticState = !teams.isEmpty() || !members.isEmpty();
            clearDynamicState();
            teams.clear();
            members.clear();
            result = new RuntimeCleanupResult(
                    hadDynamicState ? List.of("memory_dynamic_state") : List.of(),
                    hadStaticState ? List.of("team_info", "team_member") : List.of()
            );
        }
        return CompletableFuture.completedFuture(result);
    }

    public CompletableFuture<List<String>> dropSessionTablesById(String sessionId) {
        List<String> cleared;
        synchronized (lock) {
            boolean hadData = !tasks.isEmpty() || !taskDeps.isEmpty()
                    || !messages.isEmpty() || !readStatus.isEmpty();
            clearDynamicState();
            cleared = hadData ? List.of("memory_dynamic_state") : List.of();
        }
        if (!cleared.isEmpty()) {
            TEAM_LOGGER.info("Cleared all in-memory dynamic data for session %s", sessionId);
        }
        return CompletableFuture.completedFuture(cleared);
    }

    public CompletableFuture<Void> close() {
        initialized = false;
        return CompletableFuture.completedFuture(null);
    }

    public CompletableFuture<Boolean> createTeam(
            String teamName,
            String displayName,
            String leaderMemberName,
            String desc,
            String prompt) {
        synchronized (lock) {
            if (teams.containsKey(teamName)) {
                TEAM_LOGGER.error("Team %s already exists", teamName);
                return CompletableFuture.completedFuture(false);
            }
            long now = getCurrentTime();
            teams.put(teamName, new Team(teamName, displayName, leaderMemberName, desc, prompt, now, now));
            TEAM_LOGGER.info("Team %s created", teamName);
            return CompletableFuture.completedFuture(true);
        }
    }

    public CompletableFuture<Optional<Team>> getTeam(String teamName) {
        synchronized (lock) {
            return CompletableFuture.completedFuture(Optional.ofNullable(teams.get(teamName)));
        }
    }

    public CompletableFuture<Boolean> deleteTeam(String teamName) {
        synchronized (lock) {
            if (!teams.containsKey(teamName)) {
                TEAM_LOGGER.debug("Team %s not found for deletion", teamName);
                return CompletableFuture.completedFuture(false);
            }
            teams.remove(teamName);
            members.entrySet().removeIf(entry -> Objects.equals(entry.getValue().getTeamName(), teamName));
            tasks.entrySet().removeIf(entry -> Objects.equals(entry.getValue().getTeamName(), teamName));
            taskDeps.removeIf(dep -> Objects.equals(dep.getTeamName(), teamName));
            messages.removeIf(message -> Objects.equals(message.getTeamName(), teamName));
            readStatus.entrySet().removeIf(entry -> Objects.equals(entry.getValue().getTeamName(), teamName));
            TEAM_LOGGER.info("Team %s deleted", teamName);
            return CompletableFuture.completedFuture(true);
        }
    }

    public CompletableFuture<Long> getTeamUpdatedAt(String teamName) {
        synchronized (lock) {
            Team team = teams.get(teamName);
            Long updatedAt = team == null ? null : team.getUpdatedAt();
            return CompletableFuture.completedFuture(updatedAt == null ? 0L : updatedAt);
        }
    }

    public CompletableFuture<Boolean> forceDeleteTeamSession(String teamName) {
        boolean deleted;
        synchronized (lock) {
            deleted = teams.containsKey(teamName);
            if (deleted) {
                teams.remove(teamName);
                members.entrySet().removeIf(entry -> Objects.equals(entry.getValue().getTeamName(), teamName));
                tasks.entrySet().removeIf(entry -> Objects.equals(entry.getValue().getTeamName(), teamName));
                taskDeps.removeIf(dep -> Objects.equals(dep.getTeamName(), teamName));
                messages.removeIf(message -> Objects.equals(message.getTeamName(), teamName));
                readStatus.entrySet().removeIf(entry -> Objects.equals(entry.getValue().getTeamName(), teamName));
            }
            clearDynamicState();
        }
        TEAM_LOGGER.info("Force deleted team session data for %s", teamName);
        return CompletableFuture.completedFuture(deleted);
    }

    public CompletableFuture<Boolean> createMember(
            String memberName,
            String teamName,
            String displayName,
            String agentCard,
            String status,
            String role,
            String desc,
            String executionStatus,
            String mode,
            String prompt,
            String modelRefJson) {
        synchronized (lock) {
            if (members.containsKey(memberName)) {
                TEAM_LOGGER.error("Member %s already exists", memberName);
                return CompletableFuture.completedFuture(false);
            }
            members.put(memberName, new TeamMember(
                    memberName,
                    teamName,
                    displayName,
                    desc,
                    agentCard,
                    status,
                    executionStatus,
                    mode == null ? MemberMode.BUILD_MODE.value() : mode,
                    role == null ? TeamRole.TEAMMATE.value() : role,
                    prompt,
                    modelRefJson,
                    getCurrentTime()
            ));
            TEAM_LOGGER.info("Member %s created", memberName);
            return CompletableFuture.completedFuture(true);
        }
    }

    public CompletableFuture<Boolean> createMember(
            String memberName,
            String teamName,
            String displayName,
            String agentCard,
            String status) {
        return createMember(
                memberName,
                teamName,
                displayName,
                agentCard,
                status,
                TeamRole.TEAMMATE.value(),
                null,
                null,
                MemberMode.BUILD_MODE.value(),
                null,
                null
        );
    }

    public CompletableFuture<Boolean> isHumanAgent(String teamName, String memberName) {
        synchronized (lock) {
            TeamMember member = members.get(memberName);
            return CompletableFuture.completedFuture(member != null
                    && Objects.equals(member.getTeamName(), teamName)
                    && Objects.equals(member.getRole(), TeamRole.HUMAN_AGENT.value()));
        }
    }

    public CompletableFuture<List<String>> listHumanAgentNames(String teamName) {
        synchronized (lock) {
            List<String> result = new ArrayList<>();
            for (TeamMember member : members.values()) {
                if (Objects.equals(member.getTeamName(), teamName)
                        && Objects.equals(member.getRole(), TeamRole.HUMAN_AGENT.value())) {
                    result.add(member.getMemberName());
                }
            }
            return CompletableFuture.completedFuture(result);
        }
    }

    public CompletableFuture<Optional<TeamMember>> getMember(String memberName, String teamName) {
        synchronized (lock) {
            TeamMember member = members.get(memberName);
            if (member == null || !Objects.equals(member.getTeamName(), teamName)) {
                return CompletableFuture.completedFuture(Optional.empty());
            }
            return CompletableFuture.completedFuture(Optional.of(member));
        }
    }

    public CompletableFuture<List<TeamMember>> getTeamMembers(String teamName, String status) {
        synchronized (lock) {
            List<TeamMember> result = new ArrayList<>();
            for (TeamMember member : members.values()) {
                if (Objects.equals(member.getTeamName(), teamName)
                        && (status == null || Objects.equals(member.getStatus(), status))) {
                    result.add(member);
                }
            }
            return CompletableFuture.completedFuture(result);
        }
    }

    public CompletableFuture<Long> getMembersMaxUpdatedAt(String teamName) {
        synchronized (lock) {
            long max = 0L;
            for (TeamMember member : members.values()) {
                if (Objects.equals(member.getTeamName(), teamName) && member.getUpdatedAt() != null) {
                    max = Math.max(max, member.getUpdatedAt());
                }
            }
            return CompletableFuture.completedFuture(max);
        }
    }

    public CompletableFuture<Boolean> updateMemberStatus(String memberName, String teamName, String status) {
        synchronized (lock) {
            TeamMember member = members.get(memberName);
            if (member == null || !Objects.equals(member.getTeamName(), teamName)) {
                TEAM_LOGGER.error("Member %s not found in team %s", memberName, teamName);
                return CompletableFuture.completedFuture(false);
            }
            if (!isValidMemberTransition(member.getStatus(), status)) {
                TEAM_LOGGER.error(
                        "Invalid state transition for member %s: %s -> %s",
                        memberName,
                        member.getStatus(),
                        status
                );
                return CompletableFuture.completedFuture(false);
            }
            member.setStatus(status);
            TEAM_LOGGER.debug("Member %s status updated to %s", memberName, status);
            return CompletableFuture.completedFuture(true);
        }
    }

    public CompletableFuture<Boolean> updateMemberExecutionStatus(
            String memberName,
            String teamName,
            String executionStatus) {
        synchronized (lock) {
            TeamMember member = members.get(memberName);
            if (member == null || !Objects.equals(member.getTeamName(), teamName)) {
                TEAM_LOGGER.error("Member %s not found in team %s", memberName, teamName);
                return CompletableFuture.completedFuture(false);
            }
            if (!isValidExecutionTransition(member.getExecutionStatus(), executionStatus)) {
                TEAM_LOGGER.error(
                        "Invalid state transition for member %s: %s -> %s",
                        memberName,
                        member.getExecutionStatus(),
                        executionStatus
                );
                return CompletableFuture.completedFuture(false);
            }
            member.setExecutionStatus(executionStatus);
            TEAM_LOGGER.debug("Member %s execution status updated to %s", memberName, executionStatus);
            return CompletableFuture.completedFuture(true);
        }
    }

    public CompletableFuture<Boolean> createTask(
            String taskId,
            String teamName,
            String title,
            String content,
            String status) {
        synchronized (lock) {
            if (tasks.containsKey(taskId)) {
                TEAM_LOGGER.error("Task %s already exists", taskId);
                return CompletableFuture.completedFuture(false);
            }
            tasks.put(taskId, new TeamTask(taskId, teamName, title, content, status, null, getCurrentTime()));
            TEAM_LOGGER.info("Task %s created", taskId);
            return CompletableFuture.completedFuture(true);
        }
    }

    public CompletableFuture<Optional<TeamTask>> getTask(String taskId) {
        synchronized (lock) {
            return CompletableFuture.completedFuture(Optional.ofNullable(tasks.get(taskId)));
        }
    }

    public CompletableFuture<List<TeamTask>> getTeamTasks(String teamName, String status) {
        synchronized (lock) {
            List<TeamTask> result = new ArrayList<>();
            for (TeamTask task : tasks.values()) {
                if (Objects.equals(task.getTeamName(), teamName)
                        && (status == null || Objects.equals(task.getStatus(), status))) {
                    result.add(task);
                }
            }
            return CompletableFuture.completedFuture(result);
        }
    }

    public CompletableFuture<List<TeamTask>> getTasksByAssignee(String teamName, String assigneeId, String status) {
        synchronized (lock) {
            List<TeamTask> result = new ArrayList<>();
            for (TeamTask task : tasks.values()) {
                if (Objects.equals(task.getTeamName(), teamName)
                        && Objects.equals(task.getAssignee(), assigneeId)
                        && (status == null || Objects.equals(task.getStatus(), status))) {
                    result.add(task);
                }
            }
            return CompletableFuture.completedFuture(result);
        }
    }

    public CompletableFuture<Boolean> assignTask(String taskId, String memberName) {
        synchronized (lock) {
            TeamTask task = tasks.get(taskId);
            if (task == null) {
                TEAM_LOGGER.error("Task %s not found", taskId);
                return CompletableFuture.completedFuture(false);
            }
            if (task.getAssignee() != null) {
                TEAM_LOGGER.warning("Task %s already assigned to %s", taskId, task.getAssignee());
                return CompletableFuture.completedFuture(false);
            }
            if (!isValidTaskTransition(task.getStatus(), TaskStatus.CLAIMED)) {
                TEAM_LOGGER.error(
                        "Invalid state transition for task %s: %s -> %s",
                        taskId,
                        task.getStatus(),
                        TaskStatus.CLAIMED.value()
                );
                return CompletableFuture.completedFuture(false);
            }
            task.setAssignee(memberName);
            task.setStatus(TaskStatus.CLAIMED.value());
            task.setUpdatedAt(getCurrentTime());
            TEAM_LOGGER.info("Task %s assigned to %s (status=claimed)", taskId, memberName);
            return CompletableFuture.completedFuture(true);
        }
    }

    public CompletableFuture<Boolean> claimTask(String taskId, String memberName) {
        synchronized (lock) {
            TeamTask task = tasks.get(taskId);
            if (task == null) {
                TEAM_LOGGER.error("Task %s not found", taskId);
                return CompletableFuture.completedFuture(false);
            }
            if (task.getAssignee() != null) {
                TEAM_LOGGER.warning("Task %s is already claimed by member %s", taskId, task.getAssignee());
                return CompletableFuture.completedFuture(false);
            }
            if (!isValidTaskTransition(task.getStatus(), TaskStatus.CLAIMED)) {
                TEAM_LOGGER.error(
                        "Invalid state transition for task %s: %s -> %s",
                        taskId,
                        task.getStatus(),
                        TaskStatus.CLAIMED.value()
                );
                return CompletableFuture.completedFuture(false);
            }
            task.setStatus(TaskStatus.CLAIMED.value());
            task.setAssignee(memberName);
            task.setUpdatedAt(getCurrentTime());
            TEAM_LOGGER.info("Task %s claimed by member %s", taskId, memberName);
            return CompletableFuture.completedFuture(true);
        }
    }

    public CompletableFuture<Optional<TeamTask>> resetTask(String taskId) {
        synchronized (lock) {
            TeamTask task = tasks.get(taskId);
            if (task == null) {
                TEAM_LOGGER.error("Task %s not found", taskId);
                return CompletableFuture.completedFuture(Optional.empty());
            }
            if (!Objects.equals(task.getStatus(), TaskStatus.CLAIMED.value())) {
                TEAM_LOGGER.error("Cannot reset task %s with status %s, only CLAIMED tasks can be reset",
                        taskId, task.getStatus());
                return CompletableFuture.completedFuture(Optional.empty());
            }
            if (!isValidTaskTransition(task.getStatus(), TaskStatus.PENDING)) {
                TEAM_LOGGER.error(
                        "Invalid state transition for task %s: %s -> %s",
                        taskId,
                        task.getStatus(),
                        TaskStatus.PENDING.value()
                );
                return CompletableFuture.completedFuture(Optional.empty());
            }
            task.setStatus(TaskStatus.PENDING.value());
            task.setAssignee(null);
            task.setUpdatedAt(getCurrentTime());
            return CompletableFuture.completedFuture(Optional.of(task));
        }
    }

    public CompletableFuture<Optional<TeamTask>> approvePlanTask(String taskId) {
        synchronized (lock) {
            TeamTask task = tasks.get(taskId);
            if (task == null) {
                TEAM_LOGGER.error("Task %s not found", taskId);
                return CompletableFuture.completedFuture(Optional.empty());
            }
            if (!isValidTaskTransition(task.getStatus(), TaskStatus.PLAN_APPROVED)) {
                TEAM_LOGGER.error(
                        "Invalid state transition for task %s: %s -> %s",
                        taskId,
                        task.getStatus(),
                        TaskStatus.PLAN_APPROVED.value()
                );
                return CompletableFuture.completedFuture(Optional.empty());
            }
            task.setStatus(TaskStatus.PLAN_APPROVED.value());
            task.setUpdatedAt(getCurrentTime());
            TEAM_LOGGER.info("Task %s approved from CLAIMED to PLAN_APPROVED", taskId);
            return CompletableFuture.completedFuture(Optional.of(task));
        }
    }

    public CompletableFuture<Boolean> updateTaskStatus(String taskId, String status) {
        synchronized (lock) {
            TeamTask task = tasks.get(taskId);
            if (task == null) {
                TEAM_LOGGER.error("Task %s not found", taskId);
                return CompletableFuture.completedFuture(false);
            }
            TaskStatus next = TaskStatus.fromValue(status);
            if (!isValidTaskTransition(task.getStatus(), next)) {
                TEAM_LOGGER.error("Invalid state transition for task %s: %s -> %s", taskId, task.getStatus(), status);
                return CompletableFuture.completedFuture(false);
            }
            task.setStatus(status);
            task.setUpdatedAt(getCurrentTime());
            if (TaskStatus.COMPLETED == next) {
                for (TeamTaskDependency dep : taskDeps) {
                    if (Objects.equals(dep.getDependsOnTaskId(), taskId) && !Boolean.TRUE.equals(dep.getResolved())) {
                        dep.setResolved(true);
                    }
                }
            }
            TEAM_LOGGER.info("Task %s status updated to %s", taskId, status);
            return CompletableFuture.completedFuture(true);
        }
    }

    public CompletableFuture<Boolean> updateTask(String taskId, String title, String content) {
        synchronized (lock) {
            TeamTask task = tasks.get(taskId);
            if (task == null) {
                TEAM_LOGGER.error("Task %s not found", taskId);
                return CompletableFuture.completedFuture(false);
            }
            if (Objects.equals(task.getStatus(), TaskStatus.CLAIMED.value())
                    || Objects.equals(task.getStatus(), TaskStatus.PLAN_APPROVED.value())) {
                TEAM_LOGGER.error("Cannot update task %s because it is currently %s", taskId, task.getStatus());
                return CompletableFuture.completedFuture(false);
            }
            if (title != null) {
                task.setTitle(title);
            }
            if (content != null) {
                task.setContent(content);
            }
            return CompletableFuture.completedFuture(true);
        }
    }

    public CompletableFuture<GraphMutationResult> mutateDependencyGraph(
            String teamName,
            List<NewTaskSpec> newTasks,
            List<TaskDao.DependencyEdge> addEdges) {
        synchronized (lock) {
            List<NewTaskSpec> nextTasks = newTasks == null ? List.of() : newTasks;
            List<TaskDao.DependencyEdge> nextEdges = addEdges == null ? List.of() : addEdges;
            if (nextTasks.isEmpty() && nextEdges.isEmpty()) {
                return CompletableFuture.completedFuture(GraphMutationResult.success(List.of()));
            }

            long now = getCurrentTime();
            Set<String> seenNewIds = new HashSet<>();
            for (NewTaskSpec spec : nextTasks) {
                if (!seenNewIds.add(spec.taskId())) {
                    return CompletableFuture.completedFuture(
                            GraphMutationResult.fail("Duplicate task_id " + spec.taskId() + " in new_tasks")
                    );
                }
                if (tasks.containsKey(spec.taskId())) {
                    return CompletableFuture.completedFuture(
                            GraphMutationResult.fail("Task " + spec.taskId() + " already exists")
                    );
                }
            }

            Map<String, String> stagedStatus = new HashMap<>();
            for (NewTaskSpec spec : nextTasks) {
                stagedStatus.put(spec.taskId(), spec.initialStatus());
            }
            for (TaskDao.DependencyEdge edge : nextEdges) {
                String sourceStatus = stagedStatus.get(edge.taskId());
                if (sourceStatus == null && tasks.containsKey(edge.taskId())) {
                    sourceStatus = tasks.get(edge.taskId()).getStatus();
                }
                String targetStatus = stagedStatus.get(edge.dependsOnTaskId());
                if (targetStatus == null && tasks.containsKey(edge.dependsOnTaskId())) {
                    targetStatus = tasks.get(edge.dependsOnTaskId()).getStatus();
                }
                if (sourceStatus == null) {
                    return CompletableFuture.completedFuture(GraphMutationResult.fail("Task " + edge.taskId() + " not found"));
                }
                if (targetStatus == null) {
                    return CompletableFuture.completedFuture(
                            GraphMutationResult.fail("Dependency target " + edge.dependsOnTaskId() + " not found")
                    );
                }
                if (!seenNewIds.contains(edge.taskId())
                        && GraphDatabase.TASK_DEPENDENCY_REJECT_STATUSES.contains(sourceStatus)) {
                    return CompletableFuture.completedFuture(GraphMutationResult.fail(
                            "Cannot add dependency to " + edge.taskId()
                                    + " in terminal or executing status: " + sourceStatus
                    ));
                }
            }

            Set<TaskDao.DependencyEdge> existingEdges = new LinkedHashSet<>();
            Map<String, List<String>> adjacency = new LinkedHashMap<>();
            for (TeamTaskDependency dep : taskDeps) {
                if (!Objects.equals(dep.getTeamName(), teamName)) {
                    continue;
                }
                TaskDao.DependencyEdge edge = new TaskDao.DependencyEdge(dep.getTaskId(), dep.getDependsOnTaskId());
                existingEdges.add(edge);
                adjacency.computeIfAbsent(edge.taskId(), ignored -> new ArrayList<>()).add(edge.dependsOnTaskId());
            }

            Set<TaskDao.DependencyEdge> newEdgeSet = new LinkedHashSet<>();
            for (TaskDao.DependencyEdge edge : nextEdges) {
                if (existingEdges.contains(edge) || newEdgeSet.contains(edge)) {
                    continue;
                }
                newEdgeSet.add(edge);
                adjacency.computeIfAbsent(edge.taskId(), ignored -> new ArrayList<>()).add(edge.dependsOnTaskId());
            }

            List<String> cycle = GraphDatabase.detectCycleInAdjacency(adjacency);
            if (cycle != null) {
                return CompletableFuture.completedFuture(
                        GraphMutationResult.fail("Circular dependency detected: " + String.join(" -> ", cycle))
                );
            }

            for (NewTaskSpec spec : nextTasks) {
                tasks.put(spec.taskId(), new TeamTask(
                        spec.taskId(),
                        teamName,
                        spec.title(),
                        spec.content(),
                        spec.initialStatus(),
                        null,
                        now
                ));
            }
            for (TaskDao.DependencyEdge edge : newEdgeSet) {
                String dependencyStatus = stagedStatus.get(edge.dependsOnTaskId());
                if (dependencyStatus == null) {
                    dependencyStatus = tasks.get(edge.dependsOnTaskId()).getStatus();
                }
                taskDeps.add(new TeamTaskDependency(
                        edge.taskId(),
                        edge.dependsOnTaskId(),
                        teamName,
                        GraphDatabase.TASK_TERMINAL_STATUSES.contains(dependencyStatus)
                ));
            }

            Set<String> affectedIds = new LinkedHashSet<>();
            for (NewTaskSpec spec : nextTasks) {
                affectedIds.add(spec.taskId());
            }
            for (TaskDao.DependencyEdge edge : newEdgeSet) {
                affectedIds.add(edge.taskId());
            }
            List<TeamTask> refreshed = refreshStatusForTasks(affectedIds, now);
            return CompletableFuture.completedFuture(GraphMutationResult.success(new ArrayList<>(refreshed)));
        }
    }

    public CompletableFuture<Boolean> addTaskWithBidirectionalDependencies(
            String taskId,
            String teamName,
            String title,
            String content,
            String status,
            List<String> dependencies,
            List<String> dependentTaskIds) {
        List<TaskDao.DependencyEdge> edges = new ArrayList<>();
        if (dependencies != null) {
            for (String dependency : dependencies) {
                edges.add(new TaskDao.DependencyEdge(taskId, dependency));
            }
        }
        if (dependentTaskIds != null) {
            for (String dependentTaskId : dependentTaskIds) {
                edges.add(new TaskDao.DependencyEdge(dependentTaskId, taskId));
            }
        }
        return mutateDependencyGraph(
                teamName,
                List.of(new NewTaskSpec(taskId, title, content, status)),
                edges
        ).thenApply(GraphMutationResult::ok);
    }

    public CompletableFuture<List<TeamTaskDependency>> getTaskDependencies(String taskId) {
        synchronized (lock) {
            List<TeamTaskDependency> result = new ArrayList<>();
            for (TeamTaskDependency dep : taskDeps) {
                if (Objects.equals(dep.getTaskId(), taskId)) {
                    result.add(dep);
                }
            }
            return CompletableFuture.completedFuture(result);
        }
    }

    public CompletableFuture<Integer> getUnresolvedDependenciesCount(String taskId) {
        synchronized (lock) {
            int count = 0;
            for (TeamTaskDependency dep : taskDeps) {
                if (Objects.equals(dep.getTaskId(), taskId) && !Boolean.TRUE.equals(dep.getResolved())) {
                    count++;
                }
            }
            return CompletableFuture.completedFuture(count);
        }
    }

    public CompletableFuture<List<TeamTask>> getTasksDependingOn(String dependsOnTaskId) {
        synchronized (lock) {
            List<TeamTask> result = new ArrayList<>();
            for (TeamTaskDependency dep : taskDeps) {
                if (Objects.equals(dep.getDependsOnTaskId(), dependsOnTaskId) && tasks.containsKey(dep.getTaskId())) {
                    result.add(tasks.get(dep.getTaskId()));
                }
            }
            return CompletableFuture.completedFuture(result);
        }
    }

    public CompletableFuture<Boolean> deleteTask(String taskId) {
        synchronized (lock) {
            if (!tasks.containsKey(taskId)) {
                TEAM_LOGGER.debug("Task %s not found for deletion", taskId);
                return CompletableFuture.completedFuture(false);
            }
            tasks.remove(taskId);
            taskDeps.removeIf(dep -> Objects.equals(dep.getTaskId(), taskId)
                    || Objects.equals(dep.getDependsOnTaskId(), taskId));
            TEAM_LOGGER.info("Task %s deleted", taskId);
            return CompletableFuture.completedFuture(true);
        }
    }

    public CompletableFuture<Optional<TaskDao.TaskTerminationResult>> cancelTask(String taskId) {
        synchronized (lock) {
            return CompletableFuture.completedFuture(terminateTaskLocked(taskId, TaskStatus.CANCELLED, getCurrentTime()));
        }
    }

    public CompletableFuture<TaskDao.TaskBulkCancellationResult> cancelAllTasks(
            String teamName,
            Set<String> skipAssignees) {
        synchronized (lock) {
            Set<String> skipped = skipAssignees == null ? Set.of() : skipAssignees;
            long now = getCurrentTime();
            List<TeamTask> cancelled = new ArrayList<>();
            Map<String, TeamTask> unblockedById = new LinkedHashMap<>();
            Set<String> terminal = Set.of(TaskStatus.CANCELLED.value(), TaskStatus.COMPLETED.value());
            List<String> candidateIds = new ArrayList<>();
            for (TeamTask task : tasks.values()) {
                if (Objects.equals(task.getTeamName(), teamName)
                        && !terminal.contains(task.getStatus())
                        && (task.getAssignee() == null || !skipped.contains(task.getAssignee()))) {
                    candidateIds.add(task.getTaskId());
                }
            }
            for (String taskId : candidateIds) {
                Optional<TaskDao.TaskTerminationResult> outcome = terminateTaskLocked(taskId, TaskStatus.CANCELLED, now);
                if (outcome.isEmpty()) {
                    continue;
                }
                cancelled.add(outcome.get().task());
                for (TeamTask unblocked : outcome.get().unblockedTasks()) {
                    unblockedById.put(unblocked.getTaskId(), unblocked);
                }
            }
            Set<String> cancelledIds = new HashSet<>();
            for (TeamTask task : cancelled) {
                cancelledIds.add(task.getTaskId());
            }
            List<TeamTask> unblocked = new ArrayList<>();
            for (Map.Entry<String, TeamTask> entry : unblockedById.entrySet()) {
                if (!cancelledIds.contains(entry.getKey())) {
                    unblocked.add(entry.getValue());
                }
            }
            return CompletableFuture.completedFuture(new TaskDao.TaskBulkCancellationResult(cancelled, unblocked));
        }
    }

    public CompletableFuture<Optional<TaskDao.TaskTerminationResult>> completeTask(String taskId) {
        synchronized (lock) {
            return CompletableFuture.completedFuture(terminateTaskLocked(taskId, TaskStatus.COMPLETED, getCurrentTime()));
        }
    }

    public CompletableFuture<List<TeamTask>> verifyAndFixTaskConsistency(String teamName) {
        synchronized (lock) {
            List<String> blockedIds = new ArrayList<>();
            for (TeamTask task : tasks.values()) {
                if (Objects.equals(task.getTeamName(), teamName)
                        && Objects.equals(task.getStatus(), TaskStatus.BLOCKED.value())) {
                    blockedIds.add(task.getTaskId());
                }
            }
            return CompletableFuture.completedFuture(refreshStatusForTasks(blockedIds, getCurrentTime()));
        }
    }

    public CompletableFuture<Optional<TeamMessage>> getMessage(String messageId) {
        synchronized (lock) {
            for (TeamMessage message : messages) {
                if (Objects.equals(message.getMessageId(), messageId)) {
                    return CompletableFuture.completedFuture(Optional.of(message));
                }
            }
            return CompletableFuture.completedFuture(Optional.empty());
        }
    }

    public CompletableFuture<Boolean> createMessage(
            String messageId,
            String teamName,
            String fromMemberName,
            String content,
            String toMemberName,
            boolean broadcast,
            boolean isRead) {
        synchronized (lock) {
            for (TeamMessage message : messages) {
                if (Objects.equals(message.getMessageId(), messageId)) {
                    TEAM_LOGGER.error("Message %s already exists", messageId);
                    return CompletableFuture.completedFuture(false);
                }
            }
            messages.add(new TeamMessage(
                    messageId,
                    teamName,
                    fromMemberName,
                    toMemberName,
                    content,
                    getCurrentTime(),
                    broadcast,
                    broadcast ? null : isRead
            ));
            TEAM_LOGGER.debug("Message %s created", messageId);
            return CompletableFuture.completedFuture(true);
        }
    }

    public CompletableFuture<List<TeamMessage>> getMessages(
            String teamName,
            String toMemberName,
            boolean unreadOnly,
            String fromMemberName) {
        synchronized (lock) {
            List<TeamMessage> result = new ArrayList<>();
            for (TeamMessage message : messages) {
                if (!Objects.equals(message.getTeamName(), teamName)
                        || !Objects.equals(message.getToMemberName(), toMemberName)
                        || Boolean.TRUE.equals(message.getBroadcast())) {
                    continue;
                }
                if (fromMemberName != null && !Objects.equals(message.getFromMemberName(), fromMemberName)) {
                    continue;
                }
                if (unreadOnly && !Boolean.FALSE.equals(message.getIsRead())) {
                    continue;
                }
                result.add(message);
            }
            result.sort(Comparator.comparing(TeamMessage::getTimestamp));
            return CompletableFuture.completedFuture(result);
        }
    }

    public CompletableFuture<List<TeamMessage>> getBroadcastMessages(
            String teamName,
            String memberName,
            boolean unreadOnly,
            String fromMemberName) {
        synchronized (lock) {
            List<TeamMessage> result = new ArrayList<>();
            for (TeamMessage message : messages) {
                if (!Objects.equals(message.getTeamName(), teamName)
                        || !Boolean.TRUE.equals(message.getBroadcast())
                        || Objects.equals(message.getFromMemberName(), memberName)) {
                    continue;
                }
                if (fromMemberName != null && !Objects.equals(message.getFromMemberName(), fromMemberName)) {
                    continue;
                }
                result.add(message);
            }
            result.sort(Comparator.comparing(TeamMessage::getTimestamp));
            if (!unreadOnly) {
                return CompletableFuture.completedFuture(result);
            }
            MessageReadStatus status = readStatus.get(new ReadStatusKey(memberName, teamName));
            if (status == null) {
                return CompletableFuture.completedFuture(result);
            }
            Long watermark = status.getReadAt();
            List<TeamMessage> unread = new ArrayList<>();
            for (TeamMessage message : result) {
                if (watermark == null || message.getTimestamp() > watermark) {
                    unread.add(message);
                }
            }
            return CompletableFuture.completedFuture(unread);
        }
    }

    public CompletableFuture<List<TeamMessage>> getTeamMessages(String teamName, Boolean broadcast) {
        synchronized (lock) {
            List<TeamMessage> result = new ArrayList<>();
            for (TeamMessage message : messages) {
                if (Objects.equals(message.getTeamName(), teamName)
                        && (broadcast == null || Objects.equals(message.getBroadcast(), broadcast))) {
                    result.add(message);
                }
            }
            result.sort(Comparator.comparing(TeamMessage::getTimestamp));
            return CompletableFuture.completedFuture(result);
        }
    }

    public CompletableFuture<Boolean> hasUnreadMessages(String teamName) {
        return hasUnreadMessages(teamName, true);
    }

    public CompletableFuture<Boolean> hasUnreadMessages(String teamName, boolean includeBroadcast) {
        synchronized (lock) {
            for (TeamMessage message : messages) {
                if (Objects.equals(message.getTeamName(), teamName)
                        && !Boolean.TRUE.equals(message.getBroadcast())
                        && !Boolean.TRUE.equals(message.getIsRead())) {
                    return CompletableFuture.completedFuture(true);
                }
            }
            if (!includeBroadcast) {
                return CompletableFuture.completedFuture(false);
            }
            List<TeamMessage> broadcasts = new ArrayList<>();
            for (TeamMessage message : messages) {
                if (Objects.equals(message.getTeamName(), teamName) && Boolean.TRUE.equals(message.getBroadcast())) {
                    broadcasts.add(message);
                }
            }
            if (broadcasts.isEmpty()) {
                return CompletableFuture.completedFuture(false);
            }
            List<String> memberNames = new ArrayList<>();
            for (TeamMember member : members.values()) {
                if (Objects.equals(member.getTeamName(), teamName)) {
                    memberNames.add(member.getMemberName());
                }
            }
            for (String memberName : memberNames) {
                MessageReadStatus status = readStatus.get(new ReadStatusKey(memberName, teamName));
                Long watermark = status == null ? null : status.getReadAt();
                for (TeamMessage message : broadcasts) {
                    if (Objects.equals(message.getFromMemberName(), memberName)) {
                        continue;
                    }
                    if (watermark == null || message.getTimestamp() > watermark) {
                        return CompletableFuture.completedFuture(true);
                    }
                }
            }
            return CompletableFuture.completedFuture(false);
        }
    }

    public CompletableFuture<Boolean> markMessageRead(String messageId, String memberName) {
        synchronized (lock) {
            TeamMessage target = null;
            for (TeamMessage message : messages) {
                if (Objects.equals(message.getMessageId(), messageId)) {
                    target = message;
                    break;
                }
            }
            if (target == null) {
                TEAM_LOGGER.error("Message %s not found", messageId);
                return CompletableFuture.completedFuture(false);
            }
            if (!members.containsKey(memberName)) {
                TEAM_LOGGER.error("Member %s not found", memberName);
                return CompletableFuture.completedFuture(false);
            }
            if (Boolean.TRUE.equals(target.getBroadcast())) {
                ReadStatusKey key = new ReadStatusKey(memberName, target.getTeamName());
                MessageReadStatus current = readStatus.get(key);
                if (current == null) {
                    readStatus.put(key, new MessageReadStatus(memberName, target.getTeamName(), target.getTimestamp()));
                } else {
                    current.setReadAt(target.getTimestamp());
                }
            } else {
                target.setIsRead(true);
            }
            TEAM_LOGGER.debug("Message %s marked as read by %s", messageId, memberName);
            return CompletableFuture.completedFuture(true);
        }
    }

    private void clearDynamicState() {
        tasks.clear();
        taskDeps.clear();
        messages.clear();
        readStatus.clear();
    }

    private boolean isValidMemberTransition(String currentStatus, String nextStatus) {
        try {
            return StatusTransitions.isValidTransition(
                    MemberStatus.fromValue(currentStatus),
                    MemberStatus.fromValue(nextStatus),
                    StatusTransitions.MEMBER_TRANSITIONS
            );
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private boolean isValidExecutionTransition(String currentStatus, String nextStatus) {
        try {
            return StatusTransitions.isValidTransition(
                    ExecutionStatus.fromValue(currentStatus),
                    ExecutionStatus.fromValue(nextStatus),
                    StatusTransitions.EXECUTION_TRANSITIONS
            );
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private boolean isValidTaskTransition(String currentStatus, TaskStatus nextStatus) {
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

    private List<TeamTask> refreshStatusForTasks(Iterable<String> taskIds, long now) {
        Set<String> uniqueIds = new LinkedHashSet<>();
        for (String taskId : taskIds) {
            if (taskId != null && !taskId.isEmpty()) {
                uniqueIds.add(taskId);
            }
        }
        List<TeamTask> refreshed = new ArrayList<>();
        for (String taskId : uniqueIds) {
            TeamTask task = tasks.get(taskId);
            if (task == null) {
                continue;
            }
            if (!Objects.equals(task.getStatus(), TaskStatus.PENDING.value())
                    && !Objects.equals(task.getStatus(), TaskStatus.BLOCKED.value())) {
                continue;
            }
            int unresolved = 0;
            for (TeamTaskDependency dep : taskDeps) {
                if (Objects.equals(dep.getTaskId(), taskId) && !Boolean.TRUE.equals(dep.getResolved())) {
                    unresolved++;
                }
            }
            if (Objects.equals(task.getStatus(), TaskStatus.PENDING.value()) && unresolved > 0) {
                task.setStatus(TaskStatus.BLOCKED.value());
                task.setUpdatedAt(now);
                refreshed.add(task);
                TEAM_LOGGER.info("Task %s blocked (%d unresolved deps)", taskId, unresolved);
            } else if (Objects.equals(task.getStatus(), TaskStatus.BLOCKED.value()) && unresolved == 0) {
                task.setStatus(TaskStatus.PENDING.value());
                task.setUpdatedAt(now);
                refreshed.add(task);
                TEAM_LOGGER.info("Task %s unblocked (all deps resolved)", taskId);
            }
        }
        return refreshed;
    }

    private Optional<TaskDao.TaskTerminationResult> terminateTaskLocked(
            String taskId,
            TaskStatus nextStatus,
            long now) {
        if (nextStatus != TaskStatus.COMPLETED && nextStatus != TaskStatus.CANCELLED) {
            throw new IllegalArgumentException("_terminate_task_locked expects a terminal status, got " + nextStatus);
        }
        TeamTask task = tasks.get(taskId);
        if (task == null) {
            TEAM_LOGGER.error("Task %s not found", taskId);
            return Optional.empty();
        }
        if (Objects.equals(task.getStatus(), nextStatus.value())) {
            return Optional.of(new TaskDao.TaskTerminationResult(task, List.of()));
        }
        if (!isValidTaskTransition(task.getStatus(), nextStatus)) {
            TEAM_LOGGER.error(
                    "Invalid state transition for task %s: %s -> %s",
                    taskId,
                    task.getStatus(),
                    nextStatus.value()
            );
            return Optional.empty();
        }
        task.setStatus(nextStatus.value());
        task.setUpdatedAt(now);

        Set<String> downstreamIds = new LinkedHashSet<>();
        for (TeamTaskDependency dep : taskDeps) {
            if (Objects.equals(dep.getDependsOnTaskId(), taskId)) {
                if (!Boolean.TRUE.equals(dep.getResolved())) {
                    dep.setResolved(true);
                }
                downstreamIds.add(dep.getTaskId());
            }
        }
        List<TeamTask> refreshed = refreshStatusForTasks(downstreamIds, now);
        return Optional.of(new TaskDao.TaskTerminationResult(task, refreshed));
    }

    /**
     * Java representation of Python's {@code tuple[list[str], list[str]]} cleanup result.
     *
     * <p>Mirrors Python's {@code InMemoryTeamDatabase.cleanup_all_runtime_state} return value in
     * {@code openjiuwen/agent_teams/tools/memory_database.py}.</p>
     */
    public record RuntimeCleanupResult(List<String> deletedTables, List<String> clearedTables) {
        public RuntimeCleanupResult {
            deletedTables = deletedTables == null ? List.of() : List.copyOf(deletedTables);
            clearedTables = clearedTables == null ? List.of() : List.copyOf(clearedTables);
        }
    }

    private record ReadStatusKey(String memberName, String teamName) {
    }
}

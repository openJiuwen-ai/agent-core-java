/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentteams.tools.database;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * Drop-in replacement for SQLite-backed TeamDatabase using plain concurrent
 * data structures for single-process mode. Same public DAO interface as
 * TeamDatabase so callers can use it transparently.
 *
 * <p>Mirrors Python tools/memory_database.py InMemoryTeamDatabase.</p>
 */
public class InMemoryTeamDatabase {

    private final Map<String, Map<String, Object>> teams = new ConcurrentHashMap<>();
    private final Map<String, MemberRecord> members = new ConcurrentHashMap<>();
    private final Map<String, TaskRecord> tasks = new ConcurrentHashMap<>();
    private final List<TaskDependencyRecord> taskDeps = new CopyOnWriteArrayList<>();
    private final List<MessageRecord> messages = new CopyOnWriteArrayList<>();
    private final AtomicLong clock = new AtomicLong(System.currentTimeMillis());

    public final TeamDao team = new TeamDao();
    public final MemberDao member = new MemberDao();
    public final TaskDao task = new TaskDao();
    public final MessageDao message = new MessageDao();

    public void initialize() {
    }

    public void createCurSessionTables() {
    }

    public void dropCurSessionTables() {
        tasks.clear();
        taskDeps.clear();
        messages.clear();
    }

    public void close() {
        teams.clear();
        members.clear();
        tasks.clear();
        taskDeps.clear();
        messages.clear();
    }

    public class TeamDao {
        public void createTeam(TeamRecord record) {
            Map<String, Object> teamInfo = new LinkedHashMap<>();
            teamInfo.put("team_name", record.getTeamName());
            teamInfo.put("display_name", record.getDisplayName());
            teamInfo.put("desc", record.getDesc());
            teamInfo.put("updated_at", currentTimeMillis());
            teams.put(record.getTeamName(), teamInfo);
        }

        public TeamRecord getTeam(String teamName) {
            Map<String, Object> t = teams.get(teamName);
            if (t == null) {
                return null;
            }
            TeamRecord record = new TeamRecord();
            record.setTeamName((String) t.get("team_name"));
            record.setDisplayName((String) t.get("display_name"));
            record.setDesc((String) t.get("desc"));
            record.setUpdatedAt((Long) t.getOrDefault("updated_at", 0L));
            return record;
        }

        public void deleteTeam(String teamName) {
            teams.remove(teamName);
            members.entrySet().removeIf(e -> teamName.equals(e.getValue().getTeamName()));
            tasks.entrySet().removeIf(e -> teamName.equals(e.getValue().getTeamName()));
            messages.removeIf(m -> teamName.equals(m.getTeamName()));
            taskDeps.removeIf(d -> {
                TaskRecord source = tasks.get(d.getTaskId());
                return source != null && teamName.equals(source.getTeamName());
            });
        }

        public long getTeamUpdatedAt(String teamName) {
            TeamRecord t = getTeam(teamName);
            return t != null ? t.getUpdatedAt() : 0L;
        }
    }

    public class MemberDao {
        public void createMember(MemberRecord record) {
            String key = key(record.getTeamName(), record.getMemberName());
            members.put(key, record);
        }

        public MemberRecord getMember(String memberName, String teamName) {
            return members.get(key(teamName, memberName));
        }

        public List<MemberRecord> getTeamMembers(String teamName) {
            return members.values().stream()
                    .filter(m -> teamName.equals(m.getTeamName()))
                    .collect(Collectors.toList());
        }

        public long getMembersMaxUpdatedAt(String teamName) {
            return members.values().stream()
                    .filter(m -> teamName.equals(m.getTeamName()))
                    .mapToLong(MemberRecord::getUpdatedAt)
                    .max()
                    .orElse(0L);
        }

        public boolean updateMemberStatus(String memberName, String teamName, String status) {
            MemberRecord record = getMember(memberName, teamName);
            if (record == null) {
                return false;
            }
            record.setStatus(status);
            record.setUpdatedAt(currentTimeMillis());
            members.put(key(teamName, memberName), record);
            return true;
        }

        public boolean updateMemberExecutionStatus(
                String memberName, String teamName, String executionStatus) {
            MemberRecord record = getMember(memberName, teamName);
            if (record == null) {
                return false;
            }
            record.setExecutionStatus(executionStatus);
            record.setUpdatedAt(currentTimeMillis());
            members.put(key(teamName, memberName), record);
            return true;
        }
    }

    public class TaskDao {
        public void createTask(TaskRecord record) {
            tasks.put(record.getTaskId(), record);
        }

        public TaskRecord getTask(String taskId) {
            return tasks.get(taskId);
        }

        public List<TaskRecord> getTeamTasks(String teamName, String status) {
            return tasks.values().stream()
                    .filter(t -> teamName.equals(t.getTeamName()))
                    .filter(t -> status == null || status.equals(t.getStatus()))
                    .collect(Collectors.toList());
        }

        public boolean claimTask(String taskId, String memberName) {
            TaskRecord t = tasks.get(taskId);
            if (t == null || !"pending".equals(t.getStatus())) {
                return false;
            }
            t.setStatus("claimed");
            t.setAssignee(memberName);
            t.setUpdatedAt(currentTimeMillis());
            return true;
        }

        public boolean resetTask(String taskId) {
            TaskRecord t = tasks.get(taskId);
            if (t == null || !"claimed".equals(t.getStatus())) {
                return false;
            }
            t.setStatus("pending");
            t.setAssignee(null);
            t.setUpdatedAt(currentTimeMillis());
            return true;
        }

        public boolean approvePlanTask(String taskId) {
            TaskRecord t = tasks.get(taskId);
            if (t == null || !"claimed".equals(t.getStatus())) {
                return false;
            }
            t.setStatus("plan_approved");
            t.setUpdatedAt(currentTimeMillis());
            return true;
        }

        public TaskMutationResult completeTaskResult(String taskId) {
            TaskRecord t = tasks.get(taskId);
            if (t == null) {
                return TaskMutationResult.fail("Task not found: " + taskId);
            }
            String status = t.getStatus();
            if (!"claimed".equals(status) && !"plan_approved".equals(status)) {
                return TaskMutationResult.fail(
                        "Cannot complete task '" + taskId + "' from status '" + status + "'");
            }
            t.setStatus("completed");
            t.setAssignee(null);
            long now = currentTimeMillis();
            t.setUpdatedAt(now);
            refreshDependentTasks(taskId, now);
            return TaskMutationResult.success(taskId);
        }

        public TaskMutationResult cancelTaskResult(String taskId) {
            TaskRecord t = tasks.get(taskId);
            if (t == null) {
                return TaskMutationResult.fail("Task not found: " + taskId);
            }
            t.setStatus("cancelled");
            t.setAssignee(null);
            long now = currentTimeMillis();
            t.setUpdatedAt(now);
            refreshDependentTasks(taskId, now);
            return TaskMutationResult.success(taskId);
        }

        public TaskMutationResult cancelAllTasksResult(String teamName) {
            List<TaskRecord> cancelled = getTeamTasks(teamName, null).stream()
                    .filter(t -> !"completed".equals(t.getStatus())
                            && !"cancelled".equals(t.getStatus()))
                    .peek(t -> {
                        t.setStatus("cancelled");
                        t.setUpdatedAt(currentTimeMillis());
                    })
                    .collect(Collectors.toList());
            TaskRecord placeholder = new TaskRecord();
            placeholder.setTaskId("cancelled_" + cancelled.size());
            return TaskMutationResult.success(placeholder.getTaskId());
        }

        public boolean updateTask(String taskId, String title, String content) {
            TaskRecord t = tasks.get(taskId);
            if (t == null) {
                return false;
            }
            if (title != null) {
                t.setTitle(title);
            }
            if (content != null) {
                t.setContent(content);
            }
            return true;
        }

        public List<TaskDependencyRecord> getDependencies(String taskId) {
            return taskDeps.stream()
                    .filter(d -> taskId.equals(d.getTaskId()))
                    .collect(Collectors.toList());
        }

        public boolean addDependency(String taskId, String dependsOnId) {
            boolean exists = taskDeps.stream().anyMatch(
                    d -> taskId.equals(d.getTaskId())
                            && dependsOnId.equals(d.getDependsOnTaskId()));
            if (exists) {
                return true;
            }
            TaskDependencyRecord dep = TaskDependencyRecord.builder()
                    .taskId(taskId)
                    .dependsOnTaskId(dependsOnId)
                    .isResolved(false)
                    .build();
            taskDeps.add(dep);
            refreshDependentTasks(taskId, currentTimeMillis());
            return true;
        }

        public List<TaskRecord> getTasksByAssignee(String assignee, String status) {
            return tasks.values().stream()
                    .filter(t -> assignee.equals(t.getAssignee()))
                    .filter(t -> status == null || status.equals(t.getStatus()))
                    .collect(Collectors.toList());
        }

        public GraphMutationResult mutateDependencyGraph(
                String teamName, List<TaskDependencyRecord> newDeps) {
            Map<String, List<String>> adjacency = new LinkedHashMap<>();
            for (TaskDependencyRecord dep : taskDeps) {
                adjacency.computeIfAbsent(dep.getTaskId(), k -> new ArrayList<>())
                        .add(dep.getDependsOnTaskId());
            }
            for (TaskDependencyRecord dep : newDeps) {
                adjacency.computeIfAbsent(dep.getTaskId(), k -> new ArrayList<>())
                        .add(dep.getDependsOnTaskId());
            }
            List<String> cycle = GraphUtils.detectCycleInAdjacency(adjacency);
            if (cycle != null) {
                return GraphMutationResult.fail(
                        "Cycle detected: " + String.join(" -> ", cycle));
            }
            taskDeps.addAll(newDeps);
            return GraphMutationResult.success(List.of());
        }

        private void refreshDependentTasks(String dependsOnId, long now) {
            List<String> dependent = taskDeps.stream()
                    .filter(d -> dependsOnId.equals(d.getDependsOnTaskId()))
                    .map(TaskDependencyRecord::getTaskId)
                    .collect(Collectors.toList());
            for (String taskId : dependent) {
                TaskRecord t = tasks.get(taskId);
                if (t == null) {
                    continue;
                }
                if (!"pending".equals(t.getStatus()) && !"blocked".equals(t.getStatus())) {
                    continue;
                }
                boolean allResolved = getDependencies(taskId).stream()
                        .allMatch(d -> d.isResolved() || isTaskTerminal(d.getDependsOnTaskId()));
                if (allResolved && "blocked".equals(t.getStatus())) {
                    t.setStatus("pending");
                    t.setUpdatedAt(now);
                } else if (!allResolved && "pending".equals(t.getStatus())) {
                    t.setStatus("blocked");
                    t.setUpdatedAt(now);
                }
            }
        }

        private boolean isTaskTerminal(String taskId) {
            TaskRecord t = tasks.get(taskId);
            return t != null && GraphUtils.TASK_TERMINAL_STATUSES.contains(t.getStatus());
        }
    }

    public class MessageDao {
        public void createMessage(MessageRecord record) {
            messages.add(record);
        }

        public List<MessageRecord> getTeamMessages(String teamName) {
            return messages.stream()
                    .filter(m -> teamName.equals(m.getTeamName()))
                    .collect(Collectors.toList());
        }

        public List<MessageRecord> getMessages(String memberName, String teamName) {
            return messages.stream()
                    .filter(m -> teamName.equals(m.getTeamName()))
                    .filter(m -> memberName.equals(m.getToMemberName())
                            || m.getToMemberName() == null || m.getToMemberName().isBlank())
                    .collect(Collectors.toList());
        }

        public MessageRecord getMessage(String messageId) {
            return messages.stream()
                    .filter(m -> messageId.equals(m.getMessageId()))
                    .findFirst()
                    .orElse(null);
        }
    }

    private long currentTimeMillis() {
        return clock.incrementAndGet();
    }

    private static String key(String teamName, String memberName) {
        return teamName + "::" + memberName;
    }
}

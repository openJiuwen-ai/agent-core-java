/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentteams.tools.database;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
 *
 * @since 2026/7/9
 */
public class InMemoryTeamDatabase {

    private final Map<String, Map<String, Object>> teams = new ConcurrentHashMap<>();
    private final Map<String, MemberRecord> members = new ConcurrentHashMap<>();
    private final Map<String, TaskRecord> tasks = new ConcurrentHashMap<>();
    private final List<TaskDependencyRecord> taskDeps = new CopyOnWriteArrayList<>();
    private final List<MessageRecord> messages = new CopyOnWriteArrayList<>();
    private final AtomicLong clock = new AtomicLong(System.currentTimeMillis());

    /** Team data access object. */
    public final TeamDao team = new TeamDao();

    /** Member data access object. */
    public final MemberDao member = new MemberDao();

    /** Task data access object. */
    public final TaskDao task = new TaskDao();

    /** Message data access object. */
    public final MessageDao message = new MessageDao();

    /**
     * No-op initialization for API parity with TeamDatabase.
     */
    public void initialize() {
    }

    /**
     * No-op table creation for API parity with TeamDatabase.
     */
    public void createCurSessionTables() {
    }

    /**
     * Drop all in-memory session data (tasks, dependencies, messages).
     */
    public void dropCurSessionTables() {
        tasks.clear();
        taskDeps.clear();
        messages.clear();
    }

    /**
     * Release all in-memory data.
     */
    public void close() {
        teams.clear();
        members.clear();
        tasks.clear();
        taskDeps.clear();
        messages.clear();
    }

    /** Data access object for team records. */
    public class TeamDao {
        /**
         * Create a new team record.
         *
         * @param record team data to persist
         */
        public void createTeam(TeamRecord record) {
            Map<String, Object> teamRecord = new LinkedHashMap<>();
            teamRecord.put("team_name", record.getTeamName());
            teamRecord.put("display_name", record.getDisplayName());
            teamRecord.put("desc", record.getDesc());
            teamRecord.put("updated_at", currentTimeMillis());
            teams.put(record.getTeamName(), teamRecord);
        }

        /**
         * Retrieve a team record by name.
         *
         * @param teamName unique team identifier
         * @return an {@link Optional} containing the team record,
         *     or {@link Optional#empty()} if not found
         */
        public Optional<TeamRecord> getTeam(String teamName) {
            Map<String, Object> t = teams.get(teamName);
            if (t == null) {
                return Optional.empty();
            }
            TeamRecord record = new TeamRecord();
            record.setTeamName((String) t.get("team_name"));
            record.setDisplayName((String) t.get("display_name"));
            record.setDesc((String) t.get("desc"));
            record.setUpdatedAt((Long) t.getOrDefault("updated_at", 0L));
            return Optional.of(record);
        }

        /**
         * Delete a team and cascade-remove its members, tasks, messages, and dependencies.
         *
         * @param teamName team to delete
         */
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

        /**
         * Get the last-updated timestamp of a team.
         *
         * @param teamName team identifier
         * @return timestamp in milliseconds, or {@code 0} if team not found
         */
        public long getTeamUpdatedAt(String teamName) {
            Optional<TeamRecord> t = getTeam(teamName);
            return t.map(TeamRecord::getUpdatedAt).orElse(0L);
        }
    }

    /** Data access object for member records. */
    public class MemberDao {
        /**
         * Create or replace a member record.
         *
         * @param record member data to persist
         */
        public void createMember(MemberRecord record) {
            String key = key(record.getTeamName(), record.getMemberName());
            members.put(key, record);
        }

        /**
         * Retrieve a single member by name within a team.
         *
         * @param memberName member identifier
         * @param teamName   team identifier
         * @return member record, or {@code null} if not found
         */
        public MemberRecord getMember(String memberName, String teamName) {
            return members.get(key(teamName, memberName));
        }

        /**
         * List all members belonging to a team.
         *
         * @param teamName team identifier
         * @return list of member records (may be empty)
         */
        public List<MemberRecord> getTeamMembers(String teamName) {
            return members.values().stream()
                    .filter(m -> teamName.equals(m.getTeamName()))
                    .collect(Collectors.toList());
        }

        /**
         * Get the maximum updated-at timestamp among team members.
         *
         * @param teamName team identifier
         * @return max timestamp in milliseconds, or {@code 0} if no members
         */
        public long getMembersMaxUpdatedAt(String teamName) {
            return members.values().stream()
                    .filter(m -> teamName.equals(m.getTeamName()))
                    .mapToLong(MemberRecord::getUpdatedAt)
                    .max()
                    .orElse(0L);
        }

        /**
         * Update a member's status.
         *
         * @param memberName member identifier
         * @param teamName   team identifier
         * @param status     new status value
         * @return {@code true} if updated, {@code false} if member not found
         */
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

        /**
         * Update a member's execution status.
         *
         * @param memberName      member identifier
         * @param teamName        team identifier
         * @param executionStatus new execution status value
         * @return {@code true} if updated, {@code false} if member not found
         */
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

        /**
         * Atomic compare-and-swap status transition.
         *
         * <p>Mirrors Python {@code member_dao.try_transition_member_status}.
         * Check-then-set inside the same synchronized call -- sufficient
         * for the in-memory Map backing. Succeeds only when the row's
         * current status equals {@code fromStatus}.</p>
         *
         * @param memberName member identifier
         * @param teamName team identifier
         * @param fromStatus expected current status
         * @param toStatus target status to transition to
         * @return {@code true} if the transition succeeded, {@code false} if member not found or status mismatch
         */
        public boolean tryTransitionMemberStatus(
                String memberName, String teamName, String fromStatus, String toStatus) {
            MemberRecord record = getMember(memberName, teamName);
            if (record == null) {
                return false;
            }
            if (fromStatus == null || !fromStatus.equals(record.getStatus())) {
                return false;
            }
            record.setStatus(toStatus);
            record.setUpdatedAt(currentTimeMillis());
            members.put(key(teamName, memberName), record);
            return true;
        }

        /**
         * Probe {@code team_member.role} for a single member.
         *
         * <p>Mirrors Python {@code member_dao.is_human_agent}.</p>
         *
         * @param memberName member identifier
         * @param teamName team identifier
         * @return {@code true} if the member's role is {@code human_agent}
         */
        public boolean isHumanAgent(String memberName, String teamName) {
            MemberRecord record = getMember(memberName, teamName);
            if (record == null || record.getRole() == null) {
                return false;
            }
            return "human_agent".equals(record.getRole());
        }

    }

    /** Data access object for task records. */
    public class TaskDao {
        /**
         * Create or replace a task record.
         *
         * @param record task data to persist
         */
        public void createTask(TaskRecord record) {
            tasks.put(record.getTaskId(), record);
        }

        /**
         * Retrieve a task by its identifier.
         *
         * @param taskId unique task identifier
         * @return task record, or {@code null} if not found
         */
        public TaskRecord getTask(String taskId) {
            return tasks.get(taskId);
        }

        /**
         * List tasks belonging to a team, optionally filtered by status.
         *
         * @param teamName team identifier
         * @param status   status filter, or {@code null} for all statuses
         * @return list of matching task records
         */
        public List<TaskRecord> getTeamTasks(String teamName, String status) {
            return tasks.values().stream()
                    .filter(t -> teamName.equals(t.getTeamName()))
                    .filter(t -> status == null || status.equals(t.getStatus()))
                    .collect(Collectors.toList());
        }

        /**
         * Claim a pending task for a member.
         *
         * @param taskId     task to claim
         * @param memberName claiming member
         * @return {@code true} if claimed, {@code false} if task not found or not pending
         */
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

        /**
         * Reset a claimed task back to pending.
         *
         * @param taskId task to reset
         * @return {@code true} if reset, {@code false} if task not found or not claimed
         */
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

        /**
         * Approve the plan for a claimed task.
         *
         * @param taskId task whose plan to approve
         * @return {@code true} if approved, {@code false} if task not found or not claimed
         */
        public boolean approvePlanTask(String taskId) {
            TaskRecord t = tasks.get(taskId);
            if (t == null || !"claimed".equals(t.getStatus())) {
                return false;
            }
            t.setStatus("plan_approved");
            t.setUpdatedAt(currentTimeMillis());
            return true;
        }

        /**
         * Complete a task and refresh dependent tasks.
         *
         * @param taskId task to complete
         * @return mutation result indicating success or failure
         */
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

        /**
         * Cancel a task and refresh dependent tasks.
         *
         * @param taskId task to cancel
         * @return mutation result indicating success or failure
         */
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

        /**
         * Cancel all non-terminal tasks in a team.
         *
         * @param teamName team whose tasks to cancel
         * @return mutation result with count of cancelled tasks
         */
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

        /**
         * Update a task's title and/or content.
         *
         * @param taskId  task to update
         * @param title   new title, or {@code null} to keep existing
         * @param content new content, or {@code null} to keep existing
         * @return {@code true} if updated, {@code false} if task not found
         */
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

        /**
         * Get all dependency records for a task.
         *
         * @param taskId task identifier
         * @return list of dependency records
         */
        public List<TaskDependencyRecord> getDependencies(String taskId) {
            return taskDeps.stream()
                    .filter(d -> taskId.equals(d.getTaskId()))
                    .collect(Collectors.toList());
        }

        /**
         * Add a dependency edge between two tasks.
         *
         * @param taskId       task that depends on another
         * @param dependsOnId  task that must complete first
         * @return {@code true} if added or already existed
         */
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

        /**
         * List tasks assigned to a specific member, optionally filtered by status.
         *
         * @param assignee member name
         * @param status   status filter, or {@code null} for all
         * @return list of matching task records
         */
        public List<TaskRecord> getTasksByAssignee(String assignee, String status) {
            return tasks.values().stream()
                    .filter(t -> assignee.equals(t.getAssignee()))
                    .filter(t -> status == null || status.equals(t.getStatus()))
                    .collect(Collectors.toList());
        }

        /**
         * Merge new dependency edges and detect cycles before persisting.
         *
         * @param teamName team identifier (reserved for future filtering)
         * @param newDeps  dependency edges to add
         * @return success with resolved tasks, or failure with cycle description
         */
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
                } else {
                    // no-op: status already correct for current resolution state
                }
            }
        }

        private boolean isTaskTerminal(String taskId) {
            TaskRecord t = tasks.get(taskId);
            return t != null && GraphUtils.TASK_TERMINAL_STATUSES.contains(t.getStatus());
        }
    }

    /** Data access object for message records. */
    public class MessageDao {
        /**
         * Persist a new message record.
         *
         * @param record message data to persist
         */
        public void createMessage(MessageRecord record) {
            messages.add(record);
        }

        /**
         * List all messages in a team.
         *
         * @param teamName team identifier
         * @return list of message records
         */
        public List<MessageRecord> getTeamMessages(String teamName) {
            return messages.stream()
                    .filter(m -> teamName.equals(m.getTeamName()))
                    .collect(Collectors.toList());
        }

        /**
         * List messages visible to a member (direct + broadcast).
         *
         * @param memberName member identifier
         * @param teamName   team identifier
         * @return list of visible message records
         */
        public List<MessageRecord> getMessages(String memberName, String teamName) {
            return messages.stream()
                    .filter(m -> teamName.equals(m.getTeamName()))
                    .filter(m -> memberName.equals(m.getToMemberName())
                            || m.getToMemberName() == null || m.getToMemberName().isBlank())
                    .collect(Collectors.toList());
        }

        /**
         * Retrieve a single message by its identifier.
         *
         * @param messageId unique message identifier
         * @return message record, or {@code null} if not found
         */
        public MessageRecord getMessage(String messageId) {
            return messages.stream()
                    .filter(m -> messageId.equals(m.getMessageId()))
                    .findFirst()
                    .orElse(null);
        }

        /**
         * Mark one message read for a member.
         *
         * <p>Mirrors Python {@code message_dao.mark_message_read}. Broadcast
         * messages track a per-(team, member) read-at timestamp (we store it
         * on the message itself is wrong for broadcasts; instead we flip the
         * row's {@code isRead} for direct messages, and for broadcasts we
         * rely on the caller's getBroadcastMessages readAt filter -- but to
         * keep parity with TeamDatabase.MessageDao we set isRead true for
         * direct messages and ignore broadcast read tracking here (the
         * InMemory variant is a single-process test shim).</p>
         *
         * @param messageId unique message identifier
         * @param memberName the member marking the message as read
         * @return {@code true} if the message was marked read, {@code false} if not found or not applicable
         */
        public boolean markMessageRead(String messageId, String memberName) {
            MessageRecord record = getMessage(messageId);
            if (record == null) {
                return false;
            }
            if (record.isBroadcast()) {
                if (memberName == null || memberName.isBlank() || "user".equals(memberName)) {
                    return false;
                }

                // Broadcast read-state is per-(team, member); store a marker
                // timestamp on the record so getBroadcastMessages can filter.
                // InMemory variant stores readAt on the record itself.
                record.setRead(true);
                return true;
            }
            record.setRead(true);
            return true;
        }

        /**
         * Batch mark a list of messages read for one member.
         *
         * <p>Mirrors Python {@code message_dao.mark_messages_read}.</p>
         *
         * @param messageIds the message ids to mark read
         * @param memberName the member marking the messages as read
         * @return the number of messages successfully marked read
         */
        public int markMessagesRead(List<String> messageIds, String memberName) {
            if (messageIds == null || messageIds.isEmpty()) {
                return 0;
            }
            int count = 0;
            for (String messageId : messageIds) {
                if (markMessageRead(messageId, memberName)) {
                    count++;
                }
            }
            return count;
        }
    }

    private long currentTimeMillis() {
        return clock.incrementAndGet();
    }

    private static String key(String teamName, String memberName) {
        return teamName + "::" + memberName;
    }
}

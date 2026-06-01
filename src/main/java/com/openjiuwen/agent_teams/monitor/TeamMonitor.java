/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.monitor;

import com.openjiuwen.agent_teams.agent.TeamAgent;
import com.openjiuwen.agent_teams.schema.TeamRole;
import com.openjiuwen.agent_teams.schema.task.TaskStatus;
import com.openjiuwen.agent_teams.schema.message.MessageRecord;
import com.openjiuwen.agent_teams.spawn.SpawnContext;
import com.openjiuwen.agent_teams.tools.Team;
import com.openjiuwen.agent_teams.tools.TeamBackend;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * Team monitor that observes a leader's TeamAgent.
 * <p>
 * Provides query APIs for team info, members, tasks, and messages,
 * plus a real-time event stream via an async iterator.
 * <p>
 * Lifecycle:
 * <pre>
 * TeamMonitor monitor = createMonitor(teamAgent);
 * monitor.start();                    // begin listening
 * while (monitor.hasNextEvent()) {
 *     MonitorEvent evt = monitor.nextEvent();
 *     ...                              // consume events
 * }
 * monitor.stop();                      // clean up
 * </pre>
 * <p>
 * Mirrors Python's {@code TeamMonitor} in
 * {@code openjiuwen.agent_teams.monitor.team_monitor}.
 */
public class TeamMonitor {

    private static final Logger logger = Logger.getLogger(TeamMonitor.class.getName());

    private final String teamId;
    private final String sessionId;
    private final Object db;  // TeamDatabase
    private final Object teamAgent;  // TeamAgent
    private final ConcurrentLinkedQueue<MonitorEvent> eventQueue;
    private final AtomicBoolean started;
    private final Consumer<Object> listener;

    /**
     * Initialize the monitor.
     *
     * @param teamId    Team identifier
     * @param sessionId Session identifier for topic routing
     * @param db        TeamDatabase instance for state queries
     * @param teamAgent Leader TeamAgent to register event listener on
     */
    public TeamMonitor(String teamId, String sessionId, Object db, Object teamAgent) {
        this.teamId = teamId;
        this.sessionId = sessionId;
        this.db = db;
        this.teamAgent = teamAgent;
        this.eventQueue = new ConcurrentLinkedQueue<>();
        this.started = new AtomicBoolean(false);
        this.listener = this::onEvent;
    }

    public String getTeamId() {
        return teamId;
    }

    public String getSessionId() {
        return sessionId;
    }

    // ------------------------------------------------------------------
    // Lifecycle
    // ------------------------------------------------------------------

    /**
     * Start monitoring by registering as an event listener on the leader.
     */
    public void start() {
        if (started.get()) {
            return;
        }
        // Register event listener (duck-typed call)
        addEventListener(listener);
        started.set(true);
        logger.info("TeamMonitor started for team " + teamId);
    }

    /**
     * Stop monitoring, unregister listener, and terminate the event stream.
     */
    public void stop() {
        if (!started.get()) {
            return;
        }
        removeEventListener(listener);
        started.set(false);
        logger.info("TeamMonitor stopped for team " + teamId);
    }

    // ------------------------------------------------------------------
    // Query APIs
    // ------------------------------------------------------------------

    /**
     * Query team basic information.
     *
     * @return TeamInfo or null if the team does not exist
     */
    public CompletableFuture<TeamInfo> getTeamInfo() {
        return queryTeamAsync(teamId).thenApply(team -> 
            team != null ? TeamInfo.fromInternal(team) : null
        );
    }

    /**
     * Query team member list.
     *
     * @param status Optional MemberStatus value to filter by
     * @return List of MemberInfo
     */
    public CompletableFuture<List<MemberInfo>> getMembers(String status) {
        return queryTeamMembersAsync(teamId, status).thenApply(members -> {
            List<MemberInfo> result = new ArrayList<>();
            for (Object m : members) {
                result.add(MemberInfo.fromInternal(m));
            }
            return result;
        });
    }

    /**
     * Query a single member by ID.
     *
     * @param memberName Member identifier
     * @return MemberInfo or null if not found
     */
    public CompletableFuture<MemberInfo> getMember(String memberName) {
        return queryMemberAsync(memberName, teamId).thenApply(member ->
            member != null ? MemberInfo.fromInternal(member) : null
        );
    }

    /**
     * Query task list.
     *
     * @param status Optional TaskStatus value to filter by
     * @return List of TaskInfo
     */
    public CompletableFuture<List<TaskInfo>> getTasks(String status) {
        return queryTeamTasksAsync(teamId, status).thenApply(tasks -> {
            List<TaskInfo> result = new ArrayList<>();
            for (Object t : tasks) {
                result.add(TaskInfo.fromInternal(t));
            }
            return result;
        });
    }

    /**
     * Query mailbox messages.
     *
     * @param toMember   Optional filter by recipient
     * @param fromMember Optional filter by sender
     * @return List of MessageInfo
     */
    public CompletableFuture<List<MessageInfo>> getMessages(String toMember, String fromMember) {
        return queryMessagesAsync(teamId, toMember, fromMember).thenApply(messages -> {
            List<MessageInfo> result = new ArrayList<>();
            for (Object m : messages) {
                result.add(MessageInfo.fromInternal(m));
            }
            return result;
        });
    }

    // ------------------------------------------------------------------
    // Event Stream
    // ------------------------------------------------------------------

    /**
     * Check if there are pending events.
     *
     * @return true if events are available or stream is still active
     */
    public boolean hasNextEvent() {
        return started.get() || !eventQueue.isEmpty();
    }

    /**
     * Get the next event from the queue.
     *
     * @return MonitorEvent or null if stream ended
     */
    public MonitorEvent nextEvent() {
        return eventQueue.poll();
    }

    /**
     * Wait for the next event.
     *
     * @return CompletableFuture with the next event or null if stream ended
     */
    public CompletableFuture<MonitorEvent> waitForNextEvent() {
        if (!started.get() && eventQueue.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }
        MonitorEvent evt = eventQueue.poll();
        if (evt != null) {
            return CompletableFuture.completedFuture(evt);
        }
        // Poll until event available
        return CompletableFuture.supplyAsync(() -> {
            while (started.get() || !eventQueue.isEmpty()) {
                MonitorEvent e = eventQueue.poll();
                if (e != null) {
                    return e;
                }
                try {
                    Thread.sleep(50);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return null;
                }
            }
            return null;
        });
    }

    // ------------------------------------------------------------------
    // Event Listener
    // ------------------------------------------------------------------

    private void onEvent(Object eventMessage) {
        MonitorEvent evt = MonitorEvent.fromEventMessage(eventMessage);
        if (evt != null) {
            eventQueue.offer(evt);
        }
    }

    // ------------------------------------------------------------------
    // Duck-typed helper methods (reflection or interface-based)
    // ------------------------------------------------------------------

    private void addEventListener(Consumer<Object> listener) {
        if (teamAgent instanceof TeamAgent agent) {
            agent.addEventListener(listener);
            return;
        }
        invokeListenerMethod("addEventListener", listener);
    }

    private void removeEventListener(Consumer<Object> listener) {
        if (teamAgent instanceof TeamAgent agent) {
            agent.removeEventListener(listener);
            return;
        }
        invokeListenerMethod("removeEventListener", listener);
    }

    private CompletableFuture<Object> queryTeamAsync(String teamId) {
        if (db instanceof TeamBackend backend) {
            Team team = new Team(
                    backend.getTeamName(),
                    backend.getTeamName(),
                    backend.getMemberName(),
                    null,
                    null,
                    System.currentTimeMillis(),
                    System.currentTimeMillis()
            );
            return CompletableFuture.completedFuture(team);
        }
        return CompletableFuture.completedFuture(invokeObjectMethod(db, "getTeam", teamId));
    }

    private CompletableFuture<List<Object>> queryTeamMembersAsync(String teamId, String status) {
        if (db instanceof TeamBackend backend) {
            List<Object> members = backend.listMembers().stream()
                    .filter(member -> status == null || status.isBlank()
                            || status.equalsIgnoreCase(member.getStatus().name()))
                    .map(member -> (Object) member)
                    .toList();
            return CompletableFuture.completedFuture(members);
        }
        Object result = invokeObjectMethod(db, "getTeamMembers", teamId, status);
        return CompletableFuture.completedFuture(asObjectList(result));
    }

    private CompletableFuture<Object> queryMemberAsync(String memberName, String teamId) {
        if (db instanceof TeamBackend backend) {
            return CompletableFuture.completedFuture(backend.getMember(memberName));
        }
        return CompletableFuture.completedFuture(invokeObjectMethod(db, "getMember", memberName, teamId));
    }

    private CompletableFuture<List<Object>> queryTeamTasksAsync(String teamId, String status) {
        if (db instanceof TeamBackend backend) {
            TaskStatus taskStatus = parseTaskStatus(status);
            List<Object> tasks = backend.getTaskManager().listByStatus(taskStatus).stream()
                    .map(task -> (Object) task)
                    .toList();
            return CompletableFuture.completedFuture(tasks);
        }
        Object result = invokeObjectMethod(db, "getTeamTasks", teamId, status);
        return CompletableFuture.completedFuture(asObjectList(result));
    }

    private CompletableFuture<List<Object>> queryMessagesAsync(String teamId, String toMember, String fromMember) {
        if (db instanceof TeamBackend backend) {
            List<Object> messages = new ArrayList<>();
            messages.addAll(backend.getMessages(toMember, false, fromMember));
            if (toMember == null) {
                messages.addAll(backend.getBroadcastMessages(false, fromMember));
                messages.sort(Comparator.comparingLong(TeamMonitor::messageTimestamp));
            }
            return CompletableFuture.completedFuture(messages);
        }
        Object result = toMember != null
                ? invokeObjectMethod(db, "getMessages", teamId, toMember, fromMember)
                : invokeObjectMethod(db, "getTeamMessages", teamId);
        return CompletableFuture.completedFuture(asObjectList(result));
    }

    public static TeamMonitor createMonitor(TeamAgent teamAgent) {
        if (teamAgent == null) {
            throw new IllegalArgumentException("teamAgent cannot be null");
        }
        if (teamAgent.getRuntimeContext() == null || teamAgent.getRuntimeContext().getRole() != TeamRole.LEADER) {
            throw new IllegalArgumentException("TeamMonitor can only be bound to a leader TeamAgent");
        }
        TeamBackend backend = teamAgent.getTeamBackend();
        if (backend == null) {
            throw new IllegalArgumentException("TeamAgent has no team backend configured");
        }
        return new TeamMonitor(backend.getTeamName(), SpawnContext.getSessionId(), backend, teamAgent);
    }

    private void invokeListenerMethod(String methodName, Consumer<Object> listener) {
        if (teamAgent == null) {
            return;
        }
        try {
            Method method = teamAgent.getClass().getMethod(methodName, Consumer.class);
            method.invoke(teamAgent, listener);
        } catch (NoSuchMethodException e) {
            String snakeName = "addEventListener".equals(methodName) ? "add_event_listener" : "remove_event_listener";
            try {
                Method method = teamAgent.getClass().getMethod(snakeName, Object.class);
                method.invoke(teamAgent, listener);
            } catch (ReflectiveOperationException ignored) {
                // Duck-typed monitor: missing listener methods mean there is no event source to attach.
            }
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to call " + methodName + " on " + teamAgent.getClass().getName(), e);
        }
    }

    private static Object invokeObjectMethod(Object target, String methodName, Object... args) {
        if (target == null) {
            return null;
        }
        for (Method method : target.getClass().getMethods()) {
            if (!method.getName().equals(methodName) || method.getParameterCount() != args.length) {
                continue;
            }
            try {
                Object value = method.invoke(target, args);
                if (value instanceof CompletableFuture<?> future) {
                    return future.join();
                }
                if (value instanceof java.util.Optional<?> optional) {
                    return optional.orElse(null);
                }
                return value;
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("Failed to call " + methodName + " on " + target.getClass().getName(), e);
            }
        }
        return null;
    }

    private static List<Object> asObjectList(Object value) {
        if (value instanceof CompletableFuture<?> future) {
            return asObjectList(future.join());
        }
        if (value instanceof List<?> list) {
            return new ArrayList<>(list);
        }
        if (value == null) {
            return new ArrayList<>();
        }
        return new ArrayList<>(List.of(value));
    }

    private static TaskStatus parseTaskStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        for (TaskStatus candidate : TaskStatus.values()) {
            if (candidate.name().equalsIgnoreCase(status)) {
                return candidate;
            }
        }
        return null;
    }

    private static long messageTimestamp(Object message) {
        if (message instanceof MessageRecord record) {
            return record.getCreatedAt();
        }
        Object value = invokeObjectMethod(message, "getTimestamp");
        if (value instanceof Number number) {
            return number.longValue();
        }
        value = invokeObjectMethod(message, "getCreatedAt");
        if (value instanceof Number number) {
            return number.longValue();
        }
        return 0L;
    }
}

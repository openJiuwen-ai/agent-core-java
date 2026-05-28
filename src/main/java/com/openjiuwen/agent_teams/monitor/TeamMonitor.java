/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.monitor;

import java.util.ArrayList;
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
        addEventListener(this::onEvent);
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
        removeEventListener(this::onEvent);
        started.set(false);
        eventQueue.offer(null);  // Sentinel to signal end
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
        // Placeholder: should call teamAgent.addEventListener(listener)
        // Implementation depends on TeamAgent interface
    }

    private void removeEventListener(Consumer<Object> listener) {
        // Placeholder: should call teamAgent.removeEventListener(listener)
    }

    private CompletableFuture<Object> queryTeamAsync(String teamId) {
        // Placeholder: should call db.team.getTeam(teamId)
        return CompletableFuture.completedFuture(null);
    }

    private CompletableFuture<List<Object>> queryTeamMembersAsync(String teamId, String status) {
        // Placeholder: should call db.member.getTeamMembers(teamId, status)
        return CompletableFuture.completedFuture(new ArrayList<>());
    }

    private CompletableFuture<Object> queryMemberAsync(String memberName, String teamId) {
        // Placeholder: should call db.member.getMember(memberName, teamId)
        return CompletableFuture.completedFuture(null);
    }

    private CompletableFuture<List<Object>> queryTeamTasksAsync(String teamId, String status) {
        // Placeholder: should call db.task.getTeamTasks(teamId, status)
        return CompletableFuture.completedFuture(new ArrayList<>());
    }

    private CompletableFuture<List<Object>> queryMessagesAsync(String teamId, String toMember, String fromMember) {
        // Placeholder: should call db.message.getMessages(...)
        return CompletableFuture.completedFuture(new ArrayList<>());
    }
}
/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.monitor;

import com.openjiuwen.agent_teams.AgentTeamsContext;
import com.openjiuwen.agent_teams.agent.AgentConfigurator.ConfiguredTeamBackend;
import com.openjiuwen.agent_teams.agent.AgentConfigurator.TeamRole;
import com.openjiuwen.agent_teams.agent.TeamAgent;
import com.openjiuwen.agent_teams.agent.coordination.CoordinationKernel.EventListener;
import com.openjiuwen.agent_teams.schema.events.EventMessage;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.common.logging.LoggerProtocol;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Observes a leader team agent for state queries and real-time events.
 *
 * <p>Mirrors Python's {@code TeamMonitor} and {@code create_monitor} in
 * {@code openjiuwen/agent_teams/monitor/team_monitor.py}.</p>
 */
public class TeamMonitor {

    private static final LoggerProtocol TEAM_LOGGER = Loggers.TEAM;

    private final String teamName;
    private final String sessionId;
    private final TeamMonitorDatabase db;
    private final EventSource eventSource;
    private final boolean hideDm;
    private final BlockingQueue<QueuedEvent> eventQueue = new LinkedBlockingQueue<>();
    private final EventListener eventListener = this::onEvent;
    private boolean started;

    public TeamMonitor(
            String teamName,
            String sessionId,
            TeamMonitorDatabase db,
            TeamAgent teamAgent
    ) {
        this(teamName, sessionId, db, teamAgent, false);
    }

    public TeamMonitor(
            String teamName,
            String sessionId,
            TeamMonitorDatabase db,
            TeamAgent teamAgent,
            boolean hideDm
    ) {
        this(teamName, sessionId, db, EventSource.fromTeamAgent(teamAgent), hideDm);
    }

    public TeamMonitor(
            String teamName,
            String sessionId,
            TeamMonitorDatabase db,
            EventSource eventSource,
            boolean hideDm
    ) {
        this.teamName = teamName;
        this.sessionId = sessionId;
        this.db = Objects.requireNonNull(db, "db");
        this.eventSource = Objects.requireNonNull(eventSource, "eventSource");
        this.hideDm = hideDm;
    }

    public String getTeamName() {
        return teamName;
    }

    public String getSessionId() {
        return sessionId;
    }

    public boolean isHideDm() {
        return hideDm;
    }

    public CompletionStage<Void> start() {
        if (started) {
            return CompletableFuture.completedFuture(null);
        }
        eventSource.addEventListener(eventListener);
        started = true;
        TEAM_LOGGER.info("TeamMonitor started for team " + teamName);
        return CompletableFuture.completedFuture(null);
    }

    public CompletionStage<Void> stop() {
        if (!started) {
            return CompletableFuture.completedFuture(null);
        }
        eventSource.removeEventListener(eventListener);
        started = false;
        eventQueue.offer(QueuedEvent.endOfStream());
        TEAM_LOGGER.info("TeamMonitor stopped for team " + teamName);
        return CompletableFuture.completedFuture(null);
    }

    public CompletionStage<TeamInfo> getTeamInfo() {
        return withBoundSession(() -> db.getTeam(teamName))
                .thenApply(team -> team.map(TeamInfo::fromInternal).orElse(null));
    }

    public CompletionStage<List<MemberInfo>> getMembers(String status) {
        return withBoundSession(() -> db.getTeamMembers(teamName, status))
                .thenApply(rows -> mapRows(rows, MemberInfo::fromInternal));
    }

    public CompletionStage<List<MemberInfo>> getMembers() {
        return getMembers(null);
    }

    public CompletionStage<MemberInfo> getMember(String memberName) {
        return withBoundSession(() -> db.getMember(memberName, teamName))
                .thenApply(member -> member.map(MemberInfo::fromInternal).orElse(null));
    }

    public CompletionStage<List<TaskInfo>> getTasks(String status) {
        return withBoundSession(() -> db.getTeamTasks(teamName, status))
                .thenApply(rows -> mapRows(rows, TaskInfo::fromInternal));
    }

    public CompletionStage<List<TaskInfo>> getTasks() {
        return getTasks(null);
    }

    public CompletionStage<List<MessageInfo>> getMessages() {
        return getMessages(null, null);
    }

    public CompletionStage<List<MessageInfo>> getMessages(String toMemberName, String fromMemberName) {
        if (hideDm && toMemberName != null) {
            return CompletableFuture.completedFuture(List.of());
        }
        return withBoundSession(() -> {
            if (toMemberName != null) {
                return db.getMessages(teamName, toMemberName, fromMemberName);
            }
            Boolean broadcastFilter = hideDm ? Boolean.TRUE : null;
            return db.getTeamMessages(teamName, broadcastFilter);
        }).thenApply(rows -> mapRows(rows, MessageInfo::fromInternal));
    }

    public Iterator<MonitorEvent> events() {
        return new Iterator<>() {
            private MonitorEvent next;
            private boolean closed;

            @Override
            public boolean hasNext() {
                if (closed) {
                    return false;
                }
                if (next != null) {
                    return true;
                }
                QueuedEvent queued = takeQueuedEvent();
                if (queued.terminal()) {
                    closed = true;
                    return false;
                }
                next = queued.event();
                return true;
            }

            @Override
            public MonitorEvent next() {
                if (!hasNext()) {
                    throw new NoSuchElementException();
                }
                MonitorEvent result = next;
                next = null;
                return result;
            }
        };
    }

    public CompletionStage<MonitorEvent> nextEvent() {
        return CompletableFuture.supplyAsync(() -> {
            QueuedEvent queued = takeQueuedEvent();
            return queued.terminal() ? null : queued.event();
        });
    }

    public Optional<MonitorEvent> pollEvent(Duration timeout) {
        try {
            QueuedEvent queued = eventQueue.poll(timeout.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);
            if (queued == null || queued.terminal()) {
                return Optional.empty();
            }
            return Optional.of(queued.event());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new CompletionException(exception);
        }
    }

    private CompletionStage<Void> onEvent(EventMessage event) {
        MonitorEvent monitorEvent = MonitorEvent.fromEventMessage(event);
        if (monitorEvent == null) {
            return CompletableFuture.completedFuture(null);
        }
        if (hideDm && monitorEvent.eventType() == MonitorEventType.MESSAGE) {
            return CompletableFuture.completedFuture(null);
        }
        eventQueue.offer(QueuedEvent.forEvent(monitorEvent));
        return CompletableFuture.completedFuture(null);
    }

    private <T> CompletionStage<T> withBoundSession(Supplier<CompletionStage<T>> supplier) {
        AgentTeamsContext.SessionIdToken token = null;
        boolean changed = sessionId != null && !sessionId.equals(AgentTeamsContext.getSessionId());
        if (changed) {
            token = AgentTeamsContext.setSessionId(sessionId);
        }
        try {
            CompletionStage<T> stage = supplier.get();
            if (!changed) {
                return stage;
            }
            CompletableFuture<T> guarded = new CompletableFuture<>();
            AgentTeamsContext.SessionIdToken finalToken = token;
            stage.whenComplete((value, exception) -> {
                AgentTeamsContext.resetSessionId(finalToken);
                if (exception != null) {
                    guarded.completeExceptionally(exception);
                } else {
                    guarded.complete(value);
                }
            });
            return guarded;
        } catch (RuntimeException exception) {
            if (changed) {
                AgentTeamsContext.resetSessionId(token);
            }
            return CompletableFuture.failedFuture(exception);
        }
    }

    private QueuedEvent takeQueuedEvent() {
        try {
            return eventQueue.take();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new CompletionException(exception);
        }
    }

    private static <T> List<T> mapRows(List<?> rows, Function<Object, T> mapper) {
        List<T> result = new ArrayList<>();
        if (rows == null) {
            return result;
        }
        for (Object row : rows) {
            result.add(mapper.apply(row));
        }
        return result;
    }

    public static TeamMonitor createMonitor(TeamAgent teamAgent) {
        return createMonitor(teamAgent, false);
    }

    public static TeamMonitor createMonitor(TeamAgent teamAgent, boolean hideDm) {
        if (teamAgent.getRole() != TeamRole.LEADER) {
            throw new IllegalArgumentException("TeamMonitor can only be bound to a leader TeamAgent");
        }
        ConfiguredTeamBackend backend = teamAgent.getTeamBackend();
        if (backend == null) {
            throw new IllegalArgumentException("TeamAgent has no team backend configured");
        }
        if (!(backend instanceof TeamMonitorDatabase monitorDatabase)) {
            throw new IllegalArgumentException("TeamAgent has no monitor database configured");
        }
        return new TeamMonitor(
                backend.getTeamName(),
                AgentTeamsContext.getSessionId(),
                monitorDatabase,
                teamAgent,
                hideDm
        );
    }

    /**
     * Event listener boundary used by the monitor.
     */
    public interface EventSource {
        void addEventListener(EventListener listener);

        void removeEventListener(EventListener listener);

        static EventSource fromTeamAgent(TeamAgent teamAgent) {
            Objects.requireNonNull(teamAgent, "teamAgent");
            return new EventSource() {
                @Override
                public void addEventListener(EventListener listener) {
                    teamAgent.addEventListener(listener);
                }

                @Override
                public void removeEventListener(EventListener listener) {
                    teamAgent.removeEventListener(listener);
                }
            };
        }
    }

    /**
     * Narrow database query surface used by {@link TeamMonitor}.
     *
     * <p>Mirrors Python's {@code TeamDatabase} attributes used in
     * {@code openjiuwen/agent_teams/monitor/team_monitor.py}.</p>
     */
    public interface TeamMonitorDatabase {
        CompletionStage<Optional<?>> getTeam(String teamName);

        CompletionStage<List<?>> getTeamMembers(String teamName, String status);

        CompletionStage<Optional<?>> getMember(String memberName, String teamName);

        CompletionStage<List<?>> getTeamTasks(String teamName, String status);

        CompletionStage<List<?>> getMessages(String teamName, String toMemberName, String fromMemberName);

        CompletionStage<List<?>> getTeamMessages(String teamName, Boolean broadcast);
    }

    private record QueuedEvent(MonitorEvent event, boolean terminal) {
        static QueuedEvent forEvent(MonitorEvent event) {
            return new QueuedEvent(event, false);
        }

        static QueuedEvent endOfStream() {
            return new QueuedEvent(null, true);
        }
    }
}

/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentteams.monitor;

import com.openjiuwen.agentteams.messager.InProcessMessager;
import com.openjiuwen.agentteams.schema.events.EventMessage;
import com.openjiuwen.agentteams.schema.events.TeamTopic;
import com.openjiuwen.agentteams.tools.TeamBackend;
import com.openjiuwen.agentteams.tools.TeamMember;
import com.openjiuwen.agentteams.tools.TeamMessage;
import com.openjiuwen.agentteams.tools.TeamTask;
import com.openjiuwen.agentteams.tools.database.MemberRecord;
import com.openjiuwen.agentteams.tools.database.MessageRecord;
import com.openjiuwen.agentteams.tools.database.TaskRecord;
import com.openjiuwen.agentteams.tools.database.TeamRecord;
import com.openjiuwen.agentteams.agent.TeamAgent;
import com.openjiuwen.agentteams.schema.team.TeamRole;

import java.util.List;
import java.util.Iterator;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Consumer;

/**
 * Monitors a team's lifecycle, membership, tasks, and messages.
 *
 * <p>Provides a polling-based API for observing team state changes and
 * receiving events. Can be bound to a leader {@link TeamAgent} for
 * in-process event listening, or subscribe to an
 * {@link com.openjiuwen.agentteams.messager.InProcessMessager} for
 * cross-process event routing.</p>
 *
 * @since 2026/7/9
 */
public class TeamMonitor {
    private final TeamBackend backend;
    private final TeamAgent teamAgent;
    private final Consumer<EventMessage> agentEventListener;
    private final BlockingQueue<MonitorEvent> events = new LinkedBlockingQueue<>();
    private final List<String> subscribedTopics = new java.util.ArrayList<>();
    private boolean isStarted;

    /**
     * Constructs a TeamMonitor with the given backend and no bound agent.
     *
     * <p>Use this constructor when the monitor will subscribe to an
     * {@link com.openjiuwen.agentteams.messager.InProcessMessager} rather
     * than listening on a specific {@link TeamAgent}.</p>
     *
     * @param backend the team backend used to query team state
     */
    public TeamMonitor(TeamBackend backend) {
        this(backend, null);
    }

    /**
     * Constructs a TeamMonitor bound to the given backend and agent.
     *
     * @param backend the team backend used to query team state
     * @param teamAgent the leader agent to listen for events, or {@code null}
     */
    private TeamMonitor(TeamBackend backend, TeamAgent teamAgent) {
        this.backend = backend;
        this.teamAgent = teamAgent;
        this.agentEventListener = this::enqueueEvent;
    }

    /**
     * Creates a TeamMonitor bound to the given leader agent.
     *
     * <p>The agent must be a leader and must have a configured
     * {@link com.openjiuwen.agentteams.tools.TeamBackend}.</p>
     *
     * @param teamAgent the leader agent to bind; must not be {@code null}
     *                  and must have the {@link com.openjiuwen.agentteams.schema.team.TeamRole#LEADER} role
     * @return a new TeamMonitor instance bound to the agent
     * @throws IllegalArgumentException if the agent is null, not a leader, or has no backend
     */
    public static TeamMonitor createMonitor(TeamAgent teamAgent) {
        if (teamAgent == null
                || teamAgent.getContext() == null
                || teamAgent.getContext().getRole() != TeamRole.LEADER) {
            throw new IllegalArgumentException("TeamMonitor can only be bound to a leader TeamAgent");
        }
        TeamBackend backend = teamAgent.getTeamBackend();
        if (backend == null) {
            throw new IllegalArgumentException("TeamAgent has no team backend configured");
        }
        return new TeamMonitor(backend, teamAgent);
    }

    /**
     * Starts monitoring team events.
     *
     * <p>If bound to a {@link TeamAgent}, registers an event listener on it.
     * Otherwise, subscribes to all {@link TeamTopic} channels on the
     * {@link com.openjiuwen.agentteams.messager.InProcessMessager}.</p>
     *
     * <p>No-op if already started.</p>
     */
    public void start() {
        if (isStarted) {
            return;
        }
        if (teamAgent != null) {
            teamAgent.addEventListener(agentEventListener);
            isStarted = true;
            return;
        }
        if (backend.getMessager() instanceof InProcessMessager messager) {
            // Mirror Python kernel.subscribe_transport: iterate TeamTopic enum and
            // build session-scoped topic strings. Broadcast rides the MESSAGE topic.
            // Use the team-level session pinned on the backend so subscriptions match
            // the topics publishers use (which also use the team session).
            String sessionId = backend.getTeamSessionId();
            String teamName = backend.getTeamName();
            for (TeamTopic topic : TeamTopic.values()) {
                subscribe(messager, topic.build(sessionId, teamName));
            }
            isStarted = true;
        }
    }

    /**
     * Stops monitoring team events and clears the event queue.
     *
     * <p>Removes the event listener from the bound agent or unsubscribes
     * from all messager topics. A sentinel event is enqueued to unblock
     * any waiting consumer.</p>
     *
     * <p>No-op if not started.</p>
     */
    public void stop() {
        if (!isStarted) {
            return;
        }
        if (teamAgent != null) {
            teamAgent.removeEventListener(agentEventListener);
        } else if (backend.getMessager() instanceof InProcessMessager messager) {
            for (String topic : List.copyOf(subscribedTopics)) {
                messager.unsubscribe(topic).join();
            }
            subscribedTopics.clear();
        } else {
            // no-op
        }
        isStarted = false;
        events.offer(MonitorEvent.builder().build());
    }

    /**
     * Returns whether this monitor is currently started and listening for events.
     *
     * @return {@code true} if started, {@code false} otherwise
     */
    public boolean isStarted() {
        return isStarted;
    }

    private void subscribe(InProcessMessager messager, String topic) {
        messager.subscribe(topic, this::onEvent).join();
        subscribedTopics.add(topic);
    }

    /**
     * Returns summary information about the monitored team.
     *
     * @return an {@link Optional} containing the team info,
     *     or {@link Optional#empty()} if the team record does not exist
     */
    public Optional<TeamInfo> getTeamInfo() {
        TeamRecord team = backend.getDb().team.getTeam(backend.getTeamName());
        if (team == null) {
            return Optional.empty();
        }
        return Optional.of(TeamInfo.builder()
                .teamId(team.getTeamName())
                .name(team.getDisplayName())
                .leaderId(team.getLeaderMemberName())
                .desc(team.getDesc())
                .created(team.getCreated())
                .build());
    }

    /**
     * Returns all members of the monitored team.
     *
     * @return unmodifiable list of member info objects
     */
    public List<MemberInfo> getMembers() {
        return getMembers(null);
    }

    /**
     * Returns members of the monitored team filtered by status.
     *
     * @param status the member status to filter by, or {@code null} for all
     * @return unmodifiable list of matching member info objects
     */
    public List<MemberInfo> getMembers(String status) {
        return backend.getDb().member.getTeamMembers(backend.getTeamName(), status).stream()
                .map(this::toMemberInfo)
                .toList();
    }

    /**
     * Returns information about a specific team member.
     *
     * @param memberName the unique member name
     * @return member info, or {@code null} if the member does not exist
     */
    public MemberInfo getMember(String memberName) {
        MemberRecord member = backend.getDb().member.getMember(memberName, backend.getTeamName());
        return member != null ? toMemberInfo(member) : null;
    }

    /**
     * Returns all tasks on the team board.
     *
     * @return list of tasks on the board
     */
    public List<TaskInfo> getTasks() {
        return getTasks(null);
    }

    /**
     * Returns tasks on the team board filtered by status.
     *
     * @param status filter by task status, or {@code null} for all
     * @return filtered list of tasks
     */
    public List<TaskInfo> getTasks(String status) {
        return backend.getDb().task.getTeamTasks(backend.getTeamName(), status).stream()
                .map(this::toTaskInfo)
                .toList();
    }

    /**
     * Returns all messages in the team mailbox.
     *
     * @return list of messages in the mailbox
     */
    public List<MessageInfo> getMessages() {
        return getMessages(null, null);
    }

    /**
     * Returns messages filtered by recipient and/or sender.
     *
     * @param toMember recipient filter, or {@code null} for all
     * @param fromMember sender filter, or {@code null} for all
     * @return filtered list of messages
     */
    public List<MessageInfo> getMessages(String toMember, String fromMember) {
        if (toMember != null) {
            return backend.getDb().message.getMessages(backend.getTeamName(), toMember, false, fromMember).stream()
                    .map(this::toMessageInfo)
                    .toList();
        }
        return backend.getDb().message.getTeamMessages(backend.getTeamName()).stream()
                .filter(message -> fromMember == null || fromMember.equals(message.getFromMemberName()))
                .map(this::toMessageInfo)
                .toList();
    }

    /**
     * Retrieves and removes the next coordination event, blocking if none is available.
     *
     * @return the next coordination event
     * @throws InterruptedException if waiting is interrupted
     */
    public MonitorEvent nextEvent() throws InterruptedException {
        return events.take();
    }

    /**
     * Returns whether there are queued coordination events.
     *
     * @return {@code true} if there are queued events
     */
    public boolean hasQueuedEvents() {
        return !events.isEmpty();
    }

    /**
     * Returns an iterable over coordination events that blocks on each next element.
     *
     * @return an iterator over coordination events
     */
    public Iterable<MonitorEvent> events() {
        return () -> new Iterator<>() {
            private MonitorEvent next;
            private boolean isFinished;

            /**
             * Checks whether more events exist, blocking if needed.
             *
             * @return {@code true} if more events exist
             */
            @Override
            public boolean hasNext() {
                if (isFinished) {
                    return false;
                }
                if (next == null) {
                    next = takeNextEvent();
                    if (next.getEventType() == null) {
                        isFinished = true;
                        next = null;
                    }
                }
                return next != null;
            }

            /**
             * Returns the next coordination event.
             *
             * @return the next event
             * @throws NoSuchElementException if no more events
             */
            @Override
            public MonitorEvent next() {
                if (!hasNext()) {
                    throw new NoSuchElementException();
                }
                MonitorEvent current = next;
                next = null;
                return current;
            }
        };
    }

    private MonitorEvent takeNextEvent() {
        try {
            return nextEvent();
        } catch (InterruptedException e) {
            // do not self-interrupt (G.CON.10)
            return MonitorEvent.builder().build();
        }
    }

    private java.util.concurrent.CompletableFuture<Void> onEvent(EventMessage message) {
        enqueueEvent(message);
        return java.util.concurrent.CompletableFuture.completedFuture(null);
    }

    private void enqueueEvent(EventMessage message) {
        MonitorEvent event = MonitorEvent.fromEventMessage(message, backend.getTeamName());
        if (event != null) {
            events.add(event);
        }
    }

    private MemberInfo toMemberInfo(TeamMember member) {
        return MemberInfo.builder()
                .memberId(member.getMemberName())
                .teamId(backend.getTeamName())
                .name(member.getDisplayName())
                .desc(member.getDescription())
                .status("busy")
                .executionStatus(null)
                .mode(member.getRole().name().toLowerCase(Locale.ROOT))
                .build();
    }

    private MemberInfo toMemberInfo(MemberRecord member) {
        return MemberInfo.builder()
                .memberId(member.getMemberName())
                .teamId(member.getTeamName())
                .name(member.getDisplayName())
                .desc(member.getDesc())
                .status(member.getStatus())
                .executionStatus(member.getExecutionStatus())
                .mode(member.getMode())
                .build();
    }

    private TaskInfo toTaskInfo(TeamTask task) {
        return TaskInfo.builder()
                .taskId(task.getTaskId())
                .teamId(task.getTeamName())
                .title(task.getTitle())
                .content(task.getContent())
                .status(task.getStatus())
                .assignee(task.getAssignee())
                .updatedAt(task.getUpdatedAt())
                .build();
    }

    private TaskInfo toTaskInfo(TaskRecord task) {
        return TaskInfo.builder()
                .taskId(task.getTaskId())
                .teamId(task.getTeamName())
                .title(task.getTitle())
                .content(task.getContent())
                .status(task.getStatus())
                .assignee(task.getAssignee())
                .updatedAt(task.getUpdatedAt())
                .build();
    }

    private MessageInfo toMessageInfo(TeamMessage message) {
        return MessageInfo.builder()
                .messageId(message.getMessageId())
                .teamId(message.getTeamName())
                .fromMember(message.getFromMemberName())
                .toMember(message.getToMemberName())
                .content(message.getContent())
                .timestamp(message.getTimestamp())
                .isBroadcast(message.isBroadcast())
                .isRead(message.isRead())
                .build();
    }

    private MessageInfo toMessageInfo(MessageRecord message) {
        return MessageInfo.builder()
                .messageId(message.getMessageId())
                .teamId(message.getTeamName())
                .fromMember(message.getFromMemberName())
                .toMember(message.getToMemberName())
                .content(message.getContent())
                .timestamp(message.getTimestamp())
                .isBroadcast(message.isBroadcast())
                .isRead(message.isRead())
                .build();
    }
}

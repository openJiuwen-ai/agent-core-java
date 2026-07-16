/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentteams.monitor;

import com.openjiuwen.agentteams.messager.InProcessMessager;
import com.openjiuwen.agentteams.schema.events.EventMessage;
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
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Consumer;

/**
 * Public class TeamMonitor used by the Java parity implementation.
 * 
 * @since 0.1.7
 */
public class TeamMonitor {
    private final TeamBackend backend;
    private final TeamAgent teamAgent;
    private final Consumer<EventMessage> agentEventListener;

    /**
     * LinkedBlockingQueue<>.
     * 
     * @since 0.1.7
     */
    private final BlockingQueue<MonitorEvent> events = new LinkedBlockingQueue<>();

    /**
     * java.util.ArrayList<>.
     * 
     * @since 0.1.7
     */
    private final List<String> subscribedTopics = new java.util.ArrayList<>();
    private boolean isStarted;

    /**
     * TeamMonitor.
     * 
     * @param backend backend
     * @since 0.1.7
     */
    public TeamMonitor(TeamBackend backend) {
        this(backend, null);
    }

    /**
     * TeamMonitor.
     * 
     * @param backend backend
     * @param teamAgent teamAgent
     * @since 0.1.7
     */
    private TeamMonitor(TeamBackend backend, TeamAgent teamAgent) {
        this.backend = backend;
        this.teamAgent = teamAgent;
        this.agentEventListener = this::enqueueEvent;
    }

    /**
     * createMonitor.
     * 
     * @param teamAgent teamAgent
     * @return the result
     * @since 0.1.7
     */
    public static TeamMonitor createMonitor(TeamAgent teamAgent) {
        if (teamAgent == null || teamAgent.getContext() == null
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
     * start.
     * 
     * @since 0.1.7
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
            subscribe(messager, "team:" + backend.getTeamName());
            subscribe(messager, "team:task");
            subscribe(messager, "team:message");
            subscribe(messager, "team:broadcast");
            isStarted = true;
        }
    }

    /**
     * stop.
     * 
     * @since 0.1.7
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
     * isStarted.
     * 
     * @return the result
     * @since 0.1.7
     */
    public boolean isStarted() {
        return isStarted;
    }

    /**
     * subscribe.
     * 
     * @param messager messager
     * @param topic topic
     * @since 0.1.7
     */
    private void subscribe(InProcessMessager messager, String topic) {
        messager.subscribe(topic, this::onEvent).join();
        subscribedTopics.add(topic);
    }

    /**
     * getTeamInfo.
     * 
     * @return the result
     * @since 0.1.7
     */
    public TeamInfo getTeamInfo() {
        TeamRecord team = backend.getDb().team.getTeam(backend.getTeamName());
        if (team == null) {
            return null;
        }
        return TeamInfo.builder().teamId(team.getTeamName()).name(team.getDisplayName())
                .leaderId(team.getLeaderMemberName()).desc(team.getDesc()).created(team.getCreated()).build();
    }

    /**
     * getMembers.
     * 
     * @return the result
     * @since 0.1.7
     */
    public List<MemberInfo> getMembers() {
        return getMembers(null);
    }

    /**
     * getMembers.
     * 
     * @param status status
     * @return the result
     * @since 0.1.7
     */
    public List<MemberInfo> getMembers(String status) {
        return backend.getDb().member.getTeamMembers(backend.getTeamName(), status).stream().map(this::toMemberInfo)
                .toList();
    }

    /**
     * getMember.
     * 
     * @param memberName memberName
     * @return the result
     * @since 0.1.7
     */
    public MemberInfo getMember(String memberName) {
        MemberRecord member = backend.getDb().member.getMember(memberName, backend.getTeamName());
        return member != null ? toMemberInfo(member) : null;
    }

    /**
     * getTasks.
     * 
     * @return the result
     * @since 0.1.7
     */
    public List<TaskInfo> getTasks() {
        return getTasks(null);
    }

    /**
     * getTasks.
     * 
     * @param status status
     * @return the result
     * @since 0.1.7
     */
    public List<TaskInfo> getTasks(String status) {
        return backend.getDb().task.getTeamTasks(backend.getTeamName(), status).stream().map(this::toTaskInfo).toList();
    }

    /**
     * getMessages.
     * 
     * @return the result
     * @since 0.1.7
     */
    public List<MessageInfo> getMessages() {
        return getMessages(null, null);
    }

    /**
     * getMessages.
     * 
     * @param toMember toMember
     * @param fromMember fromMember
     * @return the result
     * @since 0.1.7
     */
    public List<MessageInfo> getMessages(String toMember, String fromMember) {
        if (toMember != null) {
            return backend.getDb().message.getMessages(backend.getTeamName(), toMember, false, fromMember).stream()
                    .map(this::toMessageInfo).toList();
        }
        return backend.getDb().message.getTeamMessages(backend.getTeamName()).stream()
                .filter(message -> fromMember == null || fromMember.equals(message.getFromMemberName()))
                .map(this::toMessageInfo).toList();
    }

    /**
     * nextEvent.
     * 
     * @return the result
     * @throws InterruptedException InterruptedException
     * @since 0.1.7
     */
    public MonitorEvent nextEvent() throws InterruptedException {
        return events.take();
    }

    /**
     * hasQueuedEvents.
     * 
     * @return the result
     * @since 0.1.7
     */
    public boolean hasQueuedEvents() {
        return !events.isEmpty();
    }

    /**
     * events.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Iterable<MonitorEvent> events() {
        return () -> new Iterator<>() {
            private MonitorEvent next;
            private boolean isFinished;
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

    /**
     * takeNextEvent.
     * 
     * @return the result
     * @since 0.1.7
     */
    private MonitorEvent takeNextEvent() {
        try {
            return nextEvent();
        } catch (InterruptedException e) {
            return MonitorEvent.builder().build();
        }
    }

    /**
     * onEvent.
     * 
     * @param message message
     * @since 0.1.7
     */
    private java.util.concurrent.CompletableFuture<Void> onEvent(EventMessage message) {
        enqueueEvent(message);
        return java.util.concurrent.CompletableFuture.completedFuture(null);
    }

    /**
     * enqueueEvent.
     * 
     * @param message message
     * @since 0.1.7
     */
    private void enqueueEvent(EventMessage message) {
        MonitorEvent event = MonitorEvent.fromEventMessage(message, backend.getTeamName());
        if (event != null) {
            events.add(event);
        }
    }

    /**
     * toMemberInfo.
     * 
     * @param member member
     * @return the result
     * @since 0.1.7
     */
    private MemberInfo toMemberInfo(TeamMember member) {
        return MemberInfo.builder().memberId(member.getMemberName()).teamId(backend.getTeamName())
                .name(member.getDisplayName()).desc(member.getDescription()).status("busy").executionStatus(null)
                .mode(member.getRole().name().toLowerCase(Locale.ROOT)).build();
    }

    /**
     * toMemberInfo.
     * 
     * @param member member
     * @return the result
     * @since 0.1.7
     */
    private MemberInfo toMemberInfo(MemberRecord member) {
        return MemberInfo.builder().memberId(member.getMemberName()).teamId(member.getTeamName())
                .name(member.getDisplayName()).desc(member.getDesc()).status(member.getStatus())
                .executionStatus(member.getExecutionStatus()).mode(member.getMode()).build();
    }

    /**
     * toTaskInfo.
     * 
     * @param task task
     * @return the result
     * @since 0.1.7
     */
    private TaskInfo toTaskInfo(TeamTask task) {
        return TaskInfo.builder().taskId(task.getTaskId()).teamId(task.getTeamName()).title(task.getTitle())
                .content(task.getContent()).status(task.getStatus()).assignee(task.getAssignee())
                .updatedAt(task.getUpdatedAt()).build();
    }

    /**
     * toTaskInfo.
     * 
     * @param task task
     * @return the result
     * @since 0.1.7
     */
    private TaskInfo toTaskInfo(TaskRecord task) {
        return TaskInfo.builder().taskId(task.getTaskId()).teamId(task.getTeamName()).title(task.getTitle())
                .content(task.getContent()).status(task.getStatus()).assignee(task.getAssignee())
                .updatedAt(task.getUpdatedAt()).build();
    }

    /**
     * toMessageInfo.
     * 
     * @param message message
     * @return the result
     * @since 0.1.7
     */
    private MessageInfo toMessageInfo(TeamMessage message) {
        return MessageInfo.builder().messageId(message.getMessageId()).teamId(message.getTeamName())
                .fromMember(message.getFromMemberName()).toMember(message.getToMemberName())
                .content(message.getContent()).timestamp(message.getTimestamp()).broadcast(message.isBroadcast())
                .read(message.isRead()).build();
    }

    /**
     * toMessageInfo.
     * 
     * @param message message
     * @return the result
     * @since 0.1.7
     */
    private MessageInfo toMessageInfo(MessageRecord message) {
        return MessageInfo.builder().messageId(message.getMessageId()).teamId(message.getTeamName())
                .fromMember(message.getFromMemberName()).toMember(message.getToMemberName())
                .content(message.getContent()).timestamp(message.getTimestamp()).broadcast(message.isBroadcast())
                .read(message.isRead()).build();
    }
}

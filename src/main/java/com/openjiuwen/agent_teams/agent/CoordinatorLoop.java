/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.agent;

import com.openjiuwen.agent_teams.schema.events.EventMessage;
import com.openjiuwen.agent_teams.schema.TeamRole;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Minimal wake-up coordinator loop.
 *
 * <p>Mirrors Python's {@code CoordinatorLoop} in
 * {@code openjiuwen.agent_teams.agent.coordinator}.
 */
public class CoordinatorLoop {

    private final TeamRole role;
    private final Consumer<CoordinationEvent> wakeCallback;
    private final long mailboxPollIntervalMillis;
    private final long taskPollIntervalMillis;
    private final BlockingQueue<CoordinationEvent> queuedEvents = new LinkedBlockingQueue<>();
    private boolean running;
    private boolean pollsPaused;
    private Thread pollThread;

    public CoordinatorLoop(TeamRole role, Consumer<CoordinationEvent> wakeCallback) {
        this(role, wakeCallback, 30_000L, 30_000L);
    }

    public CoordinatorLoop(
            TeamRole role,
            Consumer<CoordinationEvent> wakeCallback,
            long mailboxPollIntervalMillis,
            long taskPollIntervalMillis
    ) {
        this.role = role;
        this.wakeCallback = wakeCallback;
        this.mailboxPollIntervalMillis = mailboxPollIntervalMillis;
        this.taskPollIntervalMillis = taskPollIntervalMillis;
    }

    public TeamRole getRole() {
        return role;
    }

    public boolean isRunning() {
        return running;
    }

    public boolean isPollsPaused() {
        return pollsPaused;
    }

    public void start() {
        if (running) {
            return;
        }
        this.running = true;
        this.pollsPaused = false;
        this.pollThread = new Thread(this::runPollingLoop, "agent-teams-coordinator-" + role.name().toLowerCase());
        this.pollThread.setDaemon(true);
        this.pollThread.start();
    }

    public void stop() {
        this.running = false;
        this.pollsPaused = false;
        if (pollThread != null) {
            pollThread.interrupt();
            pollThread = null;
        }
    }

    public void pausePolls() {
        this.pollsPaused = true;
    }

    public void resumePolls() {
        this.pollsPaused = false;
    }

    public void wake(CoordinationEvent event) {
        if (!running || wakeCallback == null) {
            return;
        }
        wakeCallback.accept(event);
    }

    public void enqueue(EventMessage event) {
        if (event == null) {
            return;
        }
        enqueue(new CoordinationEvent(event.getEventType(), event.getPayload()));
    }

    public void enqueue(CoordinationEvent event) {
        if (event != null) {
            queuedEvents.offer(event);
        }
    }

    private void runPollingLoop() {
        long lastMailboxPollAt = System.currentTimeMillis();
        long lastTaskPollAt = System.currentTimeMillis();
        while (running) {
            try {
                CoordinationEvent queuedEvent = queuedEvents.poll(50L, TimeUnit.MILLISECONDS);
                if (queuedEvent != null) {
                    wake(queuedEvent);
                }
                long now = System.currentTimeMillis();
                if (!pollsPaused) {
                    if (now - lastMailboxPollAt >= mailboxPollIntervalMillis) {
                        wake(new CoordinationEvent("coordination_poll_mailbox", java.util.Map.of()));
                        lastMailboxPollAt = now;
                    }
                    if (now - lastTaskPollAt >= taskPollIntervalMillis) {
                        wake(new CoordinationEvent("coordination_poll_task", java.util.Map.of()));
                        lastTaskPollAt = now;
                    }
                }
            } catch (InterruptedException interruptedException) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }
}

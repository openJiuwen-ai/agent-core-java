/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentteams.agent;

import com.openjiuwen.agentteams.schema.events.EventMessage;
import com.openjiuwen.agentteams.schema.team.TeamRole;

import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Thin wake-up loop for team coordination.
 * <p>
 * This mirrors the narrow Python coordinator slice only: lifecycle, wake-up callback, and
 * periodic mailbox/task poll fallback. It deliberately does not contain coordination decisions.
 * 
 * @since 0.1.7
 */
public class CoordinatorLoop {
    private final TeamRole role;
    private final Consumer<Object> wakeCallback;
    private final long mailboxPollIntervalMillis;
    private final long taskPollIntervalMillis;

    /**
     * LinkedBlockingQueue<>.
     * 
     * @since 0.1.7
     */
    private final BlockingQueue<Object> eventQueue = new LinkedBlockingQueue<>();

    /**
     * AtomicBoolean.
     * 
     * @since 0.1.7
     */
    private final AtomicBoolean running = new AtomicBoolean(false);

    /**
     * AtomicBoolean.
     * 
     * @since 0.1.7
     */
    private final AtomicBoolean pollsPaused = new AtomicBoolean(false);

    private Thread loopThread;
    private ScheduledExecutorService pollExecutor;
    private ScheduledFuture<?> mailboxPollTask;
    private ScheduledFuture<?> taskPollTask;

    /**
     * CoordinatorLoop.
     * 
     * @param role role
     * @since 0.1.7
     */
    public CoordinatorLoop(TeamRole role) {
        this(role, null, 30_000L, 30_000L);
    }

    /**
     * CoordinatorLoop.
     * 
     * @param role role
     * @param wakeCallback wakeCallback
     * @since 0.1.7
     */
    public CoordinatorLoop(TeamRole role, Consumer<Object> wakeCallback) {
        this(role, wakeCallback, 30_000L, 30_000L);
    }

    /**
     * CoordinatorLoop.
     * 
     * @param role role
     * @param wakeCallback wakeCallback
     * @param mailboxPollIntervalMillis mailboxPollIntervalMillis
     * @param taskPollIntervalMillis taskPollIntervalMillis
     * @since 0.1.7
     */
    public CoordinatorLoop(TeamRole role, Consumer<Object> wakeCallback, long mailboxPollIntervalMillis,
            long taskPollIntervalMillis) {
        this.role = Objects.requireNonNull(role, "role");
        this.wakeCallback = wakeCallback;
        this.mailboxPollIntervalMillis = mailboxPollIntervalMillis;
        this.taskPollIntervalMillis = taskPollIntervalMillis;
    }

    /**
     * getRole.
     * 
     * @return the result
     * @since 0.1.7
     */
    public TeamRole getRole() {
        return role;
    }

    /**
     * isRunning.
     * 
     * @return the result
     * @since 0.1.7
     */
    public boolean isRunning() {
        return running.get();
    }

    /**
     * isPollsPaused.
     * 
     * @return the result
     * @since 0.1.7
     */
    public boolean isPollsPaused() {
        return pollsPaused.get();
    }

    /**
     * start.
     * 
     * @since 0.1.7
     */
    public synchronized void start() {
        if (running.get()) {
            return;
        }
        running.set(true);
        pollsPaused.set(false);
        eventQueue.clear();
        loopThread = new Thread(this::runLoop, "agent-teams-coordinator-" + role.name().toLowerCase(Locale.ROOT));
        loopThread.setDaemon(true);
        loopThread.setUncaughtExceptionHandler((thread, error) -> running.set(false));
        loopThread.start();
        startPollTasks();
    }

    /**
     * stop.
     * 
     * @since 0.1.7
     */
    public synchronized void stop() {
        if (!running.get()) {
            return;
        }
        running.set(false);
        pollsPaused.set(false);
        cancelPollTasks();
        eventQueue.offer(InnerEventMessage.builder().eventType(InnerEventType.SHUTDOWN).build());
        if (loopThread != null) {
            try {
                loopThread.join(5_000L);
            } catch (InterruptedException interruptedException) {
                Thread.currentThread().interrupt();
            }
            if (loopThread.isAlive()) {
                try {
                    loopThread.join(1_000L);
                } catch (InterruptedException interruptedException) {
                    Thread.currentThread().interrupt();
                }
            }
            loopThread = null;
        }
        eventQueue.clear();
    }

    /**
     * pausePolls.
     * 
     * @since 0.1.7
     */
    public synchronized void pausePolls() {
        if (pollsPaused.get()) {
            return;
        }
        cancelPollTasks();
        pollsPaused.set(true);
    }

    /**
     * resumePolls.
     * 
     * @since 0.1.7
     */
    public synchronized void resumePolls() {
        if (!running.get() || !pollsPaused.get()) {
            return;
        }
        pollsPaused.set(false);
        startPollTasks();
    }

    /**
     * enqueue.
     * 
     * @param event event
     * @since 0.1.7
     */
    public void enqueue(Object event) {
        if (event != null) {
            eventQueue.offer(event);
        }
    }

    /**
     * runLoop.
     * 
     * @since 0.1.7
     */
    private void runLoop() {
        while (running.get() || !eventQueue.isEmpty()) {
            Object event;
            try {
                event = eventQueue.poll(1L, TimeUnit.SECONDS);
            } catch (InterruptedException interruptedException) {
                if (!running.get()) {
                    break;
                }
                continue;
            }
            if (event == null) {
                continue;
            }
            if (event instanceof InnerEventMessage innerEvent && innerEvent.getEventType() == InnerEventType.SHUTDOWN) {
                break;
            }
            if (wakeCallback == null) {
                continue;
            }
            try {
                wakeCallback.accept(event);
            } catch (RuntimeException ignored) {
                // Keep the loop alive just like the Python coordinator does.
            }
        }
    }

    /**
     * startPollTasks.
     * 
     * @since 0.1.7
     */
    private synchronized void startPollTasks() {
        cancelPollTasks();
        pollExecutor = new ScheduledThreadPoolExecutor(2, runnable -> {
            Thread thread =
                new Thread(runnable, "agent-teams-coordinator-poll-" + role.name().toLowerCase(Locale.ROOT));
            thread.setDaemon(true);
            thread.setUncaughtExceptionHandler((ignoredThread, ignoredError) -> pausePolls());
            return thread;
        });
        mailboxPollTask = schedulePoll(InnerEventType.POLL_MAILBOX, mailboxPollIntervalMillis);
        taskPollTask = schedulePoll(InnerEventType.POLL_TASK, taskPollIntervalMillis);
    }

    /**
     * schedulePoll.
     * 
     * @param eventType eventType
     * @param intervalMillis intervalMillis
     * @return the result
     * @since 0.1.7
     */
    private ScheduledFuture<?> schedulePoll(InnerEventType eventType, long intervalMillis) {
        long safeInterval = Math.max(1L, intervalMillis);
        return pollExecutor.scheduleAtFixedRate(() -> {
            if (!running.get()) {
                return;
            }
            enqueue(InnerEventMessage.builder().eventType(eventType).build());
        }, safeInterval, safeInterval, TimeUnit.MILLISECONDS);
    }

    /**
     * cancelPollTasks.
     * 
     * @since 0.1.7
     */
    private synchronized void cancelPollTasks() {
        if (mailboxPollTask != null) {
            mailboxPollTask.cancel(true);
            mailboxPollTask = null;
        }
        if (taskPollTask != null) {
            taskPollTask.cancel(true);
            taskPollTask = null;
        }
        if (pollExecutor != null) {
            pollExecutor.shutdownNow();
            pollExecutor = null;
        }
    }

    /**
     * isShutdownEvent.
     * 
     * @param event event
     * @return the result
     * @since 0.1.7
     */
    public static boolean isShutdownEvent(Object event) {
        return event instanceof InnerEventMessage innerEvent && innerEvent.getEventType() == InnerEventType.SHUTDOWN;
    }

    /**
     * isMailboxPollEvent.
     * 
     * @param event event
     * @return the result
     * @since 0.1.7
     */
    public static boolean isMailboxPollEvent(Object event) {
        return event instanceof InnerEventMessage innerEvent
                && innerEvent.getEventType() == InnerEventType.POLL_MAILBOX;
    }

    /**
     * isTaskPollEvent.
     * 
     * @param event event
     * @return the result
     * @since 0.1.7
     */
    public static boolean isTaskPollEvent(Object event) {
        return event instanceof InnerEventMessage innerEvent && innerEvent.getEventType() == InnerEventType.POLL_TASK;
    }

    /**
     * isUserInputEvent.
     * 
     * @param event event
     * @return the result
     * @since 0.1.7
     */
    public static boolean isUserInputEvent(Object event) {
        return event instanceof InnerEventMessage innerEvent && innerEvent.getEventType() == InnerEventType.USER_INPUT;
    }

    /**
     * isTransportEvent.
     * 
     * @param event event
     * @return the result
     * @since 0.1.7
     */
    public static boolean isTransportEvent(Object event) {
        return event instanceof EventMessage;
    }
}

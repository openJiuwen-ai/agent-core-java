/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.agent.coordination;

import com.openjiuwen.agent_teams.agent.AgentConfigurator.TeamRole;
import com.openjiuwen.agent_teams.agent.coordination.EventDispatcher.CoordinationEvent;
import com.openjiuwen.agent_teams.agent.coordination.EventDispatcher.InnerEventMessage;
import com.openjiuwen.agent_teams.agent.coordination.EventDispatcher.InnerEventType;
import com.openjiuwen.agent_teams.agent.coordination.EventDispatcher.PollController;
import com.openjiuwen.agent_teams.agent.coordination.EventDispatcher.TransportEvent;
import com.openjiuwen.agent_teams.schema.events.EventMessage;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Event-driven wake-up loop for team coordination.
 *
 * <p>Mirrors Python's {@code EventBus}, {@code InnerEventType},
 * {@code InnerEventMessage}, {@code CoordinationEvent}, and
 * {@code WakeCallback} in
 * {@code openjiuwen/agent_teams/agent/coordination/event_bus.py}.</p>
 *
 * <p>The bus manages only wake-up transport: start/stop state, event queue
 * draining, periodic mailbox/task poll enqueueing, and pause/resume of those
 * poll timers. Decision logic stays in dispatcher and handler callbacks.</p>
 */
public class EventBus implements PollController, AutoCloseable {

    public static final String PYTHON_MODULE = "openjiuwen/agent_teams/agent/coordination/event_bus.py";
    public static final List<String> EXPORTED_SYMBOLS = List.of(
            "CoordinationEvent",
            "EventBus",
            "InnerEventMessage",
            "InnerEventType",
            "WakeCallback"
    );

    private static final Logger LOGGER = LoggerFactory.getLogger(EventBus.class);

    private final TeamRole role;
    private final double mailboxPollInterval;
    private final double taskPollInterval;
    private final boolean periodicPollEnabled;
    private final BlockingQueue<CoordinationEvent> eventQueue = new LinkedBlockingQueue<>();
    private final ExecutorService loopExecutor;
    private final ScheduledExecutorService pollScheduler;

    private volatile WakeCallback wakeCallback;
    private volatile boolean running;
    private volatile boolean pollsPaused;
    private Future<?> loopTask;
    private ScheduledFuture<?> mailboxPollTask;
    private ScheduledFuture<?> taskPollTask;

    public EventBus(TeamRole role) {
        this(role, 30.0, 30.0);
    }

    public EventBus(TeamRole role, double mailboxPollInterval, double taskPollInterval) {
        this(
                role,
                mailboxPollInterval,
                taskPollInterval,
                Executors.newSingleThreadExecutor(daemonThreadFactory("coordination-event-loop")),
                Executors.newScheduledThreadPool(2, daemonThreadFactory("coordination-poll"))
        );
    }

    EventBus(
            TeamRole role,
            double mailboxPollInterval,
            double taskPollInterval,
            ExecutorService loopExecutor,
            ScheduledExecutorService pollScheduler
    ) {
        this.role = Objects.requireNonNull(role, "role");
        this.mailboxPollInterval = mailboxPollInterval;
        this.taskPollInterval = taskPollInterval;
        this.periodicPollEnabled = role != TeamRole.HUMAN_AGENT;
        this.loopExecutor = Objects.requireNonNull(loopExecutor, "loopExecutor");
        this.pollScheduler = Objects.requireNonNull(pollScheduler, "pollScheduler");
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

    public boolean isPeriodicPollEnabled() {
        return periodicPollEnabled;
    }

    public double getMailboxPollInterval() {
        return mailboxPollInterval;
    }

    public double getTaskPollInterval() {
        return taskPollInterval;
    }

    public boolean hasMailboxPollTask() {
        return mailboxPollTask != null && !mailboxPollTask.isCancelled();
    }

    public boolean hasTaskPollTask() {
        return taskPollTask != null && !taskPollTask.isCancelled();
    }

    public int getPendingEventCount() {
        return eventQueue.size();
    }

    public CompletionStage<Void> start() {
        return start(null);
    }

    public synchronized CompletionStage<Void> start(WakeCallback wakeCallback) {
        if (running) {
            return CompletableFuture.completedFuture(null);
        }
        if (wakeCallback != null) {
            this.wakeCallback = wakeCallback;
        }
        LOGGER.info("EventBus[{}] starting", role.value());
        running = true;
        loopTask = loopExecutor.submit(this::runLoop);
        startPollTasks();
        return CompletableFuture.completedFuture(null);
    }

    public synchronized CompletionStage<Void> stop() {
        if (!running) {
            return CompletableFuture.completedFuture(null);
        }
        LOGGER.info("EventBus[{}] stopping", role.value());
        running = false;
        pollsPaused = false;
        cancelPollTasks();
        eventQueue.offer(new InnerEventMessage(InnerEventType.SHUTDOWN));
        waitForLoopToStop();
        loopTask = null;
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public synchronized CompletionStage<Void> pausePolls() {
        if (pollsPaused) {
            return CompletableFuture.completedFuture(null);
        }
        LOGGER.info("EventBus[{}] pausing polls", role.value());
        cancelPollTasks();
        pollsPaused = true;
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public synchronized CompletionStage<Void> resumePolls() {
        if (!pollsPaused || !running) {
            return CompletableFuture.completedFuture(null);
        }
        LOGGER.info("EventBus[{}] resuming polls", role.value());
        startPollTasks();
        pollsPaused = false;
        return CompletableFuture.completedFuture(null);
    }

    public CompletionStage<Void> enqueue(CoordinationEvent event) {
        eventQueue.offer(Objects.requireNonNull(event, "event"));
        return CompletableFuture.completedFuture(null);
    }

    public CompletionStage<Void> enqueue(EventMessage event) {
        return enqueue(new TransportEvent(Objects.requireNonNull(event, "event")));
    }

    @Override
    public void close() {
        stop().toCompletableFuture().join();
        loopExecutor.shutdownNow();
        pollScheduler.shutdownNow();
    }

    private synchronized void startPollTasks() {
        if (!periodicPollEnabled) {
            return;
        }
        mailboxPollTask = pollScheduler.scheduleWithFixedDelay(
                () -> enqueuePollEvent(InnerEventType.POLL_MAILBOX),
                intervalMillis(mailboxPollInterval),
                intervalMillis(mailboxPollInterval),
                TimeUnit.MILLISECONDS
        );
        taskPollTask = pollScheduler.scheduleWithFixedDelay(
                () -> enqueuePollEvent(InnerEventType.POLL_TASK),
                intervalMillis(taskPollInterval),
                intervalMillis(taskPollInterval),
                TimeUnit.MILLISECONDS
        );
    }

    private void runLoop() {
        while (true) {
            CoordinationEvent event;
            try {
                event = eventQueue.poll(1, TimeUnit.SECONDS);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                break;
            }
            if (event == null) {
                if (!running) {
                    break;
                }
                continue;
            }
            if (event instanceof InnerEventMessage innerEvent
                    && innerEvent.getEventType() == InnerEventType.SHUTDOWN) {
                break;
            }
            try {
                WakeCallback callback = wakeCallback;
                if (callback != null) {
                    CompletionStage<Void> stage = callback.onWake(event);
                    if (stage != null) {
                        stage.toCompletableFuture().join();
                    }
                }
            } catch (RuntimeException exception) {
                LOGGER.error("EventBus: error in wake_callback for {}", event.eventKey(), exception);
            } finally {
                if (!running) {
                    break;
                }
            }
        }
    }

    private synchronized void cancelPollTasks() {
        if (mailboxPollTask != null) {
            mailboxPollTask.cancel(true);
            mailboxPollTask = null;
        }
        if (taskPollTask != null) {
            taskPollTask.cancel(true);
            taskPollTask = null;
        }
    }

    private void enqueuePollEvent(InnerEventType eventType) {
        if (!running) {
            return;
        }
        enqueue(new InnerEventMessage(eventType));
    }

    private void waitForLoopToStop() {
        Future<?> task = loopTask;
        if (task == null) {
            return;
        }
        try {
            task.get(5, TimeUnit.SECONDS);
        } catch (Exception exception) {
            task.cancel(true);
        }
    }

    private static long intervalMillis(double seconds) {
        return Math.max(0L, Math.round(seconds * 1000.0d));
    }

    private static ThreadFactory daemonThreadFactory(String name) {
        return runnable -> {
            Thread thread = new Thread(runnable);
            thread.setName(name + "-" + thread.threadId());
            thread.setDaemon(true);
            return thread;
        };
    }

    /**
     * Called with the full event when the loop is woken up.
     *
     * <p>Mirrors Python's {@code WakeCallback} alias in
     * {@code openjiuwen/agent_teams/agent/coordination/event_bus.py}.</p>
     */
    @FunctionalInterface
    public interface WakeCallback {
        CompletionStage<Void> onWake(CoordinationEvent event);
    }
}

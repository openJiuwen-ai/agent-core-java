/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentteams.agent.coordination;

import com.openjiuwen.agentteams.schema.events.CoordinationEvent;
import com.openjiuwen.agentteams.schema.team.TeamRole;
import com.openjiuwen.core.common.logging.Loggers;

import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Event-driven wake-up loop for team coordination.
 *
 * <p>Mirrors Python {@code agent/coordination/event_bus.py:EventBus}. Two
 * wake-up paths, same callback:
 * <ol>
 *   <li><b>Event-driven</b>: transport / direct messages trigger immediate
 *       wake-up via {@link #enqueue(CoordinationEvent)}.</li>
 *   <li><b>Polling timer</b>: periodic fallback for idle agents, catches
 *       missed events. Runs every 30s for mailbox and task board.</li>
 * </ol>
 *
 * <p>All decision logic lives in the DeepAgent + handlers — this class only
 * manages lifecycle and wake-up. Implements {@link PollController} so the
 * {@code EventDispatcher} can receive it as the poll control surface.
 *
 * <h2>Iron rules</h2>
 * <ul>
 *   <li><b>HUMAN_AGENT never runs periodic polls</b>: {@code _periodic_poll_enabled}
 *       is {@code false} for {@link TeamRole#HUMAN_AGENT}. Their POLL_MAILBOX /
 *       POLL_TASK inner events are muted at the dispatcher, so a timer would
 *       only spin uselessly. {@code start()} / {@code resumePolls()} share
 *       {@link #startPollTasks()} as the single gating point.</li>
 *   <li><b>Wake callback bound at {@link #start}, not construction</b>: breaks
 *       the bus↔dispatcher circular dependency. {@code CoordinationKernel.setup()}
 *       builds bus → builds dispatcher (with bus as poll_ctrl) →
 *       {@code kernel.start()} calls {@code bus.start(dispatcher::dispatch)}.</li>
 *   <li><b>Loop exceptions are logged</b>: a failing wake callback never kills
 *       the loop silently — it is logged and the loop continues (Python iron
 *       rule 3, applied to the loop layer).</li>
 * </ul>
 *
 * @since 2026/7/9
 */
public class EventBus implements PollController {
    private static final long DEFAULT_MAILBOX_POLL_INTERVAL_MS = 30_000L;
    private static final long DEFAULT_TASK_POLL_INTERVAL_MS = 30_000L;
    private static final long SHUTDOWN_JOIN_TIMEOUT_MS = 5_000L;
    private static final long QUEUE_POLL_TIMEOUT_MS = 1_000L;

    private final TeamRole role;
    private final long mailboxPollIntervalMillis;
    private final long taskPollIntervalMillis;
    private final boolean isPeriodicPollEnabled;

    private final BlockingQueue<CoordinationEvent> eventQueue = new LinkedBlockingQueue<>();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean pollsPaused = new AtomicBoolean(false);

    private volatile Consumer<CoordinationEvent> wakeCallback;
    private ThreadPoolExecutor loopExecutor;
    private ScheduledExecutorService pollExecutor;
    private ScheduledFuture<?> mailboxPollTask;
    private ScheduledFuture<?> taskPollTask;

    /**
     * Construct an EventBus for a role with default 30s poll intervals.
     *
     * @param role the owning member's role
     */
    public EventBus(TeamRole role) {
        this(role, DEFAULT_MAILBOX_POLL_INTERVAL_MS, DEFAULT_TASK_POLL_INTERVAL_MS);
    }

    /**
     * Construct an EventBus with explicit poll intervals.
     *
     * <p>Wake callback is bound at {@link #start(Consumer)}, not here, so the
     * kernel can hand this bus to the dispatcher as a poll controller before
     * the dispatcher exists.
     *
     * @param role the owning member's role
     * @param mailboxPollIntervalMillis mailbox poll period in milliseconds
     * @param taskPollIntervalMillis task board poll period in milliseconds
     */
    public EventBus(TeamRole role, long mailboxPollIntervalMillis, long taskPollIntervalMillis) {
        this.role = Objects.requireNonNull(role, "role");
        this.mailboxPollIntervalMillis = mailboxPollIntervalMillis;
        this.taskPollIntervalMillis = taskPollIntervalMillis;
        this.isPeriodicPollEnabled = role != TeamRole.HUMAN_AGENT;
    }

    /**
     * Return the role that owns this loop.
     *
     * @return the team role associated with this event bus
     */
    public TeamRole getRole() {
        return role;
    }

    /**
     * Whether the background loop is active.
     *
     * @return {@code true} if the event loop is currently running
     */
    public boolean isRunning() {
        return running.get();
    }

    /**
     * Whether periodic polling is paused.
     *
     * @return {@code true} if periodic polls are currently paused
     */
    public boolean isPollsPaused() {
        return pollsPaused.get();
    }

    /**
     * Start the event loop and polling timer.
     *
     * <p>The wake callback is bound here rather than at construction so the
     * coordination kernel can break the bus ↔ dispatcher circular dependency:
     * build bus → build dispatcher (with bus as poll controller) → start bus
     * with {@code dispatcher::dispatch}. Passing {@code null} keeps any
     * previously bound callback (useful for tests that exercise the bus
     * without a dispatcher).
     *
     * @param callback the wake callback (typically {@code dispatcher::dispatch})
     */
    public synchronized void start(Consumer<CoordinationEvent> callback) {
        if (running.get()) {
            return;
        }
        if (callback != null) {
            this.wakeCallback = callback;
        }
        Loggers.AGENT.info("EventBus[{}] starting", role.name());
        running.set(true);
        pollsPaused.set(false);
        eventQueue.clear();
        String threadName = "agent-teams-coordinator-" + role.name().toLowerCase(Locale.ROOT);
        loopExecutor = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(),
                runnable -> {
                    Thread thread = new Thread(runnable, threadName);
                    thread.setDaemon(true);
                    thread.setUncaughtExceptionHandler((t, error) -> running.set(false));
                    return thread;
                });
        loopExecutor.execute(this::runLoop);
        startPollTasks();
    }

    /**
     * Start with any previously bound callback.
     */
    public synchronized void start() {
        start(null);
    }

    /**
     * Stop loops, cancel poll timer, drain.
     *
     * <p>Idempotent. Enqueues a SHUTDOWN inner event and joins the loop thread
     * with a 5s timeout. Resets the pause flag before touching poll tasks so
     * partial failures still leave the state machine consistent.
     */
    public synchronized void stop() {
        if (!running.get()) {
            return;
        }
        Loggers.AGENT.info("EventBus[{}] stopping", role.name());
        running.set(false);
        pollsPaused.set(false);
        cancelPollTasks();
        eventQueue.offer(InnerEventMessage.builder().eventType(InnerEventType.SHUTDOWN).build());
        if (loopExecutor != null) {
            loopExecutor.shutdown();
            try {
                if (!loopExecutor.awaitTermination(SHUTDOWN_JOIN_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                    loopExecutor.awaitTermination(1_000L, TimeUnit.MILLISECONDS);
                }
            } catch (InterruptedException e) {
                // best-effort shutdown; do not self-interrupt (G.CON.10)
            }
            loopExecutor = null;
        }
        eventQueue.clear();
    }

    /**
     * Pause periodic polling tasks.
     *
     * <p>Cancels the mailbox and task board poll timers. Idempotent — does
     * nothing if polls are already paused.
     */
    @Override
    public synchronized void pausePolls() {
        if (pollsPaused.get()) {
            return;
        }
        Loggers.AGENT.info("EventBus[{}] pausing polls", role.name());
        cancelPollTasks();
        pollsPaused.set(true);
    }

    /**
     * Resume periodic polling tasks after a pause.
     *
     * <p>Restarts the mailbox and task board poll timers. Does nothing if
     * the bus is not running or polls are not currently paused.
     */
    @Override
    public synchronized void resumePolls() {
        if (!running.get() || !pollsPaused.get()) {
            return;
        }
        Loggers.AGENT.info("EventBus[{}] resuming polls", role.name());
        startPollTasks();
        pollsPaused.set(false);
    }

    /**
     * Push an event into the processing queue.
     *
     * @param event the coordination event (inner or transport); ignored if {@code null}
     */
    public void enqueue(CoordinationEvent event) {
        if (event != null) {
            eventQueue.offer(event);
        }
    }

    private void runLoop() {
        while (running.get() || !eventQueue.isEmpty()) {
            CoordinationEvent event;
            try {
                event = eventQueue.poll(QUEUE_POLL_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                // cooperative shutdown via sentinel + running flag (G.CON.10)
                if (!running.get()) {
                    break;
                }
                continue;
            }
            if (event == null) {
                continue;
            }
            if (event instanceof InnerEventMessage inner
                    && inner.getEventType() == InnerEventType.SHUTDOWN) {
                break;
            }
            Consumer<CoordinationEvent> cb = wakeCallback;
            if (cb == null) {
                continue;
            }
            try {
                cb.accept(event);
            } catch (IllegalStateException | NullPointerException
                    | IllegalArgumentException | UnsupportedOperationException e) {
                String eventType = event instanceof InnerEventMessage inner
                        ? inner.getEventType().name()
                        : "transport";
                Loggers.AGENT.error(
                        "EventBus: error in wake_callback for {}: {}", eventType, e.getMessage(), e);
            }
        }
    }

    private synchronized void startPollTasks() {
        cancelPollTasks();
        if (!isPeriodicPollEnabled) {
            // HUMAN_AGENT avatars never run periodic polls: their POLL_MAILBOX /
            // POLL_TASK inner events are muted at the dispatcher and the avatar
            // is driven by its controller's Inbox plus team events rendered as
            // controller notifications. Starting a timer would only spin uselessly.
            return;
        }
        pollExecutor = new ScheduledThreadPoolExecutor(2, createPollThreadFactory());
        mailboxPollTask = schedulePoll(InnerEventType.POLL_MAILBOX, mailboxPollIntervalMillis);
        taskPollTask = schedulePoll(InnerEventType.POLL_TASK, taskPollIntervalMillis);
    }

    private ScheduledFuture<?> schedulePoll(InnerEventType eventType, long intervalMillis) {
        long safeInterval = Math.max(1L, intervalMillis);
        return pollExecutor.scheduleAtFixedRate(
                () -> {
                    if (!running.get()) {
                        return;
                    }
                    enqueue(InnerEventMessage.builder().eventType(eventType).build());
                },
                safeInterval,
                safeInterval,
                TimeUnit.MILLISECONDS);
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
        if (pollExecutor != null) {
            pollExecutor.shutdownNow();
            pollExecutor = null;
        }
    }

    /**
     * Create a named-daemon ThreadFactory for the poll executor.
     *
     * @return a ThreadFactory that produces daemon threads with uncaught-handler
     */
    private java.util.concurrent.ThreadFactory createPollThreadFactory() {
        return runnable -> {
            Thread thread = java.util.concurrent.Executors.defaultThreadFactory().newThread(runnable);
            thread.setName("agent-teams-coordinator-poll-" + role.name().toLowerCase(Locale.ROOT));
            thread.setDaemon(true);
            thread.setUncaughtExceptionHandler((ignoredThread, ignoredError) -> pausePolls());
            return thread;
        };
    }
}

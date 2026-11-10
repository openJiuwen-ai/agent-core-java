/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session.checkpointer.step;

import com.openjiuwen.core.session.checkpointer.Checkpointer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Shared recovery pipeline for process restart and the runtime dead-step
 * sweeper (issue #195 sections 3.4/3.5).
 *
 * <p>For every leftover session with a RUNNING cursor the service walks the
 * same pipeline:</p>
 * <ol>
 *   <li>hook first - a registered {@link StepRecoveryHook} may resume,
 *       re-queue, or fail the task;</li>
 *   <li>hook did not recover - mark the task lost; revival of a task whose
 *       external state is undecidable would create zombie executions.</li>
 * </ol>
 *
 * <p>Callers (restart wiring, sweeper) share one instance; the processed
 * registry makes duplicate scans idempotent within a process.</p>
 *
 * @since 0.1.17
 */
public class StepCheckpointRecoveryService {
    private static final Logger log = LoggerFactory.getLogger(StepCheckpointRecoveryService.class);

    private final Checkpointer checkpointer;
    private final StepRecoveryHook hook;
    private final Set<String> processedSessions = ConcurrentHashMap.newKeySet();

    /**
     * Create a recovery service.
     *
     * @param checkpointer checkpointer providing the RUNNING cursor scan
     * @param hook business recovery hook, may be null (everything falls to markLost)
     */
    public StepCheckpointRecoveryService(Checkpointer checkpointer, StepRecoveryHook hook) {
        this.checkpointer = checkpointer;
        this.hook = hook;
    }

    /**
     * One scan pass over all sessions whose last step cursor is RUNNING.
     *
     * <p>PAUSED cursors are deliberately invisible to this scan: a paused
     * cursor records user intent, not a crash leftover, so the automatic
     * pipeline never touches it and only {@link #resumePaused} may continue
     * such a session.</p>
     *
     * @return actions taken, one entry per processed leftover session
     */
    public List<RecoveryAction> recoverLeftovers() {
        List<RecoveryAction> actions = new ArrayList<>();
        if (checkpointer == null) {
            return actions;
        }
        List<StepCursor> running;
        try {
            running = checkpointer.scanRunningSteps();
        } catch (RuntimeException exception) {
            log.warn("Step cursor scan failed: {}", exception.getMessage());
            return actions;
        }
        for (StepCursor cursor : running) {
            String sessionId = cursorKey(cursor);
            if (!processedSessions.add(sessionId)) {
                actions.add(new RecoveryAction(cursor, RecoveryActionType.SKIPPED_ALREADY_PROCESSED, ""));
                continue;
            }
            actions.add(recoverOne(cursor));
        }
        return actions;
    }

    /**
     * Scan all sessions with a persisted PAUSED cursor, i.e. sessions a user
     * explicitly suspended. Read-only: it neither resumes nor marks lost, it
     * only reports so an operator-facing layer can offer the resume entry.
     *
     * @return paused cursors, empty when none or when the backend cannot scan
     */
    public List<StepCursor> scanPausedSteps() {
        if (checkpointer == null) {
            return List.of();
        }
        try {
            return checkpointer.scanPausedSteps();
        } catch (RuntimeException exception) {
            log.warn("Paused cursor scan failed: {}", exception.getMessage());
            return List.of();
        }
    }

    /**
     * Explicitly resume one user-paused session: the paused cursor flips back
     * to RUNNING on the main resume point and the paused evidence is cleared.
     *
     * <p>This is the only path allowed to continue a PAUSED session. It
     * shares the processed registry with the automatic scan, so a resume is
     * idempotent within a process and a later automatic scan will not process
     * the session again.</p>
     *
     * @param cursor paused cursor of the session to resume, never null
     * @return the action taken; {@code SKIPPED_ALREADY_PROCESSED} when the
     *         session was already resumed in this process
     */
    public RecoveryAction resumePaused(StepCursor cursor) {
        Objects.requireNonNull(cursor, "cursor");
        if (checkpointer == null) {
            return new RecoveryAction(cursor, RecoveryActionType.MARKED_LOST, "no checkpointer");
        }
        if (!processedSessions.add(pausedKey(cursor))) {
            return new RecoveryAction(cursor, RecoveryActionType.SKIPPED_ALREADY_PROCESSED, "already resumed");
        }
        try {
            checkpointer.resumePaused(cursor);
            log.info("Paused session resumed by explicit request: {}", cursor);
            return new RecoveryAction(cursor, RecoveryActionType.RESUMED_EXPLICITLY, "resumed");
        } catch (RuntimeException exception) {
            processedSessions.remove(pausedKey(cursor));
            log.warn("Explicit resume failed for cursor {}: {}", cursor, exception.getMessage());
            return new RecoveryAction(cursor, RecoveryActionType.MARKED_LOST, exception.getMessage());
        }
    }

    /**
     * Explicitly resume one user-paused session identified by id: the paused
     * cursor is located through the checkpointer, flipped back to RUNNING on
     * the main resume point and the paused evidence is cleared. Shares the
     * processed registry with the cursor-based entry, so a resume is
     * idempotent within a process.
     *
     * @param sessionId id of the session to resume, never null or blank
     * @return the action taken; {@code MARKED_LOST} when the session has no
     *         paused cursor or the backend cannot resolve it
     */
    public RecoveryAction resumePaused(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return new RecoveryAction(null, RecoveryActionType.MARKED_LOST, "blank session id");
        }
        StepCursor cursor = pausedCursor(sessionId);
        if (cursor == null) {
            return new RecoveryAction(null, RecoveryActionType.MARKED_LOST,
                    "no paused cursor for session " + sessionId);
        }
        return resumePaused(cursor);
    }

    /**
     * Read the persisted paused cursor of one session through the
     * checkpointer, without requiring a live session object.
     *
     * @param sessionId id of the session to inspect, never null or blank
     * @return the paused cursor, or null when the session is not paused or
     *         the backend cannot resolve it
     */
    public StepCursor pausedCursor(String sessionId) {
        if (checkpointer == null) {
            return null;
        }
        try {
            return checkpointer.lastPausedStep(sessionId);
        } catch (RuntimeException exception) {
            log.warn("Paused cursor lookup failed for session {}: {}", sessionId, exception.getMessage());
            return null;
        }
    }

    /**
     * Recover a single leftover session; shared by restart wiring and the
     * sweeper so both always walk the identical pipeline.
     *
     * @param cursor RUNNING cursor of the leftover task
     * @return the action taken
     */
    public RecoveryAction recoverOne(StepCursor cursor) {
        Objects.requireNonNull(cursor, "cursor");
        StepRecoveryHook.RecoveryRequest request = new StepRecoveryHook.RecoveryRequest(
                "", "", cursor);
        if (hook != null) {
            try {
                StepRecoveryHook.RecoveryResult result = hook.recover(request);
                if (result != null && result.outcome() == StepRecoveryHook.Outcome.RECOVERED) {
                    log.info("Leftover session recovered by hook: {}", cursor);
                    return new RecoveryAction(cursor, RecoveryActionType.RECOVERED_BY_HOOK, result.detail());
                }
                if (result != null && result.outcome() == StepRecoveryHook.Outcome.MARKED_FAILED) {
                    log.info("Leftover session marked failed by hook: {}", cursor);
                    return new RecoveryAction(cursor, RecoveryActionType.MARKED_FAILED_BY_HOOK, result.detail());
                }
            } catch (RuntimeException exception) {
                log.warn("Recovery hook failed for cursor {}: {}", cursor, exception.getMessage());
            }
        }
        log.info("Leftover session marked lost (hook absent or declined): {}", cursor);
        return new RecoveryAction(cursor, RecoveryActionType.MARKED_LOST, "");
    }

    /**
     * Forget the processed marker of one session so a later scan may process
     * it again, used after a session was re-registered.
     *
     * @param sessionId session id to forget
     */
    public void forget(String sessionId) {
        processedSessions.remove(sessionId);
    }

    private static String cursorKey(StepCursor cursor) {
        return cursor.getStepName() + "@" + cursor.getSeq() + "@" + cursor.getTimestamp();
    }

    private static String pausedKey(StepCursor cursor) {
        return "paused:" + cursor.getStepName() + "@" + cursor.getSeq() + "@" + cursor.getTimestamp();
    }

    /** Action taken for one leftover session. */
    public enum RecoveryActionType {
        RECOVERED_BY_HOOK,
        MARKED_FAILED_BY_HOOK,
        MARKED_LOST,
        SKIPPED_ALREADY_PROCESSED,
        RESUMED_EXPLICITLY
    }

    /**
     * Result entry of one recovery pass.
     *
     * @param cursor cursor of the processed session
     * @param type action taken
     * @param detail optional hook detail
     */
    public record RecoveryAction(StepCursor cursor, RecoveryActionType type, String detail) {
    }
}

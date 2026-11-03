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

    /** Action taken for one leftover session. */
    public enum RecoveryActionType {
        RECOVERED_BY_HOOK,
        MARKED_FAILED_BY_HOOK,
        MARKED_LOST,
        SKIPPED_ALREADY_PROCESSED
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

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
 * registry makes duplicate scans idempotent within a process per owning
 * session and cursor, so a session that crashes again on a later step is
 * recovered again rather than skipped.</p>
 *
 * <p>The request handed to the hook carries the owning session/agent identity
 * the scan restored from storage, empty when the scan could not determine
 * it.</p>
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
            String processedKey = ownerKey(cursor) + "|" + cursorKey(cursor);
            if (!processedSessions.add(processedKey)) {
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
                cursor.getSessionId(), cursor.getAgentId(), cursor);
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
     * Forget the processed markers of one session so a later scan may process
     * it again, used after a session was re-registered. Matches the session
     * itself as well as every agent it owns within the session.
     *
     * @param sessionId session id to forget, null or empty is a no-op
     */
    public void forget(String sessionId) {
        if (sessionId == null || sessionId.isEmpty()) {
            return;
        }
        String ownerPrefix = sessionId + "|";
        processedSessions.removeIf(key -> key.equals(sessionId) || key.startsWith(ownerPrefix));
    }

    /**
     * Identity part of the processed marker: the owning session plus the agent
     * inside it, both empty when the scan could not determine them.
     *
     * @param cursor scanned cursor
     * @return owner identity, empty when unknown
     */
    private static String ownerKey(StepCursor cursor) {
        String sessionId = cursor.getSessionId();
        if (sessionId == null || sessionId.isEmpty()) {
            return "";
        }
        String agentId = cursor.getAgentId();
        return agentId == null || agentId.isEmpty() ? sessionId : sessionId + "|" + agentId;
    }

    /**
     * Cursor fingerprint of one step, kept so the processed registry still
     * distinguishes a later crash of the same session from the one already
     * handled in this process.
     *
     * @param cursor scanned cursor
     * @return fingerprint of the step the cursor points at
     */
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

/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session.checkpointer.step;

import com.openjiuwen.core.session.BaseSession;
import com.openjiuwen.core.session.checkpointer.Checkpointer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Single entry point agents call at every step boundary.
 *
 * <p>Responsibilities, in order:</p>
 * <ol>
 *   <li>evaluate {@link StepCheckpointPolicy} via {@link PolicyEvaluator};</li>
 *   <li>on a positive verdict, advance the cursor and flush it together with
 *       the full state through {@link Checkpointer#stepCompleted};</li>
 *   <li>append the audit record to the {@link StepJournal} regardless of the
 *       policy verdict - audit is cheap and never correctness-bearing.</li>
 * </ol>
 *
 * <p>The trigger keeps the running sequence number per instance; instances are
 * per agent execution, so the counter naturally resets between tasks.</p>
 *
 * @since 0.1.17
 */
public class StepCheckpointTrigger {
    private static final Logger log = LoggerFactory.getLogger(StepCheckpointTrigger.class);

    private final Checkpointer checkpointer;
    private final StepJournal journal;
    private final PolicyEvaluator evaluator;
    private final StepCheckpointPolicy agentDefault;
    private final AtomicLong seqCounter = new AtomicLong();
    private volatile boolean isLastBoundaryPersisted;

    /**
     * Create a trigger.
     *
     * @param checkpointer checkpointer performing the step-level save, may be
     *                     null when the session runs without checkpointing
     * @param journal audit journal, may be null to skip audit
     * @param agentDefault agent-level policy default; null falls back to {@code STEP_AUTO}
     */
    public StepCheckpointTrigger(Checkpointer checkpointer, StepJournal journal,
                                 StepCheckpointPolicy agentDefault) {
        this(checkpointer, journal, agentDefault, new PolicyEvaluator());
    }

    /**
     * Create a trigger with a custom evaluator (custom AUTO thresholds).
     *
     * @param checkpointer checkpointer performing the step-level save, may be null
     * @param journal audit journal, may be null to skip audit
     * @param agentDefault agent-level policy default; null falls back to {@code STEP_AUTO}
     * @param evaluator policy evaluator, null falls back to defaults
     */
    public StepCheckpointTrigger(Checkpointer checkpointer, StepJournal journal,
                                 StepCheckpointPolicy agentDefault, PolicyEvaluator evaluator) {
        this.checkpointer = checkpointer;
        this.journal = journal;
        this.agentDefault = agentDefault;
        this.evaluator = evaluator == null ? new PolicyEvaluator() : evaluator;
    }

    /**
     * Seed the sequence counter from the persisted resume point so cursors
     * of this execution stay strictly increasing across invocations on the
     * same session. Each trigger instance counts from zero, while the
     * checkpointer rejects any cursor whose seq is not greater than the
     * persisted one; without seeding, every invocation after the first (or
     * after an explicit resume) would have its cursors silently dropped.
     *
     * @param lastSeq seq of the last persisted cursor, 0 when none exists
     */
    public void seedSeq(long lastSeq) {
        if (lastSeq > seqCounter.get()) {
            seqCounter.set(lastSeq);
        }
    }

    /**
     * Record one step boundary. A {@link StepStatus#COMPLETED} or
     * {@link StepStatus#RUNNING} boundary follows the policy evaluation and
     * advances the main cursor; a {@link StepStatus#FAILED} boundary is
     * persisted unconditionally through {@link Checkpointer#stepFailed} -
     * failure evidence must not depend on the policy verdict and must never
     * move the resume point.
     *
     * @param session session in the middle of a task, may be null to no-op
     * @param invokeOverride per-invocation policy override; null means "not overridden"
     * @param signals execution-time facts for AUTO evaluation, may be null
     * @param boundary identity of the completed step
     */
    public void stepCompleted(BaseSession session, StepCheckpointPolicy invokeOverride, StepSignalContext signals,
                              StepBoundary boundary) {
        if (session == null || boundary == null) {
            return;
        }
        long seq = seqCounter.incrementAndGet();
        long now = System.currentTimeMillis();
        isLastBoundaryPersisted = false;
        if (boundary.status() == StepStatus.FAILED) {
            recordFailedStep(session, seq, boundary, now);
            return;
        }
        boolean shouldPersist = checkpointer != null
                && evaluator.shouldPersist(agentDefault, invokeOverride, signalsWithDurations(signals, seq));
        if (shouldPersist) {
            try {
                checkpointer.stepCompleted(session, new StepCursor(seq, boundary.stepType(), boundary.stepName(),
                        boundary.status(), now));
                isLastBoundaryPersisted = true;
            } catch (RuntimeException exception) {
                log.warn("Step-level checkpoint failed for session {}: {}",
                        session.sessionId(), exception.getMessage());
            }
        }
        if (journal != null) {
            try {
                journal.append(new StepRecord(session.sessionId(), "", seq, boundary.stepType(), boundary.stepName(),
                        "", boundary.status(), now));
            } catch (RuntimeException exception) {
                log.debug("Step journal append failed for session {}: {}",
                        session.sessionId(), exception.getMessage());
            }
        }
    }

    private void recordFailedStep(BaseSession session, long seq, StepBoundary boundary, long now) {
        if (checkpointer != null) {
            try {
                checkpointer.stepFailed(session, new StepCursor(seq, boundary.stepType(), boundary.stepName(),
                        StepStatus.FAILED, now));
            } catch (RuntimeException exception) {
                log.warn("Failed-step checkpoint could not be recorded for session {}: {}",
                        session.sessionId(), exception.getMessage());
            }
        }
        if (journal != null) {
            try {
                journal.append(new StepRecord(session.sessionId(), "", seq, boundary.stepType(), boundary.stepName(),
                        "", StepStatus.FAILED, now));
            } catch (RuntimeException exception) {
                log.debug("Failed-step journal append failed for session {}: {}",
                        session.sessionId(), exception.getMessage());
            }
        }
    }

    /**
     * Persist the user-pause boundary unconditionally: the cursor advances to
     * the next sequence number, is stored as PAUSED together with the pause
     * metadata, and the full state snapshot flushes through the regular save
     * path. Unlike {@link #stepCompleted} this bypasses the policy evaluation
     * - pause evidence must never depend on the persistence verdict.
     *
     * <p>The return value tells the caller whether the durable pause evidence
     * was actually written: a failed write leaves the session running with no
     * resumption point, so callers that surface the pause to the user must
     * check it.</p>
     *
     * <p>Before taking the next sequence number the counter is seeded from
     * the persisted resume point, so a trigger whose counter trails the store
     * (an un-seeded inner-loop trigger) cannot mint a paused cursor that the
     * stale-seq guard would silently drop.</p>
     *
     * @param session session being paused, carrying the live state to freeze,
     *                may be null to no-op
     * @param stepType step type recorded on the paused cursor
     * @param stepName step name recorded on the paused cursor
     * @param pauseInfo pause metadata (operator, reason, timestamps), may be null
     * @return true when the paused cursor was persisted, false when the
     *         session was absent, the checkpoint write failed, or no newer
     *         pause evidence could be written because the store already
     *         holds an equal or newer cursor
     */
    public boolean pauseNow(BaseSession session, String stepType, String stepName, Map<String, Object> pauseInfo) {
        if (session == null) {
            return false;
        }
        seedFromPersistedResumePoint(session);
        long seq = seqCounter.incrementAndGet();
        long now = System.currentTimeMillis();
        boolean isPersisted = false;
        if (checkpointer != null) {
            try {
                isPersisted = checkpointer.stepPaused(session,
                        new StepCursor(seq, stepType, stepName, StepStatus.PAUSED, now), pauseInfo);
            } catch (RuntimeException exception) {
                log.warn("Paused-step checkpoint could not be recorded for session {}: {}",
                        session.sessionId(), exception.getMessage());
            }
        }
        if (journal != null) {
            try {
                journal.append(new StepRecord(session.sessionId(), "", seq, stepType, stepName, "",
                        StepStatus.PAUSED, now));
            } catch (RuntimeException exception) {
                log.debug("Paused-step journal append failed for session {}: {}",
                        session.sessionId(), exception.getMessage());
            }
        }
        return isPersisted;
    }

    /**
     * Lift the sequence counter to the persisted resume point of the session
     * before minting a paused cursor, so the cursor stays above the store and
     * passes the stale-seq guard.
     *
     * @param session session about to persist a pause boundary
     */
    private void seedFromPersistedResumePoint(BaseSession session) {
        if (checkpointer == null) {
            return;
        }
        try {
            StepCursor last = checkpointer.lastCompletedStep(session);
            if (last != null) {
                seedSeq(last.getSeq());
            }
        } catch (RuntimeException exception) {
            log.debug("Pause boundary seeding skipped for session {}: {}",
                    session.sessionId(), exception.getMessage());
        }
    }

    /**
     * Whether the last evaluated boundary persisted the state snapshot. Used
     * by tests and by agents that want to skip their own redundant saves.
     *
     * @return the exact persistence verdict of the most recent
     *         {@link #stepCompleted} call; false before any boundary or when
     *         the trigger runs without a checkpointer
     */
    public boolean isLastBoundaryPersisted() {
        return isLastBoundaryPersisted;
    }

    /**
     * Current step sequence counter.
     *
     * @return number of boundaries observed so far
     */
    public long observedSteps() {
        return seqCounter.get();
    }

    private StepSignalContext signalsWithDurations(StepSignalContext signals, long seq) {
        StepSignalContext effective = signals == null ? new StepSignalContext() : signals;
        if (effective.getCompletedSteps() <= 0 && seq > 0) {
            return new StepSignalContext(effective.getTaskType(), effective.isHasWorkflow(),
                    effective.isHasInteractiveCapability(), effective.getToolCount(), seq - 1,
                    effective.getLastStepDurationMillis());
        }
        return effective;
    }
}

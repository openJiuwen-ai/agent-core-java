/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session.checkpointer.step;

import com.openjiuwen.core.session.BaseSession;
import com.openjiuwen.core.session.checkpointer.Checkpointer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    private StepSignalContext lastSignals;

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
     * Record one completed step boundary.
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
        lastSignals = signals;
        StepCheckpointPolicy policy = evaluator.resolve(agentDefault, invokeOverride);
        if (checkpointer != null
                && evaluator.shouldPersist(agentDefault, invokeOverride, signalsWithDurations(signals, seq))) {
            try {
                checkpointer.stepCompleted(session, new StepCursor(seq, boundary.stepType(), boundary.stepName(),
                        boundary.status(), now));
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

    /**
     * Whether the last evaluated boundary persisted the state snapshot. Used
     * by tests and by agents that want to skip their own redundant saves.
     *
     * @return true when the most recent {@link #stepCompleted} call flushed
     */
    public boolean isLastBoundaryPersisted() {
        if (lastSignals == null) {
            return false;
        }
        return evaluator.shouldPersist(agentDefault, null, lastSignals);
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

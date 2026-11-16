/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session.checkpointer.step;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.openjiuwen.core.foundation.store.kv.InMemoryKVStore;
import com.openjiuwen.core.multitenant.TenantContextHolder;
import com.openjiuwen.core.session.BaseSession;
import com.openjiuwen.core.session.checkpointer.Checkpointer;
import com.openjiuwen.core.session.checkpointer.InMemoryCheckpointer;
import com.openjiuwen.core.session.checkpointer.PersistenceCheckpointer;
import com.openjiuwen.core.session.config.Config;
import com.openjiuwen.core.session.state.AgentStateCollection;
import com.openjiuwen.core.session.state.SessionStateAccess;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

/**
 * Tests for the step-level checkpoint layer: cursor round-trip, policy
 * evaluation, cursor persistence through the three backends, and the
 * recovery pipeline.
 */
class StepCheckpointTest {
    @BeforeEach
    void clearTenantContext() {
        TenantContextHolder.clearCurrentTenant();
    }

    @AfterEach
    void restoreTenantContext() {
        TenantContextHolder.clearCurrentTenant();
    }

    @Test
    void cursorRoundTripsThroughStateMap() {
        StepCursor original = new StepCursor(7L, "TOOL_CALL", "search", StepStatus.COMPLETED, 1234L);
        Map<String, Object> update = Map.of(StepCursor.STATE_KEY, original.toMap());
        AgentStateCollection state = new AgentStateCollection();
        state.update(update);

        StepCursor restored = StepCursor.fromState(state.getState());
        assertNotNull(restored);
        assertEquals(7L, restored.getSeq());
        assertEquals("TOOL_CALL", restored.getStepType());
        assertEquals("search", restored.getStepName());
        assertEquals(StepStatus.COMPLETED, restored.getStatus());
        assertEquals(1234L, restored.getTimestamp());
    }

    @Test
    void fromMapReturnsNullForNonCursorInput() {
        assertNull(StepCursor.fromMap(null));
        assertNull(StepCursor.fromMap("not-a-map"));
        assertNull(StepCursor.fromMap(Map.of("other", 1)));
    }

    @Test
    void policyEvaluatorResolvesOverrideOverDefault() {
        PolicyEvaluator evaluator = new PolicyEvaluator();
        assertEquals(StepCheckpointPolicy.STEP_OFF,
                evaluator.resolve(StepCheckpointPolicy.STEP_ALWAYS, StepCheckpointPolicy.STEP_OFF));
        assertEquals(StepCheckpointPolicy.STEP_ALWAYS,
                evaluator.resolve(StepCheckpointPolicy.STEP_OFF, StepCheckpointPolicy.STEP_ALWAYS));
        assertEquals(StepCheckpointPolicy.STEP_AUTO, evaluator.resolve(null, null));
    }

    @Test
    void policyEvaluatorHonorsOffAlwaysAndAuto() {
        PolicyEvaluator evaluator = new PolicyEvaluator();
        StepSignalContext plain = new StepSignalContext("", false, false, 0, 0, 0);

        assertFalse(evaluator.shouldPersist(StepCheckpointPolicy.STEP_OFF, null, plain));
        assertTrue(evaluator.shouldPersist(StepCheckpointPolicy.STEP_ALWAYS, null, plain));
        assertFalse(evaluator.shouldPersist(StepCheckpointPolicy.STEP_AUTO, null, plain));

        StepSignalContext longRunning = new StepSignalContext("LONG_RUNNING", false, false, 0, 0, 0);
        assertTrue(evaluator.shouldPersist(StepCheckpointPolicy.STEP_AUTO, null, longRunning));
    }

    @Test
    void autoStructuralSignalsTriggerPersistence() {
        PolicyEvaluator evaluator = new PolicyEvaluator();
        StepSignalContext manyTools = new StepSignalContext("", false, false, 8, 0, 0);
        StepSignalContext interactive = new StepSignalContext("", false, true, 0, 0, 0);
        StepSignalContext workflow = new StepSignalContext("", true, false, 0, 0, 0);

        assertTrue(evaluator.shouldPersist(StepCheckpointPolicy.STEP_AUTO, null, manyTools));
        assertTrue(evaluator.shouldPersist(StepCheckpointPolicy.STEP_AUTO, null, interactive));
        assertTrue(evaluator.shouldPersist(StepCheckpointPolicy.STEP_AUTO, null, workflow));
    }

    @Test
    void autoDynamicSignalsTriggerLateActivation() {
        PolicyEvaluator evaluator = new PolicyEvaluator();
        StepSignalContext enoughSteps = new StepSignalContext("", false, false, 0, 3, 0);
        StepSignalContext slowStep = new StepSignalContext("", false, false, 0, 0, 5_000L);
        StepSignalContext justBelow = new StepSignalContext("", false, false, 0, 2, 4_999L);

        assertTrue(evaluator.shouldPersist(StepCheckpointPolicy.STEP_AUTO, null, enoughSteps));
        assertTrue(evaluator.shouldPersist(StepCheckpointPolicy.STEP_AUTO, null, slowStep));
        assertFalse(evaluator.shouldPersist(StepCheckpointPolicy.STEP_AUTO, null, justBelow));
    }

    @Test
    void customThresholdsChangeAutoVerdict() {
        PolicyEvaluator strict = new PolicyEvaluator(new StepAutoThresholds(100, 10, 60_000L));
        StepSignalContext moderate = new StepSignalContext("", false, false, 8, 3, 0);
        assertFalse(strict.shouldPersist(StepCheckpointPolicy.STEP_AUTO, null, moderate));

        PolicyEvaluator loose = new PolicyEvaluator(new StepAutoThresholds(2, 1, 1L));
        assertTrue(loose.shouldPersist(StepCheckpointPolicy.STEP_AUTO, null, moderate));
    }

    @Test
    void inMemoryBackendPersistsCursorWithStateSnapshot() {
        InMemoryCheckpointer checkpointer = new InMemoryCheckpointer();
        AgentStateCollection state = new AgentStateCollection();
        TestSession session = new TestSession("session-step", "agent-step", state);
        checkpointer.preAgentExecute(session, null);

        state.update(Map.of("progress", "half"));
        StepCursor cursor = new StepCursor(1L, "TOOL_CALL", "search", StepStatus.COMPLETED, 42L);
        checkpointer.stepCompleted(session, cursor);

        AgentStateCollection restoredState = new AgentStateCollection();
        TestSession restored = new TestSession("session-step", "agent-step", restoredState);
        checkpointer.preAgentExecute(restored, null);

        assertEquals("half", restoredState.get("progress"));
        StepCursor persisted = StepCursor.fromState(restoredState.getState());
        assertNotNull(persisted);
        assertEquals(1L, persisted.getSeq());
        assertEquals(StepStatus.COMPLETED, persisted.getStatus());
    }

    @Test
    void lastCompletedStepRejectsStaleSequence() {
        InMemoryCheckpointer checkpointer = new InMemoryCheckpointer();
        AgentStateCollection state = new AgentStateCollection();
        TestSession session = new TestSession("session-order", "agent-order", state);
        checkpointer.preAgentExecute(session, null);

        checkpointer.stepCompleted(session, new StepCursor(2L, "PHASE", "round-2", StepStatus.COMPLETED, 1L));
        checkpointer.stepCompleted(session, new StepCursor(1L, "PHASE", "round-1", StepStatus.COMPLETED, 2L));

        StepCursor last = checkpointer.lastCompletedStep(session);
        assertNotNull(last);
        assertEquals(2L, last.getSeq());
    }

    @Test
    void inMemoryScanFindsRunningCursors() {
        InMemoryCheckpointer checkpointer = new InMemoryCheckpointer();
        AgentStateCollection runningState = new AgentStateCollection();
        TestSession running = new TestSession("session-running", "agent-running", runningState);
        checkpointer.preAgentExecute(running, null);
        checkpointer.stepCompleted(running,
                new StepCursor(3L, "TOOL_CALL", "search", StepStatus.RUNNING, 7L));

        List<StepCursor> found = checkpointer.scanRunningSteps();
        assertEquals(1, found.size());
        assertEquals(3L, found.get(0).getSeq());
    }

    @Test
    void inMemoryScanIgnoresCompletedCursors() {
        InMemoryCheckpointer checkpointer = new InMemoryCheckpointer();
        AgentStateCollection doneState = new AgentStateCollection();
        TestSession done = new TestSession("session-done", "agent-done", doneState);
        checkpointer.preAgentExecute(done, null);
        checkpointer.stepCompleted(done,
                new StepCursor(1L, "TOOL_CALL", "search", StepStatus.COMPLETED, 7L));

        assertTrue(checkpointer.scanRunningSteps().isEmpty());
    }

    @Test
    void persistenceBackendPersistsCursorWithStateSnapshot() {
        PersistenceCheckpointer checkpointer = new PersistenceCheckpointer(new InMemoryKVStore());
        AgentStateCollection state = new AgentStateCollection();
        TestSession session = new TestSession("session-persist", "agent-persist", state);
        checkpointer.preAgentExecute(session, null);

        state.update(Map.of("progress", "step-2"));
        checkpointer.stepCompleted(session,
                new StepCursor(2L, "TOOL_CALL", "write", StepStatus.COMPLETED, 99L));

        AgentStateCollection restoredState = new AgentStateCollection();
        TestSession restored = new TestSession("session-persist", "agent-persist", restoredState);
        checkpointer.preAgentExecute(restored, null);

        assertEquals("step-2", restoredState.get("progress"));
        StepCursor persisted = StepCursor.fromState(restoredState.getState());
        assertNotNull(persisted);
        assertEquals(2L, persisted.getSeq());
    }

    @Test
    void recoveryServicePrefersHookBeforeMarkLost() {
        InMemoryCheckpointer checkpointer = new InMemoryCheckpointer();
        AgentStateCollection state = new AgentStateCollection();
        TestSession session = new TestSession("session-recover", "agent-recover", state);
        checkpointer.preAgentExecute(session, null);
        checkpointer.stepCompleted(session,
                new StepCursor(1L, "TOOL_CALL", "search", StepStatus.RUNNING, 5L));

        List<StepCheckpointRecoveryService.RecoveryAction> recoveredHook = List.of();
        StepCheckpointRecoveryService withHook = new StepCheckpointRecoveryService(checkpointer,
                request -> StepRecoveryHook.RecoveryResult.recovered("resumed"));
        recoveredHook = withHook.recoverLeftovers();
        assertEquals(1, recoveredHook.size());
        assertEquals(StepCheckpointRecoveryService.RecoveryActionType.RECOVERED_BY_HOOK,
                recoveredHook.get(0).type());

        InMemoryCheckpointer freshCheckpointer = new InMemoryCheckpointer();
        AgentStateCollection freshState = new AgentStateCollection();
        TestSession fresh = new TestSession("session-lost", "agent-lost", freshState);
        freshCheckpointer.preAgentExecute(fresh, null);
        freshCheckpointer.stepCompleted(fresh,
                new StepCursor(1L, "TOOL_CALL", "search", StepStatus.RUNNING, 5L));

        StepCheckpointRecoveryService withoutHook = new StepCheckpointRecoveryService(freshCheckpointer, null);
        List<StepCheckpointRecoveryService.RecoveryAction> lostActions = withoutHook.recoverLeftovers();
        assertEquals(1, lostActions.size());
        assertEquals(StepCheckpointRecoveryService.RecoveryActionType.MARKED_LOST,
                lostActions.get(0).type());
    }

    @Test
    void hookExceptionsFallThroughToMarkLost() {
        InMemoryCheckpointer checkpointer = new InMemoryCheckpointer();
        AgentStateCollection state = new AgentStateCollection();
        TestSession session = new TestSession("session-hook-fail", "agent-hook-fail", state);
        checkpointer.preAgentExecute(session, null);
        checkpointer.stepCompleted(session,
                new StepCursor(1L, "TOOL_CALL", "search", StepStatus.RUNNING, 5L));

        StepCheckpointRecoveryService service = new StepCheckpointRecoveryService(checkpointer,
                request -> {
                    throw new IllegalStateException("hook exploded");
                });
        List<StepCheckpointRecoveryService.RecoveryAction> actions = service.recoverLeftovers();
        assertEquals(1, actions.size());
        assertEquals(StepCheckpointRecoveryService.RecoveryActionType.MARKED_LOST, actions.get(0).type());
    }

    @Test
    void kvJournalAppendsReplaysAndSanitizes() {
        KvStepJournal journal = new KvStepJournal(new InMemoryKVStore());
        journal.append(new StepRecord("session-j", "agent-j", 1L, "TOOL_CALL", "login",
                "{\"password\":\"hunter2\",\"token\":\"abc\"}", StepStatus.COMPLETED, 10L));
        journal.append(new StepRecord("session-j", "agent-j", 2L, "LLM_RESPONSE", "answer",
                "plain result", StepStatus.COMPLETED, 20L));

        List<StepRecord> trail = journal.replay("session-j");
        assertEquals(2, trail.size());
        assertEquals(1L, trail.get(0).getSeq());
        assertEquals(2L, trail.get(1).getSeq());
        assertFalse(trail.get(0).getPayloadJson().contains("hunter2"));
        assertFalse(trail.get(0).getPayloadJson().contains("abc"));
        assertTrue(trail.get(0).getPayloadJson().contains("***"));
    }

    @Test
    void kvJournalPurgeRemovesExpiredRecords() {
        KvStepJournal journal = new KvStepJournal(new InMemoryKVStore());
        journal.append(new StepRecord("session-old", "agent", 1L, "TOOL_CALL", "old",
                "", StepStatus.COMPLETED, 100L));
        journal.append(new StepRecord("session-new", "agent", 2L, "TOOL_CALL", "new",
                "", StepStatus.COMPLETED, 9_999_999L));

        int removed = journal.purgeBefore(5_000L);
        assertEquals(1, removed);
        assertEquals(1, journal.replay("session-old").size() + journal.replay("session-new").size());
        assertTrue(journal.replay("session-old").isEmpty());
    }

    @Test
    void sanitizerTruncatesOversizedPayloads() {
        StepPayloadSanitizer sanitizer = new StepPayloadSanitizer(16);
        String payload = "x".repeat(64);
        String sanitized = sanitizer.sanitize(payload);
        assertTrue(sanitized.startsWith("x".repeat(16)));
        assertTrue(sanitized.endsWith("(truncated)"));
    }

    @Test
    void triggerSkipsPersistenceWhenPolicyOff() {
        InMemoryCheckpointer checkpointer = new InMemoryCheckpointer();
        AgentStateCollection state = new AgentStateCollection();
        TestSession session = new TestSession("session-off", "agent-off", state);
        checkpointer.preAgentExecute(session, null);

        StepCheckpointTrigger trigger = new StepCheckpointTrigger(
                checkpointer, null, StepCheckpointPolicy.STEP_OFF);
        trigger.stepCompleted(session, null, null,
                new StepBoundary("TOOL_CALL", "search", StepStatus.COMPLETED, 1L));

        assertNull(StepCursor.fromState(state.getState()));
        assertEquals(1, trigger.observedSteps());
    }

    @Test
    void triggerPersistsCursorThroughCheckpointer() {
        InMemoryCheckpointer checkpointer = new InMemoryCheckpointer();
        AgentStateCollection state = new AgentStateCollection();
        TestSession session = new TestSession("session-trigger", "agent-trigger", state);
        checkpointer.preAgentExecute(session, null);

        StepCheckpointTrigger trigger = new StepCheckpointTrigger(
                checkpointer, null, StepCheckpointPolicy.STEP_ALWAYS);
        trigger.stepCompleted(session, null, null,
                new StepBoundary("TOOL_CALL", "search", StepStatus.COMPLETED, 1L));

        StepCursor persisted = StepCursor.fromState(state.getState());
        assertNotNull(persisted);
        assertEquals("TOOL_CALL", persisted.getStepType());
    }

    @Test
    void lastBoundaryFlagMirrorsOverrideVerdict() {
        InMemoryCheckpointer checkpointer = new InMemoryCheckpointer();
        AgentStateCollection state = new AgentStateCollection();
        TestSession session = new TestSession("session-flag-override", "agent-flag", state);
        checkpointer.preAgentExecute(session, null);

        StepCheckpointTrigger trigger = new StepCheckpointTrigger(
                checkpointer, null, StepCheckpointPolicy.STEP_OFF);
        trigger.stepCompleted(session, StepCheckpointPolicy.STEP_ALWAYS, null,
                new StepBoundary("TOOL_CALL", "search", StepStatus.COMPLETED, 1L));

        assertTrue(trigger.isLastBoundaryPersisted());
        assertNotNull(StepCursor.fromState(state.getState()));

        trigger.stepCompleted(session, null, null,
                new StepBoundary("TOOL_CALL", "search", StepStatus.COMPLETED, 2L));
        assertFalse(trigger.isLastBoundaryPersisted());
    }

    @Test
    void lastBoundaryFlagSeesCompletedStepsSignal() {
        InMemoryCheckpointer checkpointer = new InMemoryCheckpointer();
        AgentStateCollection state = new AgentStateCollection();
        TestSession session = new TestSession("session-flag-steps", "agent-flag", state);
        checkpointer.preAgentExecute(session, null);

        StepAutoThresholds thresholds = new StepAutoThresholds(
                StepAutoThresholds.DEFAULT_TOOL_COUNT, 3, StepAutoThresholds.DEFAULT_STEP_DURATION_MILLIS);
        StepCheckpointTrigger trigger = new StepCheckpointTrigger(
                checkpointer, null, StepCheckpointPolicy.STEP_AUTO, new PolicyEvaluator(thresholds));
        StepSignalContext noStepsYet = new StepSignalContext("", false, false, 0, 0, 0L);
        for (long boundary = 1L; boundary <= 3L; boundary++) {
            trigger.stepCompleted(session, null, noStepsYet,
                    new StepBoundary("TOOL_CALL", "search", StepStatus.COMPLETED, boundary));
            assertFalse(trigger.isLastBoundaryPersisted());
        }

        trigger.stepCompleted(session, null, noStepsYet,
                new StepBoundary("TOOL_CALL", "search", StepStatus.COMPLETED, 4L));
        assertTrue(trigger.isLastBoundaryPersisted());
    }

    @Test
    void lastBoundaryFlagFalseWithoutCheckpointer() {
        StepCheckpointTrigger trigger = new StepCheckpointTrigger(
                null, null, StepCheckpointPolicy.STEP_ALWAYS);
        AgentStateCollection state = new AgentStateCollection();
        TestSession session = new TestSession("session-flag-nullcp", "agent-flag", state);
        trigger.stepCompleted(session, null, null,
                new StepBoundary("TOOL_CALL", "search", StepStatus.COMPLETED, 1L));

        assertFalse(trigger.isLastBoundaryPersisted());
    }

    @Test
    void triggerRecordsAuditEvenWhenPolicyOff() {
        KvStepJournal journal = new KvStepJournal(new InMemoryKVStore());
        InMemoryCheckpointer checkpointer = new InMemoryCheckpointer();
        AgentStateCollection state = new AgentStateCollection();
        TestSession session = new TestSession("session-audit", "agent-audit", state);
        checkpointer.preAgentExecute(session, null);

        StepCheckpointTrigger trigger = new StepCheckpointTrigger(
                checkpointer, journal, StepCheckpointPolicy.STEP_OFF);
        trigger.stepCompleted(session, null, null,
                new StepBoundary("TOOL_CALL", "search", StepStatus.COMPLETED, 1L));

        assertNull(StepCursor.fromState(state.getState()));
        assertEquals(1, journal.replay("session-audit").size());
    }

    @Test
    void triggerPersistsFailedStepIndependentlyOfPolicy() {
        KvStepJournal journal = new KvStepJournal(new InMemoryKVStore());
        InMemoryCheckpointer checkpointer = new InMemoryCheckpointer();
        AgentStateCollection state = new AgentStateCollection();
        TestSession session = new TestSession("session-failed", "agent-failed", state);
        checkpointer.preAgentExecute(session, null);

        StepCheckpointTrigger trigger = new StepCheckpointTrigger(
                checkpointer, journal, StepCheckpointPolicy.STEP_OFF);
        trigger.stepCompleted(session, null, null,
                new StepBoundary("ITERATION", "iteration-2", StepStatus.FAILED, 55L));

        // The main cursor stays untouched (STEP_OFF would never write it), the
        // failed cursor lands under its own key, and the audit keeps the FAILED
        // status regardless of the policy verdict.
        assertNull(StepCursor.fromState(state.getState()));
        StepCursor failed = StepCursor.failedFromState(state.getState());
        assertNotNull(failed);
        assertEquals("ITERATION", failed.getStepType());
        assertEquals("iteration-2", failed.getStepName());
        assertEquals(StepStatus.FAILED, failed.getStatus());
        assertEquals(1L, failed.getSeq());
        assertEquals(1, trigger.observedSteps());
        List<StepRecord> trail = journal.replay("session-failed");
        assertEquals(1, trail.size());
        assertEquals(StepStatus.FAILED, trail.get(0).getStatus());
        assertEquals(checkpointer.lastCompletedStep(session), null);
        assertEquals(checkpointer.lastFailedStep(session).getStepName(), "iteration-2");
    }

    @Test
    void failedStepDoesNotMoveResumePointAfterRestart() {
        InMemoryCheckpointer checkpointer = new InMemoryCheckpointer();
        AgentStateCollection state = new AgentStateCollection();
        TestSession session = new TestSession("session-failed-resume", "agent-failed-resume", state);
        checkpointer.preAgentExecute(session, null);

        StepCheckpointTrigger trigger = new StepCheckpointTrigger(
                checkpointer, null, StepCheckpointPolicy.STEP_ALWAYS);
        trigger.stepCompleted(session, null, null,
                new StepBoundary("TOOL_CALL", "search", StepStatus.COMPLETED, 10L));
        trigger.stepCompleted(session, null, null,
                new StepBoundary("ITERATION", "iteration-2", StepStatus.FAILED, 20L));

        // A fresh session for the same id restores the state: the resume point
        // is still the completed step, not the failure.
        AgentStateCollection restoredState = new AgentStateCollection();
        TestSession restored = new TestSession("session-failed-resume", "agent-failed-resume", restoredState);
        checkpointer.preAgentExecute(restored, null);

        StepCursor resume = checkpointer.lastCompletedStep(restored);
        assertNotNull(resume);
        assertEquals(1L, resume.getSeq());
        assertEquals("search", resume.getStepName());
        assertEquals(StepStatus.COMPLETED, resume.getStatus());
        StepCursor failed = checkpointer.lastFailedStep(restored);
        assertNotNull(failed);
        assertEquals(StepStatus.FAILED, failed.getStatus());
    }

    @Test
    void failedStepOverwriteKeepsLatestFailure() {
        InMemoryCheckpointer checkpointer = new InMemoryCheckpointer();
        AgentStateCollection state = new AgentStateCollection();
        TestSession session = new TestSession("session-failed-latest", "agent-failed-latest", state);
        checkpointer.preAgentExecute(session, null);

        StepCheckpointTrigger trigger = new StepCheckpointTrigger(
                checkpointer, null, StepCheckpointPolicy.STEP_ALWAYS);
        trigger.stepCompleted(session, null, null,
                new StepBoundary("ITERATION", "iteration-1", StepStatus.FAILED, 10L));
        trigger.stepCompleted(session, null, null,
                new StepBoundary("ITERATION", "iteration-5", StepStatus.FAILED, 20L));

        StepCursor failed = checkpointer.lastFailedStep(session);
        assertNotNull(failed);
        assertEquals("iteration-5", failed.getStepName());
        assertEquals(2L, failed.getSeq());
    }

    private static final class TestSession extends BaseSession {
        private final String sessionId;
        private final String agentId;
        private final SessionStateAccess state;
        private final Config config;

        private TestSession(String sessionId, String agentId, SessionStateAccess state) {
            this.sessionId = sessionId;
            this.agentId = agentId;
            this.state = state;
            this.config = new Config();
        }

        @Override
        public String sessionId() {
            return sessionId;
        }

        @Override
        public SessionStateAccess state() {
            return state;
        }

        @Override
        public Config config() {
            return config;
        }

        @Override
        public Checkpointer checkpointer() {
            return null;
        }

        @SuppressWarnings("unused")
        String agentId() {
            return agentId;
        }
    }
}

/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session.checkpointer.step;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
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

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Tests for the user-initiated pause/resume capability on the step checkpoint
 * layer (issue #117): the PAUSED status round-trip, the dedicated paused-cursor
 * key, the recovery scan skipping paused sessions, the explicit resume entry,
 * and the no-half-write pause persistence ordering.
 */
class StepPauseResumeTest {
    @BeforeEach
    void clearTenantContext() {
        TenantContextHolder.clearCurrentTenant();
    }

    @AfterEach
    void restoreTenantContext() {
        TenantContextHolder.clearCurrentTenant();
    }

    @Test
    void pausedCursorRoundTripsThroughStateMap() {
        StepCursor original = new StepCursor(9L, "TOOL_CALL", "search", StepStatus.PAUSED, 5678L);
        AgentStateCollection state = new AgentStateCollection();
        state.update(Map.of(StepCursor.PAUSED_STATE_KEY, original.toMap()));

        StepCursor restored = StepCursor.pausedFromState(state.getState());
        assertNotNull(restored);
        assertEquals(9L, restored.getSeq());
        assertEquals(StepStatus.PAUSED, restored.getStatus());
        // Paused cursor must be invisible to the normal resume point lookup.
        assertNull(StepCursor.fromState(state.getState()));
    }

    @Test
    void pausedStatusSurvivesSerializedRoundTrip() {
        Map<String, Object> map = new StepCursor(3L, "PHASE", "round-3", StepStatus.PAUSED, 42L).toMap();
        Map<String, Object> copy = new HashMap<>(map);
        StepCursor restored = StepCursor.fromMap(copy);
        assertNotNull(restored);
        assertEquals(StepStatus.PAUSED, restored.getStatus());
    }

    @Test
    void inMemoryPausePersistsCursorAndMetadataInOneSave() {
        InMemoryCheckpointer checkpointer = new InMemoryCheckpointer();
        AgentStateCollection state = new AgentStateCollection();
        TestSession session = new TestSession("session-pause", "agent-pause", state);
        checkpointer.preAgentExecute(session, null);

        state.update(Map.of("progress", "half"));
        checkpointer.stepPaused(session,
                new StepCursor(4L, "TOOL_CALL", "search", StepStatus.COMPLETED, 7L),
                Map.of("pausedBy", "op-1", "reason", "quota"));

        // A fresh session for the same id restores the paused evidence.
        AgentStateCollection restoredState = new AgentStateCollection();
        TestSession restored = new TestSession("session-pause", "agent-pause", restoredState);
        checkpointer.preAgentExecute(restored, null);

        StepCursor paused = checkpointer.lastPausedStep(restored);
        assertNotNull(paused);
        assertEquals(4L, paused.getSeq());
        assertEquals(StepStatus.PAUSED, paused.getStatus());
        // Main resume point stays at the last completed step, untouched by pause.
        assertNull(checkpointer.lastCompletedStep(restored));
        assertEquals("half", restoredState.get("progress"));
        // Pause metadata rides the same snapshot.
        assertEquals("op-1", ((Map<?, ?>) restoredState.get(Checkpointer.SESSION_PAUSE_STATE_KEY)).get("pausedBy"));
    }

    @Test
    void persistencePausePersistsCursorAndMetadataInOneSave() {
        PersistenceCheckpointer checkpointer = new PersistenceCheckpointer(new InMemoryKVStore());
        AgentStateCollection state = new AgentStateCollection();
        TestSession session = new TestSession("session-pause-persist", "agent-pause-persist", state);
        checkpointer.preAgentExecute(session, null);

        checkpointer.stepPaused(session,
                new StepCursor(2L, "PHASE", "round-2", StepStatus.COMPLETED, 9L),
                Map.of("pausedBy", "op-2", "reason", "review"));

        AgentStateCollection restoredState = new AgentStateCollection();
        TestSession restored = new TestSession("session-pause-persist", "agent-pause-persist", restoredState);
        checkpointer.preAgentExecute(restored, null);

        StepCursor paused = checkpointer.lastPausedStep(restored);
        assertNotNull(paused);
        assertEquals(2L, paused.getSeq());
        assertEquals(StepStatus.PAUSED, paused.getStatus());
        assertEquals("review", ((Map<?, ?>) restoredState.get(Checkpointer.SESSION_PAUSE_STATE_KEY)).get("reason"));
    }

    @Test
    void inMemoryScanFindsPausedCursorsOnDedicatedKey() {
        InMemoryCheckpointer checkpointer = new InMemoryCheckpointer();
        AgentStateCollection state = new AgentStateCollection();
        TestSession session = new TestSession("session-scan-pause", "agent-scan-pause", state);
        checkpointer.preAgentExecute(session, null);
        checkpointer.stepCompleted(session,
                new StepCursor(1L, "TOOL_CALL", "search", StepStatus.COMPLETED, 1L));
        checkpointer.stepPaused(session,
                new StepCursor(2L, "PHASE", "round-2", StepStatus.COMPLETED, 2L), null);

        List<StepCursor> paused = checkpointer.scanPausedSteps();
        assertEquals(1, paused.size());
        assertEquals(2L, paused.get(0).getSeq());
        assertEquals(StepStatus.PAUSED, paused.get(0).getStatus());
        // The paused session must never leak into the crash-recovery scan.
        assertTrue(checkpointer.scanRunningSteps().isEmpty());
    }

    @Test
    void recoveryScanSkipsPausedSessions() {
        InMemoryCheckpointer checkpointer = new InMemoryCheckpointer();
        AgentStateCollection state = new AgentStateCollection();
        TestSession session = new TestSession("session-skip-pause", "agent-skip-pause", state);
        checkpointer.preAgentExecute(session, null);
        checkpointer.stepPaused(session,
                new StepCursor(5L, "PHASE", "round-5", StepStatus.COMPLETED, 3L), null);

        StepCheckpointRecoveryService service = new StepCheckpointRecoveryService(checkpointer, null);
        assertTrue(service.recoverLeftovers().isEmpty());
        // The paused session is still intact after the automatic scan.
        assertEquals(1, checkpointer.scanPausedSteps().size());
    }

    @Test
    void resumePausedFlipsCursorBackToRunning() {
        InMemoryCheckpointer checkpointer = new InMemoryCheckpointer();
        AgentStateCollection state = new AgentStateCollection();
        TestSession session = new TestSession("session-resume", "agent-resume", state);
        checkpointer.preAgentExecute(session, null);
        checkpointer.stepCompleted(session,
                new StepCursor(1L, "TOOL_CALL", "search", StepStatus.COMPLETED, 1L));
        checkpointer.stepPaused(session,
                new StepCursor(2L, "PHASE", "round-2", StepStatus.COMPLETED, 2L),
                Map.of("pausedBy", "op-3"));

        StepCheckpointRecoveryService service = new StepCheckpointRecoveryService(checkpointer, null);
        List<StepCursor> paused = service.scanPausedSteps();
        assertEquals(1, paused.size());

        StepCheckpointRecoveryService.RecoveryAction action = service.resumePaused(paused.get(0));
        assertEquals(StepCheckpointRecoveryService.RecoveryActionType.RESUMED_EXPLICITLY, action.type());

        AgentStateCollection restoredState = new AgentStateCollection();
        TestSession restored = new TestSession("session-resume", "agent-resume", restoredState);
        checkpointer.preAgentExecute(restored, null);

        StepCursor resumed = checkpointer.lastCompletedStep(restored);
        assertNotNull(resumed);
        assertEquals(2L, resumed.getSeq());
        assertEquals(StepStatus.RUNNING, resumed.getStatus());
        assertNull(checkpointer.lastPausedStep(restored));
        assertFalse(restoredState.getState().containsKey(Checkpointer.SESSION_PAUSE_STATE_KEY));
    }

    @Test
    void resumePausedIsIdempotentWithinProcess() {
        InMemoryCheckpointer checkpointer = new InMemoryCheckpointer();
        AgentStateCollection state = new AgentStateCollection();
        TestSession session = new TestSession("session-resume-idem", "agent-resume-idem", state);
        checkpointer.preAgentExecute(session, null);
        checkpointer.stepPaused(session,
                new StepCursor(1L, "PHASE", "round-1", StepStatus.COMPLETED, 1L), null);

        StepCheckpointRecoveryService service = new StepCheckpointRecoveryService(checkpointer, null);
        StepCursor paused = service.scanPausedSteps().get(0);
        assertEquals(StepCheckpointRecoveryService.RecoveryActionType.RESUMED_EXPLICITLY,
                service.resumePaused(paused).type());
        assertEquals(StepCheckpointRecoveryService.RecoveryActionType.SKIPPED_ALREADY_PROCESSED,
                service.resumePaused(paused).type());
    }

    @Test
    void resumePausedRejectsUnknownCursor() {
        InMemoryCheckpointer checkpointer = new InMemoryCheckpointer();
        StepCheckpointRecoveryService service = new StepCheckpointRecoveryService(checkpointer, null);
        StepCursor unknown = new StepCursor(99L, "PHASE", "no-such-step", StepStatus.PAUSED, 1L);
        StepCheckpointRecoveryService.RecoveryAction action = service.resumePaused(unknown);
        assertEquals(StepCheckpointRecoveryService.RecoveryActionType.MARKED_LOST, action.type());
        assertThrows(IllegalStateException.class, () -> checkpointer.resumePaused(unknown));
    }

    @Test
    void pauseThenRestartKeepsSessionPausedUntilExplicitResume() {
        PersistenceCheckpointer checkpointer = new PersistenceCheckpointer(new InMemoryKVStore());
        AgentStateCollection state = new AgentStateCollection();
        TestSession session = new TestSession("session-restart", "agent-restart", state);
        checkpointer.preAgentExecute(session, null);
        checkpointer.stepCompleted(session,
                new StepCursor(1L, "TOOL_CALL", "search", StepStatus.COMPLETED, 1L));
        checkpointer.stepPaused(session,
                new StepCursor(2L, "PHASE", "round-2", StepStatus.COMPLETED, 2L), null);

        // Automatic recovery pass after restart: must not touch the paused session.
        StepCheckpointRecoveryService autoService = new StepCheckpointRecoveryService(checkpointer, null);
        assertTrue(autoService.recoverLeftovers().isEmpty());

        // Explicit resume continues the session from the paused boundary.
        StepCheckpointRecoveryService explicitService = new StepCheckpointRecoveryService(checkpointer, null);
        List<StepCursor> paused = explicitService.scanPausedSteps();
        assertEquals(1, paused.size());
        assertEquals(StepCheckpointRecoveryService.RecoveryActionType.RESUMED_EXPLICITLY,
                explicitService.resumePaused(paused.get(0)).type());

        AgentStateCollection restoredState = new AgentStateCollection();
        TestSession restored = new TestSession("session-restart", "agent-restart", restoredState);
        checkpointer.preAgentExecute(restored, null);
        StepCursor resumed = checkpointer.lastCompletedStep(restored);
        assertNotNull(resumed);
        assertEquals(2L, resumed.getSeq());
    }

    @Test
    void pauseFailureInjectionLeavesNoHalfWrite() {
        PersistenceCheckpointer checkpointer = new PersistenceCheckpointer(new InMemoryKVStore());
        AgentStateCollection state = new AgentStateCollection();
        TestSession session = new TestSession("session-halfwrite", "agent-halfwrite", state);
        checkpointer.preAgentExecute(session, null);
        checkpointer.stepCompleted(session,
                new StepCursor(1L, "TOOL_CALL", "search", StepStatus.COMPLETED, 1L));

        // Null-cursor and null-state guards: the save path never half-writes.
        checkpointer.stepPaused(session, null, Map.of("pausedBy", "op-x"));
        assertNull(checkpointer.lastPausedStep(session));

        // Pause without metadata persists the cursor only.
        checkpointer.stepPaused(session,
                new StepCursor(2L, "PHASE", "round-2", StepStatus.COMPLETED, 2L), null);
        assertNotNull(checkpointer.lastPausedStep(session));
        AgentStateCollection restoredState = new AgentStateCollection();
        TestSession restored = new TestSession("session-halfwrite", "agent-halfwrite", restoredState);
        checkpointer.preAgentExecute(restored, null);
        assertFalse(restoredState.getState().containsKey(Checkpointer.SESSION_PAUSE_STATE_KEY));
    }

    @Test
    void inMemoryLastPausedStepBySessionIdResolvesCursor() {
        InMemoryCheckpointer checkpointer = new InMemoryCheckpointer();
        AgentStateCollection state = new AgentStateCollection();
        TestSession session = new TestSession("session-lookup", "agent-lookup", state);
        checkpointer.preAgentExecute(session, null);
        checkpointer.stepPaused(session,
                new StepCursor(7L, "PHASE", "round-7", StepStatus.COMPLETED, 11L), null);

        StepCursor paused = checkpointer.lastPausedStep("session-lookup");
        assertNotNull(paused);
        assertEquals(7L, paused.getSeq());
        assertEquals(StepStatus.PAUSED, paused.getStatus());
        assertNull(checkpointer.lastPausedStep("no-such-session"));
        assertNull(checkpointer.lastPausedStep((String) null));
    }

    @Test
    void persistenceLastPausedStepBySessionIdResolvesCursor() {
        PersistenceCheckpointer checkpointer = new PersistenceCheckpointer(new InMemoryKVStore());
        AgentStateCollection state = new AgentStateCollection();
        TestSession session = new TestSession("session-lookup-persist", "agent-lookup-persist", state);
        checkpointer.preAgentExecute(session, null);
        checkpointer.stepPaused(session,
                new StepCursor(4L, "TOOL_CALL", "search", StepStatus.COMPLETED, 8L), null);

        StepCursor paused = checkpointer.lastPausedStep("session-lookup-persist");
        assertNotNull(paused);
        assertEquals(4L, paused.getSeq());
        assertNull(checkpointer.lastPausedStep("no-such-session"));
    }

    @Test
    void inMemoryResumeBySessionIdFlipsCursorBackToRunning() {
        InMemoryCheckpointer checkpointer = new InMemoryCheckpointer();
        AgentStateCollection state = new AgentStateCollection();
        TestSession session = new TestSession("session-resume-id", "agent-resume-id", state);
        checkpointer.preAgentExecute(session, null);
        checkpointer.stepCompleted(session,
                new StepCursor(1L, "TOOL_CALL", "search", StepStatus.COMPLETED, 1L));
        checkpointer.stepPaused(session,
                new StepCursor(2L, "PHASE", "round-2", StepStatus.COMPLETED, 2L), null);

        StepCheckpointRecoveryService service = new StepCheckpointRecoveryService(checkpointer, null);
        StepCheckpointRecoveryService.RecoveryAction action = service.resumePaused("session-resume-id");
        assertEquals(StepCheckpointRecoveryService.RecoveryActionType.RESUMED_EXPLICITLY, action.type());
        assertEquals(2L, action.cursor().getSeq());

        AgentStateCollection restoredState = new AgentStateCollection();
        TestSession restored = new TestSession("session-resume-id", "agent-resume-id", restoredState);
        checkpointer.preAgentExecute(restored, null);
        StepCursor resumed = checkpointer.lastCompletedStep(restored);
        assertNotNull(resumed);
        assertEquals(2L, resumed.getSeq());
        assertEquals(StepStatus.RUNNING, resumed.getStatus());
        assertNull(checkpointer.lastPausedStep(restored));
        assertNull(checkpointer.lastPausedStep("session-resume-id"));
    }

    @Test
    void persistenceResumeBySessionIdFlipsCursorBackToRunning() {
        PersistenceCheckpointer checkpointer = new PersistenceCheckpointer(new InMemoryKVStore());
        AgentStateCollection state = new AgentStateCollection();
        TestSession session = new TestSession("session-resume-id-persist", "agent-resume-id-persist", state);
        checkpointer.preAgentExecute(session, null);
        checkpointer.stepPaused(session,
                new StepCursor(3L, "PHASE", "round-3", StepStatus.COMPLETED, 5L), null);

        StepCheckpointRecoveryService service = new StepCheckpointRecoveryService(checkpointer, null);
        assertEquals(StepCheckpointRecoveryService.RecoveryActionType.RESUMED_EXPLICITLY,
                service.resumePaused("session-resume-id-persist").type());

        AgentStateCollection restoredState = new AgentStateCollection();
        TestSession restored = new TestSession("session-resume-id-persist", "agent-resume-id-persist", restoredState);
        checkpointer.preAgentExecute(restored, null);
        StepCursor resumed = checkpointer.lastCompletedStep(restored);
        assertNotNull(resumed);
        assertEquals(3L, resumed.getSeq());
        assertEquals(StepStatus.RUNNING, resumed.getStatus());
    }

    @Test
    void resumeBySessionIdRejectsUnknownSession() {
        InMemoryCheckpointer checkpointer = new InMemoryCheckpointer();
        StepCheckpointRecoveryService service = new StepCheckpointRecoveryService(checkpointer, null);
        StepCheckpointRecoveryService.RecoveryAction action = service.resumePaused("no-such-session");
        assertEquals(StepCheckpointRecoveryService.RecoveryActionType.MARKED_LOST, action.type());
        assertThrows(IllegalStateException.class, () -> checkpointer.resumePaused("no-such-session"));
    }

    @Test
    void resumeBySessionIdIsIdempotentWithinProcess() {
        InMemoryCheckpointer checkpointer = new InMemoryCheckpointer();
        AgentStateCollection state = new AgentStateCollection();
        TestSession session = new TestSession("session-resume-idem-id", "agent-resume-idem-id", state);
        checkpointer.preAgentExecute(session, null);
        checkpointer.stepPaused(session,
                new StepCursor(1L, "PHASE", "round-1", StepStatus.COMPLETED, 1L), null);

        StepCheckpointRecoveryService service = new StepCheckpointRecoveryService(checkpointer, null);
        assertEquals(StepCheckpointRecoveryService.RecoveryActionType.RESUMED_EXPLICITLY,
                service.resumePaused("session-resume-idem-id").type());
        // The successful resume cleared the paused evidence, so a repeated
        // id-based resume finds no paused cursor; repeated resume requests
        // are rejected by the runtime state machine before reaching here.
        assertEquals(StepCheckpointRecoveryService.RecoveryActionType.MARKED_LOST,
                service.resumePaused("session-resume-idem-id").type());
    }

    @Test
    void triggerSeqSeedKeepsCursorsIncreasingAcrossInvocations() {
        InMemoryCheckpointer checkpointer = new InMemoryCheckpointer();
        AgentStateCollection state = new AgentStateCollection();
        TestSession session = new TestSession("session-seed", "agent-seed", state);
        checkpointer.preAgentExecute(session, null);
        checkpointer.stepCompleted(session,
                new StepCursor(5L, "PHASE", "round-5", StepStatus.COMPLETED, 1L));

        // A fresh trigger for the next invocation on the same session: the
        // seeded counter must produce a seq beyond the persisted one.
        StepCheckpointTrigger trigger = new StepCheckpointTrigger(checkpointer, null, StepCheckpointPolicy.STEP_ALWAYS);
        StepCursor last = checkpointer.lastCompletedStep(session);
        trigger.seedSeq(last.getSeq());
        trigger.stepCompleted(session, null, null,
                new StepBoundary("PHASE", "round-6", StepStatus.COMPLETED, 0L));

        StepCursor persisted = checkpointer.lastCompletedStep(session);
        assertNotNull(persisted);
        assertEquals(6L, persisted.getSeq());
        assertEquals("round-6", persisted.getStepName());
    }

    @Test
    void triggerSeedIgnoresLowerSeq() {
        InMemoryCheckpointer checkpointer = new InMemoryCheckpointer();
        AgentStateCollection state = new AgentStateCollection();
        TestSession session = new TestSession("session-seed-lower", "agent-seed-lower", state);
        checkpointer.preAgentExecute(session, null);
        StepCheckpointTrigger trigger = new StepCheckpointTrigger(checkpointer, null, StepCheckpointPolicy.STEP_ALWAYS);
        trigger.stepCompleted(session, null, null,
                new StepBoundary("PHASE", "round-1", StepStatus.COMPLETED, 0L));
        trigger.stepCompleted(session, null, null,
                new StepBoundary("PHASE", "round-2", StepStatus.COMPLETED, 0L));
        trigger.seedSeq(1L);

        trigger.stepCompleted(session, null, null,
                new StepBoundary("PHASE", "round-3", StepStatus.COMPLETED, 0L));
        assertEquals(3L, checkpointer.lastCompletedStep(session).getSeq());
    }

    @Test
    void pauseThenResumeBySessionIdKeepsSessionPausedUntilExplicitResume() {
        PersistenceCheckpointer checkpointer = new PersistenceCheckpointer(new InMemoryKVStore());
        AgentStateCollection state = new AgentStateCollection();
        TestSession session = new TestSession("session-restart-id", "agent-restart-id", state);
        checkpointer.preAgentExecute(session, null);
        checkpointer.stepPaused(session,
                new StepCursor(2L, "PHASE", "round-2", StepStatus.COMPLETED, 2L), null);

        // Automatic recovery pass after restart: must not touch the paused session.
        StepCheckpointRecoveryService autoService = new StepCheckpointRecoveryService(checkpointer, null);
        assertTrue(autoService.recoverLeftovers().isEmpty());

        // Explicit resume by id continues the session from the paused boundary.
        StepCheckpointRecoveryService explicitService = new StepCheckpointRecoveryService(checkpointer, null);
        assertEquals(StepCheckpointRecoveryService.RecoveryActionType.RESUMED_EXPLICITLY,
                explicitService.resumePaused("session-restart-id").type());
        assertNull(checkpointer.lastPausedStep("session-restart-id"));
    }

    @Test
    void pauseRequestRegistryPublishesAndConsumesOnce() {
        assertTrue(PauseRequestRegistry.requestPause("session-intent", Map.of("reason", "quota")));
        assertTrue(PauseRequestRegistry.hasPauseRequest("session-intent"));
        // A duplicate publish merges into the pending intent.
        assertFalse(PauseRequestRegistry.requestPause("session-intent", Map.of("reason", "other")));

        Map<String, Object> consumed = PauseRequestRegistry.consumePauseRequest("session-intent");
        assertEquals("quota", consumed.get("reason"));
        assertFalse(PauseRequestRegistry.hasPauseRequest("session-intent"));
        assertNull(PauseRequestRegistry.consumePauseRequest("session-intent"));
        // Blank ids never enter the registry.
        assertFalse(PauseRequestRegistry.requestPause(" ", null));
        assertFalse(PauseRequestRegistry.hasPauseRequest(null));
    }

    @Test
    void clearPauseRequestDiscardsPendingIntent() {
        assertTrue(PauseRequestRegistry.requestPause("session-intent-clear", null));
        PauseRequestRegistry.clearPauseRequest("session-intent-clear");
        assertFalse(PauseRequestRegistry.hasPauseRequest("session-intent-clear"));
    }

    @Test
    void triggerPauseNowPersistsPausedCursorBypassingPolicy() {
        InMemoryCheckpointer checkpointer = new InMemoryCheckpointer();
        AgentStateCollection state = new AgentStateCollection();
        TestSession session = new TestSession("session-pause-now", "agent-pause-now", state);
        checkpointer.preAgentExecute(session, null);
        state.update(Map.of("progress", "half"));
        StepCheckpointTrigger trigger = new StepCheckpointTrigger(checkpointer, null, StepCheckpointPolicy.STEP_OFF);

        trigger.pauseNow(session, "PHASE", "user-pause", Map.of("operator", "op-1", "reason", "quota"));

        // A STEP_OFF policy would normally drop every cursor, but pause
        // evidence must land regardless of the verdict.
        StepCursor paused = checkpointer.lastPausedStep(session);
        assertNotNull(paused);
        assertEquals(StepStatus.PAUSED, paused.getStatus());
        assertEquals(1L, paused.getSeq());
        assertEquals("user-pause", paused.getStepName());
        assertEquals("half", state.get("progress"));
        Map<?, ?> pauseMeta = (Map<?, ?>) state.get(Checkpointer.SESSION_PAUSE_STATE_KEY);
        assertEquals("op-1", pauseMeta.get("operator"));
        // The main resume point stays untouched by the pause.
        assertNull(checkpointer.lastCompletedStep(session));

        // A second pause on the same trigger keeps the seq increasing.
        trigger.pauseNow(session, "TOOL_CALL", "search", null);
        assertEquals(2L, checkpointer.lastPausedStep(session).getSeq());
    }

    @Test
    void triggerPauseNowWithoutSessionIsNoOp() {
        StepCheckpointTrigger trigger = new StepCheckpointTrigger(new InMemoryCheckpointer(), null,
                StepCheckpointPolicy.STEP_ALWAYS);
        assertDoesNotThrow(() -> trigger.pauseNow(null, "PHASE", "user-pause", null));
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

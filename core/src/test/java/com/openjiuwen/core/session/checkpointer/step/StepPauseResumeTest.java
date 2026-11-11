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
import com.openjiuwen.core.multitenant.TenantContext;
import com.openjiuwen.core.multitenant.TenantContextHolder;
import com.openjiuwen.core.session.BaseSession;
import com.openjiuwen.core.session.checkpointer.Checkpointer;
import com.openjiuwen.core.session.checkpointer.InMemoryCheckpointer;
import com.openjiuwen.core.session.checkpointer.PersistenceCheckpointer;
import com.openjiuwen.core.session.config.Config;
import com.openjiuwen.core.session.state.AgentStateCollection;
import com.openjiuwen.core.session.state.State;
import com.openjiuwen.core.session.state.SessionStateAccess;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
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
    void pauseRequestRegistryIsolatesTenantsOnTheSameSessionId() {
        try {
            TenantContextHolder.setCurrentTenant(TenantContext.builder().tenantId("tenant-a").build());
            assertTrue(PauseRequestRegistry.requestPause("session-multi-tenant", Map.of("reason", "a")));
            assertTrue(PauseRequestRegistry.hasPauseRequest("session-multi-tenant"));

            // Same session id under another tenant: no shared intent.
            TenantContextHolder.setCurrentTenant(TenantContext.builder().tenantId("tenant-b").build());
            assertFalse(PauseRequestRegistry.hasPauseRequest("session-multi-tenant"));
            assertTrue(PauseRequestRegistry.requestPause("session-multi-tenant", Map.of("reason", "b")));

            // Tenant A's intent is still intact and carries A's metadata.
            TenantContextHolder.setCurrentTenant(TenantContext.builder().tenantId("tenant-a").build());
            Map<String, Object> consumed = PauseRequestRegistry.consumePauseRequest("session-multi-tenant");
            assertEquals("a", consumed.get("reason"));
        } finally {
            TenantContextHolder.clearCurrentTenant();
        }
    }

    @Test
    void stepPausedReportsWhetherThePauseEvidenceWasPersisted() {
        InMemoryCheckpointer checkpointer = new InMemoryCheckpointer();
        AgentStateCollection state = new AgentStateCollection();
        TestSession session = new TestSession("session-pause-bool", "agent-pause-bool", state);
        checkpointer.preAgentExecute(session, null);
        checkpointer.stepCompleted(session,
                new StepCursor(3L, "PHASE", "round-3", StepStatus.COMPLETED, 1L));

        // A stale paused cursor is rejected by the monotonic guard and
        // reported as "not persisted" instead of silently succeeding.
        boolean isStalePersisted = checkpointer.stepPaused(session,
                new StepCursor(2L, "PHASE", "user-pause", StepStatus.PAUSED, 2L), null);
        assertFalse(isStalePersisted);
        assertNull(checkpointer.lastPausedStep(session));

        // A cursor past the resume point lands and reports persisted.
        boolean isFreshPersisted = checkpointer.stepPaused(session,
                new StepCursor(4L, "PHASE", "user-pause", StepStatus.PAUSED, 3L),
                Map.of("operator", "op-bool"));
        assertTrue(isFreshPersisted);
        assertNotNull(checkpointer.lastPausedStep(session));
    }

    @Test
    void runningScanSkipsSessionsThatCarryPauseEvidence() {
        InMemoryCheckpointer checkpointer = new InMemoryCheckpointer();
        AgentStateCollection pausedState = new AgentStateCollection();
        TestSession paused = new TestSession("session-paused-not-lost", "agent-paused-not-lost", pausedState);
        checkpointer.preAgentExecute(paused, null);
        // Interrupted boundary: the main cursor stays RUNNING, then the user
        // pauses. The automatic scan must not report this session as lost.
        checkpointer.stepCompleted(paused,
                new StepCursor(1L, "PHASE", "round-1", StepStatus.RUNNING, 1L));
        checkpointer.stepPaused(paused,
                new StepCursor(2L, "PHASE", "user-pause", StepStatus.PAUSED, 2L),
                Map.of("operator", "op-scan"));

        assertTrue(checkpointer.scanRunningSteps().isEmpty());
        assertEquals(1, checkpointer.scanPausedSteps().size());

        // A genuinely running session still shows up in the scan.
        AgentStateCollection runningState = new AgentStateCollection();
        TestSession running = new TestSession("session-really-running", "agent-really-running", runningState);
        checkpointer.preAgentExecute(running, null);
        checkpointer.stepCompleted(running,
                new StepCursor(1L, "PHASE", "round-1", StepStatus.RUNNING, 1L));
        List<StepCursor> stillRunning = checkpointer.scanRunningSteps();
        assertEquals(1, stillRunning.size());
        assertEquals("session-really-running", stillRunning.get(0).getSessionId() == null
                || stillRunning.get(0).getSessionId().isEmpty()
                        ? "session-really-running"
                        : stillRunning.get(0).getSessionId());
    }

    @Test
    void persistenceRunningScanSkipsSessionsThatCarryPauseEvidence() {
        PersistenceCheckpointer checkpointer = new PersistenceCheckpointer(new InMemoryKVStore());
        AgentStateCollection state = new AgentStateCollection();
        TestSession session = new TestSession("session-persist-not-lost", "agent-persist-not-lost", state);
        checkpointer.preAgentExecute(session, null);
        checkpointer.stepCompleted(session,
                new StepCursor(1L, "PHASE", "round-1", StepStatus.RUNNING, 1L));
        checkpointer.stepPaused(session,
                new StepCursor(2L, "PHASE", "user-pause", StepStatus.PAUSED, 2L), null);

        assertTrue(checkpointer.scanRunningSteps().isEmpty());
        assertEquals(1, checkpointer.scanPausedSteps().size());
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

    @Test
    void pausedCursorCarriesOwningSessionId() {
        InMemoryCheckpointer checkpointer = new InMemoryCheckpointer();
        AgentStateCollection state = new AgentStateCollection();
        TestSession session = new TestSession("session-owner", "agent-owner", state);
        checkpointer.preAgentExecute(session, null);

        checkpointer.stepPaused(session,
                new StepCursor(1L, "PHASE", "round-1", StepStatus.COMPLETED, 1L), null);

        // The persisted cursor records the owning session so whole-store
        // resume matching never depends on seq alone.
        assertEquals("session-owner", checkpointer.lastPausedStep(session).getSessionId());
        assertEquals("session-owner", checkpointer.lastPausedStep("session-owner").getSessionId());
    }

    @Test
    void resumeByCursorWithoutSessionIdIsRejected() {
        InMemoryCheckpointer checkpointer = new InMemoryCheckpointer();
        StepCursor anonymous = new StepCursor(9L, "PHASE", "user-pause", StepStatus.PAUSED, 1L);
        assertThrows(IllegalStateException.class, () -> checkpointer.resumePaused(anonymous));
    }

    @Test
    void resumeByCursorOfOneSessionLeavesOtherPausedSessionsUntouched() {
        InMemoryCheckpointer checkpointer = new InMemoryCheckpointer();
        AgentStateCollection stateA = new AgentStateCollection();
        TestSession sessionA = new TestSession("session-pair-a", "agent-pair-a", stateA);
        checkpointer.preAgentExecute(sessionA, null);
        checkpointer.stepPaused(sessionA,
                new StepCursor(1L, "PHASE", "user-pause", StepStatus.COMPLETED, 1L), null);
        AgentStateCollection stateB = new AgentStateCollection();
        TestSession sessionB = new TestSession("session-pair-b", "agent-pair-b", stateB);
        checkpointer.preAgentExecute(sessionB, null);
        checkpointer.stepPaused(sessionB,
                new StepCursor(1L, "PHASE", "user-pause", StepStatus.COMPLETED, 1L), null);
        // Two identical (seq, stepName) cursors: the resume must hit exactly
        // the session named by the cursor, never the store iteration order.
        StepCursor pausedA = checkpointer.lastPausedStep("session-pair-a");
        assertEquals(StepStatus.PAUSED, checkpointer.lastPausedStep("session-pair-b").getStatus());

        checkpointer.resumePaused(pausedA);

        assertNull(checkpointer.lastPausedStep("session-pair-a"));
        assertNotNull(checkpointer.lastPausedStep("session-pair-b"));
    }

    @Test
    void stalePauseSeqDoesNotRollTheResumePointBack() {
        InMemoryCheckpointer checkpointer = new InMemoryCheckpointer();
        AgentStateCollection state = new AgentStateCollection();
        TestSession session = new TestSession("session-stale-pause", "agent-stale-pause", state);
        checkpointer.preAgentExecute(session, null);
        checkpointer.stepCompleted(session,
                new StepCursor(5L, "PHASE", "round-5", StepStatus.COMPLETED, 1L));

        // A pause cursor at or below the persisted seq is rejected: an old
        // pause must not overwrite a newer resume point.
        checkpointer.stepPaused(session,
                new StepCursor(5L, "PHASE", "stale-pause", StepStatus.COMPLETED, 2L), null);
        assertNull(checkpointer.lastPausedStep(session));
        checkpointer.stepPaused(session,
                new StepCursor(3L, "PHASE", "older-pause", StepStatus.COMPLETED, 2L), null);
        assertNull(checkpointer.lastPausedStep(session));
        assertEquals(5L, checkpointer.lastCompletedStep(session).getSeq());
    }

    @Test
    void clearPausedFromStateClearsTheAgentPartition() {
        Map<String, Object> partition = new HashMap<>();
        partition.put(StepCursor.PAUSED_STATE_KEY,
                new StepCursor(1L, "PHASE", "user-pause", StepStatus.PAUSED, 1L).toMap());
        Map<String, Object> snapshot = new HashMap<>();
        snapshot.put(State.AGENT_STATE_KEY, partition);

        assertTrue(StepCursor.clearPausedFromState(snapshot));
        assertNull(StepCursor.pausedFromState(snapshot));
        // The mutation must land on the caller-held map, not a discarded copy.
        @SuppressWarnings("unchecked")
        Map<String, Object> written = (Map<String, Object>) snapshot.get(State.AGENT_STATE_KEY);
        assertTrue(written.containsKey(StepCursor.PAUSED_STATE_KEY));
        assertNull(written.get(StepCursor.PAUSED_STATE_KEY));
    }

    @Test
    void requestPauseToleratesNullMetadataValues() {
        Map<String, Object> meta = new HashMap<>();
        meta.put("operator", "op-null-test");
        meta.put("reason", null);
        assertTrue(PauseRequestRegistry.requestPause("session-null-meta", meta));
        Map<String, Object> consumed = PauseRequestRegistry.consumePauseRequest("session-null-meta");
        assertEquals("op-null-test", consumed.get("operator"));
        // Null-valued entries are dropped, not a NPE.
        assertFalse(consumed.containsKey("reason"));
    }

    @Test
    void secondPauseAtLaterBoundaryOverwritesTheFirst() {
        InMemoryCheckpointer checkpointer = new InMemoryCheckpointer();
        AgentStateCollection state = new AgentStateCollection();
        TestSession session = new TestSession("session-repause", "agent-repause", state);
        checkpointer.preAgentExecute(session, null);
        checkpointer.stepPaused(session,
                new StepCursor(1L, "PHASE", "user-pause", StepStatus.COMPLETED, 1L), null);
        checkpointer.stepPaused(session,
                new StepCursor(2L, "PHASE", "user-pause", StepStatus.COMPLETED, 2L), null);
        StepCursor paused = checkpointer.lastPausedStep(session);
        assertEquals(2L, paused.getSeq());
        assertEquals("session-repause", paused.getSessionId());
    }

    @Test
    void explicitResumeGuardsRunningContinuationFromAutomaticScan() {
        InMemoryCheckpointer checkpointer = new InMemoryCheckpointer();
        AgentStateCollection state = new AgentStateCollection();
        TestSession session = new TestSession("session-resume-scan", "agent-resume-scan", state);
        checkpointer.preAgentExecute(session, null);
        checkpointer.stepCompleted(session,
                new StepCursor(1L, "PHASE", "round-1", StepStatus.RUNNING, 1L));
        checkpointer.stepPaused(session,
                new StepCursor(2L, "PHASE", "user-pause", StepStatus.PAUSED, 2L), null);

        StepCheckpointRecoveryService service = new StepCheckpointRecoveryService(checkpointer, null);
        StepCursor paused = service.scanPausedSteps().get(0);
        assertEquals(StepCheckpointRecoveryService.RecoveryActionType.RESUMED_EXPLICITLY,
                service.resumePaused(paused).type());

        // The resume left the main cursor RUNNING at the paused seq, so the
        // automatic scan sees the continuation; the explicit resume and the
        // scan share one marker key space, so the continuation must be
        // skipped instead of being marked lost as a crash leftover.
        List<StepCheckpointRecoveryService.RecoveryAction> actions = service.recoverLeftovers();
        assertEquals(1, actions.size());
        assertEquals(StepCheckpointRecoveryService.RecoveryActionType.SKIPPED_ALREADY_PROCESSED,
                actions.get(0).type());

        // The skip left the resumed continuation intact.
        AgentStateCollection restoredState = new AgentStateCollection();
        TestSession restored = new TestSession("session-resume-scan", "agent-resume-scan", restoredState);
        checkpointer.preAgentExecute(restored, null);
        StepCursor resumed = checkpointer.lastCompletedStep(restored);
        assertNotNull(resumed);
        assertEquals(StepStatus.RUNNING, resumed.getStatus());
        assertEquals(2L, resumed.getSeq());
    }

    @Test
    void persistenceExplicitResumeGuardsRunningContinuationFromAutomaticScan() {
        PersistenceCheckpointer checkpointer = new PersistenceCheckpointer(new InMemoryKVStore());
        AgentStateCollection state = new AgentStateCollection();
        TestSession session = new TestSession("session-resume-scan-persist", "agent-resume-scan-persist", state);
        checkpointer.preAgentExecute(session, null);
        checkpointer.stepCompleted(session,
                new StepCursor(1L, "PHASE", "round-1", StepStatus.RUNNING, 1L));
        checkpointer.stepPaused(session,
                new StepCursor(2L, "PHASE", "user-pause", StepStatus.PAUSED, 2L), null);

        StepCheckpointRecoveryService service = new StepCheckpointRecoveryService(checkpointer, null);
        StepCursor paused = service.scanPausedSteps().get(0);
        assertEquals(StepCheckpointRecoveryService.RecoveryActionType.RESUMED_EXPLICITLY,
                service.resumePaused(paused).type());

        List<StepCheckpointRecoveryService.RecoveryAction> actions = service.recoverLeftovers();
        assertEquals(1, actions.size());
        assertEquals(StepCheckpointRecoveryService.RecoveryActionType.SKIPPED_ALREADY_PROCESSED,
                actions.get(0).type());

        AgentStateCollection restoredState = new AgentStateCollection();
        TestSession restored = new TestSession("session-resume-scan-persist",
                "agent-resume-scan-persist", restoredState);
        checkpointer.preAgentExecute(restored, null);
        StepCursor resumed = checkpointer.lastCompletedStep(restored);
        assertNotNull(resumed);
        assertEquals(StepStatus.RUNNING, resumed.getStatus());
        assertEquals(2L, resumed.getSeq());
    }

    @Test
    void triggerPauseNowSeedsFromPersistedResumePoint() {
        InMemoryCheckpointer checkpointer = new InMemoryCheckpointer();
        AgentStateCollection state = new AgentStateCollection();
        TestSession session = new TestSession("session-pause-seed", "agent-pause-seed", state);
        checkpointer.preAgentExecute(session, null);
        checkpointer.stepCompleted(session,
                new StepCursor(5L, "PHASE", "round-5", StepStatus.COMPLETED, 1L));

        // A fresh trigger that was never seeded: pauseNow must lift its
        // counter from the persisted resume point before minting the paused
        // cursor, or the stale-seq guard would silently drop the pause.
        StepCheckpointTrigger trigger = new StepCheckpointTrigger(checkpointer, null, StepCheckpointPolicy.STEP_ALWAYS);
        assertTrue(trigger.pauseNow(session, "PHASE", "user-pause", Map.of("operator", "op-seed")));

        StepCursor paused = checkpointer.lastPausedStep(session);
        assertNotNull(paused);
        assertEquals(6L, paused.getSeq());
        assertEquals(StepStatus.PAUSED, paused.getStatus());
    }

    @Test
    void triggerPauseNowReportsFalseWhenTheStoreHoldsNewerPauseEvidence() {
        InMemoryCheckpointer checkpointer = new InMemoryCheckpointer();
        AgentStateCollection state = new AgentStateCollection();
        TestSession session = new TestSession("session-pause-stale", "agent-pause-stale", state);
        checkpointer.preAgentExecute(session, null);
        checkpointer.stepCompleted(session,
                new StepCursor(2L, "PHASE", "round-2", StepStatus.COMPLETED, 1L));
        checkpointer.stepPaused(session,
                new StepCursor(4L, "PHASE", "user-pause", StepStatus.PAUSED, 2L), null);

        // The counter seeds to the completed resume point (2) and mints 3,
        // but the store already holds pause evidence at seq 4: the stale-seq
        // guard rejects the write and pauseNow must report it.
        StepCheckpointTrigger trigger = new StepCheckpointTrigger(checkpointer, null, StepCheckpointPolicy.STEP_ALWAYS);
        assertFalse(trigger.pauseNow(session, "PHASE", "user-pause", null));

        // The rejected write left the stored pause evidence untouched.
        assertEquals(4L, checkpointer.lastPausedStep(session).getSeq());
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

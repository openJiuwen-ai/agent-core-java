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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Tests the journal audit trail and storage-surface guarantees of the
 * step-level checkpoint layer: seq monotonicity, journal append-only
 * semantics, retention boundary decisions, purge safety for resume data,
 * atomic cursor-in-snapshot persistence, and the fixed storage key surface.
 */
class StepCheckpointAuditTrailTest {
    private InMemoryKVStore kvStore;
    private PersistenceCheckpointer checkpointer;
    private KvStepJournal journal;

    @BeforeEach
    void setUp() {
        TenantContextHolder.clearCurrentTenant();
        kvStore = new InMemoryKVStore();
        checkpointer = new PersistenceCheckpointer(kvStore);
        journal = new KvStepJournal(kvStore);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clearCurrentTenant();
    }

    @Test
    void seqStartsAtOneAndIncreasesStrictlyAcrossSteps() {
        TestSession session = newSession("session-seq");
        StepCheckpointTrigger trigger = new StepCheckpointTrigger(
                checkpointer, journal, StepCheckpointPolicy.STEP_ALWAYS);

        trigger.stepCompleted(session, null, null,
                new StepBoundary("TOOL_CALL", "search", StepStatus.COMPLETED, 10L));
        trigger.stepCompleted(session, null, null,
                new StepBoundary("LLM_RESPONSE", "model-reply", StepStatus.COMPLETED, 20L));
        trigger.stepCompleted(session, null, null,
                new StepBoundary("PHASE", "plan", StepStatus.COMPLETED, 30L));

        List<StepRecord> trail = journal.replay("session-seq");
        assertEquals(3, trail.size());
        for (int i = 0; i < trail.size(); i++) {
            assertEquals(i + 1L, trail.get(i).getSeq(),
                    "seq must start at 1 and increase by exactly one per boundary");
        }
        StepCursor resume = checkpointer.lastCompletedStep(session);
        assertNotNull(resume);
        assertEquals(3L, resume.getSeq());
        assertEquals("plan", resume.getStepName());
        assertEquals(trail.get(2).getSeq(), resume.getSeq(),
                "audit seq must align with the last persisted cursor");
    }

    @Test
    void recoveryNeverRewritesOrConsumesAuditTrail() {
        TestSession session = newSession("session-appendonly");
        StepCheckpointTrigger trigger = new StepCheckpointTrigger(
                checkpointer, journal, StepCheckpointPolicy.STEP_ALWAYS);
        trigger.stepCompleted(session, null, null,
                new StepBoundary("TOOL_CALL", "search", StepStatus.COMPLETED, 10L));
        trigger.stepCompleted(session, null, null,
                new StepBoundary("TOOL_CALL", "fetch", StepStatus.COMPLETED, 20L));
        List<StepRecord> beforeRecovery = journal.replay("session-appendonly");

        // Simulated restart: a fresh session restores the snapshot and walks
        // the recovery pipeline; resume must come from the cursor + snapshot,
        // never from the audit trail.
        TestSession restored = newSession("session-appendonly");
        checkpointer.preAgentExecute(restored, null);
        StepCheckpointRecoveryService recovery = new StepCheckpointRecoveryService(checkpointer, null);
        List<StepCheckpointRecoveryService.RecoveryAction> actions = recovery.recoverLeftovers();

        assertTrue(actions.isEmpty(), "COMPLETED cursor is not a leftover");
        StepCursor resume = checkpointer.lastCompletedStep(restored);
        assertNotNull(resume);
        assertEquals(2L, resume.getSeq());
        assertEquals("fetch", resume.getStepName());
        List<StepRecord> afterRecovery = journal.replay("session-appendonly");
        assertSameTrail(beforeRecovery, afterRecovery);
    }

    @Test
    void purgeKeepsBoundaryMinusEpsilonAndRemovesExpiredRecords() {
        long now = System.currentTimeMillis();
        journal.append(new StepRecord("session-critical", "agent", 1L, "TOOL_CALL", "old-step",
                "", StepStatus.COMPLETED, now - 100_000L));
        journal.append(new StepRecord("session-critical", "agent", 2L, "TOOL_CALL", "edge-step",
                "", StepStatus.COMPLETED, now - 60_000L));

        // Retention 60s: records strictly older than the cutoff are removed;
        // a record aged exactly the retention window (cutoff boundary) is
        // kept - the boundary is exclusive on the removal side.
        int purged = journal.purgeBefore(now - 60_000L);

        assertEquals(1, purged, "the 100s-old record is past the cutoff");
        List<StepRecord> trail = journal.replay("session-critical");
        assertEquals(1, trail.size());
        assertEquals("edge-step", trail.get(0).getStepName());
        assertEquals(2L, trail.get(0).getSeq());
    }

    @Test
    void replayAfterPartialPurgeReturnsOnlySurvivingRecords() {
        long now = System.currentTimeMillis();
        journal.append(new StepRecord("session-partial", "agent", 1L, "TOOL_CALL", "expired",
                "", StepStatus.COMPLETED, now - 200_000L));
        journal.append(new StepRecord("session-partial", "agent", 2L, "TOOL_CALL", "kept",
                "", StepStatus.COMPLETED, now - 10_000L));
        journal.append(new StepRecord("session-partial", "agent", 3L, "TOOL_CALL", "latest",
                "", StepStatus.COMPLETED, now));

        journal.purgeBefore(now - 60_000L);

        List<StepRecord> trail = journal.replay("session-partial");
        assertEquals(2, trail.size());
        assertEquals(2L, trail.get(0).getSeq());
        assertEquals(3L, trail.get(1).getSeq());
        assertTrue(trail.stream().allMatch(record -> "expired".equals(record.getStepName()) == false));
        assertEquals("kept", trail.get(0).getStepName());
        assertEquals("latest", trail.get(1).getStepName());
    }

    @Test
    void purgeSparesCursorAndSnapshotOfActiveSession() {
        TestSession session = newSession("session-purge-safe");
        StepCheckpointTrigger trigger = new StepCheckpointTrigger(
                checkpointer, journal, StepCheckpointPolicy.STEP_ALWAYS);
        trigger.stepCompleted(session, null, null,
                new StepBoundary("TOOL_CALL", "search", StepStatus.COMPLETED, 10L));
        trigger.stepCompleted(session, null, null,
                new StepBoundary("TOOL_CALL", "fetch", StepStatus.COMPLETED, 20L));

        // The audit trail ages out entirely; the resume data must survive.
        journal.purgeBefore(System.currentTimeMillis() + 1L);
        assertTrue(journal.replay("session-purge-safe").isEmpty());

        TestSession restored = newSession("session-purge-safe");
        checkpointer.preAgentExecute(restored, null);
        StepCursor resume = checkpointer.lastCompletedStep(restored);
        assertNotNull(resume, "purge must not touch the cursor inside the state snapshot");
        assertEquals(2L, resume.getSeq());
        assertEquals("fetch", resume.getStepName());
        assertEquals(StepStatus.COMPLETED, resume.getStatus());
    }

    @Test
    void cursorLivesInsideTheStateSnapshotBlob() {
        TestSession session = newSession("session-atomic");
        StepCheckpointTrigger trigger = new StepCheckpointTrigger(
                checkpointer, journal, StepCheckpointPolicy.STEP_ALWAYS);
        trigger.stepCompleted(session, null, null,
                new StepBoundary("TOOL_CALL", "search", StepStatus.COMPLETED, 10L));

        Map<String, Object> keys = kvStore.getByPrefix("session-atomic").join();
        assertTrue(keys.containsKey("session-atomic:agent::agent_state_blobs"),
                "state must persist under the pre-existing agent_state_blobs key");
        assertTrue(keys.containsKey("session-atomic:agent::agent_state_blobs_dump_type"));
        assertFalse(keys.containsKey("session-atomic:step_cursor"),
                "the cursor must not get an independent top-level key");
        assertEquals(2, keys.size(),
                "step checkpointing must not widen the pre-existing storage surface");

        TestSession restored = newSession("session-atomic");
        checkpointer.preAgentExecute(restored, null);
        StepCursor resume = checkpointer.lastCompletedStep(restored);
        assertNotNull(resume);
        assertEquals(1L, resume.getSeq());
        assertEquals("search", resume.getStepName());
    }

    @Test
    void snapshotKeepsLargeStateFieldsUnsanitized() {
        TestSession session = newSession("session-full-snapshot");
        StringBuilder large = new StringBuilder();
        for (int i = 0; i < 5_000; i++) {
            large.append("x");
        }
        session.state().update(Map.of("big_field", large.toString()));
        StepCheckpointTrigger trigger = new StepCheckpointTrigger(
                checkpointer, journal, StepCheckpointPolicy.STEP_ALWAYS);
        trigger.stepCompleted(session, null, null,
                new StepBoundary("TOOL_CALL", "search", StepStatus.COMPLETED, 10L));

        TestSession restored = newSession("session-full-snapshot");
        checkpointer.preAgentExecute(restored, null);
        Object field = restored.state().get("big_field");
        assertNotNull(field);
        assertEquals(large.toString(), field,
                "state snapshots must keep fields verbatim; only the audit trail sanitizes");
        assertEquals(5_000, field.toString().length());
    }

    @Test
    void persistenceAndInMemoryBackendsPersistIdenticalCursorContent() {
        InMemoryCheckpointer memoryBackend = new InMemoryCheckpointer();
        TestSession persistedSession = newSession("session-media");
        TestSession memorySession = new TestSession("session-media", new AgentStateCollection());

        StepCheckpointTrigger persistedTrigger = new StepCheckpointTrigger(
                checkpointer, journal, StepCheckpointPolicy.STEP_ALWAYS);
        StepCheckpointTrigger memoryTrigger = new StepCheckpointTrigger(
                memoryBackend, new KvStepJournal(new InMemoryKVStore()), StepCheckpointPolicy.STEP_ALWAYS);
        memoryBackend.preAgentExecute(memorySession, null);

        for (StepCheckpointTrigger trigger : List.of(persistedTrigger, memoryTrigger)) {
            BaseSession target = trigger == persistedTrigger ? persistedSession : memorySession;
            trigger.stepCompleted(target, null, null,
                    new StepBoundary("TOOL_CALL", "search", StepStatus.COMPLETED, 10L));
            trigger.stepCompleted(target, null, null,
                    new StepBoundary("PHASE", "plan", StepStatus.COMPLETED, 20L));
        }

        TestSession restoredPersisted = newSession("session-media");
        checkpointer.preAgentExecute(restoredPersisted, null);
        TestSession restoredMemory = new TestSession("session-media", new AgentStateCollection());
        memoryBackend.preAgentExecute(restoredMemory, null);

        StepCursor fromPersisted = checkpointer.lastCompletedStep(restoredPersisted);
        StepCursor fromMemory = memoryBackend.lastCompletedStep(restoredMemory);
        assertNotNull(fromPersisted);
        assertNotNull(fromMemory);
        assertEquals(fromPersisted.getSeq(), fromMemory.getSeq(),
                "both backends must return the same cursor content for the same step sequence");
        assertEquals(fromPersisted.getStepType(), fromMemory.getStepType());
        assertEquals(fromPersisted.getStepName(), fromMemory.getStepName());
        assertEquals(fromPersisted.getStatus(), fromMemory.getStatus());
        assertEquals(2L, fromPersisted.getSeq());
        assertEquals("plan", fromPersisted.getStepName());
        assertEquals(StepStatus.COMPLETED, fromPersisted.getStatus());
    }

    @Test
    void recoveryTreatsEachLeftoverSessionIndependently() {
        List<TestSession> crashed = new ArrayList<>();
        for (int i = 1; i <= 3; i++) {
            TestSession session = newSession("session-leftover-" + i);
            session.state().update(Map.of("progress", "round-" + i));
            checkpointer.stepCompleted(session,
                    new StepCursor(i, "TOOL_CALL", "tool-" + i, StepStatus.RUNNING, 100L + i));
            crashed.add(session);
        }

        StepCheckpointRecoveryService service = new StepCheckpointRecoveryService(checkpointer, null);
        List<StepCheckpointRecoveryService.RecoveryAction> actions = service.recoverLeftovers();

        assertEquals(3, actions.size());
        for (int i = 0; i < 3; i++) {
            assertEquals(StepCheckpointRecoveryService.RecoveryActionType.MARKED_LOST, actions.get(i).type());
            assertEquals(i + 1L, actions.get(i).cursor().getSeq());
            assertEquals("tool-" + (i + 1), actions.get(i).cursor().getStepName());
        }
        for (int i = 1; i <= 3; i++) {
            TestSession restored = newSession("session-leftover-" + i);
            checkpointer.preAgentExecute(restored, null);
            assertEquals("round-" + i, restored.state().get("progress"),
                    "each session's state must restore independently, untouched by the others");
        }
    }

    @Test
    void hookPoliciesDriveDistinctRecoveryActions() {
        StepCursor cursor = new StepCursor(1L, "TOOL_CALL", "search", StepStatus.RUNNING, 10L);

        StepCheckpointRecoveryService markedFailed = new StepCheckpointRecoveryService(checkpointer,
                request -> StepRecoveryHook.RecoveryResult.markedFailed("business-rejected"));
        assertEquals(StepCheckpointRecoveryService.RecoveryActionType.MARKED_FAILED_BY_HOOK,
                markedFailed.recoverOne(cursor).type());

        StepCheckpointRecoveryService notHandled = new StepCheckpointRecoveryService(checkpointer,
                request -> StepRecoveryHook.RecoveryResult.notHandled("no-policy"));
        assertEquals(StepCheckpointRecoveryService.RecoveryActionType.MARKED_LOST,
                notHandled.recoverOne(cursor).type(),
                "a declined hook must fall back to the safe markLost default");

        StepCheckpointRecoveryService recovered = new StepCheckpointRecoveryService(checkpointer,
                request -> StepRecoveryHook.RecoveryResult.recovered("resumed"));
        StepCheckpointRecoveryService.RecoveryAction action = recovered.recoverOne(cursor);
        assertEquals(StepCheckpointRecoveryService.RecoveryActionType.RECOVERED_BY_HOOK, action.type());
        assertEquals("resumed", action.detail());
    }

    @Test
    void wipingTheAuditJournalDoesNotBreakResume() {
        TestSession session = newSession("session-journal-wipe");
        StepCheckpointTrigger trigger = new StepCheckpointTrigger(
                checkpointer, journal, StepCheckpointPolicy.STEP_ALWAYS);
        trigger.stepCompleted(session, null, null,
                new StepBoundary("TOOL_CALL", "search", StepStatus.COMPLETED, 10L));
        trigger.stepCompleted(session, null, null,
                new StepBoundary("TOOL_CALL", "fetch", StepStatus.COMPLETED, 20L));

        // Destroy the entire audit trail, as if the journal store was lost.
        int wiped = journal.purgeBefore(System.currentTimeMillis() + 1L);
        assertEquals(2, wiped);
        assertTrue(journal.replay("session-journal-wipe").isEmpty());

        TestSession restored = newSession("session-journal-wipe");
        checkpointer.preAgentExecute(restored, null);
        StepCursor resume = checkpointer.lastCompletedStep(restored);
        assertNotNull(resume, "resume must come from the cursor inside the snapshot, not the journal");
        assertEquals(2L, resume.getSeq());
        assertEquals("fetch", resume.getStepName());
    }

    @Test
    void interruptSnapshotRestoresCursorAndCustomStateTogether() {
        // STEP_OFF: no per-step persistence, the interrupt snapshot is the
        // only write - the cursor must ride inside it (the interrupt point
        // persists unconditionally, even under STEP_OFF).
        TestSession session = newSession("session-interrupt");
        StepCheckpointTrigger trigger = new StepCheckpointTrigger(
                checkpointer, journal, StepCheckpointPolicy.STEP_OFF);
        trigger.stepCompleted(session, null, null,
                new StepBoundary("TOOL_CALL", "search", StepStatus.COMPLETED, 10L));
        assertNull(StepCursor.fromState(session.state().getState()),
                "STEP_OFF must not persist a step cursor");

        session.state().update(Map.of(StepCursor.STATE_KEY,
                new StepCursor(1L, "TOOL_CALL", "search", StepStatus.COMPLETED, 10L).toMap()));
        session.state().update(Map.of("pending_approval", "tool-call-2"));
        checkpointer.interruptAgentExecute(session);

        // Next-round restore: cursor and interruption payload came out of the
        // same snapshot write, so both survive the restart together.
        TestSession nextRound = newSession("session-interrupt");
        checkpointer.preAgentExecute(nextRound, null);
        assertEquals("tool-call-2", nextRound.state().get("pending_approval"));
        StepCursor cursor = checkpointer.lastCompletedStep(nextRound);
        assertNotNull(cursor, "the interrupt snapshot must keep the step cursor");
        assertEquals(1L, cursor.getSeq());
        assertEquals("search", cursor.getStepName());
    }

    private TestSession newSession(String sessionId) {
        return new TestSession(sessionId, new AgentStateCollection());
    }

    private static void assertSameTrail(List<StepRecord> expected, List<StepRecord> actual) {
        assertEquals(expected.size(), actual.size());
        for (int i = 0; i < expected.size(); i++) {
            StepRecord before = expected.get(i);
            StepRecord after = actual.get(i);
            assertEquals(before.getSessionId(), after.getSessionId());
            assertEquals(before.getSeq(), after.getSeq());
            assertEquals(before.getStepType(), after.getStepType());
            assertEquals(before.getStepName(), after.getStepName());
            assertEquals(before.getStatus(), after.getStatus());
            assertEquals(before.getTimestamp(), after.getTimestamp());
        }
    }

    private static final class TestSession extends BaseSession {
        private final String sessionId;
        private final SessionStateAccess state;
        private final Config config;

        private TestSession(String sessionId, SessionStateAccess state) {
            this.sessionId = sessionId;
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
    }
}

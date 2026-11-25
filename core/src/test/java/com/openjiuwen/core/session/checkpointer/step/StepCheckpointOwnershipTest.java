/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session.checkpointer.step;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.openjiuwen.core.foundation.store.kv.InMemoryKVStore;
import com.openjiuwen.core.graph.store.Serializer;
import com.openjiuwen.core.multitenant.TenantContext;
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

class StepCheckpointOwnershipTest {
    @BeforeEach
    void clearTenant() {
        TenantContextHolder.clearCurrentTenant();
    }

    @AfterEach
    void clearTenantAfter() {
        TenantContextHolder.clearCurrentTenant();
    }

    @Test
    void cursorRoundTripsCompleteOwnerIdentity() {
        StepCursor cursor = new StepCursor(4L, "PHASE", "round", StepStatus.RUNNING, 12L,
                "tenant-a", "session-a", "agent-a");

        StepCursor restored = StepCursor.fromMap(cursor.toMap());

        assertEquals(cursor, restored);
        assertEquals("tenant-a", restored.getTenantId());
        assertEquals("session-a", restored.getSessionId());
        assertEquals("agent-a", restored.getAgentId());
    }

    @Test
    void persistenceStampsActualOwnerInsteadOfCallerIdentity() {
        PersistenceCheckpointer checkpointer = new PersistenceCheckpointer(new InMemoryKVStore());
        AgentStateCollection state = new AgentStateCollection();
        TestSession session = new TestSession("actual-session", "actual-agent", state);
        checkpointer.preAgentExecute(session, null);
        StepCursor supplied = new StepCursor(1L, "PHASE", "round", StepStatus.RUNNING, 1L,
                "other-tenant", "other-session", "other-agent");

        checkpointer.stepCompleted(session, supplied);

        StepCursor persisted = checkpointer.lastCompletedStep(session);
        assertEquals("actual-session", persisted.getSessionId());
        assertEquals("actual-agent", persisted.getAgentId());
        assertEquals("", persisted.getTenantId());
    }

    @Test
    void failedCursorCarriesOwnerIdentity() {
        PersistenceCheckpointer checkpointer = new PersistenceCheckpointer(new InMemoryKVStore());
        AgentStateCollection state = new AgentStateCollection();
        TestSession session = new TestSession("failed-session", "failed-agent", state);
        checkpointer.preAgentExecute(session, null);

        checkpointer.stepFailed(session,
                new StepCursor(3L, "TOOL_CALL", "search", StepStatus.FAILED, 2L));

        AgentStateCollection restoredState = new AgentStateCollection();
        TestSession restored = new TestSession("failed-session", "failed-agent", restoredState);
        checkpointer.preAgentExecute(restored, null);
        StepCursor failed = checkpointer.lastFailedStep(restored);
        assertNotNull(failed);
        assertEquals("failed-session", failed.getSessionId());
        assertEquals("failed-agent", failed.getAgentId());
    }

    @Test
    void recoveryRequestContainsRealSessionAndAgentForBothBackends() {
        assertRecoveryOwner(new InMemoryCheckpointer());
        assertRecoveryOwner(new PersistenceCheckpointer(new InMemoryKVStore()));
    }

    @Test
    void dedupDistinguishesSessionsAndAgentsWithTheSameSequence() {
        InMemoryCheckpointer checkpointer = new InMemoryCheckpointer();
        saveRunning(checkpointer, "chat", "agent-a");
        saveRunning(checkpointer, "chat", "agent-b");
        saveRunning(checkpointer, "chat-10", "agent-a");
        StepCheckpointRecoveryService service = new StepCheckpointRecoveryService(checkpointer, null);

        List<StepCheckpointRecoveryService.RecoveryAction> first = service.recoverLeftovers();
        assertEquals(3, first.size());
        assertTrue(first.stream().allMatch(action ->
                action.type() == StepCheckpointRecoveryService.RecoveryActionType.MARKED_LOST));
        assertTrue(service.recoverLeftovers().stream().allMatch(action ->
                action.type() == StepCheckpointRecoveryService.RecoveryActionType.SKIPPED_ALREADY_PROCESSED));

        service.forget("chat");
        List<StepCheckpointRecoveryService.RecoveryAction> afterForget = service.recoverLeftovers();
        assertEquals(3, afterForget.size());
        assertEquals(2, afterForget.stream().filter(action ->
                action.type() == StepCheckpointRecoveryService.RecoveryActionType.MARKED_LOST).count());
        assertEquals(1, afterForget.stream().filter(action ->
                action.type() == StepCheckpointRecoveryService.RecoveryActionType.SKIPPED_ALREADY_PROCESSED).count());
    }

    @Test
    void sameOwnerAndSequenceDedupSurvivesTimestampAndStatusChanges() {
        InMemoryCheckpointer checkpointer = new InMemoryCheckpointer();
        AgentStateCollection state = new AgentStateCollection();
        TestSession session = new TestSession("session-resume", "agent-resume", state);
        checkpointer.preAgentExecute(session, null);
        checkpointer.stepCompleted(session,
                new StepCursor(1L, "PHASE", "round", StepStatus.RUNNING, 1L));
        StepCheckpointRecoveryService service = new StepCheckpointRecoveryService(checkpointer, null);
        assertEquals(1, service.recoverLeftovers().size());

        state.update(Map.of(StepCursor.STATE_KEY,
                new StepCursor(1L, "OTHER", "renamed", StepStatus.RUNNING, 99_999L,
                        "", "session-resume", "agent-resume").toMap()));
        checkpointer.postAgentExecute(session);

        List<StepCheckpointRecoveryService.RecoveryAction> repeated = service.recoverLeftovers();
        assertEquals(1, repeated.size());
        assertEquals(StepCheckpointRecoveryService.RecoveryActionType.SKIPPED_ALREADY_PROCESSED,
                repeated.get(0).type());
    }

    @Test
    void tenantScansAndRecoveryHooksRemainIsolated() {
        InMemoryCheckpointer checkpointer = new InMemoryCheckpointer();
        TenantContextHolder.setCurrentTenant(TenantContext.builder().tenantId("tenant-a").build());
        saveRunning(checkpointer, "shared-session", "shared-agent");
        TenantContextHolder.setCurrentTenant(TenantContext.builder().tenantId("tenant-b").build());
        saveRunning(checkpointer, "shared-session", "shared-agent");
        TenantContextHolder.clearCurrentTenant();

        List<StepCursor> scanned = checkpointer.scanRunningSteps();
        assertEquals(2, scanned.size());
        assertTrue(scanned.stream().anyMatch(cursor -> cursor.getTenantId().equals("tenant-a")));
        assertTrue(scanned.stream().anyMatch(cursor -> cursor.getTenantId().equals("tenant-b")));

        List<String> hookTenants = new ArrayList<>();
        StepCheckpointRecoveryService service = new StepCheckpointRecoveryService(checkpointer, request -> {
            hookTenants.add(Checkpointer.currentTenantId());
            return StepRecoveryHook.RecoveryResult.recovered("ok");
        });
        assertEquals(2, service.recoverLeftovers().size());
        assertTrue(hookTenants.contains("tenant-a"));
        assertTrue(hookTenants.contains("tenant-b"));

        TenantContextHolder.setCurrentTenant(TenantContext.builder().tenantId("tenant-a").build());
        service.forget("shared-session");
        List<StepCheckpointRecoveryService.RecoveryAction> actions = service.recoverLeftovers();
        assertEquals(1, actions.size());
        assertEquals("tenant-a", actions.get(0).cursor().getTenantId());
    }

    @Test
    void legacyOwnerRecoveryRejectsAmbiguousUnboundTenantKeys() {
        InMemoryKVStore store = new InMemoryKVStore();
        PersistenceCheckpointer checkpointer = new PersistenceCheckpointer(store);
        writeLegacyBlob(store, "legacy-session:agent:legacy-agent:agent_state_blobs",
                new StepCursor(1L, "PHASE", "legacy", StepStatus.RUNNING, 1L));
        writeLegacyBlob(store, "tenant-a:legacy-session:agent:legacy-agent:agent_state_blobs",
                new StepCursor(2L, "PHASE", "legacy-tenant", StepStatus.RUNNING, 2L));

        List<StepCursor> unbound = checkpointer.scanRunningSteps();
        assertEquals(1, unbound.size());
        assertEquals("legacy-session", unbound.get(0).getSessionId());
        assertEquals("legacy-agent", unbound.get(0).getAgentId());
        assertEquals("", unbound.get(0).getTenantId());

        TenantContextHolder.setCurrentTenant(TenantContext.builder().tenantId("tenant-a").build());
        List<StepCursor> bound = checkpointer.scanRunningSteps();
        assertEquals(1, bound.size());
        assertEquals("tenant-a", bound.get(0).getTenantId());
        assertEquals("legacy-session", bound.get(0).getSessionId());
        assertEquals("legacy-agent", bound.get(0).getAgentId());
    }

    @Test
    void legacyPausedCursorRecoversAgentAndTenantFromBoundKey() {
        InMemoryKVStore store = new InMemoryKVStore();
        PersistenceCheckpointer checkpointer = new PersistenceCheckpointer(store);
        writeLegacyBlob(store, "tenant-a:paused-session:agent:paused-agent:agent_state_blobs",
                new StepCursor(5L, "PHASE", "pause", StepStatus.PAUSED, 3L));
        TenantContextHolder.setCurrentTenant(TenantContext.builder().tenantId("tenant-a").build());

        StepCursor paused = checkpointer.lastPausedStep("paused-session");

        assertNotNull(paused);
        assertEquals("tenant-a", paused.getTenantId());
        assertEquals("paused-session", paused.getSessionId());
        assertEquals("paused-agent", paused.getAgentId());
    }

    @Test
    void legacyPausedCursorWithSessionIdRecoversMissingTenantAndAgent() {
        InMemoryKVStore store = new InMemoryKVStore();
        PersistenceCheckpointer checkpointer = new PersistenceCheckpointer(store);
        writeLegacyBlob(store, "tenant-a:paused-session:agent:paused-agent:agent_state_blobs",
                new StepCursor(5L, "PHASE", "pause", StepStatus.PAUSED, 3L, "paused-session"));

        List<StepCursor> paused = checkpointer.scanPausedSteps();

        assertEquals(1, paused.size());
        assertEquals("tenant-a", paused.get(0).getTenantId());
        assertEquals("paused-session", paused.get(0).getSessionId());
        assertEquals("paused-agent", paused.get(0).getAgentId());
        StepCheckpointRecoveryService service = new StepCheckpointRecoveryService(checkpointer, null);
        assertEquals(StepCheckpointRecoveryService.RecoveryActionType.RESUMED_EXPLICITLY,
                service.resumePaused(paused.get(0)).type());
        assertTrue(checkpointer.scanPausedSteps().isEmpty());
    }

    @Test
    void inMemoryResumeRejectsAmbiguousPausedOwner() {
        InMemoryCheckpointer checkpointer = new InMemoryCheckpointer();
        AgentStateCollection state = new AgentStateCollection();
        TestSession session = new TestSession("tenant-a:legacy-session", "legacy-agent", state);
        checkpointer.preAgentExecute(session, null);
        checkpointer.stepPaused(session,
                new StepCursor(1L, "PHASE", "pause", StepStatus.COMPLETED, 1L), null);
        state.update(Map.of(StepCursor.PAUSED_STATE_KEY,
                new StepCursor(1L, "PHASE", "pause", StepStatus.PAUSED, 2L).toMap()));
        checkpointer.postAgentExecute(session);

        assertThrows(IllegalStateException.class,
                () -> checkpointer.resumePaused("tenant-a:legacy-session"));
    }

    @Test
    void persistedOwnerMismatchIsNotExposedToRecovery() {
        InMemoryKVStore store = new InMemoryKVStore();
        PersistenceCheckpointer checkpointer = new PersistenceCheckpointer(store);
        writeLegacyBlob(store, "actual-session:agent:actual-agent:agent_state_blobs",
                new StepCursor(1L, "PHASE", "wrong-owner", StepStatus.RUNNING, 1L,
                        "", "other-session", "other-agent"));

        assertTrue(checkpointer.scanRunningSteps().isEmpty());
    }

    @Test
    void ownerRecoveryHandlesColonsInsideSessionAndAgentIds() {
        InMemoryKVStore store = new InMemoryKVStore();
        PersistenceCheckpointer checkpointer = new PersistenceCheckpointer(store);
        writeLegacyBlob(store, "session:part:agent:agent:agent:part:agent_state_blobs",
                new StepCursor(1L, "PHASE", "colon", StepStatus.RUNNING, 1L,
                        "", "session:part", "agent:agent:part"));

        List<StepCursor> cursors = checkpointer.scanRunningSteps();

        assertEquals(1, cursors.size());
        assertEquals("session:part", cursors.get(0).getSessionId());
        assertEquals("agent:agent:part", cursors.get(0).getAgentId());
    }

    @Test
    void pauseResumePreservesTenantSessionAndAgentOwnership() {
        PersistenceCheckpointer checkpointer = new PersistenceCheckpointer(new InMemoryKVStore());
        TenantContextHolder.setCurrentTenant(TenantContext.builder().tenantId("tenant-a").build());
        AgentStateCollection state = new AgentStateCollection();
        TestSession session = new TestSession("paused-session", "paused-agent", state);
        checkpointer.preAgentExecute(session, null);
        checkpointer.stepPaused(session,
                new StepCursor(2L, "PHASE", "pause", StepStatus.COMPLETED, 1L), null);
        TenantContextHolder.clearCurrentTenant();

        StepCursor paused = checkpointer.scanPausedSteps().get(0);
        StepCheckpointRecoveryService service = new StepCheckpointRecoveryService(checkpointer, null);
        assertEquals(StepCheckpointRecoveryService.RecoveryActionType.RESUMED_EXPLICITLY,
                service.resumePaused(paused).type());

        TenantContextHolder.setCurrentTenant(TenantContext.builder().tenantId("tenant-a").build());
        AgentStateCollection restoredState = new AgentStateCollection();
        TestSession restored = new TestSession("paused-session", "paused-agent", restoredState);
        checkpointer.preAgentExecute(restored, null);
        StepCursor resumed = checkpointer.lastCompletedStep(restored);
        assertNotNull(resumed);
        assertEquals("tenant-a", resumed.getTenantId());
        assertEquals("paused-session", resumed.getSessionId());
        assertEquals("paused-agent", resumed.getAgentId());
        assertNull(checkpointer.lastPausedStep(restored));
    }

    @Test
    void inMemoryScanAndResumeCoverEveryAgentInASession() {
        InMemoryCheckpointer checkpointer = new InMemoryCheckpointer();
        AgentStateCollection stateA = new AgentStateCollection();
        AgentStateCollection stateB = new AgentStateCollection();
        TestSession sessionA = new TestSession("multi-agent", "agent-a", stateA);
        TestSession sessionB = new TestSession("multi-agent", "agent-b", stateB);
        checkpointer.preAgentExecute(sessionA, null);
        checkpointer.preAgentExecute(sessionB, null);
        checkpointer.stepPaused(sessionA,
                new StepCursor(1L, "PHASE", "pause", StepStatus.COMPLETED, 1L), null);
        checkpointer.stepPaused(sessionB,
                new StepCursor(1L, "PHASE", "pause", StepStatus.COMPLETED, 1L), null);

        assertEquals(2, checkpointer.scanPausedSteps().size());
        StepCheckpointRecoveryService service = new StepCheckpointRecoveryService(checkpointer, null);
        assertEquals(StepCheckpointRecoveryService.RecoveryActionType.RESUMED_EXPLICITLY,
                service.resumePaused(checkpointer.scanPausedSteps().get(0)).type());

        AgentStateCollection restoredA = new AgentStateCollection();
        AgentStateCollection restoredB = new AgentStateCollection();
        checkpointer.preAgentExecute(new TestSession("multi-agent", "agent-a", restoredA), null);
        checkpointer.preAgentExecute(new TestSession("multi-agent", "agent-b", restoredB), null);
        assertNull(checkpointer.lastPausedStep(new TestSession("multi-agent", "agent-a", restoredA)));
        assertNull(checkpointer.lastPausedStep(new TestSession("multi-agent", "agent-b", restoredB)));
        assertEquals("agent-a", checkpointer.lastCompletedStep(
                new TestSession("multi-agent", "agent-a", restoredA)).getAgentId());
        assertEquals("agent-b", checkpointer.lastCompletedStep(
                new TestSession("multi-agent", "agent-b", restoredB)).getAgentId());
    }

    private void assertRecoveryOwner(Checkpointer checkpointer) {
        saveRunning(checkpointer, "owned-session", "owned-agent");
        List<StepRecoveryHook.RecoveryRequest> requests = new ArrayList<>();
        StepCheckpointRecoveryService service = new StepCheckpointRecoveryService(checkpointer, request -> {
            requests.add(request);
            return StepRecoveryHook.RecoveryResult.recovered("ok");
        });

        service.recoverLeftovers();

        assertEquals(1, requests.size());
        assertEquals("owned-session", requests.get(0).sessionId());
        assertEquals("owned-agent", requests.get(0).agentId());
    }

    private void saveRunning(Checkpointer checkpointer, String sessionId, String agentId) {
        AgentStateCollection state = new AgentStateCollection();
        TestSession session = new TestSession(sessionId, agentId, state);
        checkpointer.preAgentExecute(session, null);
        checkpointer.stepCompleted(session,
                new StepCursor(1L, "PHASE", "round", StepStatus.RUNNING, 1L));
    }

    private void writeLegacyBlob(InMemoryKVStore store, String blobKey, StepCursor cursor) {
        AgentStateCollection state = new AgentStateCollection();
        String stateKey = cursor.getStatus() == StepStatus.PAUSED
                ? StepCursor.PAUSED_STATE_KEY : StepCursor.STATE_KEY;
        state.update(Map.of(stateKey, cursor.toMap()));
        Serializer.TypedBytes bytes = Serializer.create("java").dumpsTyped(state.getState());
        String baseKey = blobKey.substring(0, blobKey.length() - ":agent_state_blobs".length());
        store.set(baseKey + ":agent_state_blobs_dump_type", bytes.type()).join();
        store.set(blobKey, bytes.data()).join();
    }

    private static final class TestSession extends BaseSession {
        private final String sessionId;
        private final String agentId;
        private final SessionStateAccess state;
        private final Config config = new Config();

        private TestSession(String sessionId, String agentId, SessionStateAccess state) {
            this.sessionId = sessionId;
            this.agentId = agentId;
            this.state = state;
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

        @SuppressWarnings("unused")
        String agentId() {
            return agentId;
        }
    }
}

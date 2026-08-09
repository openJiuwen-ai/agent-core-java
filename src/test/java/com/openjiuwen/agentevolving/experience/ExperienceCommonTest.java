/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.experience;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.openjiuwen.agentevolving.Protocols;
import com.openjiuwen.agentevolving.checkpointing.EvolutionLog;
import com.openjiuwen.agentevolving.checkpointing.EvolutionPatch;
import com.openjiuwen.agentevolving.checkpointing.EvolutionRecord;
import com.openjiuwen.agentevolving.signal.EvolutionTarget;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import org.junit.jupiter.api.Test;

/**
 * Mirrors Python's experience common tests in
 * {@code tests/unit_tests/agent_evolving/experience/test_common.py}.
 */
class ExperienceCommonTest {

    @Test
    void publicExperienceTypesAreImportable() {
        EvolutionRecord record = record("rec-1", 0.9, null);
        ExperienceProposal proposal = new ExperienceProposal(
                "skill-a",
                List.of(record),
                true,
                null,
                "",
                null,
                null
        );
        ExperienceApprovalRequest approvalRequest = new ExperienceApprovalRequest(
                "skill-a",
                proposal,
                null,
                null,
                List.of()
        );
        ExperienceApplyResult applyResult = new ExperienceApplyResult(
                "skill-a",
                1,
                0,
                0,
                List.of(),
                Map.of()
        );
        RebuildRequest rebuildRequest = new RebuildRequest("skill-a", null, 0.5, Map.of());

        assertEquals(List.of(record), proposal.getRecords());
        assertSame(proposal, approvalRequest.getProposal());
        assertEquals(1, applyResult.getAppliedCount());
        assertEquals("skill-a", rebuildRequest.getSkillName());
    }

    @Test
    void internalExperienceLifecycleTypesAreNotPublicContracts() {
        assertFalse(ExperiencePackage.EXPORTED_SYMBOLS.contains("LocalApplyPreview"));
        assertFalse(ExperiencePackage.EXPORTED_SYMBOLS.contains("PendingCommitResult"));
        assertFalse(ExperiencePackage.EXPORTED_SYMBOLS.contains("RebuildRequest"));
    }

    @Test
    void experienceApplyResultOkIgnoresRejectionsButNotPendingRecords() {
        assertTrue(new ExperienceApplyResult("skill-a", 1, 1, 0, List.of(), Map.of()).isOk());
        assertFalse(new ExperienceApplyResult("skill-a", 1, 0, 1, List.of(), Map.of()).isOk());
        assertFalse(new ExperienceApplyResult("skill-a", 1, 0, 0, List.of("disk failed"), Map.of()).isOk());
    }

    @Test
    void experienceApplyResultToHostResultPreservesMixedApprovalCounts() {
        HostFacingExperienceResult hostResult = new ExperienceApplyResult(
                "skill-a",
                1,
                1,
                0,
                List.of(),
                Map.of()
        ).toHostResult("req-1", null);

        assertEquals("partial", hostResult.getStatus());
        assertEquals(1, hostResult.getAppliedCount());
        assertEquals(1, hostResult.getRejectedCount());
        assertEquals(0, hostResult.getPendingCount());
        assertEquals(List.of(), hostResult.getErrors());
    }

    @Test
    void experienceApplyResultToHostResultPreservesFailedSelectiveAcceptCounts() {
        HostFacingExperienceResult hostResult = new ExperienceApplyResult(
                "skill-a",
                0,
                1,
                1,
                List.of("disk full"),
                Map.of()
        ).toHostResult("req-1", null);

        assertEquals("partial", hostResult.getStatus());
        assertEquals(0, hostResult.getAppliedCount());
        assertEquals(1, hostResult.getRejectedCount());
        assertEquals(1, hostResult.getPendingCount());
        assertEquals(List.of("disk full"), hostResult.getErrors());
    }

    @Test
    void makePendingChangeUsesCheckpointingSnapshotType() {
        EvolutionRecord record = record("rec-1", 0.9, null);

        PendingChange pending = ExperienceCommon.makePendingChange(
                "skill-a",
                List.of(record),
                null,
                null,
                null,
                false
        );

        assertNotNull(pending);
        assertEquals("skill-a", pending.getSkillName());
        assertEquals(List.of(record), pending.getPayload());
    }

    @Test
    void rejectPendingChangeReturnsRejectedCount() {
        PendingChange pending = ExperienceCommon.makePendingChange(
                "skill-a",
                List.of(record("rec-1", 0.9, null), record("rec-2", 0.8, null)),
                null,
                null,
                null,
                false
        );

        ExperienceApplyResult result = ExperienceCommon.rejectPendingChange(pending);

        assertEquals(2, result.getRejectedCount());
        assertEquals("skill-a", result.getSkillName());
    }

    @Test
    void makeAndRejectPendingChangePreservePythonShape() {
        PendingChange pending = ExperienceCommon.makePendingChange(
                "demo",
                List.of(record("rec-1", 0.9, null)),
                "request",
                Map.of("trace", "t1"),
                List.of(Map.of("role", "user")),
                true
        );

        ExperienceApplyResult result = ExperienceCommon.rejectPendingChange(pending);

        assertTrue(pending.getChangeId().startsWith("request_"));
        assertTrue(pending.isSharedRecords());
        assertEquals(1, pending.getPayload().size());
        assertEquals("demo", result.getSkillName());
        assertEquals(1, result.getRejectedCount());
        assertEquals(0, result.getAppliedCount());
    }

    @Test
    void commitPendingChangeRetainsApprovedTailOnWriteFailure() {
        FakeExperienceStore store = new FakeExperienceStore();
        store.failAppendAtIndex = 1;
        PendingChange pending = PendingChange.make("demo", List.of(
                record("rec-1", 0.9, null),
                record("rec-2", 0.8, null),
                record("rec-3", 0.7, null)
        ), null, null);
        Map<String, PendingChange> pendingById = new LinkedHashMap<>();
        pendingById.put(pending.getChangeId(), pending);

        PendingCommitResult result = ExperienceCommon.commitPendingChange(
                pendingById,
                pending.getChangeId(),
                store,
                List.of("rec-1", "rec-2")
        ).toCompletableFuture().join();

        assertEquals(1, result.getAppliedCount());
        assertEquals(1, result.getPendingCount());
        assertEquals(1, result.getRejectedCount());
        assertEquals(List.of("rec-2"), extractIds(pending.getPayload()));
        assertTrue(pendingById.containsKey(pending.getChangeId()));
        assertEquals(List.of("rec-1"), extractIds(store.appendedRecords));
    }

    @Test
    void executeSimplifyActionsCountsMutationOutcomes() {
        FakeExperienceStore store = new FakeExperienceStore();
        store.deleteResults.put("del-1", 1);
        store.mergeResults.put("merge-1", record("merged", 0.9, null));
        store.updateResults.put("ref-1", record("refined", 0.8, null));

        Map<String, Integer> counts = ExperienceCommon.executeSimplifyActions(
                store,
                "demo",
                List.of(
                        action("DELETE", "del-1", null, null),
                        action("MERGE", "merge-1", List.of("merge-2"), "merged-content"),
                        action("REFINE", "ref-1", null, "refined-content"),
                        action("KEEP", "keep-1", null, null),
                        action("UNKNOWN", "oops", null, null)
                )
        ).toCompletableFuture().join();

        assertEquals(1, counts.get("deleted"));
        assertEquals(1, counts.get("merged"));
        assertEquals(1, counts.get("refined"));
        assertEquals(1, counts.get("kept"));
        assertEquals(1, counts.get("errors"));
    }

    @Test
    void executeSimplifyActionsKeepUnknownAndErrorPaths() {
        FakeExperienceStore store = new FakeExperienceStore();
        store.failDelete = true;

        Map<String, Integer> counts = ExperienceCommon.executeSimplifyActions(
                store,
                "skill-a",
                List.of(
                        action("KEEP", "ev_keep", null, null),
                        action("UNKNOWN", "ev_unknown", null, null),
                        action("DELETE", "ev_fail", null, null)
                )
        ).toCompletableFuture().join();

        assertEquals(Map.of("deleted", 0, "merged", 0, "refined", 0, "kept", 1, "errors", 2), counts);
    }

    @Test
    void requestRebuildContextFiltersRecordsAndClearsArchivedLog() {
        FakeExperienceStore store = new FakeExperienceStore();
        store.skillExists = true;
        store.archiveSkillBodyResult = "SKILL.v1.md";
        store.archiveEvolutionsResult = "evolutions.v1.json";
        store.evolutionLog = new EvolutionLog(
                "demo",
                "1.0.0",
                "2026-06-09T12:00:00Z",
                List.of(
                        record("keep-1", 0.9, null),
                        record("skip-low-score", 0.2, null),
                        record("skip-reason", 0.95, "noisy")
                )
        );

        ExperienceCommon.RebuildContextPayload payload = ExperienceCommon.requestRebuildContext(
                store,
                new RebuildRequest("demo", "", 0.5, Map.of()),
                records -> "records=" + records.size(),
                "default-intent",
                "prompt {evolution_records} / {user_intent} / {min_score}",
                true
        ).toCompletableFuture().join();

        assertNotNull(payload);
        assertEquals("demo", payload.getSkillName());
        assertEquals(1, payload.getFilteredRecords().size());
        assertEquals("evolutions.v1.json", payload.getArchivePath());
        assertNull(payload.getArchiveError());
        assertTrue(payload.getPrompt().contains("records=1 / default-intent / 0.5"));
        assertTrue(store.clearEvolutionsCalled);
    }

    @Test
    void requestRebuildContextReturnsNullWhenSkillDoesNotExist() {
        FakeExperienceStore store = new FakeExperienceStore();

        ExperienceCommon.RebuildContextPayload payload = ExperienceCommon.requestRebuildContext(
                store,
                new RebuildRequest("missing"),
                records -> "",
                "intent",
                "{user_intent}",
                true
        ).toCompletableFuture().join();

        assertNull(payload);
        assertFalse(store.clearEvolutionsCalled);
    }

    @Test
    void requestRebuildContextArchiveFailureIsReportedButPromptStillBuilds() {
        FakeExperienceStore store = new FakeExperienceStore();
        store.skillExists = true;
        store.archiveSkillBodyResult = "body-archive";
        store.failArchiveEvolutions = true;
        store.evolutionLog = new EvolutionLog(
                "skill-a",
                "1.0.0",
                "2026-06-09T12:00:00Z",
                List.of(record("keep-1", 0.8, null))
        );

        ExperienceCommon.RebuildContextPayload payload = ExperienceCommon.requestRebuildContext(
                store,
                new RebuildRequest("skill-a", null, 0.5, Map.of()),
                records -> "formatted",
                "default intent",
                "{evolution_records}|{user_intent}|{min_score}",
                true
        ).toCompletableFuture().join();

        assertNotNull(payload);
        assertTrue(payload.getArchiveError() instanceof RuntimeException);
        assertEquals("archive failed", payload.getArchiveError().getMessage());
        assertEquals("formatted|default intent|0.5", payload.getPrompt());
        assertFalse(store.clearEvolutionsCalled);
    }

    private static Map<String, Object> action(
            String actionType,
            String recordId,
            List<String> mergeRemoveIds,
            String newContent
    ) {
        Map<String, Object> action = new LinkedHashMap<>();
        action.put("action", actionType);
        action.put("record_id", recordId);
        if (mergeRemoveIds != null) {
            action.put("merge_remove_ids", mergeRemoveIds);
        }
        if (newContent != null) {
            action.put("new_content", newContent);
        }
        return action;
    }

    private static EvolutionRecord record(String id, double score, String skipReason) {
        EvolutionPatch patch = EvolutionPatch.builder()
                .section("Troubleshooting")
                .action(Protocols.APPEND_MODE)
                .content("content-" + id)
                .target(EvolutionTarget.BODY)
                .skipReason(skipReason)
                .build();
        EvolutionRecord record = EvolutionRecord.builder()
                .id(id)
                .source("test")
                .timestamp("2026-06-09T12:00:00Z")
                .context("context")
                .change(patch)
                .score(score)
                .build();
        return record;
    }

    private static List<String> extractIds(List<EvolutionRecord> records) {
        List<String> ids = new ArrayList<>();
        for (EvolutionRecord record : records) {
            ids.add(record.getId());
        }
        return ids;
    }

    private static final class FakeExperienceStore implements ExperienceCommon.ExperienceStore {

        private final List<EvolutionRecord> appendedRecords = new ArrayList<>();
        private final Map<String, Integer> deleteResults = new LinkedHashMap<>();
        private final Map<String, EvolutionRecord> mergeResults = new LinkedHashMap<>();
        private final Map<String, EvolutionRecord> updateResults = new LinkedHashMap<>();
        private boolean skillExists;
        private boolean clearEvolutionsCalled;
        private boolean failDelete;
        private boolean failArchiveEvolutions;
        private int failAppendAtIndex = -1;
        private String archiveSkillBodyResult;
        private String archiveEvolutionsResult;
        private EvolutionLog evolutionLog = EvolutionLog.empty("demo");

        @Override
        public boolean skillExists(String skillName) {
            return skillExists;
        }

        @Override
        public CompletionStage<Void> appendRecord(String skillName, EvolutionRecord record) {
            if (failAppendAtIndex >= 0 && appendedRecords.size() == failAppendAtIndex) {
                return CompletableFuture.failedFuture(new IllegalStateException("append failed"));
            }
            appendedRecords.add(record);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Integer> deleteRecords(String skillName, List<String> recordIds) {
            if (failDelete) {
                return CompletableFuture.failedFuture(new IllegalStateException("disk full"));
            }
            return CompletableFuture.completedFuture(deleteResults.getOrDefault(recordIds.get(0), 0));
        }

        @Override
        public CompletionStage<EvolutionRecord> mergeRecords(
                String skillName,
                String recordId,
                List<String> mergeRemoveIds,
                String newContent
        ) {
            return CompletableFuture.completedFuture(mergeResults.get(recordId));
        }

        @Override
        public CompletionStage<EvolutionRecord> updateRecordContent(String skillName, String recordId, String newContent) {
            return CompletableFuture.completedFuture(updateResults.get(recordId));
        }

        @Override
        public CompletionStage<String> archiveSkillBody(String skillName) {
            return CompletableFuture.completedFuture(archiveSkillBodyResult);
        }

        @Override
        public CompletionStage<String> archiveEvolutions(String skillName) {
            if (failArchiveEvolutions) {
                return CompletableFuture.failedFuture(new RuntimeException("archive failed"));
            }
            return CompletableFuture.completedFuture(archiveEvolutionsResult);
        }

        @Override
        public CompletionStage<EvolutionLog> loadFullEvolutionLog(String skillName) {
            return CompletableFuture.completedFuture(evolutionLog);
        }

        @Override
        public CompletionStage<Void> clearEvolutions(String skillName) {
            clearEvolutionsCalled = true;
            return CompletableFuture.completedFuture(null);
        }
    }
}

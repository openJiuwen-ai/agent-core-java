/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.experience;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.openjiuwen.agent_evolving.Protocols;
import com.openjiuwen.agent_evolving.checkpointing.EvolutionLog;
import com.openjiuwen.agent_evolving.checkpointing.EvolutionPatch;
import com.openjiuwen.agent_evolving.checkpointing.EvolutionRecord;
import com.openjiuwen.agent_evolving.signal.EvolutionTarget;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import org.junit.jupiter.api.Test;

class ExperienceCommonTest {

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

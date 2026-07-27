/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.experience;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.openjiuwen.agent_evolving.Protocols;
import com.openjiuwen.agent_evolving.checkpointing.EvolutionLog;
import com.openjiuwen.agent_evolving.checkpointing.EvolutionPatch;
import com.openjiuwen.agent_evolving.checkpointing.EvolutionRecord;
import com.openjiuwen.agent_evolving.checkpointing.EvolutionStore;
import com.openjiuwen.agent_evolving.signal.EvolutionTarget;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Mirrors Python's pending commit tests in
 * {@code tests/unit_tests/agent_evolving/experience/test_pending_commit.py}.
 */
class PendingCommitTest {

    @TempDir
    private Path tempDir;

    @Test
    void pendingCommitResultUsesValueEquality() {
        assertEquals(new PendingCommitResult(1, 0), new PendingCommitResult(1, 0));
    }

    @Test
    void commitPendingChangeClearsSnapshotOnSuccess() throws Exception {
        Path root = tempDir.resolve("skills");
        prepareSkill(root, "skill-a");
        EvolutionStore store = new EvolutionStore(root.toString());
        PendingChange pending = pending("pending-1");
        Map<String, PendingChange> pendingById = pendingMap(pending);

        PendingCommitResult result = commit(pendingById, pending.getChangeId(), store);

        assertEquals(1, result.getAppliedCount());
        assertEquals(0, result.getPendingCount());
        assertFalse(pendingById.containsKey(pending.getChangeId()));
        assertEquals(List.of("pending-1-record"), ids(loadLog(store, "skill-a").getEntries()));
    }

    @Test
    void commitPendingChangeRetainsUnwrittenTailOnRecordFailure() {
        FailingStore store = new FailingStore("ev_2");
        PendingChange pending = new PendingChange(
                "skill_experience_skill-a",
                "skill-a",
                Protocols.SKILL_EXPERIENCE_ENTRY,
                List.of(record("ev_1"), record("ev_2")),
                "2026-01-01T00:00:00+00:00",
                "pending-2",
                false,
                null,
                null
        );
        Map<String, PendingChange> pendingById = pendingMap(pending);

        PendingCommitResult result = ExperienceCommon.commitPendingChange(
                pendingById,
                pending.getChangeId(),
                store,
                null
        ).toCompletableFuture().join();

        assertEquals(1, result.getAppliedCount());
        assertEquals(1, result.getPendingCount());
        assertEquals(List.of("disk full"), result.getErrors());
        assertSame(pending, pendingById.get(pending.getChangeId()));
        assertEquals(List.of("ev_2"), ids(pending.getPayload()));
        assertEquals(List.of("ev_1"), ids(store.appendedRecords));

        store.failRecordId = "";
        PendingCommitResult retried = ExperienceCommon.commitPendingChange(
                pendingById,
                pending.getChangeId(),
                store,
                null
        ).toCompletableFuture().join();

        assertEquals(1, retried.getAppliedCount());
        assertEquals(0, retried.getPendingCount());
        assertFalse(pendingById.containsKey(pending.getChangeId()));
        assertEquals(List.of("ev_1", "ev_2"), ids(store.appendedRecords));
    }

    @Test
    void commitPendingChangeSupportsLegacyExperienceEntry() throws Exception {
        Path root = tempDir.resolve("skills");
        prepareSkill(root, "skill-a");
        EvolutionStore store = new EvolutionStore(root.toString());
        PendingChange pending = pending("pending-legacy", "skill-a", Protocols.EXPERIENCE_ENTRY);
        Map<String, PendingChange> pendingById = pendingMap(pending);

        PendingCommitResult result = commit(pendingById, pending.getChangeId(), store);

        assertEquals(1, result.getAppliedCount());
        assertEquals(0, result.getPendingCount());
        assertFalse(pendingById.containsKey(pending.getChangeId()));
    }

    @Test
    void commitPendingChangeRejectsMissingChangeId() throws Exception {
        Path root = tempDir.resolve("skills");
        prepareSkill(root, "skill-a");
        EvolutionStore store = new EvolutionStore(root.toString());

        CompletionException thrown = assertThrows(
                CompletionException.class,
                () -> commit(Map.of(), "missing-change", store)
        );
        assertEquals(NoSuchElementException.class, thrown.getCause().getClass());
        assertEquals("missing-change", thrown.getCause().getMessage());
    }

    @Test
    void commitPendingChangeReachesTeamSkillExperienceEntryPath() throws Exception {
        Path root = tempDir.resolve("skills");
        prepareSkill(root, "team-skill-a");
        EvolutionStore store = new EvolutionStore(root.toString());
        PendingChange pending = pending("pending-team", "team-skill-a", Protocols.SKILL_EXPERIENCE_ENTRY);
        Map<String, PendingChange> pendingById = pendingMap(pending);

        PendingCommitResult result = commit(pendingById, pending.getChangeId(), store);

        assertEquals(1, result.getAppliedCount());
        assertEquals(0, result.getPendingCount());
        assertFalse(pendingById.containsKey(pending.getChangeId()));
        assertEquals(List.of("pending-team-record"), ids(loadLog(store, "team-skill-a").getEntries()));
    }

    private static PendingCommitResult commit(
            Map<String, PendingChange> pendingById,
            String changeId,
            EvolutionStore store
    ) {
        return ExperienceCommon.commitPendingChange(
                pendingById,
                changeId,
                new EvolutionStoreAdapter(store),
                null
        ).toCompletableFuture().join();
    }

    private static PendingChange pending(String changeId) {
        return pending(changeId, "skill-a", Protocols.SKILL_EXPERIENCE_ENTRY);
    }

    private static PendingChange pending(String changeId, String skillName, String changeType) {
        return new PendingChange(
                "skill_experience_" + skillName,
                skillName,
                changeType,
                List.of(record(changeId + "-record")),
                "2026-01-01T00:00:00+00:00",
                changeId,
                false,
                null,
                null
        );
    }

    private static EvolutionRecord record(String id) {
        return EvolutionRecord.builder()
                .id(id)
                .source("execution_failure")
                .timestamp("2026-01-01T00:00:00+00:00")
                .context("ctx")
                .change(EvolutionPatch.builder()
                        .section("Troubleshooting")
                        .action(Protocols.APPEND_MODE)
                        .content("fix issue")
                        .target(EvolutionTarget.BODY)
                        .build())
                .build();
    }

    private static void prepareSkill(Path root, String name) throws IOException {
        Path skillDir = root.resolve(name);
        Files.createDirectories(skillDir);
        Files.writeString(skillDir.resolve("SKILL.md"), "# Skill\n");
    }

    private static Map<String, PendingChange> pendingMap(PendingChange pending) {
        Map<String, PendingChange> pendingById = new LinkedHashMap<>();
        pendingById.put(pending.getChangeId(), pending);
        return pendingById;
    }

    private static EvolutionLog loadLog(EvolutionStore store, String skillName) {
        return store.loadEvolutionLog(skillName).toCompletableFuture().join();
    }

    private static List<String> ids(List<EvolutionRecord> records) {
        List<String> result = new ArrayList<>();
        for (EvolutionRecord record : records) {
            result.add(record.getId());
        }
        return result;
    }

    private static final class EvolutionStoreAdapter implements ExperienceCommon.ExperienceStore {

        private final EvolutionStore store;

        private EvolutionStoreAdapter(EvolutionStore store) {
            this.store = store;
        }

        @Override
        public boolean skillExists(String skillName) {
            return store.skillExists(skillName);
        }

        @Override
        public CompletionStage<Void> appendRecord(String skillName, EvolutionRecord record) {
            return store.appendRecord(skillName, record);
        }

        @Override
        public CompletionStage<Integer> deleteRecords(String skillName, List<String> recordIds) {
            return store.deleteRecords(skillName, recordIds);
        }

        @Override
        public CompletionStage<EvolutionRecord> mergeRecords(
                String skillName,
                String recordId,
                List<String> mergeRemoveIds,
                String newContent
        ) {
            return store.mergeRecords(skillName, recordId, mergeRemoveIds, newContent);
        }

        @Override
        public CompletionStage<EvolutionRecord> updateRecordContent(
                String skillName,
                String recordId,
                String newContent
        ) {
            return store.updateRecordContent(skillName, recordId, newContent);
        }

        @Override
        public CompletionStage<String> archiveSkillBody(String skillName) {
            return store.archiveSkillBody(skillName);
        }

        @Override
        public CompletionStage<String> archiveEvolutions(String skillName) {
            return store.archiveEvolutions(skillName);
        }

        @Override
        public CompletionStage<EvolutionLog> loadFullEvolutionLog(String skillName) {
            return store.loadFullEvolutionLog(skillName);
        }

        @Override
        public CompletionStage<Void> clearEvolutions(String skillName) {
            return store.clearEvolutions(skillName);
        }
    }

    private static final class FailingStore implements ExperienceCommon.ExperienceStore {

        private final List<EvolutionRecord> appendedRecords = new ArrayList<>();
        private String failRecordId;

        private FailingStore(String failRecordId) {
            this.failRecordId = failRecordId;
        }

        @Override
        public boolean skillExists(String skillName) {
            return true;
        }

        @Override
        public CompletionStage<Void> appendRecord(String skillName, EvolutionRecord record) {
            if (record.getId().equals(failRecordId)) {
                return CompletableFuture.failedFuture(new IOException("disk full"));
            }
            appendedRecords.add(record);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Integer> deleteRecords(String skillName, List<String> recordIds) {
            return CompletableFuture.completedFuture(0);
        }

        @Override
        public CompletionStage<EvolutionRecord> mergeRecords(
                String skillName,
                String recordId,
                List<String> mergeRemoveIds,
                String newContent
        ) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<EvolutionRecord> updateRecordContent(
                String skillName,
                String recordId,
                String newContent
        ) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<String> archiveSkillBody(String skillName) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<String> archiveEvolutions(String skillName) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<EvolutionLog> loadFullEvolutionLog(String skillName) {
            return CompletableFuture.completedFuture(EvolutionLog.empty(skillName));
        }

        @Override
        public CompletionStage<Void> clearEvolutions(String skillName) {
            return CompletableFuture.completedFuture(null);
        }
    }
}

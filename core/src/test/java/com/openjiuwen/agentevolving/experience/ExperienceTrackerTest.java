/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.experience;

import com.openjiuwen.agentevolving.checkpointing.EvolutionLog;
import com.openjiuwen.agentevolving.checkpointing.EvolutionPatch;
import com.openjiuwen.agentevolving.checkpointing.EvolutionRecord;
import com.openjiuwen.agentevolving.checkpointing.EvolutionStore;
import com.openjiuwen.agentevolving.checkpointing.StoreRecordsHelper;
import com.openjiuwen.agentevolving.checkpointing.UsageStats;
import com.openjiuwen.agentevolving.signal.EvolutionTarget;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for presented-experience tracking.
 *
 * <p>Mirrors Python's {@code ExperienceTracker} in
 * {@code openjiuwen/agent_evolving/experience/tracker.py}.</p>
 */
class ExperienceTrackerTest {

    @TempDir
    private Path tempDir;

    @Test
    void sessionPresentedRecordsIsolatedPerSession() {
        Object sessionA = new Object();
        Object sessionB = new Object();
        EvolutionRecord recordA = record("ev_a", EvolutionTarget.BODY, 0.6d);
        EvolutionRecord recordB = record("ev_b", EvolutionTarget.BODY, 0.6d);

        ExperienceTracker.setSessionPresentedRecords(
                sessionA,
                List.of(new ExperienceTracker.PresentedEntry("skill-a", recordA, "snippet-a"))
        );
        ExperienceTracker.setSessionPresentedRecords(
                sessionB,
                List.of(new ExperienceTracker.PresentedEntry("skill-b", recordB, "snippet-b"))
        );

        List<ExperienceTracker.PresentedEntry> recordsA = ExperienceTracker.getSessionPresentedRecords(sessionA);
        List<ExperienceTracker.PresentedEntry> recordsB = ExperienceTracker.getSessionPresentedRecords(sessionB);

        assertEquals(1, recordsA.size());
        assertEquals("skill-a", recordsA.get(0).skillName());
        assertEquals("snippet-a", recordsA.get(0).presentationSnippet());
        assertEquals(1, recordsB.size());
        assertEquals("skill-b", recordsB.get(0).skillName());
        assertEquals("snippet-b", recordsB.get(0).presentationSnippet());
    }

    @Test
    void sessionEvalCounterIsolatedPerSession() {
        Object sessionA = new Object();
        Object sessionB = new Object();

        ExperienceTracker.setSessionEvalCounter(sessionA, 3);
        ExperienceTracker.setSessionEvalCounter(sessionB, 7);

        assertEquals(3, ExperienceTracker.getSessionEvalCounter(sessionA));
        assertEquals(7, ExperienceTracker.getSessionEvalCounter(sessionB));
    }

    @Test
    void sessionHelpersWithNullSession() {
        assertEquals(List.of(), ExperienceTracker.getSessionPresentedRecords(null));
        assertEquals(0, ExperienceTracker.getSessionEvalCounter(null));

        ExperienceTracker.setSessionPresentedRecords(null, List.of());
        ExperienceTracker.setSessionEvalCounter(null, 5);
    }

    @Test
    void sessionPresentedRecordsStoreSnippet() {
        Object session = new Object();
        EvolutionRecord record = record("ev_a", EvolutionTarget.BODY, 0.6d);

        ExperienceTracker.setSessionPresentedRecords(
                session,
                List.of(new ExperienceTracker.PresentedEntry("skill-a", record, "my_snippet"))
        );

        List<ExperienceTracker.PresentedEntry> entries = ExperienceTracker.getSessionPresentedRecords(session);
        assertEquals(1, entries.size());
        assertEquals("skill-a", entries.get(0).skillName());
        assertSame(record, entries.get(0).record());
        assertEquals("my_snippet", entries.get(0).presentationSnippet());
    }

    @Test
    void consumeEvalStateWaitsForIntervalAndClears() {
        Object session = new Object();
        EvolutionRecord record = record("ev_a", EvolutionTarget.BODY, 0.6d);
        ExperienceTracker tracker = new ExperienceTracker(new FakeStore(tempDir), scorer(), 2);
        ExperienceTracker.setSessionPresentedRecords(
                session,
                List.of(new ExperienceTracker.PresentedEntry("skill-a", record, "snippet"))
        );

        assertEquals(List.of(), tracker.consumeEvalState(session));
        assertEquals(1, ExperienceTracker.getSessionEvalCounter(session));

        List<ExperienceTracker.PresentedEntry> consumed = tracker.consumeEvalState(session);
        assertEquals(1, consumed.size());
        assertSame(record, consumed.get(0).record());
        assertEquals(List.of(), ExperienceTracker.getSessionPresentedRecords(session));
        assertEquals(0, ExperienceTracker.getSessionEvalCounter(session));
    }

    @Test
    void recordPresentedOnlyBodyRecords() {
        FakeStore store = new FakeStore(tempDir);
        ExperienceTracker tracker = new ExperienceTracker(store, scorer(), 2);
        Object session = new Object();
        EvolutionRecord descRecord = record("ev_desc", EvolutionTarget.DESCRIPTION, 0.9d);
        EvolutionRecord bodyRecord = record("ev_body", EvolutionTarget.BODY, 0.8d);
        bodyRecord.setUsageStats(new UsageStats(1, 0, 0, 0, null, null));
        store.recordsByScore.put("skill-a", List.of(descRecord, bodyRecord));

        tracker.recordPresented(session, "skill-a", "some_snippet").toCompletableFuture().join();

        Map<String, StoreRecordsHelper.RecordUpdate> updates = store.updatesBySkill.get("skill-a");
        assertNotNull(updates);
        assertFalse(updates.containsKey("ev_desc"));
        assertTrue(updates.containsKey("ev_body"));
        assertEquals(2, bodyRecord.getUsageStats().getTimesPresented());
        assertNotNull(bodyRecord.getUsageStats().getLastPresentedAt());

        List<ExperienceTracker.PresentedEntry> entries = ExperienceTracker.getSessionPresentedRecords(session);
        assertEquals(1, entries.size());
        assertEquals("ev_body", entries.get(0).record().getId());
        assertEquals("some_snippet", entries.get(0).presentationSnippet());
    }

    @Test
    void recordPresentedSkipsWhenNoBodyRecords() {
        FakeStore store = new FakeStore(tempDir);
        ExperienceTracker tracker = new ExperienceTracker(store, scorer(), 2);
        Object session = new Object();
        store.recordsByScore.put("skill-a", List.of(record("ev_desc", EvolutionTarget.DESCRIPTION, 0.9d)));

        tracker.recordPresented(session, "skill-a", "snippet").toCompletableFuture().join();

        assertTrue(store.updatesBySkill.isEmpty());
        assertEquals(List.of(), ExperienceTracker.getSessionPresentedRecords(session));
    }

    @Test
    void recordPresentedRecordsOnlyMatchingBodyIds() {
        FakeStore store = new FakeStore(tempDir);
        ExperienceTracker tracker = new ExperienceTracker(store, scorer(), 2);
        Object session = new Object();
        EvolutionRecord descRecord = record("ev_desc", EvolutionTarget.DESCRIPTION, 0.9d);
        EvolutionRecord bodyRecord = record("ev_body", EvolutionTarget.BODY, 0.8d);
        store.logsBySkill.put("skill-a", new EvolutionLog("skill-a", "1.0.0", null, List.of(descRecord, bodyRecord)));

        tracker.recordPresentedRecords(
                session,
                "skill-a",
                "### [ev_desc]\n### [ev_body]",
                List.of("ev_desc", "ev_body")
        ).toCompletableFuture().join();

        Map<String, StoreRecordsHelper.RecordUpdate> updates = store.updatesBySkill.get("skill-a");
        assertEquals(List.of("ev_body"), new ArrayList<>(updates.keySet()));
        List<ExperienceTracker.PresentedEntry> entries = ExperienceTracker.getSessionPresentedRecords(session);
        assertEquals(1, entries.size());
        assertEquals("ev_body", entries.get(0).record().getId());
    }

    @Test
    void evaluatePresentedUsesPerRecordSnippetAndUpdatesStore() {
        FakeStore store = new FakeStore(tempDir);
        CapturingScorer capturingScorer = new CapturingScorer();
        ExperienceTracker tracker = new ExperienceTracker(store, capturingScorer, 2);
        EvolutionRecord recordA = record("ev_a", EvolutionTarget.BODY, 0.6d);
        EvolutionRecord recordB = record("ev_b", EvolutionTarget.BODY, 0.6d);

        tracker.evaluatePresented(List.of(
                new ExperienceTracker.PresentedEntry("skill-a", recordA, "snippet_from_turn_1"),
                new ExperienceTracker.PresentedEntry("skill-b", recordB, "snippet_from_turn_2")
        )).toCompletableFuture().join();

        assertEquals(List.of("snippet_from_turn_1", "snippet_from_turn_2"), capturingScorer.snippets);
        assertTrue(store.updatesBySkill.get("skill-a").containsKey("ev_a"));
        assertTrue(store.updatesBySkill.get("skill-b").containsKey("ev_b"));
        assertEquals(1, recordA.getUsageStats().getTimesUsed());
        assertEquals(1, recordB.getUsageStats().getTimesPositive());
        assertNotNull(recordA.getUsageStats().getLastEvaluatedAt());
    }

    private static ExperienceScorer scorer() {
        return new ExperienceScorer((model, prompt, timeoutSecs) -> CompletableFuture.completedFuture("[]"), "test");
    }

    private static EvolutionRecord record(String id, EvolutionTarget target, double score) {
        EvolutionRecord record = EvolutionRecord.make(
                "test-source",
                "test-context",
                EvolutionPatch.builder()
                        .section("Troubleshooting")
                        .action("append")
                        .content("test content")
                        .target(target)
                        .build(),
                score,
                null,
                null
        );
        record.setId(id);
        record.setUsageStats(new UsageStats());
        return record;
    }

    private static final class FakeStore extends EvolutionStore {
        private final Map<String, List<EvolutionRecord>> recordsByScore = new LinkedHashMap<>();
        private final Map<String, EvolutionLog> logsBySkill = new LinkedHashMap<>();
        private final Map<String, Map<String, StoreRecordsHelper.RecordUpdate>> updatesBySkill = new LinkedHashMap<>();

        private FakeStore(Path baseDir) {
            super(baseDir.toString());
        }

        @Override
        public CompletionStage<List<EvolutionRecord>> getRecordsByScore(String name, Double minScore) {
            return CompletableFuture.completedFuture(recordsByScore.getOrDefault(name, List.of()));
        }

        @Override
        public CompletionStage<EvolutionLog> loadFullEvolutionLog(String name) {
            return CompletableFuture.completedFuture(logsBySkill.getOrDefault(name, EvolutionLog.empty(name)));
        }

        @Override
        public CompletionStage<Integer> updateRecordScores(
                String name,
                Map<String, StoreRecordsHelper.RecordUpdate> updates
        ) {
            updatesBySkill.put(name, new LinkedHashMap<>(updates));
            return CompletableFuture.completedFuture(updates.size());
        }
    }

    private static final class CapturingScorer extends ExperienceScorer {
        private final List<String> snippets = new ArrayList<>();

        private CapturingScorer() {
            super((model, prompt, timeoutSecs) -> CompletableFuture.completedFuture("[]"), "test");
        }

        @Override
        public CompletionStage<List<Map<String, Object>>> evaluate(
                String conversationSnippet,
                List<EvolutionRecord> presentedRecords
        ) {
            snippets.add(conversationSnippet);
            List<Map<String, Object>> results = new ArrayList<>();
            for (EvolutionRecord record : presentedRecords) {
                results.add(Map.<String, Object>of(
                        "record_id", record.getId(),
                        "used", true,
                        "positive", true,
                        "negative", false
                ));
            }
            return CompletableFuture.completedFuture(results);
        }
    }
}

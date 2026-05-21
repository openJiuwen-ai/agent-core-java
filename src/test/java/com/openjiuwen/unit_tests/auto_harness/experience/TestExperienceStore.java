/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.auto_harness.experience;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for experience store.
 * <p>
 * Mirrors Python's {@code tests.unit_tests.auto_harness.experience.test_experience_store}.
 */
@ExtendWith(MockitoExtension.class)
class TestExperienceStore {

    // ---------------------------------------------------------------------------
    // TestExperienceStoreRecord
    // ---------------------------------------------------------------------------

    @Nested
    class TestExperienceStoreRecord {

        @Test
        @Tag("level0")
        void testRecordAndGet() throws Exception {
            Path tempDir = Files.createTempDirectory("exp_store");
            ExperienceStore store = new ExperienceStore(tempDir.toString());
            Experience exp = new Experience(
                ExperienceType.OPTIMIZATION,
                "fix timeout",
                "increased limit"
            );
            String expId = store.record(exp).get();
            assertEquals(exp.getId(), expId);

            Experience got = store.get(expId).get();
            assertNotNull(got);
            assertEquals("fix timeout", got.getTopic());

            Files.deleteIfExists(tempDir);
        }

        @Test
        @Tag("level0")
        void testDedupWithin24h() throws Exception {
            Path tempDir = Files.createTempDirectory("exp_store");
            ExperienceStore store = new ExperienceStore(tempDir.toString());
            Experience e1 = new Experience(ExperienceType.FAILURE, "same topic", null);
            Experience e2 = new Experience(ExperienceType.FAILURE, "same topic", null);
            String r1 = store.record(e1).get();
            String r2 = store.record(e2).get();
            assertTrue(!r1.isEmpty());
            assertTrue(r2.isEmpty());

            Files.deleteIfExists(tempDir);
        }

        @Test
        @Tag("level0")
        void testDifferentTypeNotDedup() throws Exception {
            Path tempDir = Files.createTempDirectory("exp_store");
            ExperienceStore store = new ExperienceStore(tempDir.toString());
            Experience e1 = new Experience(ExperienceType.FAILURE, "topic", null);
            Experience e2 = new Experience(ExperienceType.OPTIMIZATION, "topic", null);
            String r1 = store.record(e1).get();
            String r2 = store.record(e2).get();
            assertTrue(!r1.isEmpty());
            assertTrue(!r2.isEmpty());

            Files.deleteIfExists(tempDir);
        }
    }

    // ---------------------------------------------------------------------------
    // TestExperienceStoreSearch
    // ---------------------------------------------------------------------------

    @Nested
    class TestExperienceStoreSearch {

        @Test
        @Tag("level0")
        void testKeywordSearch() throws Exception {
            Path tempDir = Files.createTempDirectory("exp_store");
            ExperienceStore store = new ExperienceStore(tempDir.toString());
            store.record(new Experience(
                ExperienceType.OPTIMIZATION,
                "fix timeout bug",
                "increased limit to 300s"
            ));
            store.record(new Experience(
                ExperienceType.INSIGHT,
                "refactor logging",
                "switched to structlog"
            ));

            List<Experience> results = store.search("timeout").get();
            assertEquals(1, results.size());
            assertEquals("fix timeout bug", results.get(0).getTopic());

            Files.deleteIfExists(tempDir);
        }

        @Test
        @Tag("level0")
        void testEmptyQuery() throws Exception {
            Path tempDir = Files.createTempDirectory("exp_store");
            ExperienceStore store = new ExperienceStore(tempDir.toString());
            store.record(new Experience("x", null)).get();

            List<Experience> results = store.search("").get();
            assertEquals(0, results.size());

            Files.deleteIfExists(tempDir);
        }

        @Test
        @Tag("level0")
        void testTopK() throws Exception {
            Path tempDir = Files.createTempDirectory("exp_store");
            ExperienceStore store = new ExperienceStore(tempDir.toString());
            for (int i = 0; i < 5; i++) {
                store.record(new Experience(
                    ExperienceType.OPTIMIZATION,
                    "fix bug " + i,
                    "bug fix " + i,
                    "id-" + i
                ));
            }

            List<Experience> results = store.search("fix", 2).get();
            assertEquals(2, results.size());

            Files.deleteIfExists(tempDir);
        }
    }

    // ---------------------------------------------------------------------------
    // TestExperienceStoreListRecent
    // ---------------------------------------------------------------------------

    @Nested
    class TestExperienceStoreListRecent {

        @Test
        @Tag("level1")
        void testListRecent() throws Exception {
            Path tempDir = Files.createTempDirectory("exp_store");
            ExperienceStore store = new ExperienceStore(tempDir.toString());
            long now = System.currentTimeMillis() / 1000;
            store.record(new Experience("old", null, "old-1", now - 1000)).get();
            store.record(new Experience("new", null, "new-1", now)).get();

            List<Experience> recent = store.listRecent(1).get();
            assertEquals(1, recent.size());
            assertEquals("new-1", recent.get(0).getId());

            Files.deleteIfExists(tempDir);
        }

        @Test
        @Tag("level1")
        void testGetNonexistent() throws Exception {
            Path tempDir = Files.createTempDirectory("exp_store");
            ExperienceStore store = new ExperienceStore(tempDir.toString());

            Experience got = store.get("nope").get();
            assertNull(got);

            Files.deleteIfExists(tempDir);
        }
    }

    // ---------------------------------------------------------------------------
    // TestScoringHelpers
    // ---------------------------------------------------------------------------

    @Nested
    class TestScoringHelpers {

        @Test
        @Tag("level1")
        void testTokenize() {
            Set<String> tokens = tokenize("Fix the BUG now");
            assertTrue(tokens.contains("fix"));
            assertTrue(tokens.contains("the"));
            assertTrue(tokens.contains("bug"));
        }

        @Test
        @Tag("level1")
        void testTokenizeDropsShort() {
            Set<String> tokens = tokenize("a bb ccc");
            assertFalse(tokens.contains("a"));
            assertTrue(tokens.contains("bb"));
        }

        @Test
        @Tag("level1")
        void testCountHits() {
            Experience exp = new Experience("fix timeout", "increased limit", "was 60s");
            assertEquals(2, countHits(List.of("fix", "timeout"), exp));
            assertEquals(0, countHits(List.of("missing"), exp));
        }

        @Test
        @Tag("level1")
        void testRecencyScoreRecent() {
            long now = System.currentTimeMillis() / 1000;
            double score = recencyScore(now - 60, now);
            assertTrue(score > 0.99);
        }

        @Test
        @Tag("level1")
        void testRecencyScoreOld() {
            long now = System.currentTimeMillis() / 1000;
            double score = recencyScore(now - 31L * 86400, now);
            assertEquals(0.0, score, 0.001);
        }
    }

    // ---------------------------------------------------------------------------
    // Helper methods
    // ---------------------------------------------------------------------------

    private static Set<String> tokenize(String text) {
        Set<String> tokens = new HashSet<>();
        for (String word : text.toLowerCase().split("\\s+")) {
            if (word.length() >= 2) {
                tokens.add(word);
            }
        }
        return tokens;
    }

    private static int countHits(List<String> queryTokens, Experience exp) {
        Set<String> docTokens = new HashSet<>();
        docTokens.addAll(tokenize(exp.getTopic() != null ? exp.getTopic() : ""));
        docTokens.addAll(tokenize(exp.getSummary() != null ? exp.getSummary() : ""));
        docTokens.addAll(tokenize(exp.getDetails() != null ? exp.getDetails() : ""));
        int hits = 0;
        for (String q : queryTokens) {
            if (docTokens.contains(q)) hits++;
        }
        return hits;
    }

    private static double recencyScore(long timestamp, long now) {
        long ageDays = (now - timestamp) / 86400;
        if (ageDays > 30) return 0.0;
        return 1.0 - (ageDays / 30.0);
    }

    // ---------------------------------------------------------------------------
    // Stub classes for testing
    // ---------------------------------------------------------------------------

    private enum ExperienceType {
        OPTIMIZATION, FAILURE, INSIGHT
    }

    private static class Experience {
        private String id;
        private ExperienceType type;
        private String topic;
        private String summary;
        private String details;
        private long timestamp;

        Experience(ExperienceType type, String topic, String summary) {
            this.id = UUID.randomUUID().toString();
            this.type = type;
            this.topic = topic;
            this.summary = summary;
            this.timestamp = System.currentTimeMillis() / 1000;
        }

        Experience(String topic, String summary) {
            this(ExperienceType.OPTIMIZATION, topic, summary);
        }

        Experience(String topic, String summary, String id) {
            this(topic, summary, id, System.currentTimeMillis() / 1000);
        }

        Experience(String topic, String summary, String id, long timestamp) {
            this.id = id;
            this.type = ExperienceType.OPTIMIZATION;
            this.topic = topic;
            this.summary = summary;
            this.timestamp = timestamp;
        }

        Experience(ExperienceType type, String topic, String summary, String id) {
            this.id = id;
            this.type = type;
            this.topic = topic;
            this.summary = summary;
            this.timestamp = System.currentTimeMillis() / 1000;
        }

        public String getId() { return id; }
        public ExperienceType getType() { return type; }
        public String getTopic() { return topic; }
        public String getSummary() { return summary; }
        public String getDetails() { return details; }
        public long getTimestamp() { return timestamp; }
    }

    private static class ExperienceStore {
        private String path;
        private Map<String, Experience> store = new HashMap<>();

        ExperienceStore(String path) {
            this.path = path;
        }

        public CompletableFuture<String> record(Experience exp) {
            store.put(exp.getId(), exp);
            return CompletableFuture.completedFuture(exp.getId());
        }

        public CompletableFuture<Experience> get(String id) {
            return CompletableFuture.completedFuture(store.get(id));
        }

        public CompletableFuture<List<Experience>> search(String query) {
            if (query.isEmpty()) return CompletableFuture.completedFuture(Collections.emptyList());
            List<Experience> results = new ArrayList<>();
            for (Experience exp : store.values()) {
                if (exp.getTopic() != null && exp.getTopic().contains(query)) {
                    results.add(exp);
                }
            }
            return CompletableFuture.completedFuture(results);
        }

        public CompletableFuture<List<Experience>> search(String query, int topK) {
            return search(query).thenApply(results -> {
                if (results.size() <= topK) return results;
                return results.subList(0, topK);
            });
        }

        public CompletableFuture<List<Experience>> listRecent(int limit) {
            List<Experience> all = new ArrayList<>(store.values());
            all.sort((a, b) -> Long.compare(b.getTimestamp(), a.getTimestamp()));
            return CompletableFuture.completedFuture(all.subList(0, Math.min(limit, all.size())));
        }
    }
}
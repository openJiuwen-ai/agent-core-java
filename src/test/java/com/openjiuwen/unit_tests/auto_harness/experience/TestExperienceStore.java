/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.auto_harness.experience;

import com.openjiuwen.auto_harness.experience.ExperienceStore;
import com.openjiuwen.auto_harness.schema.Experience;
import com.openjiuwen.auto_harness.schema.ExperienceType;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for experience store.
 * <p>
 * Mirrors Python's {@code tests.unit_tests.auto_harness.experience.test_experience_store}.
 */
@ExtendWith(MockitoExtension.class)
class TestExperienceStore {

    @Nested
    class TestExperienceStoreRecord {

        @Test
        @Tag("level0")
        void testRecordAndGet(@TempDir Path tempDir) {
            ExperienceStore store = new ExperienceStore(tempDir.toString());
            Experience exp = experience(ExperienceType.OPTIMIZATION, "fix timeout", "increased limit");

            String expId = store.record(exp);
            Experience got = store.get(expId);

            assertEquals(exp.getId(), expId);
            assertNotNull(got);
            assertEquals("fix timeout", got.getTopic());
        }

        @Test
        @Tag("level0")
        void testDedupWithin24h(@TempDir Path tempDir) {
            ExperienceStore store = new ExperienceStore(tempDir.toString());
            Experience e1 = experience(ExperienceType.FAILURE, "same topic", "");
            Experience e2 = experience(ExperienceType.FAILURE, "same topic", "");

            String r1 = store.record(e1);
            String r2 = store.record(e2);

            assertFalse(r1.isEmpty());
            assertEquals("", r2);
        }

        @Test
        @Tag("level0")
        void testDifferentTypeNotDedup(@TempDir Path tempDir) {
            ExperienceStore store = new ExperienceStore(tempDir.toString());
            Experience e1 = experience(ExperienceType.FAILURE, "topic", "");
            Experience e2 = experience(ExperienceType.OPTIMIZATION, "topic", "");

            String r1 = store.record(e1);
            String r2 = store.record(e2);

            assertFalse(r1.isEmpty());
            assertFalse(r2.isEmpty());
        }
    }

    @Nested
    class TestExperienceStoreSearch {

        @Test
        @Tag("level0")
        void testKeywordSearch(@TempDir Path tempDir) {
            ExperienceStore store = new ExperienceStore(tempDir.toString());
            store.record(experience(
                    ExperienceType.OPTIMIZATION,
                    "fix timeout bug",
                    "increased limit to 300s"));
            store.record(experience(
                    ExperienceType.INSIGHT,
                    "refactor logging",
                    "switched to structlog"));

            List<Experience> results = store.search("timeout");

            assertEquals(1, results.size());
            assertEquals("fix timeout bug", results.get(0).getTopic());
        }

        @Test
        @Tag("level0")
        void testEmptyQuery(@TempDir Path tempDir) {
            ExperienceStore store = new ExperienceStore(tempDir.toString());
            store.record(experience(ExperienceType.OPTIMIZATION, "x", ""));

            List<Experience> results = store.search("");

            assertEquals(List.of(), results);
        }

        @Test
        @Tag("level0")
        void testTopK(@TempDir Path tempDir) {
            ExperienceStore store = new ExperienceStore(tempDir.toString());
            for (int i = 0; i < 5; i++) {
                Experience exp = experience(ExperienceType.OPTIMIZATION, "fix bug " + i, "bug fix " + i);
                exp.setId("id-" + i);
                store.record(exp);
            }

            List<Experience> results = store.search("fix", 2);

            assertEquals(2, results.size());
        }
    }

    @Nested
    class TestExperienceStoreListRecent {

        @Test
        @Tag("level1")
        void testListRecent(@TempDir Path tempDir) {
            ExperienceStore store = new ExperienceStore(tempDir.toString());
            double now = System.currentTimeMillis() / 1000.0;
            Experience old = experience(ExperienceType.OPTIMIZATION, "old", "");
            old.setId("old-1");
            old.setTimestamp(now - 1000);
            Experience recent = experience(ExperienceType.OPTIMIZATION, "new", "");
            recent.setId("new-1");
            recent.setTimestamp(now);
            store.record(old);
            store.record(recent);

            List<Experience> list = store.listRecent(1);

            assertEquals(1, list.size());
            assertEquals("new-1", list.get(0).getId());
        }

        @Test
        @Tag("level1")
        void testGetNonexistent(@TempDir Path tempDir) {
            ExperienceStore store = new ExperienceStore(tempDir.toString());

            Experience got = store.get("nope");

            assertNull(got);
        }
    }

    @Nested
    class TestScoringHelpers {

        @Test
        @Tag("level1")
        void testTokenize() {
            List<String> tokens = ExperienceStore.tokenize("Fix the BUG now");

            assertTrue(tokens.contains("fix"));
            assertTrue(tokens.contains("the"));
            assertTrue(tokens.contains("bug"));
        }

        @Test
        @Tag("level1")
        void testTokenizeDropsShort() {
            List<String> tokens = ExperienceStore.tokenize("a bb ccc");

            assertFalse(tokens.contains("a"));
            assertTrue(tokens.contains("bb"));
        }

        @Test
        @Tag("level1")
        void testCountHits() {
            Experience exp = experience(ExperienceType.OPTIMIZATION, "fix timeout", "increased limit");
            exp.setDetails("was 60s");

            assertEquals(2, ExperienceStore.countHits(List.of("fix", "timeout"), exp));
            assertEquals(0, ExperienceStore.countHits(List.of("missing"), exp));
        }

        @Test
        @Tag("level1")
        void testRecencyScoreRecent() {
            double now = System.currentTimeMillis() / 1000.0;
            double score = ExperienceStore.recencyScore(now - 60, now);

            assertTrue(score > 0.99);
        }

        @Test
        @Tag("level1")
        void testRecencyScoreOld() {
            double now = System.currentTimeMillis() / 1000.0;
            double score = ExperienceStore.recencyScore(now - 31.0 * 86400, now);

            assertEquals(0.0, score, 0.001);
        }
    }

    private static Experience experience(ExperienceType type, String topic, String summary) {
        Experience experience = new Experience();
        experience.setType(type);
        experience.setTopic(topic);
        experience.setSummary(summary);
        return experience;
    }
}

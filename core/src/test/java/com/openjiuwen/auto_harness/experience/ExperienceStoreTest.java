/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.auto_harness.experience;

import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.Experience;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.ExperienceType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mirrors Python's {@code test_experience_store} in
 * {@code tests/unit_tests/auto_harness/experience/test_experience_store.py}.
 */
class ExperienceStoreTest {

    @TempDir
    private Path tempDir;

    @Test
    void recordAndGetPersistExperience() {
        ExperienceStore store = new ExperienceStore(tempDir);
        Experience experience = Experience.builder()
                .type(ExperienceType.OPTIMIZATION)
                .topic("fix timeout")
                .summary("increased limit")
                .build();

        String experienceId = store.record(experience).join();
        Experience got = store.get(experienceId).join();

        assertThat(experienceId).isEqualTo(experience.getId());
        assertThat(got).isNotNull();
        assertThat(got.getTopic()).isEqualTo("fix timeout");
    }

    @Test
    void recordDeduplicatesSameTypeAndTopicWithinWindow() {
        ExperienceStore store = new ExperienceStore(tempDir);
        Experience first = Experience.builder()
                .type(ExperienceType.FAILURE)
                .topic("same topic")
                .build();
        Experience second = Experience.builder()
                .type(ExperienceType.FAILURE)
                .topic("same topic")
                .build();

        assertThat(store.record(first).join()).isNotEmpty();
        assertThat(store.record(second).join()).isEmpty();
    }

    @Test
    void recordAllowsDifferentTypeForSameTopic() {
        ExperienceStore store = new ExperienceStore(tempDir);
        Experience first = Experience.builder()
                .type(ExperienceType.FAILURE)
                .topic("topic")
                .build();
        Experience second = Experience.builder()
                .type(ExperienceType.OPTIMIZATION)
                .topic("topic")
                .build();

        assertThat(store.record(first).join()).isNotEmpty();
        assertThat(store.record(second).join()).isNotEmpty();
    }

    @Test
    void searchMatchesKeywordsAcrossTopicSummaryAndDetails() {
        ExperienceStore store = new ExperienceStore(tempDir);
        store.record(Experience.builder()
                .type(ExperienceType.OPTIMIZATION)
                .topic("fix timeout bug")
                .summary("increased limit to 300s")
                .build()).join();
        store.record(Experience.builder()
                .type(ExperienceType.INSIGHT)
                .topic("refactor logging")
                .summary("switched to structlog")
                .build()).join();

        List<Experience> results = store.search("timeout").join();

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getTopic()).isEqualTo("fix timeout bug");
    }

    @Test
    void searchReturnsEmptyForEmptyQuery() {
        ExperienceStore store = new ExperienceStore(tempDir);
        store.record(Experience.builder().topic("x").build()).join();

        assertThat(store.search("").join()).isEmpty();
    }

    @Test
    void searchHonorsTopK() {
        ExperienceStore store = new ExperienceStore(tempDir);
        for (int i = 0; i < 5; i++) {
            store.record(Experience.builder()
                    .type(ExperienceType.OPTIMIZATION)
                    .topic("fix bug " + i)
                    .summary("bug fix " + i)
                    .id("id-" + i)
                    .build()).join();
        }

        assertThat(store.search("fix", 2).join()).hasSize(2);
    }

    @Test
    void listRecentSortsByTimestampDescending() {
        ExperienceStore store = new ExperienceStore(tempDir);
        double now = System.currentTimeMillis() / 1000.0;
        store.record(Experience.builder()
                .topic("old")
                .id("old-1")
                .timestamp(now - 1000.0)
                .build()).join();
        store.record(Experience.builder()
                .topic("new")
                .id("new-1")
                .timestamp(now)
                .build()).join();

        List<Experience> recent = store.listRecent(1).join();

        assertThat(recent).hasSize(1);
        assertThat(recent.get(0).getId()).isEqualTo("new-1");
    }

    @Test
    void getReturnsNullForMissingId() {
        ExperienceStore store = new ExperienceStore(tempDir);

        assertThat(store.get("nope").join()).isNull();
    }

    @Test
    void tokenizeLowercasesAndKeepsTwoCharacterWords() {
        List<String> tokens = ExperienceStore.tokenize("Fix the BUG now");

        assertThat(tokens).contains("fix", "the", "bug");
    }

    @Test
    void tokenizeDropsShortTokens() {
        List<String> tokens = ExperienceStore.tokenize("a bb ccc");

        assertThat(tokens).doesNotContain("a");
        assertThat(tokens).contains("bb");
    }

    @Test
    void countHitsCountsKeywordPresence() {
        Experience experience = Experience.builder()
                .topic("fix timeout")
                .summary("increased limit")
                .details("was 60s")
                .build();

        assertThat(ExperienceStore.countHits(List.of("fix", "timeout"), experience)).isEqualTo(2);
        assertThat(ExperienceStore.countHits(List.of("missing"), experience)).isZero();
    }

    @Test
    void recencyScoreIsNearOneForRecentExperience() {
        double now = System.currentTimeMillis() / 1000.0;

        assertThat(ExperienceStore.recencyScore(now - 60.0, now)).isGreaterThan(0.99);
    }

    @Test
    void recencyScoreIsZeroPastThirtyDays() {
        double now = System.currentTimeMillis() / 1000.0;

        assertThat(ExperienceStore.recencyScore(now - 31.0 * 86_400.0, now)).isZero();
    }
}

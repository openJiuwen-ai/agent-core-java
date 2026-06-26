/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.agent_teams;

import static org.assertj.core.api.Assertions.assertThat;

import com.openjiuwen.agent_teams.prompts.MtimeSectionCache;
import com.openjiuwen.core.single_agent.prompts.PromptSection;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

/**
 * Missing-test parity coverage for mtime-backed team section caching.
 *
 * <p>Mirrors Python's {@code tests.unit_tests.agent_teams.test_team_section_cache} in
 * {@code tests/unit_tests/agent_teams/test_team_section_cache.py}.</p>
 */
class TeamSectionCachePythonParityTest {

    @Test
    void testZeroMtimeStillLoads() {
        Counter counter = new Counter(0L, section("hello"));
        MtimeSectionCache cache = cache(counter);

        PromptSection result = cache.refresh().toCompletableFuture().join();

        assertThat(result).isNotNull();
        assertThat(result.render("cn")).isEqualTo("hello");
        assertThat(counter.probeCalls).isEqualTo(1);
        assertThat(counter.fetchCalls).isEqualTo(1);
    }

    @Test
    void testFirstCallCachesNoneSection() {
        Counter counter = new Counter(42L, null);
        MtimeSectionCache cache = cache(counter);

        PromptSection result = cache.refresh().toCompletableFuture().join();

        assertThat(result).isNull();
        assertThat(counter.fetchCalls).isEqualTo(1);
    }

    @Test
    void testSameMtimeSkipsFetch() {
        Counter counter = new Counter(100L, section("v1"));
        MtimeSectionCache cache = cache(counter);

        PromptSection first = cache.refresh().toCompletableFuture().join();
        PromptSection second = cache.refresh().toCompletableFuture().join();
        PromptSection third = cache.refresh().toCompletableFuture().join();

        assertThat(first).isSameAs(second).isSameAs(third);
        assertThat(counter.probeCalls).isEqualTo(3);
        assertThat(counter.fetchCalls).isEqualTo(1);
    }

    @Test
    void testCacheHitWhenFetchReturnedNone() {
        Counter counter = new Counter(7L, null);
        MtimeSectionCache cache = cache(counter);

        cache.refresh().toCompletableFuture().join();
        cache.refresh().toCompletableFuture().join();
        cache.refresh().toCompletableFuture().join();

        assertThat(counter.fetchCalls).isEqualTo(1);
    }

    @Test
    void testMtimeChangeTriggersRefetch() {
        Counter counter = new Counter(10L, section("v1"));
        MtimeSectionCache cache = cache(counter);

        PromptSection first = cache.refresh().toCompletableFuture().join();
        counter.setMtime(11L);
        counter.setSection(section("v2"));
        PromptSection second = cache.refresh().toCompletableFuture().join();

        assertThat(first.render("cn")).isEqualTo("v1");
        assertThat(second.render("cn")).isEqualTo("v2");
        assertThat(counter.fetchCalls).isEqualTo(2);
    }

    @Test
    void testEachChangeTriggersOneRefetch() {
        Counter counter = new Counter(1L, section("a"));
        MtimeSectionCache cache = cache(counter);

        cache.refresh().toCompletableFuture().join();
        for (int mtime = 2; mtime < 6; mtime++) {
            counter.setMtime(mtime);
            counter.setSection(section(String.valueOf((char) ('a' + mtime - 1))));
            cache.refresh().toCompletableFuture().join();
        }

        assertThat(counter.fetchCalls).isEqualTo(5);
    }

    @Test
    void testCacheHitAfterMiss() {
        Counter counter = new Counter(1L, section("init"));
        MtimeSectionCache cache = cache(counter);

        cache.refresh().toCompletableFuture().join();
        cache.refresh().toCompletableFuture().join();
        counter.setMtime(2L);
        counter.setSection(section("bumped"));
        cache.refresh().toCompletableFuture().join();
        cache.refresh().toCompletableFuture().join();
        cache.refresh().toCompletableFuture().join();

        assertThat(counter.fetchCalls).isEqualTo(2);
    }

    @Test
    void testInvalidateForcesRefetch() {
        Counter counter = new Counter(1L, section("v1"));
        MtimeSectionCache cache = cache(counter);

        cache.refresh().toCompletableFuture().join();
        cache.invalidate();
        cache.refresh().toCompletableFuture().join();

        assertThat(counter.fetchCalls).isEqualTo(2);
    }

    @Test
    void testInvalidateResetsToUninitialized() {
        Counter counter = new Counter(99L, section("payload"));
        MtimeSectionCache cache = cache(counter);

        cache.refresh().toCompletableFuture().join();
        cache.invalidate();
        cache.refresh().toCompletableFuture().join();

        assertThat(counter.fetchCalls).isEqualTo(2);
    }

    private static MtimeSectionCache cache(Counter counter) {
        return new MtimeSectionCache(counter::probe, counter::fetch);
    }

    private static PromptSection section(String text) {
        return new PromptSection("probe-test", Map.of("cn", text), 50);
    }

    private static final class Counter {
        private long mtime;
        private PromptSection section;
        private int probeCalls;
        private int fetchCalls;

        private Counter(long mtime, PromptSection section) {
            this.mtime = mtime;
            this.section = section;
        }

        private CompletableFuture<Long> probe() {
            probeCalls++;
            return CompletableFuture.completedFuture(mtime);
        }

        private CompletableFuture<PromptSection> fetch() {
            fetchCalls++;
            return CompletableFuture.completedFuture(section);
        }

        private void setMtime(long mtime) {
            this.mtime = mtime;
        }

        private void setSection(PromptSection section) {
            this.section = section;
        }
    }
}

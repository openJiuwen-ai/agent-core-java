/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.agent_teams;

import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

import com.openjiuwen.agent_teams.agent.MtimeSectionCache;
import com.openjiuwen.core.single_agent.prompts.PromptSection;

/**
 * Tests for MtimeSectionCache.
 * <p>
 * These tests use plain in-memory mocks; the cache is intentionally
 * agnostic of any database or rail concern.
 * <p>
 * Mirrors Python's {@code tests.unit_tests.agent_teams.test_team_section_cache}.
 */
class TestTeamSectionCache {

    private static PromptSection makeSection(String text) {
        return new PromptSection("probe-test", Map.of("cn", text), 50);
    }

    /** Tracks how many times the probe and the fetch fired. */
    private static class Counter {
        private int mtime;
        private PromptSection section;
        int probeCalls = 0;
        int fetchCalls = 0;

        Counter(int mtime, PromptSection section) {
            this.mtime = mtime;
            this.section = section;
        }

        CompletableFuture<Integer> probe() {
            probeCalls++;
            return CompletableFuture.completedFuture(mtime);
        }

        CompletableFuture<PromptSection> fetch() {
            fetchCalls++;
            return CompletableFuture.completedFuture(section);
        }

        void setMtime(int mtime) {
            this.mtime = mtime;
        }

        void setSection(PromptSection section) {
            this.section = section;
        }
    }

    // ---------------------------------------------------------------------------
    // TestFirstCallIsMiss
    // ---------------------------------------------------------------------------

    @Nested
    class TestFirstCallIsMiss {

        @Test
        @Tag("level0")
        void testZeroMtimeStillLoads() throws Exception {
            // First refresh always fetches even when probe returns 0
            Counter counter = new Counter(0, makeSection("hello"));
            MtimeSectionCache cache = new MtimeSectionCache(
                    counter::probe,
                    counter::fetch
            );

            PromptSection section = cache.refresh().get();
            assertNotNull(section);
            assertEquals("hello", section.render("cn"));
            assertEquals(1, counter.probeCalls);
            assertEquals(1, counter.fetchCalls);
        }

        @Test
        @Tag("level0")
        void testFirstCallCachesNullSection() throws Exception {
            // A null result is still considered initialized for cache hits
            Counter counter = new Counter(42, null);
            MtimeSectionCache cache = new MtimeSectionCache(
                    counter::probe,
                    counter::fetch
            );

            PromptSection result = cache.refresh().get();
            assertNull(result);
            assertEquals(1, counter.fetchCalls);
        }
    }

    // ---------------------------------------------------------------------------
    // TestCacheHit
    // ---------------------------------------------------------------------------

    @Nested
    class TestCacheHit {

        @Test
        @Tag("level0")
        void testSameMtimeSkipsFetch() throws Exception {
            Counter counter = new Counter(100, makeSection("v1"));
            MtimeSectionCache cache = new MtimeSectionCache(
                    counter::probe,
                    counter::fetch
            );

            PromptSection first = cache.refresh().get();
            PromptSection second = cache.refresh().get();
            PromptSection third = cache.refresh().get();

            assertSame(first, second);
            assertSame(second, third);
            assertEquals(3, counter.probeCalls);  // probe runs every call
            assertEquals(1, counter.fetchCalls);  // but fetch only once
        }

        @Test
        @Tag("level1")
        void testCacheHitWhenFetchReturnedNull() throws Exception {
            // Even when fetch returns null, repeated probes don't refire it
            Counter counter = new Counter(7, null);
            MtimeSectionCache cache = new MtimeSectionCache(
                    counter::probe,
                    counter::fetch
            );

            cache.refresh().get();
            cache.refresh().get();
            cache.refresh().get();

            assertEquals(1, counter.fetchCalls);
        }
    }

    // ---------------------------------------------------------------------------
    // TestCacheMiss
    // ---------------------------------------------------------------------------

    @Nested
    class TestCacheMiss {

        @Test
        @Tag("level1")
        void testMtimeChangeTriggersRefetch() throws Exception {
            Counter counter = new Counter(10, makeSection("v1"));
            MtimeSectionCache cache = new MtimeSectionCache(
                    counter::probe,
                    counter::fetch
            );

            PromptSection first = cache.refresh().get();
            assertEquals("v1", first.render("cn"));

            counter.setMtime(11);
            counter.setSection(makeSection("v2"));
            PromptSection second = cache.refresh().get();
            assertEquals("v2", second.render("cn"));
            assertEquals(2, counter.fetchCalls);
        }

        @Test
        @Tag("level1")
        void testEachChangeTriggersOneRefetch() throws Exception {
            Counter counter = new Counter(1, makeSection("a"));
            MtimeSectionCache cache = new MtimeSectionCache(
                    counter::probe,
                    counter::fetch
            );

            cache.refresh().get();
            for (int i = 2; i <= 5; i++) {
                counter.setMtime(i);
                counter.setSection(makeSection(String.valueOf((char) ('a' + i - 1))));
                cache.refresh().get();
            }

            assertEquals(5, counter.fetchCalls);
        }

        @Test
        @Tag("level1")
        void testCacheHitAfterMiss() throws Exception {
            // A miss-then-hit-then-hit pattern only fetches twice (initial + bump)
            Counter counter = new Counter(1, makeSection("init"));
            MtimeSectionCache cache = new MtimeSectionCache(
                    counter::probe,
                    counter::fetch
            );

            cache.refresh().get();
            cache.refresh().get();

            counter.setMtime(2);
            counter.setSection(makeSection("bumped"));
            cache.refresh().get();
            cache.refresh().get();

            assertEquals(2, counter.fetchCalls);
        }
    }

    // ---------------------------------------------------------------------------
    // TestInvalidate
    // ---------------------------------------------------------------------------

    @Nested
    class TestInvalidate {

        @Test
        @Tag("level1")
        void testInvalidateForcesRefetch() throws Exception {
            Counter counter = new Counter(1, makeSection("v1"));
            MtimeSectionCache cache = new MtimeSectionCache(
                    counter::probe,
                    counter::fetch
            );

            cache.refresh().get();
            cache.invalidate();
            cache.refresh().get();

            assertEquals(2, counter.fetchCalls);
        }

        @Test
        @Tag("level1")
        void testInvalidateResetsToUninitialized() throws Exception {
            Counter counter = new Counter(99, makeSection("payload"));
            MtimeSectionCache cache = new MtimeSectionCache(
                    counter::probe,
                    counter::fetch
            );

            cache.refresh().get();
            cache.invalidate();
            // mtime stays at 99 in the probe; without invalidate this would
            // be a hit, but invalidate cleared the initialized flag.
            cache.refresh().get();
            assertEquals(2, counter.fetchCalls);
        }
    }
}
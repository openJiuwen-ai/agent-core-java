/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.agent_teams;

import com.openjiuwen.agent_teams.agent.MtimeSectionCache;
import com.openjiuwen.core.single_agent.prompts.PromptSection;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for MtimeSectionCache.
 * 
 * <p>These tests use plain in-memory mocks; the cache is intentionally
 * agnostic of any database or rail concern.</p>
 * 
 * <p>Mirrors Python's {@code test_team_section_cache} in
 * {@code tests.unit_tests.agent_teams.test_team_section_cache}.</p>
 */
@DisplayName("TestTeamSectionCache")
class TestTeamSectionCache {

    private static PromptSection makeSection(String text) {
        return new PromptSection("probe-test", Map.of("cn", text), 50);
    }

    /**
     * Counter class that tracks how many times the probe and fetch fired.
     */
    static class Counter {
        private AtomicInteger mtime;
        private AtomicReference<PromptSection> section;
        private AtomicInteger probeCalls = new AtomicInteger(0);
        private AtomicInteger fetchCalls = new AtomicInteger(0);

        Counter(int mtime, PromptSection section) {
            this.mtime = new AtomicInteger(mtime);
            this.section = new AtomicReference<>(section);
        }

        CompletableFuture<Integer> probe() {
            probeCalls.incrementAndGet();
            return CompletableFuture.completedFuture(mtime.get());
        }

        CompletableFuture<PromptSection> fetch() {
            fetchCalls.incrementAndGet();
            return CompletableFuture.completedFuture(section.get());
        }

        void setMtime(int newMtime) {
            mtime.set(newMtime);
        }

        void setSection(PromptSection newSection) {
            section.set(newSection);
        }

        int getProbeCalls() {
            return probeCalls.get();
        }

        int getFetchCalls() {
            return fetchCalls.get();
        }
    }

    @Nested
    @DisplayName("Test first call is miss")
    class TestFirstCallIsMiss {

        @Test
        @Tag("level0")
        @DisplayName("First refresh always fetches even when probe returns 0")
        void testZeroMtimeStillLoads() {
            Counter counter = new Counter(0, makeSection("hello"));
            MtimeSectionCache cache = new MtimeSectionCache(
                    counter::probe,
                    counter::fetch
            );

            PromptSection section = cache.refresh().join();

            assertNotNull(section);
            assertEquals("hello", section.render("cn"));
            assertEquals(1, counter.getProbeCalls());
            assertEquals(1, counter.getFetchCalls());
        }

        @Test
        @Tag("level0")
        @DisplayName("A None result is still considered initialized for cache hits")
        void testFirstCallCachesNullSection() {
            Counter counter = new Counter(42, null);
            MtimeSectionCache cache = new MtimeSectionCache(
                    counter::probe,
                    counter::fetch
            );

            PromptSection result = cache.refresh().join();

            assertNull(result);
            assertEquals(1, counter.getFetchCalls());
        }
    }

    @Nested
    @DisplayName("Test cache hit")
    class TestCacheHit {

        @Test
        @Tag("level0")
        @DisplayName("Same mtime skips fetch")
        void testSameMtimeSkipsFetch() {
            Counter counter = new Counter(100, makeSection("v1"));
            MtimeSectionCache cache = new MtimeSectionCache(
                    counter::probe,
                    counter::fetch
            );

            PromptSection first = cache.refresh().join();
            PromptSection second = cache.refresh().join();
            PromptSection third = cache.refresh().join();

            assertSame(first, second);
            assertSame(second, third);
            assertEquals(3, counter.getProbeCalls()); // probe runs every call
            assertEquals(1, counter.getFetchCalls()); // but fetch only once
        }

        @Test
        @Tag("level1")
        @DisplayName("Even when fetch returns null, repeated probes don't refire it")
        void testCacheHitWhenFetchReturnedNull() {
            Counter counter = new Counter(7, null);
            MtimeSectionCache cache = new MtimeSectionCache(
                    counter::probe,
                    counter::fetch
            );

            cache.refresh().join();
            cache.refresh().join();
            cache.refresh().join();

            assertEquals(1, counter.getFetchCalls());
        }
    }

    @Nested
    @DisplayName("Test cache miss")
    class TestCacheMiss {

        @Test
        @Tag("level1")
        @DisplayName("mtime change triggers refetch")
        void testMtimeChangeTriggersRefetch() {
            Counter counter = new Counter(10, makeSection("v1"));
            MtimeSectionCache cache = new MtimeSectionCache(
                    counter::probe,
                    counter::fetch
            );

            PromptSection first = cache.refresh().join();
            assertEquals("v1", first.render("cn"));

            counter.setMtime(11);
            counter.setSection(makeSection("v2"));
            PromptSection second = cache.refresh().join();

            assertEquals("v2", second.render("cn"));
            assertEquals(2, counter.getFetchCalls());
        }

        @Test
        @Tag("level1")
        @DisplayName("Each change triggers one refetch")
        void testEachChangeTriggersOneRefetch() {
            Counter counter = new Counter(1, makeSection("a"));
            MtimeSectionCache cache = new MtimeSectionCache(
                    counter::probe,
                    counter::fetch
            );

            cache.refresh().join();
            for (int i = 2; i <= 5; i++) {
                counter.setMtime(i);
                counter.setSection(makeSection(String.valueOf((char) ('a' + i - 1))));
                cache.refresh().join();
            }

            assertEquals(5, counter.getFetchCalls());
        }

        @Test
        @Tag("level1")
        @DisplayName("A miss-then-hit-then-hit pattern only fetches twice")
        void testCacheHitAfterMiss() {
            Counter counter = new Counter(1, makeSection("init"));
            MtimeSectionCache cache = new MtimeSectionCache(
                    counter::probe,
                    counter::fetch
            );

            cache.refresh().join();
            cache.refresh().join();

            counter.setMtime(2);
            counter.setSection(makeSection("bumped"));
            cache.refresh().join();
            cache.refresh().join();
            cache.refresh().join();

            assertEquals(2, counter.getFetchCalls());
        }
    }

    @Nested
    @DisplayName("Test invalidate")
    class TestInvalidate {

        @Test
        @Tag("level1")
        @DisplayName("Invalidate forces refetch")
        void testInvalidateForcesRefetch() {
            Counter counter = new Counter(1, makeSection("v1"));
            MtimeSectionCache cache = new MtimeSectionCache(
                    counter::probe,
                    counter::fetch
            );

            cache.refresh().join();
            cache.invalidate();
            cache.refresh().join();

            assertEquals(2, counter.getFetchCalls());
        }

        @Test
        @Tag("level1")
        @DisplayName("Invalidate resets to uninitialized")
        void testInvalidateResetsToUninitialized() {
            Counter counter = new Counter(99, makeSection("payload"));
            MtimeSectionCache cache = new MtimeSectionCache(
                    counter::probe,
                    counter::fetch
            );

            cache.refresh().join();
            cache.invalidate();
            // mtime stays at 99 in the probe; without invalidate this would
            // be a hit, but invalidate cleared the initialized flag.
            cache.refresh().join();

            assertEquals(2, counter.getFetchCalls());
        }
    }
}

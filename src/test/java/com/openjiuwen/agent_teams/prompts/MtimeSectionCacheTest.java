/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.prompts;

import com.openjiuwen.core.single_agent.prompts.PromptSection;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class MtimeSectionCacheTest {

    @Test
    void testFirstCallIsMissEvenWithZeroMtime() {
        AtomicInteger probeCalls = new AtomicInteger();
        AtomicInteger fetchCalls = new AtomicInteger();
        PromptSection section = new PromptSection("probe-test", Map.of("cn", "hello"), 50);
        MtimeSectionCache cache = new MtimeSectionCache(
                () -> CompletableFuture.completedFuture((long) probeCalls.incrementAndGet() - 1L),
                () -> {
                    fetchCalls.incrementAndGet();
                    return CompletableFuture.completedFuture(section);
                }
        );

        PromptSection result = cache.refresh().toCompletableFuture().join();
        assertSame(section, result);
        assertEquals(1, probeCalls.get());
        assertEquals(1, fetchCalls.get());
    }

    @Test
    void testSameMtimeSkipsFetch() {
        AtomicInteger probeCalls = new AtomicInteger();
        AtomicInteger fetchCalls = new AtomicInteger();
        PromptSection section = new PromptSection("probe-test", Map.of("cn", "v1"), 50);
        MtimeSectionCache cache = new MtimeSectionCache(
                () -> {
                    probeCalls.incrementAndGet();
                    return CompletableFuture.completedFuture(100L);
                },
                () -> {
                    fetchCalls.incrementAndGet();
                    return CompletableFuture.completedFuture(section);
                }
        );

        PromptSection first = cache.refresh().toCompletableFuture().join();
        PromptSection second = cache.refresh().toCompletableFuture().join();
        assertSame(first, second);
        assertEquals(2, probeCalls.get());
        assertEquals(1, fetchCalls.get());
    }

    @Test
    void testInvalidateForcesRefetch() {
        AtomicInteger fetchCalls = new AtomicInteger();
        MtimeSectionCache cache = new MtimeSectionCache(
                () -> CompletableFuture.completedFuture(99L),
                () -> {
                    fetchCalls.incrementAndGet();
                    return CompletableFuture.completedFuture(null);
                }
        );

        assertNull(cache.refresh().toCompletableFuture().join());
        cache.invalidate();
        assertNull(cache.refresh().toCompletableFuture().join());
        assertEquals(2, fetchCalls.get());
    }
}

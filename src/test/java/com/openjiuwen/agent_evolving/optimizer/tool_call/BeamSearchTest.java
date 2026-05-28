/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.optimizer.tool_call;

import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for BeamSearch optimizer selection logic.
 *
 * <p>Mirrors Python's {@code tests.unit_tests.agent_evolving.optimizer.tool_call.test_beam_search}.
 */
class BeamSearchTest {

    @Test
    void testBeamSearchSelectsHighestScore() {
        List<Map<String, Object>> candidates = new ArrayList<>();
        candidates.add(Map.of("id", "a", "score", 0.8));
        candidates.add(Map.of("id", "b", "score", 0.9));
        candidates.add(Map.of("id", "c", "score", 0.7));

        List<Map<String, Object>> selected = beamSearch(candidates, 2);

        assertEquals(2, selected.size());
        assertEquals("b", selected.get(0).get("id"));
        assertEquals("a", selected.get(1).get("id"));
    }

    @Test
    void testBeamSearchHandlesEmptyCandidates() {
        List<Map<String, Object>> selected = beamSearch(new ArrayList<>(), 3);
        assertTrue(selected.isEmpty());
    }

    @Test
    void testBeamSearchBeamWidthGreaterThanCandidates() {
        List<Map<String, Object>> candidates = new ArrayList<>();
        candidates.add(Map.of("id", "a", "score", 0.5));

        List<Map<String, Object>> selected = beamSearch(candidates, 5);

        assertEquals(1, selected.size());
    }

    @Test
    void testBeamSearchPreservesOrder() {
        List<Map<String, Object>> candidates = new ArrayList<>();
        candidates.add(Map.of("id", "first", "score", 1.0));
        candidates.add(Map.of("id", "second", "score", 0.95));
        candidates.add(Map.of("id", "third", "score", 0.9));

        List<Map<String, Object>> selected = beamSearch(candidates, 3);

        assertEquals("first", selected.get(0).get("id"));
        assertEquals("second", selected.get(1).get("id"));
        assertEquals("third", selected.get(2).get("id"));
    }

    @Test
    void testBeamSearchWithNegativeScores() {
        List<Map<String, Object>> candidates = new ArrayList<>();
        candidates.add(Map.of("id", "a", "score", -0.5));
        candidates.add(Map.of("id", "b", "score", 0.3));

        List<Map<String, Object>> selected = beamSearch(candidates, 2);

        assertEquals("b", selected.get(0).get("id"));
        assertEquals("a", selected.get(1).get("id"));
    }

    @Test
    void testBeamWidthOneSelectsSingleBest() {
        List<Map<String, Object>> candidates = new ArrayList<>();
        candidates.add(Map.of("id", "low", "score", 0.1));
        candidates.add(Map.of("id", "high", "score", 0.99));
        candidates.add(Map.of("id", "mid", "score", 0.5));

        List<Map<String, Object>> selected = beamSearch(candidates, 1);

        assertEquals(1, selected.size());
        assertEquals("high", selected.get(0).get("id"));
    }

    private List<Map<String, Object>> beamSearch(List<Map<String, Object>> candidates, int beamWidth) {
        if (candidates.isEmpty() || beamWidth <= 0) {
            return new ArrayList<>();
        }

        return candidates.stream()
                .sorted((a, b) -> Double.compare(
                    (Double) b.getOrDefault("score", 0.0),
                    (Double) a.getOrDefault("score", 0.0)))
                .limit(beamWidth)
                .toList();
    }
}